const test = require("node:test");
const assert = require("node:assert/strict");
const { validateAutomationGraph, ownedAutomationRecords } = require("../src/server.js");

function graph(overrides = {}) {
  return { formatVersion: 1, id: "automation-1", name: "Test flow", nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 }, generatedCode: "", ...overrides };
}

test("backend accepts the versioned graph persistence contract", () => {
  assert.equal(validateAutomationGraph(graph()).id, "automation-1");
});

test("backend rejects malformed graph persistence payloads", () => {
  assert.throws(() => validateAutomationGraph(null), /must be an object/);
  assert.throws(() => validateAutomationGraph(graph({ formatVersion: 2 })), /Unsupported/);
  assert.throws(() => validateAutomationGraph(graph({ id: "bad/id" })), /Invalid automation id/);
  assert.throws(() => validateAutomationGraph(graph({ name: "" })), /name/);
  assert.throws(() => validateAutomationGraph(graph({ nodes: {} })), /nodes/);
  assert.throws(() => validateAutomationGraph(graph({ edges: {} })), /edges/);
  assert.throws(() => validateAutomationGraph(graph({ nodes: [{ id: "unknown", type: "plugin.hidden", version: 1, position: { x: 0, y: 0 }, data: {} }] })), /Unsupported node type/);
  assert.throws(() => validateAutomationGraph(graph({ nodes: [{ id: "start", type: "flow.start", version: 1, position: { x: "0", y: 0 }, data: {} }] })), /position/);
  const unsafeData = JSON.parse('{"constructor":{"prototype":{"polluted":true}}}');
  assert.throws(() => validateAutomationGraph(graph({ nodes: [{ id: "start", type: "flow.start", version: 1, position: { x: 0, y: 0 }, data: unsafeData }] })), /forbidden key/);
  const nodes = [
    { id: "start", type: "flow.start", version: 1, position: { x: 0, y: 0 }, data: {} },
    { id: "stop", type: "flow.stop", version: 1, position: { x: 200, y: 0 }, data: {} }
  ];
  const edge = { id: "edge-1", source: "start", sourceHandle: "execution-out", target: "stop", targetHandle: "execution-in", dataType: "execution" };
  assert.throws(() => validateAutomationGraph(graph({ nodes, edges: [edge, { ...edge, id: "edge-2" }] })), /Duplicate connection/);
});

test("ownership filtering never returns another user's private graph", () => {
  const records = [{ userId: "user-a", graph: graph({ id: "a" }) }, { userId: "user-b", graph: graph({ id: "b" }) }];
  assert.deepEqual(ownedAutomationRecords(records, "user-a").map(record => record.graph.id), ["a"]);
  assert.deepEqual(ownedAutomationRecords(records, "user-b").map(record => record.graph.id), ["b"]);
  assert.deepEqual(ownedAutomationRecords(records, "user-c"), []);
});
