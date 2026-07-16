import test from "node:test";
import assert from "node:assert/strict";
import { FlowStore } from "../src/flow/store.js";

function storage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key), clear: () => values.clear() };
}

function apiHarness() {
  const records = new Map();
  const api = async (path, options = {}) => {
    if (path === "/api/automations" && options.method === "POST") { const graph = JSON.parse(options.body); records.set(graph.id, structuredClone(graph)); return structuredClone(graph); }
    if (path === "/api/automations") return [...records.values()].map(graph => structuredClone(graph));
    const id = decodeURIComponent(path.split("/").at(-1));
    if (options.method === "PUT") { const graph = JSON.parse(options.body); records.set(id, structuredClone(graph)); return structuredClone(graph); }
    if (options.method === "DELETE") { records.delete(id); return { ok: true }; }
    if (!records.has(id)) throw new Error("Automation not found");
    return structuredClone(records.get(id));
  };
  return { api, records };
}

globalThis.localStorage = storage();

test("store mutations drive graph, validation, compilation and history", () => {
  const store = new FlowStore();
  store.add("flow.start", { x: 10, y: 20 });
  store.add("flow.stop", { x: 10, y: 20 });
  const [start, stop] = store.state.graph.nodes;
  assert.ok(Math.abs(start.position.x - stop.position.x) >= 190);
  store.connect({ source: start.id, sourceHandle: "execution-out", target: stop.id, targetHandle: "execution-in" });
  assert.equal(store.state.graph.edges.length, 1);
  assert.equal(store.state.validation.valid, true);
  assert.match(store.state.compileResult.code, new RegExp(start.id));
  const connectedEdge = store.state.graph.edges[0];
  store.select([], [connectedEdge.id]);
  store.remove();
  assert.equal(store.state.graph.edges.length, 0);
  store.undo();
  assert.equal(store.state.graph.edges.length, 1);
  store.move(stop.id, { x: 300, y: 80 });
  assert.deepEqual(store.state.graph.nodes[1].position, { x: 300, y: 80 });
  store.undo();
  assert.deepEqual(store.state.graph.nodes[1].position, stop.position);
  store.redo();
  assert.deepEqual(store.state.graph.nodes[1].position, { x: 300, y: 80 });
  clearTimeout(store.autosave);
});

test("copy, paste, duplicate and delete preserve Start uniqueness", () => {
  const store = new FlowStore();
  store.add("flow.start", { x: 0, y: 0 });
  store.add("flow.delay", { x: 100, y: 0 });
  const [start, delay] = store.state.graph.nodes;
  store.select([start.id, delay.id], []);
  store.copy();
  store.paste();
  assert.equal(store.state.graph.nodes.filter(node => node.type === "flow.start").length, 1);
  assert.equal(store.state.graph.nodes.filter(node => node.type === "flow.delay").length, 2);
  store.select([delay.id], []);
  store.duplicate();
  assert.equal(store.state.graph.nodes.filter(node => node.type === "flow.delay").length, 3);
  const beforeDelete = store.state.graph.nodes.length;
  store.remove();
  assert.equal(store.state.graph.nodes.length, beforeDelete - 1);
  assert.equal(store.state.graph.nodes.filter(node => node.id === delay.id).length, 1);
  clearTimeout(store.autosave);
});

test("create-and-connect adds a compatible node and edge as one undoable change", () => {
  const store = new FlowStore();
  store.add("flow.start", { x: 0, y: 0 });
  const start = store.state.graph.nodes[0];
  const historyBefore = store.state.history.past.length;
  const created = store.addConnected("flow.delay", { x: 300, y: 20 }, {
    source: start.id,
    sourceHandle: "execution-out",
    targetHandle: "execution-in"
  });
  assert.equal(created.type, "flow.delay");
  assert.equal(store.state.graph.nodes.length, 2);
  assert.equal(store.state.graph.edges.length, 1);
  assert.equal(store.state.graph.edges[0].target, created.id);
  assert.equal(store.state.history.past.length, historyBefore + 1);
  store.undo();
  assert.equal(store.state.graph.nodes.length, 1);
  assert.equal(store.state.graph.edges.length, 0);
  clearTimeout(store.autosave);
});

test("save clears recovery draft and reload restores a clean graph", async () => {
  localStorage.clear();
  const { api, records } = apiHarness();
  const store = new FlowStore();
  store.configure({ api, userId: "user-1" });
  store.add("flow.start", { x: 15, y: 25 });
  store.rename("Persisted flow");
  store.setViewport({ x: 40, y: 50, zoom: 1.25 });
  assert.equal(store.state.graph.name, "Persisted flow");
  await store.save();
  clearTimeout(store.autosave);
  assert.equal(store.state.dirty, false);
  assert.equal(localStorage.getItem("shulkr_flow_draft:user-1"), null);
  assert.equal(records.size, 1);
  assert.equal([...records.values()][0].name, "Persisted flow");

  const reloaded = new FlowStore();
  reloaded.configure({ api, userId: "user-1" });
  reloaded.state.error = "Previous backend error";
  await reloaded.loadAll();
  assert.equal(reloaded.state.error, "");
  assert.equal(reloaded.state.dirty, false);
  assert.equal(reloaded.state.graph.name, "Persisted flow");
  assert.deepEqual(reloaded.state.graph.nodes[0].position, { x: 15, y: 25 });
  assert.deepEqual(reloaded.state.graph.viewport, { x: 40, y: 50, zoom: 1.25 });
});

test("client connection state is derived rather than fixed", () => {
  const store = new FlowStore();
  assert.equal(store.state.runtime.status, "disconnected");
  store.setClientConnection(true, false, "client-1");
  assert.equal(store.state.runtime.clientConnected, true);
  assert.equal(store.state.runtime.clientId, "client-1");
  assert.equal(store.state.runtime.status, "ready");
  assert.match(store.state.runtime.message, /ready for automation execution/i);
});

test("changing authenticated users clears private graph and runtime state", () => {
  const { api } = apiHarness();
  const store = new FlowStore();
  store.configure({ api, userId: "first-user" });
  store.add("flow.start", { x: 0, y: 0 });
  store.state.automations.push(structuredClone(store.state.graph));
  store.setClientConnection(true, false, "first-user-client");
  clearTimeout(store.autosave);

  store.configure({ api: null, userId: "second-user" });
  assert.equal(store.api, null);
  assert.equal(store.state.graph.nodes.length, 0);
  assert.equal(store.state.automations.length, 0);
  assert.equal(store.state.runtime.clientConnected, false);
  assert.equal(store.state.clipboard.length, 0);
});

test("execution and cancellation use the automation runtime API", async () => {
  const calls = [];
  const api = async (path, options = {}) => {
    calls.push({ path, options });
    if (path.endsWith("/execute")) return { id: "execution-1", status: "queued", logs: [{ message: "Queued" }] };
    if (path.endsWith("/cancel")) return { id: "execution-1", status: "cancelling", logs: [{ message: "Cancelling" }] };
    throw new Error(`Unexpected API request: ${path}`);
  };
  const store = new FlowStore();
  store.configure({ api, userId: "runtime-user" });
  store.add("flow.start", { x: 0, y: 0 });
  store.add("flow.stop", { x: 300, y: 0 });
  const [start, stop] = store.state.graph.nodes;
  store.connect({ source: start.id, sourceHandle: "execution-out", target: stop.id, targetHandle: "execution-in" });
  store.state.dirty = false;
  clearTimeout(store.autosave);
  store.setClientConnection(true, false, "client-1");

  await store.execute(true);
  clearTimeout(store.executionPoll);
  assert.equal(store.state.runtime.status, "queued");
  assert.equal(store.state.runtime.executionId, "execution-1");
  assert.deepEqual(JSON.parse(calls[0].options.body), { clientId: "client-1", confirmPermissions: true });

  await store.cancelExecution();
  clearTimeout(store.executionPoll);
  assert.equal(store.state.runtime.status, "cancelling");
  assert.match(calls[1].path, /executions\/execution-1\/cancel$/);
});

test("debounced autosave creates a persisted automation", async () => {
  localStorage.clear();
  const { api, records } = apiHarness();
  const store = new FlowStore();
  store.configure({ api, userId: "autosave-user" });
  store.add("flow.start", { x: 5, y: 10 });
  await new Promise(resolve => setTimeout(resolve, 1000));
  assert.equal(records.size, 1);
  assert.equal(store.state.dirty, false);
  clearTimeout(store.autosave);
});

test("large graphs validate and compile within an interactive budget", () => {
  const store = new FlowStore();
  const graph = store.state.graph;
  graph.nodes = Array.from({ length: 200 }, (_, index) => ({ id: `node-${index}`, type: index === 0 ? "flow.start" : "flow.delay", version: 1, position: { x: index * 10, y: 0 }, data: index === 0 ? {} : { duration: 1 } }));
  graph.edges = Array.from({ length: 400 }, (_, index) => ({ id: `edge-${index}`, source: `node-${Math.min(index, 198)}`, sourceHandle: "execution-out", target: `node-${Math.min(index + 1, 199)}`, targetHandle: "execution-in", dataType: "execution" }));
  const started = performance.now();
  store.setGraph(graph, { dirty: false });
  const elapsed = performance.now() - started;
  assert.ok(elapsed < 1000, `large graph processing took ${elapsed}ms`);
});
