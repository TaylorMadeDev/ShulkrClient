export const SCRIPT_SETTING_TYPES = new Set(["number", "slider", "text", "boolean", "select", "block", "coordinates"]);

function tokenize(value) {
  const tokens = [];
  let current = "";
  let quote = "";
  let escaped = false;
  for (const character of String(value || "")) {
    if (escaped) { current += character; escaped = false; continue; }
    if (character === "\\" && quote) { escaped = true; continue; }
    if (quote) {
      if (character === quote) quote = "";
      else current += character;
      continue;
    }
    if (character === '"' || character === "'") { quote = character; continue; }
    if (/\s/.test(character)) {
      if (current) { tokens.push(current); current = ""; }
    } else current += character;
  }
  if (quote) throw new Error("Unclosed quoted value");
  if (current) tokens.push(current);
  return tokens;
}

function parseBoolean(value, property) {
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${property} must be true or false`);
}

export function normalizeBlockIdentifier(value) {
  const normalized = String(value || "").trim().toLowerCase();
  const qualified = normalized.includes(":") ? normalized : `minecraft:${normalized}`;
  if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(qualified)) throw new Error("Enter a valid Minecraft identifier such as minecraft:diamond_ore");
  return qualified;
}

export function normalizeCoordinates(value) {
  let parts;
  if (Array.isArray(value)) parts = value;
  else if (value && typeof value === "object") parts = [value.x, value.y, value.z];
  else parts = String(value || "").trim().split(/[\s,]+/);
  if (parts.length !== 3 || parts.some(part => part === "" || !Number.isFinite(Number(part)))) throw new Error("Coordinates require numeric X, Y, and Z values");
  return { x: Number(parts[0]), y: Number(parts[1]), z: Number(parts[2]) };
}

export function validateScriptSetting(definition, value) {
  if ((value === undefined || value === null || value === "") && definition.required) throw new Error(`${definition.label} is required`);
  if ((value === undefined || value === null || value === "") && !definition.required && definition.type !== "text") return value;
  if (definition.type === "number" || definition.type === "slider") {
    const number = Number(value);
    if (!Number.isFinite(number)) throw new Error(`${definition.label} must be a number`);
    if (definition.min !== undefined && number < definition.min) throw new Error(`${definition.label} must be at least ${definition.min}`);
    if (definition.max !== undefined && number > definition.max) throw new Error(`${definition.label} must be at most ${definition.max}`);
    if (definition.step !== undefined) {
      const base = definition.min ?? 0;
      const units = (number - base) / definition.step;
      if (Math.abs(units - Math.round(units)) > 1e-8) throw new Error(`${definition.label} must use increments of ${definition.step}`);
    }
    return number;
  }
  if (definition.type === "boolean") {
    if (typeof value === "boolean") return value;
    return parseBoolean(String(value).toLowerCase(), definition.label);
  }
  if (definition.type === "select") {
    const selected = String(value);
    if (!definition.options.includes(selected)) throw new Error(`${definition.label} must be one of: ${definition.options.join(", ")}`);
    return selected;
  }
  if (definition.type === "block") return normalizeBlockIdentifier(value);
  if (definition.type === "coordinates") return normalizeCoordinates(value);
  return String(value ?? "");
}

export function parseScriptSettings(source) {
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
      const tokens = tokenize(match[2]);
      const [key, type, ...properties] = tokens;
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key || "")) throw new Error("Setting key must be a Python-style identifier");
      if (!SCRIPT_SETTING_TYPES.has(type)) throw new Error(`Unsupported setting type: ${type || "missing"}`);
      if (keys.has(key)) throw new Error(`Duplicate setting key: ${key}`);
      const raw = {};
      for (const token of properties) {
        const separator = token.indexOf("=");
        if (separator < 1) throw new Error(`Malformed property: ${token}`);
        const name = token.slice(0, separator);
        if (!["label", "default", "description", "required", "min", "max", "step", "options"].includes(name)) throw new Error(`Unsupported property: ${name}`);
        raw[name] = token.slice(separator + 1);
      }
      const definition = {
        key,
        label: raw.label || key.replaceAll("_", " ").replace(/\b\w/g, value => value.toUpperCase()),
        type,
        defaultValue: undefined,
        description: raw.description || "",
        required: raw.required === undefined ? false : parseBoolean(raw.required, "required"),
        line: lineNumber,
        version
      };
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
      let defaultValue = raw.default;
      if (defaultValue === undefined) {
        if (type === "boolean") defaultValue = false;
        else if (type === "coordinates") defaultValue = "0,0,0";
        else if (type === "number" || type === "slider") defaultValue = definition.min ?? 0;
        else defaultValue = type === "select" ? definition.options[0] : "";
      }
      definition.defaultValue = validateScriptSetting(definition, defaultValue);
      keys.add(key);
      definitions.push(definition);
    } catch (error) {
      issues.push({ line: lineNumber, message: error.message, source: line.trim() });
    }
  });
  return { version: 1, definitions, issues };
}

export function validateScriptValues(definitions, values = {}) {
  const normalized = {};
  const errors = {};
  for (const definition of definitions || []) {
    try { normalized[definition.key] = validateScriptSetting(definition, values[definition.key] ?? definition.defaultValue); }
    catch (error) { errors[definition.key] = error.message; }
  }
  return { valid: Object.keys(errors).length === 0, values: normalized, errors };
}

export function defaultsForSettings(definitions) {
  return Object.fromEntries((definitions || []).map(definition => [definition.key, structuredClone(definition.defaultValue)]));
}

export function reconcileScriptValues(definitions, saved = {}) {
  const next = {};
  const warnings = [];
  const known = new Set((definitions || []).map(definition => definition.key));
  for (const definition of definitions || []) {
    try { next[definition.key] = validateScriptSetting(definition, Object.prototype.hasOwnProperty.call(saved, definition.key) ? saved[definition.key] : definition.defaultValue); }
    catch { next[definition.key] = structuredClone(definition.defaultValue); warnings.push(`${definition.label} was reset because its saved value is no longer compatible.`); }
  }
  for (const key of Object.keys(saved || {})) if (!known.has(key)) warnings.push(`Saved setting ${key} was removed by the script update.`);
  return { values: next, warnings };
}
