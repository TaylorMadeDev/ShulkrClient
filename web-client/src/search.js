export function normalizedSearch(value) { return String(value || "").trim().toLocaleLowerCase(); }

export function matchesSearch(item, query, fields) {
  const needle = normalizedSearch(query);
  if (!needle) return true;
  const haystack = fields.flatMap(field => {
    const value = typeof field === "function" ? field(item) : item?.[field];
    return Array.isArray(value) ? value : [value];
  }).map(value => String(value ?? "")).join(" ").toLocaleLowerCase();
  return needle.split(/\s+/).every(part => haystack.includes(part));
}

export function filterSearch(items, query, fields) { return (items || []).filter(item => matchesSearch(item, query, fields)); }

export function buildGlobalSearchIndex({ nav = [], scripts = [], hub = [], clients = [], templates = [], libraries = [], modules = [], settings = [] }) {
  return [
    ...nav.map(([id, label, icon, description]) => ({ kind: "page", id, label, detail: description, icon, search: `${label} ${description} ${id}` })),
    ...scripts.map(item => ({ kind: "script", id: item.path, label: item.name || item.fileName || item.path, detail: "Installed script", icon: "fa-solid fa-file-code", search: `${item.name || ""} ${item.fileName || ""} ${item.path || ""} ${item.description || ""} ${item.author || ""} ${item.shortcut || ""}` })),
    ...hub.map(item => ({ kind: "hub", id: item.id, label: item.name, detail: `Script Hub · ${item.author || "Unknown author"}`, icon: "fa-solid fa-cloud-arrow-down", search: `${item.name || ""} ${item.author || ""} ${item.about || item.description || ""} ${(item.tags || []).join(" ")} ${item.category || ""} ${item.id || ""}` })),
    ...clients.map(item => ({ kind: "client", id: item.id, label: item.deviceName || item.displayName || "Minecraft client", detail: item.connected !== false ? "Connected device" : "Offline device", icon: "fa-solid fa-microchip", search: `${item.displayName || ""} ${item.deviceName || ""} ${item.id || ""}` })),
    ...templates.map(item => ({ kind: "template", id: item.id, label: item.name, detail: "Template", icon: "fa-solid fa-layer-group", search: `${item.name || ""} ${item.description || ""} ${item.category || ""} ${(item.tags || []).join(" ")} ${item.id || ""}` })),
    ...libraries.map(item => ({ kind: "library", id: item.id, label: item.name, detail: "Library", icon: "fa-solid fa-cubes", search: `${item.name || ""} ${item.description || ""} ${item.author || ""} ${item.category || ""} ${item.id || ""}` })),
    ...modules.map(item => ({ kind: "module", id: item.id, label: item.name, detail: "Module", icon: "fa-solid fa-puzzle-piece", search: `${item.name || ""} ${item.description || ""} ${item.category || ""} ${item.id || ""}` })),
    ...settings.map(item => ({ kind: "setting", id: item.id, label: item.label, detail: `Settings · ${item.tab}`, icon: "fa-solid fa-sliders", search: `${item.label} ${item.description || ""} ${item.tab} ${item.id}` }))
  ];
}

export function searchGlobalIndex(index, query, limit = 10) {
  const needle = normalizedSearch(query);
  if (!needle) return [];
  return index.filter(item => needle.split(/\s+/).every(part => item.search.toLocaleLowerCase().includes(part))).slice(0, limit);
}
