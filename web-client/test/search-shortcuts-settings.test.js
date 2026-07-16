import test from "node:test";
import assert from "node:assert/strict";
import { buildGlobalSearchIndex, filterSearch, searchGlobalIndex } from "../src/search.js";
import { defaultsForSettings, parseScriptSettings, reconcileScriptValues, validateScriptValues } from "../src/scriptSettings.js";
import { eventMatchesShortcut, findShortcutConflict, loadShortcutConfig, normalizeShortcut, saveShortcutConfig, shortcutFromKeyboardEvent } from "../src/shortcuts.js";

test("global search indexes dashboard sections and returns real navigation targets", () => {
  const index = buildGlobalSearchIndex({
    nav: [["settings", "Settings", "fa-gear", "Preferences"]],
    scripts: [{ id: "stable-1", path: "mining/diamonds.py", name: "Diamond Miner", description: "Find ores" }],
    hub: [{ id: "hub-1", name: "Crop Farmer", author: "Alex", about: "Harvest wheat", tags: ["farming"], category: "World" }],
    clients: [], templates: [], libraries: [], modules: [], settings: [{ id: "shortcuts", label: "Keyboard shortcuts", tab: "shortcuts" }]
  });
  assert.equal(searchGlobalIndex(index, "diamond ore")[0].id, "mining/diamonds.py");
  assert.equal(searchGlobalIndex(index, "Alex wheat")[0].id, "hub-1");
  assert.equal(searchGlobalIndex(index, "keyboard")[0].kind, "setting");
  assert.deepEqual(searchGlobalIndex(index, "nothing"), []);
});

test("page search is trimmed, case-insensitive, field-aware, and independent", () => {
  const scripts = [{ name: "Builder", author: "Ryan", tags: ["World"], path: "tools/build.py" }, { name: "Miner", author: "Alex", tags: ["Ore"], path: "mine.py" }];
  assert.deepEqual(filterSearch(scripts, "  RYAN world ", ["name", "author", "tags", "path"]).map(item => item.name), ["Builder"]);
  assert.deepEqual(filterSearch(scripts, "missing", ["name"]).map(item => item.name), []);
  assert.equal(filterSearch(scripts, "", ["name"]).length, 2);
});

test("shortcut recording supports modifiers, conflict detection, clearing, and persistence", () => {
  const event = { key: "r", ctrlKey: true, altKey: false, shiftKey: true, metaKey: false };
  assert.equal(shortcutFromKeyboardEvent(event), "Ctrl+Shift+R");
  assert.equal(eventMatchesShortcut(event, "control+shift+r"), true);
  assert.equal(findShortcutConflict({ one: "Ctrl+K", two: "Alt+P" }, "ctrl+k", "two"), "one");
  assert.equal(normalizeShortcut(""), "");
  const values = new Map();
  const storage = { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value) };
  const config = loadShortcutConfig(storage);
  config.app["global-search"] = "Alt+K";
  config.scripts.stable = "F8";
  saveShortcutConfig(config, storage);
  assert.deepEqual(loadShortcutConfig(storage), config);
});

test("script settings parse, validate, reset, and preserve compatible values", () => {
  const parsed = parseScriptSettings(`# @setting radius slider min=1 max=16 step=1 default=8 label="Radius"\n# @setting block block default=stone\n# @setting enabled boolean default=true\n# @setting mode select options="safe,fast" default=safe\n# @setting target coordinates default="1,64,2"`);
  assert.equal(parsed.issues.length, 0);
  assert.equal(parsed.definitions.length, 5);
  assert.deepEqual(defaultsForSettings(parsed.definitions).target, { x: 1, y: 64, z: 2 });
  assert.equal(validateScriptValues(parsed.definitions, { radius: 3.5, block: "bad block", enabled: true, mode: "unsafe", target: "a,b,c" }).valid, false);
  const reconciled = reconcileScriptValues(parsed.definitions, { radius: 12, block: "minecraft:diamond_ore", enabled: true, mode: "safe", target: { x: 4, y: 5, z: 6 }, old: 1 });
  assert.equal(reconciled.values.radius, 12);
  assert.equal(reconciled.warnings.length, 1);
});

test("metadata errors include malformed and duplicate annotation line numbers", () => {
  const parsed = parseScriptSettings(`# @setting radius number default=4\n# @setting radius number default=5\n# @setting invalid unsupported default=x`);
  assert.deepEqual(parsed.issues.map(issue => issue.line), [2, 3]);
});
