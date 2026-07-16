const MODIFIER_ORDER = ["Ctrl", "Alt", "Shift", "Meta"];

export function isTypingTarget(target, modalOpen = false) {
  if (modalOpen) return true;
  if (!(target instanceof Element)) return false;
  return Boolean(target.closest("input, textarea, select, [contenteditable='true'], .monaco-editor, [role='dialog']"));
}

export function normalizeShortcut(value) {
  const parts = String(value || "").split("+").map(part => part.trim()).filter(Boolean);
  if (!parts.length) return "";
  const aliases = { control: "Ctrl", ctrl: "Ctrl", alt: "Alt", option: "Alt", shift: "Shift", meta: "Meta", cmd: "Meta", command: "Meta" };
  const modifiers = new Set();
  let key = "";
  for (const part of parts) {
    const modifier = aliases[part.toLowerCase()];
    if (modifier) modifiers.add(modifier);
    else key = part.length === 1 ? part.toUpperCase() : part.replace(/^Key/, "");
  }
  if (!key) return "";
  return [...MODIFIER_ORDER.filter(modifier => modifiers.has(modifier)), key].join("+");
}

export function shortcutFromKeyboardEvent(event) {
  if (["Control", "Alt", "Shift", "Meta"].includes(event.key)) return null;
  const key = event.key === " " ? "Space" : event.key.length === 1 ? event.key.toUpperCase() : event.key;
  return normalizeShortcut([event.ctrlKey ? "Ctrl" : "", event.altKey ? "Alt" : "", event.shiftKey ? "Shift" : "", event.metaKey ? "Meta" : "", key].filter(Boolean).join("+"));
}

export function findShortcutConflict(bindings, candidate, exceptId = "") {
  const normalized = normalizeShortcut(candidate);
  if (!normalized) return null;
  return Object.entries(bindings || {}).find(([id, value]) => id !== exceptId && normalizeShortcut(value) === normalized)?.[0] || null;
}

export function eventMatchesShortcut(event, shortcut) {
  return shortcutFromKeyboardEvent(event) === normalizeShortcut(shortcut);
}

export function loadShortcutConfig(storage = localStorage) {
  const defaults = { "global-search": "Ctrl+K", "remote-run": "R", "new-script": "Ctrl+Shift+N" };
  try {
    const saved = JSON.parse(storage.getItem("shulkr_shortcuts") || "{}");
    return { app: { ...defaults, ...(saved.app || {}) }, scripts: saved.scripts || {} };
  } catch { return { app: defaults, scripts: {} }; }
}

export function saveShortcutConfig(config, storage = localStorage) {
  storage.setItem("shulkr_shortcuts", JSON.stringify(config));
  return config;
}
