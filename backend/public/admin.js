const $ = (id) => document.getElementById(id);

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: { "X-Shulkr-Request": "dashboard", ...(options.headers || {}) }
  });
  if (!response.ok) throw new Error((await response.json().catch(() => null))?.error || response.statusText);
  return response.json();
}

const get = (path) => request(path);
const send = (path, options) => request(path, options);
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" })[char]);
const safeLocalUrl = (value) => {
  try {
    const parsed = new URL(String(value || "/web/"), location.origin);
    const loopback = ["127.0.0.1", "localhost", "::1"].includes(parsed.hostname);
    return (parsed.origin === location.origin || (loopback && ["http:", "https:"].includes(parsed.protocol))) ? parsed.href : "/web/";
  } catch {
    return "/web/";
  }
};
const card = (title, body, meta = "") => `<div class="card"><b>${escapeHtml(title)}</b><small>${escapeHtml(body || "")}</small>${meta ? `<div>${meta}</div>` : ""}</div>`;

async function load() {
  const [stats, clients, scripts, libraries, modules, libraryScripts, templates, licenses] = await Promise.all([
    get("/api/stats"), get("/api/clients"), get("/api/library/scripts"), get("/api/libraries"),
    get("/api/client-modules"), get("/api/libraries/scripts"), get("/api/templates"), get("/api/licenses")
  ]);
  $("stats").innerHTML = [
    ["Scripts", stats.scripts], ["Published", stats.publishedScripts],
    ["Libraries", stats.installedLibraries ?? stats.installedModules],
    ["Modules", stats.installedClientModules ?? modules.filter(module => module.installed !== false).length],
    ["Templates", stats.templates]
  ].map(([label, value]) => `<div class="stat"><small>${escapeHtml(label)}</small><strong>${Number(value || 0)}</strong></div>`).join("");
  $("clients").innerHTML = clients.map(client => `<tr><td>${escapeHtml(client.displayName)} <span class="pill">${escapeHtml(client.tier || "User")}</span></td><td><span class="pill"><span class="dot"></span>${client.connected ? "Connected" : "Away"}</span></td><td>${escapeHtml(client.minecraft || "unknown")}</td><td>${escapeHtml(client.lastSeenAt || "")}</td></tr>`).join("");
  $("scripts").innerHTML = scripts.length ? scripts.map(script => card(script.name, script.about, `<span class="pill">${escapeHtml(script.category)}</span> <span class="pill">${Number(script.downloads || 0)} installs</span><div class="actions"><button class="secondary" data-install="${escapeHtml(script.id)}">Install</button><button class="danger" data-delete="${escapeHtml(script.id)}">Delete</button></div>`)).join("") : card("No published scripts", "Publish a community script from Shulkr.");
  $("licenses").innerHTML = licenses.map(license => card(license.displayName, `${license.tier} / ${license.status}`, `<span class="pill">${escapeHtml((license.features || []).join(", "))}</span>`)).join("");
  $("libraries").innerHTML = libraries.map(library => card(library.name, library.description, `<span class="pill">${escapeHtml(library.category)}</span> <span class="pill">${library.installed ? "Installed" : "Available"}</span>`)).join("");
  $("modules").innerHTML = modules.length ? modules.map(module => card(module.name, module.description, `<span class="pill">${escapeHtml(module.category)}</span> <span class="pill">${module.installed ? "Installed" : "Available"}</span><div class="actions"><a class="button" href="${escapeHtml(safeLocalUrl(module.openUrl))}" target="_blank" rel="noopener noreferrer">Open</a><button class="secondary" data-module-toggle="${escapeHtml(module.id)}">${module.installed ? "Disable" : "Install"}</button></div>`)).join("") : card("No modules", "No client modules are available.");
  $("libraryScripts").innerHTML = libraryScripts.length ? libraryScripts.map(script => card(script, "Reusable local script helper.", `<span class="pill">Local</span> <span class="pill">${escapeHtml(script.split(".").pop().toUpperCase())}</span>`)).join("") : card("No local library scripts", "Mark scripts as reusable libraries from Shulkr.");
  $("templates").innerHTML = templates.map(template => card(template.name, template.description, `<span class="pill">${escapeHtml(template.category)}</span> <span class="pill">${escapeHtml(template.difficulty)}</span>`)).join("");
}

$("clearPublish").addEventListener("click", () => {
  ["publishName", "publishFile", "publishTags", "publishIcon", "publishAbout", "publishCode"].forEach(id => { $(id).value = ""; });
  $("publishMessage").textContent = "";
});

$("publishButton").addEventListener("click", async () => {
  $("publishMessage").textContent = "Publishing...";
  try {
    await send("/api/library/scripts", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({
      name: $("publishName").value, fileName: $("publishFile").value, author: $("publishAuthor").value,
      tags: $("publishTags").value.split(",").map(tag => tag.trim()).filter(Boolean), icon: $("publishIcon").value,
      about: $("publishAbout").value, code: $("publishCode").value
    }) });
    $("publishMessage").textContent = "Published.";
    await load();
  } catch (error) {
    $("publishMessage").textContent = error.message;
  }
});

document.addEventListener("click", async event => {
  const install = event.target.closest("[data-install]");
  const remove = event.target.closest("[data-delete]");
  const moduleToggle = event.target.closest("[data-module-toggle]");
  if (install) { await send(`/api/library/scripts/${encodeURIComponent(install.dataset.install)}/install`, { method: "POST" }); await load(); }
  if (remove && window.confirm("Delete this published script?")) { await send(`/api/library/scripts/${encodeURIComponent(remove.dataset.delete)}`, { method: "DELETE" }); await load(); }
  if (moduleToggle) {
    const id = moduleToggle.dataset.moduleToggle;
    const module = (await get("/api/client-modules")).find(item => item.id === id);
    if (module) { await send(`/api/client-modules/${encodeURIComponent(id)}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ installed: !module.installed }) }); await load(); }
  }
});

load().catch(error => {
  const message = document.createElement("pre");
  message.textContent = error.message;
  document.body.prepend(message);
});
