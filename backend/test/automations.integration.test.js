const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");

const port = 51191;
const adminPassword = "TestAdmin42!";
let child;
let dataDir;
let scriptDir;

async function request(url, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${url}`, { ...options, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const body = await response.json().catch(() => null);
  return { response, body };
}

async function waitForServer() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try { const { response } = await request("/api/health"); if (response.ok) return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error("Backend did not start for integration test");
}

test.before(async () => {
  dataDir = await fs.mkdtemp(path.join(os.tmpdir(), "shulkr-automation-data-"));
  scriptDir = await fs.mkdtemp(path.join(os.tmpdir(), "shulkr-automation-scripts-"));
  child = spawn(process.execPath, [path.join(__dirname, "..", "src", "server.js")], { env: { ...process.env, SHULKR_BACKEND_PORT: String(port), SHULKR_DATA_DIR: dataDir, SHULKR_SCRIPT_DIR: scriptDir, SHULKR_ADMIN_PASSWORD: adminPassword }, stdio: "ignore" });
  await waitForServer();
});

test.after(async () => {
  child?.kill();
  await fs.rm(dataDir, { recursive: true, force: true });
  await fs.rm(scriptDir, { recursive: true, force: true });
});

test("automation CRUD, duplicate, conflict, and ownership work through authenticated API", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  assert.equal(signedIn.response.status, 200);
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const graph = { formatVersion: 1, id: "automation-integration", name: "Integration flow", description: "test", nodes: [{ id: "start", type: "flow.start", version: 1, position: { x: 0, y: 0 }, data: {} }, { id: "stop", type: "flow.stop", version: 1, position: { x: 220, y: 0 }, data: {} }], edges: [{ id: "edge-1", source: "start", sourceHandle: "execution-out", target: "stop", targetHandle: "execution-in", dataType: "execution" }], viewport: { x: 0, y: 0, zoom: 1 }, generatedCode: "print('test')" };
  const created = await request("/api/automations", { method: "POST", headers: auth, body: JSON.stringify(graph) });
  assert.equal(created.response.status, 201); assert.equal(created.body.name, graph.name);
  const listed = await request("/api/automations", { headers: auth }); assert.equal(listed.response.status, 200); assert.equal(listed.body.length, 1);
  const loaded = await request(`/api/automations/${graph.id}`, { headers: auth }); assert.deepEqual(loaded.body.nodes, graph.nodes); assert.deepEqual(loaded.body.edges, graph.edges);
  const updated = await request(`/api/automations/${graph.id}`, { method: "PUT", headers: auth, body: JSON.stringify({ ...loaded.body, name: "Updated flow", expectedUpdatedAt: loaded.body.updatedAt }) }); assert.equal(updated.response.status, 200);
  const renamed = await request(`/api/automations/${graph.id}`, { method: "PATCH", headers: auth, body: JSON.stringify({ name: "Renamed flow", expectedUpdatedAt: updated.body.updatedAt }) }); assert.equal(renamed.response.status, 200);
  const duplicated = await request(`/api/automations/${graph.id}/duplicate`, { method: "POST", headers: auth, body: JSON.stringify({ name: "Forked flow" }) }); assert.equal(duplicated.response.status, 201); assert.notEqual(duplicated.body.id, graph.id);
  const conflict = await request(`/api/automations/${graph.id}`, { method: "PUT", headers: auth, body: JSON.stringify({ ...renamed.body, expectedUpdatedAt: "stale" }) }); assert.equal(conflict.response.status, 409); assert.equal(conflict.body.code, "AUTOMATION_CONFLICT");
  const second = await request("/api/auth/local/signup", { method: "POST", body: JSON.stringify({ displayName: "Other User", email: `other-${Date.now()}@example.local`, password: "OtherUser42!" }) });
  const otherAuth = { Authorization: `Bearer ${second.body.token}` };
  const forbiddenRead = await request(`/api/automations/${graph.id}`, { headers: otherAuth }); assert.equal(forbiddenRead.response.status, 404);
  const malformed = await request("/api/automations", { method: "POST", headers: auth, body: JSON.stringify({ ...graph, id: "bad-version", formatVersion: 9 }) }); assert.equal(malformed.response.status, 422);
  const missingNode = await request("/api/automations", { method: "POST", headers: auth, body: JSON.stringify({ ...graph, id: "missing-node", edges: [{ ...graph.edges[0], target: "does-not-exist" }] }) }); assert.equal(missingNode.response.status, 422);
  const deleted = await request(`/api/automations/${duplicated.body.id}`, { method: "DELETE", headers: auth }); assert.equal(deleted.response.status, 200);
  const deletedRead = await request(`/api/automations/${duplicated.body.id}`, { headers: auth }); assert.equal(deletedRead.response.status, 404);
});

test("automation templates publish and import as private non-executing copies", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const templates = await request("/api/templates", { headers: auth });
  assert.equal(templates.response.status, 200);
  const template = templates.body.find(item => item.kind === "automation" && item.id === "walk-to-coordinates");
  assert.ok(template);
  const created = await request("/api/automations/from-template", { method: "POST", headers: auth, body: JSON.stringify({ templateId: template.id }) });
  assert.equal(created.response.status, 201);
  assert.equal(created.body.nodes.length, template.graph.nodes.length);
  assert.equal(created.body.generatedCode, "");
  const published = await request("/api/library/scripts", { method: "POST", headers: auth, body: JSON.stringify({ kind: "automation", graph: { ...created.body, generatedCode: "" }, title: "Published automation", description: "Review before import", tags: ["test"], supportedMinecraftVersions: ["1.20+"] }) });
  assert.equal(published.response.status, 201);
  assert.equal(published.body.kind, "automation");
  assert.deepEqual(published.body.requiredPermissions, ["player_movement"]);
  assert.equal(published.body.verification.serverCompiled, true);
  assert.equal(published.body.verification.graphValidated, true);
  const imported = await request(`/api/library/scripts/${published.body.id}/install`, { method: "POST", headers: auth });
  assert.equal(imported.response.status, 201);
  assert.notEqual(imported.body.graph.id, created.body.id);
  assert.equal(imported.body.graph.generatedCode.length > 0, true);
  const versioned = await request(`/api/library/scripts/${published.body.id}/versions`, { method: "POST", headers: auth, body: JSON.stringify({ graph: created.body, version: "1.1.0", changelog: "Safer timeout" }) });
  assert.equal(versioned.response.status, 201);
  assert.equal(versioned.body.version, "1.1.0");
  const report = await request(`/api/library/scripts/${published.body.id}/report`, { method: "POST", headers: auth, body: JSON.stringify({ reason: "Test report" }) });
  assert.equal(report.response.status, 201);
});

test("automation execution is ownership-scoped, acknowledged, and cancellable", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const clientId = "integration-client";
  const heartbeat = await request("/api/clients/heartbeat", { method: "POST", headers: { "X-Shulkr-Device-Bootstrap": "1" }, body: JSON.stringify({ deviceId: clientId, deviceName: "Integration Client", licenseUserId: signedIn.body.user.id, status: "Connected" }) });
  assert.equal(heartbeat.response.status, 200);
  const deviceAuth = { Authorization: `Device ${heartbeat.body.deviceToken}` };
  const started = await request("/api/automations/automation-integration/execute", { method: "POST", headers: auth, body: JSON.stringify({ clientId, confirmPermissions: true }) });
  assert.equal(started.response.status, 202);
  assert.equal(started.body.status, "queued");
  assert.equal(started.body.userId, undefined);
  const commands = await request(`/api/control/commands?clientId=${clientId}`, { headers: deviceAuth });
  assert.equal(commands.response.status, 200);
  assert.equal(commands.body.length, 1);
  assert.equal(commands.body[0].type, "run_script");
  assert.match(commands.body[0].payload.path, /^automations\//);
  const acknowledged = await request(`/api/control/commands/${commands.body[0].id}/ack`, { method: "POST", headers: deviceAuth, body: JSON.stringify({ ok: true, message: "Started automation" }) });
  assert.equal(acknowledged.response.status, 200);
  const replayedAck = await request(`/api/control/commands/${commands.body[0].id}/ack`, { method: "POST", headers: deviceAuth, body: JSON.stringify({ ok: true, message: "Replayed acknowledgement" }) });
  assert.equal(replayedAck.response.status, 404);
  const running = await request(`/api/automations/executions/${started.body.id}`, { headers: auth });
  assert.equal(running.response.status, 200);
  assert.equal(running.body.status, "running");
  const cancelling = await request(`/api/automations/executions/${started.body.id}/cancel`, { method: "POST", headers: auth, body: JSON.stringify({}) });
  assert.equal(cancelling.response.status, 202);
  assert.equal(cancelling.body.status, "cancelling");
  const stopCommands = await request(`/api/control/commands?clientId=${clientId}`, { headers: deviceAuth });
  assert.equal(stopCommands.body[0].type, "stop_scripts");
  await request(`/api/control/commands/${stopCommands.body[0].id}/ack`, { method: "POST", headers: deviceAuth, body: JSON.stringify({ ok: true, message: "Stopped automation" }) });
  const cancelled = await request(`/api/automations/executions/${started.body.id}`, { headers: auth });
  assert.equal(cancelled.body.status, "cancelled");
});

test("remote stream uses a short-lived HttpOnly session instead of a JWT query parameter", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const clientId = "stream-integration-client";
  const heartbeat = await request("/api/clients/heartbeat", { method: "POST", headers: { "X-Shulkr-Device-Bootstrap": "1" }, body: JSON.stringify({ deviceId: clientId, deviceName: "Stream Integration Client", licenseUserId: signedIn.body.user.id, status: "Connected" }) });
  assert.equal(heartbeat.response.status, 200);
  const session = await request("/api/stream/session", { method: "POST", headers: auth, body: JSON.stringify({ clientId }) });
  assert.equal(session.response.status, 200);
  const cookie = session.response.headers.get("set-cookie") || "";
  assert.match(cookie, /shulkr_stream_session=/);
  assert.match(cookie, /HttpOnly/i);
  const rejected = await request(`/api/stream/mjpeg?token=${encodeURIComponent(signedIn.body.token)}`);
  assert.equal(rejected.response.status, 401);
});

test("script folders and rename/delete management stay inside the script root", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const folder = await request("/api/scripts/folders", { method: "POST", headers: auth, body: JSON.stringify({ name: "managed" }) });
  assert.equal(folder.response.status, 201);
  const created = await request("/api/scripts", { method: "POST", headers: auth, body: JSON.stringify({ name: "managed/example.py", content: "print('managed')", overwrite: true }) });
  assert.equal(created.response.status, 201);
  assert.equal(created.body.path, "managed/example.py");
  const renamed = await request("/api/scripts", { method: "PATCH", headers: auth, body: JSON.stringify({ path: created.body.path, name: "renamed.py" }) });
  assert.equal(renamed.body.path, "managed/renamed.py");
  const traversal = await request("/api/scripts", { method: "PATCH", headers: auth, body: JSON.stringify({ path: renamed.body.path, newPath: "../escape.py" }) });
  assert.equal(traversal.response.status, 400);
  const removed = await request("/api/scripts", { method: "DELETE", headers: auth, body: JSON.stringify({ path: renamed.body.path }) });
  assert.equal(removed.response.status, 200);
  const removedFolder = await request("/api/scripts/folders", { method: "DELETE", headers: auth, body: JSON.stringify({ path: folder.body.path }) });
  assert.equal(removedFolder.response.status, 200);
});

test("security headers and CORS do not expose the local backend to arbitrary origins", async () => {
  const health = await request("/api/health");
  assert.equal(health.response.status, 200);
  assert.equal(health.body.rootDir, undefined);
  assert.equal(health.body.dataDir, undefined);
  assert.equal(health.body.scriptDir, undefined);
  assert.equal(health.response.headers.get("x-content-type-options"), "nosniff");
  assert.equal(health.response.headers.get("x-frame-options"), "DENY");
  assert.match(health.response.headers.get("content-security-policy") || "", /frame-ancestors 'none'/);
  assert.equal(health.response.headers.get("x-powered-by"), null);

  const hostile = await request("/api/health", { headers: { Origin: "https://attacker.example" } });
  assert.equal(hostile.response.status, 403);
  assert.equal(hostile.response.headers.get("access-control-allow-origin"), null);

  const users = JSON.parse(await fs.readFile(path.join(dataDir, "users.json"), "utf8"));
  assert.equal(users.every(user => typeof user.passwordHash === "string" && !("password" in user)), true);
});

test("password policy, rate limiting, and privilege derivation resist account attacks", async () => {
  const weak = await request("/api/auth/local/signup", { method: "POST", body: JSON.stringify({ displayName: "Weak", email: `weak-${Date.now()}@example.local`, password: "password" }) });
  assert.equal(weak.response.status, 400);

  const userEmail = `admin-name-${Date.now()}@example.local`;
  const created = await request("/api/auth/local/signup", { method: "POST", body: JSON.stringify({ displayName: "Admin", email: userEmail, password: "SafePassword42!" }) });
  assert.equal(created.response.status, 201);
  assert.equal(created.body.user.isAdmin, false);
  assert.equal(created.body.user.tier, "Free");

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const failed = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ email: "rate-limit@example.local", password: "WrongPassword42!" }) });
    assert.equal(failed.response.status, 401);
  }
  const limited = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ email: "rate-limit@example.local", password: "WrongPassword42!" }) });
  assert.equal(limited.response.status, 429);
  assert.equal(limited.body.code, "AUTH_RATE_LIMITED");
});

test("cookie authentication enforces the dashboard CSRF header and profile fields are allowlisted", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const cookie = (signedIn.response.headers.get("set-cookie") || "").split(";")[0];
  assert.match(cookie, /shulkr_auth_session=/);
  assert.match(signedIn.response.headers.get("set-cookie") || "", /HttpOnly/i);

  const rejected = await request("/api/profile", { method: "PATCH", headers: { Cookie: cookie }, body: JSON.stringify({ displayName: "Cookie Admin" }) });
  assert.equal(rejected.response.status, 403);
  assert.equal(rejected.body.code, "CSRF_CHECK_FAILED");

  const accepted = await request("/api/profile", { method: "PATCH", headers: { Cookie: cookie, "X-Shulkr-Request": "dashboard" }, body: JSON.stringify({ displayName: "Admin", tier: "Free", isAdmin: false, features: [] }) });
  assert.equal(accepted.response.status, 200);
  assert.equal(accepted.body.tier, "Admin");
  assert.equal(accepted.body.features.includes("admin"), true);
});

test("filesystem imports and unauthenticated Minecraft device requests are rejected", async () => {
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const auth = { Authorization: `Bearer ${signedIn.body.token}` };
  const sourceImport = await request("/api/scripts", { method: "POST", headers: auth, body: JSON.stringify({ sourcePath: __filename }) });
  assert.equal(sourceImport.response.status, 400);
  assert.equal(sourceImport.body.code, "SOURCE_PATH_FORBIDDEN");
  const publishImport = await request("/api/library/scripts", { method: "POST", headers: auth, body: JSON.stringify({ sourcePath: __filename }) });
  assert.equal(publishImport.response.status, 400);
  assert.equal(publishImport.body.code, "SOURCE_PATH_FORBIDDEN");
  const managedWrite = await request("/api/scripts", { method: "POST", headers: auth, body: JSON.stringify({ name: "system/overwritten.py", content: "print('no')", overwrite: true }) });
  assert.equal(managedWrite.response.status, 403);
  assert.equal(managedWrite.body.code, "MANAGED_PATH_FORBIDDEN");

  const poll = await request("/api/control/commands?clientId=spoofed-client");
  assert.equal(poll.response.status, 401);
  const browserHeartbeat = await request("/api/clients/heartbeat", { method: "POST", headers: { Origin: "http://127.0.0.1:5177", "X-Shulkr-Device-Bootstrap": "1" }, body: JSON.stringify({ deviceId: "spoofed-client", licenseUserId: signedIn.body.user.id }) });
  assert.equal(browserHeartbeat.response.status, 401);
});

test("client streams, telemetry, and commands remain isolated between accounts", async () => {
  const adminSignIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ username: "admin", password: adminPassword }) });
  const adminAuth = { Authorization: `Bearer ${adminSignIn.body.token}` };
  const adminClientId = `admin-private-client-${Date.now()}`;
  const adminHeartbeat = await request("/api/clients/heartbeat", { method: "POST", headers: { "X-Shulkr-Device-Bootstrap": "1" }, body: JSON.stringify({ deviceId: adminClientId, deviceName: "Admin Private Client", licenseUserId: adminSignIn.body.user.id, status: "Connected", fps: 144 }) });
  assert.equal(adminHeartbeat.response.status, 200);

  const email = `isolated-${Date.now()}@example.local`;
  const created = await request("/api/auth/local/signup", { method: "POST", body: JSON.stringify({ displayName: "Isolated User", email, password: "IsolatedUser42!" }) });
  assert.equal(created.response.status, 201);
  const license = await request("/api/licenses", { method: "POST", headers: adminAuth, body: JSON.stringify({ userId: created.body.user.id, displayName: "Isolated User", tier: "Premium", status: "active" }) });
  assert.equal(license.response.status, 200);
  const signedIn = await request("/api/auth/local/signin", { method: "POST", body: JSON.stringify({ email, password: "IsolatedUser42!" }) });
  const userAuth = { Authorization: `Bearer ${signedIn.body.token}` };
  assert.equal(signedIn.body.user.tier, "Premium");

  const clients = await request("/api/clients", { headers: userAuth });
  assert.equal(clients.response.status, 200);
  assert.equal(clients.body.some(client => client.id === adminClientId), false);
  const stream = await request("/api/stream/session", { method: "POST", headers: userAuth, body: JSON.stringify({ clientId: adminClientId }) });
  assert.equal(stream.response.status, 403);
  assert.equal(stream.body.code, "CLIENT_OWNERSHIP_REQUIRED");
  const analytics = await request(`/api/stats/analytics?clientId=${encodeURIComponent(adminClientId)}`, { headers: userAuth });
  assert.equal(analytics.response.status, 403);
  assert.equal(analytics.body.code, "ANALYTICS_CLIENT_FORBIDDEN");
  const globalTemplateWrite = await request("/api/templates", { method: "POST", headers: userAuth, body: JSON.stringify({ id: "cross-account-template", name: "Cross account template", script: "print('unexpected')" }) });
  assert.equal(globalTemplateWrite.response.status, 403);
  const globalModuleWrite = await request("/api/client-modules/web-dashboard", { method: "PATCH", headers: userAuth, body: JSON.stringify({ installed: false }) });
  assert.equal(globalModuleWrite.response.status, 403);

  const queued = await request("/api/control/commands", { method: "POST", headers: adminAuth, body: JSON.stringify({ clientId: adminClientId, type: "open_ui", payload: { unexpected: "discarded" } }) });
  assert.equal(queued.response.status, 202);
  assert.deepEqual(queued.body.payload, {});
  const stolen = await request(`/api/control/commands?clientId=${encodeURIComponent(adminClientId)}`);
  assert.equal(stolen.response.status, 401);
});
