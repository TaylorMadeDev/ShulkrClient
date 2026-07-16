import "./styles.css";

import { flowStore } from "./flow/store.js";

let flowEditorModule = null;
let flowEditorPromise = null;
let flowEditorWatching = false;

async function ensureFlowEditorModule() {
  if (!flowEditorPromise) flowEditorPromise = import("./flow/editor.js");
  flowEditorModule = await flowEditorPromise;
  if (!flowEditorWatching) {
    flowEditorWatching = true;
    flowEditorModule.watchFlowBuilder(render, toast);
  }
  if (state.page === "flow" && document.querySelector("[data-flow-loading]")) render();
  return flowEditorModule;
}

function safeApiBase(value) {
  try {
    const parsed = new URL(String(value || "http://127.0.0.1:50991"));
    if (!["http:", "https:"].includes(parsed.protocol) || !["127.0.0.1", "localhost", "::1", "[::1]"].includes(parsed.hostname)) throw new Error("Backend must use a local loopback address");
    return parsed.origin;
  } catch {
    return "http://127.0.0.1:50991";
  }
}

const API_BASE = safeApiBase(localStorage.getItem("shulkr_api_base") || localStorage.getItem("shulk_api_base"));

const state = {
  page: "profile",
  online: false,
  loading: true,
  error: "",
  health: null,
  profile: null,
  stats: null,
  clients: [],
  scripts: [],
  scriptFolders: [],
  scriptSearch: "",
  scriptFolder: "all",
  libraries: [],
  clientModules: [],
  templates: [],
  library: [],
  selectedScript: null,
  selectedClient: null,
  modal: null,
  toast: "",
  notificationsOpen: false,
  notificationRead: loadNotificationRead(),
  search: "",
  timeFilter: "30d",
  metricTab: "time",
  editorScript: null,
  editorContent: "",
  editorDirty: false,
  hubSearch: "",
  hubCategory: "all",
  hubSort: "recent",
  hubPage: 1,
  hubPerPage: 12,
  hubFilters: { python: true, pyjinn: true, farming: true, combat: true, world: true, utility: true, other: true },
  statsHistory: [],
  statsSummary: null,
  statsClients: [],
  statsScripts: [],
  statsClientFilter: "all",
  statsScriptFilter: "all",
  stream: null,
  entitlements: null,
  adminOverview: null,
  billingStatus: null,
  remoteChatMessage: "",
  remoteDraftName: "QuickRemoteScript.py",
  remoteDraftCode: "# Quick remote script\nimport minescript as ms\n\nms.echo(\"Remote script online\")\n",
  settingsTab: "customization",
  theme: localStorage.getItem("shulkr_theme") || "nova",
  density: localStorage.getItem("shulkr_density") || "comfortable",
  auth: loadAuth()
};

let lightRefreshTimer = null;
let keybindingsBound = false;
window.addEventListener("beforeunload", event => { if (flowStore.snapshot().dirty) { event.preventDefault(); event.returnValue = ""; } });

const nav = [
  ["profile", "Dashboard", "fa-solid fa-house", "Dashboard"],
  ["scripts", "Scripts", "fa-solid fa-file-code", "Local scripts"],
  ["library", "Library", "fa-solid fa-cubes", "Script library"],
  ["statistics", "Statistics", "fa-solid fa-chart-line", "Statistics"],
  ["editor", "Editor", "fa-solid fa-code", "Editor"],
  ["flow", "Flow Builder", "fa-solid fa-diagram-project", "Visual automation builder"],
  ["remote", "Remote", "fa-solid fa-satellite-dish", "Remote"],
  ["settings", "Settings", "fa-solid fa-sliders", "Settings"]
];

const app = document.querySelector("#app");

applyDashboardTheme(state.theme, state.density);
init();

function loadAuth() {
  try {
    const params = new URLSearchParams(window.location.search);
    if (params.get("auth") === "error") {
      params.delete("auth");
      const cleanSearch = params.toString();
      window.history.replaceState({}, document.title, window.location.pathname + (cleanSearch ? `?${cleanSearch}` : ""));
      setTimeout(() => toast("Google sign-in failed. Please try again."), 100);
    }
    if (params.has("token")) {
      params.delete("token");
      params.delete("name");
      params.delete("email");
      const cleanSearch = params.toString();
      window.history.replaceState({}, document.title, window.location.pathname + (cleanSearch ? `?${cleanSearch}` : ""));
    }
  } catch (error) {
    console.error("loadAuth error", error);
  }
  localStorage.removeItem("shulkr_token");
  return { loggedIn: false, view: "landing", user: null, token: null };
}

function saveAuth(user) {
  localStorage.removeItem("shulkr_token");
  state.auth = { loggedIn: true, view: "dashboard", user, token: null };
}

function clearAuth() {
  localStorage.removeItem("shulkr_token");
  flowStore.configure({ api: null, userId: "anonymous" });
  state.adminOverview = null;
  state.entitlements = null;
  state.billingStatus = null;
  state.auth = { loggedIn: false, view: "landing", user: null, token: null };
}

function loadNotificationRead() {
  try { return JSON.parse(localStorage.getItem("shulkr_notification_read") || "{}"); } catch { return {}; }
}

function hasFeature(feature) {
  const features = state.entitlements?.features || state.auth.user?.features || [];
  return state.auth.user?.isAdmin || features.includes(feature);
}

async function init() {
  render();
  bindGlobalShortcuts();
  handleBillingQueryState();
  try {
    const health = await api("/api/health");
    state.online = Boolean(health.ok);
  } catch {
    state.online = false;
  }
  render();
  try {
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get("auth_code");
    if (authCode) {
      params.delete("auth_code");
      window.history.replaceState({}, document.title, window.location.pathname + (params.toString() ? `?${params}` : ""));
      const exchanged = await api("/api/auth/exchange", { method: "POST", body: JSON.stringify({ code: authCode }) });
      saveAuth(exchanged.user);
    }
    const me = await api("/api/auth/me");
    state.auth.loggedIn = true;
    state.auth.view = "dashboard";
    state.auth.user = {
        id: me.id,
        displayName: me.displayName,
        email: me.email,
        username: me.username,
        tier: me.tier,
        avatar: me.avatar,
        isAdmin: Boolean(me.isAdmin),
        features: Array.isArray(me.features) ? me.features : []
      };
    flowStore.configure({ api, userId: state.auth.user.id });
  } catch {
    clearAuth();
    render();
    return;
  }
  if (state.auth.loggedIn) {
    if (sessionStorage.getItem("shulkr_billing_sync") === "1") {
      try {
        state.billingStatus = await api("/api/billing/sync", { method: "POST", body: JSON.stringify({}) });
      } catch {
      } finally {
        sessionStorage.removeItem("shulkr_billing_sync");
      }
    }
    await refreshAll();
    ensureLightRefresh();
  }
}

async function refreshAll() {
  state.loading = true;
  state.error = "";
  render();
  try {
    const analyticsPath = statsAnalyticsPath();
    const requests = [
      api("/api/health"),
      api("/api/snapshot"),
      apiOptional("/api/library/scripts", []),
      apiOptional("/api/libraries", []),
      apiOptional("/api/client-modules", []),
      api("/api/clients"),
      apiOptional("/api/scripts/folders", []),
      apiOptional(analyticsPath, { history: [], summary: null, clients: [], scripts: [] }),
      apiOptional("/api/stream/status", null),
      apiOptional("/api/billing/status", null)
    ];
    if (state.auth.user?.isAdmin) {
      requests.push(apiOptional("/api/admin/overview", null));
    }
    const [health, snapshot, library, libraries, clientModules, clients, scriptFolders, analytics, stream, billingStatus, adminOverview] = await Promise.all(requests);
    state.online = Boolean(health.ok);
    state.health = health;
    state.profile = snapshot.profile;
    state.stats = snapshot.stats;
    state.entitlements = snapshot.entitlements || null;
    state.clients = clients || [];
    state.scripts = snapshot.scripts || [];
    state.scriptFolders = Array.isArray(scriptFolders) ? scriptFolders : [];
    state.libraries = libraries || [];
    state.clientModules = clientModules || [];
    state.templates = snapshot.templates || [];
    state.library = library || [];
    state.statsHistory = Array.isArray(analytics?.history) ? analytics.history : [];
    state.statsSummary = analytics?.summary || null;
    state.statsClients = Array.isArray(analytics?.clients) ? analytics.clients : [];
    state.statsScripts = Array.isArray(analytics?.scripts) ? analytics.scripts : [];
    state.stream = stream || null;
    state.billingStatus = billingStatus || null;
    state.adminOverview = adminOverview || null;
    const currentScriptPath = state.selectedScript?.path;
    const currentClientId = state.selectedClient?.id;
    state.selectedScript = state.scripts.find((script) => script.path === currentScriptPath) || state.scripts[0] || null;
    state.selectedClient = state.clients.find((client) => client.id === currentClientId) || state.clients[0] || null;
    if (state.page === "remote") {
      await ensureRemoteStream();
    }
  } catch (error) {
    state.online = false;
    state.health = null;
    state.error = "Server is offline";
  } finally {
    state.loading = false;
    render();
  }
}

async function refreshLight() {
  try {
    const health = await api("/api/health");
    state.online = Boolean(health.ok);
    if (!state.online) {
      state.error = "Server is offline";
      render();
      return;
    }
    if (state.error) {
      await refreshAll();
      return;
    }
    await refreshLiveState();
    if (state.page === "flow") {
      const connectedClient = state.clients.find((client) => client.connected !== false);
      flowStore.setClientConnection(Boolean(connectedClient), true, connectedClient?.id || "");
    } else {
      render();
    }
  } catch {
    state.online = false;
    state.error = "Server is offline";
    render();
  }
}

async function refreshLiveState() {
  const requests = [
    api("/api/clients"),
    api("/api/scripts"),
    apiOptional("/api/stream/status", null),
    apiOptional("/api/scripts/folders", [])
  ];
  if (state.page === "statistics") {
    requests.push(apiOptional(statsAnalyticsPath(), { history: [], summary: null, clients: [], scripts: [] }));
  }
  const [clients, scripts, stream, scriptFolders, analytics] = await Promise.all(requests);
  state.clients = Array.isArray(clients) ? clients : [];
  state.scripts = Array.isArray(scripts) ? scripts : [];
  state.stream = stream || null;
  state.scriptFolders = Array.isArray(scriptFolders) ? scriptFolders : [];
  if (analytics) {
    state.statsHistory = Array.isArray(analytics.history) ? analytics.history : [];
    state.statsSummary = analytics.summary || null;
    state.statsClients = Array.isArray(analytics.clients) ? analytics.clients : [];
    state.statsScripts = Array.isArray(analytics.scripts) ? analytics.scripts : [];
  }
  const currentClientId = state.selectedClient?.id;
  const currentScriptPath = state.selectedScript?.path;
  state.selectedClient = state.clients.find((client) => client.id === currentClientId) || state.clients[0] || null;
  state.selectedScript = state.scripts.find((script) => script.path === currentScriptPath) || state.scripts[0] || null;
  if (state.page === "remote") {
    await ensureRemoteStream();
  }
}

const refreshRemoteState = refreshLiveState;

function statsAnalyticsPath() {
  const query = new URLSearchParams();
  if (state.timeFilter) query.set("range", state.timeFilter);
  if (state.statsClientFilter && state.statsClientFilter !== "all") query.set("clientId", state.statsClientFilter);
  if (state.statsScriptFilter && state.statsScriptFilter !== "all") query.set("scriptPath", state.statsScriptFilter);
  return `/api/stats/analytics?${query.toString()}`;
}

function ensureLightRefresh() {
  if (lightRefreshTimer) return;
  lightRefreshTimer = setInterval(refreshLight, 10000);
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", "X-Shulkr-Request": "dashboard", ...(options.headers || {}) };
  if (state.auth.token) headers.Authorization = `Bearer ${state.auth.token}`;
  const response = await fetch(API_BASE + path, {
    ...options,
    headers,
    credentials: "include"
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (response.status === 401 && state.auth.loggedIn) {
    clearAuth();
    render();
  }
  if (!response.ok) {
    const routeUnavailable = response.status === 404 && /Cannot (?:GET|POST|PUT|PATCH|DELETE) \/api\//i.test(text);
    const message = body?.error || (routeUnavailable
      ? "Flow Builder persistence is unavailable. Restart the Shulkr backend and retry."
      : response.statusText || "Backend request failed");
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  if (text && body === null) throw new Error("Backend returned an invalid response");
  return body;
}

async function apiOptional(path, fallback = null) {
  try {
    return await api(path);
  } catch (error) {
    if (error?.status === 403 || error?.status === 404 || error?.status === 503) {
      return fallback;
    }
    throw error;
  }
}

function handleBillingQueryState() {
  const params = new URLSearchParams(window.location.search);
  let message = "";
  if (params.get("checkout") === "success") {
    message = "Stripe checkout completed. Refreshing your tier...";
    sessionStorage.setItem("shulkr_billing_sync", "1");
  } else if (params.get("checkout") === "cancelled") {
    message = "Stripe checkout was cancelled.";
  } else if (params.get("billing") === "portal") {
    message = "Returned from the billing portal.";
    sessionStorage.setItem("shulkr_billing_sync", "1");
  }
  if (!message) return;
  params.delete("checkout");
  params.delete("tier");
  params.delete("billing");
  const cleanSearch = params.toString();
  window.history.replaceState({}, document.title, window.location.pathname + (cleanSearch ? `?${cleanSearch}` : ""));
  setTimeout(() => toast(message), 100);
}

function navItems() {
  const items = nav.filter(([id]) => {
    if (id === "editor") return hasFeature("scripts.write");
    if (id === "statistics") return hasFeature("stats.read");
    if (id === "remote") return hasFeature("remote.control");
    if (id === "billing") return stripeEnabled();
    return true;
  });
  return state.auth.user?.isAdmin
    ? [...items, ["admin", "Admin", "fa-solid fa-shield-halved", "Admin"]]
    : items;
}

function stripeEnabled() {
  return Boolean(state.billingStatus?.enabled || state.health?.billing?.stripe);
}

function render() {
  if (!state.auth.loggedIn || state.auth.view !== "dashboard") {
    const viewMap = {
      signin: signInPage(),
      signup: signUpPage(),
      faq: faqPage(),
      changelog: changelogPage(),
      purchase: purchasePage(),
      terms: termsPage(),
      cookies: cookiePolicyPage(),
      features: featuresPage(),
      default: landingPage()
    };
    app.innerHTML = `
      ${viewMap[state.auth.view] || viewMap.default}
      ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
    `;
    bindLanding();
    return;
  }
  app.innerHTML = `
    <div class="shell">
      ${sidebar()}
      <main class="main">
        ${state.error ? offlineState() : page()}
        ${state.error ? "" : authenticatedFooter()}
      </main>
    </div>
    ${state.modal ? renderModal() : ""}
    ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
  `;
  bind();
}

function sidebar() {
  const user = state.auth.user || state.profile || { displayName: "EnderUser", tier: "Premium" };
  const items = navItems();
  const grouped = [
    ["Workspace", ["profile", "scripts", "library", "statistics", "editor", "flow", "remote", "settings"]]
  ].map(([title, ids]) => [title, ids.map((id) => items.find(([itemId]) => itemId === id)).filter(Boolean)]);
  return `
    <aside class="sidebar">
      <div class="sidebar-shell">
        <a href="#" class="sidebar-brand" data-landing title="Back to landing page">
          <div class="sidebar-brand-mark">
            <img src="/shulkr-icons.png" alt="Shulkr logo">
          </div>
          <div class="sidebar-brand-copy">
            <strong>Shulkr</strong>
            <span>Control Center</span>
          </div>
        </a>

        <nav class="sidebar-nav">
          ${grouped.map(([title, sectionItems]) => sidebarSection(title, sectionItems)).join("")}
        </nav>
        <div class="sidebar-user-card">
          <span class="sidebar-account-avatar">${escapeHtml((user.displayName || "U").slice(0, 1))}</span>
          <span class="sidebar-user-copy">
            <strong>${escapeHtml(user.displayName || "EnderUser")}</strong>
            <span>${escapeHtml(user.tier || "Premium")}</span>
          </span>
        </div>
      </div>
    </aside>
  `;
}

function authenticatedFooter() {
  return `
    <footer class="app-footer">
      <div class="app-footer-copy">
        <span>Shulkr dashboard</span>
        <span>•</span>
        <span>${escapeHtml(state.auth.user?.tier || state.profile?.tier || "Premium")}</span>
        <span>•</span>
        <span>${state.online ? "Client linked" : "Waiting for client"}</span>
      </div>
      <div class="app-footer-links">
        <button class="btn-link" data-page="profile">Home</button>
        <button class="btn-link" data-page="remote">Remote</button>
        <button class="btn-link" data-page="settings">Settings</button>
        <button class="btn-link" data-landing>Website</button>
      </div>
    </footer>
  `;
}

function landingPage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero">
        <div class="hero-bg">
          <div class="mountain-glow"></div>
          <div class="shards-container">
            <div class="shard shard-1"></div>
            <div class="shard shard-2"></div>
            <div class="shard shard-3"></div>
            <div class="shard shard-4"></div>
            <div class="shard shard-5"></div>
          </div>
        </div>
        <div class="hero-content">
          <div class="hero-left">
            <div class="hero-tag"><span class="tag-line"></span><span>END. ELEVATED.</span></div>
            <h1 class="hero-title">
              <span class="title-line">BUILT FOR</span>
              <span class="title-line title-accent">THE END.</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> SHULKR IS A MINECRAFT SCRIPTING PLATFORM AND WEB DASHBOARD FOR PLAYERS WHO AUTOMATE MORE.</p>
            <div class="feature-badges">
              <div class="badge"><div class="badge-icon"><i class="fa-solid fa-code"></i></div><div class="badge-text"><strong>PYTHON</strong><span>SCRIPTS</span></div></div>
              <div class="badge"><div class="badge-icon"><i class="fa-solid fa-satellite-dish"></i></div><div class="badge-text"><strong>REMOTE</strong><span>DASHBOARD</span></div></div>
              <div class="badge"><div class="badge-icon"><i class="fa-solid fa-robot"></i></div><div class="badge-text"><strong>IN-GAME</strong><span>AUTOMATION</span></div></div>
              <div class="badge"><div class="badge-icon"><i class="fa-solid fa-users"></i></div><div class="badge-text"><strong>SCRIPTER</strong><span>COMMUNITY</span></div></div>
            </div>
          </div>
          <div class="hero-right">
            <div class="release-card">
              <div class="release-header"><span class="release-label">LATEST RELEASE</span><span class="release-close">×</span></div>
              <div class="release-version"><h2>v2.0</h2><span class="release-codename">/ OBSIDIAN</span></div>
              <p class="release-tagline">AUTOMATE. CUSTOMIZE. CONTROL.</p>
              <ul class="release-features">
                <li><span class="check"><i class="fa-solid fa-check"></i></span> PYTHON SCRIPTING</li>
                <li><span class="check"><i class="fa-solid fa-check"></i></span> REMOTE DASHBOARD</li>
                <li><span class="check"><i class="fa-solid fa-check"></i></span> ACCOUNT MANAGER</li>
                <li><span class="check"><i class="fa-solid fa-check"></i></span> OVERLAY TOOLS</li>
              </ul>
              <button class="btn btn-download" data-auth="signup"><i class="fa-solid fa-download"></i><span>DOWNLOAD NOW</span></button>
              <p class="release-meta">Windows 10/11 · 64-bit</p>
            </div>
          </div>
        </div>
        <div class="hero-footer">
          <p class="footer-tag"><span class="footer-line"></span><span>NOT JUST A CLIENT.</span><span class="footer-accent">IT'S YOUR WORKBENCH</span></p>
          <div class="social-icons">
            <a href="#community" class="social-icon" aria-label="Discord"><i class="fa-brands fa-discord"></i></a>
            <a href="#community" class="social-icon" aria-label="X / Twitter"><i class="fa-brands fa-x-twitter"></i></a>
            <a href="#community" class="social-icon" aria-label="YouTube"><i class="fa-brands fa-youtube"></i></a>
            <a href="#community" class="social-icon add-icon" aria-label="More"><i class="fa-solid fa-plus"></i></a>
          </div>
        </div>
      </section>

      <section class="landing-section about-section" id="features">
        <div class="about-bg">
          <div class="about-shard about-shard-1"></div>
          <div class="about-shard about-shard-2"></div>
        </div>
        <div class="container about-grid">
          <div class="about-text">
            <h2 class="section-title"><span class="title-line">MORE THAN</span><span class="title-line title-accent">JUST A CLIENT.</span></h2>
            <p class="about-desc">Shulkr is a Minecraft Fabric mod that brings Python scripting into your world. Write scripts, manage accounts, launch them remotely from your browser, and extend the game with modules, overlays, and templates.</p>
            <button class="btn btn-secondary" data-auth="signup"><span>EXPLORE FEATURES</span><i class="fa-solid fa-arrow-right"></i></button>
          </div>
          <div class="about-visual">
            <div class="app-window">
              <div class="window-header">
                <div class="window-dots"><span></span><span></span><span></span></div>
                <div class="window-title">Shulkr Client</div>
              </div>
              <div class="window-body">
                <div class="window-sidebar">
                  <div class="sidebar-item active">Dashboard</div>
                  <div class="sidebar-item">Scripts</div>
                  <div class="sidebar-item">Editor</div>
                  <div class="sidebar-item">Modules</div>
                  <div class="sidebar-item">Overlays</div>
                  <div class="sidebar-item">Settings</div>
                </div>
                <div class="window-content">
                  <div class="code-line"><span class="code-keyword">function</span> <span class="code-func">onTick</span>() {</div>
                  <div class="code-line indent"><span class="code-var">player</span>.<span class="code-func">boost</span>(<span class="code-num">1.5</span>);</div>
                  <div class="code-line indent"><span class="code-keyword">if</span> (<span class="code-var">fps</span> &lt; <span class="code-num">60</span>) {</div>
                  <div class="code-line indent-2"><span class="code-var">optimizer</span>.<span class="code-func">run</span>();</div>
                  <div class="code-line indent">}</div>
                  <div class="code-line">}</div>
                  <div class="code-line blank"></div>
                  <div class="code-line"><span class="code-comment">// Shulkr v2.0 — Obsidian</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="landing-section features-section" id="performance">
        <div class="container">
          <div class="section-header">
            <div class="section-tag"><span class="tag-line"></span><span>EVERYTHING YOU NEED</span></div>
            <h2 class="section-title"><span class="title-line">POWERFUL FEATURES</span><span class="title-line title-accent">BUILT FOR CREATORS</span></h2>
            <p class="section-desc">From in-game automation to remote management, Shulkr gives you the tools to build, run, and share Minecraft scripts.</p>
          </div>
          <div class="features-grid">
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-code"></i></div><h3>PYTHON SCRIPTING</h3><p>Write Minecraft automation in Python with the bundled Minescript runtime and a growing API surface.</p></div>
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-file-code"></i></div><h3>TEMPLATES</h3><p>Jumpstart your ideas with pre-made script templates for farms, builders, chat bots, and more.</p></div>
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-cubes"></i></div><h3>MODULES</h3><p>Extend what Shulkr can do with client modules and libraries that plug into your scripts.</p></div>
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-desktop"></i></div><h3>WINDOWSPY</h3><p>Inspect Minecraft windows and UI elements to build precise overlays and automation.</p></div>
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-eye"></i></div><h3>OVERLAY TOOLS</h3><p>Render custom HUDs, notifications, and info panels directly in-game.</p></div>
            <div class="feature-card"><div class="feature-icon"><i class="fa-solid fa-satellite-dish"></i></div><h3>REMOTE DASHBOARD</h3><p>Manage accounts, launch scripts, and browse your library from any browser.</p></div>
          </div>
        </div>
      </section>

      <section class="landing-section download-section" id="download">
        <div class="container download-grid">
          <div class="download-text">
            <div class="section-tag"><span class="tag-line"></span><span>GET SHULKR</span></div>
            <h2 class="section-title"><span class="title-line">DOWNLOAD.</span><span class="title-line title-accent">DOMINATE.</span></h2>
            <p class="download-desc">Get the latest Shulkr build for Windows 10/11. Install the Fabric mod, sign in, and start scripting inside Minecraft.</p>
            <div class="download-actions">
              <button class="btn btn-primary" data-auth="signup"><i class="fa-solid fa-download"></i><span>DOWNLOAD FOR WINDOWS</span></button>
              <button class="btn btn-secondary" data-view="changelog"><span>VIEW CHANGELOG</span><i class="fa-solid fa-arrow-right"></i></button>
            </div>
            <p class="download-meta">v2.0 Obsidian · Fabric 1.20+ · 64-bit · ~180 MB</p>
          </div>
          <div class="download-card">
            <div class="download-header"><span>SYSTEM REQUIREMENTS</span></div>
            <ul class="download-specs">
              <li><i class="fa-brands fa-windows"></i><span>Windows 10/11 64-bit</span></li>
              <li><i class="fa-solid fa-microchip"></i><span>4 GB RAM minimum</span></li>
              <li><i class="fa-solid fa-hard-drive"></i><span>500 MB free space</span></li>
              <li><i class="fa-solid fa-wifi"></i><span>Internet connection</span></li>
            </ul>
          </div>
        </div>
      </section>

      <section class="landing-section showcase-section" id="showcase">
        <div class="showcase-bg">
          <div class="showcase-shard showcase-shard-1"></div>
          <div class="showcase-shard showcase-shard-2"></div>
        </div>
        <div class="container">
          <div class="section-header showcase-header">
            <div class="section-tag"><span class="tag-line"></span><span>SEE IT IN ACTION</span></div>
            <h2 class="section-title"><span class="title-line">POWERFUL.</span><span class="title-line title-accent">INTUITIVE.</span></h2>
            <p class="section-desc">A clean web interface for managing your Minecraft scripts and accounts from anywhere.</p>
          </div>
          <div class="showcase-stage">
            <div class="showcase-frame">
              <img src="/main.png" alt="Shulkr Client Interface" class="showcase-image" data-hide-on-error>
              <div class="showcase-glow"></div>
            </div>
            <div class="floating-icons">
              <img src="/shulkr-icons.png" alt="Shulkr Icons" class="icons-cluster icons-cluster-1" data-hide-on-error>
              <img src="/shulkr-icons.png" alt="Shulkr Icons" class="icons-cluster icons-cluster-2" data-hide-on-error>
            </div>
          </div>
        </div>
      </section>

      <section class="landing-section stats-section" id="security">
        <div class="stats-bg"></div>
        <div class="container">
          <div class="stats-tag"><span class="tag-line"></span><span>BUILT FOR SCRIPTS THAT RUN AROUND THE CLOCK</span></div>
          <div class="stats-grid">
            <div class="stat-item"><span class="stat-number" data-target="100">0</span><span class="stat-suffix">+</span><span class="stat-label">API CALLS</span></div>
            <div class="stat-item"><span class="stat-number" data-target="50">0</span><span class="stat-suffix">+</span><span class="stat-label">SCRIPT TEMPLATES</span></div>
            <div class="stat-item"><span class="stat-number" data-target="99">0</span><span class="stat-suffix">%</span><span class="stat-label">LOCAL FIRST</span></div>
            <div class="stat-item"><span class="stat-number" data-target="24">0</span><span class="stat-suffix">/7</span><span class="stat-label">AUTOMATION</span></div>
          </div>
        </div>
      </section>

      <section class="landing-section community-section" id="community">
        <div class="community-bg">
          <div class="community-shard community-shard-1"></div>
          <div class="community-shard community-shard-2"></div>
        </div>
        <div class="container community-grid">
          <div class="community-text">
            <div class="section-tag"><span class="tag-line"></span><span>READY TO ELEVATE?</span></div>
            <h2 class="section-title"><span class="title-line">JOIN THE</span><span class="title-line title-accent">SHULKR COMMUNITY</span></h2>
            <p class="community-desc">Share scripts, get help, show off your automations, and connect with other Minecraft scripters.</p>
            <a href="#community" class="btn btn-primary"><span>JOIN OUR DISCORD</span><i class="fa-solid fa-arrow-right"></i></a>
          </div>
          <div class="community-visual">
            <div class="discord-card">
              <div class="discord-header"><span>Shulkr Community</span><i class="fa-solid fa-chevron-down"></i></div>
              <div class="discord-body">
                <div class="channel-list">
                  <div class="channel-category">announcements</div>
                  <div class="channel"># releases</div>
                  <div class="channel"># updates</div>
                  <div class="channel-category">scripts</div>
                  <div class="channel"># scripts</div>
                  <div class="channel"># showcase</div>
                  <div class="channel-category">support</div>
                  <div class="channel"># support</div>
                  <div class="channel"># general</div>
                </div>
                <div class="chat-preview">
                  <div class="message">
                    <div class="message-avatar">S</div>
                    <div class="message-content">
                      <div class="message-author">Shulkr <span class="bot-tag">BOT</span></div>
                      <div class="message-text">Shulkr v2.0 is now live! Check out the new performance improvements, modules, and more.</div>
                    </div>
                  </div>
                  <div class="message">
                    <div class="message-avatar" style="background: #5865F2;">P</div>
                    <div class="message-content">
                      <div class="message-author">Player</div>
                      <div class="message-text">Just made this script! 🔥</div>
                      <div class="message-file"><i class="fa-solid fa-file-code"></i><span>AutoFarm.lua</span></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a href="#features">Features</a>
            <a href="#scripts">Scripts</a>
            <a href="#showcase">Dashboard</a>
            <a href="#community">Community</a>
            <a href="#download">Download</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function signInPage() {
  return authPage("Sign In", "signin", "Welcome back, pilot.", "Don't have an account?", "signup", "Create one");
}

function signUpPage() {
  return authPage("Get Started", "signup", "Join the endgame.", "Already have an account?", "signin", "Sign in");
}

function authPage(title, mode, subtitle, switchText, switchMode, switchLabel) {
  return `
    <div class="auth-page">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <div class="auth-card">
        <a href="#" class="logo" data-landing>
          <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
          <span>SHULKR</span>
        </a>
        <h1>${escapeHtml(title)}</h1>
        <p class="auth-subtitle">${escapeHtml(subtitle)}</p>
        <form class="auth-form" data-auth-form="${mode}">
          ${mode === "signup" ? `<div class="form-group"><label>Display name</label><input type="text" id="auth-name" placeholder="Your name" maxlength="80" autocomplete="name" required /></div>` : ""}
          <div class="form-group">
            <label>${mode === "signin" ? "Username or Email" : "Email"}</label>
            <input type="${mode === "signin" ? "text" : "email"}" id="auth-email" placeholder="${mode === "signin" ? "Username or email" : "you@example.com"}" maxlength="254" autocomplete="username" required />
          </div>
          <div class="form-group">
            <label>Password</label>
            <input type="password" id="auth-password" placeholder="••••••••••" minlength="${mode === "signup" ? "10" : "1"}" autocomplete="${mode === "signup" ? "new-password" : "current-password"}" required />
          </div>
          ${mode === "signup" ? `<div class="form-group"><label>Confirm Password</label><input type="password" id="auth-password2" placeholder="••••••••••" minlength="10" autocomplete="new-password" required /></div>` : ""}
          <button type="submit" class="btn btn-primary" style="width: 100%;">${mode === "signin" ? "Sign In" : "Create Account"}</button>
        </form>
        <div class="auth-divider"><span>or</span></div>
        <button class="btn btn-secondary" style="width: 100%; justify-content: center;" data-google-auth>
          <i class="fa-brands fa-google"></i>
          <span>${mode === "signin" ? "Sign in with Google" : "Continue with Google"}</span>
        </button>
        <p class="auth-switch">${escapeHtml(switchText)} <button class="btn-link" data-auth="${switchMode}">${escapeHtml(switchLabel)}</button></p>
        <button class="btn btn-secondary" style="width: 100%; margin-top: 12px;" data-landing>Back to website</button>
      </div>
    </div>
  `;
}

function faqPage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero" style="padding: 100px 0 40px;">
        <div class="hero-content">
          <div class="hero-left">
            <div class="hero-tag"><span class="tag-line"></span><span>QUESTIONS ANSWERED</span></div>
            <h1 class="hero-title">
              <span class="title-line">FREQUENTLY ASKED</span>
              <span class="title-line title-accent">QUESTIONS</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> FIND ANSWERS TO COMMON QUESTIONS ABOUT SHULKR.</p>
          </div>
        </div>
      </section>

      <section class="landing-section" style="padding: 60px 0;">
        <div class="container">
          <div class="faq-grid">
            <div class="faq-item">
              <h3>What is Shulkr?</h3>
              <p>Shulkr is a Minecraft Fabric mod that lets you write Python scripts to automate gameplay. It includes a web dashboard for remote management, script templates, modules, and overlay tools.</p>
            </div>
            <div class="faq-item">
              <h3>Is Shulkr free?</h3>
              <p>Shulkr has a free tier with essential features. Premium tiers unlock additional templates, modules, and extended API access. Check our pricing for details.</p>
            </div>
            <div class="faq-item">
              <h3>What Minecraft versions does Shulkr support?</h3>
              <p>Shulkr supports Minecraft 1.20 and later with Fabric mod loader. We regularly update to support the latest stable versions.</p>
            </div>
            <div class="faq-item">
              <h3>Do I need to know Python?</h3>
              <p>Yes, Shulkr uses Python for scripting. If you're new to Python, we provide templates and documentation to help you get started.</p>
            </div>
            <div class="faq-item">
              <h3>Can I share my scripts with others?</h3>
              <p>Absolutely! Shulkr includes a community hub where you can publish, share, and discover scripts from other players.</p>
            </div>
            <div class="faq-item">
              <h3>Is it safe to use?</h3>
              <p>Shulkr runs locally on your machine first. Your scripts and data are encrypted when stored or synced. We never access your Minecraft accounts without permission.</p>
            </div>
            <div class="faq-item">
              <h3>Can I create custom overlays?</h3>
              <p>Yes! With WindowSpy and overlay tools, you can render custom HUDs, notifications, and info panels directly in Minecraft.</p>
            </div>
            <div class="faq-item">
              <h3>How do I get support?</h3>
              <p>Join our Discord community for help, documentation, and to connect with other scripters. We're always here to help you build amazing automations.</p>
            </div>
          </div>
        </div>
      </section>

      <section class="landing-section community-section">
        <div class="container community-grid">
          <div class="community-text">
            <h2 class="section-title"><span class="title-line">STILL HAVE</span><span class="title-line title-accent">QUESTIONS?</span></h2>
            <p class="community-desc">Join our Discord and connect with the Shulkr community. Get real-time support and share your creations.</p>
            <a href="#community" class="btn btn-primary"><span>JOIN OUR DISCORD</span><i class="fa-solid fa-arrow-right"></i></a>
          </div>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a data-view="features">Features</a>
            <a data-view="faq">FAQ</a>
            <a data-landing>Home</a>
            <a data-view="terms">Terms</a>
            <a data-view="cookies">Privacy</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function featuresPage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero" style="padding: 100px 0 40px;">
        <div class="hero-content">
          <div class="hero-left">
            <div class="hero-tag"><span class="tag-line"></span><span>SHOWCASE</span></div>
            <h1 class="hero-title">
              <span class="title-line">EXPLORE</span>
              <span class="title-line title-accent">THE FEATURES</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> DISCOVER EVERYTHING SHULKR CAN DO FOR YOUR MINECRAFT AUTOMATION.</p>
          </div>
        </div>
      </section>

      <section class="landing-section features-section" style="padding: 60px 0;">
        <div class="container">
          <div class="features-grid">
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-code"></i></div>
              <h3>PYTHON SCRIPTING</h3>
              <p>Write powerful automation scripts in Python with the bundled Minescript runtime and comprehensive API access.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-file-code"></i></div>
              <h3>SCRIPT TEMPLATES</h3>
              <p>Start quickly with pre-built templates for farms, builders, combat helpers, and more. Customize for your needs.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-cubes"></i></div>
              <h3>MODULES</h3>
              <p>Extend functionality with client modules and reusable libraries. Share and discover community modules.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-desktop"></i></div>
              <h3>WINDOWSPY</h3>
              <p>Inspect Minecraft windows and UI elements to build advanced overlays and precision automation tools.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-eye"></i></div>
              <h3>CUSTOM OVERLAYS</h3>
              <p>Render HUDs, notifications, and info panels directly in-game with overlay tools and WindowSpy integration.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-satellite-dish"></i></div>
              <h3>REMOTE DASHBOARD</h3>
              <p>Manage accounts, launch scripts, and browse your library from any browser, anywhere in the world.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-users"></i></div>
              <h3>COMMUNITY HUB</h3>
              <p>Share scripts, get help, and connect with thousands of other Minecraft scripters and automation enthusiasts.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon"><i class="fa-solid fa-lock"></i></div>
              <h3>SECURE ACCOUNTS</h3>
              <p>Securely manage multiple Minecraft accounts with encrypted storage and industry-standard security practices.</p>
            </div>
          </div>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a data-view="features">Features</a>
            <a data-view="faq">FAQ</a>
            <a data-landing>Home</a>
            <a data-view="terms">Terms</a>
            <a data-view="cookies">Privacy</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function purchasePage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero" style="padding: 100px 0 40px;">
        <div class="hero-content">
          <div class="hero-left">
            <div class="hero-tag"><span class="tag-line"></span><span>PRICING</span></div>
            <h1 class="hero-title">
              <span class="title-line">CHOOSE YOUR</span>
              <span class="title-line title-accent">PLAN</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> UPGRADE YOUR SHULKR EXPERIENCE WITH PREMIUM FEATURES.</p>
          </div>
        </div>
      </section>

      <section class="landing-section" style="padding: 60px 0;">
        <div class="container">
          <div class="pricing-grid">
            <div class="pricing-card">
              <div class="pricing-header">
                <h3>FREE</h3>
                <div class="pricing-price"><span class="price-amount">$0</span><span class="price-period">/month</span></div>
              </div>
              <ul class="pricing-features">
                <li><i class="fa-solid fa-check"></i> Python Scripting</li>
                <li><i class="fa-solid fa-check"></i> 5 Script Limit</li>
                <li><i class="fa-solid fa-check"></i> Remote Dashboard</li>
                <li><i class="fa-solid fa-check"></i> Community Access</li>
                <li class="disabled"><i class="fa-solid fa-x"></i> Premium Templates</li>
                <li class="disabled"><i class="fa-solid fa-x"></i> Client Modules</li>
                <li class="disabled"><i class="fa-solid fa-x"></i> Priority Support</li>
              </ul>
              <button class="btn btn-secondary" data-auth="signup">GET STARTED</button>
            </div>

            <div class="pricing-card featured">
              <div class="featured-badge">RECOMMENDED</div>
              <div class="pricing-header">
                <h3>PRO</h3>
                <div class="pricing-price"><span class="price-amount">$9.99</span><span class="price-period">/month</span></div>
              </div>
              <ul class="pricing-features">
                <li><i class="fa-solid fa-check"></i> Python Scripting</li>
                <li><i class="fa-solid fa-check"></i> Unlimited Scripts</li>
                <li><i class="fa-solid fa-check"></i> Remote Dashboard</li>
                <li><i class="fa-solid fa-check"></i> Community Access</li>
                <li><i class="fa-solid fa-check"></i> Premium Templates</li>
                <li><i class="fa-solid fa-check"></i> Client Modules</li>
                <li class="disabled"><i class="fa-solid fa-x"></i> Priority Support</li>
              </ul>
              <button class="btn btn-primary" data-auth="signin">SIGN IN TO UPGRADE</button>
            </div>

            <div class="pricing-card">
              <div class="pricing-header">
                <h3>PREMIUM</h3>
                <div class="pricing-price"><span class="price-amount">$19.99</span><span class="price-period">/month</span></div>
              </div>
              <ul class="pricing-features">
                <li><i class="fa-solid fa-check"></i> Python Scripting</li>
                <li><i class="fa-solid fa-check"></i> Unlimited Scripts</li>
                <li><i class="fa-solid fa-check"></i> Remote Dashboard</li>
                <li><i class="fa-solid fa-check"></i> Community Access</li>
                <li><i class="fa-solid fa-check"></i> Premium Templates</li>
                <li><i class="fa-solid fa-check"></i> Client Modules</li>
                <li><i class="fa-solid fa-check"></i> Priority Support</li>
              </ul>
              <button class="btn btn-secondary" data-auth="signin">SIGN IN TO UPGRADE</button>
            </div>
          </div>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a data-view="features">Features</a>
            <a data-view="faq">FAQ</a>
            <a data-landing>Home</a>
            <a data-view="terms">Terms</a>
            <a data-view="cookies">Privacy</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function termsPage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero" style="padding: 100px 0 40px;">
        <div class="hero-content">
          <div class="hero-left">
            <h1 class="hero-title">
              <span class="title-line">TERMS OF</span>
              <span class="title-line title-accent">SERVICE</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> Please read these terms carefully before using Shulkr.</p>
          </div>
        </div>
      </section>

      <section class="landing-section legal-section" style="padding: 60px 0;">
        <div class="container legal-content">
          <h2>1. Acceptance of Terms</h2>
          <p>By accessing and using Shulkr, you accept and agree to be bound by the terms and provision of this agreement. If you do not agree to abide by the above, please do not use this service.</p>

          <h2>2. Use License</h2>
          <p>Shulkr grants you a limited, non-exclusive, non-transferable license to use the software in accordance with these terms. You agree not to:</p>
          <ul>
            <li>Use the software for any illegal purpose or in violation of Minecraft's End User License Agreement</li>
            <li>Attempt to gain unauthorized access to the platform or its systems</li>
            <li>Distribute or transmit malicious code through Shulkr</li>
            <li>Reverse engineer or decompile the software without permission</li>
          </ul>

          <h2>3. Disclaimer of Warranties</h2>
          <p>Shulkr is provided on an "as is" and "as available" basis. We make no warranties, expressed or implied, regarding the software or any content provided through it. We disclaim all warranties including, but not limited to, warranties of merchantability and fitness for a particular purpose.</p>

          <h2>4. Limitation of Liability</h2>
          <p>In no event shall Shulkr or its suppliers be liable for any damages (including, without limitation, lost profits, lost data, or business interruption) arising out of the use or inability to use the software, even if we have been advised of the possibility of such damages.</p>

          <h2>5. Governing Law</h2>
          <p>These terms and conditions are governed by and construed in accordance with the laws of the jurisdiction in which Shulkr operates, and you irrevocably submit to the exclusive jurisdiction of the courts in that location.</p>

          <h2>6. Changes to Terms</h2>
          <p>Shulkr reserves the right to modify these terms at any time. Your continued use of the software after such modifications constitutes your acceptance of the updated terms.</p>

          <p class="legal-date">Last updated: July 2026</p>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a data-view="features">Features</a>
            <a data-view="faq">FAQ</a>
            <a data-landing>Home</a>
            <a data-view="terms">Terms</a>
            <a data-view="cookies">Privacy</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function cookiePolicyPage() {
  return `
    <div class="landing">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <nav class="landing-navbar">
        <div class="nav-container">
          <a href="#" class="logo" data-landing>
            <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
            <span>SHULKR</span>
          </a>
          <ul class="landing-nav-links">
            <li><a href="#features">FEATURES</a></li>
            <li><a href="#scripts">SCRIPTS</a></li>
            <li><a href="#showcase">DASHBOARD</a></li>
            <li><a href="#community">COMMUNITY</a></li>
            <li><a href="#download">DOWNLOAD</a></li>
            <li><a data-view="faq">FAQ</a></li>
            <li><a data-view="features">SHOWCASE</a></li>
          </ul>
          <div class="landing-nav-actions">
            <button class="btn btn-secondary" data-auth="signin">SIGN IN</button>
            <button class="btn btn-primary" data-auth="signup">GET STARTED</button>
          </div>
        </div>
      </nav>

      <section class="hero" style="padding: 100px 0 40px;">
        <div class="hero-content">
          <div class="hero-left">
            <h1 class="hero-title">
              <span class="title-line">PRIVACY &</span>
              <span class="title-line title-accent">COOKIE POLICY</span>
            </h1>
            <p class="hero-subtitle"><span class="slash">/</span> Understanding how we protect your data and use cookies.</p>
          </div>
        </div>
      </section>

      <section class="landing-section legal-section" style="padding: 60px 0;">
        <div class="container legal-content">
          <h2>1. Privacy Policy</h2>
          <p>At Shulkr, we are committed to protecting your privacy. This policy outlines how we collect, use, and protect your personal information.</p>

          <h2>2. Information We Collect</h2>
          <p>We may collect the following information:</p>
          <ul>
            <li>Account information (email, username, display name)</li>
            <li>Minecraft account credentials (encrypted and stored securely)</li>
            <li>Script and automation data</li>
            <li>Usage analytics and telemetry (anonymized)</li>
            <li>IP address and browser information</li>
          </ul>

          <h2>3. How We Use Your Information</h2>
          <p>Your information is used to:</p>
          <ul>
            <li>Provide and improve Shulkr services</li>
            <li>Authenticate your account and secure your data</li>
            <li>Send important updates and security notices</li>
            <li>Analyze usage patterns to improve performance</li>
            <li>Comply with legal obligations</li>
          </ul>

          <h2>4. Cookie Usage</h2>
          <p>Shulkr uses cookies to:</p>
          <ul>
            <li><strong>Session Management:</strong> Keep you logged in and maintain your preferences</li>
            <li><strong>Analytics:</strong> Understand how users interact with our platform (anonymized)</li>
            <li><strong>Security:</strong> Detect and prevent fraudulent activity</li>
            <li><strong>Personalization:</strong> Remember your settings and preferences</li>
          </ul>

          <h2>5. Data Security</h2>
          <p>We implement industry-standard security measures including encryption, secure sockets layer (SSL), and regular security audits. Your Minecraft account credentials are encrypted and never transmitted unencrypted.</p>

          <h2>6. Data Sharing</h2>
          <p>We do not sell your personal data to third parties. We may share information with:</p>
          <ul>
            <li>Service providers who assist in platform operations</li>
            <li>Law enforcement when required by law</li>
            <li>Other users when sharing scripts or community content (only information you choose to share)</li>
          </ul>

          <h2>7. Your Rights</h2>
          <p>You have the right to:</p>
          <ul>
            <li>Access your personal data</li>
            <li>Request correction of inaccurate data</li>
            <li>Request deletion of your account and data</li>
            <li>Opt-out of analytics and promotional communications</li>
          </ul>

          <h2>8. Contact Us</h2>
          <p>If you have questions about our privacy practices or cookies, please contact us through our support channels or Discord community.</p>

          <p class="legal-date">Last updated: July 2026</p>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="container footer-content">
          <div class="footer-brand">
            <a href="#" class="logo" data-landing>
              <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
              <span>SHULKR</span>
            </a>
            <p>A Minecraft scripting platform and web dashboard.</p>
          </div>
          <div class="footer-links">
            <a data-view="features">Features</a>
            <a data-view="faq">FAQ</a>
            <a data-landing>Home</a>
            <a data-view="terms">Terms</a>
            <a data-view="cookies">Privacy</a>
          </div>
          <p class="footer-copy">© 2026 Shulkr. All rights reserved.</p>
        </div>
      </footer>
    </div>
  `;
}

function page() {
  if (state.loading) return loadingState();
  return {
    profile: profilePage,
    scripts: scriptsPage,
    library: libraryPage,
    editor: editorPage,
    flow: flowBuilderPage,
    statistics: statisticsPage,
    accounts: accountsPage,
    billing: billingPage,
    customerPortal: customerPortalPage,
    remote: remotePage,
    settings: settingsPage,
    admin: adminPage
  }[state.page]?.() || profilePage();
}

function topBar(title, icon) {
  const user = state.auth.user || state.profile || { displayName: "EnderUser" };
  const unreadNotifications = notificationItems().filter(item => !state.notificationRead[item.id]).length;
  return `
    <header class="top-bar">
      <div class="top-bar-left">
        <div class="page-title">
          <i class="${icon}"></i>
          <div class="page-title-copy">
            <span class="page-eyebrow">Dashboard workspace</span>
            <h1>${escapeHtml(title)}</h1>
          </div>
        </div>
      </div>
      <div class="global-search-shell">
        <label class="global-search" data-testid="top-search">
          <i class="fa-solid fa-magnifying-glass"></i>
          <input id="search" value="${escapeAttr(state.search)}" placeholder="Search pages, scripts, devices..." autocomplete="off" aria-label="Search dashboard" />
          <kbd>Ctrl</kbd><kbd>K</kbd>
        </label>
        <div class="global-search-results" id="global-search-results" hidden></div>
      </div>
      <div class="top-actions">
        <button class="btn-icon" title="Refresh" aria-label="Refresh dashboard data" data-action="refresh"><i class="fa-solid fa-rotate"></i></button>
        <div class="notification-shell">
          <button class="btn-icon" title="Status center" aria-label="Open status center${unreadNotifications ? `, ${unreadNotifications} unread` : ""}" aria-expanded="${state.notificationsOpen}" data-action="notifications"><i class="fa-solid fa-bell"></i>${unreadNotifications ? `<span class="notification-count">${unreadNotifications}</span>` : ""}</button>
          ${state.notificationsOpen ? notificationPanel() : ""}
        </div>
        <details class="account-menu">
          <summary class="user-chip" title="Open account menu">
            <span class="user-chip-avatar">${escapeHtml((user.displayName || "U").slice(0, 1))}</span>
            <span class="user-chip-name">${escapeHtml(user.displayName || "EnderUser")}</span>
            <i class="fa-solid fa-chevron-down account-menu-chevron"></i>
          </summary>
          <div class="account-menu-popover">
            <div class="account-menu-heading">
              <strong>${escapeHtml(user.displayName || "EnderUser")}</strong>
              <span>${escapeHtml(user.tier || "Premium")} account</span>
            </div>
            <button data-page="admin"><i class="fa-solid fa-shield-halved"></i><span>Admin</span></button>
            <button data-page="accounts"><i class="fa-solid fa-microchip"></i><span>Devices</span></button>
            <button data-page="billing"><i class="fa-solid fa-credit-card"></i><span>Billing</span></button>
            <button data-page="settings"><i class="fa-solid fa-gear"></i><span>Settings</span></button>
            <div class="account-menu-divider"></div>
            <button data-landing><i class="fa-solid fa-globe"></i><span>Website</span></button>
            <button class="account-menu-danger" data-action="logout"><i class="fa-solid fa-right-from-bracket"></i><span>Sign out</span></button>
          </div>
        </details>
        <span class="badge ${state.online ? "badge-good" : "badge-active"}" style="height: 32px;">${state.online ? "Backend online" : "Backend offline"}</span>
      </div>
    </header>
  `;
}

function notificationPanel() {
  const items = notificationItems();
  return `<div class="notification-popover" role="status" aria-label="System status"><div class="notification-popover-head"><span><strong>Status center</strong><small>Live dashboard state</small></span><button type="button" data-action="notifications-read">Mark all read</button></div>${items.map(item => `<div class="notification-item ${state.notificationRead[item.id] ? "" : "unread"}"><i class="fa-solid ${item.icon}"></i><span><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.detail)}</small></span></div>`).join("")}</div>`;
}

function notificationItems() {
  const connected = state.clients.filter(client => client.connected !== false).length;
  const flowDirty = flowStore.snapshot().dirty;
  return [
    { id: `backend-${state.online ? "online" : "offline"}`, title: state.online ? "Backend connected" : "Backend unavailable", detail: state.online ? "API requests are responding normally." : "Check the backend connection in Settings.", icon: state.online ? "fa-circle-check" : "fa-triangle-exclamation" },
    { id: `clients-${connected}`, title: connected ? `${connected} Minecraft client${connected === 1 ? "" : "s"} connected` : "No Minecraft client connected", detail: connected ? "Remote execution controls are available." : "Open Minecraft and connect the Shulkr client.", icon: connected ? "fa-link" : "fa-link-slash" },
    { id: `flow-${flowDirty ? "dirty" : "saved"}`, title: flowDirty ? "Flow Builder has unsaved changes" : "Flow Builder changes are saved", detail: flowDirty ? "Open Flow Builder to save or review the draft." : "Your latest visual automation is persisted.", icon: flowDirty ? "fa-floppy-disk" : "fa-cloud-arrow-up" }
  ];
}

function updateGlobalSearchResults(value) {
  const container = document.getElementById("global-search-results");
  if (!container) return;
  const query = String(value || "").trim().toLowerCase();
  state.search = value;
  if (!query) {
    container.hidden = true;
    container.innerHTML = "";
    return;
  }
  const pageResults = nav
    .filter(([, label, , description]) => `${label} ${description}`.toLowerCase().includes(query))
    .map(([page, label, icon, description]) => ({ kind: "page", id: page, label, detail: description, icon }));
  const scriptResults = state.scripts
    .filter(script => `${script.name || ""} ${script.fileName || ""} ${script.path || ""}`.toLowerCase().includes(query))
    .map(script => ({ kind: "script", id: script.path, label: script.name || script.fileName || script.path, detail: "Open in Script Editor", icon: "fa-solid fa-file-code" }));
  const clientResults = state.clients
    .filter(client => `${client.displayName || ""} ${client.deviceName || ""} ${client.id || ""}`.toLowerCase().includes(query))
    .map(client => ({ kind: "client", id: client.id, label: client.deviceName || client.displayName || "Minecraft client", detail: client.connected !== false ? "Connected device" : "Offline device", icon: "fa-solid fa-microchip" }));
  const results = [...pageResults, ...scriptResults, ...clientResults].slice(0, 7);
  container.hidden = false;
  container.innerHTML = results.length
    ? results.map(result => `<button type="button" data-global-result="${result.kind}" data-global-id="${escapeAttr(result.id)}"><i class="${result.icon}"></i><span><strong>${escapeHtml(result.label)}</strong><small>${escapeHtml(result.detail)}</small></span><i class="fa-solid fa-arrow-right"></i></button>`).join("")
    : `<div class="global-search-empty">No pages, scripts, or devices match “${escapeHtml(value)}”.</div>`;
  container.querySelectorAll("[data-global-result]").forEach(button => button.addEventListener("click", () => {
    const kind = button.dataset.globalResult;
    const id = button.dataset.globalId;
    state.search = "";
    if (kind === "page") return navigateToPage(id);
    if (kind === "script") return openScriptEditor(id);
    if (kind === "client") {
      state.selectedClient = state.clients.find(client => client.id === id) || state.selectedClient;
      navigateToPage("remote");
    }
  }));
}

function changelogPage() {
  return `
    <div class="auth-page">
      <div class="auth-card" style="max-width: 980px;">
        <a href="#" class="logo" data-landing>
          <img src="/shulkr-logo.png" alt="Shulkr" data-hide-on-error>
          <span>Shulkr</span>
        </a>
        <h2 style="margin-top: 20px;">Changelog</h2>
        <p class="auth-subtitle">What we shipped recently across the dashboard, client, and billing flow.</p>
        <div class="list" style="margin-top: 24px;">
          ${[
            ["July 2026", "Remote dashboard layout tightened up, the customer portal landed, and billing navigation was cleaned up."],
            ["June 2026", "Main menu branding, script library cleanup, and admin billing tools shipped."],
            ["May 2026", "Stripe subscriptions, test-mode checkout, and tier unlocks were added."]
          ].map(([date, text]) => `
            <div class="list-item">
              <div class="icon"><i class="fa-solid fa-clock-rotate-left"></i></div>
              <div class="content">
                <h4>${escapeHtml(date)}</h4>
                <p>${escapeHtml(text)}</p>
              </div>
            </div>
          `).join("")}
        </div>
        <div class="modal-actions" style="margin-top: 24px;">
          <button class="btn btn-secondary" data-landing>Back to website</button>
          <button class="btn btn-primary" data-auth="signup">Get Started</button>
        </div>
      </div>
    </div>
  `;
}

function profilePage() {
  const profile = state.profile || { displayName: "EnderUser", tier: "Premium" };
  const pulseHistory = (state.statsHistory || []).slice(-12);
  const pulseValues = pulseHistory.length ? pulseHistory.map((point) => point.commandsCompleted || point.scriptRuns || point.heartbeats || 0) : [];
  const pulseLabels = pulseHistory.map((point) => chartPointLabel(point.at));
  const totalScripts = state.stats?.scripts ?? state.scripts.length;
  const totalLibraries = state.stats?.installedLibraries ?? state.libraries.length;
  const totalTemplates = state.stats?.templates ?? state.templates.length;
  const connectedClients = state.clients.filter((client) => client.connected !== false).length;
  return `
    ${topBar("Overview", "fa-solid fa-house")}
    <section class="overview-hero">
      <div class="card overview-hero-main">
        <div class="overview-copy">
          <span class="overview-kicker">Welcome back</span>
          <h2>${escapeHtml(profile.displayName || "EnderUser")}</h2>
          <p>Your scripts, libraries, and remote tools are all in one place. Pick up where you left off without digging through the whole client.</p>
          <div class="overview-chip-row">
            <span class="badge badge-premium">${escapeHtml(profile.tier || "Premium")}</span>
            <span class="soft-pill"><i class="fa-solid fa-microchip"></i>${connectedClients}/${state.clients.length || 1} devices active</span>
            <span class="soft-pill"><i class="fa-solid fa-file-code"></i>${totalScripts} scripts ready</span>
          </div>
        </div>
        <div class="overview-hero-actions">
          <button class="btn btn-primary" data-page="scripts"><i class="fa-solid fa-rocket"></i> Open scripts</button>
          <button class="btn btn-secondary" data-page="remote"><i class="fa-solid fa-satellite-dish"></i> Remote console</button>
        </div>
      </div>
      <div class="card overview-focus-card">
        <div class="card-header">
          <h3><i class="fa-solid fa-bullseye"></i> Session Focus</h3>
        </div>
        <div class="focus-stack">
          ${focusItem("Selected script", state.selectedScript?.name || state.scripts[0]?.name || "Choose a script")}
          ${focusItem("Libraries installed", `${totalLibraries} ready to import`)}
          ${focusItem("Templates saved", `${totalTemplates} layouts available`)}
          ${focusItem("Status", connectedClients ? `${connectedClients} client${connectedClients === 1 ? "" : "s"} connected and ready` : state.online ? "Backend online · no client connected" : "Backend offline")}
        </div>
      </div>
    </section>

    <section class="overview-metrics">
      ${overviewMetric("Scripts", totalScripts, "fa-solid fa-file-code", "Ready to run")}
      ${overviewMetric("Libraries", totalLibraries, "fa-solid fa-cubes", "Reusable helpers")}
      ${overviewMetric("Templates", totalTemplates, "fa-solid fa-layer-group", "Saved workflows")}
      ${overviewMetric("Published", state.stats?.publishedScripts ?? state.library.length, "fa-solid fa-cloud-arrow-up", "Shared builds")}
    </section>

    <div class="grid-2 overview-grid">
      <div class="card chart-card">
        <div class="card-header">
          <h3><i class="fa-solid fa-wave-square"></i> Activity Pulse</h3>
          <button class="card-link" data-page="statistics">Details <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="chart-area">
          ${pulseValues.length ? areaChart(pulseValues, "var(--accent-purple)", pulseLabels, (value) => `${formatNumber(value || 0)} events`) : empty("Activity will appear once the client starts sending telemetry.")}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-sparkles"></i> Quick Start</h3>
          <button class="card-link" data-page="scripts">Library <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="quick-start-stack">
          ${quickStartCard("Run a script", "Pick a script and launch it on your connected client.", "fa-solid fa-play", "remote")}
          ${quickStartCard("Open editor", "Jump into Minescript and keep building from the dashboard.", "fa-solid fa-code", "editor")}
          ${quickStartCard("Manage billing", "Review your plan, current subscription, and customer tools.", "fa-solid fa-credit-card", stripeEnabled() ? "customerPortal" : "billing")}
        </div>
      </div>
    </div>

    <div class="grid-2 overview-grid overview-grid-secondary">
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-clock-rotate-left"></i> Recent Activity</h3>
          <button class="card-link" data-page="statistics">All <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="list">
          ${state.scripts.slice(0, 4).map(activityItem).join("") || empty("No recent activity.")}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-microchip"></i> Licensed Device</h3>
          <button class="card-link" data-page="accounts">Manage <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="list">
          ${state.clients.length ? state.clients.slice(0, 4).map(clientListItem).join("") : clientListItem({ displayName: "EnderUser", tier: "Premium", connected: state.online, source: "profile" })}
        </div>
      </div>
    </div>
  `;
}

function scriptsPage() {
  const term = state.scriptSearch.trim().toLowerCase();
  const folder = state.scriptFolder;
  const visibleScripts = state.scripts.filter(script => {
    const matchesTerm = !term || `${script.name || ""} ${script.path || ""} ${script.description || ""}`.toLowerCase().includes(term);
    const matchesFolder = folder === "all" || String(script.path || "").startsWith(`${folder}/`);
    return matchesTerm && matchesFolder;
  });
  return `
    ${topBar("Scripts", "fa-solid fa-file-code")}
    <section class="card">
      <div class="card-header">
        <h3><i class="fa-solid fa-folder-open"></i> Local scripts</h3>
        <div class="script-management-actions">
          <button class="btn btn-secondary" data-action="new-folder"><i class="fa-solid fa-folder-plus"></i> New folder</button>
          <button class="btn btn-primary" data-page="editor"><i class="fa-solid fa-code"></i> Open editor</button>
        </div>
      </div>
      <div class="script-management-toolbar">
        <label><i class="fa-solid fa-magnifying-glass"></i><input id="script-search" value="${escapeAttr(state.scriptSearch)}" placeholder="Search local scripts…" aria-label="Search local scripts"></label>
        <select id="script-folder-filter" aria-label="Filter by folder"><option value="all">All folders</option>${state.scriptFolders.map(item => `<option value="${escapeAttr(item.path)}" ${state.scriptFolder === item.path ? "selected" : ""}>${escapeHtml(item.path)}</option>`).join("")}</select>
        ${folder !== "all" ? `<button class="btn btn-secondary btn-sm" data-folder-rename="${escapeAttr(folder)}"><i class="fa-solid fa-pen"></i> Rename folder</button><button class="btn btn-secondary btn-sm" data-folder-delete="${escapeAttr(folder)}"><i class="fa-solid fa-trash"></i> Delete folder</button>` : ""}
      </div>
      <div class="list script-management-list">
        ${visibleScripts.length ? visibleScripts.map(scriptManagementItem).join("") : empty(state.scripts.length ? "No scripts match these filters." : "No local scripts yet. Create one in the editor.")}
      </div>
    </section>
  `;
}

function scriptManagementItem(script) {
  const name = script.name || script.fileName || script.path || "Untitled";
  return `<div class="list-item script-management-item"><button class="script-management-open" data-script-open="${escapeAttr(script.path)}" aria-label="Open ${escapeAttr(name)} in editor"><span class="icon"><i class="fa-solid fa-file-code"></i></span><span class="content"><strong>${escapeHtml(name)}</strong><small>${escapeHtml(script.description || script.path || "Local script")}</small></span></button><span class="meta">${formatBytes(script.sizeBytes || 0)} · ${script.modifiedAt ? timeAgo(script.modifiedAt) : "Unknown"}</span><div class="script-management-row-actions"><button class="btn-icon" data-script-rename="${escapeAttr(script.path)}" aria-label="Rename ${escapeAttr(name)}" title="Rename"><i class="fa-solid fa-pen"></i></button><button class="btn-icon" data-script-delete="${escapeAttr(script.path)}" aria-label="Delete ${escapeAttr(name)}" title="Delete"><i class="fa-solid fa-trash"></i></button></div></div>`;
}

function libraryPage() {
  if (state.loading && state.library.length === 0) {
    return scriptHubSkeleton();
  }
  const all = getHubScripts();
  const term = state.hubSearch.toLowerCase();
  const category = state.hubCategory.toLowerCase();
  let filtered = all.filter((s) => {
    const matchesTerm = !term || (s.name || "").toLowerCase().includes(term) || (s.about || "").toLowerCase().includes(term) || (s.author || "").toLowerCase().includes(term);
    const cat = (s.category || "Other").toLowerCase();
    const matchesCategory = category === "all" || cat === category || (category === "recent" && true);
    const matchesFilter = state.hubFilters[cat] !== false;
    return matchesTerm && matchesCategory && matchesFilter;
  });
  if (state.hubSort === "popular") filtered.sort((a, b) => (b.downloads || 0) - (a.downloads || 0));
  else if (state.hubSort === "stars") filtered.sort((a, b) => (b.stars || 0) - (a.stars || 0));
  else if (state.hubSort === "name") filtered.sort((a, b) => (a.name || "").localeCompare(b.name || ""));
  else filtered.sort((a, b) => (b.publishedAt || 0) - (a.publishedAt || 0));

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / state.hubPerPage));
  const page = Math.min(state.hubPage, totalPages);
  const start = (page - 1) * state.hubPerPage;
  const pageItems = filtered.slice(start, start + state.hubPerPage);

  const categories = ["all", "recent", "visual automation", "python", "pyjinn", "farming", "combat", "world", "utility", "other"];

  return `
    ${topBar("Script Hub", "fa-solid fa-file-code")}
    <section class="hub-layout">
      <div class="hub-main">
        <div class="hub-header">
          <div>
            <h2>Script Library</h2>
            <p class="hub-subtitle">Publish, discover, and install Shulkr scripts from the community.</p>
          </div>
          <div class="hub-actions">
            <span class="hub-count">${total} published</span>
            <button class="hub-action-btn hub-action-btn-primary" data-action="publish-script" title="Publish script"><i class="fa-solid fa-cloud-arrow-up"></i></button>
            <button class="hub-action-btn hub-action-btn-secondary" data-action="refresh" title="Refresh"><i class="fa-solid fa-rotate"></i></button>
          </div>
        </div>
        <div class="hub-searchbar">
          <i class="fa-solid fa-magnifying-glass"></i>
          <input id="hub-search" value="${escapeAttr(state.hubSearch)}" placeholder="Search scripts, e.g. AutoCrystal..." />
          <kbd>Ctrl</kbd><kbd>K</kbd>
        </div>
        <div class="hub-categories">
          ${categories.map((c) => `
            <button class="hub-category ${state.hubCategory === c ? "active" : ""}" data-hub-category="${c}">
              ${c === "all" ? "All" : c === "recent" ? "Recent" : c.charAt(0).toUpperCase() + c.slice(1)}
            </button>
          `).join("")}
        </div>
        <div class="hub-grid">
          ${pageItems.length ? pageItems.map(hubScriptCard).join("") : hubEmptyState()}
        </div>
        ${totalPages > 1 ? hubPagination(page, totalPages, total) : ""}
      </div>
      <aside class="hub-filters card">
        <div class="filter-section">
          <div class="filter-title">Filters</div>
          <button class="btn-link" data-hub-reset>Reset</button>
        </div>
        <div class="filter-section">
          <div class="filter-search">
            <i class="fa-solid fa-magnifying-glass"></i>
            <input id="filter-search" value="${escapeAttr(state.hubSearch)}" placeholder="Search filters..." />
          </div>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Categories</div>
          <div class="filter-checks">
            ${Object.entries(state.hubFilters).map(([key, checked]) => `
              <label class="filter-check">
                <input type="checkbox" data-hub-filter="${key}" ${checked ? "checked" : ""} />
                <span>${key.charAt(0).toUpperCase() + key.slice(1)}</span>
              </label>
            `).join("")}
          </div>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Sort by</div>
          <select id="hub-sort" data-hub-sort>
            <option value="recent" ${state.hubSort === "recent" ? "selected" : ""}>Recently modified</option>
            <option value="popular" ${state.hubSort === "popular" ? "selected" : ""}>Most installs</option>
            <option value="stars" ${state.hubSort === "stars" ? "selected" : ""}>Most stars</option>
            <option value="name" ${state.hubSort === "name" ? "selected" : ""}>Name</option>
          </select>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Time</div>
          <select id="hub-time">
            <option>All Time</option>
            <option>Today</option>
            <option>This Week</option>
            <option>This Month</option>
            <option>This Year</option>
          </select>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Other</div>
          <label class="filter-toggle">
            <input type="checkbox" checked />
            <span class="toggle"></span>
            <span>Published scripts</span>
          </label>
          <label class="filter-toggle">
            <input type="checkbox" checked />
            <span class="toggle"></span>
            <span>Installable</span>
          </label>
          <label class="filter-toggle">
            <input type="checkbox" checked />
            <span class="toggle"></span>
            <span>Show about text</span>
          </label>
        </div>
      </aside>
    </section>
  `;
}

function getHubScripts() {
  if (state.library.length) return state.library;
  return state.scripts.map((s) => ({
    id: s.path,
    name: s.name || s.fileName || "Untitled",
    author: state.profile?.displayName || "EnderUser",
    about: s.description || "A Shulkr community script.",
    category: scriptCategoryFromName(s.name || s.fileName || ""),
    tags: [s.extension?.toUpperCase() || "PY", scriptCategoryFromName(s.name || s.fileName || "")],
    version: "1.0.0",
    icon: "",
    fileName: s.name || s.fileName || "",
    downloads: 0,
    stars: 0,
    publishedAt: s.modifiedAt || Date.now(),
    updatedAt: s.modifiedAt || Date.now()
  }));
}

function scriptCategoryFromName(name) {
  const lower = String(name).toLowerCase();
  if (lower.endsWith(".pyj") || lower.includes("pyjinn")) return "Pyjinn";
  if (lower.includes("farm") || lower.includes("crop") || lower.includes("mine")) return "Farming";
  if (lower.includes("combat") || lower.includes("kill") || lower.includes("crystal") || lower.includes("aura")) return "Combat";
  if (lower.includes("build") || lower.includes("chunk") || lower.includes("world")) return "World";
  if (lower.includes("speed") || lower.includes("nofall") || lower.includes("bright") || lower.includes("haste") || lower.includes("jump") || lower.includes("fire") || lower.includes("water") || lower.includes("saturation") || lower.includes("cleanup") || lower.includes("chat") || lower.includes("sort") || lower.includes("inventory")) return "Utility";
  return "Other";
}

function hubScriptCard(script) {
  const name = escapeHtml(script.name || "Untitled");
  const author = escapeHtml(script.author || "Shulkr user");
  const about = escapeHtml(script.about || "A Shulkr community script.");
  const category = escapeHtml(script.category || "Other");
  const tags = (script.tags || [category]).slice(0, 2);
  const ext = (script.fileName || script.name || "").split(".").pop()?.toLowerCase() || "py";
  const isAutomation = script.kind === "automation";
  const trustLabel = script.verification?.serverCompiled ? "Server verified" : "Review code";
  const trustIcon = script.verification?.serverCompiled ? "fa-shield-check" : "fa-code";
  const iconClass = isAutomation ? "fa-solid fa-diagram-project" : ext === "py" ? "fa-brands fa-python" : ext === "lua" ? "fa-solid fa-moon" : ext === "js" ? "fa-brands fa-js" : "fa-solid fa-code";
  const selected = state.selectedScript?.id === script.id ? "selected" : "";
  return `
    <div class="hub-card ${selected}" data-hub-script="${escapeAttr(script.id)}">
      <div class="hub-card-thumb">
        <div class="hub-thumb-icon"><i class="${iconClass}"></i></div>
        <span class="hub-card-badge"><i class="fa-solid ${trustIcon}"></i> ${escapeHtml(script.badge || trustLabel)}</span>
      </div>
      <div class="hub-card-body">
        <div class="hub-card-title">
          <h4>${name}</h4>
          <span class="hub-card-tag">${isAutomation ? "Visual Automation" : category}</span>
        </div>
        <div class="hub-card-meta">
          <span>by ${author}</span>
          <span>•</span>
          <span>v${escapeHtml(script.version || "1.0.0")}</span>
          <span>•</span>
          <span>${isAutomation ? `${script.nodeCount || 0} nodes · ${script.edgeCount || 0} edges` : `<i class="fa-solid fa-download"></i> ${formatNumber(script.downloads || script.installs || 0)}`}</span>
          <span>•</span>
          <span>Updated ${escapeHtml(timeAgo(script.updatedAt || script.publishedAt))}</span>
        </div>
        <p class="hub-card-desc">${about}</p>
        <div class="hub-card-tags">
          ${tags.map((t) => `<span class="hub-tag">${escapeHtml(t)}</span>`).join("")}
        </div>
      </div>
      <div class="hub-card-actions">
        <button class="btn btn-primary btn-sm" data-hub-install="${escapeAttr(script.id)}" title="Install" aria-label="Install ${escapeAttr(script.name || "script")}"><i class="fa-solid fa-download"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-view="${escapeAttr(script.id)}" title="${isAutomation ? "Review automation" : "View code"}" aria-label="${isAutomation ? "Review automation" : "View code"} ${escapeAttr(script.name || "script")}"><i class="fa-solid fa-${isAutomation ? "diagram-project" : "code"}"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-more="${escapeAttr(script.id)}" title="More" aria-label="More options for ${escapeAttr(script.name || "script")}"><i class="fa-solid fa-ellipsis"></i></button>
      </div>
    </div>
  `;
}

function hubEmptyState() {
  return `
    <div class="hub-empty">
      <i class="fa-solid fa-box-open"></i>
      <h3>No scripts found</h3>
      <p>Try a different search or publish your first script.</p>
      <button class="btn btn-primary" data-action="publish-script"><i class="fa-solid fa-cloud-arrow-up"></i> Publish Script</button>
    </div>
  `;
}

function hubPagination(page, totalPages, total) {
  const pages = [];
  for (let i = 1; i <= totalPages; i++) {
    if (i === 1 || i === totalPages || (i >= page - 1 && i <= page + 1)) {
      pages.push(i);
    } else if (pages[pages.length - 1] !== "...") {
      pages.push("...");
    }
  }
  return `
    <div class="hub-pagination">
      <button class="btn btn-secondary btn-sm" data-hub-page="${page - 1}" ${page <= 1 ? "disabled" : ""}>Previous</button>
      <div class="hub-page-numbers">
        ${pages.map((p) => p === "..." ? `<span class="hub-ellipsis">...</span>` : `
          <button class="hub-page ${p === page ? "active" : ""}" data-hub-page="${p}">${p}</button>
        `).join("")}
      </div>
      <button class="btn btn-secondary btn-sm" data-hub-page="${page + 1}" ${page >= totalPages ? "disabled" : ""}>Next</button>
    </div>
  `;
}

function scriptHubSkeleton() {
  return `
    ${topBar("Script Hub", "fa-solid fa-file-code")}
    <section class="hub-layout">
      <div class="hub-main">
        <div class="hub-header">
          <div>
            <div class="skeleton skeleton-text" style="width: 180px; height: 28px;"></div>
            <div class="skeleton skeleton-text-sm" style="width: 260px; margin-top: 8px;"></div>
          </div>
          <div class="skeleton skeleton-btn"></div>
        </div>
        <div class="skeleton" style="height: 48px; border-radius: 999px; margin-bottom: 18px;"></div>
        <div class="hub-grid">
          ${Array(6).fill(0).map(hubCardSkeleton).join("")}
        </div>
      </div>
      <aside class="hub-filters card">
        ${Array(5).fill(0).map(() => `<div class="skeleton skeleton-text" style="width: 70%; margin-bottom: 16px;"></div>`).join("")}
      </aside>
    </section>
  `;
}

function hubCardSkeleton() {
  return `
    <div class="hub-card skeleton-card">
      <div class="skeleton hub-thumb-skeleton"></div>
      <div class="hub-card-body">
        <div class="skeleton skeleton-text" style="width: 65%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 40%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 85%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 60%;"></div>
      </div>
    </div>
  `;
}

function editorPage() {
  if (state.loading && state.scripts.length === 0) {
    return editorSkeletonPage();
  }
  const filtered = state.scripts.filter((s) => {
    const term = state.scriptSearch.toLowerCase();
    return !term || (s.name || s.fileName || s.path || "").toLowerCase().includes(term);
  });
  return `
    ${topBar("Script Editor", "fa-solid fa-code")}
    <section class="scripts-layout">
      <div class="scripts-sidebar card">
        <div class="card-header">
          <h3><i class="fa-solid fa-folder-open"></i> Scripts</h3>
          <button class="btn btn-primary btn-sm" data-action="new-script"><i class="fa-solid fa-plus"></i> New</button>
        </div>
        <label class="editor-script-search"><i class="fa-solid fa-magnifying-glass"></i><input id="script-search" value="${escapeAttr(state.scriptSearch)}" placeholder="Search scripts…" aria-label="Search scripts"></label>
        <div class="scripts-list">
          ${filtered.map(scriptListItem).join("") || empty("No scripts found.")}
        </div>
      </div>
      <div class="scripts-editor card">
        ${state.editorScript ? editorPanel() : editorEmpty()}
      </div>
    </section>
  `;
}

function editorSkeletonPage() {
  return `
    ${topBar("Script Editor", "fa-solid fa-code")}
    <section class="scripts-layout">
      <div class="scripts-sidebar card">
        <div class="card-header">
          <h3><i class="fa-solid fa-folder-open"></i> Scripts</h3>
          <div class="skeleton skeleton-btn-sm"></div>
        </div>
        <div class="scripts-list">
          ${Array(6).fill(0).map(scriptCardSkeleton).join("")}
        </div>
      </div>
      <div class="scripts-editor card">
        ${editorSkeleton("Loading...")}
      </div>
    </section>
  `;
}

function scriptCardSkeleton() {
  return `
    <div class="script-item skeleton-card">
      <div class="skeleton skeleton-icon"></div>
      <div class="script-info" style="flex: 1;">
        <div class="skeleton skeleton-text" style="width: 55%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 35%;"></div>
      </div>
    </div>
  `;
}

function scriptListItem(script) {
  const name = escapeHtml(script.name || script.fileName || script.path || "Untitled");
  const ext = (name.split(".").pop() || "").toLowerCase();
  const icon = ext === "py" ? "fa-brands fa-python" : ext === "lua" ? "fa-solid fa-moon" : "fa-solid fa-file-code";
  const active = state.editorScript?.path === script.path ? "active" : "";
  return `
    <div class="script-item ${active}" data-select-script="${escapeAttr(script.path)}" data-select-name="${escapeAttr(name)}" role="button" tabindex="0" aria-label="Open ${escapeAttr(name)}">
      <div class="script-icon"><i class="${icon}"></i></div>
      <div class="script-info">
        <h4>${name}</h4>
        <p>${formatBytes(script.sizeBytes || script.size || 0)} · ${script.modifiedAt ? timeAgo(script.modifiedAt) : "Unknown"}</p>
      </div>
    </div>
  `;
}

function editorEmpty() {
  return `
    <div class="editor-empty">
      <i class="fa-solid fa-code"></i>
      <h3>Select a script to edit</h3>
      <p>Or create a new Minescript/Python file to get started.</p>
      <button class="btn btn-primary" data-action="new-script"><i class="fa-solid fa-plus"></i> New Script</button>
    </div>
  `;
}

function editorPanel() {
  const name = escapeHtml(state.editorScript?.fileName || state.editorScript?.name || "Untitled");
  if (state.editorContent === "" && state.editorScript && !state.editorDirty) {
    return editorSkeleton(name);
  }
  return `
    <div class="editor-header">
      <div class="editor-title">
        <i class="fa-solid fa-file-code"></i>
        <span>${name}</span>
        ${state.editorDirty ? '<span class="editor-dirty">●</span>' : ""}
      </div>
      <div class="editor-actions">
        <button class="btn btn-secondary btn-sm" data-action="run-script"><i class="fa-solid fa-play"></i> Run</button>
        <button class="btn btn-primary btn-sm" data-action="save-script" ${!state.editorDirty ? "disabled" : ""}><i class="fa-solid fa-floppy-disk"></i> Save</button>
      </div>
    </div>
    <div class="editor-container" id="monaco-editor"></div>
  `;
}

function editorSkeleton(name) {
  return `
    <div class="editor-header">
      <div class="editor-title">
        <i class="fa-solid fa-file-code"></i>
        <span>${name}</span>
      </div>
      <div class="editor-actions">
        <div class="skeleton skeleton-btn"></div>
        <div class="skeleton skeleton-btn"></div>
      </div>
    </div>
    <div class="editor-skeleton">
      <div class="skeleton skeleton-line" style="width: 85%;"></div>
      <div class="skeleton skeleton-line" style="width: 60%;"></div>
      <div class="skeleton skeleton-line" style="width: 90%;"></div>
      <div class="skeleton skeleton-line" style="width: 40%;"></div>
      <div class="skeleton skeleton-line" style="width: 75%;"></div>
      <div class="skeleton skeleton-line" style="width: 55%;"></div>
      <div class="skeleton skeleton-line" style="width: 80%;"></div>
      <div class="skeleton skeleton-line" style="width: 65%;"></div>
      <div class="skeleton skeleton-line" style="width: 95%;"></div>
      <div class="skeleton skeleton-line" style="width: 50%;"></div>
      <div class="skeleton skeleton-line" style="width: 70%;"></div>
      <div class="skeleton skeleton-line" style="width: 45%;"></div>
    </div>
  `;
}

function statisticsPage() {
  const summary = state.statsSummary || {};
  const history = state.statsHistory || [];
  const metric = state.metricTab;
  const metricKey = metric === "time"
    ? "runtimeSeconds"
    : metric === "fps"
      ? "avgFps"
      : metric === "commands"
        ? "commandsCompleted"
        : metric === "chat"
          ? "chatMessages"
          : "scriptRuns";
  const chartValues = history.length ? history.map((p) => p[metricKey] || 0) : [0];
  const chartColor = metric === "fps" ? "var(--accent-cyan)" : metric === "commands" ? "var(--accent-orange)" : metric === "chat" ? "var(--accent-lime)" : metric === "runs" ? "var(--accent-pink)" : "var(--accent-purple)";
  const accountOptions = [{ id: "all", displayName: "All Devices" }, ...state.statsClients];
  const scriptOptions = [{ path: "all", name: "All Scripts" }, ...state.statsScripts];
  return `
    ${topBar("Analytics", "fa-solid fa-chart-pie")}
    <div class="toolbar">
      <div class="toolbar-left">
        <select id="stats-account">
          ${accountOptions.map((client) => `<option value="${escapeAttr(client.id)}" ${state.statsClientFilter === client.id ? "selected" : ""}>${escapeHtml(client.displayName)}</option>`).join("")}
        </select>
        <select id="stats-script">
          ${scriptOptions.map((script) => `<option value="${escapeAttr(script.path)}" ${state.statsScriptFilter === script.path ? "selected" : ""}>${escapeHtml(script.name)}</option>`).join("")}
        </select>
      </div>
      <div class="toolbar-right">
        <button class="btn btn-secondary btn-sm" data-action="export-analytics" ${history.length ? "" : "disabled"}>
          <i class="fa-solid fa-file-csv"></i> Export CSV
        </button>
        <div class="pill-group">
          ${["7d", "30d", "90d", "1yr", "All"].map((f) => `
            <button class="pill-btn ${state.timeFilter === f ? "active" : ""}" data-filter="${f}">${f}</button>
          `).join("")}
        </div>
      </div>
    </div>

    <div class="card chart-card" style="margin-bottom: 24px;">
      <div class="card-header">
        <h3><i class="fa-solid fa-chart-area"></i> Performance Over Time</h3>
        <div class="pill-group">
          ${[["time", "Runtime"], ["fps", "FPS"], ["commands", "Commands"], ["runs", "Runs"], ["chat", "Chat"]].map(([id, label]) => `
            <button class="pill-btn ${state.metricTab === id ? "active" : ""}" data-metric="${id}">${label}</button>
          `).join("")}
        </div>
      </div>
      <div class="chart-area" style="min-height: 280px;">
        ${history.length ? areaChart(chartValues, chartColor, history.map((point) => chartPointLabel(point.at)), metricLabel(metric, chartValues)) : empty("Analytics will appear here once the client sends telemetry.")}
      </div>
    </div>

    <section class="stats-row">
      ${statCard("Runtime", formatDurationSeconds(summary.runtimeSeconds || 0), "fa-solid fa-hourglass-half", "var(--accent-purple)")}
      ${statCard("Active Script Time", formatDurationSeconds(summary.activeScriptSeconds || 0), "fa-solid fa-code", "var(--accent-purple)")}
      ${statCard("Script Runs", formatNumber(summary.scriptRuns || 0), "fa-solid fa-play", "var(--accent-pink)")}
      ${statCard("Commands", formatNumber(summary.commandsCompleted || 0), "fa-solid fa-terminal", "var(--accent-orange)")}
      ${statCard("Chat Sends", formatNumber(summary.chatMessages || 0), "fa-solid fa-comment-dots", "var(--accent-lime)")}
      ${statCard("Avg FPS", formatNumber(summary.avgFps || 0), "fa-solid fa-gauge-high", "var(--accent-cyan)")}
      ${statCard("Screenshots", formatNumber(summary.screenshots || 0), "fa-solid fa-camera", "var(--accent-orange)")}
      ${statCard("Peak Clients", formatNumber(summary.activeClientsPeak || 0), "fa-solid fa-users", "var(--accent-purple)")}
      ${statCard("Sessions", formatNumber(summary.sessions || history.length), "fa-solid fa-route", "var(--accent-purple)")}
    </section>

    <div class="card" style="margin-top: 24px;">
      <div class="card-header">
        <h3><i class="fa-solid fa-list-ul"></i> Session History</h3>
      </div>
      <div class="list">
        ${history.slice().reverse().slice(0, 10).map(sessionHistoryItem).join("") || empty("No session history yet.")}
      </div>
    </div>
  `;
}

function sessionHistoryItem(point) {
  const date = new Date(point.at || Date.now()).toLocaleDateString(undefined, { month: "short", day: "numeric" });
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-calendar-day"></i></div>
      <div class="content">
        <h4>${date}</h4>
        <p>${formatDurationSeconds(point.runtimeSeconds || 0)} • ${formatNumber(point.scriptRuns || 0)} runs • ${formatNumber(point.commandsCompleted || 0)} commands</p>
      </div>
      <span class="meta">${formatNumber(point.avgFps || 0)} FPS</span>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function formatDurationSeconds(seconds) {
  const totalSeconds = Math.max(0, Math.round(Number(seconds) || 0));
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const m = Math.floor(totalSeconds / 60);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  if (h < 24) return rem ? `${h}h ${rem}m` : `${h}h`;
  const d = Math.floor(h / 24);
  const rh = h % 24;
  return rh ? `${d}d ${rh}h` : `${d}d`;
}

function accountsPage() {
  const accounts = state.clients.length ? state.clients : [state.profile || { displayName: "EnderUser", tier: "Premium" }];
  return `
    ${topBar("Licensed Devices", "fa-solid fa-microchip")}
    <section class="accounts-grid">
      ${accounts.map(accountCard).join("")}
    </section>
  `;
}

function remotePage() {
  const selectedClient = state.selectedClient;
  const selectedScript = state.selectedScript;
  const clientConnected = selectedClient?.connected !== false;
  const stream = state.stream;
  const streamReady = Boolean(stream?.available && stream?.running);
  const streamSource = `${API_BASE}/api/stream/mjpeg?ts=${encodeURIComponent(stream?.startedAt || "idle")}`;
  const clientStatus = selectedClient
    ? [selectedClient.server || "Not connected", selectedClient.world || "Main menu", selectedClient.position || "-"]
    : ["No client selected", "Waiting for heartbeat", "-"];
  const eventLog = [
    "Remote initialized.",
    state.online ? "Backend handshake complete." : "Backend offline.",
    selectedClient ? `${selectedClient.displayName || "Client"} ${clientConnected ? "is live." : "is offline."}` : "No client selected.",
    selectedScript ? `Selected script: ${selectedScript.name || selectedScript.path}` : "No script selected.",
    streamReady ? `Feed source: ${stream.captureTitle || stream.source || "window"}` : "Feed idle."
  ];
  const streamMessage = friendlyStreamMessage(stream);
  return `
    ${topBar("Remote", "fa-solid fa-satellite-dish")}
    <div class="remote-layout">
      <div class="remote-main">
        <div class="remote-screen">
          ${streamReady ? `
            <img
              class="remote-stream"
              src="${escapeAttr(streamSource)}"
              alt="Live Minecraft feed"
              data-stream-image
            >
          ` : `
            <div class="remote-placeholder">
              <i class="fa-solid fa-desktop"></i>
              <p>${selectedClient ? `${clientConnected ? "Connected to" : "Last seen"} ${escapeHtml(selectedClient.deviceName || selectedClient.displayName || "device")}` : "Select a licensed device to begin remote control"}</p>
              <p>${escapeHtml(clientStatus.join(" • "))}</p>
              <p>${escapeHtml(streamMessage)}</p>
            </div>
          `}
        </div>
        <div class="remote-controls">
          <select id="remote-account">
            ${state.clients.length ? state.clients.map((c) => `<option value="${escapeAttr(c.id || "")}" ${selectedClient?.id === c.id ? "selected" : ""}>${escapeHtml(c.deviceName || c.displayName || "Device")}</option>`).join("") : `<option>This device</option>`}
          </select>
          <select id="remote-script">
            ${state.scripts.length ? state.scripts.map((s) => `<option value="${escapeAttr(s.path || "")}" ${selectedScript?.path === s.path ? "selected" : ""}>${escapeHtml(s.name || s.fileName || s.path)}</option>`).join("") : `<option>No scripts</option>`}
          </select>
          <button class="btn btn-secondary" data-action="remote-capture" ${selectedClient && clientConnected ? "" : "disabled"}><i class="fa-solid fa-camera"></i> Capture</button>
          <button class="btn btn-secondary" data-action="remote-stream-restart"><i class="fa-solid fa-video"></i> Restart feed</button>
          <button class="btn btn-primary" data-action="remote-run" style="margin-left: auto;" ${selectedClient && selectedScript && clientConnected ? "" : "disabled"}><i class="fa-solid fa-play"></i> Run</button>
          <button class="btn btn-secondary" data-action="remote-stop" ${selectedClient && clientConnected ? "" : "disabled"}><i class="fa-solid fa-stop"></i> Stop</button>
        </div>
      </div>
      <div class="remote-sidebar">
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-circle-info"></i> Remote Viewer Setup</h3></div>
          <div class="remote-help">
            <p>1. Keep Minecraft and the Shulkr backend running.</p>
            <p>2. Select the connected device above.</p>
            <p>3. Use Restart feed if the picture is blank.</p>
            <span>${escapeHtml(stream?.available ? "Capture engine detected and ready." : "The capture engine is not available on this machine.")}</span>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-terminal"></i> Event Log</h3></div>
          <div class="log-messages">
            ${eventLog.map((message) => `<div class="log-message"><span class="time" aria-hidden="true">•</span> ${escapeHtml(message)}</div>`).join("")}
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-comment-dots"></i> Live Chat</h3></div>
          <div class="remote-chat-row">
            <input id="remote-chat-input" placeholder="Type a live chat message..." value="${escapeAttr(state.remoteChatMessage)}">
            <button class="btn btn-primary" data-action="remote-chat-send" ${selectedClient && clientConnected ? "" : "disabled"}><i class="fa-solid fa-paper-plane"></i> Send</button>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-bolt"></i> Quick Script</h3></div>
          <div class="form-group">
            <label>Script Name</label>
            <input id="remote-draft-name" value="${escapeAttr(state.remoteDraftName)}" placeholder="QuickRemoteScript.py">
          </div>
          <div class="form-group">
            <label>Script Code</label>
            <textarea id="remote-draft-code" rows="6" placeholder="Write a fast remote script...">${escapeHtml(state.remoteDraftCode)}</textarea>
          </div>
          <div class="remote-quick-actions">
            <button class="btn btn-secondary" data-action="remote-script-save"><i class="fa-solid fa-floppy-disk"></i> Save Script</button>
            <button class="btn btn-primary" data-action="remote-script-save-run" ${selectedClient && clientConnected ? "" : "disabled"}><i class="fa-solid fa-play"></i> Save + Run</button>
          </div>
        </div>
      </div>
    </div>
  `;
}

function friendlyStreamMessage(stream) {
  if (!stream?.available) return "Live preview needs the capture engine to be installed.";
  if (!stream?.lastError) return stream?.running ? "Live preview is ready." : "Feed stopped. Use Restart feed to begin window capture.";
  const error = String(stream.lastError).toLowerCase();
  if (error.includes("can't find window") || error.includes("cannot find window")) return "Live preview could not find the configured Minecraft window.";
  if (error.includes("permission") || error.includes("access denied")) return "Live preview needs permission to capture the Minecraft window.";
  if (error.includes("opening input") || error.includes("i/o error")) return "Live preview could not open the Minecraft window. Check that it is running and visible.";
  return "Live preview is unavailable right now. Restart the feed after checking Minecraft.";
}

function billingPage() {
  const billing = state.billingStatus || { enabled: false, plans: [] };
  const currentTier = billing.currentTier || state.auth.user?.tier || "Free";
  const canPurchase = !state.auth.user?.isAdmin;
  const plans = billing.plans?.length
    ? billing.plans
    : [
        { id: "pro", tier: "Pro", amount: 999, currency: "usd", interval: "month", description: "Remote dashboard, templates, and client modules." },
        { id: "premium", tier: "Premium", amount: 1999, currency: "usd", interval: "month", description: "Everything in Pro plus publishing/import power features." }
      ];
  return `
    ${topBar("Billing", "fa-solid fa-credit-card")}
    <section class="profile-hero">
      <div class="card profile-main">
        <div class="profile-info">
          <div class="avatar-xl"><i class="fa-solid fa-credit-card"></i></div>
          <div class="profile-name">
            <h2>${escapeHtml(currentTier)}</h2>
            <p>${escapeHtml(billing.stripeStatus || "No active subscription")}</p>
            <span class="badge badge-premium">${escapeHtml(currentTier)}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="btn btn-primary" data-page="customerPortal"><i class="fa-solid fa-id-card"></i> Customer Portal</button>
          <button class="btn btn-secondary" data-action="open-billing-portal" ${billing.stripeCustomerId ? "" : "disabled"}><i class="fa-solid fa-arrow-up-right-from-square"></i> Stripe Portal</button>
        </div>
      </div>
      <div class="card profile-meta">
        <div class="meta-row"><span>Customer</span><span>${billing.stripeCustomerId ? "Connected" : "Not linked"}</span></div>
        <div class="meta-row"><span>Subscription</span><span>${escapeHtml(billing.stripeSubscriptionId || "None")}</span></div>
        <div class="meta-row"><span>Status</span><span>${escapeHtml(billing.stripeStatus || "inactive")}</span></div>
        <div class="meta-row"><span>HWID lock</span><span>${licenseHwidSummary(billing.currentLicense)}</span></div>
      </div>
    </section>

    <section class="landing-section" style="padding: 0;">
      <div class="pricing-grid">
        <div class="pricing-card ${currentTier === "Free" ? "featured" : ""}">
          <div class="pricing-header">
            <h3>FREE</h3>
            <div class="pricing-price"><span class="price-amount">$0</span><span class="price-period">/month</span></div>
          </div>
          <ul class="pricing-features">
            <li><i class="fa-solid fa-check"></i> Script editor access</li>
            <li><i class="fa-solid fa-check"></i> Local scripting and analytics</li>
            <li><i class="fa-solid fa-check"></i> Core remote controls</li>
            <li class="disabled"><i class="fa-solid fa-x"></i> Hosted stream feed</li>
            <li class="disabled"><i class="fa-solid fa-x"></i> Premium templates</li>
            <li class="disabled"><i class="fa-solid fa-x"></i> Client modules</li>
          </ul>
          <button class="btn btn-secondary" disabled>${currentTier === "Free" ? "CURRENT PLAN" : "FREE PLAN"}</button>
        </div>
        ${plans.map((plan) => {
          const active = currentTier.toLowerCase() === String(plan.tier || "").toLowerCase();
          const isFeatured = plan.id === "pro";
          return `
            <div class="pricing-card ${isFeatured ? "featured" : ""}">
              ${isFeatured ? `<div class="featured-badge">RECOMMENDED</div>` : ""}
              <div class="pricing-header">
                <h3>${escapeHtml(String(plan.tier || "").toUpperCase())}</h3>
                <div class="pricing-price"><span class="price-amount">${formatMoney(plan.amount, plan.currency)}</span><span class="price-period">/${escapeHtml(plan.interval || "month")}</span></div>
              </div>
              <p style="color: var(--text-secondary); margin: 0 0 18px;">${escapeHtml(plan.description || "")}</p>
              <ul class="pricing-features">
                <li><i class="fa-solid fa-check"></i> Hosted Stripe subscription</li>
                <li><i class="fa-solid fa-check"></i> Automatic tier unlocks</li>
                <li><i class="fa-solid fa-check"></i> Manage from customer portal</li>
                <li><i class="fa-solid fa-check"></i> Test mode safe</li>
              </ul>
              <button class="btn ${active ? "btn-secondary" : "btn-primary"}" data-action="checkout-${escapeAttr(plan.id)}" ${active || !billing.enabled || !canPurchase ? "disabled" : ""}>
                ${active ? "CURRENT PLAN" : !canPurchase ? "ADMIN ACCOUNT" : `UPGRADE TO ${escapeHtml(String(plan.tier || "").toUpperCase())}`}
              </button>
            </div>
          `;
        }).join("")}
      </div>
    </section>
  `;
}

function customerPortalPage() {
  const billing = state.billingStatus || { currentLicense: null, features: [] };
  const license = billing.currentLicense || null;
  const currentTier = billing.currentTier || state.auth.user?.tier || "Free";
  const features = Array.isArray(billing.features) ? billing.features : [];
  return `
    ${topBar("Customer Portal", "fa-solid fa-id-card")}
    <section class="profile-hero">
      <div class="card profile-main">
        <div class="profile-info">
          <div class="avatar-xl"><i class="fa-solid fa-user-shield"></i></div>
          <div class="profile-name">
            <h2>${escapeHtml(state.auth.user?.displayName || "Customer")}</h2>
            <p>${escapeHtml(state.auth.user?.email || state.auth.user?.username || "Signed in locally")}</p>
            <span class="badge badge-premium">${escapeHtml(currentTier)}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="btn btn-secondary" data-page="billing"><i class="fa-solid fa-credit-card"></i> Billing</button>
          <button class="btn btn-primary" data-action="open-billing-portal" ${billing.stripeCustomerId ? "" : "disabled"}><i class="fa-solid fa-arrow-up-right-from-square"></i> Open Stripe Portal</button>
        </div>
      </div>
      <div class="card profile-meta">
        <div class="meta-row"><span>Current plan</span><span>${escapeHtml(currentTier)}</span></div>
        <div class="meta-row"><span>Subscription status</span><span>${escapeHtml(billing.stripeStatus || license?.status || "inactive")}</span></div>
        <div class="meta-row"><span>Seats</span><span>${escapeHtml(String(license?.seats || 1))}</span></div>
        <div class="meta-row"><span>Bound hardware</span><span>${licenseHwidSummary(license)}</span></div>
      </div>
    </section>

    <div class="grid-2">
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-receipt"></i> Subscription</h3>
        </div>
        <div class="meta-row"><span>Stripe customer</span><span>${escapeHtml(billing.stripeCustomerId || "Not linked")}</span></div>
        <div class="meta-row"><span>Stripe subscription</span><span>${escapeHtml(billing.stripeSubscriptionId || "No subscription")}</span></div>
        <div class="meta-row"><span>License status</span><span>${escapeHtml(license?.status || "inactive")}</span></div>
        <div class="meta-row"><span>Tier source</span><span>${escapeHtml(license ? "License + entitlements" : "Account default")}</span></div>
        <div class="meta-row"><span>Primary device</span><span>${escapeHtml(license?.primaryDeviceName || shortDeviceId(license?.primaryDeviceId) || "Not bound")}</span></div>
        <div class="meta-row"><span>Access ends</span><span>${escapeHtml(formatDateLabel(license?.expiresAt))}</span></div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-bolt"></i> Enabled Features</h3>
        </div>
        <div class="admin-tag-row">
          ${features.length ? features.map((feature) => `<span class="hub-tag">${escapeHtml(feature)}</span>`).join("") : `<span class="hub-tag">No premium features yet</span>`}
        </div>
        <div style="display:flex; gap:12px; margin-top: 18px; flex-wrap: wrap;">
          <button class="btn btn-primary" data-page="billing"><i class="fa-solid fa-arrow-up"></i> Change Plan</button>
          <button class="btn btn-secondary" data-action="refresh"><i class="fa-solid fa-rotate"></i> Refresh Status</button>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top: 24px;">
      <div class="card-header">
        <h3><i class="fa-solid fa-circle-info"></i> Customer Options</h3>
      </div>
      <div class="list">
        ${portalOption("Update payment method", billing.stripeCustomerId ? "Open the Stripe portal to update cards, invoices, or billing details." : "Create a Stripe customer first by starting a checkout session.")}
        ${portalOption("Change subscription tier", "Use the Billing page to switch between Free, Pro, and Premium.")}
        ${portalOption("Check entitlement sync", "The dashboard reflects the stored license, bound hardware ID, and Stripe sync status for this device.")}
      </div>
    </div>
  `;
}

function settingsPage() {
  const entitlements = state.entitlements || {
    tier: state.auth.user?.tier || "Premium",
    features: state.auth.user?.features || [],
    isAdmin: Boolean(state.auth.user?.isAdmin)
  };
  const tabs = [
    ["customization", "Customization", "fa-solid fa-palette"],
    ["layout", "Layout", "fa-solid fa-table-columns"],
    ["connection", "Connection", "fa-solid fa-server"],
    ["account", "Account", "fa-solid fa-user-shield"]
  ];
  return `
    ${topBar("Settings", "fa-solid fa-sliders")}
    <section class="settings-workspace">
      <div class="settings-hero">
        <div>
          <span class="page-eyebrow">Dashboard preferences</span>
          <h2>Make Shulkr feel like yours.</h2>
          <p>Theme, layout, account and connection controls—all saved instantly.</p>
        </div>
        <div class="settings-save-state"><i class="fa-solid fa-circle-check"></i> Saved locally</div>
      </div>
      <div class="settings-tabs">
        ${tabs.map(([id, label, icon]) => `<button class="${state.settingsTab === id ? "active" : ""}" data-settings-tab="${id}"><i class="${icon}"></i>${label}</button>`).join("")}
      </div>
      <div class="settings-tab-panel">
        ${settingsTabContent(entitlements)}
      </div>
    </section>
  `;
}

function settingsTabContent(entitlements) {
  if (state.settingsTab === "customization") {
    const themes = [
      ["nova", "Nova", "Purple energy", ["#08050c", "#9d4dff", "#ff3aa5"]],
      ["midnight", "Midnight", "Deep blue focus", ["#050814", "#5577ff", "#2de2e6"]],
      ["ember", "Ember", "Warm dark contrast", ["#100706", "#ff6b35", "#ffd166"]],
      ["matrix", "Matrix", "Terminal green", ["#030a07", "#35e58b", "#b8ff2c"]]
    ];
    return `
      <div class="settings-customizer-grid">
        <div class="card settings-control-card">
          <div class="card-header"><h3><i class="fa-solid fa-swatchbook"></i> Theme presets</h3></div>
          <div class="theme-preset-grid">
            ${themes.map(([id, name, desc, colors]) => `
              <button class="theme-preset ${state.theme === id ? "active" : ""}" data-theme-choice="${id}">
                <span class="theme-preview" style="--preview-bg:${colors[0]};--preview-a:${colors[1]};--preview-b:${colors[2]}"><i></i><b></b><em></em></span>
                <span><strong>${name}</strong><small>${desc}</small></span>
                <i class="fa-solid fa-check theme-check"></i>
              </button>`).join("")}
          </div>
        </div>
        <div class="card settings-live-preview">
          <div class="card-header"><h3><i class="fa-solid fa-eye"></i> Live preview</h3><span class="hub-tag">Instant</span></div>
          <div class="theme-demo">
            <div class="theme-demo-sidebar"><i></i><span></span><span></span><span></span></div>
            <div class="theme-demo-main"><small>WELCOME BACK</small><strong>Shulkr Dashboard</strong><p>Your scripts and devices, beautifully organized.</p><button>OPEN SCRIPTS</button></div>
          </div>
          <p class="settings-note">The selected palette applies across every dashboard page and is remembered on this device.</p>
        </div>
      </div>`;
  }
  if (state.settingsTab === "layout") {
    return `<div class="settings-grid">
      <div class="card"><div class="card-header"><h3><i class="fa-solid fa-compress"></i> Interface density</h3></div>
        <div class="density-options">${["compact", "comfortable", "spacious"].map(id => `<button class="${state.density === id ? "active" : ""}" data-density-choice="${id}"><i class="fa-solid fa-${id === "compact" ? "bars" : id === "spacious" ? "grip" : "list"}"></i><strong>${id[0].toUpperCase() + id.slice(1)}</strong><span>${id === "compact" ? "More content" : id === "spacious" ? "More breathing room" : "Balanced default"}</span></button>`).join("")}</div>
      </div>
      <div class="card"><div class="card-header"><h3><i class="fa-solid fa-wand-magic-sparkles"></i> Experience</h3></div><div class="meta-row"><span>Sidebar</span><span>Adaptive</span></div><div class="meta-row"><span>Animations</span><span>Enabled</span></div><div class="meta-row"><span>Card effects</span><span>Glass</span></div></div>
    </div>`;
  }
  if (state.settingsTab === "connection") {
    return `<div class="settings-grid"><div class="card"><div class="card-header"><h3><i class="fa-solid fa-server"></i> Backend connection</h3></div><div class="form-group"><label>Backend URL</label><input id="api-base" value="${escapeAttr(API_BASE)}" /></div><div class="settings-actions"><button class="btn btn-primary" data-action="save-api"><i class="fa-solid fa-floppy-disk"></i> Save</button><button class="btn btn-secondary" data-action="refresh"><i class="fa-solid fa-rotate"></i> Test connection</button></div></div><div class="card"><div class="card-header"><h3><i class="fa-solid fa-signal"></i> Status</h3></div><div class="meta-row"><span>Backend</span><span class="${state.online ? "settings-online" : ""}">${state.online ? "Connected" : "Offline"}</span></div><div class="meta-row"><span>Remote stream</span><span>${state.stream?.available ? "Ready" : "Unavailable"}</span></div></div></div>`;
  }
  return `<div class="settings-grid"><div class="card"><div class="card-header"><h3><i class="fa-solid fa-key"></i> Entitlements</h3></div><div class="meta-row"><span>Tier</span><span>${escapeHtml(entitlements.tier || "Premium")}</span></div><div class="meta-row"><span>Admin</span><span>${entitlements.isAdmin ? "Yes" : "No"}</span></div><div class="admin-tag-row settings-feature-tags">${(entitlements.features || []).map(feature => `<span class="hub-tag">${escapeHtml(feature)}</span>`).join("") || `<span class="hub-tag">client</span>`}</div></div><div class="card"><div class="card-header"><h3><i class="fa-solid fa-wallet"></i> Billing & account</h3></div><p class="settings-note">Manage your subscription, payment details, devices, and customer profile.</p><div class="settings-actions"><button class="btn btn-primary" data-page="billing"><i class="fa-solid fa-credit-card"></i> Billing</button><button class="btn btn-secondary" data-page="customerPortal"><i class="fa-solid fa-id-card"></i> Customer portal</button></div></div></div>`;
}

function applyDashboardTheme(theme, density) {
  document.documentElement.dataset.theme = theme || "nova";
  document.documentElement.dataset.density = density || "comfortable";
}

function adminPage() {
  if (!state.auth.user?.isAdmin) {
    return `
      ${topBar("Admin", "fa-solid fa-shield-halved")}
      <div class="card">
        ${empty("Admin access required.")}
      </div>
    `;
  }
  const overview = state.adminOverview || { summary: {}, users: [], licenses: [], tamperEvents: [], analytics: {} };
  const summary = overview.summary || {};
  const analytics = overview.analytics || {};
  return `
    ${topBar("Admin", "fa-solid fa-shield-halved")}
    <section class="stats-row">
      ${statCard("Users", formatNumber(summary.users || 0), "fa-solid fa-users", "var(--accent-cyan)")}
      ${statCard("Admins", formatNumber(summary.admins || 0), "fa-solid fa-crown", "var(--gold)")}
      ${statCard("Licenses", formatNumber(summary.activeLicenses || 0), "fa-solid fa-id-card", "var(--accent-purple)")}
      ${statCard("Clients", formatNumber(summary.connectedClients || 0), "fa-solid fa-plug", "var(--green)")}
      ${statCard("Tamper Events", formatNumber(summary.tamperEvents || 0), "fa-solid fa-shield", "var(--accent-orange)")}
      ${statCard("Blocked", formatNumber(summary.blockedAttempts || 0), "fa-solid fa-ban", "var(--red)")}
    </section>

    <div class="grid-2">
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-lock"></i> Protection Status</h3></div>
        <div class="admin-tag-row" style="margin-bottom: 18px;">
          <span class="hub-tag">JWT auth</span>
          <span class="hub-tag">tier checks</span>
          <span class="hub-tag">feature gates</span>
          <span class="hub-tag">tamper log</span>
          <span class="hub-tag">admin routes</span>
        </div>
        <div class="list">
          ${adminHealthItem("30d runtime", formatDurationSeconds(analytics.runtimeSeconds || 0))}
          ${adminHealthItem("Script runs", formatNumber(analytics.scriptRuns || 0))}
          ${adminHealthItem("Commands completed", formatNumber(analytics.commandsCompleted || 0))}
          ${adminHealthItem("Peak active clients", formatNumber(analytics.activeClientsPeak || 0))}
        </div>
      </div>
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-triangle-exclamation"></i> Recent Tamper Events</h3></div>
        <div class="list">
          ${overview.tamperEvents?.length ? overview.tamperEvents.slice(0, 6).map(tamperEventItem).join("") : empty("No recent tamper events.")}
        </div>
      </div>
    </div>

    <div class="grid-2" style="margin-top: 24px;">
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-user-shield"></i> Users</h3></div>
        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead><tr><th>User</th><th>Login</th><th>Tier</th><th>Provider</th></tr></thead>
            <tbody>
              ${overview.users?.map((user) => `
                <tr>
                  <td>${escapeHtml(user.displayName || "User")}</td>
                  <td>${escapeHtml(user.username || user.email || "")}</td>
                  <td>${escapeHtml(user.tier || "Premium")}</td>
                  <td>${escapeHtml(user.provider || "local")}</td>
                </tr>
              `).join("") || ""}
            </tbody>
          </table>
        </div>
      </div>
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-file-contract"></i> Licenses</h3></div>
        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead><tr><th>User</th><th>Tier</th><th>Status</th><th>Features</th></tr></thead>
            <tbody>
              ${overview.licenses?.map((license) => `
                <tr>
                  <td>${escapeHtml(license.displayName || license.userId || "User")}</td>
                  <td>${escapeHtml(license.tier || "Premium")}</td>
                  <td>${escapeHtml(license.status || "active")}</td>
                  <td>${escapeHtml((license.features || []).slice(0, 4).join(", "))}</td>
                </tr>
              `).join("") || ""}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `;
}

function offlineState() {
  return `
    ${topBar("Offline", "fa-solid fa-triangle-exclamation")}
    <div class="card" style="max-width: 600px;">
      <div class="empty-state" style="min-height: 320px;">
        <div>
          <i class="fa-solid fa-triangle-exclamation" style="font-size: 3rem; color: var(--red); margin-bottom: 16px;"></i>
          <h2 style="margin: 0 0 8px;">Server is offline</h2>
          <p style="color: var(--text-muted); margin: 0 0 20px;">The Shulkr backend is not responding at <code>${escapeHtml(API_BASE)}</code>.</p>
          <div class="form-group" style="text-align: left; width: 100%; max-width: 400px;">
            <label>Backend URL</label>
            <input id="api-base" value="${escapeAttr(API_BASE)}" />
          </div>
          <div style="display: flex; gap: 12px; justify-content: center; margin-top: 16px;">
            <button class="btn btn-primary" data-action="save-api">Save URL</button>
            <button class="btn btn-secondary" data-action="refresh">Try again</button>
          </div>
        </div>
      </div>
    </div>
  `;
}

function loadingState() {
  return `
    ${topBar("Loading", "fa-solid fa-circle-notch")}
    <div class="empty-state" style="min-height: 400px;">
      <div>
        <i class="fa-solid fa-circle-notch fa-spin" style="font-size: 3rem; color: var(--accent-purple); margin-bottom: 16px;"></i>
        <h2 style="margin: 0;">Loading Shulkr...</h2>
      </div>
    </div>
  `;
}

function statCard(label, value, icon, color) {
  return `
    <div class="stat-card">
      <div class="label"><i class="${icon}" style="color: ${color};"></i> ${escapeHtml(label)}</div>
      <div class="value">${escapeHtml(String(value))}</div>
    </div>
  `;
}

function sidebarSection(title, items) {
  if (!items.length) return "";
  return `
    <div class="sidebar-section">
      <span class="sidebar-section-title">${escapeHtml(title)}</span>
      <div class="sidebar-section-items">
        ${items.map(([id, label, icon, tip]) => `
          <button class="nav-item ${state.page === id ? "active" : ""}" data-page="${id}" data-testid="nav-${id}" aria-label="${label}">
            <span class="nav-item-icon"><i class="${icon}"></i></span>
            <span class="nav-copy">
              <strong>${escapeHtml(label)}</strong>
            </span>
            <span class="nav-tooltip">${escapeHtml(tip || label)}</span>
          </button>
        `).join("")}
      </div>
    </div>
  `;
}

function overviewMetric(label, value, icon, detail) {
  return `
    <div class="overview-metric card">
      <div class="overview-metric-top">
        <span class="overview-metric-icon"><i class="${icon}"></i></span>
        <span class="overview-metric-label">${escapeHtml(label)}</span>
      </div>
      <strong>${escapeHtml(String(value))}</strong>
      <p>${escapeHtml(detail)}</p>
    </div>
  `;
}

function focusItem(label, value) {
  return `
    <div class="focus-item">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </div>
  `;
}

function quickStartCard(title, text, icon, page) {
  return `
    <button class="quick-start-card" data-page="${escapeAttr(page)}">
      <span class="quick-start-icon"><i class="${icon}"></i></span>
      <span class="quick-start-copy">
        <strong>${escapeHtml(title)}</strong>
        <small>${escapeHtml(text)}</small>
      </span>
      <i class="fa-solid fa-arrow-right"></i>
    </button>
  `;
}

function adminHealthItem(label, value) {
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-shield-heart"></i></div>
      <div class="content">
        <h4>${escapeHtml(label)}</h4>
      </div>
      <span class="meta">${escapeHtml(String(value))}</span>
    </div>
  `;
}

function tamperEventItem(event) {
  const severityColor = event.severity === "error" ? "var(--red)" : "var(--accent-orange)";
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-triangle-exclamation" style="color: ${severityColor};"></i></div>
      <div class="content">
        <h4>${escapeHtml(event.message || event.type || "Tamper event")}</h4>
        <p>${escapeHtml([event.route, event.actorEmail || event.actorId, event.at ? timeAgo(event.at) : ""].filter(Boolean).join(" • "))}</p>
      </div>
      <span class="meta">${escapeHtml((event.severity || "warn").toUpperCase())}</span>
    </div>
  `;
}

function activityItem(script) {
  return `
    <div class="list-item" data-select-script="${escapeAttr(script.path)}" role="button" tabindex="0" aria-label="Open ${escapeAttr(script.name || script.fileName || script.path)}">
      <div class="icon"><i class="fa-solid fa-file-code"></i></div>
      <div class="content">
        <h4>${escapeHtml(script.name || script.fileName || script.path)}</h4>
        <p>${escapeHtml(script.path || "Local script")}</p>
      </div>
      <span class="meta">${formatBytes(script.sizeBytes || script.size || 0)}</span>
    </div>
  `;
}

function quickLaunchItem(script) {
  return `
    <div class="list-item" data-select-script="${escapeAttr(script.path)}" role="button" tabindex="0" aria-label="Run ${escapeAttr(script.name || script.fileName || "Script")}">
      <div class="icon" style="background: rgba(45, 226, 230, 0.1); color: var(--accent-cyan);"><i class="fa-solid fa-play"></i></div>
      <div class="content">
        <h4>${escapeHtml(script.name || script.fileName || "Script")}</h4>
        <p>Click to launch remotely</p>
      </div>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function clientListItem(client) {
  const connected = client.connected !== false;
  return `
    <div class="list-item">
      <div class="icon" style="background: ${connected ? "rgba(61, 220, 132, 0.1)" : "rgba(255, 77, 109, 0.1)"}; color: ${connected ? "var(--green)" : "var(--red)"};">
        <i class="fa-solid fa-user"></i>
      </div>
      <div class="content">
        <h4>${escapeHtml(client.displayName || "EnderUser")}</h4>
        <p>${escapeHtml(client.tier || "Local user")}</p>
      </div>
      <span class="account-status ${connected ? "" : "offline"}"><span class="dot"></span>${connected ? "Online" : "Offline"}</span>
    </div>
  `;
}

function sessionItem(script) {
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-route"></i></div>
      <div class="content">
        <h4>${escapeHtml(script.name || script.fileName || "Script")}</h4>
        <p>${escapeHtml(script.path || "Local script")}</p>
      </div>
      <span class="meta">${formatModified(script.modifiedAt || script.modifiedAgo || script.modified)}</span>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function accountCard(client) {
  const connected = client.connected !== false;
  const shortId = shortDeviceId(client.deviceId || client.id || "");
  const accountName = client.accountName ? ` • ${client.accountName}` : "";
  const licenseLine = client.hardwareLocked === false
    ? "Hardware lock disabled"
    : `${client.licenseStatus === "blocked" ? "Blocked" : "HWID locked"}${shortId ? ` • ${shortId}` : ""}`;
  return `
    <div class="card account-card">
      <div class="avatar">${escapeHtml((client.deviceName || client.displayName || "D").slice(0, 1))}</div>
      <div class="info">
        <h4>${escapeHtml(client.deviceName || client.displayName || "This device")}</h4>
        <p>${escapeHtml(licenseLine)}</p>
        <span class="account-status ${connected ? "" : "offline"}"><span class="dot"></span>${connected ? "Active now" : "Offline"}${client.minecraft ? ` • ${escapeHtml(client.minecraft)}` : ""}${accountName ? ` • ${escapeHtml(client.accountName)}` : ""}</span>
      </div>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function shortDeviceId(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  if (text.length <= 18) return text;
  return `${text.slice(0, 10)}...${text.slice(-6)}`;
}

function licenseHwidSummary(license) {
  if (!license) return "Not bound";
  if (license.hardwareLock === false) return "Disabled";
  const boundCount = Array.isArray(license.boundDeviceIds) ? license.boundDeviceIds.length : 0;
  if (boundCount === 0) return "Waiting for first device";
  return `${boundCount}/${Number(license.deviceLimit || license.seats || 1) || 1} bound`;
}

function areaChart(values, color = "var(--accent-purple)", labels = [], formatter = (value) => formatNumber(value)) {
  if (!values.length) {
    return empty("No chart data yet.");
  }
  if (values.length === 1) {
    const value = values[0] || 0;
    const label = labels[0] || "Latest";
    return `
      <div class="chart-single">
        <div class="chart-single-label">${escapeHtml(label)}</div>
        <div class="chart-single-value" style="color: ${color};">${escapeHtml(formatter(value))}</div>
        <p>More points will appear as telemetry comes in.</p>
      </div>
    `;
  }
  const width = 800;
  const height = 220;
  const max = Math.max(...values, 1);
  const min = Math.min(...values);
  const range = max - min || 1;
  const stepX = values.length > 1 ? width / (values.length - 1) : width;
  const points = values.map((v, i) => {
    const x = i * stepX;
    const y = height - ((v - min) / range) * (height - 40) - 20;
    return `${x},${y}`;
  });
  const area = `${points[0]} ${points.map((p) => p).join(" ")} ${width},${height} 0,${height}`;
  const lastValue = values[values.length - 1] || 0;
  const lastLabel = labels[labels.length - 1] || "";
  return `
    <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
      <defs>
        <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="${color}" stop-opacity="0.4"/>
          <stop offset="100%" stop-color="${color}" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <line class="chart-grid" x1="0" y1="${height / 2}" x2="${width}" y2="${height / 2}"/>
      <line class="chart-grid" x1="0" y1="20" x2="${width}" y2="20"/>
      <line class="chart-grid" x1="0" y1="${height - 20}" x2="${width}" y2="${height - 20}"/>
      <polygon class="chart-fill" points="${area}" fill="url(#areaGrad)"/>
      <polyline class="chart-line" points="${points.join(" ")}" style="stroke: ${color};"/>
      ${values.length ? `<circle cx="${points[points.length - 1].split(",")[0]}" cy="${points[points.length - 1].split(",")[1]}" r="5" fill="${color}"/>` : ""}
      <text x="0" y="16" fill="rgba(255,255,255,0.55)" font-size="12">${escapeHtml(formatter(max))}</text>
      <text x="0" y="${height - 6}" fill="rgba(255,255,255,0.55)" font-size="12">${escapeHtml(formatter(min))}</text>
      ${lastLabel ? `<text x="${Math.max(0, width - 170)}" y="18" fill="rgba(255,255,255,0.75)" font-size="12">${escapeHtml(lastLabel)} • ${escapeHtml(formatter(lastValue))}</text>` : ""}
    </svg>
  `;
}

function chartPointLabel(timestamp) {
  return new Date(timestamp || Date.now()).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function metricLabel(metric, values) {
  return (value) => {
    if (metric === "time") return formatDurationSeconds(value);
    if (metric === "fps") return `${Math.round(value || 0)} FPS`;
    if (metric === "commands") return `${formatNumber(value || 0)} cmds`;
    if (metric === "chat") return `${formatNumber(value || 0)} chat`;
    return `${formatNumber(value || 0)} runs`;
  };
}

function portalOption(title, text) {
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-check"></i></div>
      <div class="content">
        <h4>${escapeHtml(title)}</h4>
        <p>${escapeHtml(text)}</p>
      </div>
    </div>
  `;
}

function formatDateLabel(value) {
  if (!value) return "Not scheduled";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric"
  });
}

function flowBuilderPage() {
  flowStore.state.templates = state.templates.filter((template) => template.kind === "automation");
  const connectedClient = state.clients.find(client => client.connected !== false);
  flowStore.setClientConnection(Boolean(connectedClient), false, connectedClient?.id || "");
  if (!flowEditorModule) {
    void ensureFlowEditorModule().catch(error => { state.error = error.message || "Flow Builder could not be loaded"; render(); });
    return `${topBar("Flow Builder", "fa-solid fa-diagram-project")}<div class="card flow-module-loading" data-flow-loading><i class="fa-solid fa-diagram-project"></i><strong>Loading Flow Builder…</strong><span>Preparing the visual automation canvas.</span></div>`;
  }
  return `${topBar("Flow Builder", "fa-solid fa-diagram-project")}${flowEditorModule.flowBuilderPage()}`;
}

function navigateToPage(nextPage) {
  if (!nextPage) return;
  state.auth.view = "dashboard";
  state.page = nextPage;
  state.notificationsOpen = false;
  if (nextPage === "flow") {
    void ensureFlowEditorModule();
    if (!flowStore.snapshot().loading && !flowStore.snapshot().automations.length) flowStore.loadAll();
  }
  render();
  if (nextPage === "remote" || nextPage === "statistics") {
    refreshLiveState()
      .then(() => { if (state.page === nextPage) render(); })
      .catch(error => { if (state.page === nextPage) toast(error.message || "Live data could not be refreshed"); });
  }
}

function bind() {
  if (state.page === "flow" && flowEditorModule) flowEditorModule.mountFlowBuilder({ rerender: render, toast });
  document.querySelectorAll("[data-hide-on-error]").forEach(image => {
    image.addEventListener("error", () => { image.hidden = true; }, { once: true });
  });
  document.querySelector("[data-stream-image]")?.addEventListener("error", event => {
    event.currentTarget.closest(".remote-screen")?.classList.add("stream-error");
  }, { once: true });
  document.querySelectorAll(".modal-card").forEach(card => card.addEventListener("click", event => event.stopPropagation()));
  document.querySelectorAll("[data-settings-tab]").forEach((node) => {
    node.addEventListener("click", () => {
      state.settingsTab = node.dataset.settingsTab;
      render();
    });
  });
  document.querySelectorAll("[data-theme-choice]").forEach((node) => {
    node.addEventListener("click", () => {
      state.theme = node.dataset.themeChoice;
      localStorage.setItem("shulkr_theme", state.theme);
      applyDashboardTheme(state.theme, state.density);
      render();
    });
  });
  document.querySelectorAll("[data-density-choice]").forEach((node) => {
    node.addEventListener("click", () => {
      state.density = node.dataset.densityChoice;
      localStorage.setItem("shulkr_density", state.density);
      applyDashboardTheme(state.theme, state.density);
      render();
    });
  });
  document.querySelectorAll("[data-page]").forEach((node) => {
    node.addEventListener("click", () => navigateToPage(node.dataset.page));
  });
  document.querySelectorAll("[data-landing]").forEach((node) => {
    node.addEventListener("click", (e) => {
      e.preventDefault();
      state.auth.view = "landing";
      render();
    });
  });
  document.querySelectorAll("[data-action]").forEach((node) => {
    node.addEventListener("click", () => handleAction(node.dataset.action));
  });
  document.querySelectorAll("[data-modal-close]").forEach((node) => {
    node.addEventListener("click", () => {
      state.modal = null;
      render();
    });
  });
  document.querySelectorAll("[data-filter]").forEach((node) => {
    node.addEventListener("click", async () => {
      state.timeFilter = node.dataset.filter;
      if (state.page === "statistics") {
        try {
          await refreshLiveState();
        } catch {
        }
      }
      render();
    });
  });
  document.querySelectorAll("[data-metric]").forEach((node) => {
    node.addEventListener("click", async () => {
      state.metricTab = node.dataset.metric;
      if (state.page === "statistics") {
        try {
          await refreshLiveState();
        } catch {
        }
      }
      render();
    });
  });
  document.querySelectorAll("[data-select-script]").forEach((node) => {
    node.addEventListener("click", () => {
      if (state.page === "editor") {
        openScriptEditor(node.dataset.selectScript);
      } else {
        state.selectedScript = state.scripts.find((s) => s.path === node.dataset.selectScript) || state.selectedScript;
        state.page = "remote";
        ensureRemoteStream().catch(() => {});
        render();
      }
    });
    if (node.getAttribute("role") === "button") node.addEventListener("keydown", event => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      node.click();
    });
  });
  document.querySelectorAll("[data-script-open]").forEach(node => node.addEventListener("click", () => openScriptEditor(node.dataset.scriptOpen)));
  document.querySelectorAll("[data-script-rename]").forEach(node => node.addEventListener("click", () => renameManagedScript(node.dataset.scriptRename)));
  document.querySelectorAll("[data-script-delete]").forEach(node => node.addEventListener("click", () => deleteManagedScript(node.dataset.scriptDelete)));
  document.querySelectorAll("[data-folder-rename]").forEach(node => node.addEventListener("click", () => renameScriptFolder(node.dataset.folderRename)));
  document.querySelectorAll("[data-folder-delete]").forEach(node => node.addEventListener("click", () => deleteScriptFolder(node.dataset.folderDelete)));
  const scriptSearchInput = document.getElementById("script-search");
  if (scriptSearchInput) scriptSearchInput.addEventListener("input", event => {
    state.scriptSearch = event.target.value;
    render();
    const replacement = document.getElementById("script-search");
    replacement?.focus();
    replacement?.setSelectionRange?.(replacement.value.length, replacement.value.length);
  });
  const scriptFolderFilter = document.getElementById("script-folder-filter");
  if (scriptFolderFilter) scriptFolderFilter.addEventListener("change", event => { state.scriptFolder = event.target.value; render(); });
  document.querySelectorAll("[data-hub-script]").forEach((node) => {
    node.addEventListener("click", (e) => {
      if (e.target.closest("[data-hub-install], [data-hub-view], [data-hub-more]")) return;
      const script = getHubScripts().find((s) => s.id === node.dataset.hubScript);
      if (script) {
        state.selectedScript = script;
        render();
      }
    });
  });
  document.querySelectorAll("[data-hub-category]").forEach((node) => {
    node.addEventListener("click", () => {
      state.hubCategory = node.dataset.hubCategory;
      state.hubPage = 1;
      render();
    });
  });
  document.querySelectorAll("[data-hub-page]").forEach((node) => {
    node.addEventListener("click", () => {
      state.hubPage = Number(node.dataset.hubPage);
      render();
    });
  });
  document.querySelectorAll("[data-hub-filter]").forEach((node) => {
    node.addEventListener("change", () => {
      state.hubFilters[node.dataset.hubFilter] = node.checked;
      state.hubPage = 1;
      render();
    });
  });
  document.querySelectorAll("[data-hub-sort]").forEach((node) => {
    node.addEventListener("change", () => {
      state.hubSort = node.value;
      render();
    });
  });
  document.querySelectorAll("[data-hub-reset]").forEach((node) => {
    node.addEventListener("click", () => {
      state.hubSearch = "";
      state.hubCategory = "all";
      state.hubSort = "recent";
      state.hubPage = 1;
      state.hubFilters = { python: true, pyjinn: true, farming: true, combat: true, world: true, utility: true, other: true };
      render();
    });
  });
  document.querySelectorAll("[data-hub-install]").forEach((node) => {
    node.addEventListener("click", (e) => {
      e.stopPropagation();
      installHubScript(node.dataset.hubInstall);
    });
  });
  document.querySelectorAll("[data-hub-view]").forEach((node) => {
    node.addEventListener("click", (e) => {
      e.stopPropagation();
      viewHubScript(node.dataset.hubView);
    });
  });
  document.querySelectorAll("[data-hub-more]").forEach((node) => {
    node.addEventListener("click", (e) => {
      e.stopPropagation();
      openHubDetails(node.dataset.hubMore);
    });
  });
  document.querySelectorAll("[data-modal-install]").forEach((node) => {
    node.addEventListener("click", () => installHubScript(node.dataset.modalInstall));
  });
  document.querySelectorAll("[data-modal-open-editor]").forEach((node) => {
    node.addEventListener("click", () => viewHubScript(node.dataset.modalOpenEditor));
  });
  const publishForm = document.getElementById("publish-script-form");
  if (publishForm) {
    publishForm.addEventListener("submit", (e) => {
      e.preventDefault();
      submitPublishHubScript();
    });
  }
  const remoteAccount = document.getElementById("remote-account");
  if (remoteAccount) {
    remoteAccount.addEventListener("change", (e) => {
      state.selectedClient = state.clients.find((client) => client.id === e.target.value) || null;
      render();
    });
  }
  const statsAccount = document.getElementById("stats-account");
  if (statsAccount) {
    statsAccount.addEventListener("change", async (e) => {
      state.statsClientFilter = e.target.value || "all";
      await refreshLiveState();
      render();
    });
  }
  const statsScript = document.getElementById("stats-script");
  if (statsScript) {
    statsScript.addEventListener("change", async (e) => {
      state.statsScriptFilter = e.target.value || "all";
      await refreshLiveState();
      render();
    });
  }
  const remoteScript = document.getElementById("remote-script");
  if (remoteScript) {
    remoteScript.addEventListener("change", (e) => {
      state.selectedScript = state.scripts.find((script) => script.path === e.target.value) || null;
      render();
    });
  }
  const remoteChatInput = document.getElementById("remote-chat-input");
  if (remoteChatInput) {
    remoteChatInput.addEventListener("input", (e) => {
      state.remoteChatMessage = e.target.value;
    });
    remoteChatInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        handleAction("remote-chat-send");
      }
    });
  }
  const remoteDraftName = document.getElementById("remote-draft-name");
  if (remoteDraftName) {
    remoteDraftName.addEventListener("input", (e) => {
      state.remoteDraftName = e.target.value;
    });
  }
  const remoteDraftCode = document.getElementById("remote-draft-code");
  if (remoteDraftCode) {
    remoteDraftCode.addEventListener("input", (e) => {
      state.remoteDraftCode = e.target.value;
    });
  }
  const hubSearchInput = document.getElementById("hub-search");
  if (hubSearchInput) {
    hubSearchInput.addEventListener("input", (e) => {
      state.hubSearch = e.target.value;
      state.hubPage = 1;
      render();
    });
  }
  const globalSearchInput = document.getElementById("search");
  if (globalSearchInput) {
    const update = () => updateGlobalSearchResults(globalSearchInput.value);
    globalSearchInput.addEventListener("input", update);
    globalSearchInput.addEventListener("focus", update);
    globalSearchInput.addEventListener("keydown", event => {
      if (event.key !== "Escape") return;
      globalSearchInput.value = "";
      state.search = "";
      update();
      globalSearchInput.blur();
    });
    if (state.search) update();
  }
  if (state.page === "editor" && state.editorScript && document.getElementById("monaco-editor")) {
    initMonacoEditor();
  }
}

function bindLanding() {
  document.querySelectorAll("[data-auth]").forEach((node) => {
    node.addEventListener("click", () => {
      state.auth.view = node.dataset.auth;
      render();
    });
  });
  document.querySelectorAll("[data-view]").forEach((node) => {
    node.addEventListener("click", (e) => {
      e.preventDefault();
      state.auth.view = node.dataset.view;
      render();
    });
  });
  document.querySelectorAll("[data-google-auth]").forEach((node) => {
    node.addEventListener("click", () => {
      window.location.href = `${API_BASE}/auth/google`;
    });
  });
  document.querySelectorAll("[data-auth-form]").forEach((form) => {
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      handleAuthSubmit(form.dataset.authForm);
    });
  });
  initLandingAnimations();
}

function initLandingAnimations() {
  if (typeof gsap === "undefined" || typeof ScrollTrigger === "undefined") return;
  if (!document.querySelector(".landing")) return;
  gsap.registerPlugin(ScrollTrigger, ScrollToPlugin);
  ScrollTrigger.getAll().forEach((t) => t.kill());
  gsap.fromTo(".landing-navbar", { y: -40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.8, ease: "power3.out" });
  ScrollTrigger.create({ start: "top -80", end: 99999, toggleClass: { className: "scrolled", targets: ".landing-navbar" } });
  gsap.fromTo(".hero-tag", { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.8, delay: 0.2, ease: "power3.out" });
  gsap.fromTo(".hero-title .title-line", { y: 60, opacity: 0 }, { y: 0, opacity: 1, duration: 1, stagger: 0.15, delay: 0.35, ease: "power3.out" });
  gsap.fromTo(".hero-subtitle", { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.8, delay: 0.7, ease: "power3.out" });
  gsap.fromTo(".feature-badges .badge", { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, stagger: 0.1, delay: 0.9, ease: "power3.out" });
  gsap.fromTo(".release-card", { x: 60, opacity: 0 }, { x: 0, opacity: 1, duration: 1, delay: 1.1, ease: "back.out(1.2)" });
  gsap.fromTo(".hero-footer", { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.8, delay: 1.35, ease: "power3.out" });
  gsap.utils.toArray(".landing-section").forEach((section) => {
    gsap.fromTo(section.querySelectorAll(".section-header, .about-grid > *, .features-grid > *, .stats-grid > *, .community-grid > *, .download-grid > *"),
      { y: 50, opacity: 0 },
      {
        y: 0, opacity: 1, duration: 0.8, stagger: 0.1, ease: "power3.out",
        scrollTrigger: { trigger: section, start: "top 80%", toggleActions: "play none none reverse" }
      }
    );
  });
  gsap.to(".shard", {
    y: (i) => (i + 1) * 70,
    rotation: (i) => (i % 2 === 0 ? 8 : -8),
    ease: "none",
    scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: 1.2 }
  });
  gsap.to(".mountain-glow", {
    y: 120, opacity: 0.25, scale: 1.1, ease: "none",
    scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: 1 }
  });
  gsap.to(".showcase-frame", {
    rotateY: 8, rotateX: -4, y: -40, ease: "none",
    scrollTrigger: { trigger: ".showcase-section", start: "top bottom", end: "bottom top", scrub: 1.5 }
  });
  gsap.to(".icons-cluster-1", {
    y: -120, rotateY: -25, ease: "none",
    scrollTrigger: { trigger: ".showcase-section", start: "top bottom", end: "bottom top", scrub: 1.2 }
  });
  gsap.to(".icons-cluster-2", {
    y: -80, rotateY: 25, ease: "none",
    scrollTrigger: { trigger: ".showcase-section", start: "top bottom", end: "bottom top", scrub: 1.2 }
  });
  const showcaseFrame = document.querySelector(".showcase-frame");
  const showcaseSection = document.querySelector(".showcase-section");
  if (showcaseFrame && showcaseSection && window.matchMedia("(pointer: fine)").matches) {
    showcaseSection.addEventListener("mousemove", (e) => {
      const rect = showcaseSection.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      gsap.to(showcaseFrame, { rotateY: x * 12, rotateX: -y * 12, duration: 0.6, ease: "power2.out", overwrite: "auto" });
    });
    showcaseSection.addEventListener("mouseleave", () => {
      gsap.to(showcaseFrame, { rotateY: 0, rotateX: 0, duration: 0.8, ease: "power2.out" });
    });
  }
  document.querySelectorAll(".stat-number").forEach((numberEl, i) => {
    const target = parseFloat(numberEl.dataset.target);
    const isDecimal = target % 1 !== 0;
    gsap.to(numberEl, {
      innerText: target,
      duration: 2,
      ease: "power2.out",
      delay: i * 0.1,
      snap: { innerText: isDecimal ? 0.1 : 1 },
      scrollTrigger: { trigger: ".stats-grid", start: "top 85%", toggleActions: "play none none reverse" },
      onUpdate: function() {
        const val = parseFloat(numberEl.innerText);
        numberEl.innerText = isDecimal ? val.toFixed(1) : Math.floor(val);
      }
    });
  });
  document.querySelectorAll('.landing-nav-links a[href^="#"], .footer-links a[href^="#"]').forEach((link) => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      const target = document.querySelector(link.getAttribute("href"));
      if (target) gsap.to(window, { duration: 0.8, scrollTo: { y: target, offsetY: 80 }, ease: "power2.out" });
    });
  });
}

async function handleAuthSubmit(mode) {
  const email = document.querySelector("#auth-email")?.value.trim();
  const password = document.querySelector("#auth-password")?.value;
  const password2 = document.querySelector("#auth-password2")?.value;
  const displayName = document.querySelector("#auth-name")?.value.trim();
  if (!email || !password) return toast("Please fill in all fields");
  if (mode === "signup" && password !== password2) return toast("Passwords do not match");
  try {
    const endpoint = mode === "signin" ? "/api/auth/local/signin" : "/api/auth/local/signup";
    const body = mode === "signin" ? { email, password } : { email, password, displayName };
    const result = await api(endpoint, { method: "POST", body: JSON.stringify(body) });
    saveAuth({
      id: result.user.id,
      displayName: result.user.displayName,
      username: result.user.username,
      email: result.user.email,
      tier: result.user.tier,
      isAdmin: Boolean(result.user.isAdmin),
      features: Array.isArray(result.user.features) ? result.user.features : []
    });
    flowStore.configure({ api, userId: state.auth.user.id });
    toast(mode === "signin" ? "Welcome back." : "Account created.");
    await refreshAll();
    if (state.auth.user?.isAdmin) state.page = "admin";
    ensureLightRefresh();
    render();
  } catch (error) {
    toast(error.message || "Authentication failed");
  }
}

function bindGlobalShortcuts() {
  if (keybindingsBound) return;
  keybindingsBound = true;
  window.addEventListener("keydown", (e) => {
    const target = e.target;
    const typing = target instanceof HTMLElement && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable);
    if (e.key === "Escape" && state.modal) {
      state.modal = null;
      render();
      return;
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      const focusTarget = state.page === "scripts"
        ? document.getElementById("script-search")
        : document.getElementById("search")
          || document.getElementById("hub-search");
      focusTarget?.focus();
      focusTarget?.select?.();
      return;
    }
    if (typing) return;
    if (state.page === "remote" && e.key.toLowerCase() === "r" && state.selectedClient && state.selectedScript) {
      handleAction("remote-run");
    }
  });
}

async function handleAction(action) {
  try {
    if (action === "refresh") return refreshAll();
    if (action === "save-api") return saveApiBase();
    if (action === "notifications") {
      state.notificationsOpen = !state.notificationsOpen;
      return render();
    }
    if (action === "notifications-read") {
      for (const item of notificationItems()) state.notificationRead[item.id] = Date.now();
      localStorage.setItem("shulkr_notification_read", JSON.stringify(state.notificationRead));
      return render();
    }
    if (action === "export-analytics") return exportAnalyticsCsv();
    if (action === "logout") {
      try { await api("/api/auth/logout", { method: "POST", body: JSON.stringify({}) }); } catch {}
      clearAuth();
      return render();
    }
    if (action === "new-script") return createNewScript();
    if (action === "new-folder") return createScriptFolder();
    if (action === "save-script") return saveCurrentScript();
    if (action === "run-script") return runCurrentScript();
    if (action === "checkout-pro") return startCheckout("pro");
    if (action === "checkout-premium") return startCheckout("premium");
    if (action === "open-billing-portal") return openBillingPortal();
    if (action === "remote-run") return runRemoteScript();
    if (action === "remote-stop") return stopRemoteScripts();
    if (action === "remote-capture") return captureRemoteClient();
    if (action === "remote-stream-restart") return restartRemoteStream();
    if (action === "remote-chat-send") return sendRemoteChat();
    if (action === "remote-script-save") return saveRemoteQuickScript(false);
    if (action === "remote-script-save-run") return saveRemoteQuickScript(true);
    if (action === "publish-script") return publishHubScript();
  } catch (error) {
    toast(error.message || "Action failed");
  }
}

async function openScriptEditor(scriptPath) {
  const script = state.scripts.find((s) => s.path === scriptPath);
  if (!script) return;
  state.editorScript = script;
  state.editorContent = "";
  state.editorDirty = false;
  state.page = "editor";
  render();
  try {
    const result = await api(`/api/scripts/read?path=${encodeURIComponent(scriptPath)}`);
    state.editorContent = result.content || "";
    state.editorDirty = false;
    render();
  } catch (error) {
    toast(error.message || "Failed to load script");
  }
}

function exportAnalyticsCsv() {
  if (!state.statsHistory?.length) {
    toast("There is no analytics history to export yet.");
    return;
  }
  const columns = [
    ["Timestamp", "at"],
    ["Runtime seconds", "runtimeSeconds"],
    ["Active script seconds", "activeScriptSeconds"],
    ["Average FPS", "avgFps"],
    ["Script runs", "scriptRuns"],
    ["Commands completed", "commandsCompleted"],
    ["Chat messages", "chatMessages"],
    ["Screenshots", "screenshots"],
    ["Peak clients", "activeClientsPeak"]
  ];
  const csvCell = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const rows = [
    columns.map(([label]) => csvCell(label)).join(","),
    ...state.statsHistory.map((point) => columns.map(([, key]) => csvCell(
      key === "at" ? new Date(point[key] || Date.now()).toISOString() : Number(point[key] || 0)
    )).join(","))
  ];
  const blob = new Blob([`\uFEFF${rows.join("\r\n")}`], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `shulkr-analytics-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  toast("Analytics exported.");
}


async function startCheckout(tier) {
  const result = await api("/api/billing/checkout", {
    method: "POST",
    body: JSON.stringify({ tier })
  });
  if (!result?.url) throw new Error("Stripe checkout URL was not returned");
  window.location.href = result.url;
}

async function openBillingPortal() {
  const result = await api("/api/billing/portal", {
    method: "POST",
    body: JSON.stringify({})
  });
  if (!result?.url) throw new Error("Billing portal URL was not returned");
  window.location.href = result.url;
}

async function installHubScript(id) {
  try {
    const installed = await api(`/api/library/scripts/${encodeURIComponent(id)}/install`, { method: "POST" });
    if (installed?.graph) {
      flowStore.setGraph(installed.graph, { dirty: false });
      state.page = "flow";
      state.modal = null;
      render();
      toast("Automation imported privately; review it before running");
      return;
    }
    await refreshAll();
    if (state.modal?.type === "hub-details" && state.modal.script?.id === id) {
      state.modal = {
        ...state.modal,
        installedPath: installed?.path || state.modal.installedPath || ""
      };
      render();
    }
    toast("Script installed");
  } catch (error) {
    toast(error.message || "Failed to install script");
  }
}

async function viewHubScript(id) {
  try {
    const script = await api(`/api/library/scripts/${encodeURIComponent(id)}`);
    if (script.kind === "automation") {
      state.modal = { type: "hub-details", script };
      render();
      return;
    }
    state.editorScript = { path: script.fileName || script.id, name: script.fileName || script.name, ...script };
    state.editorContent = script.code || "";
    state.editorDirty = false;
    state.page = "editor";
    render();
  } catch (error) {
    toast(error.message || "Failed to load script");
  }
}

async function publishHubScript() {
  state.modal = {
    type: "publish-script",
    values: {
      name: "",
      category: "Utility",
      about: "",
      code: "# New Shulkr script\nimport minescript as ms\n\nms.echo(\"Hello from Shulkr!\")\n"
    }
  };
  render();
}

async function submitPublishHubScript() {
  const form = document.getElementById("publish-script-form");
  if (!form) return;
  const formData = new FormData(form);
  const name = String(formData.get("name") || "").trim();
  const category = String(formData.get("category") || "Utility").trim();
  const about = String(formData.get("about") || "").trim();
  const code = String(formData.get("code") || "");
  if (!name) {
    toast("Script name is required");
    return;
  }
  try {
    await api("/api/library/scripts", {
      method: "POST",
      body: JSON.stringify({
        name,
        code,
        about,
        category: category || scriptCategoryFromName(name),
        author: state.auth.user?.displayName || "Shulkr user"
      })
    });
    await refreshAll();
    state.modal = null;
    toast("Script published");
  } catch (error) {
    toast(error.message || "Failed to publish script");
  }
}

function openHubDetails(id) {
  const script = getHubScripts().find((item) => item.id === id);
  if (!script) return;
  state.modal = { type: "hub-details", script, installedPath: "" };
  render();
}

function renderModal() {
  if (state.modal?.type === "publish-script") {
    return publishScriptModal();
  }
  if (state.modal?.type === "hub-details") {
    return hubDetailsModal(state.modal.script, state.modal.installedPath);
  }
  return "";
}

function publishScriptModal() {
  const values = state.modal?.values || {};
  return `
    <div class="modal-backdrop" data-modal-close>
      <div class="modal-card modal-lg">
        <div class="card-header">
          <h3><i class="fa-solid fa-cloud-arrow-up"></i> Publish Script</h3>
          <button class="btn-icon" type="button" data-modal-close title="Close"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <form id="publish-script-form" class="modal-form">
          <div class="form-group">
            <label>Script Name</label>
            <input name="name" value="${escapeAttr(values.name || "")}" placeholder="Camera Controller" />
          </div>
          <div class="form-group">
            <label>Category</label>
            <select name="category">
              ${["Utility", "Python", "Pyjinn", "Farming", "Combat", "World", "Other"].map((category) => `<option value="${escapeAttr(category)}" ${values.category === category ? "selected" : ""}>${escapeHtml(category)}</option>`).join("")}
            </select>
          </div>
          <div class="form-group">
            <label>Description</label>
            <textarea name="about" rows="3" placeholder="What does this script do?">${escapeHtml(values.about || "")}</textarea>
          </div>
          <div class="form-group">
            <label>Code</label>
            <textarea name="code" rows="14" placeholder="Paste your script code here">${escapeHtml(values.code || "")}</textarea>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
            <button type="submit" class="btn btn-primary"><i class="fa-solid fa-cloud-arrow-up"></i> Publish</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

function hubDetailsModal(script, installedPath = "") {
  if (!script) return "";
  const automation = script.kind === "automation";
  return `
    <div class="modal-backdrop" data-modal-close>
      <div class="modal-card modal-lg">
        <div class="card-header">
          <h3><i class="fa-solid fa-circle-info"></i> ${escapeHtml(script.name || "Script Details")}</h3>
          <button class="btn-icon" type="button" data-modal-close title="Close"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <div class="modal-meta-grid">
          <div class="stat-card">
            <div class="label"><i class="fa-solid fa-user"></i> Author</div>
            <div class="value modal-stat">${escapeHtml(script.author || "Shulkr user")}</div>
          </div>
          ${automation ? `<div class="stat-card"><div class="label"><i class="fa-solid fa-diagram-project"></i> Graph</div><div class="value modal-stat">${script.nodeCount || 0} nodes · ${script.edgeCount || 0} edges</div></div>` : ""}
          <div class="stat-card">
            <div class="label"><i class="fa-solid fa-layer-group"></i> Category</div>
            <div class="value modal-stat">${escapeHtml(script.category || "Other")}</div>
          </div>
          <div class="stat-card">
            <div class="label"><i class="fa-solid fa-download"></i> Downloads</div>
            <div class="value modal-stat">${escapeHtml(formatNumber(script.downloads || 0))}</div>
          </div>
          <div class="stat-card"><div class="label"><i class="fa-solid fa-shield-halved"></i> Verification</div><div class="value modal-stat">${script.verification?.serverCompiled ? "Graph validated and compiled by Shulkr" : "Source code requires review before running"}</div></div>
        </div>
        <div class="form-group">
          <label>Description</label>
          <div class="modal-code-preview">${escapeHtml(script.about || "No description yet.")}</div>
        </div>
        <div class="form-group">
          <label>${automation ? "Required permissions" : "Install Path"}</label>
          <div class="modal-code-preview">${automation ? escapeHtml((script.requiredPermissions || []).join(", ") || "None") : `<code>${escapeHtml(script.fileName || script.name || "script.py")}</code>${installedPath ? `<br><span class="modal-note">Installed as ${escapeHtml(installedPath)}</span>` : ""}`}</div>
        </div>
        <div class="form-group">
          <label>${automation ? "Compatibility and changelog" : "Import Example"}</label>
          <div class="modal-code-preview">${automation ? `${escapeHtml((script.supportedMinecraftVersions || []).join(", "))} · client ${escapeHtml(script.supportedClientVersion || "1.0.0")}<br>${escapeHtml(script.changelog || "Initial version")}` : `<code>${escapeHtml(importExample(script.fileName || script.name || ""))}</code>`}</div>
        </div>
        <div class="modal-actions">
          ${automation ? `<button class="btn btn-secondary" data-modal-open-editor="${escapeAttr(script.id)}"><i class="fa-solid fa-code"></i> Review Graph</button>` : `<button class="btn btn-secondary" data-modal-open-editor="${escapeAttr(script.id)}"><i class="fa-solid fa-code"></i> View Code</button>`}
          <button class="btn btn-primary" data-modal-install="${escapeAttr(script.id)}"><i class="fa-solid fa-${automation ? "clone" : "download"}"></i> ${automation ? "Import Copy" : "Install"}</button>
        </div>
      </div>
    </div>
  `;
}

async function createNewScript() {
  const name = prompt("New script name (e.g. MyScript.py):", "NewScript.py");
  if (!name) return;
  try {
    const result = await api("/api/scripts", {
      method: "POST",
      body: JSON.stringify({ name, content: "# New Shulkr script\nimport minescript as ms\n\nms.echo(\"Hello from Shulkr!\")\n" })
    });
    await refreshAll();
    openScriptEditor(result.path);
    toast("Script created");
  } catch (error) {
    toast(error.message || "Failed to create script");
  }
}

async function createScriptFolder() {
  const name = prompt("New folder name:", "automation-scripts");
  if (!name) return;
  const folder = await api("/api/scripts/folders", { method: "POST", body: JSON.stringify({ name }) });
  await refreshLiveState();
  state.scriptFolder = folder.path;
  render();
  toast("Folder created");
}

async function renameManagedScript(scriptPath) {
  const currentName = String(scriptPath || "").split("/").pop();
  const name = prompt("Rename script:", currentName);
  if (!name || name === currentName) return;
  const renamed = await api("/api/scripts", { method: "PATCH", body: JSON.stringify({ path: scriptPath, name }) });
  if (state.editorScript?.path === scriptPath) state.editorScript = { ...state.editorScript, ...renamed };
  await refreshLiveState();
  render();
  toast(`Renamed to ${renamed.name}`);
}

async function deleteManagedScript(scriptPath) {
  if (!confirm(`Delete ${scriptPath}? This cannot be undone.`)) return;
  await api("/api/scripts", { method: "DELETE", body: JSON.stringify({ path: scriptPath }) });
  if (state.editorScript?.path === scriptPath) { state.editorScript = null; state.editorContent = ""; state.editorDirty = false; }
  await refreshLiveState();
  render();
  toast("Script deleted");
}

async function renameScriptFolder(folderPath) {
  const currentName = String(folderPath || "").split("/").pop();
  const name = prompt("Rename folder:", currentName);
  if (!name || name === currentName) return;
  const renamed = await api("/api/scripts/folders", { method: "PATCH", body: JSON.stringify({ path: folderPath, name }) });
  state.scriptFolder = renamed.path;
  await refreshLiveState();
  render();
  toast(`Renamed folder to ${renamed.name}`);
}

async function deleteScriptFolder(folderPath) {
  if (!confirm(`Delete ${folderPath} and every script inside it? This cannot be undone.`)) return;
  await api("/api/scripts/folders", { method: "DELETE", body: JSON.stringify({ path: folderPath }) });
  state.scriptFolder = "all";
  await refreshLiveState();
  render();
  toast("Folder deleted");
}

async function saveCurrentScript() {
  if (!state.editorScript) return;
  const content = state.editorContent;
  try {
    await api("/api/scripts", {
      method: "POST",
      body: JSON.stringify({ name: state.editorScript.path, content, overwrite: true })
    });
    state.editorDirty = false;
    await refreshAll();
    render();
    toast("Script saved");
  } catch (error) {
    toast(error.message || "Failed to save script");
  }
}

async function runCurrentScript() {
  if (!state.editorScript) return;
  try {
    await api("/api/control/commands", {
      method: "POST",
      body: JSON.stringify({
        clientId: state.selectedClient?.id || "local-user",
        type: "run_script",
        payload: { path: state.editorScript.path }
      })
    });
    toast("Script queued on client");
  } catch (error) {
    toast(error.message || "Failed to run script");
  }
}

async function runRemoteScript() {
  if (!state.selectedClient) throw new Error("Select a client first");
  if (!state.selectedScript) throw new Error("Select a script first");
  await api("/api/control/commands", {
    method: "POST",
    body: JSON.stringify({
      clientId: state.selectedClient.id,
      type: "run_script",
      payload: { path: state.selectedScript.path }
    })
  });
  toast(`Queued ${state.selectedScript.name || state.selectedScript.path}`);
  await refreshRemoteState();
  render();
}

async function stopRemoteScripts() {
  if (!state.selectedClient) throw new Error("Select a client first");
  await api("/api/control/commands", {
    method: "POST",
    body: JSON.stringify({
      clientId: state.selectedClient.id,
      type: "stop_scripts",
      payload: {}
    })
  });
  toast("Stop command queued");
}

async function captureRemoteClient() {
  if (!state.selectedClient) throw new Error("Select a client first");
  await api("/api/control/commands", {
    method: "POST",
    body: JSON.stringify({
      clientId: state.selectedClient.id,
      type: "take_screenshot",
      payload: {}
    })
  });
  toast("Capture command queued");
}

async function sendRemoteChat() {
  if (!state.selectedClient) throw new Error("Select a client first");
  const message = (state.remoteChatMessage || "").trim();
  if (!message) throw new Error("Type a chat message first");
  await api("/api/control/commands", {
    method: "POST",
    body: JSON.stringify({
      clientId: state.selectedClient.id,
      type: "send_chat",
      payload: { message }
    })
  });
  state.remoteChatMessage = "";
  render();
  toast("Chat sent to Minecraft");
}

async function saveRemoteQuickScript(runAfterSave) {
  const scriptName = (state.remoteDraftName || "").trim() || "QuickRemoteScript.py";
  const scriptCode = state.remoteDraftCode || "";
  const saved = await api("/api/scripts", {
    method: "POST",
    body: JSON.stringify({
      name: scriptName,
      content: scriptCode,
      overwrite: true
    })
  });
  await refreshRemoteState();
  state.selectedScript = state.scripts.find((script) => script.path === saved.path) || saved;
  if (runAfterSave) {
    if (!state.selectedClient) throw new Error("Select a client first");
    await api("/api/control/commands", {
      method: "POST",
      body: JSON.stringify({
        clientId: state.selectedClient.id,
        type: "run_script",
        payload: { path: saved.path }
      })
    });
    toast("Quick script saved and queued");
    return;
  }
  toast("Quick script saved");
}

async function ensureRemoteStream({ start = false } = {}) {
  if (!state.online || !state.selectedClient?.id) return;
  const current = state.stream || await api("/api/stream/status");
  state.stream = current;
  if (!current?.available) return;
  const clientId = state.selectedClient.id;
  if (current?.running) {
    await api("/api/stream/session", { method: "POST", body: JSON.stringify({ clientId }) });
    return;
  }
  if (!start) return;
  const started = await api("/api/stream/start", {
    method: "POST",
    body: JSON.stringify({
      clientId,
      mode: "window",
      fps: 15,
      title: "Minecraft"
    })
  });
  state.stream = started || current;
}

let monacoEditor = null;
let monacoInitializing = false;

function initMonacoEditor() {
  const container = document.getElementById("monaco-editor");
  if (!container || typeof require === "undefined") return;
  const existingDomNode = monacoEditor && monacoEditor.getDomNode && monacoEditor.getDomNode();
  if (monacoEditor && (!existingDomNode || !document.body.contains(existingDomNode))) {
    try { monacoEditor.dispose(); } catch {}
    monacoEditor = null;
  }
  if (monacoEditor) {
    if (monacoEditor.getValue() !== state.editorContent) {
      monacoEditor.setValue(state.editorContent);
    }
    state.editorScript.__original = state.editorContent;
    return;
  }
  if (monacoInitializing || container.dataset.monaco === "true") return;
  monacoInitializing = true;
  container.dataset.monaco = "true";
  require.config({ paths: { vs: "https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.0/min/vs" } });
  require(["vs/editor/editor.main"], () => {
    monacoInitializing = false;
    const currentContainer = document.getElementById("monaco-editor");
    if (!currentContainer || monacoEditor) return;
    const language = (state.editorScript?.path || "").toLowerCase().endsWith(".lua") ? "lua" : "python";
    monacoEditor = monaco.editor.create(currentContainer, {
      value: state.editorContent,
      language,
      theme: "vs-dark",
      automaticLayout: true,
      minimap: { enabled: true },
      fontSize: 14,
      fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
      lineNumbers: "on",
      scrollBeyondLastLine: false,
      roundedSelection: false,
      padding: { top: 16 },
      folding: true,
      renderLineHighlight: "all",
      matchBrackets: "always",
      tabSize: 4,
      insertSpaces: true
    });
    monacoEditor.onDidChangeModelContent(() => {
      state.editorContent = monacoEditor.getValue();
      state.editorDirty = state.editorContent !== state.editorScript?.__original;
      const dirtyIndicator = document.querySelector(".editor-dirty");
      if (dirtyIndicator) dirtyIndicator.style.display = state.editorDirty ? "inline" : "none";
      const saveBtn = document.querySelector('[data-action="save-script"]');
      if (saveBtn) saveBtn.disabled = !state.editorDirty;
    });
    state.editorScript.__original = state.editorContent;
  });
}

function saveApiBase() {
  const value = document.querySelector("#api-base")?.value.trim();
  if (!value) return;
  const normalized = safeApiBase(value);
  if (normalized !== value.replace(/\/$/, "")) return toast("Backend URL must use localhost or a loopback IP.");
  localStorage.setItem("shulkr_api_base", normalized);
  toast("Backend URL saved. Reloading...");
  setTimeout(() => location.reload(), 700);
}

function toast(message) {
  state.toast = message;
  render();
  setTimeout(() => {
    state.toast = "";
    render();
  }, 2200);
}

function empty(text) {
  return `<div class="empty-state" style="min-height: 120px;">${escapeHtml(text)}</div>`;
}

function importExample(fileName) {
  const moduleName = String(fileName || "script.py")
    .replace(/\.[^.]+$/, "")
    .replace(/[^a-zA-Z0-9_]/g, "_")
    .replace(/^[^a-zA-Z_]+/, "")
    || "script_library";
  return `import ${moduleName}`;
}

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(1)} KB`;
}

async function restartRemoteStream() {
  if (!state.selectedClient?.id) throw new Error("Select a connected client first");
  const clientId = state.selectedClient.id;
  try {
    await api("/api/stream/stop", { method: "POST", body: JSON.stringify({}) });
  } catch {
  }
  state.stream = await api("/api/stream/start", {
    method: "POST",
    body: JSON.stringify({ clientId, mode: "window", fps: 15, quality: 6, title: "Minecraft" })
  });
  await api("/api/stream/session", { method: "POST", body: JSON.stringify({ clientId }) });
  render();
  toast("Remote viewer restarted");
}

function formatMoney(amount, currency = "usd") {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: String(currency || "usd").toUpperCase(),
    minimumFractionDigits: 2
  }).format((Number(amount) || 0) / 100);
}

function formatNumber(num) {
  const n = Number(num) || 0;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function formatModified(value) {
  if (!value) return "-";
  if (typeof value === "string" && Number.isNaN(Number(value))) return value;
  const time = Number(value);
  if (!Number.isFinite(time)) return String(value);
  const seconds = Math.max(1, Math.round((Date.now() - time) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

function timeAgo(value) {
  return formatModified(value);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[char]);
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, "&#96;");
}
