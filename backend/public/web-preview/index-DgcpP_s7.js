var e=(e=>typeof require<`u`?require:typeof Proxy<`u`?new Proxy(e,{get:(e,t)=>(typeof require<`u`?require:e)[t]}):e)(function(e){if(typeof require<`u`)return require.apply(this,arguments);throw Error('Calling `require` for "'+e+"\" in an environment that doesn't expose the `require` function. See https://rolldown.rs/in-depth/bundling-cjs#require-external-modules for more details.")});(function(){let e=document.createElement(`link`).relList;if(e&&e.supports&&e.supports(`modulepreload`))return;for(let e of document.querySelectorAll(`link[rel="modulepreload"]`))n(e);new MutationObserver(e=>{for(let t of e)if(t.type===`childList`)for(let e of t.addedNodes)e.tagName===`LINK`&&e.rel===`modulepreload`&&n(e)}).observe(document,{childList:!0,subtree:!0});function t(e){let t={};return e.integrity&&(t.integrity=e.integrity),e.referrerPolicy&&(t.referrerPolicy=e.referrerPolicy),e.crossOrigin===`use-credentials`?t.credentials=`include`:e.crossOrigin===`anonymous`?t.credentials=`omit`:t.credentials=`same-origin`,t}function n(e){if(e.ep)return;e.ep=!0;let n=t(e);fetch(e.href,n)}})();var t=localStorage.getItem(`shulkr_api_base`)||localStorage.getItem(`shulk_api_base`)||`http://127.0.0.1:50991`,n={page:`profile`,online:!1,loading:!0,error:``,health:null,profile:null,stats:null,clients:[],scripts:[],libraries:[],clientModules:[],templates:[],library:[],selectedScript:null,selectedClient:null,modal:null,toast:``,search:``,timeFilter:`30d`,metricTab:`time`,editorScript:null,editorContent:``,editorDirty:!1,hubSearch:``,hubCategory:`all`,hubSort:`recent`,hubPage:1,hubPerPage:12,hubFilters:{python:!0,pyjinn:!0,farming:!0,combat:!0,world:!0,utility:!0,other:!0},statsHistory:[],statsSummary:null,statsClients:[],statsScripts:[],statsClientFilter:`all`,statsScriptFilter:`all`,stream:null,remoteChatMessage:``,remoteDraftName:`QuickRemoteScript.py`,remoteDraftCode:`# Quick remote script
import minescript as ms

ms.echo("Remote script online")
`,auth:o()},r=null,i=[[`profile`,`Profile`,`fa-solid fa-user`,`Profile`],[`scripts`,`Scripts`,`fa-solid fa-file-code`,`Scripts`],[`editor`,`Editor`,`fa-solid fa-code`,`Editor`],[`statistics`,`Analytics`,`fa-solid fa-chart-pie`,`Analytics`],[`accounts`,`Accounts`,`fa-solid fa-users`,`Accounts`],[`remote`,`Remote`,`fa-solid fa-satellite-dish`,`Remote`],[`settings`,`Settings`,`fa-solid fa-sliders`,`Settings`]],a=document.querySelector(`#app`);l();function o(){try{let e=new URLSearchParams(window.location.search);if(e.get(`auth`)===`error`){e.delete(`auth`);let t=e.toString();window.history.replaceState({},document.title,window.location.pathname+(t?`?${t}`:``)),setTimeout(()=>J(`Google sign-in failed. Please try again.`),100)}let t=e.get(`token`);if(t){localStorage.setItem(`shulkr_token`,t),e.delete(`token`),e.delete(`name`),e.delete(`email`);let n=e.toString();window.history.replaceState({},document.title,window.location.pathname+(n?`?${n}`:``))}let n=localStorage.getItem(`shulkr_token`);if(n){let e=JSON.parse(atob(n.split(`.`)[1]));if(e.exp*1e3>Date.now())return{loggedIn:!0,view:`dashboard`,user:{id:e.id,displayName:e.displayName,email:e.email},token:n}}}catch(e){console.error(`loadAuth error`,e)}return localStorage.removeItem(`shulkr_token`),{loggedIn:!1,view:`landing`,user:null,token:null}}function s(e,t){localStorage.setItem(`shulkr_token`,t),n.auth={loggedIn:!0,view:`dashboard`,user:e,token:t}}function c(){localStorage.removeItem(`shulkr_token`),n.auth={loggedIn:!1,view:`landing`,user:null,token:null}}async function l(){_();try{n.online=!!(await g(`/api/health`)).ok}catch{n.online=!1}if(_(),n.auth.loggedIn){try{let e=await g(`/api/auth/me`);n.auth.user={id:e.id,displayName:e.displayName,email:e.email,username:e.username,tier:e.tier,avatar:e.avatar}}catch{c(),_();return}await u(),h()}}async function u(){n.loading=!0,n.error=``,_();try{let e=m(),[t,r,i,a,o,s,c,l]=await Promise.all([g(`/api/health`),g(`/api/snapshot`),g(`/api/library/scripts`),g(`/api/libraries`),g(`/api/client-modules`),g(`/api/clients`),g(e),g(`/api/stream/status`)]);n.online=!!t.ok,n.health=t,n.profile=r.profile,n.stats=r.stats,n.clients=s||[],n.scripts=r.scripts||[],n.libraries=a||[],n.clientModules=o||[],n.templates=r.templates||[],n.library=i||[],n.statsHistory=Array.isArray(c?.history)?c.history:[],n.statsSummary=c?.summary||null,n.statsClients=Array.isArray(c?.clients)?c.clients:[],n.statsScripts=Array.isArray(c?.scripts)?c.scripts:[],n.stream=l||null;let u=n.selectedScript?.path,d=n.selectedClient?.id;n.selectedScript=n.scripts.find(e=>e.path===u)||n.scripts[0]||null,n.selectedClient=n.clients.find(e=>e.id===d)||n.clients[0]||null,n.page===`remote`&&await W()}catch{n.online=!1,n.health=null,n.error=`Server is offline`}finally{n.loading=!1,_()}}async function d(){try{if(n.online=!!(await g(`/api/health`)).ok,!n.online){n.error=`Server is offline`,_();return}if(n.error){await u();return}await f(),_()}catch{n.online=!1,n.error=`Server is offline`,_()}}async function f(){let e=[g(`/api/clients`),g(`/api/scripts`),g(`/api/stream/status`)];n.page===`statistics`&&e.push(g(m()));let[t,r,i,a]=await Promise.all(e);n.clients=Array.isArray(t)?t:[],n.scripts=Array.isArray(r)?r:[],n.stream=i||null,a&&(n.statsHistory=Array.isArray(a.history)?a.history:[],n.statsSummary=a.summary||null,n.statsClients=Array.isArray(a.clients)?a.clients:[],n.statsScripts=Array.isArray(a.scripts)?a.scripts:[]);let o=n.selectedClient?.id,s=n.selectedScript?.path;n.selectedClient=n.clients.find(e=>e.id===o)||n.clients[0]||null,n.selectedScript=n.scripts.find(e=>e.path===s)||n.scripts[0]||null,n.page===`remote`&&await W()}var p=f;function m(){let e=new URLSearchParams;return n.timeFilter&&e.set(`range`,n.timeFilter),n.statsClientFilter&&n.statsClientFilter!==`all`&&e.set(`clientId`,n.statsClientFilter),n.statsScriptFilter&&n.statsScriptFilter!==`all`&&e.set(`scriptPath`,n.statsScriptFilter),`/api/stats/analytics?${e.toString()}`}function h(){r||=setInterval(d,1e4)}async function g(e,r={}){let i={"Content-Type":`application/json`,...r.headers||{}};n.auth.token&&(i.Authorization=`Bearer ${n.auth.token}`);let a=await fetch(t+e,{headers:i,...r}),o=await a.text(),s=null;try{s=o?JSON.parse(o):null}catch{s={error:o||`Backend returned invalid JSON`}}if(!a.ok)throw Error(s?.error||a.statusText||`Backend error`);return s}function _(){if(!n.auth.loggedIn){a.innerHTML=`
      ${n.auth.view===`signin`?y():n.auth.view===`signup`?b():v()}
      ${n.toast?`<div class="toast">${Q(n.toast)}</div>`:``}
    `,_e();return}a.innerHTML=`
    <div class="shell">
      ${ee()}
      <main class="main">
        ${n.error?N():te()}
      </main>
    </div>
    ${n.toast?`<div class="toast">${Q(n.toast)}</div>`:``}
  `,ge()}function ee(){let e=n.auth.user||n.profile||{displayName:`EnderUser`};return`
    <aside class="sidebar">
      <a href="#" class="brand-icon" data-landing title="Back to landing page">S</a>
      <nav class="nav-rail">
        ${i.map(([e,t,r,i])=>`
          <button class="nav-item ${n.page===e?`active`:``}" data-page="${e}" data-testid="nav-${e}" aria-label="${t}">
            <i class="${r}"></i>
            <span class="nav-tooltip">${i}</span>
          </button>
        `).join(``)}
      </nav>
      <div class="sidebar-footer">
        <button class="btn-icon" title="Notifications"><i class="fa-solid fa-bell"></i></button>
        <button class="user-avatar" data-action="logout" title="Log out">${Q((e.displayName||`U`).slice(0,1))}</button>
      </div>
    </aside>
  `}function v(){return`
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
  `}function y(){return x(`Sign In`,`signin`,`Welcome back, pilot.`,`Don't have an account?`,`signup`,`Create one`)}function b(){return x(`Get Started`,`signup`,`Join the endgame.`,`Already have an account?`,`signin`,`Sign in`)}function x(e,t,n,r,i,a){return`
    <div class="auth-page">
      <div class="noise-overlay"></div>
      <div class="grid-bg"></div>
      <div class="auth-card">
        <a href="#" class="logo" data-landing>
          <img class="logo-icon" src="/shulkr-icons.png" alt="Shulkr logo">
          <span>SHULKR</span>
        </a>
        <h1>${Q(e)}</h1>
        <p class="auth-subtitle">${Q(n)}</p>
        <form class="auth-form" data-auth-form="${t}">
          ${t===`signup`?`<div class="form-group"><label>Display name</label><input type="text" id="auth-name" placeholder="Your name" required value="Admin" /></div>`:``}
          <div class="form-group">
            <label>Email</label>
            <input type="email" id="auth-email" placeholder="you@example.com" required value="admin@shulkr.local" />
          </div>
          <div class="form-group">
            <label>Password</label>
            <input type="password" id="auth-password" placeholder="••••••••" required value="admin" />
          </div>
          ${t===`signup`?`<div class="form-group"><label>Confirm Password</label><input type="password" id="auth-password2" placeholder="••••••••" required value="admin" /></div>`:``}
          <button type="submit" class="btn btn-primary" style="width: 100%;">${t===`signin`?`Sign In`:`Create Account`}</button>
        </form>
        <div class="auth-divider"><span>or</span></div>
        <button class="btn btn-secondary" style="width: 100%; justify-content: center;" data-google-auth>
          <i class="fa-brands fa-google"></i>
          <span>${t===`signin`?`Sign in with Google`:`Continue with Google`}</span>
        </button>
        <p class="auth-switch">${Q(r)} <button class="btn-link" data-auth="${i}">${Q(a)}</button></p>
        <button class="btn btn-secondary" style="width: 100%; margin-top: 12px;" data-landing>Back to website</button>
      </div>
    </div>
  `}function te(){return n.loading?P():{profile:C,scripts:w,editor:se,statistics:pe,accounts:A,remote:j,settings:M}[n.page]?.()||C()}function S(e,t){return`
    <header class="top-bar">
      <div class="page-title">
        <i class="${t}"></i>
        <h1>${Q(e)}</h1>
      </div>
      <label class="global-search" data-testid="top-search">
        <i class="fa-solid fa-magnifying-glass"></i>
        <input id="search" value="${$(n.search)}" placeholder="Search scripts, accounts..." />
        <kbd>Ctrl</kbd><kbd>K</kbd>
      </label>
      <div class="top-actions">
        <button class="btn-icon" title="Refresh" data-action="refresh"><i class="fa-solid fa-rotate"></i></button>
        <span class="badge ${n.online?`badge-good`:`badge-active`}" style="height: 32px;">${n.online?`Online`:`Offline`}</span>
      </div>
    </header>
  `}function C(){let e=n.profile||{displayName:`EnderUser`,tier:`Premium`};return`
    ${S(`Overview`,`fa-solid fa-house`)}
    <section class="profile-hero">
      <div class="card profile-main">
        <div class="profile-info">
          <div class="avatar-xl">${Q((e.displayName||`U`).slice(0,1))}</div>
          <div class="profile-name">
            <h2>${Q(e.displayName||`EnderUser`)}</h2>
            <p>@${Q(e.username||e.minecraft||`shulkr_user`)}</p>
            <span class="badge badge-premium">${Q(e.tier||`Premium`)}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="btn btn-secondary" data-page="settings"><i class="fa-solid fa-gear"></i> Settings</button>
          <button class="btn btn-primary"><i class="fa-solid fa-download"></i> Download</button>
        </div>
      </div>
      <div class="card profile-meta">
        <div class="meta-row"><span>Plan</span><span>${Q(e.tier||`Premium`)}</span></div>
        <div class="meta-row"><span>Last active</span><span>Just now</span></div>
        <div class="meta-row"><span>Member since</span><span>November 2025</span></div>
        <div class="meta-row"><span>Connections</span><span>${n.clients.length} accounts</span></div>
      </div>
    </section>

    <section class="stats-row">
      ${F(`Scripts`,n.stats?.scripts??n.scripts.length,`fa-solid fa-file-code`,`var(--accent-purple)`)}
      ${F(`Templates`,n.stats?.templates??n.templates.length,`fa-solid fa-layer-group`,`var(--accent-pink)`)}
      ${F(`Libraries`,n.stats?.installedLibraries??n.libraries.length,`fa-solid fa-cubes`,`var(--accent-cyan)`)}
      ${F(`Published`,n.stats?.publishedScripts??n.library.length,`fa-solid fa-cloud-arrow-up`,`var(--accent-orange)`)}
    </section>

    <div class="grid-2">
      <div class="card chart-card">
        <div class="card-header">
          <h3><i class="fa-solid fa-wave-square"></i> Activity Pulse</h3>
          <button class="card-link">Details <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="chart-area">
          ${B([3,5,4,7,6,9,11,10,13,15,14,18],`var(--accent-purple)`)}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-clock-rotate-left"></i> Recent Activity</h3>
          <button class="card-link" data-page="statistics">All <i class="fa-solid fa-arrow-right"></i></button>
        </div>
        <div class="list">
          ${n.scripts.slice(0,5).map(I).join(``)||Y(`No recent activity.`)}
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
          ${n.clients.length?n.clients.slice(0,4).map(R).join(``):R({displayName:`EnderUser`,tier:`Premium`,connected:n.online,source:`profile`})}
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3><i class="fa-solid fa-bolt"></i> Quick Launch</h3>
        </div>
        <div class="list">
          ${n.scripts.slice(0,4).map(L).join(``)||Y(`No scripts available.`)}
        </div>
      </div>
    </div>
  `}function w(){if(n.loading&&n.library.length===0)return ae();let e=T(),t=n.hubSearch.toLowerCase(),r=n.hubCategory.toLowerCase(),i=e.filter(e=>{let i=!t||(e.name||``).toLowerCase().includes(t)||(e.about||``).toLowerCase().includes(t)||(e.author||``).toLowerCase().includes(t),a=(e.category||`Other`).toLowerCase(),o=r===`all`||a===r||r===`recent`&&!0,s=n.hubFilters[a]!==!1;return i&&o&&s});n.hubSort===`popular`?i.sort((e,t)=>(t.downloads||0)-(e.downloads||0)):n.hubSort===`stars`?i.sort((e,t)=>(t.stars||0)-(e.stars||0)):n.hubSort===`name`?i.sort((e,t)=>(e.name||``).localeCompare(t.name||``)):i.sort((e,t)=>(t.publishedAt||0)-(e.publishedAt||0));let a=i.length,o=Math.max(1,Math.ceil(a/n.hubPerPage)),s=Math.min(n.hubPage,o),c=(s-1)*n.hubPerPage,l=i.slice(c,c+n.hubPerPage);return`
    ${S(`Script Hub`,`fa-solid fa-file-code`)}
    <section class="hub-layout">
      <div class="hub-main">
        <div class="hub-header">
          <div>
            <h2>Script Library</h2>
            <p class="hub-subtitle">Publish, discover, and install Shulkr scripts from the community.</p>
          </div>
          <div class="hub-actions">
            <span class="hub-count">${a} published</span>
            <button class="btn btn-primary btn-icon" data-action="publish-script" title="Publish script"><i class="fa-solid fa-cloud-arrow-up"></i></button>
            <button class="btn btn-secondary btn-icon" data-action="refresh" title="Refresh"><i class="fa-solid fa-rotate"></i></button>
          </div>
        </div>
        <div class="hub-searchbar">
          <i class="fa-solid fa-magnifying-glass"></i>
          <input id="hub-search" value="${$(n.hubSearch)}" placeholder="Search scripts, e.g. AutoCrystal..." />
          <kbd>Ctrl</kbd><kbd>K</kbd>
        </div>
        <div class="hub-categories">
          ${[`all`,`recent`,`python`,`pyjinn`,`farming`,`combat`,`world`,`utility`,`other`].map(e=>`
            <button class="hub-category ${n.hubCategory===e?`active`:``}" data-hub-category="${e}">
              ${e===`all`?`All`:e===`recent`?`Recent`:e.charAt(0).toUpperCase()+e.slice(1)}
            </button>
          `).join(``)}
        </div>
        <div class="hub-grid">
          ${l.length?l.map(ne).join(``):re()}
        </div>
        ${o>1?ie(s,o,a):``}
      </div>
      <aside class="hub-filters card">
        <div class="filter-section">
          <div class="filter-title">Filters</div>
          <button class="btn-link" data-hub-reset>Reset</button>
        </div>
        <div class="filter-section">
          <div class="filter-search">
            <i class="fa-solid fa-magnifying-glass"></i>
            <input id="filter-search" value="${$(n.hubSearch)}" placeholder="Search filters..." />
          </div>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Categories</div>
          <div class="filter-checks">
            ${Object.entries(n.hubFilters).map(([e,t])=>`
              <label class="filter-check">
                <input type="checkbox" data-hub-filter="${e}" ${t?`checked`:``} />
                <span>${e.charAt(0).toUpperCase()+e.slice(1)}</span>
              </label>
            `).join(``)}
          </div>
        </div>
        <div class="filter-section">
          <div class="filter-heading">Sort by</div>
          <select id="hub-sort" data-hub-sort>
            <option value="recent" ${n.hubSort===`recent`?`selected`:``}>Recently modified</option>
            <option value="popular" ${n.hubSort===`popular`?`selected`:``}>Most installs</option>
            <option value="stars" ${n.hubSort===`stars`?`selected`:``}>Most stars</option>
            <option value="name" ${n.hubSort===`name`?`selected`:``}>Name</option>
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
  `}function T(){return n.library.length?n.library:n.scripts.map(e=>({id:e.path,name:e.name||e.fileName||`Untitled`,author:n.profile?.displayName||`EnderUser`,about:e.description||`A Shulkr community script.`,category:E(e.name||e.fileName||``),tags:[e.extension?.toUpperCase()||`PY`,E(e.name||e.fileName||``)],version:`1.0.0`,icon:``,fileName:e.name||e.fileName||``,downloads:0,stars:0,publishedAt:e.modifiedAt||Date.now(),updatedAt:e.modifiedAt||Date.now()}))}function E(e){let t=String(e).toLowerCase();return t.endsWith(`.pyj`)||t.includes(`pyjinn`)?`Pyjinn`:t.includes(`farm`)||t.includes(`crop`)||t.includes(`mine`)?`Farming`:t.includes(`combat`)||t.includes(`kill`)||t.includes(`crystal`)||t.includes(`aura`)?`Combat`:t.includes(`build`)||t.includes(`chunk`)||t.includes(`world`)?`World`:t.includes(`speed`)||t.includes(`nofall`)||t.includes(`bright`)||t.includes(`haste`)||t.includes(`jump`)||t.includes(`fire`)||t.includes(`water`)||t.includes(`saturation`)||t.includes(`cleanup`)||t.includes(`chat`)||t.includes(`sort`)||t.includes(`inventory`)?`Utility`:`Other`}function ne(e){let t=Q(e.name||`Untitled`),r=Q(e.author||`Shulkr user`),i=Q(e.about||`A Shulkr community script.`),a=Q(e.category||`Other`),o=(e.tags||[a]).slice(0,2),s=(e.fileName||e.name||``).split(`.`).pop()?.toLowerCase()||`py`,c=s===`py`?`fa-brands fa-python`:s===`lua`?`fa-solid fa-moon`:s===`js`?`fa-brands fa-js`:`fa-solid fa-code`;return`
    <div class="hub-card ${n.selectedScript?.id===e.id?`selected`:``}" data-hub-script="${$(e.id)}">
      <div class="hub-card-thumb">
        <div class="hub-thumb-icon"><i class="${c}"></i></div>
        ${e.badge?`<span class="hub-card-badge">${Q(e.badge)}</span>`:``}
      </div>
      <div class="hub-card-body">
        <div class="hub-card-title">
          <h4>${t}</h4>
          <span class="hub-card-tag">${a}</span>
        </div>
        <div class="hub-card-meta">
          <span>by ${r}</span>
          <span>•</span>
          <span>v${Q(e.version||`1.0.0`)}</span>
          <span>•</span>
          <span><i class="fa-solid fa-download"></i> ${Z(e.downloads||0)}</span>
        </div>
        <p class="hub-card-desc">${i}</p>
        <div class="hub-card-tags">
          ${o.map(e=>`<span class="hub-tag">${Q(e)}</span>`).join(``)}
        </div>
      </div>
      <div class="hub-card-actions">
        <button class="btn btn-primary btn-sm" data-hub-install="${$(e.id)}" title="Install"><i class="fa-solid fa-download"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-view="${$(e.id)}" title="View code"><i class="fa-solid fa-code"></i></button>
        <button class="btn btn-secondary btn-sm" data-hub-more="${$(e.id)}" title="More"><i class="fa-solid fa-ellipsis"></i></button>
      </div>
    </div>
  `}function re(){return`
    <div class="hub-empty">
      <i class="fa-solid fa-box-open"></i>
      <h3>No scripts found</h3>
      <p>Try a different search or publish your first script.</p>
      <button class="btn btn-primary" data-action="publish-script"><i class="fa-solid fa-cloud-arrow-up"></i> Publish Script</button>
    </div>
  `}function ie(e,t,n){let r=[];for(let n=1;n<=t;n++)n===1||n===t||n>=e-1&&n<=e+1?r.push(n):r[r.length-1]!==`...`&&r.push(`...`);return`
    <div class="hub-pagination">
      <button class="btn btn-secondary btn-sm" data-hub-page="${e-1}" ${e<=1?`disabled`:``}>Previous</button>
      <div class="hub-page-numbers">
        ${r.map(t=>t===`...`?`<span class="hub-ellipsis">...</span>`:`
          <button class="hub-page ${t===e?`active`:``}" data-hub-page="${t}">${t}</button>
        `).join(``)}
      </div>
      <button class="btn btn-secondary btn-sm" data-hub-page="${e+1}" ${e>=t?`disabled`:``}>Next</button>
    </div>
  `}function ae(){return`
    ${S(`Script Hub`,`fa-solid fa-file-code`)}
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
          ${[,,,,,,].fill(0).map(oe).join(``)}
        </div>
      </div>
      <aside class="hub-filters card">
        ${[,,,,,].fill(0).map(()=>`<div class="skeleton skeleton-text" style="width: 70%; margin-bottom: 16px;"></div>`).join(``)}
      </aside>
    </section>
  `}function oe(){return`
    <div class="hub-card skeleton-card">
      <div class="skeleton hub-thumb-skeleton"></div>
      <div class="hub-card-body">
        <div class="skeleton skeleton-text" style="width: 65%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 40%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 85%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 60%;"></div>
      </div>
    </div>
  `}function se(){if(n.loading&&n.scripts.length===0)return ce();let e=n.scripts.filter(e=>{let t=n.search.toLowerCase();return!t||(e.name||e.fileName||e.path||``).toLowerCase().includes(t)});return`
    ${S(`Script Editor`,`fa-solid fa-code`)}
    <section class="scripts-layout">
      <div class="scripts-sidebar card">
        <div class="card-header">
          <h3><i class="fa-solid fa-folder-open"></i> Scripts</h3>
          <button class="btn btn-primary btn-sm" data-action="new-script"><i class="fa-solid fa-plus"></i> New</button>
        </div>
        <div class="scripts-list">
          ${e.map(ue).join(``)||Y(`No scripts found.`)}
        </div>
      </div>
      <div class="scripts-editor card">
        ${n.editorScript?fe():de()}
      </div>
    </section>
  `}function ce(){return`
    ${S(`Script Editor`,`fa-solid fa-code`)}
    <section class="scripts-layout">
      <div class="scripts-sidebar card">
        <div class="card-header">
          <h3><i class="fa-solid fa-folder-open"></i> Scripts</h3>
          <div class="skeleton skeleton-btn-sm"></div>
        </div>
        <div class="scripts-list">
          ${[,,,,,,].fill(0).map(le).join(``)}
        </div>
      </div>
      <div class="scripts-editor card">
        ${D(`Loading...`)}
      </div>
    </section>
  `}function le(){return`
    <div class="script-item skeleton-card">
      <div class="skeleton skeleton-icon"></div>
      <div class="script-info" style="flex: 1;">
        <div class="skeleton skeleton-text" style="width: 55%;"></div>
        <div class="skeleton skeleton-text-sm" style="width: 35%;"></div>
      </div>
    </div>
  `}function ue(e){let t=Q(e.name||e.fileName||e.path||`Untitled`),r=(t.split(`.`).pop()||``).toLowerCase(),i=r===`py`?`fa-brands fa-python`:r===`lua`?`fa-solid fa-moon`:`fa-solid fa-file-code`;return`
    <div class="script-item ${n.editorScript?.path===e.path?`active`:``}" data-select-script="${$(e.path)}" data-select-name="${$(t)}">
      <div class="script-icon"><i class="${i}"></i></div>
      <div class="script-info">
        <h4>${t}</h4>
        <p>${X(e.size||0)} · ${e.modifiedAt?Me(e.modifiedAt):`Unknown`}</p>
      </div>
    </div>
  `}function de(){return`
    <div class="editor-empty">
      <i class="fa-solid fa-code"></i>
      <h3>Select a script to edit</h3>
      <p>Or create a new Minescript/Python file to get started.</p>
      <button class="btn btn-primary" data-action="new-script"><i class="fa-solid fa-plus"></i> New Script</button>
    </div>
  `}function fe(){let e=Q(n.editorScript?.fileName||n.editorScript?.name||`Untitled`);return n.editorContent===``&&n.editorScript&&!n.editorDirty?D(e):`
    <div class="editor-header">
      <div class="editor-title">
        <i class="fa-solid fa-file-code"></i>
        <span>${e}</span>
        ${n.editorDirty?`<span class="editor-dirty">●</span>`:``}
      </div>
      <div class="editor-actions">
        <button class="btn btn-secondary btn-sm" data-action="run-script"><i class="fa-solid fa-play"></i> Run</button>
        <button class="btn btn-primary btn-sm" data-action="save-script" ${n.editorDirty?``:`disabled`}><i class="fa-solid fa-floppy-disk"></i> Save</button>
      </div>
    </div>
    <div class="editor-container" id="monaco-editor"></div>
  `}function D(e){return`
    <div class="editor-header">
      <div class="editor-title">
        <i class="fa-solid fa-file-code"></i>
        <span>${e}</span>
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
  `}function pe(){let e=n.statsSummary||{},t=n.statsHistory||[],r=n.metricTab,i=r===`time`?`runtimeSeconds`:r===`fps`?`avgFps`:r===`commands`?`commandsCompleted`:r===`chat`?`chatMessages`:`scriptRuns`,a=t.length?t.map(e=>e[i]||0):[0],o=r===`fps`?`var(--accent-cyan)`:r===`commands`?`var(--accent-orange)`:r===`chat`?`var(--accent-lime)`:r===`runs`?`var(--accent-pink)`:`var(--accent-purple)`,s=[{id:`all`,displayName:`All Accounts`},...n.statsClients],c=[{path:`all`,name:`All Scripts`},...n.statsScripts];return`
    ${S(`Analytics`,`fa-solid fa-chart-pie`)}
    <div class="toolbar">
      <div class="toolbar-left">
        <select id="stats-account">
          ${s.map(e=>`<option value="${$(e.id)}" ${n.statsClientFilter===e.id?`selected`:``}>${Q(e.displayName)}</option>`).join(``)}
        </select>
        <select id="stats-script">
          ${c.map(e=>`<option value="${$(e.path)}" ${n.statsScriptFilter===e.path?`selected`:``}>${Q(e.name)}</option>`).join(``)}
        </select>
      </div>
      <div class="toolbar-right">
        <div class="pill-group">
          ${[`7d`,`30d`,`90d`,`1yr`,`All`].map(e=>`
            <button class="pill-btn ${n.timeFilter===e?`active`:``}" data-filter="${e}">${e}</button>
          `).join(``)}
        </div>
      </div>
    </div>

    <div class="card chart-card" style="margin-bottom: 24px;">
      <div class="card-header">
        <h3><i class="fa-solid fa-chart-area"></i> Performance Over Time</h3>
        <div class="pill-group">
          ${[[`time`,`Runtime`],[`fps`,`FPS`],[`commands`,`Commands`],[`runs`,`Runs`],[`chat`,`Chat`]].map(([e,t])=>`
            <button class="pill-btn ${n.metricTab===e?`active`:``}" data-metric="${e}">${t}</button>
          `).join(``)}
        </div>
      </div>
      <div class="chart-area" style="min-height: 280px;">
        ${t.length?B(a,o,t.map(e=>me(e.at)),he(r,a)):Y(`Analytics will appear here once the client sends telemetry.`)}
      </div>
    </div>

    <section class="stats-row">
      ${F(`Runtime`,k(e.runtimeSeconds||0),`fa-solid fa-hourglass-half`,`var(--accent-purple)`)}
      ${F(`Active Script Time`,k(e.activeScriptSeconds||0),`fa-solid fa-code`,`var(--accent-purple)`)}
      ${F(`Script Runs`,Z(e.scriptRuns||0),`fa-solid fa-play`,`var(--accent-pink)`)}
      ${F(`Commands`,Z(e.commandsCompleted||0),`fa-solid fa-terminal`,`var(--accent-orange)`)}
      ${F(`Chat Sends`,Z(e.chatMessages||0),`fa-solid fa-comment-dots`,`var(--accent-lime)`)}
      ${F(`Avg FPS`,Z(e.avgFps||0),`fa-solid fa-gauge-high`,`var(--accent-cyan)`)}
      ${F(`Screenshots`,Z(e.screenshots||0),`fa-solid fa-camera`,`var(--accent-orange)`)}
      ${F(`Peak Clients`,Z(e.activeClientsPeak||0),`fa-solid fa-users`,`var(--accent-purple)`)}
      ${F(`Sessions`,Z(e.sessions||t.length),`fa-solid fa-route`,`var(--accent-purple)`)}
    </section>

    <div class="card" style="margin-top: 24px;">
      <div class="card-header">
        <h3><i class="fa-solid fa-list-ul"></i> Session History</h3>
      </div>
      <div class="list">
        ${t.slice().reverse().slice(0,10).map(O).join(``)||Y(`No session history yet.`)}
      </div>
    </div>
  `}function O(e){return`
    <div class="list-item">
      <div class="icon"><i class="fa-solid fa-calendar-day"></i></div>
      <div class="content">
        <h4>${new Date(e.at||Date.now()).toLocaleDateString(void 0,{month:`short`,day:`numeric`})}</h4>
        <p>${k(e.runtimeSeconds||0)} • ${Z(e.scriptRuns||0)} runs • ${Z(e.commandsCompleted||0)} commands</p>
      </div>
      <span class="meta">${Z(e.avgFps||0)} FPS</span>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `}function k(e){let t=Math.max(0,Math.round(Number(e)||0));if(t<60)return`${t}s`;let n=Math.floor(t/60);if(n<60)return`${n}m`;let r=Math.floor(n/60),i=n%60;if(r<24)return i?`${r}h ${i}m`:`${r}h`;let a=Math.floor(r/24),o=r%24;return o?`${a}d ${o}h`:`${a}d`}function A(){let e=n.clients.length?n.clients:[n.profile||{displayName:`EnderUser`,tier:`Premium`}];return`
    ${S(`Accounts`,`fa-solid fa-users`)}
    <section class="accounts-grid">
      ${e.map(z).join(``)}
    </section>
  `}function j(){let e=n.selectedClient,r=n.selectedScript,i=e?.connected!==!1,a=n.stream,o=!!(a?.available&&a?.running),s=`${t}/api/stream/mjpeg?ts=${encodeURIComponent(a?.startedAt||`idle`)}`,c=e?[e.server||`Not connected`,e.world||`Main menu`,e.position||`-`]:[`No client selected`,`Waiting for heartbeat`,`-`],l=[`Remote initialized.`,n.online?`Backend handshake complete.`:`Backend offline.`,e?`${e.displayName||`Client`} ${i?`is live.`:`is offline.`}`:`No client selected.`,r?`Selected script: ${r.name||r.path}`:`No script selected.`,o?`Feed source: ${a.captureTitle||a.source||`window`}`:`Feed idle.`];return`
    ${S(`Remote`,`fa-solid fa-satellite-dish`)}
    <div class="remote-layout">
      <div>
        <div class="remote-screen">
          ${o?`
            <img
              class="remote-stream"
              src="${$(s)}"
              alt="Live Minecraft feed"
            >
          `:`
            <div class="remote-placeholder">
              <i class="fa-solid fa-desktop"></i>
              <p>${e?`${i?`Connected to`:`Last seen`} ${Q(e.displayName||`account`)}`:`Select an account to begin remote control`}</p>
              <p>${Q(c.join(` • `))}</p>
              <p>${Q(a?.lastError||(a?.available?`Starting live feed...`:`FFmpeg is not available.`))}</p>
            </div>
          `}
        </div>
        <div class="remote-controls">
          <select id="remote-account">
            ${n.clients.length?n.clients.map(t=>`<option value="${$(t.id||``)}" ${e?.id===t.id?`selected`:``}>${Q(t.displayName||`Account`)}</option>`).join(``):`<option>EnderUser</option>`}
          </select>
          <select id="remote-script">
            ${n.scripts.length?n.scripts.map(e=>`<option value="${$(e.path||``)}" ${r?.path===e.path?`selected`:``}>${Q(e.name||e.fileName||e.path)}</option>`).join(``):`<option>No scripts</option>`}
          </select>
          <button class="btn btn-secondary" data-action="remote-capture" ${e?``:`disabled`}><i class="fa-solid fa-camera"></i> Capture</button>
          <button class="btn btn-primary" data-action="remote-run" style="margin-left: auto;" ${e&&r?``:`disabled`}><i class="fa-solid fa-play"></i> Run</button>
          <button class="btn btn-secondary" data-action="remote-stop" ${e?``:`disabled`}><i class="fa-solid fa-stop"></i> Stop</button>
        </div>
        <div class="remote-tool-grid">
          <div class="card">
            <div class="card-header"><h3><i class="fa-solid fa-comment-dots"></i> Live Chat</h3></div>
            <div class="remote-chat-row">
              <input id="remote-chat-input" placeholder="Type a live chat message..." value="${$(n.remoteChatMessage)}">
              <button class="btn btn-primary" data-action="remote-chat-send" ${e?``:`disabled`}><i class="fa-solid fa-paper-plane"></i> Send</button>
            </div>
          </div>
          <div class="card">
            <div class="card-header"><h3><i class="fa-solid fa-bolt"></i> Quick Script</h3></div>
            <div class="form-group">
              <label>Script Name</label>
              <input id="remote-draft-name" value="${$(n.remoteDraftName)}" placeholder="QuickRemoteScript.py">
            </div>
            <div class="form-group">
              <label>Script Code</label>
              <textarea id="remote-draft-code" rows="10" placeholder="Write a fast remote script...">${Q(n.remoteDraftCode)}</textarea>
            </div>
            <div class="remote-quick-actions">
              <button class="btn btn-secondary" data-action="remote-script-save"><i class="fa-solid fa-floppy-disk"></i> Save Script</button>
              <button class="btn btn-primary" data-action="remote-script-save-run" ${e?``:`disabled`}><i class="fa-solid fa-play"></i> Save + Run</button>
            </div>
          </div>
        </div>
      </div>
      <div class="remote-sidebar">
        <div class="card">
          <div class="card-header"><h3><i class="fa-solid fa-terminal"></i> Event Log</h3></div>
          <div class="log-messages">
            ${l.map((e,t)=>`<div class="log-message"><span class="time">[00:00:0${t}]</span> ${Q(e)}</div>`).join(``)}
          </div>
        </div>
      </div>
    </div>
  `}function M(){return`
    ${S(`Settings`,`fa-solid fa-sliders`)}
    <div class="settings-grid">
      <div class="card">
        <div class="card-header"><h3><i class="fa-solid fa-server"></i> Connection</h3></div>
        <div class="form-group">
          <label>Backend URL</label>
          <input id="api-base" value="${$(t)}" />
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
  `}function N(){return`
    ${S(`Offline`,`fa-solid fa-triangle-exclamation`)}
    <div class="card" style="max-width: 600px;">
      <div class="empty-state" style="min-height: 320px;">
        <div>
          <i class="fa-solid fa-triangle-exclamation" style="font-size: 3rem; color: var(--red); margin-bottom: 16px;"></i>
          <h2 style="margin: 0 0 8px;">Server is offline</h2>
          <p style="color: var(--text-muted); margin: 0 0 20px;">The Shulkr backend is not responding at <code>${Q(t)}</code>.</p>
          <div class="form-group" style="text-align: left; width: 100%; max-width: 400px;">
            <label>Backend URL</label>
            <input id="api-base" value="${$(t)}" />
          </div>
          <div style="display: flex; gap: 12px; justify-content: center; margin-top: 16px;">
            <button class="btn btn-primary" data-action="save-api">Save URL</button>
            <button class="btn btn-secondary" data-action="refresh">Try again</button>
          </div>
        </div>
      </div>
    </div>
  `}function P(){return`
    ${S(`Loading`,`fa-solid fa-circle-notch`)}
    <div class="empty-state" style="min-height: 400px;">
      <div>
        <i class="fa-solid fa-circle-notch fa-spin" style="font-size: 3rem; color: var(--accent-purple); margin-bottom: 16px;"></i>
        <h2 style="margin: 0;">Loading Shulkr...</h2>
      </div>
    </div>
  `}function F(e,t,n,r){return`
    <div class="stat-card">
      <div class="label"><i class="${n}" style="color: ${r};"></i> ${Q(e)}</div>
      <div class="value">${Q(String(t))}</div>
    </div>
  `}function I(e){return`
    <div class="list-item" data-select-script="${$(e.path)}">
      <div class="icon"><i class="fa-solid fa-file-code"></i></div>
      <div class="content">
        <h4>${Q(e.name||e.fileName||e.path)}</h4>
        <p>${Q(e.path||`Local script`)}</p>
      </div>
      <span class="meta">${X(e.sizeBytes||e.size||0)}</span>
    </div>
  `}function L(e){return`
    <div class="list-item" data-select-script="${$(e.path)}">
      <div class="icon" style="background: rgba(45, 226, 230, 0.1); color: var(--accent-cyan);"><i class="fa-solid fa-play"></i></div>
      <div class="content">
        <h4>${Q(e.name||e.fileName||`Script`)}</h4>
        <p>Click to launch remotely</p>
      </div>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `}function R(e){let t=e.connected!==!1;return`
    <div class="list-item">
      <div class="icon" style="background: ${t?`rgba(61, 220, 132, 0.1)`:`rgba(255, 77, 109, 0.1)`}; color: ${t?`var(--green)`:`var(--red)`};">
        <i class="fa-solid fa-user"></i>
      </div>
      <div class="content">
        <h4>${Q(e.displayName||`EnderUser`)}</h4>
        <p>${Q(e.tier||`Local user`)}</p>
      </div>
      <span class="account-status ${t?``:`offline`}"><span class="dot"></span>${t?`Online`:`Offline`}</span>
    </div>
  `}function z(e){let t=e.connected!==!1;return`
    <div class="card account-card">
      <div class="avatar">${Q((e.displayName||`U`).slice(0,1))}</div>
      <div class="info">
        <h4>${Q(e.displayName||`EnderUser`)}</h4>
        <p>${Q(e.tier||`Local user`)}</p>
        <span class="account-status ${t?``:`offline`}"><span class="dot"></span>${t?`Active now`:`Offline`}${e.minecraft?` • ${Q(e.minecraft)}`:``}</span>
      </div>
      <i class="fa-solid fa-chevron-right arrow"></i>
    </div>
  `}function B(e,t=`var(--accent-purple)`,n=[],r=e=>Z(e)){let i=Math.max(...e,1),a=Math.min(...e),o=i-a||1,s=e.length>1?800/(e.length-1):800,c=e.map((e,t)=>`${t*s},${220-(e-a)/o*180-20}`),l=`${c[0]} ${c.map(e=>e).join(` `)} 800,220 0,220`,u=e[e.length-1]||0,d=n[n.length-1]||``;return`
    <svg viewBox="0 0 800 220" preserveAspectRatio="none">
      <defs>
        <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="${t}" stop-opacity="0.4"/>
          <stop offset="100%" stop-color="${t}" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <line class="chart-grid" x1="0" y1="${220/2}" x2="800" y2="${220/2}"/>
      <line class="chart-grid" x1="0" y1="20" x2="800" y2="20"/>
      <line class="chart-grid" x1="0" y1="200" x2="800" y2="200"/>
      <polygon class="chart-fill" points="${l}" fill="url(#areaGrad)"/>
      <polyline class="chart-line" points="${c.join(` `)}" style="stroke: ${t};"/>
      ${e.length?`<circle cx="${c[c.length-1].split(`,`)[0]}" cy="${c[c.length-1].split(`,`)[1]}" r="5" fill="${t}"/>`:``}
      <text x="0" y="16" fill="rgba(255,255,255,0.55)" font-size="12">${Q(r(i))}</text>
      <text x="0" y="214" fill="rgba(255,255,255,0.55)" font-size="12">${Q(r(a))}</text>
      ${d?`<text x="630" y="18" fill="rgba(255,255,255,0.75)" font-size="12">${Q(d)} • ${Q(r(u))}</text>`:``}
    </svg>
  `}function me(e){return new Date(e||Date.now()).toLocaleDateString(void 0,{month:`short`,day:`numeric`})}function he(e,t){return t=>e===`time`?k(t):e===`fps`?`${Math.round(t||0)} FPS`:e===`commands`?`${Z(t||0)} cmds`:e===`chat`?`${Z(t||0)} chat`:`${Z(t||0)} runs`}function ge(){document.querySelectorAll(`[data-page]`).forEach(e=>{e.addEventListener(`click`,async()=>{if(n.page=e.dataset.page,n.page===`remote`||n.page===`statistics`)try{await f()}catch{}_()})}),document.querySelectorAll(`[data-landing]`).forEach(e=>{e.addEventListener(`click`,e=>{e.preventDefault(),n.auth.view=`landing`,_()})}),document.querySelectorAll(`[data-action]`).forEach(e=>{e.addEventListener(`click`,()=>V(e.dataset.action))}),document.querySelectorAll(`[data-filter]`).forEach(e=>{e.addEventListener(`click`,async()=>{if(n.timeFilter=e.dataset.filter,n.page===`statistics`)try{await f()}catch{}_()})}),document.querySelectorAll(`[data-metric]`).forEach(e=>{e.addEventListener(`click`,async()=>{if(n.metricTab=e.dataset.metric,n.page===`statistics`)try{await f()}catch{}_()})}),document.querySelectorAll(`[data-select-script]`).forEach(e=>{e.addEventListener(`click`,()=>{n.page===`editor`?H(e.dataset.selectScript):(n.selectedScript=n.scripts.find(t=>t.path===e.dataset.selectScript)||n.selectedScript,n.page=`remote`,W().catch(()=>{}),_())})}),document.querySelectorAll(`[data-hub-script]`).forEach(e=>{e.addEventListener(`click`,t=>{if(t.target.closest(`[data-hub-install], [data-hub-view], [data-hub-more]`))return;let r=T().find(t=>t.id===e.dataset.hubScript);r&&(n.selectedScript=r,_())})}),document.querySelectorAll(`[data-hub-category]`).forEach(e=>{e.addEventListener(`click`,()=>{n.hubCategory=e.dataset.hubCategory,n.hubPage=1,_()})}),document.querySelectorAll(`[data-hub-page]`).forEach(e=>{e.addEventListener(`click`,()=>{n.hubPage=Number(e.dataset.hubPage),_()})}),document.querySelectorAll(`[data-hub-filter]`).forEach(e=>{e.addEventListener(`change`,()=>{n.hubFilters[e.dataset.hubFilter]=e.checked,n.hubPage=1,_()})}),document.querySelectorAll(`[data-hub-sort]`).forEach(e=>{e.addEventListener(`change`,()=>{n.hubSort=e.value,_()})}),document.querySelectorAll(`[data-hub-reset]`).forEach(e=>{e.addEventListener(`click`,()=>{n.hubSearch=``,n.hubCategory=`all`,n.hubSort=`recent`,n.hubPage=1,n.hubFilters={python:!0,pyjinn:!0,farming:!0,combat:!0,world:!0,utility:!0,other:!0},_()})}),document.querySelectorAll(`[data-hub-install]`).forEach(e=>{e.addEventListener(`click`,t=>{t.stopPropagation(),be(e.dataset.hubInstall)})}),document.querySelectorAll(`[data-hub-view]`).forEach(e=>{e.addEventListener(`click`,t=>{t.stopPropagation(),xe(e.dataset.hubView)})});let e=document.getElementById(`remote-account`);e&&e.addEventListener(`change`,e=>{n.selectedClient=n.clients.find(t=>t.id===e.target.value)||null,_()});let t=document.getElementById(`stats-account`);t&&t.addEventListener(`change`,async e=>{n.statsClientFilter=e.target.value||`all`,await f(),_()});let r=document.getElementById(`stats-script`);r&&r.addEventListener(`change`,async e=>{n.statsScriptFilter=e.target.value||`all`,await f(),_()});let i=document.getElementById(`remote-script`);i&&i.addEventListener(`change`,e=>{n.selectedScript=n.scripts.find(t=>t.path===e.target.value)||null,_()});let a=document.getElementById(`remote-chat-input`);a&&(a.addEventListener(`input`,e=>{n.remoteChatMessage=e.target.value}),a.addEventListener(`keydown`,e=>{e.key===`Enter`&&(e.preventDefault(),V(`remote-chat-send`))}));let o=document.getElementById(`remote-draft-name`);o&&o.addEventListener(`input`,e=>{n.remoteDraftName=e.target.value});let s=document.getElementById(`remote-draft-code`);s&&s.addEventListener(`input`,e=>{n.remoteDraftCode=e.target.value});let c=document.getElementById(`hub-search`);c&&c.addEventListener(`input`,e=>{n.hubSearch=e.target.value,n.hubPage=1,_()}),n.page===`editor`&&n.editorScript&&document.getElementById(`monaco-editor`)&&q()}function _e(){document.querySelectorAll(`[data-auth]`).forEach(e=>{e.addEventListener(`click`,()=>{n.auth.view=e.dataset.auth,_()})}),document.querySelectorAll(`[data-google-auth]`).forEach(e=>{e.addEventListener(`click`,()=>{window.location.href=`${t}/auth/google`})}),document.querySelectorAll(`[data-auth-form]`).forEach(e=>{e.addEventListener(`submit`,t=>{t.preventDefault(),ye(e.dataset.authForm)})}),ve()}function ve(){if(typeof gsap>`u`||typeof ScrollTrigger>`u`||!document.querySelector(`.landing`))return;gsap.registerPlugin(ScrollTrigger,ScrollToPlugin),ScrollTrigger.getAll().forEach(e=>e.kill()),gsap.fromTo(`.landing-navbar`,{y:-40,opacity:0},{y:0,opacity:1,duration:.8,ease:`power3.out`}),ScrollTrigger.create({start:`top -80`,end:99999,toggleClass:{className:`scrolled`,targets:`.landing-navbar`}}),gsap.fromTo(`.hero-tag`,{y:30,opacity:0},{y:0,opacity:1,duration:.8,delay:.2,ease:`power3.out`}),gsap.fromTo(`.hero-title .title-line`,{y:60,opacity:0},{y:0,opacity:1,duration:1,stagger:.15,delay:.35,ease:`power3.out`}),gsap.fromTo(`.hero-subtitle`,{y:30,opacity:0},{y:0,opacity:1,duration:.8,delay:.7,ease:`power3.out`}),gsap.fromTo(`.feature-badges .badge`,{y:40,opacity:0},{y:0,opacity:1,duration:.7,stagger:.1,delay:.9,ease:`power3.out`}),gsap.fromTo(`.release-card`,{x:60,opacity:0},{x:0,opacity:1,duration:1,delay:1.1,ease:`back.out(1.2)`}),gsap.fromTo(`.hero-footer`,{y:30,opacity:0},{y:0,opacity:1,duration:.8,delay:1.35,ease:`power3.out`}),gsap.utils.toArray(`.landing-section`).forEach(e=>{gsap.fromTo(e.querySelectorAll(`.section-header, .about-grid > *, .features-grid > *, .stats-grid > *, .community-grid > *, .download-grid > *`),{y:50,opacity:0},{y:0,opacity:1,duration:.8,stagger:.1,ease:`power3.out`,scrollTrigger:{trigger:e,start:`top 80%`,toggleActions:`play none none reverse`}})}),gsap.to(`.shard`,{y:e=>(e+1)*70,rotation:e=>e%2==0?8:-8,ease:`none`,scrollTrigger:{trigger:`.hero`,start:`top top`,end:`bottom top`,scrub:1.2}}),gsap.to(`.mountain-glow`,{y:120,opacity:.25,scale:1.1,ease:`none`,scrollTrigger:{trigger:`.hero`,start:`top top`,end:`bottom top`,scrub:1}}),gsap.to(`.showcase-frame`,{rotateY:8,rotateX:-4,y:-40,ease:`none`,scrollTrigger:{trigger:`.showcase-section`,start:`top bottom`,end:`bottom top`,scrub:1.5}}),gsap.to(`.icons-cluster-1`,{y:-120,rotateY:-25,ease:`none`,scrollTrigger:{trigger:`.showcase-section`,start:`top bottom`,end:`bottom top`,scrub:1.2}}),gsap.to(`.icons-cluster-2`,{y:-80,rotateY:25,ease:`none`,scrollTrigger:{trigger:`.showcase-section`,start:`top bottom`,end:`bottom top`,scrub:1.2}});let e=document.querySelector(`.showcase-frame`),t=document.querySelector(`.showcase-section`);e&&t&&window.matchMedia(`(pointer: fine)`).matches&&(t.addEventListener(`mousemove`,n=>{let r=t.getBoundingClientRect(),i=(n.clientX-r.left)/r.width-.5,a=(n.clientY-r.top)/r.height-.5;gsap.to(e,{rotateY:i*12,rotateX:-a*12,duration:.6,ease:`power2.out`,overwrite:`auto`})}),t.addEventListener(`mouseleave`,()=>{gsap.to(e,{rotateY:0,rotateX:0,duration:.8,ease:`power2.out`})})),document.querySelectorAll(`.stat-number`).forEach((e,t)=>{let n=parseFloat(e.dataset.target),r=n%1!=0;gsap.to(e,{innerText:n,duration:2,ease:`power2.out`,delay:t*.1,snap:{innerText:r?.1:1},scrollTrigger:{trigger:`.stats-grid`,start:`top 85%`,toggleActions:`play none none reverse`},onUpdate:function(){let t=parseFloat(e.innerText);e.innerText=r?t.toFixed(1):Math.floor(t)}})}),document.querySelectorAll(`.landing-nav-links a[href^="#"], .footer-links a[href^="#"]`).forEach(e=>{e.addEventListener(`click`,t=>{t.preventDefault();let n=document.querySelector(e.getAttribute(`href`));n&&gsap.to(window,{duration:.8,scrollTo:{y:n,offsetY:80},ease:`power2.out`})})})}async function ye(e){let t=document.querySelector(`#auth-email`)?.value.trim(),n=document.querySelector(`#auth-password`)?.value,r=document.querySelector(`#auth-password2`)?.value,i=document.querySelector(`#auth-name`)?.value.trim();if(!t||!n)return J(`Please fill in all fields`);if(e===`signup`&&n!==r)return J(`Passwords do not match`);try{let r=await g(e===`signin`?`/api/auth/local/signin`:`/api/auth/local/signup`,{method:`POST`,body:JSON.stringify(e===`signin`?{email:t,password:n}:{email:t,password:n,displayName:i})});s({id:r.user.id,displayName:r.user.displayName,email:r.user.email,tier:r.user.tier},r.token),J(e===`signin`?`Welcome back.`:`Account created.`),await u(),h(),_()}catch(e){J(e.message||`Authentication failed`)}}async function V(e){try{if(e===`refresh`)return u();if(e===`save-api`)return Ae();if(e===`logout`)return c(),_();if(e===`new-script`)return Ce();if(e===`save-script`)return we();if(e===`run-script`)return Te();if(e===`remote-run`)return Ee();if(e===`remote-stop`)return De();if(e===`remote-capture`)return Oe();if(e===`remote-chat-send`)return ke();if(e===`remote-script-save`)return U(!1);if(e===`remote-script-save-run`)return U(!0);if(e===`publish-script`)return Se()}catch(e){J(e.message||`Action failed`)}}async function H(e){let t=n.scripts.find(t=>t.path===e);if(t){n.editorScript=t,n.editorContent=``,n.editorDirty=!1,n.page=`editor`,_();try{n.editorContent=(await g(`/api/scripts/read?path=${encodeURIComponent(e)}`)).content||``,n.editorDirty=!1,_()}catch(e){J(e.message||`Failed to load script`)}}}async function be(e){try{await g(`/api/library/scripts/${encodeURIComponent(e)}/install`,{method:`POST`}),await u(),J(`Script installed`)}catch(e){J(e.message||`Failed to install script`)}}async function xe(e){try{let t=await g(`/api/library/scripts/${encodeURIComponent(e)}`);n.editorScript={path:t.fileName||t.id,name:t.fileName||t.name,...t},n.editorContent=t.code||``,n.editorDirty=!1,n.page=`editor`,_()}catch(e){J(e.message||`Failed to load script`)}}async function Se(){let e=prompt(`Script name:`);if(!e)return;let t=prompt(`Paste script code (or leave blank to publish a placeholder):`,`# New Shulkr script
import minescript as ms

ms.echo("Hello from Shulkr!")
`);if(t!==null)try{await g(`/api/library/scripts`,{method:`POST`,body:JSON.stringify({name:e,code:t,category:E(e),author:n.auth.user?.displayName||`Shulkr user`})}),await u(),J(`Script published`)}catch(e){J(e.message||`Failed to publish script`)}}async function Ce(){let e=prompt(`New script name (e.g. MyScript.py):`,`NewScript.py`);if(e)try{let t=await g(`/api/scripts`,{method:`POST`,body:JSON.stringify({name:e,content:`# New Shulkr script
import minescript as ms

ms.echo("Hello from Shulkr!")
`})});await u(),H(t.path),J(`Script created`)}catch(e){J(e.message||`Failed to create script`)}}async function we(){if(!n.editorScript)return;let e=n.editorContent;try{await g(`/api/scripts`,{method:`POST`,body:JSON.stringify({name:n.editorScript.path,content:e,overwrite:!0})}),n.editorDirty=!1,await u(),_(),J(`Script saved`)}catch(e){J(e.message||`Failed to save script`)}}async function Te(){if(n.editorScript)try{await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient?.id||`local-user`,type:`run_script`,payload:{path:n.editorScript.path}})}),J(`Script queued on client`)}catch(e){J(e.message||`Failed to run script`)}}async function Ee(){if(!n.selectedClient)throw Error(`Select a client first`);if(!n.selectedScript)throw Error(`Select a script first`);await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient.id,type:`run_script`,payload:{path:n.selectedScript.path}})}),J(`Queued ${n.selectedScript.name||n.selectedScript.path}`),await p(),_()}async function De(){if(!n.selectedClient)throw Error(`Select a client first`);await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient.id,type:`stop_scripts`,payload:{}})}),J(`Stop command queued`)}async function Oe(){if(!n.selectedClient)throw Error(`Select a client first`);await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient.id,type:`take_screenshot`,payload:{}})}),J(`Capture command queued`)}async function ke(){if(!n.selectedClient)throw Error(`Select a client first`);let e=(n.remoteChatMessage||``).trim();if(!e)throw Error(`Type a chat message first`);await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient.id,type:`send_chat`,payload:{message:e}})}),n.remoteChatMessage=``,_(),J(`Chat sent to Minecraft`)}async function U(e){let t=(n.remoteDraftName||``).trim()||`QuickRemoteScript.py`,r=n.remoteDraftCode||``,i=await g(`/api/scripts`,{method:`POST`,body:JSON.stringify({name:t,content:r,overwrite:!0})});if(await p(),n.selectedScript=n.scripts.find(e=>e.path===i.path)||i,e){if(!n.selectedClient)throw Error(`Select a client first`);await g(`/api/control/commands`,{method:`POST`,body:JSON.stringify({clientId:n.selectedClient.id,type:`run_script`,payload:{path:i.path}})}),J(`Quick script saved and queued`);return}J(`Quick script saved`)}async function W(){if(!n.online)return;let e=n.stream||await g(`/api/stream/status`);n.stream=e,!(!e?.available||e?.running)&&(n.stream=await g(`/api/stream/start`,{method:`POST`,body:JSON.stringify({mode:`window`,fps:15,title:`Minecraft`})})||e)}var G=null,K=!1;function q(){let t=document.getElementById(`monaco-editor`);if(!t||e===void 0)return;let r=G&&G.getDomNode&&G.getDomNode();if(G&&(!r||!document.body.contains(r))){try{G.dispose()}catch{}G=null}if(G){G.getValue()!==n.editorContent&&G.setValue(n.editorContent),n.editorScript.__original=n.editorContent;return}K||t.dataset.monaco===`true`||(K=!0,t.dataset.monaco=`true`,e.config({paths:{vs:`https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.0/min/vs`}}),e([`vs/editor/editor.main`],()=>{K=!1;let e=document.getElementById(`monaco-editor`);if(!e||G)return;let t=(n.editorScript?.path||``).toLowerCase().endsWith(`.lua`)?`lua`:`python`;G=monaco.editor.create(e,{value:n.editorContent,language:t,theme:`vs-dark`,automaticLayout:!0,minimap:{enabled:!0},fontSize:14,fontFamily:`'JetBrains Mono', 'Fira Code', monospace`,lineNumbers:`on`,scrollBeyondLastLine:!1,roundedSelection:!1,padding:{top:16},folding:!0,renderLineHighlight:`all`,matchBrackets:`always`,tabSize:4,insertSpaces:!0}),G.onDidChangeModelContent(()=>{n.editorContent=G.getValue(),n.editorDirty=n.editorContent!==n.editorScript?.__original;let e=document.querySelector(`.editor-dirty`);e&&(e.style.display=n.editorDirty?`inline`:`none`);let t=document.querySelector(`[data-action="save-script"]`);t&&(t.disabled=!n.editorDirty)}),n.editorScript.__original=n.editorContent}))}function Ae(){let e=document.querySelector(`#api-base`)?.value.trim();e&&(localStorage.setItem(`shulkr_api_base`,e),J(`Backend URL saved. Reloading...`),setTimeout(()=>location.reload(),700))}function J(e){n.toast=e,_(),setTimeout(()=>{n.toast=``,_()},2200)}function Y(e){return`<div class="empty-state" style="min-height: 120px;">${Q(e)}</div>`}function X(e){return e?e<1024?`${e} B`:`${(e/1024).toFixed(1)} KB`:`0 B`}function Z(e){let t=Number(e)||0;return t>=1e6?`${(t/1e6).toFixed(1)}M`:t>=1e3?`${(t/1e3).toFixed(1)}K`:String(t)}function je(e){if(!e)return`-`;if(typeof e==`string`&&Number.isNaN(Number(e)))return e;let t=Number(e);if(!Number.isFinite(t))return String(e);let n=Math.max(1,Math.round((Date.now()-t)/1e3));if(n<60)return`just now`;let r=Math.round(n/60);if(r<60)return`${r}m ago`;let i=Math.round(r/60);return i<48?`${i}h ago`:`${Math.round(i/24)}d ago`}function Me(e){return je(e)}function Q(e){return String(e??``).replace(/[&<>"']/g,e=>({"&":`&amp;`,"<":`&lt;`,">":`&gt;`,'"':`&quot;`,"'":`&#39;`})[e])}function $(e){return Q(e).replace(/`/g,`&#96;`)}