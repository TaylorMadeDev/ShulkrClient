require("dotenv").config();
const cors = require("cors");
const express = require("express");
const fsSync = require("fs");
const fs = require("fs/promises");
const path = require("path");
const { spawn, execFileSync } = require("child_process");
const crypto = require("crypto");
const session = require("express-session");
const passport = require("passport");
const GoogleStrategy = require("passport-google-oauth20").Strategy;
const jwt = require("jsonwebtoken");
const Stripe = require("stripe");
const { pathToFileURL } = require("url");
const { parseScriptSettings, validateValues, reconcileValues } = require("./scriptSettings");

const rootDir = path.resolve(__dirname, "..", "..");
const runDir = path.join(rootDir, "run");
const scriptDir = path.resolve(process.env.SHULKR_SCRIPT_DIR || process.env.SHULK_SCRIPT_DIR || process.env.FLUXUS_SCRIPT_DIR || path.join(runDir, "minescript"));
const dataDir = path.resolve(process.env.SHULKR_DATA_DIR || process.env.SHULK_DATA_DIR || process.env.FLUXUS_DATA_DIR || path.join(runDir, "shulkr-backend"));
const legacyDataDir = path.join(runDir, "shulk-backend");
const profilePath = path.join(dataDir, "profile.json");
const librariesPath = path.join(dataDir, "modules.json");
const clientModulesPath = path.join(dataDir, "client-modules.json");
const templatesPath = path.join(dataDir, "templates.json");
const libraryScriptsPath = path.join(dataDir, "library-scripts.json");
const libraryScriptPathsPath = path.join(dataDir, "module-scripts.json");
const clientsPath = path.join(dataDir, "clients.json");
const licensesPath = path.join(dataDir, "licenses.json");
const usersPath = path.join(dataDir, "users.json");
const tamperEventsPath = path.join(dataDir, "tamper-events.json");
const automationsPath = path.join(dataDir, "automations.json");
const libraryReportsPath = path.join(dataDir, "library-reports.json");
const scriptRegistryPath = path.join(dataDir, "script-registry.json");
const scriptSettingsPath = path.join(dataDir, "script-settings.json");
const scriptShortcutsPath = path.join(dataDir, "script-shortcuts.json");
const AUTOMATION_FORMAT_VERSION = 1;
const AUTOMATION_LIMITS = Object.freeze({ maxNodes: 500, maxEdges: 1500, maxGraphBytes: 2 * 1024 * 1024, maxNameLength: 120, maxDescriptionLength: 2000, maxCodeBytes: 2 * 1024 * 1024 });
const AUTOMATION_DATA_TYPES = new Set(["execution", "number", "text", "boolean", "coordinates", "block", "entity", "target", "item", "variable", "any"]);
const AUTOMATION_PERMISSION_BY_NODE = Object.freeze({
  "movement.walk_to_coordinates": ["player_movement"], "movement.walk_to_target": ["player_movement"], "movement.stop": ["player_movement"],
  "action.look_at_target": ["camera_control", "entity_targeting"], "action.mine_block": ["block_interaction", "block_breaking"],
  "world.find_nearest_block": ["block_interaction"], "world.get_player_position": [], "world.store_target": [],
  "flow.start": [], "flow.delay": [], "flow.if": [], "flow.repeat": [], "flow.stop": []
});
const resourcesDataDir = path.join(rootDir, "src", "main", "resources", "assets", "triton-ui", "data");
const iconAssetsDir = path.join(rootDir, "src", "main", "resources", "assets", "triton-ui", "textures", "icons");
const publicDir = path.join(__dirname, "..", "public");
const webClientDist = path.join(rootDir, "web-client", "dist");
const port = Number(process.env.SHULKR_BACKEND_PORT || process.env.SHULK_BACKEND_PORT || process.env.FLUXUS_BACKEND_PORT || 50991);
const backendUrl = process.env.SHULKR_BACKEND_URL || `http://127.0.0.1:${port}`;
const webClientUrl = process.env.SHULKR_WEB_CLIENT_URL || "http://127.0.0.1:5178";
const googleClientId = process.env.SHULKR_GOOGLE_CLIENT_ID || "";
const googleClientSecret = process.env.SHULKR_GOOGLE_CLIENT_SECRET || "";
const configuredJwtSecret = process.env.SHULKR_JWT_SECRET || "";
const stripeSecretKey = process.env.SHULKR_STRIPE_SECRET_KEY || "";
const stripePublishableKey = process.env.SHULKR_STRIPE_PUBLISHABLE_KEY || "";
const stripeWebhookSecret = process.env.SHULKR_STRIPE_WEBHOOK_SECRET || "";
const controlCommands = [];
const controlActivity = [];
const analyticsHistoryPath = path.join(dataDir, "analytics-history.json");
const commandStatsIndex = new Map();
const stripe = stripeSecretKey ? new Stripe(stripeSecretKey) : null;
const stripePlanCatalog = {
  pro: {
    key: "pro",
    tier: "Pro",
    lookupKey: "shulkr_pro_monthly",
    name: "Shulkr Pro",
    amount: 999,
    currency: "usd",
    interval: "month",
    description: "Remote dashboard, templates, and client module access."
  },
  premium: {
    key: "premium",
    tier: "Premium",
    lookupKey: "shulkr_premium_monthly",
    name: "Shulkr Premium",
    amount: 1999,
    currency: "usd",
    interval: "month",
    description: "Everything in Pro plus publish/import power features."
  }
};
const streamState = {
  process: null,
  clients: new Set(),
  startedAt: null,
  lastError: "",
  mode: "window",
  fps: 20,
  captureRect: null,
  captureTitle: "",
  ownerId: "",
  clientId: ""
};

function loadServerSecret() {
  if (configuredJwtSecret) {
    if (Buffer.byteLength(configuredJwtSecret, "utf8") < 32) throw new Error("SHULKR_JWT_SECRET must be at least 32 bytes");
    return configuredJwtSecret;
  }
  const secretPath = path.join(dataDir, ".server-secret");
  fsSync.mkdirSync(dataDir, { recursive: true });
  try {
    const existing = fsSync.readFileSync(secretPath, "utf8").trim();
    if (existing.length >= 32) return existing;
  } catch {
  }
  const generated = crypto.randomBytes(48).toString("base64url");
  fsSync.writeFileSync(secretPath, generated, { encoding: "utf8", mode: 0o600 });
  return generated;
}

const authSecret = loadServerSecret();
const allowedOrigins = new Set([
  backendUrl,
  webClientUrl,
  `http://127.0.0.1:${port}`,
  `http://localhost:${port}`,
  "http://127.0.0.1:5177",
  "http://localhost:5177",
  "http://127.0.0.1:5178",
  "http://localhost:5178",
  ...String(process.env.SHULKR_ALLOWED_ORIGINS || "").split(",").map(value => value.trim()).filter(Boolean)
].map(value => value.replace(/\/$/, "")));

function originAllowed(origin) {
  if (!origin) return true;
  try { return allowedOrigins.has(new URL(origin).origin); } catch { return false; }
}

const app = express();
app.disable("x-powered-by");
app.use((req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
  res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
  res.setHeader("Cross-Origin-Resource-Policy", "same-site");
  if (req.path.startsWith("/api/") || req.path.startsWith("/auth/")) res.setHeader("Cache-Control", "no-store");
  res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com 'unsafe-eval'; style-src 'self' https://fonts.googleapis.com https://cdnjs.cloudflare.com 'unsafe-inline'; font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com data:; img-src 'self' data: blob: https:; connect-src 'self' http://127.0.0.1:* http://localhost:* ws://127.0.0.1:* ws://localhost:*; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
  next();
});
app.use(cors({
  credentials: true,
  origin(origin, callback) {
    if (originAllowed(origin)) return callback(null, true);
    const error = new Error("Origin is not allowed");
    error.status = 403;
    return callback(error);
  }
}));
app.use(express.json({
  limit: "3mb",
  verify: (req, _res, buffer) => {
    req.rawBody = buffer;
  }
}));
app.use(express.text({ type: "text/*", limit: "3mb" }));
app.use("/assets/icons", express.static(iconAssetsDir));
app.use(session({
  secret: authSecret,
  resave: false,
  saveUninitialized: false,
  cookie: { httpOnly: true, secure: backendUrl.startsWith("https://"), sameSite: "lax" }
}));
app.use(passport.initialize());
app.use(passport.session());

passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((obj, done) => done(null, obj));

if (googleClientId && googleClientSecret) {
  passport.use(new GoogleStrategy({
    clientID: googleClientId,
    clientSecret: googleClientSecret,
    callbackURL: `${backendUrl}/auth/google/callback`
  }, (accessToken, refreshToken, profile, done) => {
    const email = profile.emails?.[0]?.value || `${profile.id}@google.local`;
    const user = {
      id: profile.id,
      displayName: profile.displayName || email.split("@")[0],
      email,
      avatar: profile.photos?.[0]?.value || "",
      provider: "google"
    };
    return done(null, user);
  }));
}

const scriptExtensions = new Set([".py", ".pyj", ".lua", ".js", ".txt"]);
const hiddenRoots = new Set(["system", "templates", "plugins", "plugins_disabled", "exports", "blockpacks", "automations", "shulkr_runtime", "shulkr_config"]);
const tierCatalog = {
  free: {
    tier: "Free",
    features: [
      "client",
      "editor",
      "libraries.read",
      "scripts.read",
      "scripts.write",
      "stats.read",
      "remote.control"
      ,"automations.read","automations.write"
    ]
  },
  pro: {
    tier: "Pro",
    features: [
      "client",
      "editor",
      "libraries.read",
      "scripts.read",
      "scripts.write",
      "stats.read",
      "remote.control",
      "remote.stream",
      "templates.read",
      "templates.write",
      "client-modules.read"
      ,"automations.read","automations.write"
    ]
  },
  premium: {
    tier: "Premium",
    features: [
      "client",
      "editor",
      "libraries.read",
      "libraries.write",
      "script-library",
      "script-library.publish",
      "scripts.read",
      "scripts.write",
      "scripts.delete",
      "templates.read",
      "templates.write",
      "client-modules.read",
      "client-modules.write",
      "stats.read",
      "remote.control",
      "remote.stream"
      ,"automations.read","automations.write"
    ]
  },
  admin: {
    tier: "Admin",
    features: [
      "admin",
      "client",
      "editor",
      "libraries.read",
      "libraries.write",
      "script-library",
      "script-library.publish",
      "script-library.delete",
      "scripts.read",
      "scripts.write",
      "scripts.delete",
      "templates.read",
      "templates.write",
      "client-modules.read",
      "client-modules.write",
      "licenses.read",
      "licenses.write",
      "stats.read",
      "remote.control",
      "remote.stream"
      ,"automations.read","automations.write"
    ]
  }
};
const STREAM_COOKIE = "shulkr_stream_session";
const AUTH_COOKIE = "shulkr_auth_session";
const AUTH_WINDOW_MS = 15 * 60 * 1000;
const AUTH_MAX_FAILURES = 5;
const authFailures = new Map();
const oauthExchangeCodes = new Map();
const automationExecutions = new Map();

async function ensureReady() {
  if (!process.env.SHULKR_DATA_DIR && !process.env.SHULK_DATA_DIR && !process.env.FLUXUS_DATA_DIR && !fsSync.existsSync(dataDir) && fsSync.existsSync(legacyDataDir)) {
    await fs.cp(legacyDataDir, dataDir, { recursive: true });
  }
  await fs.mkdir(dataDir, { recursive: true });
  await fs.mkdir(scriptDir, { recursive: true });
  await seedJson(profilePath, path.join(resourcesDataDir, "profile.json"), defaultProfile());
  await seedJson(librariesPath, path.join(resourcesDataDir, "modules.json"), defaultLibraries());
  await seedJson(clientModulesPath, "", defaultClientModules());
  await syncClientModules();
  await seedJson(templatesPath, path.join(resourcesDataDir, "templates.json"), defaultTemplates());
  await ensureAutomationTemplates();
  await seedJson(libraryScriptsPath, "", []);
  await seedJson(libraryScriptPathsPath, "", defaultLibraryScriptPaths());
  await cleanLegacyAutoModules();
  await seedJson(clientsPath, "", []);
  await seedJson(licensesPath, "", defaultLicenses());
  await seedJson(tamperEventsPath, "", []);
  await seedJson(libraryReportsPath, "", []);
  await migrateAutomationStore();
  await seedLocalUsers();
  await seedAnalyticsHistory();
}

async function ensureAutomationTemplates() {
  const current = await readJson(templatesPath, []);
  const defaults = defaultTemplates().filter(template => template.kind === "automation");
  const next = Array.isArray(current) ? [...current] : [];
  for (const template of defaults) if (!next.some(item => item.id === template.id)) next.push(template);
  if (next.length !== (Array.isArray(current) ? current.length : 0)) await writeJson(templatesPath, next);
}

async function seedAnalyticsHistory() {
  if (fsSync.existsSync(analyticsHistoryPath)) return;
  await writeJson(analyticsHistoryPath, []);
}

async function seedLocalUsers() {
  const users = await readJson(usersPath, []);
  const adminIndex = users.findIndex((u) => u.id === "admin-user" || normalizeTierName(u.tier) === "admin");
  const bootstrapPassword = String(process.env.SHULKR_ADMIN_PASSWORD || "");
  if (adminIndex < 0 && bootstrapPassword) {
    assertStrongPassword(bootstrapPassword);
    users.push({
      id: "admin-user",
      displayName: "Admin",
      username: "admin",
      email: "admin@shulkr.local",
      passwordHash: hashPassword(bootstrapPassword),
      provider: "local",
      tier: "Admin",
      createdAt: new Date().toISOString()
    });
  } else if (adminIndex >= 0) {
    users[adminIndex] = {
      ...users[adminIndex],
      username: users[adminIndex].username || "admin",
      tier: "Admin"
    };
  }
  const securedUsers = users.map((user) => {
    if (user.passwordHash || typeof user.password !== "string") return user;
    const { password, ...safeUser } = user;
    return { ...safeUser, passwordHash: hashPassword(password) };
  });
  await writeJson(usersPath, securedUsers);
}

function assertStrongPassword(password) {
  const value = String(password || "");
  if (value.length < 10 || !/[a-z]/.test(value) || !/[A-Z]/.test(value) || !/\d/.test(value)) {
    throw new Error("Password must be at least 10 characters and include upper-case, lower-case, and numeric characters");
  }
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(String(password), salt, 64);
  return `scrypt$${salt.toString("base64url")}$${hash.toString("base64url")}`;
}

function verifyPassword(user, password) {
  const stored = String(user?.passwordHash || "");
  if (stored.startsWith("scrypt$")) {
    const [, saltValue, hashValue] = stored.split("$");
    try {
      const expected = Buffer.from(hashValue, "base64url");
      const actual = crypto.scryptSync(String(password), Buffer.from(saltValue, "base64url"), expected.length);
      return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
    } catch {
      return false;
    }
  }
  const legacy = Buffer.from(String(user?.password || ""));
  const supplied = Buffer.from(String(password || ""));
  return legacy.length > 0 && legacy.length === supplied.length && crypto.timingSafeEqual(legacy, supplied);
}

async function findLocalUser(login) {
  const users = await readJson(usersPath, []);
  const value = String(login || "").trim().toLowerCase();
  return users.find((u) => [u.email, u.username].filter(Boolean).some((entry) => String(entry).trim().toLowerCase() === value)) || null;
}

async function createLocalUser(displayName, email, password) {
  const users = await readJson(usersPath, []);
  const normalizedEmail = String(email || "").trim().toLowerCase();
  if (normalizedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) throw new Error("A valid email address is required");
  if (users.some((u) => String(u.email || "").trim().toLowerCase() === normalizedEmail)) {
    throw new Error("An account with that email already exists");
  }
  assertStrongPassword(password);
  const safeDisplayName = String(displayName || normalizedEmail.split("@")[0]).trim().replace(/[\u0000-\u001F\u007F]/g, "").slice(0, 80);
  const usernameBase = slug(safeDisplayName || normalizedEmail.split("@")[0] || `user-${Date.now()}`);
  let username = usernameBase;
  let suffix = 2;
  while (users.some(existing => String(existing.username || "").toLowerCase() === username.toLowerCase())) username = `${usernameBase}-${suffix++}`;
  const user = {
    id: `local-${Date.now()}`,
    displayName: safeDisplayName || "Shulkr User",
    username,
    email: normalizedEmail,
    passwordHash: hashPassword(password),
    provider: "local",
    tier: "Free",
    createdAt: new Date().toISOString()
  };
  users.push(user);
  await writeJson(usersPath, users);
  return user;
}

async function syncLocalProfile(user) {
  const current = await readJson(profilePath, defaultProfile());
  const entitlements = await resolveEntitlements(user);
  const next = {
    ...current,
    id: user.id,
    displayName: user.displayName,
    email: user.email,
    username: user.username || current.username || user.displayName,
    tier: entitlements.tier,
    features: entitlements.features,
    connected: true,
    lastSeenAt: new Date().toISOString()
  };
  await writeJson(profilePath, next);
  await upsertLicenseForUser(user, entitlements);
  return next;
}

async function seedJson(target, source, fallback) {
  try {
    await fs.access(target);
  } catch {
    try {
      await fs.copyFile(source, target);
    } catch {
      await writeJson(target, fallback);
    }
  }
}

function defaultProfile() {
  const now = new Date().toISOString();
  return {
    id: "local-user",
    displayName: "EnderUser",
    tier: "Premium",
    features: tierCatalog.premium.features,
    avatar: "user-solid.png",
    activePage: "Dashboard",
    connected: true,
    uiScale: 100,
    status: "Ready",
    createdAt: now,
    lastSeenAt: now
  };
}

function defaultTemplates() {
  const node = (id, type, x, y, data = {}) => ({ id, type, version: 1, position: { x, y }, data });
  const edge = (id, source, sourceHandle, target, targetHandle, dataType = "execution") => ({ id, source, sourceHandle, target, targetHandle, dataType });
  const graph = (id, name, description, nodes, edges, permissions) => ({ formatVersion: 1, id: `template-${id}`, name, description, nodes, edges, viewport: { x: 0, y: 0, zoom: 1 }, generatedCode: "", requiredPermissions: permissions, supportedMinecraftVersions: ["1.20+"] , supportedClientVersion: "1.0.0" });
  const automation = (id, name, description, nodes, edges, permissions, tags = []) => ({ id, name, category: "Visual Automation", description, difficulty: "Intermediate", blocks: nodes.length, icon: "diagram-project-solid.png", badge: "Automation", script: "", kind: "automation", graph: graph(id, name, description, nodes, edges, permissions), permissions, requiredPermissions: permissions, supportedMinecraftVersions: ["1.20+"], supportedClientVersion: "1.0.0", version: "1.0.0", changelog: "Initial template", tags });
  return [
    automation("walk-to-coordinates", "Walk To Coordinates", "Walk safely to a fixed Minecraft coordinate.", [node("start", "flow.start", 0, 0), node("walk", "movement.walk_to_coordinates", 220, 0, { coordinates: { x: 0, y: 64, z: 0 }, timeout: 60 }), node("stop", "flow.stop", 440, 0)], [edge("e1", "start", "execution-out", "walk", "execution-in"), edge("e2", "walk", "execution-out", "stop", "execution-in")], ["player_movement"], ["movement", "coordinates"]),
    automation("tree-miner", "Tree Miner", "Find the nearest oak log, look at it, and mine it with bounded retries.", [node("start", "flow.start", 0, 0), node("find", "world.find_nearest_block", 220, 0, { blockId: "minecraft:oak_log", radius: 48 }), node("mine", "action.mine_block", 440, 0, { retries: 3, timeout: 20 }), node("stop", "flow.stop", 660, 0)], [edge("e1", "start", "execution-out", "find", "execution-in"), edge("e2", "find", "execution-out", "mine", "execution-in"), edge("e3", "find", "target-out", "mine", "target-in", "target"), edge("e4", "mine", "execution-out", "stop", "execution-in")], ["player_movement", "block_interaction", "block_breaking"], ["mining", "blocks"]),
    automation("mine-nearest-block", "Mine Nearest Block", "Locate and mine the nearest configured block type.", [node("start", "flow.start", 0, 0), node("find", "world.find_nearest_block", 220, 0, { blockId: "minecraft:stone", radius: 32 }), node("mine", "action.mine_block", 440, 0, { retries: 2, timeout: 15 }), node("stop", "flow.stop", 660, 0)], [edge("e1", "start", "execution-out", "find", "execution-in"), edge("e2", "find", "execution-out", "mine", "execution-in"), edge("e3", "find", "target-out", "mine", "target-in", "target"), edge("e4", "mine", "execution-out", "stop", "execution-in")], ["block_interaction", "block_breaking"], ["mining", "blocks"]),
    automation("patrol-between-coordinates", "Patrol Between Coordinates", "Walk between two safe coordinate checkpoints with a delay.", [node("start", "flow.start", 0, 0), node("first", "movement.walk_to_coordinates", 220, 0, { coordinates: { x: 0, y: 64, z: 0 }, timeout: 60 }), node("delay", "flow.delay", 440, 0, { duration: 2 }), node("second", "movement.walk_to_coordinates", 660, 0, { coordinates: { x: 10, y: 64, z: 10 }, timeout: 60 }), node("stop", "flow.stop", 880, 0)], [edge("e1", "start", "execution-out", "first", "execution-in"), edge("e2", "first", "execution-out", "delay", "execution-in"), edge("e3", "delay", "execution-out", "second", "execution-in"), edge("e4", "second", "execution-out", "stop", "execution-in")], ["player_movement"], ["movement", "patrol"]),
    automation("find-and-look-at-block", "Find And Look At Block", "Find the nearest configured block and look at it without breaking it.", [node("start", "flow.start", 0, 0), node("find", "world.find_nearest_block", 220, 0, { blockId: "minecraft:diamond_ore", radius: 48 }), node("look", "action.look_at_target", 440, 0), node("stop", "flow.stop", 660, 0)], [edge("e1", "start", "execution-out", "find", "execution-in"), edge("e2", "find", "execution-out", "look", "execution-in"), edge("e3", "find", "target-out", "look", "target-in", "target"), edge("e4", "look", "execution-out", "stop", "execution-in")], ["camera_control", "entity_targeting"], ["world", "observation"]),
    automation("repeat-action-with-delay", "Repeat Action With Delay", "Repeat a bounded search and mine action with a safe delay between attempts.", [node("start", "flow.start", 0, 0), node("repeat", "flow.repeat", 220, 0, { iterations: 3 }), node("delay", "flow.delay", 440, 80, { duration: 2 }), node("find", "world.find_nearest_block", 660, 80, { blockId: "minecraft:oak_log", radius: 32 }), node("mine", "action.mine_block", 880, 80, { retries: 2, timeout: 15 }), node("stop", "flow.stop", 440, -100)], [edge("e1", "start", "execution-out", "repeat", "execution-in"), edge("e2", "repeat", "loop-out", "delay", "execution-in"), edge("e3", "delay", "execution-out", "find", "execution-in"), edge("e4", "find", "execution-out", "mine", "execution-in"), edge("e5", "find", "target-out", "mine", "target-in", "target"), edge("e6", "mine", "execution-out", "repeat", "execution-in"), edge("e7", "repeat", "done-out", "stop", "execution-in")], ["block_interaction", "block_breaking"], ["loops", "mining"]),
    {
      id: "starter",
      name: "Starter Script",
      category: "Utility",
      description: "A tiny Minescript starter.",
      difficulty: "Easy",
      blocks: 3,
      icon: "code-solid.png",
      badge: "New",
      script: "import minescript as ms\n\nms.echo(\"Starter loaded\")\n"
    },
    {
      id: "pathfinder-route",
      name: "Pathfinder Route",
      category: "Movement",
      description: "Build a route from your current position to the block under your crosshair.",
      difficulty: "Advanced",
      blocks: 11,
      icon: "person-walking-dashed-line-arrow-right-solid.png",
      badge: "New",
      script: "import minescript as ms\n\nms.echo(\"Pathfinder Route scaffold\")\n"
    }
  ];
}

function defaultLicenses() {
  return [
    {
      id: "local-premium",
      userId: "local-user",
      displayName: "EnderUser",
      tier: "Premium",
      status: "active",
      seats: 1,
      deviceLimit: 1,
      hardwareLock: true,
      boundDeviceIds: [],
      expiresAt: null,
      features: tierCatalog.premium.features
    }
  ];
}

function normalizeTierName(tier) {
  const key = String(tier || "premium").trim().toLowerCase();
  if (key === "admin") return "admin";
  if (key === "pro") return "pro";
  if (key === "free") return "free";
  return "premium";
}

function featureSetForTier(tier) {
  const normalized = normalizeTierName(tier);
  return [...new Set((tierCatalog[normalized]?.features || tierCatalog.premium.features).map(String))];
}

function userIsAdmin(user) {
  if (!user) return false;
  return normalizeTierName(user.tier) === "admin";
}

async function resolveEntitlements(user) {
  const licenses = await readJson(licensesPath, defaultLicenses());
  const activeLicenses = licenses.filter((license) => license.userId === user.id && String(license.status || "active").toLowerCase() === "active");
  const chosenTier = userIsAdmin(user)
    ? "admin"
    : normalizeTierName(activeLicenses[0]?.tier || user.tier || "premium");
  const features = new Set(featureSetForTier(chosenTier));
  for (const license of activeLicenses) {
    for (const feature of Array.isArray(license.features) ? license.features : []) {
      features.add(String(feature));
    }
  }
  if (chosenTier === "admin") features.add("admin");
  return {
    tier: tierCatalog[chosenTier].tier,
    features: [...features].sort(),
    isAdmin: chosenTier === "admin"
  };
}

async function upsertLicenseForUser(user, entitlements) {
  const licenses = await readJson(licensesPath, defaultLicenses());
  const index = licenses.findIndex((license) => license.userId === user.id);
  const nextLicense = {
    id: licenses[index]?.id || `${slug(user.username || user.displayName || user.id)}-${user.id}`,
    userId: user.id,
    displayName: user.displayName,
    tier: entitlements.tier,
    status: "active",
    seats: 1,
    deviceLimit: Number(licenses[index]?.deviceLimit || licenses[index]?.seats || 1) || 1,
    hardwareLock: licenses[index]?.hardwareLock !== false,
    boundDeviceIds: Array.isArray(licenses[index]?.boundDeviceIds) ? licenses[index].boundDeviceIds : [],
    primaryDeviceId: licenses[index]?.primaryDeviceId || null,
    primaryDeviceName: licenses[index]?.primaryDeviceName || null,
    lastDeviceSeenAt: licenses[index]?.lastDeviceSeenAt || null,
    expiresAt: null,
    features: entitlements.features
  };
  if (index >= 0) {
    licenses[index] = { ...licenses[index], ...nextLicense };
  } else {
    licenses.push(nextLicense);
  }
  await writeJson(licensesPath, licenses);
}

async function recordTamperEvent(type, detail = {}) {
  try {
    const events = await readJson(tamperEventsPath, []);
    const next = {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      type: String(type || "tamper"),
      severity: String(detail.severity || "warn"),
      message: String(detail.message || type || "Tamper event"),
      at: new Date().toISOString(),
      actorId: detail.actorId || null,
      actorEmail: detail.actorEmail || null,
      route: detail.route || null,
      ip: detail.ip || null,
      metadata: detail.metadata && typeof detail.metadata === "object" ? detail.metadata : {}
    };
    events.unshift(next);
    await writeJson(tamperEventsPath, events.slice(0, 250));
  } catch (error) {
    console.error("recordTamperEvent error", error);
  }
}

async function adminOverview() {
  const [users, licenses, tamperEvents, clients, analytics] = await Promise.all([
    readJson(usersPath, []),
    readJson(licensesPath, defaultLicenses()),
    readJson(tamperEventsPath, []),
    connectedClients(),
    analyticsResponse({ range: "30d" })
  ]);
  const recentTamperEvents = Array.isArray(tamperEvents) ? tamperEvents.slice(0, 30) : [];
  return {
    summary: {
      users: users.length,
      admins: users.filter(userIsAdmin).length,
      activeLicenses: licenses.filter((license) => String(license.status || "").toLowerCase() === "active").length,
      connectedClients: clients.filter((client) => client.connected !== false).length,
      tamperEvents: recentTamperEvents.length,
      blockedAttempts: recentTamperEvents.filter((event) => event.severity === "error").length
    },
    analytics: analytics.summary,
    users: users.map((user) => ({
      id: user.id,
      displayName: user.displayName,
      username: user.username || "",
      email: user.email,
      provider: user.provider || "local",
      tier: userIsAdmin(user) ? "Admin" : (user.tier || "Premium"),
      isAdmin: userIsAdmin(user),
      createdAt: user.createdAt || null
    })),
    licenses,
    tamperEvents: recentTamperEvents
  };
}

async function readUsers() {
  const users = await readJson(usersPath, []);
  return Array.isArray(users) ? users : [];
}

async function saveUsers(users) {
  await writeJson(usersPath, users);
}

async function updateStoredUser(userId, updater) {
  const users = await readUsers();
  const index = users.findIndex((user) => user.id === userId);
  if (index < 0) return null;
  users[index] = typeof updater === "function" ? updater(users[index]) : { ...users[index], ...updater };
  await saveUsers(users);
  return users[index];
}

function stripeReady() {
  return Boolean(stripe && stripePublishableKey);
}

async function ensureStripePlans() {
  if (!stripe) throw new Error("Stripe is not configured on the backend");
  const entries = await Promise.all(Object.values(stripePlanCatalog).map(async (plan) => {
    const existingPrices = await stripe.prices.list({
      lookup_keys: [plan.lookupKey],
      active: true,
      expand: ["data.product"],
      limit: 1
    });
    if (existingPrices.data[0]) {
      return {
        ...plan,
        priceId: existingPrices.data[0].id,
        productId: typeof existingPrices.data[0].product === "string" ? existingPrices.data[0].product : existingPrices.data[0].product?.id
      };
    }
    const product = await stripe.products.create({
      name: plan.name,
      description: plan.description,
      metadata: { tier: plan.key, app: "shulkr" }
    });
    const price = await stripe.prices.create({
      product: product.id,
      unit_amount: plan.amount,
      currency: plan.currency,
      recurring: { interval: plan.interval },
      lookup_key: plan.lookupKey,
      metadata: { tier: plan.key, app: "shulkr" }
    });
    return { ...plan, priceId: price.id, productId: product.id };
  }));
  return entries;
}

async function billingPlansResponse() {
  const plans = Object.values(stripePlanCatalog).map((plan) => ({
    id: plan.key,
    tier: plan.tier,
    amount: plan.amount,
    currency: plan.currency,
    interval: plan.interval,
    description: plan.description
  }));
  if (!stripe) {
    return { enabled: false, publishableKey: "", plans };
  }
  const ensured = await ensureStripePlans();
  return {
    enabled: true,
    publishableKey: stripePublishableKey,
    plans: ensured.map((plan) => ({
      id: plan.key,
      tier: plan.tier,
      amount: plan.amount,
      currency: plan.currency,
      interval: plan.interval,
      description: plan.description,
      priceId: plan.priceId
    }))
  };
}

async function ensureStripeCustomer(user) {
  if (!stripe) throw new Error("Stripe is not configured on the backend");
  if (user.stripeCustomerId) {
    return user.stripeCustomerId;
  }
  const customer = await stripe.customers.create({
    email: user.email,
    name: user.displayName,
    metadata: {
      userId: user.id,
      username: user.username || ""
    }
  });
  await updateStoredUser(user.id, { stripeCustomerId: customer.id });
  return customer.id;
}

function tierFromStripeLookup(value) {
  const normalized = String(value || "").toLowerCase();
  if (normalized.includes("premium")) return "Premium";
  if (normalized.includes("pro")) return "Pro";
  return "Free";
}

async function upsertLicenseForTier(userId, tier, metadata = {}) {
  const users = await readUsers();
  const user = users.find((entry) => entry.id === userId);
  if (!user) return null;
  const entitlements = await resolveEntitlements({ ...user, tier });
  await updateStoredUser(userId, {
    tier: entitlements.tier,
    stripeCustomerId: metadata.customerId || user.stripeCustomerId || "",
    stripeSubscriptionId: metadata.subscriptionId || user.stripeSubscriptionId || "",
    stripePriceId: metadata.priceId || user.stripePriceId || "",
    stripeStatus: metadata.status || user.stripeStatus || ""
  });
  await upsertLicenseForUser({ ...user, tier: entitlements.tier }, entitlements);
  return entitlements;
}

async function syncStripeSubscription(subscription) {
  const customerId = typeof subscription.customer === "string" ? subscription.customer : subscription.customer?.id;
  const priceId = subscription.items?.data?.[0]?.price?.id || "";
  const lookupKey = subscription.items?.data?.[0]?.price?.lookup_key || "";
  const users = await readUsers();
  const user = users.find((entry) => entry.stripeCustomerId === customerId);
  if (!user) {
    await recordTamperEvent("stripe.unknown_customer", {
      severity: "warn",
      message: "Stripe webhook received for unknown customer",
      metadata: { customerId, subscriptionId: subscription.id }
    });
    return null;
  }
  const active = new Set(["active", "trialing"]);
  const nextTier = active.has(String(subscription.status || "").toLowerCase())
    ? tierFromStripeLookup(lookupKey || subscription.metadata?.tier || "")
    : "Free";
  return upsertLicenseForTier(user.id, nextTier, {
    customerId,
    subscriptionId: subscription.id,
    priceId,
    status: subscription.status || ""
  });
}

async function billingStatusForUser(user) {
  const licenses = await readJson(licensesPath, defaultLicenses());
  const license = licenses.find((entry) => entry.userId === user.id) || null;
  const plans = await billingPlansResponse();
  return {
    enabled: stripeReady(),
    publishableKey: plans.publishableKey || "",
    currentTier: user.tier || "Free",
    isAdmin: Boolean(user.isAdmin),
    features: user.features || [],
    stripeCustomerId: user.stripeCustomerId || "",
    stripeSubscriptionId: user.stripeSubscriptionId || "",
    stripeStatus: user.stripeStatus || "",
    currentLicense: license,
    plans: plans.plans
  };
}

function defaultLibraries() {
  return [
    {
      id: "nbtlib",
      name: "nbtlib",
      author: "jasonperlow",
      version: "v2.3.5",
      description: "Read, write and modify Minecraft NBT data.",
      category: "Data",
      icon: "box-solid.png",
      status: "Installed",
      installed: true,
      favorite: true
    },
    {
      id: "matplotlib",
      name: "matplotlib",
      author: "matplotlib",
      version: "v3.8.2",
      description: "Create static, animated and interactive visualizations.",
      category: "Visualization",
      icon: "circle-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "requests",
      name: "requests",
      author: "psf",
      version: "v2.31.0",
      description: "HTTP for Humans.",
      category: "Web",
      icon: "arrows-rotate-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "numpy",
      name: "numpy",
      author: "numpy",
      version: "v1.26.4",
      description: "Fundamental package for scientific computing.",
      category: "Data",
      icon: "box-open-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "pillow",
      name: "pillow",
      author: "python-pillow",
      version: "v10.2.0",
      description: "Python Imaging Library for image processing.",
      category: "Utility",
      icon: "cloud-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "pandas",
      name: "pandas",
      author: "pandas-dev",
      version: "v2.2.1",
      description: "Powerful data structures for data analysis.",
      category: "Data",
      icon: "border-all-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "rich",
      name: "rich",
      author: "Textualize",
      version: "v13.7.1",
      description: "Rich text and beautiful formatting in the console.",
      category: "Utility",
      icon: "code-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "aiohttp",
      name: "aiohttp",
      author: "aio-libs",
      version: "v3.9.3",
      description: "Asynchronous HTTP client/server framework.",
      category: "Web",
      icon: "circle-info-solid.png",
      status: "Not Installed",
      installed: false,
      favorite: false
    }
  ];
}

function defaultClientModules() {
  return [
    {
      id: "web-dashboard",
      name: "Web Dashboard",
      author: "Shulkr",
      version: "0.1.0",
      description: "Remotely control scripts, overlays, and the connected Minecraft client from a local web dashboard.",
      category: "Dashboard",
      icon: "window-restore-regular.png",
      status: "Available",
      installed: false,
      favorite: true,
      openUrl: "http://127.0.0.1:50991/web/"
    },
    {
      id: "backend-admin",
      name: "Backend Admin",
      author: "Shulk",
      version: "1.0.0",
      description: "Open the local Express control surface for scripts, licenses, and the module catalog.",
      category: "Admin",
      icon: "gear-solid.png",
      status: "Available",
      installed: true,
      favorite: false,
      openUrl: "http://127.0.0.1:50991/admin/"
    },
    {
      id: "script-hub",
      name: "Script Hub",
      author: "Shulk",
      version: "1.0.0",
      description: "Jump into the browser-based script workspace and published library tools.",
      category: "Workspace",
      icon: "house-solid.png",
      status: "Available",
      installed: true,
      favorite: false,
      openUrl: "http://127.0.0.1:50991/web/"
    }
  ];
}

async function syncClientModules() {
  const defaults = defaultClientModules();
  const current = await readJson(clientModulesPath, []);
  const existing = Array.isArray(current) ? current : [];
  const merged = defaults.map((fallback) => {
    const saved = existing.find((item) => item.id === fallback.id);
    return saved ? { ...fallback, ...saved, openUrl: fallback.openUrl } : fallback;
  });
  for (const item of existing) {
    if (!merged.some((candidate) => candidate.id === item.id)) merged.push(item);
  }
  await writeJson(clientModulesPath, merged);
}

function addControlActivity(action, detail, status = "ok") {
  controlActivity.unshift({ id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, action, detail, status, at: new Date().toISOString() });
  controlActivity.splice(80);
}

function queueControlCommand({ clientId, type, payload = {}, userId = "", executionId = "" }) {
  const command = {
    id: crypto.randomUUID(),
    clientId: String(clientId || "local-user"),
    userId: String(userId || ""),
    executionId: String(executionId || ""),
    type,
    payload: payload && typeof payload === "object" ? payload : {},
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 30000).toISOString()
  };
  controlCommands.push(command);
  commandStatsIndex.set(command.id, command);
  return command;
}

function publicControlCommand(command) {
  return {
    id: command.id,
    clientId: command.clientId,
    type: command.type,
    payload: command.payload,
    createdAt: command.createdAt,
    expiresAt: command.expiresAt
  };
}

async function normalizeControlPayload(type, input) {
  const payload = input && typeof input === "object" && !Array.isArray(input) ? input : {};
  if (type === "run_script") {
    const file = scriptPathFromRequest(payload.path);
    await fs.access(file);
    return prepareConfiguredScriptExecution(file);
  }
  if (type === "send_chat") {
    const message = String(payload.message || "").trim();
    if (!message || message.length > 256 || /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(message)) throw automationError(422, "COMMAND_PAYLOAD_INVALID", "Chat message is invalid");
    return { message };
  }
  if (type === "set_overlay") {
    const name = String(payload.name || "").trim();
    if (!/^[A-Za-z0-9 _-]{1,50}$/.test(name)) throw automationError(422, "COMMAND_PAYLOAD_INVALID", "Overlay name is invalid");
    return { name, visible: payload.visible !== false };
  }
  if (type === "set_renderer") return { visible: payload.visible !== false };
  return {};
}

async function ownedConnectedClient(user, clientId) {
  const client = (await connectedClients()).find(candidate => candidate.id === String(clientId || ""));
  if (!client || client.connected === false) throw automationError(409, "CLIENT_NOT_CONNECTED", "The selected Minecraft client is not connected");
  if (!user?.isAdmin && String(client.licenseUserId || "") !== String(user?.id || "")) {
    throw automationError(403, "CLIENT_OWNERSHIP_REQUIRED", "The selected Minecraft client belongs to another account");
  }
  return client;
}

function publicAutomationExecution(execution) {
  if (!execution) return null;
  const { userId: _userId, scriptPath: _scriptPath, ...safe } = execution;
  return safe;
}

function updateAutomationExecution(executionId, patch, message = "") {
  const current = automationExecutions.get(String(executionId || ""));
  if (!current) return null;
  const next = { ...current, ...patch, updatedAt: new Date().toISOString() };
  if (message) next.logs = [...(current.logs || []), { at: next.updatedAt, message }].slice(-100);
  automationExecutions.set(next.id, next);
  return next;
}

function analyticsBucketStart(timestamp = Date.now()) {
  const bucket = new Date(timestamp);
  bucket.setHours(0, 0, 0, 0);
  return bucket.getTime();
}

function analyticsBucketId(timestamp = Date.now()) {
  return new Date(analyticsBucketStart(timestamp)).toISOString().slice(0, 10);
}

function createAnalyticsBucket(timestamp = Date.now()) {
  return {
    day: analyticsBucketId(timestamp),
    at: analyticsBucketStart(timestamp),
    runtimeSeconds: 0,
    heartbeats: 0,
    fpsTotal: 0,
    fpsSamples: 0,
    commandsQueued: 0,
    commandsCompleted: 0,
    scriptRuns: 0,
    chatMessages: 0,
    screenshots: 0,
    activeScriptSeconds: 0,
    activeClients: 0,
    byClient: {},
    byScript: {}
  };
}

function ensureClientBucket(bucket, clientId, displayName = "") {
  const id = String(clientId || "local-user");
  if (!bucket.byClient[id]) {
    bucket.byClient[id] = {
      id,
      displayName: displayName || id,
      runtimeSeconds: 0,
      heartbeats: 0,
      fpsTotal: 0,
      fpsSamples: 0,
      commandsQueued: 0,
      commandsCompleted: 0,
      scriptRuns: 0,
      chatMessages: 0,
      screenshots: 0,
      activeScriptSeconds: 0
    };
  }
  if (displayName) bucket.byClient[id].displayName = displayName;
  return bucket.byClient[id];
}

function ensureScriptBucket(bucket, scriptPath) {
  const pathKey = normalizeSlashes(scriptPath || "");
  if (!pathKey) return null;
  if (!bucket.byScript[pathKey]) {
    bucket.byScript[pathKey] = {
      path: pathKey,
      name: path.basename(pathKey),
      runtimeSeconds: 0,
      runs: 0,
      clients: {}
    };
  }
  return bucket.byScript[pathKey];
}

async function readAnalyticsHistory() {
  const history = await readJson(analyticsHistoryPath, []);
  return Array.isArray(history) ? history : [];
}

async function updateAnalyticsHistory(mutator) {
  const history = await readAnalyticsHistory();
  const nextHistory = Array.isArray(history) ? history : [];
  await mutator(nextHistory);
  nextHistory.sort((a, b) => Number(a.at || 0) - Number(b.at || 0));
  await writeJson(analyticsHistoryPath, nextHistory.slice(-365));
}

async function recordHeartbeatAnalytics(input, activeClients) {
  const runtimeSeconds = 5;
  const now = Date.now();
  await updateAnalyticsHistory(async (history) => {
    const day = analyticsBucketId(now);
    let bucket = history.find((item) => item.day === day);
    if (!bucket) {
      bucket = createAnalyticsBucket(now);
      history.push(bucket);
    }
    bucket.runtimeSeconds += runtimeSeconds;
    bucket.heartbeats += 1;
    bucket.activeClients = Math.max(Number(bucket.activeClients || 0), Number(activeClients || 0));
    const fps = Math.max(0, Number(input?.fps || 0));
    if (fps > 0) {
      bucket.fpsTotal += fps;
      bucket.fpsSamples += 1;
    }
    const client = ensureClientBucket(bucket, input?.id, input?.displayName);
    client.runtimeSeconds += runtimeSeconds;
    client.heartbeats += 1;
    if (fps > 0) {
      client.fpsTotal += fps;
      client.fpsSamples += 1;
    }
    const activeScript = normalizeSlashes(String(input?.activeScript || ""));
    if (activeScript) {
      bucket.activeScriptSeconds += runtimeSeconds;
      client.activeScriptSeconds += runtimeSeconds;
      const script = ensureScriptBucket(bucket, activeScript);
      if (script) {
        script.runtimeSeconds += runtimeSeconds;
        script.clients[client.id] = (script.clients[client.id] || 0) + runtimeSeconds;
      }
    }
  });
}

async function recordCommandQueuedAnalytics(command) {
  await updateAnalyticsHistory(async (history) => {
    const now = Date.now();
    const day = analyticsBucketId(now);
    let bucket = history.find((item) => item.day === day);
    if (!bucket) {
      bucket = createAnalyticsBucket(now);
      history.push(bucket);
    }
    bucket.commandsQueued += 1;
    const client = ensureClientBucket(bucket, command?.clientId, command?.clientId);
    client.commandsQueued += 1;
  });
}

async function recordCommandAckAnalytics(command, ok) {
  if (!command || !ok) return;
  await updateAnalyticsHistory(async (history) => {
    const now = Date.now();
    const day = analyticsBucketId(now);
    let bucket = history.find((item) => item.day === day);
    if (!bucket) {
      bucket = createAnalyticsBucket(now);
      history.push(bucket);
    }
    bucket.commandsCompleted += 1;
    const client = ensureClientBucket(bucket, command.clientId, command.clientId);
    client.commandsCompleted += 1;
    if (command.type === "run_script") {
      bucket.scriptRuns += 1;
      client.scriptRuns += 1;
      const script = ensureScriptBucket(bucket, command.payload?.path);
      if (script) {
        script.runs += 1;
      }
    } else if (command.type === "send_chat") {
      bucket.chatMessages += 1;
      client.chatMessages += 1;
    } else if (command.type === "take_screenshot") {
      bucket.screenshots += 1;
      client.screenshots += 1;
    }
  });
}

function summarizeAnalyticsPoint(point, filters = {}) {
  const clientId = String(filters.clientId || "all");
  const scriptPath = normalizeSlashes(filters.scriptPath || "");
  const clientFilter = clientId && clientId !== "all" ? clientId : "";
  const scriptFilter = scriptPath && scriptPath !== "all" ? scriptPath : "";
  const allowedClientIds = Array.isArray(filters.allowedClientIds) ? filters.allowedClientIds : null;
  const scopedClientIds = allowedClientIds === null ? null : (clientFilter ? [clientFilter] : allowedClientIds);
  const clientData = scopedClientIds === null
    ? (clientFilter ? point?.byClient?.[clientFilter] : null)
    : scopedClientIds.reduce((total, id) => {
      const value = point?.byClient?.[id] || {};
      for (const key of ["runtimeSeconds", "activeScriptSeconds", "scriptRuns", "commandsQueued", "commandsCompleted", "chatMessages", "screenshots", "heartbeats", "fpsTotal", "fpsSamples"]) {
        total[key] += Number(value[key] || 0);
      }
      return total;
    }, { runtimeSeconds: 0, activeScriptSeconds: 0, scriptRuns: 0, commandsQueued: 0, commandsCompleted: 0, chatMessages: 0, screenshots: 0, heartbeats: 0, fpsTotal: 0, fpsSamples: 0 });
  const scriptData = scriptFilter ? point?.byScript?.[scriptFilter] : null;
  const runtimeSeconds = scriptFilter
    ? Number(scriptData?.runtimeSeconds || 0)
    : clientData
      ? Number(clientData?.runtimeSeconds || 0)
      : Number(point?.runtimeSeconds || 0);
  const activeScriptSeconds = scriptFilter
    ? Number(scriptData?.runtimeSeconds || 0)
    : clientData
      ? Number(clientData?.activeScriptSeconds || 0)
      : Number(point?.activeScriptSeconds || 0);
  const scriptRuns = scriptFilter
    ? Number(scriptData?.runs || 0)
    : clientData
      ? Number(clientData?.scriptRuns || 0)
      : Number(point?.scriptRuns || 0);
  const commandsQueued = clientData ? Number(clientData?.commandsQueued || 0) : Number(point?.commandsQueued || 0);
  const commandsCompleted = clientData ? Number(clientData?.commandsCompleted || 0) : Number(point?.commandsCompleted || 0);
  const chatMessages = clientData ? Number(clientData?.chatMessages || 0) : Number(point?.chatMessages || 0);
  const screenshots = clientData ? Number(clientData?.screenshots || 0) : Number(point?.screenshots || 0);
  const heartbeats = scriptFilter ? Math.round(runtimeSeconds / 5) : clientData ? Number(clientData?.heartbeats || 0) : Number(point?.heartbeats || 0);
  const fpsTotal = clientData ? Number(clientData?.fpsTotal || 0) : Number(point?.fpsTotal || 0);
  const fpsSamples = clientData ? Number(clientData?.fpsSamples || 0) : Number(point?.fpsSamples || 0);
  const avgFps = fpsSamples > 0 ? fpsTotal / fpsSamples : 0;
  return {
    day: point?.day || analyticsBucketId(),
    at: Number(point?.at || Date.now()),
    runtimeSeconds,
    activeScriptSeconds,
    heartbeats,
    avgFps,
    commandsQueued,
    commandsCompleted,
    scriptRuns,
    chatMessages,
    screenshots,
    activeClients: scopedClientIds === null
      ? Number(point?.activeClients || 0)
      : scopedClientIds.filter(id => Number(point?.byClient?.[id]?.heartbeats || 0) > 0).length
  };
}

function applyAnalyticsRange(history, range = "30d") {
  const normalized = String(range || "30d");
  if (normalized === "All") return history;
  const days = { "7d": 7, "30d": 30, "90d": 90, "1yr": 365 }[normalized] || 30;
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  return history.filter((point) => Number(point.at || 0) >= cutoff);
}

async function analyticsResponse(filters = {}) {
  const history = applyAnalyticsRange(await readAnalyticsHistory(), filters.range);
  const points = history.map((point) => summarizeAnalyticsPoint(point, filters));
  const summary = points.reduce((acc, point) => {
    acc.runtimeSeconds += point.runtimeSeconds;
    acc.activeScriptSeconds += point.activeScriptSeconds;
    acc.heartbeats += point.heartbeats;
    acc.commandsQueued += point.commandsQueued;
    acc.commandsCompleted += point.commandsCompleted;
    acc.scriptRuns += point.scriptRuns;
    acc.chatMessages += point.chatMessages;
    acc.screenshots += point.screenshots;
    acc.activeClientsPeak = Math.max(acc.activeClientsPeak, point.activeClients);
    acc.fpsTotal += point.avgFps * (point.heartbeats || 0);
    acc.fpsSamples += point.heartbeats || 0;
    return acc;
  }, {
    runtimeSeconds: 0,
    activeScriptSeconds: 0,
    heartbeats: 0,
    commandsQueued: 0,
    commandsCompleted: 0,
    scriptRuns: 0,
    chatMessages: 0,
    screenshots: 0,
    activeClientsPeak: 0,
    fpsTotal: 0,
    fpsSamples: 0
  });
  const scriptCatalog = new Map();
  const clientCatalog = new Map();
  for (const rawPoint of history) {
    Object.values(rawPoint.byClient || {}).forEach((client) => {
      if (Array.isArray(filters.allowedClientIds) && !filters.allowedClientIds.includes(client.id)) return;
      if (!clientCatalog.has(client.id)) clientCatalog.set(client.id, { id: client.id, displayName: client.displayName || client.id });
    });
    Object.values(rawPoint.byScript || {}).forEach((script) => {
      if (Array.isArray(filters.allowedClientIds)) return;
      if (!scriptCatalog.has(script.path)) scriptCatalog.set(script.path, { path: script.path, name: script.name || path.basename(script.path) });
    });
  }
  return {
    summary: {
      runtimeSeconds: summary.runtimeSeconds,
      activeScriptSeconds: summary.activeScriptSeconds,
      heartbeats: summary.heartbeats,
      commandsQueued: summary.commandsQueued,
      commandsCompleted: summary.commandsCompleted,
      scriptRuns: summary.scriptRuns,
      chatMessages: summary.chatMessages,
      screenshots: summary.screenshots,
      activeClientsPeak: summary.activeClientsPeak,
      avgFps: summary.fpsSamples > 0 ? summary.fpsTotal / summary.fpsSamples : 0,
      sessions: points.length
    },
    history: points,
    clients: [...clientCatalog.values()].sort((a, b) => a.displayName.localeCompare(b.displayName)),
    scripts: [...scriptCatalog.values()].sort((a, b) => a.name.localeCompare(b.name))
  };
}

async function analyticsFiltersForUser(req) {
  if (req.user.isAdmin) return { range: req.query.range, clientId: req.query.clientId, scriptPath: req.query.scriptPath };
  const allowedClientIds = (await connectedClients())
    .filter(client => String(client.licenseUserId || "") === String(req.user.id))
    .map(client => client.id);
  const requestedClientId = String(req.query.clientId || "all");
  if (requestedClientId !== "all" && !allowedClientIds.includes(requestedClientId)) {
    throw automationError(403, "ANALYTICS_CLIENT_FORBIDDEN", "Analytics for this Minecraft client are not available to this account");
  }
  const scriptPath = normalizeSlashes(req.query.scriptPath || "");
  if (scriptPath && scriptPath !== "all") {
    throw automationError(403, "ANALYTICS_SCRIPT_SCOPE_UNAVAILABLE", "Per-script analytics require an administrator because legacy analytics are not account-scoped");
  }
  return { range: req.query.range, clientId: requestedClientId, allowedClientIds };
}

function defaultLibraryScriptPaths() {
  return ["camera_controller.py", "title_bridge.py", "VanillaPathfinding.pyj"];
}

function legacyAutoModulePaths() {
  return [
    "Speed.py",
    "NoFall.py",
    "Fullbright.py",
    "Haste.py",
    "JumpBoost.py",
    "FireResistance.py",
    "WaterBreathing.py",
    "Saturation.py",
    "CleanupHacks.py",
    "UtilityStatus.py"
  ];
}

async function cleanLegacyAutoModules() {
  const legacy = new Set(legacyAutoModulePaths());
  const current = await readJson(libraryScriptPathsPath, []);
  if (!Array.isArray(current)) {
    await writeJson(libraryScriptPathsPath, []);
    return;
  }
  const cleaned = current.filter((item) => !legacy.has(normalizeSlashes(item)));
  if (cleaned.length !== current.length) {
    await writeJson(libraryScriptPathsPath, cleaned);
  }
}

async function readJson(file, fallback) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch {
    return fallback;
  }
}

async function writeJson(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

function normalizeSlashes(value) {
  return String(value || "").replace(/\\/g, "/");
}

function hasScriptExtension(fileName) {
  return scriptExtensions.has(path.extname(fileName).toLowerCase());
}

function decodeBase64Text(value, maxBytes = AUTOMATION_LIMITS.maxCodeBytes) {
  const encoded = String(value || "");
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(encoded) || encoded.length % 4 !== 0) throw automationError(422, "BASE64_INVALID", "Uploaded content is not valid base64");
  const buffer = Buffer.from(encoded, "base64");
  if (buffer.length > maxBytes) throw automationError(413, "SCRIPT_TOO_LARGE", "Script content exceeds the 2 MB limit");
  return buffer.toString("utf8");
}

function validateScriptContent(value) {
  const content = String(value || "");
  if (Buffer.byteLength(content, "utf8") > AUTOMATION_LIMITS.maxCodeBytes) throw automationError(413, "SCRIPT_TOO_LARGE", "Script content exceeds the 2 MB limit");
  if (content.includes("\0")) throw automationError(422, "SCRIPT_CONTENT_INVALID", "Script content contains a null byte");
  return content;
}

function safeScriptName(fileName) {
  let name = normalizeSlashes(fileName).split("/").pop().replace(/[\r\n\t]/g, "").trim();
  if (!name) {
    name = "UploadedScript.py";
  }
  if (!hasScriptExtension(name)) {
    name += ".py";
  }
  return name;
}

function safeScriptRelativePath(fileName) {
  const normalized = normalizeSlashes(fileName || "").replace(/[\r\n\t]/g, "").trim();
  if (!normalized || normalized.includes("\0")) {
    return "UploadedScript.py";
  }
  const parts = normalized.split("/").filter(Boolean);
  if (!parts.length) {
    return "UploadedScript.py";
  }
  const cleanParts = parts.map((part) => part.replace(/[<>:"|?*]/g, "").trim()).filter(Boolean);
  if (cleanParts.length !== parts.length || cleanParts.some((part) => part === "." || part === "..")) {
    throw new Error("Invalid script path");
  }
  const file = cleanParts[cleanParts.length - 1];
  if (!hasScriptExtension(file)) {
    cleanParts[cleanParts.length - 1] = file + ".py";
  }
  if (hiddenRoots.has(cleanParts[0].toLowerCase())) throw automationError(403, "MANAGED_PATH_FORBIDDEN", "This script path is managed internally");
  return cleanParts.join("/");
}

function safeFolderRelativePath(folderName) {
  const normalized = normalizeSlashes(folderName || "").replace(/[\r\n\t]/g, "").trim();
  if (!normalized || normalized.includes("\0")) {
    throw new Error("Missing folder path");
  }
  const cleanParts = normalized.split("/").filter(Boolean).map((part) => part.replace(/[<>:"|?*]/g, "").trim()).filter(Boolean);
  if (!cleanParts.length || cleanParts.some((part) => part === "." || part === "..")) {
    throw new Error("Invalid folder path");
  }
  if (hiddenRoots.has(cleanParts[0].toLowerCase())) throw automationError(403, "MANAGED_PATH_FORBIDDEN", "This folder is managed internally");
  return cleanParts.join("/");
}

function safeTemplateId(value) {
  const id = String(value || "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  return id || "starter";
}

function safeInside(base, candidate) {
  const resolvedBase = path.resolve(base);
  const resolved = path.resolve(candidate);
  const relative = path.relative(resolvedBase, resolved);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error("Path is outside the allowed directory");
  }
  let current = resolvedBase;
  for (const part of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, part);
    if (!fsSync.existsSync(current)) break;
    if (fsSync.lstatSync(current).isSymbolicLink()) throw new Error("Symbolic links are not allowed in managed paths");
  }
  return resolved;
}

function scriptPathFromRequest(value) {
  const normalized = normalizeSlashes(value);
  if (!normalized || normalized.includes("\0")) {
    throw new Error("Missing script path");
  }
  if (hiddenRoots.has(normalized.split("/").filter(Boolean)[0]?.toLowerCase())) throw automationError(403, "MANAGED_PATH_FORBIDDEN", "This script path is managed internally");
  return safeInside(scriptDir, path.join(scriptDir, normalized));
}

function folderPathFromRequest(value) {
  const relative = safeFolderRelativePath(value);
  if (hiddenRoots.has(relative.split("/")[0]?.toLowerCase())) throw automationError(403, "MANAGED_PATH_FORBIDDEN", "This folder is managed internally");
  return safeInside(scriptDir, path.join(scriptDir, relative));
}

async function uniqueScriptPath(fileName) {
  const name = safeScriptRelativePath(fileName);
  let target = safeInside(scriptDir, path.join(scriptDir, name));
  try {
    await fs.access(target);
  } catch {
    return target;
  }
  const ext = path.extname(name);
  const stem = path.basename(name, ext);
  const dir = path.dirname(name) === "." ? "" : path.dirname(name);
  for (let i = 2; i < 1000; i += 1) {
    target = safeInside(scriptDir, path.join(scriptDir, dir, `${stem}-${i}${ext}`));
    try {
      await fs.access(target);
    } catch {
      return target;
    }
  }
  return safeInside(scriptDir, path.join(scriptDir, dir, `${stem}-${Date.now()}${ext}`));
}

async function folders(dir = scriptDir, depth = 0) {
  if (depth > 2) {
    return [];
  }
  let entries = [];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const result = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }
    const absolute = path.join(dir, entry.name);
    const relative = normalizeSlashes(path.relative(scriptDir, absolute));
    if (isHiddenScript(relative + "/placeholder.py")) {
      continue;
    }
    result.push({ path: relative, name: entry.name });
    result.push(...await folders(absolute, depth + 1));
  }
  return result.sort((a, b) => a.path.localeCompare(b.path));
}

function isHiddenScript(relativePath) {
  const normalized = normalizeSlashes(relativePath);
  const parts = normalized.split("/");
  const root = (parts[0] || "").toLowerCase();
  const name = (parts[parts.length - 1] || "").toLowerCase();
  return hiddenRoots.has(root) || root.startsWith(".") || name.startsWith(".") || name === "config.txt";
}

async function walkScripts(dir = scriptDir, depth = 0) {
  if (depth > 3) {
    return [];
  }
  let entries = [];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const result = [];
  for (const entry of entries) {
    const absolute = path.join(dir, entry.name);
    const relative = normalizeSlashes(path.relative(scriptDir, absolute));
    if (isHiddenScript(relative)) {
      continue;
    }
    if (entry.isDirectory()) {
      result.push(...await walkScripts(absolute, depth + 1));
    } else if (entry.isFile() && hasScriptExtension(entry.name)) {
      result.push(absolute);
    }
  }
  return result;
}

async function loadScriptRegistry() {
  const registry = await readJson(scriptRegistryPath, { version: 1, scripts: {} });
  return registry && typeof registry === "object" && registry.scripts && typeof registry.scripts === "object"
    ? registry
    : { version: 1, scripts: {} };
}

async function ensureScriptIdentity(relativePath, registry = null) {
  const normalized = normalizeSlashes(relativePath);
  const current = registry || await loadScriptRegistry();
  if (!current.scripts[normalized]) {
    current.scripts[normalized] = { id: crypto.randomUUID(), installedAt: new Date().toISOString(), lastRunAt: null };
    await writeJson(scriptRegistryPath, current);
  }
  return current.scripts[normalized];
}

async function moveScriptIdentity(fromPath, toPath) {
  const registry = await loadScriptRegistry();
  const from = normalizeSlashes(fromPath);
  const to = normalizeSlashes(toPath);
  registry.scripts[to] = registry.scripts[from] || { id: crypto.randomUUID(), installedAt: new Date().toISOString(), lastRunAt: null };
  delete registry.scripts[from];
  await writeJson(scriptRegistryPath, registry);
}

async function removeScriptIdentity(relativePath) {
  const registry = await loadScriptRegistry();
  const normalized = normalizeSlashes(relativePath);
  const identity = registry.scripts[normalized];
  delete registry.scripts[normalized];
  await writeJson(scriptRegistryPath, registry);
  if (!identity?.id) return;
  const [settings, shortcuts] = await Promise.all([readJson(scriptSettingsPath, {}), readJson(scriptShortcutsPath, {})]);
  delete settings[identity.id];
  delete shortcuts[identity.id];
  await Promise.all([writeJson(scriptSettingsPath, settings), writeJson(scriptShortcutsPath, shortcuts)]);
  await fs.rm(path.join(scriptDir, "shulkr_runtime", `${identity.id}.py`), { force: true });
  await fs.rm(path.join(scriptDir, "shulkr_config", `${identity.id}.json`), { force: true });
}

async function moveFolderIdentities(fromPath, toPath) {
  const registry = await loadScriptRegistry();
  const from = normalizeSlashes(fromPath).replace(/\/$/, "");
  const to = normalizeSlashes(toPath).replace(/\/$/, "");
  for (const [scriptPath, identity] of Object.entries({ ...registry.scripts })) {
    if (scriptPath !== from && !scriptPath.startsWith(`${from}/`)) continue;
    registry.scripts[`${to}${scriptPath.slice(from.length)}`] = identity;
    delete registry.scripts[scriptPath];
  }
  await writeJson(scriptRegistryPath, registry);
}

async function removeFolderIdentities(folderPath) {
  const registry = await loadScriptRegistry();
  const folder = normalizeSlashes(folderPath).replace(/\/$/, "");
  const removed = Object.entries(registry.scripts).filter(([scriptPath]) => scriptPath === folder || scriptPath.startsWith(`${folder}/`));
  const [settings, shortcuts] = await Promise.all([readJson(scriptSettingsPath, {}), readJson(scriptShortcutsPath, {})]);
  for (const [scriptPath, identity] of removed) {
    delete registry.scripts[scriptPath];
    delete settings[identity.id];
    delete shortcuts[identity.id];
  }
  await Promise.all([writeJson(scriptRegistryPath, registry), writeJson(scriptSettingsPath, settings), writeJson(scriptShortcutsPath, shortcuts)]);
}

async function scriptMetadata(file, identity) {
  const source = await fs.readFile(file, "utf8");
  const metadata = parseScriptSettings(source);
  const stored = await readJson(scriptSettingsPath, {});
  const saved = stored[identity.id]?.values || {};
  const reconciled = reconcileValues(metadata.definitions, saved);
  return {
    ...metadata,
    values: reconciled.values,
    warnings: [...reconciled.warnings, ...metadata.issues.map(issue => `Line ${issue.line}: ${issue.message}`)],
    sourceHash: crypto.createHash("sha256").update(source).digest("hex")
  };
}

async function prepareConfiguredScriptExecution(file) {
  const relative = normalizeSlashes(path.relative(scriptDir, file));
  const identity = await ensureScriptIdentity(relative);
  const metadata = await scriptMetadata(file, identity);
  if (metadata.issues.length) throw automationError(422, "SCRIPT_METADATA_INVALID", "Script settings metadata must be fixed before configured execution", { issues: metadata.issues });
  const validation = validateValues(metadata.definitions, metadata.values);
  if (!validation.valid) throw automationError(422, "SCRIPT_SETTINGS_INVALID", "Saved script settings are invalid", { errors: validation.errors });
  if (!metadata.definitions.length) return { path: relative, sourcePath: relative, scriptId: identity.id, configured: false };
  const runtimeDir = safeInside(scriptDir, path.join(scriptDir, "shulkr_runtime"));
  const configDir = safeInside(scriptDir, path.join(scriptDir, "shulkr_config"));
  await Promise.all([fs.mkdir(runtimeDir, { recursive: true }), fs.mkdir(configDir, { recursive: true })]);
  const configFile = safeInside(configDir, path.join(configDir, `${identity.id}.json`));
  const wrapperFile = safeInside(runtimeDir, path.join(runtimeDir, `${identity.id}.py`));
  await fs.writeFile(configFile, JSON.stringify({ version: 1, scriptId: identity.id, scriptPath: relative, values: validation.values }, null, 2) + "\n", "utf8");
  const wrapper = [
    "# Generated by Shulkr. Do not edit.",
    "import builtins, json, os, runpy",
    `_config_path = ${JSON.stringify(configFile)}`,
    `_script_path = ${JSON.stringify(file)}`,
    "with open(_config_path, 'r', encoding='utf-8') as _handle:",
    "    _payload = json.load(_handle)",
    "builtins.SHULKR_SETTINGS = _payload.get('values', {})",
    "builtins.shulkr_setting = lambda key, default=None: builtins.SHULKR_SETTINGS.get(key, default)",
    "os.environ['SHULKR_SCRIPT_SETTINGS_FILE'] = _config_path",
    "os.environ['SHULKR_SCRIPT_ID'] = _payload.get('scriptId', '')",
    "runpy.run_path(_script_path, run_name='__main__')",
    ""
  ].join("\n");
  await fs.writeFile(wrapperFile, wrapper, "utf8");
  const registry = await loadScriptRegistry();
  if (registry.scripts[relative]) registry.scripts[relative].lastRunAt = new Date().toISOString();
  await writeJson(scriptRegistryPath, registry);
  return { path: normalizeSlashes(path.relative(scriptDir, wrapperFile)), sourcePath: relative, scriptId: identity.id, configured: true };
}

async function scriptSummary(file) {
  const stat = await fs.stat(file);
  const relative = normalizeSlashes(path.relative(scriptDir, file));
  const identity = await ensureScriptIdentity(relative);
  const metadata = await scriptMetadata(file, identity);
  const shortcuts = await readJson(scriptShortcutsPath, {});
  return {
    id: identity.id,
    path: relative,
    name: path.basename(file),
    extension: path.extname(file).replace(".", "").toLowerCase(),
    sizeBytes: stat.size,
    modifiedAt: stat.mtimeMs,
    description: await scriptDescription(file),
    author: "Local",
    version: "local",
    installedAt: identity.installedAt,
    lastRunAt: identity.lastRunAt,
    shortcut: shortcuts[identity.id] || "",
    settingCount: metadata.definitions.length,
    settings: metadata.definitions,
    metadataIssues: metadata.issues,
    settingWarnings: metadata.warnings
  };
}

async function scriptDescription(file) {
  try {
    const lines = (await fs.readFile(file, "utf8")).split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        const comment = trimmed.replace(/^#+/, "").trim();
        if (comment) {
          return comment;
        }
      }
      if (trimmed && !trimmed.startsWith("import ") && !trimmed.startsWith("from ")) {
        return trimmed.length > 90 ? trimmed.slice(0, 87) + "..." : trimmed;
      }
    }
  } catch {
  }
  return "";
}

async function scripts() {
  const files = await walkScripts();
  const summaries = await Promise.all(files.map(scriptSummary));
  return summaries.sort((a, b) => a.path.localeCompare(b.path));
}

async function scriptModules(scriptSummaries = null) {
  const marked = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  const markedSet = new Set(Array.isArray(marked) ? marked.map(normalizeSlashes).filter(Boolean) : []);
  const summaries = scriptSummaries || await scripts();
  return summaries
    .filter((script) => markedSet.has(normalizeSlashes(script.path)))
    .map((script) => ({
      id: `local:${script.path}`,
      name: path.basename(script.name, path.extname(script.name)),
      author: "Local library",
      version: script.extension.toUpperCase() || "PY",
      description: script.description || "Marked as a reusable Shulkr library.",
      category: script.extension === "pyj" ? "Pyjinn" : "Python",
      icon: script.extension === "pyj" ? "route-solid.png" : scriptIcon(script.name),
      status: "Installed",
      installed: true,
      favorite: true
    }));
}

function scriptCategory(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.includes("farm") || lower.includes("crop") || lower.includes("mine")) return "Farming";
  if (lower.includes("combat") || lower.includes("killaura") || lower.includes("crystal")) return "Combat";
  if (lower.includes("build") || lower.includes("chunk") || lower.includes("world")) return "World";
  if (lower.includes("speed") || lower.includes("nofall") || lower.includes("fullbright") || lower.includes("haste")
    || lower.includes("jump") || lower.includes("fire") || lower.includes("water") || lower.includes("saturation")
    || lower.includes("cleanup") || lower.includes("chat") || lower.includes("sort") || lower.includes("inventory")
    || lower.includes("config")) return "Utility";
  return "Other";
}

function scriptIcon(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.includes("farm")) return "user-solid.png";
  if (lower.includes("combat") || lower.includes("mine")) return "broom-solid.png";
  if (lower.includes("build") || lower.includes("chunk")) return "box-open-solid.png";
  return "code-solid.png";
}

function firstComment(content) {
  for (const line of String(content || "").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed.startsWith("#")) {
      const comment = trimmed.replace(/^#+/, "").trim();
      if (comment) return comment;
    }
  }
  return "Published Shulkr script.";
}

function slug(value) {
  return String(value || "script").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || "script";
}

async function uniqueLibraryId(name) {
  const items = await libraryScripts();
  const base = slug(name);
  let candidate = base;
  let suffix = 2;
  while (items.some((item) => item.id === candidate)) {
    candidate = `${base}-${suffix++}`;
  }
  return candidate;
}

function normalizeLibraryScript(input, fallbackContent = "") {
  if (input?.kind === "automation" && input?.graph) return normalizeAutomationListing(input, input.ownerId || null);
  const now = Date.now();
  const fileName = safeScriptName(input?.fileName || input?.name || "PublishedScript.py");
  const code = validateScriptContent(input?.code ?? input?.content ?? fallbackContent ?? "");
  const name = (String(input?.name || path.basename(fileName, path.extname(fileName))).trim() || "Untitled Script").slice(0, 120);
  const category = String(input?.category || scriptCategory(fileName));
  const tags = Array.isArray(input?.tags) && input.tags.length
    ? input.tags.map(String)
    : [path.extname(fileName).replace(".", "").toUpperCase() || "PY", category];
  return {
    id: String(input?.id || "").trim() || slug(name),
    name,
    author: String(input?.author || "Shulkr user").slice(0, 80),
    about: String(input?.about || input?.description || firstComment(code)).slice(0, 2000),
    category,
    tags,
    version: String(input?.version || "1.0.0"),
    icon: String(input?.icon || scriptIcon(fileName)),
    fileName,
    code,
    downloads: Math.max(0, Number(input?.downloads || 0)),
    stars: Math.max(0, Number(input?.stars || 0)),
    verification: { graphValidated: false, serverCompiled: false, permissionsDerived: false, codeReviewRequired: true },
    publishedAt: Number(input?.publishedAt || now),
    updatedAt: Number(input?.updatedAt || now)
  };
}

async function libraryScripts() {
  const items = await readJson(libraryScriptsPath, []);
  return items.map((item) => normalizeLibraryScript(item)).sort((a, b) => b.publishedAt - a.publishedAt);
}

async function saveLibraryScripts(items) {
  await writeJson(libraryScriptsPath, items);
}

async function saveLibraries(items) {
  await writeJson(librariesPath, items);
}

async function saveClientModules(items) {
  await writeJson(clientModulesPath, items);
}

async function snapshotData() {
  const [profile, scriptList, templates, licenses, publishedScripts, libraries, clientModules] = await Promise.all([
    readJson(profilePath, defaultProfile()),
    scripts(),
    readJson(templatesPath, defaultTemplates()),
    readJson(licensesPath, defaultLicenses()),
    libraryScripts(),
    readJson(librariesPath, defaultLibraries()),
    readJson(clientModulesPath, defaultClientModules())
  ]);
  const modules = await scriptModules(scriptList);
  const stats = {
    scripts: scriptList.length,
    installedModules: modules.length,
    installedLibraries: modules.length,
    installedClientModules: Array.isArray(clientModules) ? clientModules.filter((item) => item.installed !== false).length : 0,
    templates: templates.length,
    publishedScripts: publishedScripts.length
  };
  return { profile, stats, scripts: scriptList, modules, libraries, clientModules, templates, licenses };
}

async function connectedClients() {
  const profile = await readJson(profilePath, defaultProfile());
  const stored = await readJson(clientsPath, []);
  const now = Date.now();
  const profileLastSeen = Date.parse(profile.lastSeenAt || 0);
  const safeProfileLastSeen = Number.isFinite(profileLastSeen) ? profile.lastSeenAt : new Date(now).toISOString();
  const localClient = {
    id: profile.deviceId || profile.id || "local-user",
    displayName: profile.deviceName || profile.displayName || "This PC",
    tier: profile.tier || "Premium",
    status: profile.status || "Ready",
    connected: profile.connected !== false,
    minecraft: "local client",
    deviceId: profile.deviceId || profile.id || "local-user",
    deviceName: profile.deviceName || profile.displayName || "This PC",
    accountName: profile.displayName || "EnderUser",
    licenseUserId: profile.id || "local-user",
    lastSeenAt: safeProfileLastSeen,
    source: "profile"
  };
  const liveLocal = stored.find((client) => client.id === localClient.id || client.licenseUserId === localClient.licenseUserId);
  const mergedLocal = liveLocal ? { ...localClient, ...liveLocal, source: "heartbeat" } : localClient;
  const merged = [mergedLocal, ...stored.filter((client) => client.id !== localClient.id && client.id !== liveLocal?.id && client.licenseUserId !== localClient.licenseUserId)];
  return merged.map((client) => {
    const lastSeen = Date.parse(client.lastSeenAt || 0);
    const heartbeatFresh = Number.isFinite(lastSeen) && now - lastSeen < 120000;
    return {
      ...client,
      connected: client.source === "heartbeat" ? heartbeatFresh : client.connected !== false && heartbeatFresh
    };
  });
}

async function upsertClientHeartbeat(input) {
  const now = new Date().toISOString();
  const id = normalizeDeviceId(input?.deviceId || input?.hardwareId || input?.id || input?.displayName || "local-device");
  const clients = await connectedClients();
  const deviceName = String(input?.deviceName || input?.displayName || "This PC").trim().slice(0, 120) || "This PC";
  const accountName = String(input?.accountName || input?.playerName || "").trim().slice(0, 80);
  const licenseUserId = String(input?.licenseUserId || input?.userId || "local-user").trim().slice(0, 160);
  if (!/^[a-zA-Z0-9_.@-]{1,160}$/.test(licenseUserId)) throw automationError(422, "DEVICE_USER_INVALID", "The device account identifier is invalid");
  const deviceBinding = await bindDeviceToLicense({ deviceId: id, deviceName, licenseUserId });
  const next = {
    id,
    displayName: deviceName,
    deviceId: id,
    deviceName,
    accountName,
    licenseUserId,
    licenseTier: deviceBinding.license?.tier || String(input?.tier || "Premium"),
    licenseStatus: deviceBinding.allowed ? "bound" : "blocked",
    hardwareLocked: deviceBinding.hardwareLock !== false,
    tier: deviceBinding.license?.tier || String(input?.tier || "Premium"),
    status: deviceBinding.allowed ? String(input?.status || "Connected").slice(0, 80) : "Hardware ID not licensed",
    minecraft: String(input?.minecraft || "unknown").slice(0, 48),
    server: String(input?.server || "Singleplayer").slice(0, 160),
    world: String(input?.world || "No world").slice(0, 160),
    position: String(input?.position || "-").slice(0, 100),
    fps: Math.max(0, Math.min(2000, Number(input?.fps || 0) || 0)),
    activeScript: String(input?.activeScript || "").slice(0, 240),
    rendererActive: input?.rendererActive !== false,
    overlays: Array.isArray(input?.overlays) ? input.overlays.slice(0, 50).map(value => String(value).slice(0, 80)) : [],
    connected: deviceBinding.allowed,
    hardwareMessage: deviceBinding.message || "",
    lastSeenAt: now,
    source: "heartbeat"
  };
  const rest = clients.filter((client) => client.id !== id && client.source !== "profile");
  await writeJson(clientsPath, [next, ...rest]);
  await recordHeartbeatAnalytics(next, 1 + rest.filter((client) => client.connected !== false).length);
  return next;
}

function deriveAutomationPermissions(graph) {
  const permissions = new Set();
  for (const node of Array.isArray(graph?.nodes) ? graph.nodes : []) {
    for (const permission of AUTOMATION_PERMISSION_BY_NODE[node.type] || []) permissions.add(permission);
  }
  return [...permissions].sort();
}

async function trustedAutomationCompilation(graph) {
  const compilerUrl = pathToFileURL(path.join(rootDir, "web-client", "src", "flow", "compiler.js")).href;
  const { compileGraph } = await import(compilerUrl);
  return compileGraph(graph);
}

function normalizeAutomationListing(input, ownerId = null) {
  const graph = structuredClone(input.graph || input);
  validateAutomationGraph(graph);
  const permissions = deriveAutomationPermissions(graph);
  const compile = input.generatedCodePreview ? { code: String(input.generatedCodePreview) } : null;
  return {
    id: String(input.id || slug(input.title || graph.name || "automation")),
    kind: "automation",
    title: String(input.title || graph.name || "Untitled automation").slice(0, 120),
    name: String(input.title || graph.name || "Untitled automation").slice(0, 120),
    description: String(input.description || graph.description || "").slice(0, 2000),
    author: String(input.author || "Shulkr user"),
    ownerId: ownerId || input.ownerId || null,
    visibility: input.visibility === "private" ? "private" : "public",
    tags: Array.isArray(input.tags) ? input.tags.map(String).slice(0, 20) : [],
    version: String(input.version || "1.0.0"),
    graphFormatVersion: graph.formatVersion,
    nodeCount: graph.nodes.length,
    edgeCount: graph.edges.length,
    requiredPermissions: permissions,
    supportedMinecraftVersions: Array.isArray(input.supportedMinecraftVersions) ? input.supportedMinecraftVersions.map(String) : ["1.20+"],
    supportedClientVersion: String(input.supportedClientVersion || "1.0.0"),
    generatedCodePreview: compile?.code || String(input.generatedCodePreview || ""),
    verification: { graphValidated: true, serverCompiled: true, permissionsDerived: true },
    graph,
    changelog: String(input.changelog || "Initial version"),
    installs: Math.max(0, Number(input.installs || 0)),
    ratings: Array.isArray(input.ratings) ? input.ratings : [],
    favorites: Math.max(0, Number(input.favorites || 0)),
    publishedAt: Number(input.publishedAt || Date.now()),
    updatedAt: Number(input.updatedAt || Date.now())
  };
}

function automationError(status, code, message, details = undefined) {
  const error = new Error(message);
  error.status = status;
  error.code = code;
  if (details !== undefined) error.details = details;
  return error;
}

function validateSafeGraphData(value, pathLabel = "node.data", depth = 0) {
  if (depth > 20) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} is nested too deeply`);
  if (value === null || typeof value === "boolean") return;
  if (typeof value === "string") {
    if (value.length > 100000) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} contains an oversized string`);
    return;
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} contains a non-finite number`);
    return;
  }
  if (Array.isArray(value)) {
    if (value.length > 10000) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} contains an oversized array`);
    value.forEach((item, index) => validateSafeGraphData(item, `${pathLabel}[${index}]`, depth + 1));
    return;
  }
  if (typeof value !== "object") throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} contains an unsupported value type`);
  for (const [key, item] of Object.entries(value)) {
    if (["__proto__", "prototype", "constructor"].includes(key)) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `${pathLabel} contains a forbidden key`);
    validateSafeGraphData(item, `${pathLabel}.${key}`, depth + 1);
  }
}

function validateAutomationGraph(graph) {
  if (!graph || typeof graph !== "object" || Array.isArray(graph)) throw automationError(400, "AUTOMATION_MALFORMED", "Automation graph must be an object");
  const serialized = JSON.stringify(graph);
  if (Buffer.byteLength(serialized, "utf8") > AUTOMATION_LIMITS.maxGraphBytes) throw automationError(413, "AUTOMATION_TOO_LARGE", "Automation graph exceeds the 2 MB limit");
  if (graph.formatVersion !== AUTOMATION_FORMAT_VERSION) throw automationError(422, "AUTOMATION_VERSION_UNSUPPORTED", "Unsupported automation format version");
  if (!/^[a-zA-Z0-9_-]{1,100}$/.test(String(graph.id || ""))) throw automationError(400, "AUTOMATION_ID_INVALID", "Invalid automation id");
  if (!String(graph.name || "").trim() || String(graph.name).length > AUTOMATION_LIMITS.maxNameLength) throw automationError(422, "AUTOMATION_NAME_INVALID", `Automation name must be between 1 and ${AUTOMATION_LIMITS.maxNameLength} characters`);
  if (graph.description !== undefined && (typeof graph.description !== "string" || graph.description.length > AUTOMATION_LIMITS.maxDescriptionLength)) throw automationError(422, "AUTOMATION_DESCRIPTION_INVALID", `Automation description must be at most ${AUTOMATION_LIMITS.maxDescriptionLength} characters`);
  if (!Array.isArray(graph.nodes) || graph.nodes.length > AUTOMATION_LIMITS.maxNodes) throw automationError(422, "AUTOMATION_NODES_INVALID", `Automation nodes must be an array with at most ${AUTOMATION_LIMITS.maxNodes} entries`);
  if (!Array.isArray(graph.edges) || graph.edges.length > AUTOMATION_LIMITS.maxEdges) throw automationError(422, "AUTOMATION_EDGES_INVALID", `Automation edges must be an array with at most ${AUTOMATION_LIMITS.maxEdges} entries`);
  const nodeIds = new Set();
  for (const node of graph.nodes) {
    if (!node || typeof node !== "object" || Array.isArray(node) || !/^[a-zA-Z0-9_-]{1,120}$/.test(String(node.id || ""))) throw automationError(422, "AUTOMATION_NODE_INVALID", "Each automation node must have a valid id");
    if (nodeIds.has(node.id)) throw automationError(422, "AUTOMATION_NODE_DUPLICATE", `Duplicate node id: ${node.id}`);
    nodeIds.add(node.id);
    if (typeof node.type !== "string" || !/^[a-zA-Z0-9_.-]{1,120}$/.test(node.type)) throw automationError(422, "AUTOMATION_NODE_INVALID", `Invalid node type for ${node.id}`);
    if (!Object.prototype.hasOwnProperty.call(AUTOMATION_PERMISSION_BY_NODE, node.type)) throw automationError(422, "AUTOMATION_NODE_TYPE_UNSUPPORTED", `Unsupported node type for ${node.id}`);
    if (!Number.isInteger(node.version) || node.version < 1 || node.version > 1) throw automationError(422, "AUTOMATION_NODE_VERSION_UNSUPPORTED", `Unsupported node version for ${node.id}`);
    if (!node.position || typeof node.position.x !== "number" || typeof node.position.y !== "number" || !Number.isFinite(node.position.x) || !Number.isFinite(node.position.y) || Math.abs(node.position.x) > 1000000 || Math.abs(node.position.y) > 1000000) throw automationError(422, "AUTOMATION_NODE_POSITION_INVALID", `Invalid node position for ${node.id}`);
    if (!node.data || typeof node.data !== "object" || Array.isArray(node.data)) throw automationError(422, "AUTOMATION_NODE_DATA_INVALID", `Invalid node data for ${node.id}`);
    validateSafeGraphData(node.data, `node ${node.id}.data`);
  }
  const edgeIds = new Set();
  const edgeSignatures = new Set();
  for (const edge of graph.edges) {
    if (!edge || typeof edge !== "object" || Array.isArray(edge) || !/^[a-zA-Z0-9_-]{1,120}$/.test(String(edge.id || ""))) throw automationError(422, "AUTOMATION_EDGE_INVALID", "Each automation edge must have a valid id");
    if (edgeIds.has(edge.id)) throw automationError(422, "AUTOMATION_EDGE_DUPLICATE", `Duplicate edge id: ${edge.id}`);
    edgeIds.add(edge.id);
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) throw automationError(422, "AUTOMATION_EDGE_NODE_MISSING", `Edge ${edge.id} references a missing node`);
    if (edge.source === edge.target) throw automationError(422, "AUTOMATION_EDGE_SELF_CONNECTION", `Edge ${edge.id} cannot connect a node to itself`);
    if (typeof edge.sourceHandle !== "string" || !/^[a-zA-Z0-9_.-]{1,120}$/.test(edge.sourceHandle) || typeof edge.targetHandle !== "string" || !/^[a-zA-Z0-9_.-]{1,120}$/.test(edge.targetHandle) || !AUTOMATION_DATA_TYPES.has(edge.dataType)) throw automationError(422, "AUTOMATION_EDGE_INVALID", `Invalid handles or data type for edge ${edge.id}`);
    const signature = `${edge.source}\u0000${edge.sourceHandle}\u0000${edge.target}\u0000${edge.targetHandle}\u0000${edge.dataType}`;
    if (edgeSignatures.has(signature)) throw automationError(422, "AUTOMATION_EDGE_DUPLICATE", `Duplicate connection for edge ${edge.id}`);
    edgeSignatures.add(signature);
  }
  if (!graph.viewport || typeof graph.viewport.x !== "number" || typeof graph.viewport.y !== "number" || typeof graph.viewport.zoom !== "number" || !Number.isFinite(graph.viewport.x) || !Number.isFinite(graph.viewport.y) || !Number.isFinite(graph.viewport.zoom) || graph.viewport.zoom < 0.1 || graph.viewport.zoom > 4 || Math.abs(graph.viewport.x) > 1000000 || Math.abs(graph.viewport.y) > 1000000) throw automationError(422, "AUTOMATION_VIEWPORT_INVALID", "Invalid automation viewport");
  if (graph.generatedCode !== undefined && (typeof graph.generatedCode !== "string" || Buffer.byteLength(graph.generatedCode, "utf8") > AUTOMATION_LIMITS.maxCodeBytes)) throw automationError(422, "AUTOMATION_CODE_INVALID", "Generated code exceeds the 2 MB limit");
  return graph;
}

function migrateAutomationGraph(graph) {
  const next = { ...graph, edges: graph.edges || (graph.connections || []).map(edge => ({ id: edge.id, source: edge.fromNode, sourceHandle: edge.fromPort, target: edge.toNode, targetHandle: edge.toPort, dataType: edge.dataType || "execution" })), nodes: (graph.nodes || []).map(node => ({ ...node, version: node.version || 1, data: node.data || node.properties || {} })), viewport: graph.viewport || { x: 0, y: 0, zoom: 1 }, description: graph.description || "", generatedCode: graph.generatedCode || "" };
  delete next.connections;
  delete next.properties;
  return next;
}

async function migrateAutomationStore() {
  const records = await readJson(automationsPath, []);
  if (!Array.isArray(records)) return writeJson(automationsPath, []);
  const migrated = [];
  for (const record of records) {
    try {
      const graph = migrateAutomationGraph(record.graph || record);
      validateAutomationGraph(graph);
      migrated.push({ userId: record.userId || record.ownerId, graph });
    } catch {
      // Invalid legacy records are not exposed to users after migration.
    }
  }
  if (JSON.stringify(migrated) !== JSON.stringify(records)) await writeJson(automationsPath, migrated);
}

async function userAutomations(userId) {
  const records = await readJson(automationsPath, []);
  return ownedAutomationRecords(records, userId);
}

function ownedAutomationRecords(records, userId) {
  return Array.isArray(records) ? records.filter(record => record.userId === userId && record.graph) : [];
}

async function persistUserAutomation(userId, graph, { allowCreate = true } = {}) {
  validateAutomationGraph(graph);
  const records = await readJson(automationsPath, []);
  const safeRecords = Array.isArray(records) ? records : [];
  const now = new Date().toISOString();
  const existing = safeRecords.find(record => record.userId === userId && record.graph?.id === graph.id);
  if (!existing && !allowCreate) throw automationError(404, "AUTOMATION_NOT_FOUND", "Automation not found");
  if (existing && graph.expectedUpdatedAt && graph.expectedUpdatedAt !== existing.graph.updatedAt) throw automationError(409, "AUTOMATION_CONFLICT", "Automation was changed by another request", { current: existing.graph });
  const { expectedUpdatedAt: _expectedUpdatedAt, ...cleanGraph } = graph;
  const saved = { ...cleanGraph, createdAt: existing?.graph?.createdAt || graph.createdAt || now, updatedAt: now };
  const next = safeRecords.filter(record => !(record.userId === userId && record.graph?.id === graph.id));
  next.push({ userId, graph: saved });
  await writeJson(automationsPath, next);
  return saved;
}

async function createUserAutomation(userId, graph) {
  const now = new Date().toISOString();
  const requested = { ...graph, id: graph.id || `automation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, createdAt: now, updatedAt: now };
  const records = await readJson(automationsPath, []);
  if (Array.isArray(records) && records.some(record => record.userId === userId && record.graph?.id === requested.id)) throw automationError(409, "AUTOMATION_ID_CONFLICT", "Automation id is already in use");
  return persistUserAutomation(userId, requested, { allowCreate: true });
}

function normalizeDeviceId(value) {
  const cleaned = String(value || "")
    .slice(0, 256)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  return cleaned || "hwid-local-device";
}

function deviceTokenFor(deviceId) {
  return crypto.createHmac("sha256", authSecret).update(`device:${normalizeDeviceId(deviceId)}`).digest("base64url");
}

function suppliedDeviceToken(req) {
  const header = String(req.headers.authorization || "");
  return header.startsWith("Device ") ? header.slice(7).trim() : "";
}

function deviceTokenMatches(deviceId, supplied) {
  const expected = Buffer.from(deviceTokenFor(deviceId));
  const actual = Buffer.from(String(supplied || ""));
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

function requireDeviceToken(req, res, deviceId) {
  if (!deviceId || !deviceTokenMatches(deviceId, suppliedDeviceToken(req))) {
    void recordTamperEvent("device.invalid_token", { severity: "error", message: "Minecraft device token was rejected", route: req.originalUrl, ip: req.ip, metadata: { deviceId: obfuscateDeviceId(deviceId) } });
    res.status(401).json({ error: "Minecraft device authentication required", code: "DEVICE_AUTH_REQUIRED" });
    return false;
  }
  return true;
}

function obfuscateDeviceId(value) {
  const normalized = normalizeDeviceId(value);
  if (normalized.length <= 14) return normalized;
  return `${normalized.slice(0, 8)}...${normalized.slice(-6)}`;
}

async function bindDeviceToLicense({ deviceId, deviceName, licenseUserId }) {
  const normalizedDeviceId = normalizeDeviceId(deviceId);
  const licenses = await readJson(licensesPath, defaultLicenses());
  const now = new Date().toISOString();
  const licenseIndex = licenses.findIndex((entry) => entry.userId === licenseUserId && String(entry.status || "active").toLowerCase() === "active");
  if (licenseIndex < 0) {
    return {
      allowed: true,
      hardwareLock: false,
      license: null,
      message: "No license record was found for this device yet."
    };
  }
  const license = { ...licenses[licenseIndex] };
  const boundDeviceIds = Array.isArray(license.boundDeviceIds) ? [...new Set(license.boundDeviceIds.map(normalizeDeviceId))] : [];
  const deviceLimit = Math.max(1, Number(license.deviceLimit || license.seats || 1) || 1);
  const hardwareLock = license.hardwareLock !== false;
  const alreadyBound = boundDeviceIds.includes(normalizedDeviceId);
  if (hardwareLock && !alreadyBound && boundDeviceIds.length >= deviceLimit) {
    await recordTamperEvent("license.hardware_id_mismatch", {
      severity: "error",
      message: `Blocked device ${obfuscateDeviceId(normalizedDeviceId)} because the license is already bound to another hardware ID.`,
      actorId: licenseUserId,
      route: "/api/clients/heartbeat",
      metadata: {
        deviceId: obfuscateDeviceId(normalizedDeviceId),
        deviceName,
        boundDeviceIds: boundDeviceIds.map(obfuscateDeviceId),
        deviceLimit
      }
    });
    return {
      allowed: false,
      hardwareLock,
      license,
      message: "This license is already bound to another hardware ID."
    };
  }
  if (!alreadyBound) {
    boundDeviceIds.push(normalizedDeviceId);
  }
  const nextLicense = {
    ...license,
    boundDeviceIds,
    primaryDeviceId: license.primaryDeviceId || normalizedDeviceId,
    primaryDeviceName: license.primaryDeviceName || deviceName || "This PC",
    lastDeviceSeenAt: now,
    lastDeviceId: normalizedDeviceId,
    lastDeviceName: deviceName || license.lastDeviceName || "This PC"
  };
  licenses[licenseIndex] = nextLicense;
  await writeJson(licensesPath, licenses);
  return {
    allowed: true,
    hardwareLock,
    license: nextLicense,
    message: hardwareLock ? `Licensed to ${nextLicense.primaryDeviceName || "this device"} (${obfuscateDeviceId(nextLicense.primaryDeviceId || normalizedDeviceId)}).` : "Hardware lock disabled."
  };
}

async function publishLibraryScript(input) {
  const item = normalizeLibraryScript(input);
  item.id = input?.id ? slug(input.id) : await uniqueLibraryId(item.name);
  item.publishedAt = Date.now();
  item.updatedAt = item.publishedAt;
  const items = await libraryScripts();
  items.unshift(item);
  await saveLibraryScripts(items);
  return item;
}

async function installLibraryScript(id) {
  const items = await libraryScripts();
  const item = items.find((candidate) => candidate.id === id);
  if (!item) throw new Error("Published script not found");
  const target = await uniqueScriptPath(item.fileName);
  await fs.writeFile(target, item.code, "utf8");
  item.downloads += 1;
  item.updatedAt = Date.now();
  await saveLibraryScripts(items);
  return scriptSummary(target);
}

function normalizeTemplate(template) {
  const fallback = defaultTemplates()[0];
  const safeName = (String(template?.name || fallback.name).trim() || fallback.name).slice(0, AUTOMATION_LIMITS.maxNameLength);
  const kind = template?.kind === "automation" ? "automation" : "script";
  const graph = kind === "automation" && template?.graph ? structuredClone(template.graph) : undefined;
  if (graph) validateAutomationGraph(graph);
  return {
    id: safeTemplateId(template?.id || safeName),
    name: safeName,
    category: String(template?.category || fallback.category),
    description: String(template?.description || fallback.description).slice(0, AUTOMATION_LIMITS.maxDescriptionLength),
    difficulty: String(template?.difficulty || fallback.difficulty),
    blocks: Number(template?.blocks || fallback.blocks),
    icon: String(template?.icon || fallback.icon),
    badge: template?.badge ? String(template.badge) : "",
    script: validateScriptContent(template?.script || fallback.script),
    kind,
    graph,
    requiredPermissions: Array.isArray(template?.requiredPermissions || template?.permissions) ? [...new Set((template.requiredPermissions || template.permissions).map(String))] : [],
    supportedMinecraftVersions: Array.isArray(template?.supportedMinecraftVersions) ? template.supportedMinecraftVersions.slice(0, 20).map(value => String(value).slice(0, 32)) : ["1.20+"],
    supportedClientVersion: String(template?.supportedClientVersion || "1.0.0").slice(0, 32),
    version: String(template?.version || "1.0.0").slice(0, 32),
    changelog: String(template?.changelog || "Initial template").slice(0, 2000),
    tags: Array.isArray(template?.tags) ? template.tags.map(String) : []
  };
}

async function createScriptFromTemplate(id) {
  const templates = await readJson(templatesPath, defaultTemplates());
  const template = templates.find((item) => item.id === id) || templates[0] || defaultTemplates()[0];
  const file = await uniqueScriptPath(`${template.id.replace(/(^|-)([a-z])/g, (_, __, char) => char.toUpperCase())}.py`);
  const script = String(template.script || `import minescript as ms\n\nms.echo("${template.name} loaded")\n`);
  const content = script.startsWith("#") ? script : `# ${template.name} - generated from Shulkr Templates\n${script}`;
  await fs.writeFile(file, content, "utf8");
  return scriptSummary(file);
}

function ffmpegCandidates() {
  const names = process.platform === "win32" ? ["ffmpeg.exe", "ffmpeg"] : ["ffmpeg"];
  const configured = [
    process.env.SHULKR_FFMPEG_PATH,
    process.env.FFMPEG_PATH,
    path.join(rootDir, "tools", "ffmpeg", "bin", "ffmpeg.exe"),
    path.join(rootDir, "tools", "ffmpeg", "ffmpeg.exe"),
    path.join(runDir, "ffmpeg", "bin", "ffmpeg.exe"),
    path.join(runDir, "ffmpeg", "ffmpeg.exe")
  ].filter(Boolean);
  const pathDirs = String(process.env.PATH || "")
    .split(path.delimiter)
    .filter(Boolean)
    .flatMap((dir) => names.map((name) => path.join(dir, name)));
  return [...configured, ...pathDirs, ...names];
}

function findFfmpeg() {
  for (const candidate of ffmpegCandidates()) {
    try {
      if (candidate.includes(path.sep) || candidate.includes("/")) {
        if (fsSync.existsSync(candidate)) return candidate;
      } else {
        return candidate;
      }
    } catch {
    }
  }
  return "";
}

function streamStatus(viewer = null) {
  const ffmpegPath = findFfmpeg();
  const mayInspect = !streamState.ownerId || !viewer || viewer.isAdmin || String(viewer.id || viewer.userId || "") === streamState.ownerId;
  return {
    available: Boolean(ffmpegPath),
    running: mayInspect && Boolean(streamState.process),
    clients: mayInspect ? streamState.clients.size : 0,
    startedAt: mayInspect ? streamState.startedAt : null,
    lastError: mayInspect ? streamState.lastError : "",
    mode: streamState.mode,
    fps: streamState.fps,
    captureRect: mayInspect ? streamState.captureRect : null,
    captureTitle: mayInspect ? streamState.captureTitle : "",
    source: "Minecraft window capture",
    help: ffmpegPath
      ? ""
      : "Install FFmpeg or put ffmpeg.exe at tools/ffmpeg/bin/ffmpeg.exe, or set SHULKR_FFMPEG_PATH."
  };
}

function findMinecraftWindowRect(titleHint = "") {
  if (process.platform !== "win32") return null;
  const safeTitle = JSON.stringify(String(titleHint || "Minecraft"));
  const script = `
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShulkrWindowRect {
  [DllImport("user32.dll")]
  public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
  [DllImport("user32.dll")]
  public static extern int GetSystemMetrics(int nIndex);
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@
$hint = ${safeTitle}
$window = Get-Process | Where-Object {
  $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -and (
    $_.MainWindowTitle -like "*Minecraft*" -or
    $_.MainWindowTitle -like "*Shulkr*" -or
    ($hint -and $_.MainWindowTitle -like $hint)
  )
} | Sort-Object @{Expression={
  if ($_.MainWindowTitle -like "*Minecraft*" -or $_.MainWindowTitle -like "*Shulkr*") { 0 }
  else { 1 }
}} | Select-Object -First 1
if ($window) {
  $rect = New-Object ShulkrWindowRect+RECT
  [ShulkrWindowRect]::GetWindowRect($window.MainWindowHandle, [ref]$rect) | Out-Null
  $virtualX = [ShulkrWindowRect]::GetSystemMetrics(76)
  $virtualY = [ShulkrWindowRect]::GetSystemMetrics(77)
  $virtualW = [ShulkrWindowRect]::GetSystemMetrics(78)
  $virtualH = [ShulkrWindowRect]::GetSystemMetrics(79)
  $left = [Math]::Max($virtualX, $rect.Left)
  $top = [Math]::Max($virtualY, $rect.Top)
  $right = [Math]::Min($virtualX + $virtualW, $rect.Right)
  $bottom = [Math]::Min($virtualY + $virtualH, $rect.Bottom)
  $width = [Math]::Max(1, $right - $left)
  $height = [Math]::Max(1, $bottom - $top)
  [pscustomobject]@{
    title = $window.MainWindowTitle
    x = $left
    y = $top
    width = $width
    height = $height
  } | ConvertTo-Json -Compress
}
`;
  try {
    const output = execFileSync("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-Command", script
    ], {
      encoding: "utf8",
      timeout: 3000,
      windowsHide: true
    }).trim();
    if (!output) return null;
    const rect = JSON.parse(output);
    if (!rect || rect.width < 120 || rect.height < 90) return null;
    return rect;
  } catch (error) {
    streamState.lastError = `Window rect lookup failed: ${error.message}`;
    return null;
  }
}

function ffmpegArgs(options = {}) {
  const fps = Math.max(5, Math.min(60, Number(options.fps || streamState.fps || 20)));
  streamState.mode = "window";
  streamState.fps = fps;
  streamState.captureRect = null;
  streamState.captureTitle = "";
  const rect = findMinecraftWindowRect(options.title);
  if (!rect) {
    streamState.captureTitle = String(options.title || "Minecraft");
    streamState.lastError = "Minecraft window was not found. Desktop capture is disabled for privacy.";
    throw new Error(streamState.lastError);
  }
  streamState.captureRect = rect;
  streamState.captureTitle = rect.title || "Minecraft";
  const input = [
    "-f", "gdigrab",
    "-framerate", String(fps),
    "-draw_mouse", "0",
    "-i", `title=${rect.title}`
  ];
  return [
    "-hide_banner",
    "-loglevel", "warning",
    ...input,
    "-vf", "scale=1280:-1",
    "-q:v", String(Math.max(2, Math.min(15, Number(options.quality || 7)))),
    "-f", "mjpeg",
    "pipe:1"
  ];
}

function startStream(options = {}, owner = {}) {
  if (streamState.process) {
    if (streamState.ownerId && streamState.ownerId !== String(owner.userId || "")) throw automationError(403, "STREAM_OWNERSHIP_REQUIRED", "The active stream belongs to another account");
    if (streamState.clientId && streamState.clientId !== String(owner.clientId || "")) throw automationError(409, "STREAM_CLIENT_CONFLICT", "The active stream belongs to another Minecraft client");
    return streamStatus();
  }
  const ffmpegPath = findFfmpeg();
  if (!ffmpegPath) {
    streamState.lastError = "FFmpeg was not found.";
    throw new Error(streamStatus().help);
  }
  const args = ffmpegArgs(options);
  streamState.lastError = "";
  streamState.startedAt = new Date().toISOString();
  streamState.ownerId = String(owner.userId || "");
  streamState.clientId = String(owner.clientId || "");
  const child = spawn(ffmpegPath, args, {
    cwd: rootDir,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"]
  });
  streamState.process = child;
  child.on("error", (error) => {
    streamState.lastError = `FFmpeg failed to start: ${error.message}`;
    if (streamState.process === child) {
      streamState.process = null;
      streamState.startedAt = null;
    }
  });
  child.stderr.on("data", (chunk) => {
    streamState.lastError = chunk.toString("utf8").trim().slice(-600);
  });
  child.on("exit", (code, signal) => {
    if (streamState.process === child) {
      streamState.process = null;
      streamState.startedAt = null;
    }
    if (code && code !== 0) {
      streamState.lastError = streamState.lastError || `FFmpeg exited with code ${code}${signal ? ` (${signal})` : ""}.`;
    }
    for (const res of streamState.clients) {
      try {
        res.end();
      } catch {
      }
    }
    streamState.clients.clear();
  });
  return streamStatus();
}

function stopStream() {
  if (streamState.process) {
    const child = streamState.process;
    streamState.process = null;
    try {
      child.kill("SIGTERM");
    } catch {
    }
  }
  streamState.startedAt = null;
  streamState.ownerId = "";
  streamState.clientId = "";
  for (const res of streamState.clients) {
    try {
      res.end();
    } catch {
    }
  }
  streamState.clients.clear();
  return streamStatus();
}

function pipeMjpeg(res) {
  if (!streamState.process) throw automationError(409, "STREAM_NOT_RUNNING", "The Minecraft stream is not running");
  const child = streamState.process;
  const boundary = "shulkr-frame";
  let buffer = Buffer.alloc(0);
  res.writeHead(200, {
    "Cache-Control": "no-store, no-cache, must-revalidate, proxy-revalidate",
    "Connection": "close",
    "Content-Type": `multipart/x-mixed-replace; boundary=${boundary}`,
    "Pragma": "no-cache"
  });
  streamState.clients.add(res);
  const onData = (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (true) {
      const start = buffer.indexOf(Buffer.from([0xff, 0xd8]));
      const end = buffer.indexOf(Buffer.from([0xff, 0xd9]), start + 2);
      if (start < 0 || end < 0) {
        if (buffer.length > 1024 * 1024) buffer = buffer.subarray(-1024 * 256);
        return;
      }
      const frame = buffer.subarray(start, end + 2);
      buffer = buffer.subarray(end + 2);
      res.write(`--${boundary}\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.length}\r\n\r\n`);
      res.write(frame);
      res.write("\r\n");
    }
  };
  const cleanup = () => {
    streamState.clients.delete(res);
    child.stdout.off("data", onData);
  };
  child.stdout.on("data", onData);
  res.on("close", cleanup);
}

function signAuthToken(user) {
  return jwt.sign({
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    username: user.username || "",
    tier: user.tier || "Premium",
    features: Array.isArray(user.features) ? user.features : [],
    isAdmin: Boolean(user.isAdmin)
  }, authSecret, { expiresIn: "12h", issuer: "shulkr-backend", audience: "shulkr-dashboard" });
}

function setAuthCookie(res, token) {
  res.cookie(AUTH_COOKIE, token, {
    httpOnly: true,
    sameSite: "strict",
    secure: backendUrl.startsWith("https://"),
    path: "/api",
    maxAge: 12 * 60 * 60 * 1000
  });
}

function publicUser(user) {
  return {
    id: user.id,
    displayName: user.displayName,
    username: user.username || "",
    email: user.email || "",
    tier: user.tier,
    isAdmin: Boolean(user.isAdmin),
    features: Array.isArray(user.features) ? user.features : []
  };
}

function authFailureKey(req, login = "") {
  return `${req.ip || req.socket?.remoteAddress || "local"}|${String(login).trim().toLowerCase().slice(0, 200)}`;
}

function assertAuthAttemptAllowed(req, login) {
  if (authFailures.size > 5000) {
    const now = Date.now();
    for (const [candidate, value] of authFailures) if (value.resetAt <= now) authFailures.delete(candidate);
    while (authFailures.size > 5000) authFailures.delete(authFailures.keys().next().value);
  }
  const key = authFailureKey(req, login);
  const current = authFailures.get(key);
  if (!current || current.resetAt <= Date.now()) {
    authFailures.delete(key);
    return key;
  }
  if (current.count >= AUTH_MAX_FAILURES) throw automationError(429, "AUTH_RATE_LIMITED", "Too many authentication attempts. Try again later.");
  return key;
}

function recordAuthFailure(key) {
  const current = authFailures.get(key);
  authFailures.set(key, !current || current.resetAt <= Date.now()
    ? { count: 1, resetAt: Date.now() + AUTH_WINDOW_MS }
    : { ...current, count: current.count + 1 });
}

function clearAuthFailures(key) {
  authFailures.delete(key);
}

async function publishLibraryAutomation(input, ownerId) {
  const graph = structuredClone(input.graph || input);
  validateAutomationGraph(graph);
  const compile = await trustedAutomationCompilation(graph);
  if (compile.errors.length) throw automationError(422, "AUTOMATION_COMPILE_INVALID", "Automation cannot be published until compilation succeeds", { errors: compile.errors });
  const item = normalizeAutomationListing({ ...input, graph, generatedCodePreview: compile.code }, ownerId);
  item.id = input?.id ? slug(input.id) : await uniqueLibraryId(item.title);
  item.generatedCodePreview = compile.code;
  item.publishedAt = Date.now(); item.updatedAt = item.publishedAt;
  const items = await libraryScripts();
  items.unshift(item);
  await saveLibraryScripts(items);
  return item;
}

async function importLibraryAutomation(id, userId) {
  const items = await libraryScripts();
  const item = items.find(candidate => candidate.id === id && candidate.kind === "automation");
  if (!item || (item.visibility === "private" && item.ownerId !== userId)) throw automationError(404, "AUTOMATION_LISTING_NOT_FOUND", "Automation listing not found");
  const graph = structuredClone(item.graph);
  validateAutomationGraph(graph);
  const compile = await trustedAutomationCompilation(graph);
  if (compile.errors.length) throw automationError(422, "AUTOMATION_COMPILE_INVALID", "Published automation is no longer compilable");
  graph.id = `automation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  graph.name = `${item.title} Copy`;
  graph.description = item.description;
  graph.generatedCode = compile.code;
  const created = await createUserAutomation(userId, graph);
  item.installs += 1; item.updatedAt = Date.now();
  await saveLibraryScripts(items);
  return { graph: created, listing: item, requiredPermissions: item.requiredPermissions };
}

function verifyAuthToken(token) {
  try {
    return { ok: true, payload: jwt.verify(token, authSecret, { issuer: "shulkr-backend", audience: "shulkr-dashboard" }) };
  } catch (error) {
    return { ok: false, reason: error?.name || "InvalidToken" };
  }
}

function cookieValue(req, name) {
  const cookies = String(req.headers.cookie || "").split(";");
  for (const cookie of cookies) {
    const separator = cookie.indexOf("=");
    if (separator < 0) continue;
    if (cookie.slice(0, separator).trim() === name) return decodeURIComponent(cookie.slice(separator + 1).trim());
  }
  return "";
}

function createStreamTicket(user, clientId) {
  return jwt.sign({ sub: user.id, clientId, scope: "remote.stream", type: "stream" }, authSecret, { expiresIn: "2m", issuer: "shulkr-backend", audience: "shulkr-stream" });
}

function streamTicketMiddleware(req, res, next) {
  let verified;
  try {
    verified = { ok: true, payload: jwt.verify(cookieValue(req, STREAM_COOKIE), authSecret, { issuer: "shulkr-backend", audience: "shulkr-stream" }) };
  } catch {
    verified = { ok: false };
  }
  if (!verified.ok || verified.payload?.type !== "stream" || verified.payload?.scope !== "remote.stream") {
    return res.status(401).json({ error: "Stream session expired" });
  }
  if (streamState.ownerId && String(verified.payload.sub || "") !== streamState.ownerId) return res.status(403).json({ error: "Stream ownership required" });
  if (streamState.clientId && String(verified.payload.clientId || "") !== streamState.clientId) return res.status(403).json({ error: "Stream client mismatch" });
  req.streamUserId = String(verified.payload.sub || "");
  next();
}

async function syncGoogleProfile(user) {
  const current = await readJson(profilePath, defaultProfile());
  const entitlements = await resolveEntitlements({ ...user, tier: user.tier || "Premium" });
  const next = {
    ...current,
    id: user.id,
    displayName: user.displayName,
    email: user.email,
    avatar: user.avatar || current.avatar,
    username: user.username || current.username || user.displayName,
    tier: entitlements.tier,
    features: entitlements.features,
    connected: true,
    lastSeenAt: new Date().toISOString()
  };
  await writeJson(profilePath, next);
  await upsertLicenseForUser(user, entitlements);
  return next;
}

async function hydrateAuthenticatedUser(payload) {
  if (!payload?.id) return null;
  const users = await readJson(usersPath, []);
  const stored = users.find((user) => user.id === payload.id) || {
    id: payload.id,
    displayName: payload.displayName,
    username: payload.username,
    email: payload.email,
    tier: payload.tier
  };
  const entitlements = await resolveEntitlements(stored);
  return {
    ...stored,
    displayName: stored.displayName || payload.displayName,
    username: stored.username || payload.username || "",
    email: stored.email || payload.email || "",
    tier: entitlements.tier,
    features: entitlements.features,
    isAdmin: entitlements.isAdmin,
    stripeCustomerId: stored.stripeCustomerId || "",
    stripeSubscriptionId: stored.stripeSubscriptionId || "",
    stripePriceId: stored.stripePriceId || "",
    stripeStatus: stored.stripeStatus || ""
  };
}

async function authMiddleware(req, res, next) {
  const header = req.headers.authorization || "";
  const bearerToken = header.startsWith("Bearer ") ? header.slice(7) : "";
  const cookieToken = cookieValue(req, AUTH_COOKIE);
  const token = bearerToken || cookieToken;
  const verified = verifyAuthToken(token);
  if (!verified.ok) {
    void recordTamperEvent("auth.invalid_token", {
      severity: "error",
      message: "Authentication token was rejected",
      route: req.originalUrl,
      ip: req.ip,
      metadata: { reason: verified.reason }
    });
    return res.status(401).json({ error: "Unauthorized" });
  }
  req.user = await hydrateAuthenticatedUser(verified.payload);
  if (!req.user) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (!bearerToken && !["GET", "HEAD", "OPTIONS"].includes(req.method) && req.headers["x-shulkr-request"] !== "dashboard") {
    return res.status(403).json({ error: "Authenticated dashboard request header required", code: "CSRF_CHECK_FAILED" });
  }
  next();
}

function requireFeature(feature) {
  return async (req, res, next) => {
    await authMiddleware(req, res, async () => {
      if (req.user?.isAdmin || req.user?.features?.includes(feature)) {
        return next();
      }
      void recordTamperEvent("auth.feature_denied", {
        severity: "warn",
        message: `Feature denied: ${feature}`,
        actorId: req.user?.id,
        actorEmail: req.user?.email,
        route: req.originalUrl,
        ip: req.ip,
        metadata: { feature }
      });
      return res.status(403).json({ error: "This account does not have access to that feature" });
    });
  };
}

function requireAdmin(req, res, next) {
  authMiddleware(req, res, () => {
    if (req.user?.isAdmin) {
      return next();
    }
    void recordTamperEvent("auth.admin_denied", {
      severity: "error",
      message: "Admin route access denied",
      actorId: req.user?.id,
      actorEmail: req.user?.email,
      route: req.originalUrl,
      ip: req.ip
    });
    return res.status(403).json({ error: "Admin access required" });
  });
}

app.get("/api/health", async (_req, res) => {
  res.json({
    ok: true,
    app: "shulkr",
    backend: "express",
    auth: { google: Boolean(googleClientId && googleClientSecret), local: true },
    billing: { stripe: stripeReady() }
  });
});

app.get("/api/billing/plans", async (_req, res, next) => {
  try {
    res.json(await billingPlansResponse());
  } catch (error) {
    next(error);
  }
});

app.post("/api/stripe/webhook", async (req, res) => {
  if (!stripe || !stripeWebhookSecret) {
    return res.status(503).json({ error: "Stripe webhook is not configured" });
  }
  try {
    const event = stripe.webhooks.constructEvent(req.rawBody, req.headers["stripe-signature"], stripeWebhookSecret);
    if (event.type === "checkout.session.completed") {
      const session = event.data.object;
      if (session.mode === "subscription" && session.metadata?.userId && session.customer) {
        await updateStoredUser(String(session.metadata.userId), {
          stripeCustomerId: typeof session.customer === "string" ? session.customer : session.customer?.id || "",
          stripeSubscriptionId: typeof session.subscription === "string" ? session.subscription : session.subscription?.id || ""
        });
      }
    }
    if (event.type === "customer.subscription.created" || event.type === "customer.subscription.updated" || event.type === "customer.subscription.deleted") {
      await syncStripeSubscription(event.data.object);
    }
    res.json({ received: true });
  } catch (error) {
    return res.status(400).send(`Webhook Error: ${error.message}`);
  }
});

app.post("/api/auth/local/signin", async (req, res, next) => {
  try {
    const { email, username, password } = req.body || {};
    const login = String(email || username || "").trim();
    if (!login || !password) throw new Error("Username/email and password are required");
    const failureKey = assertAuthAttemptAllowed(req, login);
    const user = await findLocalUser(login);
    if (!user || !verifyPassword(user, password)) {
      recordAuthFailure(failureKey);
      void recordTamperEvent("auth.bad_credentials", {
        severity: "warn",
        message: "Failed local sign-in attempt",
        route: req.originalUrl,
        ip: req.ip,
        metadata: { login }
      });
      throw automationError(401, "AUTH_INVALID_CREDENTIALS", "Invalid username, email, or password");
    }
    clearAuthFailures(failureKey);
    if (!user.passwordHash) {
      const migrated = await updateStoredUser(user.id, current => {
        const { password: _legacyPassword, ...safe } = current;
        return { ...safe, passwordHash: hashPassword(password) };
      });
      if (migrated) Object.assign(user, migrated);
    }
    const profile = await syncLocalProfile(user);
    const authedUser = await hydrateAuthenticatedUser(user);
    const token = signAuthToken(authedUser);
    setAuthCookie(res, token);
    res.json({
      token,
      user: { ...publicUser(authedUser), profileTier: profile.tier || authedUser.tier }
    });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/local/signup", async (req, res, next) => {
  try {
    const { displayName, email, password } = req.body || {};
    if (!email || !password) throw new Error("Email and password are required");
    const failureKey = assertAuthAttemptAllowed(req, email);
    assertStrongPassword(password);
    const user = await createLocalUser(String(displayName || ""), String(email), String(password));
    clearAuthFailures(failureKey);
    const profile = await syncLocalProfile(user);
    const authedUser = await hydrateAuthenticatedUser(user);
    const token = signAuthToken(authedUser);
    setAuthCookie(res, token);
    res.status(201).json({
      token,
      user: { ...publicUser(authedUser), profileTier: profile.tier || authedUser.tier }
    });
  } catch (error) {
    next(error);
  }
});

app.get("/auth/google", (req, res, next) => {
  if (!googleClientId || !googleClientSecret) {
    return res.status(503).json({ error: "Google OAuth is not configured. Set SHULKR_GOOGLE_CLIENT_ID and SHULKR_GOOGLE_CLIENT_SECRET." });
  }
  passport.authenticate("google", { scope: ["profile", "email"] })(req, res, next);
});

app.get("/auth/google/callback",
  passport.authenticate("google", { failureRedirect: `${webClientUrl}/?auth=error` }),
  async (req, res) => {
    const profile = await syncGoogleProfile(req.user);
    const authedUser = await hydrateAuthenticatedUser(req.user);
    const code = crypto.randomBytes(32).toString("base64url");
    oauthExchangeCodes.set(code, { user: publicUser(authedUser), expiresAt: Date.now() + 60000 });
    res.redirect(`${webClientUrl}/?auth_code=${encodeURIComponent(code)}`);
  }
);

app.post("/api/auth/exchange", async (req, res, next) => {
  try {
    const code = String(req.body?.code || "");
    const failureKey = assertAuthAttemptAllowed(req, `oauth:${code.slice(0, 12)}`);
    const exchange = oauthExchangeCodes.get(code);
    oauthExchangeCodes.delete(code);
    if (!exchange || exchange.expiresAt <= Date.now()) {
      recordAuthFailure(failureKey);
      throw automationError(401, "AUTH_CODE_INVALID", "Sign-in code is invalid or expired");
    }
    clearAuthFailures(failureKey);
    const authedUser = await hydrateAuthenticatedUser(exchange.user);
    if (!authedUser) throw automationError(401, "AUTH_CODE_INVALID", "Sign-in code is invalid or expired");
    const token = signAuthToken(authedUser);
    setAuthCookie(res, token);
    res.json({ user: publicUser(authedUser) });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/logout", authMiddleware, (_req, res) => {
  res.clearCookie(AUTH_COOKIE, { path: "/api", sameSite: "strict", secure: backendUrl.startsWith("https://") });
  res.clearCookie(STREAM_COOKIE, { path: "/api/stream/mjpeg", sameSite: "strict", secure: backendUrl.startsWith("https://") });
  res.json({ ok: true });
});

app.get("/api/auth/me", authMiddleware, async (req, res) => {
  const profile = await readJson(profilePath, defaultProfile());
  res.json({
    id: req.user.id,
    displayName: req.user.displayName,
    email: req.user.email,
    username: req.user.username || profile.username || profile.displayName || req.user.displayName,
    tier: req.user.tier || profile.tier || "Premium",
    avatar: profile.avatar || "",
    isAdmin: Boolean(req.user.isAdmin),
    features: req.user.features || []
  });
});

app.get("/api/billing/status", authMiddleware, async (req, res, next) => {
  try {
    res.json(await billingStatusForUser(req.user));
  } catch (error) {
    next(error);
  }
});

app.post("/api/billing/checkout", authMiddleware, async (req, res, next) => {
  try {
    if (!stripeReady()) throw new Error("Stripe is not configured on this backend yet");
    if (req.user.isAdmin) throw new Error("Admin accounts do not need a paid plan");
    const requestedTier = normalizeTierName(req.body?.tier || "");
    if (!stripePlanCatalog[requestedTier]) throw new Error("Unknown billing plan");
    const plans = await ensureStripePlans();
    const plan = plans.find((entry) => entry.key === requestedTier);
    if (!plan?.priceId) throw new Error("Stripe price is unavailable");
    const customerId = await ensureStripeCustomer(req.user);
    const successUrl = `${webClientUrl}/?checkout=success&tier=${encodeURIComponent(plan.key)}`;
    const cancelUrl = `${webClientUrl}/?checkout=cancelled`;
    const session = await stripe.checkout.sessions.create({
      mode: "subscription",
      customer: customerId,
      success_url: successUrl,
      cancel_url: cancelUrl,
      allow_promotion_codes: true,
      line_items: [{ price: plan.priceId, quantity: 1 }],
      metadata: {
        userId: req.user.id,
        tier: plan.key
      },
      subscription_data: {
        metadata: {
          userId: req.user.id,
          tier: plan.key
        }
      }
    });
    res.status(201).json({ url: session.url, id: session.id });
  } catch (error) {
    next(error);
  }
});

app.post("/api/billing/portal", authMiddleware, async (req, res, next) => {
  try {
    if (!stripeReady()) throw new Error("Stripe is not configured on this backend yet");
    const customerId = req.user.stripeCustomerId || (await ensureStripeCustomer(req.user));
    const session = await stripe.billingPortal.sessions.create({
      customer: customerId,
      return_url: `${webClientUrl}/?billing=portal`
    });
    res.status(201).json({ url: session.url });
  } catch (error) {
    next(error);
  }
});

app.post("/api/billing/sync", authMiddleware, async (req, res, next) => {
  try {
    if (!stripeReady()) throw new Error("Stripe is not configured on this backend yet");
    const customerId = req.user.stripeCustomerId;
    if (!customerId) {
      await upsertLicenseForTier(req.user.id, "Free", { status: "" });
      return res.json(await billingStatusForUser(await hydrateAuthenticatedUser(req.user)));
    }
    const subscriptions = await stripe.subscriptions.list({
      customer: customerId,
      status: "all",
      limit: 10
    });
    const active = subscriptions.data.find((subscription) => ["active", "trialing"].includes(String(subscription.status || "").toLowerCase()));
    if (active) {
      await syncStripeSubscription(active);
    } else {
      await upsertLicenseForTier(req.user.id, "Free", {
        customerId,
        subscriptionId: "",
        priceId: "",
        status: ""
      });
    }
    const refreshed = await hydrateAuthenticatedUser(req.user);
    res.json(await billingStatusForUser(refreshed));
  } catch (error) {
    next(error);
  }
});

app.get("/api/profile", authMiddleware, async (req, res) => {
  const profile = await readJson(profilePath, defaultProfile());
  res.json({
    id: req.user.id,
    displayName: req.user.displayName,
    username: req.user.username || "",
    email: req.user.email || "",
    avatar: profile.id === req.user.id ? String(profile.avatar || "") : "",
    tier: req.user.tier,
    features: req.user.features,
    connected: true,
    lastSeenAt: profile.id === req.user.id ? profile.lastSeenAt : null
  });
});

app.get("/api/clients", authMiddleware, async (req, res) => {
  const clients = await connectedClients();
  res.json(req.user.isAdmin ? clients : clients.filter(client => String(client.licenseUserId || "") === String(req.user.id)));
});

app.get("/api/licenses", authMiddleware, async (req, res) => {
  const licenses = await readJson(licensesPath, defaultLicenses());
  res.json(req.user?.isAdmin ? licenses : licenses.filter((license) => license.userId === req.user.id));
});

app.get("/api/libraries", requireFeature("libraries.read"), async (_req, res) => {
  res.json(await readJson(librariesPath, defaultLibraries()));
});

app.post("/api/licenses", requireAdmin, async (req, res) => {
  const licenses = await readJson(licensesPath, defaultLicenses());
  const body = req.body || {};
  const userId = String(body.userId || "").trim();
  if (!/^[a-zA-Z0-9_.@-]{1,160}$/.test(userId)) return res.status(422).json({ error: "A valid user id is required", code: "LICENSE_USER_INVALID" });
  const existingIndex = licenses.findIndex(license => String(license.userId || "") === userId);
  const existing = existingIndex >= 0 ? licenses[existingIndex] : {};
  const tierKey = normalizeTierName(body.tier || existing.tier || "free");
  const status = ["active", "inactive", "cancelled", "expired"].includes(String(body.status || existing.status || "active").toLowerCase())
    ? String(body.status || existing.status || "active").toLowerCase()
    : "inactive";
  const id = String(existing.id || body.id || `${slug(body.displayName || userId)}-${Date.now()}`).slice(0, 180);
  const next = {
    ...existing,
    id,
    userId,
    displayName: String(body.displayName || existing.displayName || "Shulkr User").slice(0, 120),
    tier: tierCatalog[tierKey].tier,
    status,
    seats: Math.max(1, Math.min(100, Number(body.seats || existing.seats || 1) || 1)),
    expiresAt: body.expiresAt || existing.expiresAt || null,
    features: Array.isArray(body.features) ? [...new Set(body.features.map(String).filter(feature => feature.length <= 100))].slice(0, 100) : featureSetForTier(tierKey)
  };
  if (existingIndex >= 0) licenses[existingIndex] = next;
  else licenses.push(next);
  await writeJson(licensesPath, licenses);
  res.status(existingIndex >= 0 ? 200 : 201).json(next);
});

app.post("/api/clients/heartbeat", async (req, res) => {
  const deviceId = normalizeDeviceId(req.body?.deviceId || req.body?.hardwareId || req.body?.id);
  const supplied = suppliedDeviceToken(req);
  const bootstrap = req.headers["x-shulkr-device-bootstrap"] === "1" && !req.headers.origin;
  if (supplied ? !deviceTokenMatches(deviceId, supplied) : !bootstrap) {
    return res.status(401).json({ error: "Minecraft device authentication required", code: "DEVICE_AUTH_REQUIRED" });
  }
  const heartbeat = await upsertClientHeartbeat(req.body || {});
  res.json({ ...heartbeat, deviceToken: deviceTokenFor(deviceId) });
});

app.get("/api/control/state", requireFeature("remote.control"), async (req, res) => {
  const [clients, scriptList, clientModules] = await Promise.all([
    connectedClients(), scripts(), readJson(clientModulesPath, defaultClientModules())
  ]);
  res.json({
    clients: req.user.isAdmin ? clients : clients.filter(client => String(client.licenseUserId || "") === String(req.user.id)),
    scripts: scriptList,
    modules: clientModules,
    activity: controlActivity.slice(0, 20),
    overlays: ["Target HUD", "Coordinates", "Script Status", "NBT Peek", "FPS Counter", "Player Vitals", "Crosshair Inspector"]
  });
});

app.get("/api/stats/history", requireFeature("stats.read"), async (req, res, next) => {
  try {
    const analytics = await analyticsResponse(await analyticsFiltersForUser(req));
    res.json(analytics.history);
  } catch (error) { next(error); }
});

app.get("/api/stats/summary", requireFeature("stats.read"), async (req, res, next) => {
  try {
    const analytics = await analyticsResponse(await analyticsFiltersForUser(req));
    res.json(analytics.summary);
  } catch (error) { next(error); }
});

app.get("/api/stats/analytics", requireFeature("stats.read"), async (req, res, next) => {
  try {
    res.json(await analyticsResponse(await analyticsFiltersForUser(req)));
  } catch (error) { next(error); }
});

app.get("/api/stream/status", requireFeature("remote.stream"), (req, res) => {
  res.json(streamStatus(req.user));
});

app.post("/api/stream/session", requireFeature("remote.stream"), async (req, res, next) => {
  try {
  const client = await ownedConnectedClient(req.user, req.body?.clientId);
  res.cookie(STREAM_COOKIE, createStreamTicket(req.user, client.id), {
    httpOnly: true,
    sameSite: "strict",
    secure: false,
    path: "/api/stream/mjpeg",
    maxAge: 120000
  });
  res.json({ ok: true, expiresInSeconds: 120 });
  } catch (error) { next(error); }
});

app.post("/api/stream/start", requireFeature("remote.stream"), async (req, res, next) => {
  try {
    const client = await ownedConnectedClient(req.user, req.body?.clientId);
    res.json(startStream(req.body || {}, { userId: req.user.id, clientId: client.id }));
  } catch (error) {
    next(error);
  }
});

app.post("/api/stream/stop", requireFeature("remote.stream"), (req, res) => {
  if (streamState.ownerId && streamState.ownerId !== req.user.id && !req.user.isAdmin) return res.status(403).json({ error: "Stream ownership required" });
  res.json(stopStream());
});

app.get("/api/stream/mjpeg", streamTicketMiddleware, (_req, res, next) => {
  try {
    pipeMjpeg(res);
  } catch (error) {
    next(error);
  }
});

app.post("/api/control/commands", requireFeature("remote.control"), async (req, res, next) => {
  try {
    const type = String(req.body?.type || "");
    const allowed = new Set(["run_script", "stop_scripts", "set_overlay", "set_renderer", "open_ui", "take_screenshot", "send_chat"]);
    if (!allowed.has(type)) throw new Error("Unsupported control command");
    const client = await ownedConnectedClient(req.user, req.body?.clientId || "local-user");
    const payload = await normalizeControlPayload(type, req.body?.payload);
    const command = queueControlCommand({ clientId: client.id, type, payload, userId: req.user.id });
    addControlActivity("Command queued", type.replaceAll("_", " "), "pending");
    await recordCommandQueuedAnalytics(command);
    res.status(202).json(publicControlCommand(command));
  } catch (error) {
    next(error);
  }
});

app.get("/api/control/commands", async (req, res) => {
  const clientId = String(req.query.clientId || "local-user");
  if (!requireDeviceToken(req, res, clientId)) return;
  const now = Date.now();
  const delivered = controlCommands.filter((command) => command.clientId === clientId && Date.parse(command.expiresAt || 0) > now);
  for (let index = controlCommands.length - 1; index >= 0; index -= 1) {
    if (Date.parse(controlCommands[index].expiresAt || 0) <= now) {
      const expired = controlCommands.splice(index, 1)[0];
      commandStatsIndex.delete(expired.id);
      if (expired.executionId) updateAutomationExecution(expired.executionId, { status: "failed" }, "Execution request expired before the client received it.");
    }
  }
  for (const command of delivered) {
    const index = controlCommands.findIndex((candidate) => candidate.id === command.id);
    if (index >= 0) controlCommands.splice(index, 1);
  }
  res.json(delivered.map(publicControlCommand));
});

app.post("/api/control/commands/:id/ack", async (req, res) => {
  const command = commandStatsIndex.get(String(req.params.id || ""));
  if (!command) return res.status(404).json({ error: "Command not found" });
  if (!requireDeviceToken(req, res, command.clientId)) return;
  const ok = req.body?.ok !== false;
  const message = String(req.body?.message || req.params.id).slice(0, 500);
  addControlActivity(ok ? "Command completed" : "Command failed", message, ok ? "ok" : "error");
  commandStatsIndex.delete(String(req.params.id || ""));
  if (command?.executionId) {
    const cancelled = command.type === "stop_scripts" && ok;
    updateAutomationExecution(command.executionId, { status: cancelled ? "cancelled" : ok ? "running" : "failed" }, message || (ok ? "Minecraft client accepted the execution." : "Minecraft client rejected the execution."));
  }
  await recordCommandAckAnalytics(command, ok);
  res.json({ ok: true });
});

async function updateOwnProfile(req, res) {
  const displayName = String(req.body?.displayName ?? req.user.displayName).trim().slice(0, 80);
  const username = String(req.body?.username ?? req.user.username ?? "").trim().slice(0, 40);
  const avatar = String(req.body?.avatar || "").trim().slice(0, 2048);
  if (!displayName) return res.status(400).json({ error: "Display name is required" });
  const stored = await updateStoredUser(req.user.id, current => ({ ...current, displayName, username }));
  const current = await readJson(profilePath, defaultProfile());
  const next = {
    ...current,
    id: req.user.id,
    displayName,
    username,
    email: stored?.email || req.user.email || "",
    avatar,
    tier: req.user.tier,
    features: req.user.features,
    lastSeenAt: new Date().toISOString()
  };
  await writeJson(profilePath, next);
  res.json(next);
}

app.post("/api/profile", authMiddleware, updateOwnProfile);
app.patch("/api/profile", authMiddleware, updateOwnProfile);

app.get("/api/library/scripts", requireFeature("script-library"), async (req, res) => {
  const items = await libraryScripts();
  res.json(items.filter(item => item.kind !== "automation" || item.visibility === "public" || item.ownerId === req.user.id));
});

app.post("/api/library/scripts", requireFeature("script-library.publish"), async (req, res, next) => {
  try {
    const body = typeof req.body === "string" ? { content: req.body } : (req.body || {});
    if (body.kind === "automation" || body.graph) return res.status(201).json(await publishLibraryAutomation(body, req.user.id));
    let payload = body;
    if (body.sourcePath) {
      throw automationError(400, "SOURCE_PATH_FORBIDDEN", "Server filesystem paths are not accepted; upload script content instead");
    } else if (body.base64) {
      payload = { ...body, code: decodeBase64Text(body.base64) };
    }
    res.status(201).json(await publishLibraryScript(payload));
  } catch (error) {
    next(error);
  }
});

app.get("/api/library/scripts/:id", requireFeature("script-library"), async (req, res, next) => {
  try {
    const item = (await libraryScripts()).find((script) => script.id === req.params.id);
    if (!item || (item.kind === "automation" && item.visibility === "private" && item.ownerId !== req.user.id)) throw new Error("Published script not found");
    res.json(item);
  } catch (error) {
    next(error);
  }
});

app.delete("/api/library/scripts/:id", requireFeature("script-library.delete"), async (req, res, next) => {
  try {
    const items = await libraryScripts();
    const nextItems = items.filter((script) => script.id !== req.params.id);
    if (nextItems.length === items.length) throw new Error("Published script not found");
    await saveLibraryScripts(nextItems);
    res.json({ ok: true, id: req.params.id });
  } catch (error) {
    next(error);
  }
});

app.post("/api/library/scripts/:id/install", requireFeature("script-library"), async (req, res, next) => {
  try {
    const listing = (await libraryScripts()).find(item => item.id === req.params.id);
    if (listing?.kind === "automation") return res.status(201).json(await importLibraryAutomation(req.params.id, req.user.id));
    res.status(201).json(await installLibraryScript(req.params.id));
  } catch (error) {
    next(error);
  }
});

app.get("/api/client-modules", requireFeature("client-modules.read"), async (_req, res) => {
  res.json(await readJson(clientModulesPath, defaultClientModules()));
});

app.post("/api/library/scripts/:id/report", requireFeature("script-library"), async (req, res, next) => {
  try {
    const item = (await libraryScripts()).find(script => script.id === req.params.id);
    if (!item) throw automationError(404, "LIBRARY_ITEM_NOT_FOUND", "Published item not found");
    const reports = await readJson(libraryReportsPath, []);
    const report = { id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, itemId: item.id, reporterId: req.user.id, reason: String(req.body?.reason || "unspecified").slice(0, 500), createdAt: Date.now() };
    reports.unshift(report); await writeJson(libraryReportsPath, reports.slice(0, 1000));
    res.status(201).json({ ok: true, reportId: report.id });
  } catch (error) { next(error); }
});

app.post("/api/library/scripts/:id/versions", requireFeature("script-library.publish"), async (req, res, next) => {
  try {
    const items = await libraryScripts();
    const current = items.find(script => script.id === req.params.id);
    if (!current || current.kind !== "automation") throw automationError(404, "LIBRARY_ITEM_NOT_FOUND", "Automation listing not found");
    if (current.ownerId && current.ownerId !== req.user.id && !req.user.isAdmin) throw automationError(403, "LIBRARY_OWNERSHIP_REQUIRED", "Only the owner may publish a new version");
    const graph = structuredClone(req.body?.graph || {}); validateAutomationGraph(graph);
    const compile = await trustedAutomationCompilation(graph);
    if (compile.errors.length) throw automationError(422, "AUTOMATION_COMPILE_INVALID", "Automation version cannot be published", { errors: compile.errors });
    const next = normalizeAutomationListing({ ...current, ...req.body, graph, version: String(req.body?.version || current.version), generatedCodePreview: compile.code, changelog: String(req.body?.changelog || "Updated automation") }, current.ownerId || req.user.id);
    next.id = current.id; next.publishedAt = current.publishedAt; next.updatedAt = Date.now();
    await saveLibraryScripts(items.map(item => item.id === current.id ? next : item));
    res.status(201).json(next);
  } catch (error) { next(error); }
});

app.patch("/api/client-modules/:id", requireAdmin, async (req, res, next) => {
  try {
    const items = await readJson(clientModulesPath, defaultClientModules());
    const index = items.findIndex((item) => item.id === req.params.id);
    if (index < 0) throw new Error("Module not found");
    if (!Object.prototype.hasOwnProperty.call(req.body || {}, "installed") || typeof req.body.installed !== "boolean") throw automationError(422, "MODULE_UPDATE_INVALID", "Only the installed state may be changed");
    items[index] = { ...items[index], installed: req.body.installed, status: req.body.installed ? "Installed" : "Available" };
    await saveClientModules(items);
    res.json(items[index]);
  } catch (error) {
    next(error);
  }
});

app.get("/api/scripts", requireFeature("scripts.read"), async (_req, res) => {
  res.json(await scripts());
});

app.get("/api/automations", requireFeature("automations.read"), async (req, res) => {
  const records = await userAutomations(req.user.id);
  res.json(records.map(record => record.graph).sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt))));
});

app.post("/api/automations/from-template", requireFeature("automations.write"), async (req, res, next) => {
  try {
    const templates = await readJson(templatesPath, defaultTemplates());
    const template = templates.find(item => item.id === String(req.body?.templateId || "") && item.kind === "automation");
    if (!template?.graph) throw automationError(404, "AUTOMATION_TEMPLATE_NOT_FOUND", "Automation template not found");
    const graph = structuredClone(template.graph);
    validateAutomationGraph(graph);
    graph.id = `automation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    graph.name = String(req.body?.name || template.name).slice(0, AUTOMATION_LIMITS.maxNameLength);
    graph.description = template.description;
    graph.generatedCode = "";
    res.status(201).json(await createUserAutomation(req.user.id, graph));
  } catch (error) { next(error); }
});

app.post("/api/automations", requireFeature("automations.write"), async (req, res, next) => {
  try {
    res.status(201).json(await createUserAutomation(req.user.id, req.body || {}));
  } catch (error) { next(error); }
});

app.get("/api/automations/:id", requireFeature("automations.read"), async (req, res) => {
  const record = (await userAutomations(req.user.id)).find(item => item.graph?.id === req.params.id);
  if (!record) return res.status(404).json({ error: "Automation not found" });
  res.json(record.graph);
});

app.put("/api/automations/:id", requireFeature("automations.write"), async (req, res, next) => {
  try {
    if (req.body?.id !== req.params.id) throw new Error("Automation id does not match route");
    res.json(await persistUserAutomation(req.user.id, req.body, { allowCreate: false }));
  } catch (error) { next(error); }
});

app.patch("/api/automations/:id", requireFeature("automations.write"), async (req, res, next) => {
  try {
    const current = (await userAutomations(req.user.id)).find(record => record.graph.id === req.params.id);
    if (!current) throw automationError(404, "AUTOMATION_NOT_FOUND", "Automation not found");
    const nextGraph = { ...current.graph, ...(req.body || {}), id: current.graph.id, nodes: current.graph.nodes, edges: current.graph.edges, viewport: current.graph.viewport, expectedUpdatedAt: req.body?.expectedUpdatedAt };
    res.json(await persistUserAutomation(req.user.id, nextGraph, { allowCreate: false }));
  } catch (error) { next(error); }
});

app.post("/api/automations/:id/duplicate", requireFeature("automations.write"), async (req, res, next) => {
  try {
    const current = (await userAutomations(req.user.id)).find(record => record.graph.id === req.params.id);
    if (!current) throw automationError(404, "AUTOMATION_NOT_FOUND", "Automation not found");
    const copy = structuredClone(current.graph);
    copy.id = `automation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    copy.name = String(req.body?.name || `${copy.name} Copy`).slice(0, AUTOMATION_LIMITS.maxNameLength);
    copy.createdAt = new Date().toISOString();
    copy.updatedAt = copy.createdAt;
    res.status(201).json(await createUserAutomation(req.user.id, copy));
  } catch (error) { next(error); }
});

app.post("/api/automations/:id/execute", requireFeature("automations.write"), async (req, res, next) => {
  try {
    const record = (await userAutomations(req.user.id)).find(item => item.graph?.id === req.params.id);
    if (!record) throw automationError(404, "AUTOMATION_NOT_FOUND", "Automation not found");
    const client = await ownedConnectedClient(req.user, req.body?.clientId);
    const graph = validateAutomationGraph(structuredClone(record.graph));
    const permissions = deriveAutomationPermissions(graph);
    if (permissions.length && req.body?.confirmPermissions !== true) {
      throw automationError(409, "AUTOMATION_PERMISSION_CONFIRMATION_REQUIRED", "Review and confirm the automation permissions before execution", { permissions });
    }
    const compile = await trustedAutomationCompilation(graph);
    if (compile.errors?.length || !compile.code) {
      throw automationError(422, "AUTOMATION_COMPILE_INVALID", "Automation cannot execute until compilation succeeds", { errors: compile.errors || [] });
    }
    const runtimeDir = safeInside(scriptDir, path.join(scriptDir, "automations"));
    await fs.mkdir(runtimeDir, { recursive: true });
    const runtimeName = `${req.user.id}-${graph.id}`.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 180) || "automation";
    const runtimeFile = safeInside(runtimeDir, path.join(runtimeDir, `${runtimeName}.py`));
    await fs.writeFile(runtimeFile, compile.code, "utf8");
    const scriptPath = normalizeSlashes(path.relative(scriptDir, runtimeFile));
    const executionId = `execution-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const now = new Date().toISOString();
    const command = queueControlCommand({ clientId: client.id, type: "run_script", payload: { path: scriptPath }, userId: req.user.id, executionId });
    const execution = {
      id: executionId,
      automationId: graph.id,
      automationName: graph.name,
      userId: req.user.id,
      clientId: client.id,
      commandId: command.id,
      scriptPath,
      status: "queued",
      activeNodeId: null,
      permissions,
      logs: [{ at: now, message: "Execution queued for the Minecraft client." }],
      createdAt: now,
      updatedAt: now
    };
    automationExecutions.set(executionId, execution);
    while (automationExecutions.size > 200) automationExecutions.delete(automationExecutions.keys().next().value);
    addControlActivity("Automation queued", graph.name, "pending");
    await recordCommandQueuedAnalytics(command);
    res.status(202).json(publicAutomationExecution(execution));
  } catch (error) { next(error); }
});

app.get("/api/automations/executions/:executionId", requireFeature("automations.read"), async (req, res) => {
  const execution = automationExecutions.get(req.params.executionId);
  if (!execution || (execution.userId !== req.user.id && !req.user.isAdmin)) return res.status(404).json({ error: "Automation execution not found" });
  if (["queued", "running", "cancelling"].includes(execution.status)) {
    const client = (await connectedClients()).find(candidate => candidate.id === execution.clientId);
    if (!client || client.connected === false) updateAutomationExecution(execution.id, { status: "disconnected" }, "Minecraft client disconnected during execution.");
  }
  res.json(publicAutomationExecution(automationExecutions.get(req.params.executionId)));
});

app.post("/api/automations/executions/:executionId/cancel", requireFeature("automations.write"), async (req, res, next) => {
  try {
    const execution = automationExecutions.get(req.params.executionId);
    if (!execution || (execution.userId !== req.user.id && !req.user.isAdmin)) throw automationError(404, "AUTOMATION_EXECUTION_NOT_FOUND", "Automation execution not found");
    if (["cancelled", "failed", "disconnected"].includes(execution.status)) return res.json(publicAutomationExecution(execution));
    const client = await ownedConnectedClient(req.user, execution.clientId);
    const command = queueControlCommand({ clientId: client.id, type: "stop_scripts", payload: {}, userId: req.user.id, executionId: execution.id });
    updateAutomationExecution(execution.id, { status: "cancelling", cancelCommandId: command.id }, "Cancellation requested.");
    await recordCommandQueuedAnalytics(command);
    res.status(202).json(publicAutomationExecution(automationExecutions.get(execution.id)));
  } catch (error) { next(error); }
});

app.delete("/api/automations/:id", requireFeature("automations.write"), async (req, res) => {
  const records = await readJson(automationsPath, []);
  const safeRecords = Array.isArray(records) ? records : [];
  const next = safeRecords.filter(record => !(record.userId === req.user.id && record.graph?.id === req.params.id));
  if (next.length === safeRecords.length) return res.status(404).json({ error: "Automation not found" });
  await writeJson(automationsPath, next);
  res.json({ ok: true });
});

app.get("/api/scripts/folders", requireFeature("scripts.read"), async (_req, res) => {
  res.json(await folders());
});

app.post("/api/scripts/folders", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const folder = folderPathFromRequest(req.body?.path || req.body?.name);
    await fs.mkdir(folder, { recursive: true });
    res.status(201).json({ path: normalizeSlashes(path.relative(scriptDir, folder)), name: path.basename(folder) });
  } catch (error) {
    next(error);
  }
});

app.patch("/api/scripts/folders", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const from = folderPathFromRequest(req.body?.path);
    const requested = req.body?.newPath || req.body?.name;
    if (!requested) {
      throw new Error("Missing new folder name");
    }
    let target;
    if (String(requested).includes("/") || String(requested).includes("\\")) {
      target = safeInside(scriptDir, path.join(scriptDir, safeFolderRelativePath(requested)));
    } else {
      target = safeInside(path.dirname(from), path.join(path.dirname(from), safeFolderRelativePath(requested)));
    }
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.rename(from, target);
    await moveFolderIdentities(path.relative(scriptDir, from), path.relative(scriptDir, target));
    res.json({ path: normalizeSlashes(path.relative(scriptDir, target)), name: path.basename(target) });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/scripts/folders", requireFeature("scripts.delete"), async (req, res, next) => {
  try {
    const folder = folderPathFromRequest(req.body?.path || req.query.path);
    const relative = normalizeSlashes(path.relative(scriptDir, folder));
    await fs.rm(folder, { recursive: true, force: true });
    await removeFolderIdentities(relative);
    res.json({ ok: true, path: relative });
  } catch (error) {
    next(error);
  }
});

app.post("/api/scripts", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const body = typeof req.body === "string" ? { content: req.body } : (req.body || {});
    let target;
    if (body.sourcePath) {
      throw automationError(400, "SOURCE_PATH_FORBIDDEN", "Server filesystem paths are not accepted; upload script content instead");
    } else {
      target = body.overwrite
        ? safeInside(scriptDir, path.join(scriptDir, safeScriptRelativePath(body.name)))
        : await uniqueScriptPath(body.name || "UploadedScript.py");
      const content = body.base64 ? decodeBase64Text(body.base64) : validateScriptContent(body.content || "");
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.writeFile(target, content, "utf8");
    }
    res.status(201).json(await scriptSummary(target));
  } catch (error) {
    next(error);
  }
});

app.patch("/api/scripts", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const from = scriptPathFromRequest(req.body?.path);
    const requested = req.body?.newPath || req.body?.name;
    if (!requested) {
      throw new Error("Missing new script name");
    }
    let target;
    if (String(requested).includes("/") || String(requested).includes("\\")) {
      target = safeInside(scriptDir, path.join(scriptDir, safeScriptRelativePath(requested)));
    } else {
      target = safeInside(path.dirname(from), path.join(path.dirname(from), safeScriptRelativePath(requested)));
    }
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.rename(from, target);
    await moveScriptIdentity(path.relative(scriptDir, from), path.relative(scriptDir, target));
    res.json(await scriptSummary(target));
  } catch (error) {
    next(error);
  }
});

app.get("/api/scripts/read", requireFeature("scripts.read"), async (req, res, next) => {
  try {
    const file = scriptPathFromRequest(req.query.path);
    res.json({ path: normalizeSlashes(path.relative(scriptDir, file)), content: await fs.readFile(file, "utf8") });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/scripts", requireFeature("scripts.delete"), async (req, res, next) => {
  try {
    const file = scriptPathFromRequest(req.body?.path || req.query.path);
    const relative = normalizeSlashes(path.relative(scriptDir, file));
    await fs.rm(file, { force: true });
    await removeScriptIdentity(relative);
    res.json({ ok: true, path: relative });
  } catch (error) {
    next(error);
  }
});

app.get("/api/scripts/:id/settings", requireFeature("scripts.read"), async (req, res, next) => {
  try {
    const script = (await scripts()).find(item => item.id === req.params.id);
    if (!script) throw automationError(404, "SCRIPT_NOT_FOUND", "Installed script not found");
    const file = scriptPathFromRequest(script.path);
    const identity = await ensureScriptIdentity(script.path);
    const metadata = await scriptMetadata(file, identity);
    res.json({ scriptId: script.id, path: script.path, definitions: metadata.definitions, issues: metadata.issues, values: metadata.values, warnings: metadata.warnings, sourceHash: metadata.sourceHash });
  } catch (error) { next(error); }
});

app.put("/api/scripts/:id/settings", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const script = (await scripts()).find(item => item.id === req.params.id);
    if (!script) throw automationError(404, "SCRIPT_NOT_FOUND", "Installed script not found");
    const file = scriptPathFromRequest(script.path);
    const identity = await ensureScriptIdentity(script.path);
    const metadata = await scriptMetadata(file, identity);
    if (metadata.issues.length) throw automationError(422, "SCRIPT_METADATA_INVALID", "Fix malformed metadata before saving variables", { issues: metadata.issues });
    const validation = validateValues(metadata.definitions, req.body?.values || {});
    if (!validation.valid) throw automationError(422, "SCRIPT_SETTINGS_INVALID", "One or more script variables are invalid", { errors: validation.errors });
    const all = await readJson(scriptSettingsPath, {});
    all[identity.id] = { values: validation.values, sourceHash: metadata.sourceHash, updatedAt: new Date().toISOString() };
    await writeJson(scriptSettingsPath, all);
    res.json({ scriptId: identity.id, values: validation.values, warnings: metadata.warnings, updatedAt: all[identity.id].updatedAt });
  } catch (error) { next(error); }
});

app.delete("/api/scripts/:id/settings", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const script = (await scripts()).find(item => item.id === req.params.id);
    if (!script) throw automationError(404, "SCRIPT_NOT_FOUND", "Installed script not found");
    const all = await readJson(scriptSettingsPath, {});
    delete all[script.id];
    await writeJson(scriptSettingsPath, all);
    const metadata = await scriptMetadata(scriptPathFromRequest(script.path), await ensureScriptIdentity(script.path));
    res.json({ scriptId: script.id, values: Object.fromEntries(metadata.definitions.map(item => [item.key, item.defaultValue])) });
  } catch (error) { next(error); }
});

app.put("/api/scripts/:id/shortcut", requireFeature("scripts.write"), async (req, res, next) => {
  try {
    const script = (await scripts()).find(item => item.id === req.params.id);
    if (!script) throw automationError(404, "SCRIPT_NOT_FOUND", "Installed script not found");
    const shortcut = String(req.body?.shortcut || "").trim();
    if (shortcut && !/^(?:(?:Ctrl|Alt|Shift|Meta)\+){0,4}(?:[A-Z0-9]|F(?:[1-9]|1[0-2])|Space|Enter|Home|End|PageUp|PageDown|Insert)$/i.test(shortcut)) throw automationError(422, "SHORTCUT_INVALID", "Shortcut combination is invalid");
    const all = await readJson(scriptShortcutsPath, {});
    const conflict = Object.entries(all).find(([id, value]) => id !== script.id && String(value).toLowerCase() === shortcut.toLowerCase());
    if (shortcut && conflict) throw automationError(409, "SHORTCUT_CONFLICT", "Shortcut is already assigned to another installed script", { scriptId: conflict[0] });
    if (shortcut) all[script.id] = shortcut;
    else delete all[script.id];
    await writeJson(scriptShortcutsPath, all);
    res.json({ scriptId: script.id, shortcut });
  } catch (error) { next(error); }
});

app.get("/api/modules", requireFeature("libraries.read"), async (_req, res) => {
  res.json(await scriptModules());
});

app.get("/api/libraries/scripts", requireFeature("libraries.read"), async (_req, res) => {
  const paths = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  res.json(Array.isArray(paths) ? [...new Set(paths.map(normalizeSlashes).filter(Boolean))].sort() : defaultLibraryScriptPaths());
});

app.get("/api/modules/scripts", requireFeature("libraries.read"), async (_req, res) => {
  const paths = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  res.json(Array.isArray(paths) ? [...new Set(paths.map(normalizeSlashes).filter(Boolean))].sort() : defaultLibraryScriptPaths());
});

app.patch("/api/modules/:id", requireAdmin, async (req, res, next) => {
  try {
    const modules = await readJson(librariesPath, defaultLibraries());
    const index = modules.findIndex((item) => item.id === req.params.id);
    if (index < 0) throw new Error("Module not found");
    if (!Object.prototype.hasOwnProperty.call(req.body || {}, "installed") || typeof req.body.installed !== "boolean") throw automationError(422, "MODULE_UPDATE_INVALID", "Only the installed state may be changed");
    modules[index] = { ...modules[index], installed: req.body.installed, status: req.body.installed ? "Installed" : "Available" };
    await saveLibraries(modules);
    res.json(modules[index]);
  } catch (error) {
    next(error);
  }
});

app.patch("/api/modules/scripts", requireAdmin, async (req, res, next) => {
  try {
    const body = req.body || {};
    const relative = normalizeSlashes(body.path || "");
    if (!relative || relative.includes("\0")) {
      throw new Error("Missing script path");
    }
    scriptPathFromRequest(relative);
    const current = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
    const nextPaths = new Set(Array.isArray(current) ? current.map(normalizeSlashes).filter(Boolean) : defaultLibraryScriptPaths());
    if (body.module === false || body.module === "false") {
      nextPaths.delete(relative);
    } else {
      nextPaths.add(relative);
    }
    const sorted = [...nextPaths].sort();
    await writeJson(libraryScriptPathsPath, sorted);
    res.json({ path: relative, module: nextPaths.has(relative), scripts: sorted });
  } catch (error) {
    next(error);
  }
});

app.get("/api/templates", requireFeature("templates.read"), async (_req, res) => {
  res.json(await readJson(templatesPath, defaultTemplates()));
});

app.post("/api/templates", requireAdmin, async (req, res) => {
  const templates = await readJson(templatesPath, defaultTemplates());
  const normalized = normalizeTemplate(req.body || {});
  const index = templates.findIndex((item) => item.id === normalized.id);
  if (index >= 0) {
    templates[index] = normalized;
  } else {
    templates.push(normalized);
  }
  await writeJson(templatesPath, templates);
  res.status(201).json(normalized);
});

app.put("/api/templates", requireAdmin, async (req, res) => {
  const templates = await readJson(templatesPath, defaultTemplates());
  const normalized = normalizeTemplate(req.body || {});
  const index = templates.findIndex((item) => item.id === normalized.id);
  if (index >= 0) {
    templates[index] = normalized;
  } else {
    templates.push(normalized);
  }
  await writeJson(templatesPath, templates);
  res.json(normalized);
});

app.delete("/api/templates/:id", requireAdmin, async (req, res, next) => {
  try {
    const templates = await readJson(templatesPath, defaultTemplates());
    const id = String(req.params.id || "");
    const nextTemplates = templates.filter((template) => template.id !== id);
    if (nextTemplates.length === templates.length) {
      throw new Error("Template not found");
    }
    await writeJson(templatesPath, nextTemplates);
    res.json({ ok: true, id });
  } catch (error) {
    next(error);
  }
});

app.post("/api/templates/use", requireFeature("templates.read"), async (req, res, next) => {
  try {
    res.status(201).json(await createScriptFromTemplate(String(req.body?.id || "")));
  } catch (error) {
    next(error);
  }
});

app.get("/api/stats", requireFeature("stats.read"), async (_req, res) => {
  const [scriptList, templates, publishedScripts, clientModules] = await Promise.all([
    scripts(),
    readJson(templatesPath, defaultTemplates()),
    libraryScripts(),
    readJson(clientModulesPath, defaultClientModules())
  ]);
  const modules = await scriptModules(scriptList);
  res.json({
    scripts: scriptList.length,
    installedModules: modules.length,
    installedLibraries: modules.length,
    installedClientModules: Array.isArray(clientModules) ? clientModules.filter((item) => item.installed !== false).length : 0,
    templates: templates.length,
    publishedScripts: publishedScripts.length
  });
});

app.get("/api/snapshot", authMiddleware, async (req, res) => {
  const snapshot = await snapshotData();
  snapshot.licenses = req.user.isAdmin ? snapshot.licenses : snapshot.licenses.filter(license => String(license.userId || "") === String(req.user.id));
  snapshot.profile = {
    id: req.user.id,
    displayName: req.user.displayName,
    username: req.user.username || "",
    email: req.user.email || "",
    tier: req.user.tier,
    features: req.user.features,
    connected: true
  };
  snapshot.entitlements = {
    tier: req.user.tier,
    isAdmin: req.user.isAdmin,
    features: req.user.features
  };
  res.json(snapshot);
});

app.get("/api/admin/overview", requireAdmin, async (_req, res) => {
  res.json(await adminOverview());
});

app.get("/api/admin/tamper-events", requireAdmin, async (_req, res) => {
  res.json(await readJson(tamperEventsPath, []));
});

app.get("/api/admin/users", requireAdmin, async (_req, res) => {
  const overview = await adminOverview();
  res.json(overview.users);
});

app.use("/web", express.static(webClientDist));
app.get("/web/*", (_req, res, next) => {
  const indexPath = path.join(webClientDist, "index.html");
  if (fsSync.existsSync(indexPath)) {
    res.sendFile(indexPath);
  } else {
    next();
  }
});

app.use("/admin", express.static(publicDir));
app.get("/", (_req, res) => {
  const webIndex = path.join(webClientDist, "index.html");
  res.redirect(fsSync.existsSync(webIndex) ? "/web/" : "/admin/");
});

app.use((error, _req, res, _next) => {
  const rawMessage = String(error.message || "Backend error");
  const containsSensitivePath = [rootDir, dataDir, scriptDir].some(value => rawMessage.toLowerCase().includes(String(value).toLowerCase()));
  const message = containsSensitivePath || /\b(?:ENOENT|EACCES|EPERM)\b/.test(rawMessage) ? "File operation failed" : rawMessage;
  res.status(error.status || 400).json({ error: message, ...(error.code ? { code: error.code } : {}), ...(error.details ? { details: error.details } : {}) });
});

if (require.main === module) {
  ensureReady()
    .then(() => {
      app.listen(port, "127.0.0.1", () => {
        console.log(`Shulkr Express backend running at http://127.0.0.1:${port}`);
        console.log(`Scripts: ${scriptDir}`);
        console.log(`Data: ${dataDir}`);
      });
    })
    .catch((error) => {
      console.error("Failed to start Shulkr backend", error);
      process.exitCode = 1;
    });
}

module.exports = { app, ensureReady, validateAutomationGraph, ownedAutomationRecords };
