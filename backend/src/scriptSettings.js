const SETTING_TYPES = new Set(["number", "slider", "text", "boolean", "select", "block", "coordinates"]);

function tokenize(value) {
  const tokens = [];
  let current = "";
  let quote = "";
  let escaped = false;
  for (const character of String(value || "")) {
    if (escaped) { current += character; escaped = false; continue; }
    if (character === "\\" && quote) { escaped = true; continue; }
    if (quote) { if (character === quote) quote = ""; else current += character; continue; }
    if (character === '"' || character === "'") { quote = character; continue; }
    if (/\s/.test(character)) { if (current) { tokens.push(current); current = ""; } }
    else current += character;
  }
  if (quote) throw new Error("Unclosed quoted value");
  if (current) tokens.push(current);
  return tokens;
}

function booleanValue(value, property) {
  if (value === "true" || value === true) return true;
  if (value === "false" || value === false) return false;
  throw new Error(`${property} must be true or false`);
}

function normalizeBlockIdentifier(value) {
  const normalized = String(value || "").trim().toLowerCase();
  const qualified = normalized.includes(":") ? normalized : `minecraft:${normalized}`;
  if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(qualified)) throw new Error("Enter a valid Minecraft identifier such as minecraft:diamond_ore");
  return qualified;
}

function normalizeCoordinates(value) {
  let parts;
  if (Array.isArray(value)) parts = value;
  else if (value && typeof value === "object") parts = [value.x, value.y, value.z];
  else parts = String(value || "").trim().split(/[\s,]+/);
  if (parts.length !== 3 || parts.some(part => part === "" || !Number.isFinite(Number(part)))) throw new Error("Coordinates require numeric X, Y, and Z values");
  return { x: Number(parts[0]), y: Number(parts[1]), z: Number(parts[2]) };
}

function validateSetting(definition, value) {
  if ((value === undefined || value === null || value === "") && definition.required) throw new Error(`${definition.label} is required`);
  if ((value === undefined || value === null || value === "") && !definition.required && definition.type !== "text") return value;
  if (["number", "slider"].includes(definition.type)) {
    const number = Number(value);
    if (!Number.isFinite(number)) throw new Error(`${definition.label} must be a number`);
    if (definition.min !== undefined && number < definition.min) throw new Error(`${definition.label} must be at least ${definition.min}`);
    if (definition.max !== undefined && number > definition.max) throw new Error(`${definition.label} must be at most ${definition.max}`);
    if (definition.step !== undefined) {
      const units = (number - (definition.min ?? 0)) / definition.step;
      if (Math.abs(units - Math.round(units)) > 1e-8) throw new Error(`${definition.label} must use increments of ${definition.step}`);
    }
    return number;
  }
  if (definition.type === "boolean") return typeof value === "boolean" ? value : booleanValue(String(value).toLowerCase(), definition.label);
  if (definition.type === "select") {
    const selected = String(value);
    if (!definition.options.includes(selected)) throw new Error(`${definition.label} must be one of: ${definition.options.join(", ")}`);
    return selected;
  }
  if (definition.type === "block") return normalizeBlockIdentifier(value);
  if (definition.type === "coordinates") return normalizeCoordinates(value);
  return String(value ?? "");
}

function parseScriptSettings(source) {
  const definitions = [];
  const issues = [];
  const keys = new Set();
  String(source || "").split(/\r?\n/).forEach((line, index) => {
    const match = line.match(/^\s*#\s*@setting(?:\s+v(\d+))?\s+(.+)$/);
    if (!match) return;
    const lineNumber = index + 1;
    try {
      const version = Number(match[1] || 1);
      if (version !== 1) throw new Error(`Unsupported metadata version v${version}`);
      const [key, type, ...tokens] = tokenize(match[2]);
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key || "")) throw new Error("Setting key must be a Python-style identifier");
      if (!SETTING_TYPES.has(type)) throw new Error(`Unsupported setting type: ${type || "missing"}`);
      if (keys.has(key)) throw new Error(`Duplicate setting key: ${key}`);
      const raw = {};
      for (const token of tokens) {
        const separator = token.indexOf("=");
        if (separator < 1) throw new Error(`Malformed property: ${token}`);
        const property = token.slice(0, separator);
        if (!["label", "default", "description", "required", "min", "max", "step", "options"].includes(property)) throw new Error(`Unsupported property: ${property}`);
        raw[property] = token.slice(separator + 1);
      }
      const definition = { key, label: raw.label || key.replaceAll("_", " ").replace(/\b\w/g, value => value.toUpperCase()), type, defaultValue: undefined, description: raw.description || "", required: raw.required === undefined ? false : booleanValue(raw.required, "required"), line: lineNumber, version };
      if (raw.min !== undefined) definition.min = Number(raw.min);
      if (raw.max !== undefined) definition.max = Number(raw.max);
      if (raw.step !== undefined) definition.step = Number(raw.step);
      if ([definition.min, definition.max, definition.step].some(value => value !== undefined && !Number.isFinite(value))) throw new Error("min, max, and step must be numbers");
      if (definition.min !== undefined && definition.max !== undefined && definition.min > definition.max) throw new Error("min cannot be greater than max");
      if (definition.step !== undefined && definition.step <= 0) throw new Error("step must be greater than zero");
      if (type === "select") {
        definition.options = String(raw.options || "").split(",").map(value => value.trim()).filter(Boolean);
        if (!definition.options.length) throw new Error("select settings require options");
      }
      let fallback = raw.default;
      if (fallback === undefined) fallback = type === "boolean" ? false : type === "coordinates" ? "0,0,0" : ["number", "slider"].includes(type) ? definition.min ?? 0 : type === "select" ? definition.options[0] : "";
      definition.defaultValue = validateSetting(definition, fallback);
      keys.add(key);
      definitions.push(definition);
    } catch (error) { issues.push({ line: lineNumber, message: error.message, source: line.trim() }); }
  });
  return { version: 1, definitions, issues };
}

function validateValues(definitions, values = {}) {
  const normalized = {};
  const errors = {};
  for (const definition of definitions || []) {
    try { normalized[definition.key] = validateSetting(definition, values[definition.key] ?? definition.defaultValue); }
    catch (error) { errors[definition.key] = error.message; }
  }
  return { valid: Object.keys(errors).length === 0, values: normalized, errors };
}

function reconcileValues(definitions, saved = {}) {
  const result = validateValues(definitions, saved);
  const values = {};
  const warnings = [];
  const known = new Set((definitions || []).map(item => item.key));
  for (const definition of definitions || []) {
    if (result.errors[definition.key]) {
      values[definition.key] = definition.defaultValue;
      warnings.push(`${definition.label} was reset because its saved value is no longer compatible.`);
    } else values[definition.key] = result.values[definition.key];
  }
  for (const key of Object.keys(saved || {})) if (!known.has(key)) warnings.push(`Saved setting ${key} was removed by the script update.`);
  return { values, warnings };
}

module.exports = { SETTING_TYPES, parseScriptSettings, validateSetting, validateValues, reconcileValues, normalizeBlockIdentifier, normalizeCoordinates };
