import "./styles.css";

const API_BASE = localStorage.getItem("shulkr_api_base") || localStorage.getItem("shulk_api_base") || "http://127.0.0.1:50991";

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
  libraries: [],
  clientModules: [],
  templates: [],
  library: [],
  selectedScript: null,
  selectedClient: null,
  modal: null,
  toast: "",
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
  auth: loadAuth()
};

const nav = [
  ["profile", "Profile", "fa-solid fa-user", "Profile"],
  ["scripts", "Scripts", "fa-solid fa-file-code", "Scripts"],
  ["editor", "Editor", "fa-solid fa-code", "Editor"],
  ["statistics", "Analytics", "fa-solid fa-chart-pie", "Analytics"],
  ["accounts", "Accounts", "fa-solid fa-users", "Accounts"],
  ["remote", "Remote", "fa-solid fa-satellite-dish", "Remote"],
  ["settings", "Settings", "fa-solid fa-sliders", "Settings"]
];

const app = document.querySelector("#app");

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
    const urlToken = params.get("token");
    if (urlToken) {
      localStorage.setItem("shulkr_token", urlToken);
      params.delete("token");
      params.delete("name");
      params.delete("email");
      const cleanSearch = params.toString();
      window.history.replaceState({}, document.title, window.location.pathname + (cleanSearch ? `?${cleanSearch}` : ""));
    }
    const token = localStorage.getItem("shulkr_token");
    if (token) {
      const payload = JSON.parse(atob(token.split(".")[1]));
      if (payload.exp * 1000 > Date.now()) {
        return { loggedIn: true, view: "dashboard", user: { id: payload.id, displayName: payload.displayName, email: payload.email }, token };
      }
    }
  } catch (error) {
    console.error("loadAuth error", error);
  }
  localStorage.removeItem("shulkr_token");
  return { loggedIn: false, view: "landing", user: null, token: null };
}

function saveAuth(user, token) {
  localStorage.setItem("shulkr_token", token);
  state.auth = { loggedIn: true, view: "dashboard", user, token };
}

function clearAuth() {
  localStorage.removeItem("shulkr_token");
  state.auth = { loggedIn: false, view: "landing", user: null, token: null };
}

async function init() {
  render();
  try {
    const health = await api("/api/health");
    state.online = Boolean(health.ok);
  } catch {
    state.online = false;
  }
  render();
  if (state.auth.loggedIn) {
    try {
      const me = await api("/api/auth/me");
      state.auth.user = { id: me.id, displayName: me.displayName, email: me.email, username: me.username, tier: me.tier, avatar: me.avatar };
    } catch {
      clearAuth();
      render();
      return;
    }
    await refreshAll();
    setInterval(refreshLight, 10000);
  }
}

async function refreshAll() {
  state.loading = true;
  state.error = "";
  render();
  try {
    const [health, snapshot, library, libraries, clientModules, clients, statsHistory, statsSummary] = await Promise.all([
      api("/api/health"),
      api("/api/snapshot"),
      api("/api/library/scripts"),
      api("/api/libraries"),
      api("/api/client-modules"),
      api("/api/clients"),
      api("/api/stats/history"),
      api("/api/stats/summary")
    ]);
    state.online = Boolean(health.ok);
    state.health = health;
    state.profile = snapshot.profile;
    state.stats = snapshot.stats;
    state.clients = clients || [];
    state.scripts = snapshot.scripts || [];
    state.libraries = libraries || [];
    state.clientModules = clientModules || [];
    state.templates = snapshot.templates || [];
    state.library = library || [];
    state.statsHistory = Array.isArray(statsHistory) ? statsHistory : [];
    state.statsSummary = statsSummary || null;
    state.selectedScript = state.scripts[0] || null;
    state.selectedClient = state.clients[0] || null;
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
    if (state.online && state.error) await refreshAll();
    else render();
  } catch {
    state.online = false;
    state.error = "Server is offline";
    render();
  }
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.auth.token) headers.Authorization = `Bearer ${state.auth.token}`;
  const response = await fetch(API_BASE + path, {
    headers,
    ...options
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { error: text || "Backend returned invalid JSON" };
  }
  if (!response.ok) throw new Error(body?.error || response.statusText || "Backend error");
  return body;
}

function render() {
  if (!state.auth.loggedIn) {
    app.innerHTML = `
      ${state.auth.view === "signin" ? signInPage() : state.auth.view === "signup" ? signUpPage() : landingPage()}
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
      </main>
    </div>
    ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
  `;
  bind();
}

function sidebar() {
  const user = state.auth.user || state.profile || { displayName: "EnderUser" };
  return `
    <aside class="sidebar">
      <a href="#" class="brand-icon" data-landing title="Back to landing page">S</a>
      <nav class="nav-rail">
        ${nav.map(([id, label, icon, tip]) => `
          <button class="nav-item ${state.page === id ? "active" : ""}" data-page="${id}" data-testid="nav-${id}" aria-label="${label}">
            <i class="${icon}"></i>
            <span class="nav-tooltip">${tip}</span>
          </button>
        `).join("")}
      </nav>
      <div class="sidebar-footer">
        <button class="btn-icon" title="Notifications"><i class="fa-solid fa-bell"></i></button>
        <button class="user-avatar" data-action="logout" title="Log out">${escapeHtml((user.displayName || "U").slice(0, 1))}</button>
      </div>
    </aside>
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
            <a href="#" class="social-icon" aria-label="Discord"><i class="fa-brands fa-discord"></i></a>
            <a href="#" class="social-icon" aria-label="X / Twitter"><i class="fa-brands fa-x-twitter"></i></a>
            <a href="#" class="social-icon" aria-label="YouTube"><i class="fa-brands fa-youtube"></i></a>
            <a href="#" class="social-icon add-icon" aria-label="More"><i class="fa-solid fa-plus"></i></a>
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
              <button class="btn btn-secondary" data-auth="signup"><span>VIEW CHANGELOG</span><i class="fa-solid fa-arrow-right"></i></button>
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
              <img src="/main.png" alt="Shulkr Client Interface" class="showcase-image" onerror="this.style.display='none'">
              <div class="showcase-glow"></div>
            </div>
            <div class="floating-icons">
              <img src="/shulkr-icons.png" alt="Shulkr Icons" class="icons-cluster icons-cluster-1" onerror="this.style.display='none'">
              <img src="/shulkr-icons.png" alt="Shulkr Icons" class="icons-cluster icons-cluster-2" onerror="this.style.display='none'">
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
            <button class="btn btn-primary" data-auth="signup"><span>JOIN OUR DISCORD</span><i class="fa-solid fa-arrow-right"></i></button>
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
          ${mode === "signup" ? `<div class="form-group"><label>Display name</label><input type="text" id="auth-name" placeholder="Your name" required value="Admin" /></div>` : ""}
          <div class="form-group">
            <label>Email</label>
            <input type="email" id="auth-email" placeholder="you@example.com" required value="admin@shulkr.local" />
          </div>
          <div class="form-group">
            <label>Password</label>
            <input type="password" id="auth-password" placeholder="••••••••" required value="admin" />
          </div>
          ${mode === "signup" ? `<div class="form-group"><label>Confirm Password</label><input type="password" id="auth-password2" placeholder="••••••••" required value="admin" /></div>` : ""}
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

function page() {
  if (state.loading) return loadingState();
  return {
    profile: profilePage,
    scripts: scriptsPage,
    editor: editorPage,
    statistics: statisticsPage,
    accounts: accountsPage,
    remote: remotePage,
    settings: settingsPage
  }[state.page]?.() || profilePage();
}

function topBar(title, icon) {
  return `
    <header class="top-bar">
      <div class="page-title">
        <i class="${icon}"></i>
        <h1>${escapeHtml(title)}</h1>
      </div>
      <label class="global-search" data-testid="top-search">
        <i class="fa-solid fa-magnifying-glass"></i>
        <input id="search" value="${escapeAttr(state.search)}" placeholder="Search scripts, accounts..." />
        <kbd>Ctrl</kbd><kbd>K</kbd>
      </label>
      <div class="top-actions">
        <button class="btn-icon" title="Refresh" data-action="refresh"><i class="fa-solid fa-rotate"></i></button>
        <span class="badge ${state.online ? "badge-good" : "badge-active"}" style="height: 32px;">${state.online ? "Online" : "Offline"}</span>
      </div>
    </header>
  `;
}

function profilePage() {
  const profile = state.profile || { displayName: "EnderUser", tier: "Premium" };
  return `
    ${topBar("Overview", "fa-solid fa-house")}
    <section class="profile-hero">
      <div class="card profile-main">
        <div class="profile-info">
          <div class="avatar-xl">${escapeHtml((profile.displayName || "U").slice(0, 1))}</div>
          <div class="profile-name">
            <h2>${escapeHtml(profile.displayName || "EnderUser")}</h2>
            <p>@${escapeHtml(profile.username || profile.minecraft || "shulkr_user")}</p>
            <span class="badge badge-premium">${escapeHtml(profile.tier || "Premium")}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="btn btn-secondary" data-page="settings"><i class="fa-solid fa-gear"></i> Settings</button>
          <button class="btn btn-primary"><i class="fa-solid fa-download"></i> Download</button>
        </div>
      </div>
      <div class="card profile-meta">
        <div class="meta-row"><span>Plan</span><span>${escapeHtml(profile.tier || "Premium")}</span></div>
        <div class="meta-row"><span>Last active</span><span>Just now</span></div>
        <div class="meta-row"><span>Member since</span><span>November 2025</span></div>
        <div class="meta-row"><span>Connections</span><span>${state.clients.length} accounts</span></div>
      </div>
    </section>

    <section class="stats-row">
      ${statCard("Scripts", state.stats?.scripts ?? state.scripts.length, "fa-solid fa-file-code", "var(--accent-purple)")}
      ${statCard("Templates", state.stats?.templates ?? state.templates.length, "fa-solid fa-layer-group", "var(--accent-pink)")}
      ${statCard("Libraries", state.stats?.installedLibraries ?? state.libraries.length, "fa-solid fa-cubes", "var(--accent-cyan)")}
      ${statCard("Published", state.stats?.publishedScripts ?? state.library.length, "fa-solid fa-cloud-arrow-up", "var(--accent-orange)")}
    </section>

    <div class="grid-2">
      <div class="card chart-card">
        <div class="card-header">
          <h3><i class="fa-solid fa-wave-square"></i> Activity Pulse</h3>
          <button class="card-link">Details <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="chart-area">
          ${areaChart([3, 5, 4, 7, 6, 9, 11, 10, 13, 15, 14, 18], "var(--accent-purple)")}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-clock-rotate-left"></i> Recent Activity</h3>
          <button class="card-link" data-page="statistics">All <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="list">
          ${state.scripts.slice(0, 5).map(activityItem).join("") || empty("No recent activity.")}
        </div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-server"></i> Connected Accounts</h3>
          <button class="card-link" data-page="accounts">Manage <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="list">
          ${state.clients.length ? state.clients.slice(0, 4).map(clientListItem).join("") : clientListItem({ displayName: "EnderUser", tier: "Premium", connected: state.online, source: "profile" })}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-bolt"></i> Quick Launch</h3>
        </div>
        <div class="list">
          ${state.scripts.slice(0, 4).map(quickLaunchItem).join("") || empty("No scripts available.")}
        </div>
      </div>
    </div>
  `;
}

function scriptsPage() {
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

  const categories = ["all", "recent", "python", "pyjinn", "farming", "combat", "world", "utility", "other"];

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
            <button class="btn btn-primary btn-icon" data-action="publish-script" title="Publish script"><i class="fa-solid fa-cloud-arrow-up"></i></button>
            <button class="btn btn-secondary btn-icon" data-action="refresh" title="Refresh"><i class="fa-solid fa-rotate"></i></button>
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
  const iconClass = ext === "py" ? "fa-brands fa-python" : ext === "lua" ? "fa-solid fa-moon" : ext === "js" ? "fa-brands fa-js" : "fa-solid fa-code";
  const selected = state.selectedScript?.id === script.id ? "selected" : "";
  return `
    <div class="hub-card ${selected}" data-hub-script="${escapeAttr(script.id)}">
      <div class="hub-card-thumb">
        <div class="hub-thumb-icon"><i class="${iconClass}"></i></div>
        ${script.badge ? `<span class="hub-card-badge">${escapeHtml(script.badge)}</span>` : ""}
      </div>
      <div class="hub-card-body">
        <div class="hub-card-title">
          <h4>${name}</h4>
          <span class="hub-card-tag">${category}</span>
        </div>
        <div class="hub-card-meta">
          <span>by ${author}</span>
          <span>•</span>
          <span>v${escapeHtml(script.version || "1.0.0")}</span>
          <span>•</span>
          <span><i class="fa-solid fa-download"></i> ${formatNumber(script.downloads || 0)}</span>
        </div>
        <p class="hub-card-desc">${about}</p>
        <div class="hub-card-tags">
          ${tags.map((t) => `<span class="hub-tag">${escapeHtml(t)}</span>`).join("")}
        </div>
      </div>
      <div class="hub-card-actions">
        <button class="btn btn-primary btn-sm" data-hub-install="${escapeAttr(script.id)}" title="Install"><i class="fa-solid fa-download"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-view="${escapeAttr(script.id)}" title="View code"><i class="fa-solid fa-code"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-more="${escapeAttr(script.id)}" title="More"><i class="fa-solid fa-ellipsis"></i></button>
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
    const term = state.search.toLowerCase();
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
    <div class="script-item ${active}" data-select-script="${escapeAttr(script.path)}" data-select-name="${escapeAttr(name)}">
      <div class="script-icon"><i class="${icon}"></i></div>
      <div class="script-info">
        <h4>${name}</h4>
        <p>${formatBytes(script.size || 0)} · ${script.modifiedAt ? timeAgo(script.modifiedAt) : "Unknown"}</p>
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
  const history = getFilteredHistory();
  const metric = state.metricTab;
  const metricKey = metric === "time" ? "runtime" : metric === "starts" ? "runs" : metric === "failsafes" ? "failsafes" : metric === "exp" ? "exp" : "profit";
  const chartValues = history.length ? history.map((p) => p[metricKey] || 0) : [0];
  const chartColor = metric === "exp" ? "var(--accent-lime)" : metric === "profit" ? "var(--accent-orange)" : metric === "starts" ? "var(--accent-pink)" : metric === "failsafes" ? "var(--accent-cyan)" : "var(--accent-purple)";
  return `
    ${topBar("Analytics", "fa-solid fa-chart-pie")}
    <div class="toolbar">
      <div class="toolbar-left">
        <select id="stats-account"><option>All Accounts</option></select>
        <select id="stats-script"><option>All Scripts</option></select>
      </div>
      <div class="toolbar-right">
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
          ${[["time", "Time"], ["exp", "EXP"], ["profit", "Profit"], ["starts", "Runs"], ["failsafes", "Failsafes"]].map(([id, label]) => `
            <button class="pill-btn ${state.metricTab === id ? "active" : ""}" data-metric="${id}">${label}</button>
          `).join("")}
        </div>
      </div>
      <div class="chart-area" style="min-height: 280px;">
        ${areaChart(chartValues, chartColor)}
      </div>
    </div>

    <section class="stats-row">
      ${statCard("Runtime", formatDuration(summary.runtime || 0), "fa-solid fa-hourglass-half", "var(--accent-purple)")}
      ${statCard("Script Runs", formatNumber(summary.runs || 0), "fa-solid fa-play", "var(--accent-pink)")}
      ${statCard("Failsafes", formatNumber(summary.failsafes || 0), "fa-solid fa-shield-halved", "var(--accent-cyan)")}
      ${statCard("EXP Gained", formatNumber(summary.exp || 0), "fa-solid fa-star", "var(--accent-lime)")}
      ${statCard("Profit", formatNumber(summary.profit || 0), "fa-solid fa-coins", "var(--accent-orange)")}
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

function getFilteredHistory() {
  const history = state.statsHistory || [];
  const now = Date.now();
  const days = { "7d": 7, "30d": 30, "90d": 90, "1yr": 365, "All": 10000 }[state.timeFilter] || 30;
  const cutoff = now - days * 24 * 60 * 60 * 1000;
  return history.filter((p) => (p.at || 0) >= cutoff);
}

function sessionHistoryItem(point) {
  const date = new Date(point.at || Date.now()).toLocaleDateString(undefined, { month: "short", day: "numeric" });
  return `
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-calendar-day"></i></div>
      <div class="content">
        <h4>${date}</h4>
        <p>${formatDuration(point.runtime || 0)} • ${point.runs || 0} runs</p>
      </div>
      <span class="meta">${formatNumber(point.exp || 0)} EXP</span>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function formatDuration(minutes) {
  const m = Math.max(0, Math.round(Number(minutes) || 0));
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
    ${topBar("Accounts", "fa-solid fa-users")}
    <section class="accounts-grid">
      ${accounts.map(accountCard).join("")}
    </section>
  `;
}

function remotePage() {
  return `
    ${topBar("Remote", "fa-solid fa-satellite-dish")}
    <div class="remote-layout">
      <div>
        <div class="remote-screen">
          <div class="remote-placeholder">
            <i class="fa-solid fa-desktop"></i>
            <p>${state.selectedClient ? `Connected to ${escapeHtml(state.selectedClient.displayName || "account")}` : "Select an account to begin remote control"}</p>
          </div>
        </div>
        <div class="remote-controls">
          <select id="remote-account">
            ${state.clients.length ? state.clients.map((c) => `<option>${escapeHtml(c.displayName || "Account")}</option>`).join("") : `<option>EnderUser</option>`}
          </select>
          <select id="remote-script">
            ${state.scripts.length ? state.scripts.map((s) => `<option>${escapeHtml(s.name || s.fileName || s.path)}</option>`).join("") : `<option>No scripts</option>`}
          </select>
          <button class="btn btn-secondary"><i class="fa-solid fa-camera"></i> Capture</button>
          <button class="btn btn-primary" style="margin-left: auto;"><i class="fa-solid fa-play"></i> Run</button>
          <button class="btn btn-secondary"><i class="fa-solid fa-stop"></i> Stop</button>
        </div>
      </div>
      <div class="remote-sidebar">
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-heart-pulse"></i> Live Status</h3></div>
          <div class="panel-empty">
            <div>
              <i class="fa-solid fa-circle-notch fa-spin" style="font-size: 2rem; color: var(--accent-purple); margin-bottom: 12px;"></i>
              <p>${state.online ? "Backend connected" : "Waiting for connection..."}</p>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-terminal"></i> Event Log</h3></div>
          <div class="log-messages">
            <div class="log-message"><span class="time">[00:00:00]</span> Remote initialized.</div>
            ${state.online ? `<div class="log-message"><span class="time">[00:00:01]</span> Backend handshake complete.</div>` : ""}
          </div>
        </div>
      </div>
    </div>
  `;
}

function settingsPage() {
  return `
    ${topBar("Settings", "fa-solid fa-sliders")}
    <div class="settings-grid">
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-server"></i> Connection</h3></div>
        <div class="form-group">
          <label>Backend URL</label>
          <input id="api-base" value="${escapeAttr(API_BASE)}" />
        </div>
        <div style="display: flex; gap: 12px;">
          <button class="btn btn-primary" data-action="save-api"><i class="fa-solid fa-floppy-disk"></i> Save</button>
          <button class="btn btn-secondary" data-action="refresh"><i class="fa-solid fa-rotate"></i> Test</button>
        </div>
      </div>
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-palette"></i> Appearance</h3></div>
        <div class="form-group">
          <label>Theme</label>
          <select><option>Nova Dark</option><option>Midnight</option></select>
        </div>
        <div class="form-group">
          <label>Accent</label>
          <select><option>Neon Purple</option><option>Cyber Pink</option><option>Cyan</option></select>
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

function activityItem(script) {
  return `
    <div class="list-item" data-select-script="${escapeAttr(script.path)}">
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
    <div class="list-item" data-select-script="${escapeAttr(script.path)}">
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
  return `
    <div class="card account-card">
      <div class="avatar">${escapeHtml((client.displayName || "U").slice(0, 1))}</div>
      <div class="info">
        <h4>${escapeHtml(client.displayName || "EnderUser")}</h4>
        <p>${escapeHtml(client.tier || "Local user")}</p>
        <span class="account-status ${connected ? "" : "offline"}"><span class="dot"></span>${connected ? "Active now" : "Offline"}${client.minecraft ? ` • ${escapeHtml(client.minecraft)}` : ""}</span>
      </div>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `;
}

function areaChart(values, color = "var(--accent-purple)") {
  const width = 800;
  const height = 220;
  const max = Math.max(...values, 1);
  const min = Math.min(...values);
  const range = max - min || 1;
  const stepX = width / (values.length - 1);
  const points = values.map((v, i) => {
    const x = i * stepX;
    const y = height - ((v - min) / range) * (height - 40) - 20;
    return `${x},${y}`;
  });
  const area = `${points[0]} ${points.map((p) => p).join(" ")} ${width},${height} 0,${height}`;
  return `
    <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none">
      <defs>
        <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="${color}" stop-opacity="0.4"/>
          <stop offset="100%" stop-color="${color}" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <line class="chart-grid" x1="0" y1="${height / 2}" x2="${width}" y2="${height / 2}"/>
      <polygon class="chart-fill" points="${area}" fill="url(#areaGrad)"/>
      <polyline class="chart-line" points="${points.join(" ")}" style="stroke: ${color};"/>
    </svg>
  `;
}

function bind() {
  document.querySelectorAll("[data-page]").forEach((node) => {
    node.addEventListener("click", () => {
      state.page = node.dataset.page;
      render();
    });
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
  document.querySelectorAll("[data-filter]").forEach((node) => {
    node.addEventListener("click", () => {
      state.timeFilter = node.dataset.filter;
      render();
    });
  });
  document.querySelectorAll("[data-metric]").forEach((node) => {
    node.addEventListener("click", () => {
      state.metricTab = node.dataset.metric;
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
        render();
      }
    });
  });
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
  const hubSearchInput = document.getElementById("hub-search");
  if (hubSearchInput) {
    hubSearchInput.addEventListener("input", (e) => {
      state.hubSearch = e.target.value;
      state.hubPage = 1;
      render();
    });
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
    saveAuth({ id: result.user.id, displayName: result.user.displayName, email: result.user.email, tier: result.user.tier }, result.token);
    toast(mode === "signin" ? "Welcome back." : "Account created.");
    await refreshAll();
    setInterval(refreshLight, 10000);
    render();
  } catch (error) {
    toast(error.message || "Authentication failed");
  }
}

async function handleAction(action) {
  try {
    if (action === "refresh") return refreshAll();
    if (action === "save-api") return saveApiBase();
    if (action === "logout") {
      clearAuth();
      return render();
    }
    if (action === "new-script") return createNewScript();
    if (action === "save-script") return saveCurrentScript();
    if (action === "run-script") return runCurrentScript();
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

async function installHubScript(id) {
  try {
    await api(`/api/library/scripts/${encodeURIComponent(id)}/install`, { method: "POST" });
    await refreshAll();
    toast("Script installed");
  } catch (error) {
    toast(error.message || "Failed to install script");
  }
}

async function viewHubScript(id) {
  try {
    const script = await api(`/api/library/scripts/${encodeURIComponent(id)}`);
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
  const name = prompt("Script name:");
  if (!name) return;
  const code = prompt("Paste script code (or leave blank to publish a placeholder):", "# New Shulkr script\nimport minescript as ms\n\nms.echo(\"Hello from Shulkr!\")\n");
  if (code === null) return;
  try {
    await api("/api/library/scripts", {
      method: "POST",
      body: JSON.stringify({ name, code, category: scriptCategoryFromName(name), author: state.auth.user?.displayName || "Shulkr user" })
    });
    await refreshAll();
    toast("Script published");
  } catch (error) {
    toast(error.message || "Failed to publish script");
  }
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
      body: JSON.stringify({ type: "run_script", payload: { path: state.editorScript.path } })
    });
    toast("Script queued on client");
  } catch (error) {
    toast(error.message || "Failed to run script");
  }
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
  localStorage.setItem("shulkr_api_base", value);
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

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(1)} KB`;
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
