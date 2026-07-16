const test = require("node:test");
const assert = require("node:assert/strict");
const { parseScriptSettings, validateValues, reconcileValues, normalizeBlockIdentifier, normalizeCoordinates } = require("../src/scriptSettings");

const source = `# normal comment
# @setting sensitivity slider min=1 max=10 step=1 default=5 label="Sensitivity"
# @setting block_to_mine block default="diamond_ore" label="Block to mine"
# @setting avoid_mobs boolean default=true label="Avoid mobs"
# @setting tool select options="any,pickaxe,axe" default="pickaxe" label="Preferred tool"
# @setting target coordinates default="10,64,-5" label="Target"
# @setting message text default="Mining started" label="Message"`;

test("parses every supported metadata shape without executing source", () => {
  const result = parseScriptSettings(source);
  assert.equal(result.issues.length, 0);
  assert.equal(result.definitions.length, 6);
  assert.deepEqual(result.definitions.find(item => item.key === "target").defaultValue, { x: 10, y: 64, z: -5 });
  assert.equal(result.definitions.find(item => item.key === "block_to_mine").defaultValue, "minecraft:diamond_ore");
});

test("reports malformed, duplicate, and future-version annotations with line numbers", () => {
  const result = parseScriptSettings(`# @setting value number default=2\n# @setting value text default=x\n# @setting v2 future text default=x\n# @setting bad unknown default=x`);
  assert.equal(result.definitions.length, 1);
  assert.deepEqual(result.issues.map(issue => issue.line), [2, 3, 4]);
});

test("validates numeric ranges, slider steps, selects, blocks, and coordinates", () => {
  const definitions = parseScriptSettings(source).definitions;
  const invalid = validateValues(definitions, { sensitivity: 5.5, block_to_mine: "Bad Block", avoid_mobs: true, tool: "sword", target: "x,y,z", message: "safe text" });
  assert.equal(invalid.valid, false);
  assert.deepEqual(Object.keys(invalid.errors).sort(), ["block_to_mine", "sensitivity", "target", "tool"]);
  assert.equal(normalizeBlockIdentifier("stone"), "minecraft:stone");
  assert.deepEqual(normalizeCoordinates([1, 2, 3]), { x: 1, y: 2, z: 3 });
});

test("preserves compatible saved values and resets changed or removed definitions", () => {
  const definitions = parseScriptSettings(`# @setting radius number min=1 max=32 default=8 label="Radius"\n# @setting enabled boolean default=true`).definitions;
  const result = reconcileValues(definitions, { radius: 16, enabled: "invalid", removed: "old" });
  assert.deepEqual(result.values, { radius: 16, enabled: true });
  assert.equal(result.warnings.length, 2);
});
