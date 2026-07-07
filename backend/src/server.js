require("dotenv").config();
const cors = require("cors");
const express = require("express");
const fsSync = require("fs");
const fs = require("fs/promises");
const path = require("path");
const { spawn, execFileSync } = require("child_process");
const session = require("express-session");
const passport = require("passport");
const GoogleStrategy = require("passport-google-oauth20").Strategy;
const jwt = require("jsonwebtoken");

const rootDir = path.resolve(__dirname, "..", "..");
const runDir = path.join(rootDir, "run");
const scriptDir = path.resolve(process.env.SHULKR_SCRIPT_DIR || process.env.SHULK_SCRIPT_DIR || process.env.FLUXUS_SCRIPT_DIR || path.join(runDir, "minescript"));
const dataDir = path.resolve(process.env.SHULKR_DATA_DIR || process.env.SHULK_DATA_DIR || process.env.FLUXUS_DATA_DIR || path.join(runDir, "shulkr-backend"));
const legacyDataDir = path.join(runDir, "shulk-backend");
const profilePath = path.join(dataDir, "profile.json");
const librariesPath = path.join(dataDir, "modules.json");
const clientModulesPath = path.join(dataDir, "client-modules.json");
const templatesPath = path.join(dataDir, "templates.json");
const libraryScriptsPath = path.join(dataDir, "library-scripts.json");
const libraryScriptPathsPath = path.join(dataDir, "module-scripts.json");
const clientsPath = path.join(dataDir, "clients.json");
const licensesPath = path.join(dataDir, "licenses.json");
const usersPath = path.join(dataDir, "users.json");
const resourcesDataDir = path.join(rootDir, "src", "main", "resources", "assets", "triton-ui", "data");
const iconAssetsDir = path.join(rootDir, "src", "main", "resources", "assets", "triton-ui", "textures", "icons");
const publicDir = path.join(__dirname, "..", "public");
const webClientDist = path.join(rootDir, "web-client", "dist");
const port = Number(process.env.SHULKR_BACKEND_PORT || process.env.SHULK_BACKEND_PORT || process.env.FLUXUS_BACKEND_PORT || 50991);
const backendUrl = process.env.SHULKR_BACKEND_URL || `http://127.0.0.1:${port}`;
const webClientUrl = process.env.SHULKR_WEB_CLIENT_URL || "http://127.0.0.1:5178";
const googleClientId = process.env.SHULKR_GOOGLE_CLIENT_ID || "";
const googleClientSecret = process.env.SHULKR_GOOGLE_CLIENT_SECRET || "";
const jwtSecret = process.env.SHULKR_JWT_SECRET || "";
const controlCommands = [];
const controlActivity = [];
const statsHistoryPath = path.join(dataDir, "stats-history.json");
const streamState = {
  process: null,
  clients: new Set(),
  startedAt: null,
  lastError: "",
  mode: "desktop",
  fps: 20,
  captureRect: null,
  captureTitle: ""
};

const app = express();
app.use(cors({ origin: true, credentials: true }));
app.use(express.json({ limit: "10mb" }));
app.use(express.text({ type: "text/*", limit: "10mb" }));
app.use("/assets/icons", express.static(iconAssetsDir));
app.use(session({
  secret: jwtSecret || "shulkr-dev-secret-change-me",
  resave: false,
  saveUninitialized: false,
  cookie: { secure: false, sameSite: "lax" }
}));
app.use(passport.initialize());
app.use(passport.session());

passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((obj, done) => done(null, obj));

if (googleClientId && googleClientSecret) {
  passport.use(new GoogleStrategy({
    clientID: googleClientId,
    clientSecret: googleClientSecret,
    callbackURL: `${backendUrl}/auth/google/callback`
  }, (accessToken, refreshToken, profile, done) => {
    const email = profile.emails?.[0]?.value || `${profile.id}@google.local`;
    const user = {
      id: profile.id,
      displayName: profile.displayName || email.split("@")[0],
      email,
      avatar: profile.photos?.[0]?.value || "",
      provider: "google"
    };
    return done(null, user);
  }));
}

const scriptExtensions = new Set([".py", ".pyj", ".lua", ".js", ".txt"]);
const hiddenRoots = new Set(["system", "templates", "plugins", "plugins_disabled", "exports", "blockpacks"]);

async function ensureReady() {
  if (!process.env.SHULKR_DATA_DIR && !process.env.SHULK_DATA_DIR && !process.env.FLUXUS_DATA_DIR && !fsSync.existsSync(dataDir) && fsSync.existsSync(legacyDataDir)) {
    await fs.cp(legacyDataDir, dataDir, { recursive: true });
  }
  await fs.mkdir(dataDir, { recursive: true });
  await fs.mkdir(scriptDir, { recursive: true });
  await seedJson(profilePath, path.join(resourcesDataDir, "profile.json"), defaultProfile());
  await seedJson(librariesPath, path.join(resourcesDataDir, "modules.json"), defaultLibraries());
  await seedJson(clientModulesPath, "", defaultClientModules());
  await syncClientModules();
  await seedJson(templatesPath, path.join(resourcesDataDir, "templates.json"), defaultTemplates());
  await seedJson(libraryScriptsPath, "", []);
  await seedJson(libraryScriptPathsPath, "", defaultLibraryScriptPaths());
  await cleanLegacyAutoModules();
  await seedJson(clientsPath, "", []);
  await seedJson(licensesPath, "", defaultLicenses());
  await seedLocalUsers();
  await seedStatsHistory();
}

async function seedStatsHistory() {
  if (fsSync.existsSync(statsHistoryPath)) return;
  const now = Date.now();
  const points = [];
  for (let i = 30; i >= 0; i--) {
    const day = now - i * 24 * 60 * 60 * 1000;
    points.push({
      at: day,
      runtime: Math.floor(Math.random() * 180 + 30),
      runs: Math.floor(Math.random() * 20 + 2),
      failsafes: Math.floor(Math.random() * 5),
      exp: Math.floor(Math.random() * 500000 + 50000),
      profit: Math.floor(Math.random() * 200000 - 50000)
    });
  }
  await writeJson(statsHistoryPath, points);
}

async function seedLocalUsers() {
  const users = await readJson(usersPath, []);
  if (!users.some((u) => u.email === "admin@shulkr.local")) {
    users.push({
      id: "admin-user",
      displayName: "Admin",
      email: "admin@shulkr.local",
      password: "admin",
      provider: "local",
      tier: "Premium",
      createdAt: new Date().toISOString()
    });
    await writeJson(usersPath, users);
  }
}

async function findLocalUser(email) {
  const users = await readJson(usersPath, []);
  return users.find((u) => u.email === email) || null;
}

async function createLocalUser(displayName, email, password) {
  const users = await readJson(usersPath, []);
  if (users.some((u) => u.email === email)) {
    throw new Error("An account with that email already exists");
  }
  const user = {
    id: `local-${Date.now()}`,
    displayName: displayName || email.split("@")[0],
    email,
    password,
    provider: "local",
    tier: "Premium",
    createdAt: new Date().toISOString()
  };
  users.push(user);
  await writeJson(usersPath, users);
  return user;
}

async function syncLocalProfile(user) {
  const current = await readJson(profilePath, defaultProfile());
  const next = {
    ...current,
    id: user.id,
    displayName: user.displayName,
    email: user.email,
    connected: true,
    lastSeenAt: new Date().toISOString()
  };
  await writeJson(profilePath, next);
  const licenses = await readJson(licensesPath, defaultLicenses());
  if (!licenses.some((license) => license.userId === user.id)) {
    licenses.push({
      id: `local-${user.id}`,
      userId: user.id,
      displayName: user.displayName,
      tier: user.tier || "Premium",
      status: "active",
      seats: 1,
      expiresAt: null,
      features: ["client", "editor", "script-library", "templates", "modules"]
    });
    await writeJson(licensesPath, licenses);
  }
  return next;
}

async function seedJson(target, source, fallback) {
  try {
    await fs.access(target);
  } catch {
    try {
      await fs.copyFile(source, target);
    } catch {
      await writeJson(target, fallback);
    }
  }
}

function defaultProfile() {
  const now = new Date().toISOString();
  return {
    id: "local-user",
    displayName: "EnderUser",
    tier: "Premium",
    avatar: "user-solid.png",
    activePage: "Dashboard",
    connected: true,
    uiScale: 100,
    status: "Ready",
    createdAt: now,
    lastSeenAt: now
  };
}

function defaultTemplates() {
  return [
    {
      id: "starter",
      name: "Starter Script",
      category: "Utility",
      description: "A tiny Minescript starter.",
      difficulty: "Easy",
      blocks: 3,
      icon: "code-solid.png",
      badge: "New",
      script: "import minescript as ms\n\nms.echo(\"Starter loaded\")\n"
    },
    {
      id: "pathfinder-route",
      name: "Pathfinder Route",
      category: "Movement",
      description: "Build a route from your current position to the block under your crosshair.",
      difficulty: "Advanced",
      blocks: 11,
      icon: "person-walking-dashed-line-arrow-right-solid.png",
      badge: "New",
      script: "import minescript as ms\n\nms.echo(\"Pathfinder Route scaffold\")\n"
    }
  ];
}

function defaultLicenses() {
  return [
    {
      id: "local-premium",
      userId: "local-user",
      displayName: "EnderUser",
      tier: "Premium",
      status: "active",
      seats: 1,
      expiresAt: null,
      features: ["client", "editor", "script-library", "templates", "modules"]
    }
  ];
}

function defaultLibraries() {
  return [
    {
      id: "nbtlib",
      name: "nbtlib",
      author: "jasonperlow",
      version: "v2.3.5",
      description: "Read, write and modify Minecraft NBT data.",
      category: "Data",
      icon: "box-solid.png",
      status: "Installed",
      installed: true,
      favorite: true
    },
    {
      id: "matplotlib",
      name: "matplotlib",
      author: "matplotlib",
      version: "v3.8.2",
      description: "Create static, animated and interactive visualizations.",
      category: "Visualization",
      icon: "circle-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "requests",
      name: "requests",
      author: "psf",
      version: "v2.31.0",
      description: "HTTP for Humans.",
      category: "Web",
      icon: "arrows-rotate-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "numpy",
      name: "numpy",
      author: "numpy",
      version: "v1.26.4",
      description: "Fundamental package for scientific computing.",
      category: "Data",
      icon: "box-open-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "pillow",
      name: "pillow",
      author: "python-pillow",
      version: "v10.2.0",
      description: "Python Imaging Library for image processing.",
      category: "Utility",
      icon: "cloud-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "pandas",
      name: "pandas",
      author: "pandas-dev",
      version: "v2.2.1",
      description: "Powerful data structures for data analysis.",
      category: "Data",
      icon: "border-all-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "rich",
      name: "rich",
      author: "Textualize",
      version: "v13.7.1",
      description: "Rich text and beautiful formatting in the console.",
      category: "Utility",
      icon: "code-solid.png",
      status: "Installed",
      installed: true,
      favorite: false
    },
    {
      id: "aiohttp",
      name: "aiohttp",
      author: "aio-libs",
      version: "v3.9.3",
      description: "Asynchronous HTTP client/server framework.",
      category: "Web",
      icon: "circle-info-solid.png",
      status: "Not Installed",
      installed: false,
      favorite: false
    }
  ];
}

function defaultClientModules() {
  return [
    {
      id: "web-dashboard",
      name: "Web Dashboard",
      author: "Shulkr",
      version: "0.1.0",
      description: "Remotely control scripts, overlays, and the connected Minecraft client from a local web dashboard.",
      category: "Dashboard",
      icon: "window-restore-regular.png",
      status: "Available",
      installed: false,
      favorite: true,
      openUrl: "http://127.0.0.1:50991/web/"
    },
    {
      id: "backend-admin",
      name: "Backend Admin",
      author: "Shulk",
      version: "1.0.0",
      description: "Open the local Express control surface for scripts, licenses, and the module catalog.",
      category: "Admin",
      icon: "gear-solid.png",
      status: "Available",
      installed: true,
      favorite: false,
      openUrl: "http://127.0.0.1:50991/admin/"
    },
    {
      id: "script-hub",
      name: "Script Hub",
      author: "Shulk",
      version: "1.0.0",
      description: "Jump into the browser-based script workspace and published library tools.",
      category: "Workspace",
      icon: "house-solid.png",
      status: "Available",
      installed: true,
      favorite: false,
      openUrl: "http://127.0.0.1:50991/web/"
    }
  ];
}

async function syncClientModules() {
  const defaults = defaultClientModules();
  const current = await readJson(clientModulesPath, []);
  const existing = Array.isArray(current) ? current : [];
  const merged = defaults.map((fallback) => {
    const saved = existing.find((item) => item.id === fallback.id);
    return saved ? { ...fallback, ...saved, openUrl: fallback.openUrl } : fallback;
  });
  for (const item of existing) {
    if (!merged.some((candidate) => candidate.id === item.id)) merged.push(item);
  }
  await writeJson(clientModulesPath, merged);
}

function addControlActivity(action, detail, status = "ok") {
  controlActivity.unshift({ id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, action, detail, status, at: new Date().toISOString() });
  controlActivity.splice(80);
}

function defaultLibraryScriptPaths() {
  return [];
}

function legacyAutoModulePaths() {
  return [
    "Speed.py",
    "NoFall.py",
    "Fullbright.py",
    "Haste.py",
    "JumpBoost.py",
    "FireResistance.py",
    "WaterBreathing.py",
    "Saturation.py",
    "CleanupHacks.py",
    "UtilityStatus.py"
  ];
}

async function cleanLegacyAutoModules() {
  const legacy = new Set(legacyAutoModulePaths());
  const current = await readJson(libraryScriptPathsPath, []);
  if (!Array.isArray(current)) {
    await writeJson(libraryScriptPathsPath, []);
    return;
  }
  const cleaned = current.filter((item) => !legacy.has(normalizeSlashes(item)));
  if (cleaned.length !== current.length) {
    await writeJson(libraryScriptPathsPath, cleaned);
  }
}

async function readJson(file, fallback) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch {
    return fallback;
  }
}

async function writeJson(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

function normalizeSlashes(value) {
  return String(value || "").replace(/\\/g, "/");
}

function hasScriptExtension(fileName) {
  return scriptExtensions.has(path.extname(fileName).toLowerCase());
}

function safeScriptName(fileName) {
  let name = normalizeSlashes(fileName).split("/").pop().replace(/[\r\n\t]/g, "").trim();
  if (!name) {
    name = "UploadedScript.py";
  }
  if (!hasScriptExtension(name)) {
    name += ".py";
  }
  return name;
}

function safeScriptRelativePath(fileName) {
  const normalized = normalizeSlashes(fileName || "").replace(/[\r\n\t]/g, "").trim();
  if (!normalized || normalized.includes("\0")) {
    return "UploadedScript.py";
  }
  const parts = normalized.split("/").filter(Boolean);
  if (!parts.length) {
    return "UploadedScript.py";
  }
  const cleanParts = parts.map((part) => part.replace(/[<>:"|?*]/g, "").trim()).filter(Boolean);
  if (cleanParts.length !== parts.length || cleanParts.some((part) => part === "." || part === "..")) {
    throw new Error("Invalid script path");
  }
  const file = cleanParts[cleanParts.length - 1];
  if (!hasScriptExtension(file)) {
    cleanParts[cleanParts.length - 1] = file + ".py";
  }
  return cleanParts.join("/");
}

function safeFolderRelativePath(folderName) {
  const normalized = normalizeSlashes(folderName || "").replace(/[\r\n\t]/g, "").trim();
  if (!normalized || normalized.includes("\0")) {
    throw new Error("Missing folder path");
  }
  const cleanParts = normalized.split("/").filter(Boolean).map((part) => part.replace(/[<>:"|?*]/g, "").trim()).filter(Boolean);
  if (!cleanParts.length || cleanParts.some((part) => part === "." || part === "..")) {
    throw new Error("Invalid folder path");
  }
  return cleanParts.join("/");
}

function safeTemplateId(value) {
  const id = String(value || "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  return id || "starter";
}

function safeInside(base, candidate) {
  const resolved = path.resolve(candidate);
  const relative = path.relative(path.resolve(base), resolved);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error("Path must stay inside " + base);
  }
  return resolved;
}

function scriptPathFromRequest(value) {
  const normalized = normalizeSlashes(value);
  if (!normalized || normalized.includes("\0")) {
    throw new Error("Missing script path");
  }
  return safeInside(scriptDir, path.join(scriptDir, normalized));
}

function folderPathFromRequest(value) {
  const relative = safeFolderRelativePath(value);
  return safeInside(scriptDir, path.join(scriptDir, relative));
}

async function uniqueScriptPath(fileName) {
  const name = safeScriptRelativePath(fileName);
  let target = safeInside(scriptDir, path.join(scriptDir, name));
  try {
    await fs.access(target);
  } catch {
    return target;
  }
  const ext = path.extname(name);
  const stem = path.basename(name, ext);
  const dir = path.dirname(name) === "." ? "" : path.dirname(name);
  for (let i = 2; i < 1000; i += 1) {
    target = safeInside(scriptDir, path.join(scriptDir, dir, `${stem}-${i}${ext}`));
    try {
      await fs.access(target);
    } catch {
      return target;
    }
  }
  return safeInside(scriptDir, path.join(scriptDir, dir, `${stem}-${Date.now()}${ext}`));
}

async function folders(dir = scriptDir, depth = 0) {
  if (depth > 2) {
    return [];
  }
  let entries = [];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const result = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }
    const absolute = path.join(dir, entry.name);
    const relative = normalizeSlashes(path.relative(scriptDir, absolute));
    if (isHiddenScript(relative + "/placeholder.py")) {
      continue;
    }
    result.push({ path: relative, name: entry.name });
    result.push(...await folders(absolute, depth + 1));
  }
  return result.sort((a, b) => a.path.localeCompare(b.path));
}

function isHiddenScript(relativePath) {
  const normalized = normalizeSlashes(relativePath);
  const parts = normalized.split("/");
  const root = (parts[0] || "").toLowerCase();
  const name = (parts[parts.length - 1] || "").toLowerCase();
  return hiddenRoots.has(root) || root.startsWith(".") || name.startsWith(".") || name === "config.txt";
}

async function walkScripts(dir = scriptDir, depth = 0) {
  if (depth > 3) {
    return [];
  }
  let entries = [];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const result = [];
  for (const entry of entries) {
    const absolute = path.join(dir, entry.name);
    const relative = normalizeSlashes(path.relative(scriptDir, absolute));
    if (isHiddenScript(relative)) {
      continue;
    }
    if (entry.isDirectory()) {
      result.push(...await walkScripts(absolute, depth + 1));
    } else if (entry.isFile() && hasScriptExtension(entry.name)) {
      result.push(absolute);
    }
  }
  return result;
}

async function scriptSummary(file) {
  const stat = await fs.stat(file);
  const relative = normalizeSlashes(path.relative(scriptDir, file));
  return {
    path: relative,
    name: path.basename(file),
    extension: path.extname(file).replace(".", "").toLowerCase(),
    sizeBytes: stat.size,
    modifiedAt: stat.mtimeMs,
    description: await scriptDescription(file)
  };
}

async function scriptDescription(file) {
  try {
    const lines = (await fs.readFile(file, "utf8")).split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        const comment = trimmed.replace(/^#+/, "").trim();
        if (comment) {
          return comment;
        }
      }
      if (trimmed && !trimmed.startsWith("import ") && !trimmed.startsWith("from ")) {
        return trimmed.length > 90 ? trimmed.slice(0, 87) + "..." : trimmed;
      }
    }
  } catch {
  }
  return "";
}

async function scripts() {
  const files = await walkScripts();
  const summaries = await Promise.all(files.map(scriptSummary));
  return summaries.sort((a, b) => a.path.localeCompare(b.path));
}

async function scriptModules(scriptSummaries = null) {
  const marked = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  const markedSet = new Set(Array.isArray(marked) ? marked.map(normalizeSlashes).filter(Boolean) : []);
  const summaries = scriptSummaries || await scripts();
  return summaries
    .filter((script) => markedSet.has(normalizeSlashes(script.path)))
    .map((script) => ({
      id: `local:${script.path}`,
      name: path.basename(script.name, path.extname(script.name)),
      author: "local module",
      version: script.extension.toUpperCase() || "PY",
      description: script.description || "Marked as a reusable Shulkr module.",
      category: script.extension === "pyj" ? "Pyjinn" : "Python",
      icon: script.extension === "pyj" ? "route-solid.png" : scriptIcon(script.name),
      status: "Installed",
      installed: true,
      favorite: true
    }));
}

function scriptCategory(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.includes("farm") || lower.includes("crop") || lower.includes("mine")) return "Farming";
  if (lower.includes("combat") || lower.includes("killaura") || lower.includes("crystal")) return "Combat";
  if (lower.includes("build") || lower.includes("chunk") || lower.includes("world")) return "World";
  if (lower.includes("speed") || lower.includes("nofall") || lower.includes("fullbright") || lower.includes("haste")
    || lower.includes("jump") || lower.includes("fire") || lower.includes("water") || lower.includes("saturation")
    || lower.includes("cleanup") || lower.includes("chat") || lower.includes("sort") || lower.includes("inventory")
    || lower.includes("config")) return "Utility";
  return "Other";
}

function scriptIcon(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.includes("farm")) return "user-solid.png";
  if (lower.includes("combat") || lower.includes("mine")) return "broom-solid.png";
  if (lower.includes("build") || lower.includes("chunk")) return "box-open-solid.png";
  return "code-solid.png";
}

function firstComment(content) {
  for (const line of String(content || "").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed.startsWith("#")) {
      const comment = trimmed.replace(/^#+/, "").trim();
      if (comment) return comment;
    }
  }
  return "Published Shulkr script.";
}

function slug(value) {
  return String(value || "script").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || "script";
}

async function uniqueLibraryId(name) {
  const items = await libraryScripts();
  const base = slug(name);
  let candidate = base;
  let suffix = 2;
  while (items.some((item) => item.id === candidate)) {
    candidate = `${base}-${suffix++}`;
  }
  return candidate;
}

function normalizeLibraryScript(input, fallbackContent = "") {
  const now = Date.now();
  const fileName = safeScriptName(input?.fileName || input?.name || "PublishedScript.py");
  const code = String(input?.code ?? input?.content ?? fallbackContent ?? "");
  const name = String(input?.name || path.basename(fileName, path.extname(fileName))).trim() || "Untitled Script";
  const category = String(input?.category || scriptCategory(fileName));
  const tags = Array.isArray(input?.tags) && input.tags.length
    ? input.tags.map(String)
    : [path.extname(fileName).replace(".", "").toUpperCase() || "PY", category];
  return {
    id: String(input?.id || "").trim() || slug(name),
    name,
    author: String(input?.author || "Shulkr user"),
    about: String(input?.about || input?.description || firstComment(code)),
    category,
    tags,
    version: String(input?.version || "1.0.0"),
    icon: String(input?.icon || scriptIcon(fileName)),
    fileName,
    code,
    downloads: Math.max(0, Number(input?.downloads || 0)),
    stars: Math.max(0, Number(input?.stars || 0)),
    publishedAt: Number(input?.publishedAt || now),
    updatedAt: Number(input?.updatedAt || now)
  };
}

async function libraryScripts() {
  const items = await readJson(libraryScriptsPath, []);
  return items.map((item) => normalizeLibraryScript(item)).sort((a, b) => b.publishedAt - a.publishedAt);
}

async function saveLibraryScripts(items) {
  await writeJson(libraryScriptsPath, items);
}

async function saveLibraries(items) {
  await writeJson(librariesPath, items);
}

async function saveClientModules(items) {
  await writeJson(clientModulesPath, items);
}

async function snapshotData() {
  const [profile, scriptList, templates, licenses, publishedScripts, libraries, clientModules] = await Promise.all([
    readJson(profilePath, defaultProfile()),
    scripts(),
    readJson(templatesPath, defaultTemplates()),
    readJson(licensesPath, defaultLicenses()),
    libraryScripts(),
    readJson(librariesPath, defaultLibraries()),
    readJson(clientModulesPath, defaultClientModules())
  ]);
  const modules = await scriptModules(scriptList);
  const stats = {
    scripts: scriptList.length,
    installedModules: modules.length,
    installedLibraries: modules.length,
    installedClientModules: Array.isArray(clientModules) ? clientModules.filter((item) => item.installed !== false).length : 0,
    templates: templates.length,
    publishedScripts: publishedScripts.length,
    scriptDir,
    dataDir
  };
  return { profile, stats, scripts: scriptList, modules, libraries, clientModules, templates, licenses };
}

async function connectedClients() {
  const profile = await readJson(profilePath, defaultProfile());
  const stored = await readJson(clientsPath, []);
  const now = Date.now();
  const profileLastSeen = Date.parse(profile.lastSeenAt || 0);
  const safeProfileLastSeen = Number.isFinite(profileLastSeen) ? profile.lastSeenAt : new Date(now).toISOString();
  const localClient = {
    id: profile.id || "local-user",
    displayName: profile.displayName || "EnderUser",
    tier: profile.tier || "Premium",
    status: profile.status || "Ready",
    connected: profile.connected !== false,
    minecraft: "local client",
    lastSeenAt: safeProfileLastSeen,
    source: "profile"
  };
  const liveLocal = stored.find((client) => client.id === localClient.id);
  const mergedLocal = liveLocal ? { ...localClient, ...liveLocal, source: "heartbeat" } : localClient;
  const merged = [mergedLocal, ...stored.filter((client) => client.id !== localClient.id)];
  return merged.map((client) => {
    const lastSeen = Date.parse(client.lastSeenAt || 0);
    const heartbeatFresh = Number.isFinite(lastSeen) && now - lastSeen < 120000;
    return {
      ...client,
      connected: client.source === "heartbeat" ? heartbeatFresh : client.connected !== false && heartbeatFresh
    };
  });
}

async function upsertClientHeartbeat(input) {
  const now = new Date().toISOString();
  const id = String(input?.id || input?.displayName || "local-user").trim() || "local-user";
  const clients = await connectedClients();
  const next = {
    id,
    displayName: String(input?.displayName || "EnderUser"),
    tier: String(input?.tier || "Premium"),
    status: String(input?.status || "Connected"),
    minecraft: String(input?.minecraft || "unknown"),
    server: String(input?.server || "Singleplayer"),
    world: String(input?.world || "No world"),
    position: String(input?.position || "-"),
    fps: Math.max(0, Number(input?.fps || 0)),
    activeScript: String(input?.activeScript || ""),
    rendererActive: input?.rendererActive !== false,
    overlays: Array.isArray(input?.overlays) ? input.overlays.map(String) : [],
    connected: true,
    lastSeenAt: now,
    source: "heartbeat"
  };
  const rest = clients.filter((client) => client.id !== id && client.source !== "profile");
  await writeJson(clientsPath, [next, ...rest]);
  return next;
}

async function publishLibraryScript(input) {
  const item = normalizeLibraryScript(input);
  item.id = input?.id ? slug(input.id) : await uniqueLibraryId(item.name);
  item.publishedAt = Date.now();
  item.updatedAt = item.publishedAt;
  const items = await libraryScripts();
  items.unshift(item);
  await saveLibraryScripts(items);
  return item;
}

async function installLibraryScript(id) {
  const items = await libraryScripts();
  const item = items.find((candidate) => candidate.id === id);
  if (!item) throw new Error("Published script not found");
  const target = await uniqueScriptPath(item.fileName);
  await fs.writeFile(target, item.code, "utf8");
  item.downloads += 1;
  item.updatedAt = Date.now();
  await saveLibraryScripts(items);
  return scriptSummary(target);
}

function normalizeTemplate(template) {
  const fallback = defaultTemplates()[0];
  const safeName = String(template?.name || fallback.name).trim() || fallback.name;
  return {
    id: safeTemplateId(template?.id || safeName),
    name: safeName,
    category: String(template?.category || fallback.category),
    description: String(template?.description || fallback.description),
    difficulty: String(template?.difficulty || fallback.difficulty),
    blocks: Number(template?.blocks || fallback.blocks),
    icon: String(template?.icon || fallback.icon),
    badge: template?.badge ? String(template.badge) : "",
    script: String(template?.script || fallback.script)
  };
}

async function createScriptFromTemplate(id) {
  const templates = await readJson(templatesPath, defaultTemplates());
  const template = templates.find((item) => item.id === id) || templates[0] || defaultTemplates()[0];
  const file = await uniqueScriptPath(`${template.id.replace(/(^|-)([a-z])/g, (_, __, char) => char.toUpperCase())}.py`);
  const script = String(template.script || `import minescript as ms\n\nms.echo("${template.name} loaded")\n`);
  const content = script.startsWith("#") ? script : `# ${template.name} - generated from Shulkr Templates\n${script}`;
  await fs.writeFile(file, content, "utf8");
  return scriptSummary(file);
}

function ffmpegCandidates() {
  const names = process.platform === "win32" ? ["ffmpeg.exe", "ffmpeg"] : ["ffmpeg"];
  const configured = [
    process.env.SHULKR_FFMPEG_PATH,
    process.env.FFMPEG_PATH,
    path.join(rootDir, "tools", "ffmpeg", "bin", "ffmpeg.exe"),
    path.join(rootDir, "tools", "ffmpeg", "ffmpeg.exe"),
    path.join(runDir, "ffmpeg", "bin", "ffmpeg.exe"),
    path.join(runDir, "ffmpeg", "ffmpeg.exe")
  ].filter(Boolean);
  const pathDirs = String(process.env.PATH || "")
    .split(path.delimiter)
    .filter(Boolean)
    .flatMap((dir) => names.map((name) => path.join(dir, name)));
  return [...configured, ...pathDirs, ...names];
}

function findFfmpeg() {
  for (const candidate of ffmpegCandidates()) {
    try {
      if (candidate.includes(path.sep) || candidate.includes("/")) {
        if (fsSync.existsSync(candidate)) return candidate;
      } else {
        return candidate;
      }
    } catch {
    }
  }
  return "";
}

function streamStatus() {
  const ffmpegPath = findFfmpeg();
  return {
    available: Boolean(ffmpegPath),
    running: Boolean(streamState.process),
    clients: streamState.clients.size,
    startedAt: streamState.startedAt,
    lastError: streamState.lastError,
    mode: streamState.mode,
    fps: streamState.fps,
    captureRect: streamState.captureRect,
    captureTitle: streamState.captureTitle,
    source: streamState.mode === "desktop" ? "Desktop capture" : "Minecraft window capture",
    ffmpegPath: ffmpegPath || "",
    help: ffmpegPath
      ? ""
      : "Install FFmpeg or put ffmpeg.exe at tools/ffmpeg/bin/ffmpeg.exe, or set SHULKR_FFMPEG_PATH."
  };
}

function findMinecraftWindowRect(titleHint = "") {
  if (process.platform !== "win32") return null;
  const safeTitle = JSON.stringify(String(titleHint || "Minecraft"));
  const script = `
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShulkrWindowRect {
  [DllImport("user32.dll")]
  public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
  [DllImport("user32.dll")]
  public static extern int GetSystemMetrics(int nIndex);
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@
$hint = ${safeTitle}
$window = Get-Process | Where-Object {
  $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -and (
    $_.MainWindowTitle -like "*Minecraft*" -or
    $_.MainWindowTitle -like "*Shulkr*" -or
    $_.ProcessName -in @("java", "javaw") -or
    ($hint -and $_.MainWindowTitle -like $hint)
  )
} | Sort-Object @{Expression={
  if ($_.MainWindowTitle -like "*Minecraft*" -or $_.MainWindowTitle -like "*Shulkr*") { 0 }
  elseif ($_.ProcessName -in @("java", "javaw")) { 1 }
  else { 2 }
}} | Select-Object -First 1
if ($window) {
  $rect = New-Object ShulkrWindowRect+RECT
  [ShulkrWindowRect]::GetWindowRect($window.MainWindowHandle, [ref]$rect) | Out-Null
  $virtualX = [ShulkrWindowRect]::GetSystemMetrics(76)
  $virtualY = [ShulkrWindowRect]::GetSystemMetrics(77)
  $virtualW = [ShulkrWindowRect]::GetSystemMetrics(78)
  $virtualH = [ShulkrWindowRect]::GetSystemMetrics(79)
  $left = [Math]::Max($virtualX, $rect.Left)
  $top = [Math]::Max($virtualY, $rect.Top)
  $right = [Math]::Min($virtualX + $virtualW, $rect.Right)
  $bottom = [Math]::Min($virtualY + $virtualH, $rect.Bottom)
  $width = [Math]::Max(1, $right - $left)
  $height = [Math]::Max(1, $bottom - $top)
  [pscustomobject]@{
    title = $window.MainWindowTitle
    x = $left
    y = $top
    width = $width
    height = $height
  } | ConvertTo-Json -Compress
}
`;
  try {
    const output = execFileSync("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-Command", script
    ], {
      encoding: "utf8",
      timeout: 3000,
      windowsHide: true
    }).trim();
    if (!output) return null;
    const rect = JSON.parse(output);
    if (!rect || rect.width < 120 || rect.height < 90) return null;
    return rect;
  } catch (error) {
    streamState.lastError = `Window rect lookup failed: ${error.message}`;
    return null;
  }
}

function ffmpegArgs(options = {}) {
  const fps = Math.max(5, Math.min(60, Number(options.fps || streamState.fps || 20)));
  const mode = String(options.mode || streamState.mode || "desktop");
  streamState.mode = mode;
  streamState.fps = fps;
  streamState.captureRect = null;
  streamState.captureTitle = "";
  let input = ["-f", "gdigrab", "-framerate", String(fps), "-draw_mouse", "0", "-i", "desktop"];
  if (mode === "window") {
    const rect = findMinecraftWindowRect(options.title);
    if (rect) {
      streamState.captureRect = rect;
      streamState.captureTitle = rect.title || "";
      input = [
        "-f", "gdigrab",
        "-framerate", String(fps),
        "-draw_mouse", "0",
        "-offset_x", String(rect.x),
        "-offset_y", String(rect.y),
        "-video_size", `${rect.width}x${rect.height}`,
        "-i", "desktop"
      ];
    } else {
      const title = String(options.title || "Minecraft* 26.1.2");
      streamState.captureTitle = title;
      input = ["-f", "gdigrab", "-framerate", String(fps), "-draw_mouse", "0", "-i", `title=${title}`];
    }
  }
  return [
    "-hide_banner",
    "-loglevel", "warning",
    ...input,
    "-vf", "scale=1280:-1",
    "-q:v", String(Math.max(2, Math.min(15, Number(options.quality || 7)))),
    "-f", "mjpeg",
    "pipe:1"
  ];
}

function startStream(options = {}) {
  if (streamState.process) return streamStatus();
  const ffmpegPath = findFfmpeg();
  if (!ffmpegPath) {
    streamState.lastError = "FFmpeg was not found.";
    throw new Error(streamStatus().help);
  }
  const args = ffmpegArgs(options);
  streamState.lastError = "";
  streamState.startedAt = new Date().toISOString();
  const child = spawn(ffmpegPath, args, {
    cwd: rootDir,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"]
  });
  streamState.process = child;
  child.stderr.on("data", (chunk) => {
    streamState.lastError = chunk.toString("utf8").trim().slice(-600);
  });
  child.on("exit", (code, signal) => {
    if (streamState.process === child) {
      streamState.process = null;
      streamState.startedAt = null;
    }
    if (code && code !== 0) {
      streamState.lastError = streamState.lastError || `FFmpeg exited with code ${code}${signal ? ` (${signal})` : ""}.`;
    }
    for (const res of streamState.clients) {
      try {
        res.end();
      } catch {
      }
    }
    streamState.clients.clear();
  });
  return streamStatus();
}

function stopStream() {
  if (streamState.process) {
    const child = streamState.process;
    streamState.process = null;
    try {
      child.kill("SIGTERM");
    } catch {
    }
  }
  streamState.startedAt = null;
  for (const res of streamState.clients) {
    try {
      res.end();
    } catch {
    }
  }
  streamState.clients.clear();
  return streamStatus();
}

function pipeMjpeg(res) {
  if (!streamState.process) startStream();
  const child = streamState.process;
  const boundary = "shulkr-frame";
  let buffer = Buffer.alloc(0);
  res.writeHead(200, {
    "Cache-Control": "no-store, no-cache, must-revalidate, proxy-revalidate",
    "Connection": "close",
    "Content-Type": `multipart/x-mixed-replace; boundary=${boundary}`,
    "Pragma": "no-cache"
  });
  streamState.clients.add(res);
  const onData = (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (true) {
      const start = buffer.indexOf(Buffer.from([0xff, 0xd8]));
      const end = buffer.indexOf(Buffer.from([0xff, 0xd9]), start + 2);
      if (start < 0 || end < 0) {
        if (buffer.length > 1024 * 1024) buffer = buffer.subarray(-1024 * 256);
        return;
      }
      const frame = buffer.subarray(start, end + 2);
      buffer = buffer.subarray(end + 2);
      res.write(`--${boundary}\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.length}\r\n\r\n`);
      res.write(frame);
      res.write("\r\n");
    }
  };
  const cleanup = () => {
    streamState.clients.delete(res);
    child.stdout.off("data", onData);
  };
  child.stdout.on("data", onData);
  res.on("close", cleanup);
}

function signAuthToken(user) {
  return jwt.sign({ id: user.id, email: user.email, displayName: user.displayName }, jwtSecret || "shulkr-dev-secret-change-me", { expiresIn: "7d" });
}

function verifyAuthToken(token) {
  try {
    return jwt.verify(token, jwtSecret || "shulkr-dev-secret-change-me");
  } catch {
    return null;
  }
}

async function syncGoogleProfile(user) {
  const current = await readJson(profilePath, defaultProfile());
  const next = {
    ...current,
    id: user.id,
    displayName: user.displayName,
    email: user.email,
    avatar: user.avatar || current.avatar,
    connected: true,
    lastSeenAt: new Date().toISOString()
  };
  await writeJson(profilePath, next);
  const licenses = await readJson(licensesPath, defaultLicenses());
  if (!licenses.some((license) => license.userId === user.id)) {
    licenses.push({
      id: `google-${user.id}`,
      userId: user.id,
      displayName: user.displayName,
      tier: "Premium",
      status: "active",
      seats: 1,
      expiresAt: null,
      features: ["client", "editor", "script-library", "templates", "modules"]
    });
    await writeJson(licensesPath, licenses);
  }
  return next;
}

function authMiddleware(req, res, next) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : (req.query.token || "");
  const payload = verifyAuthToken(token);
  if (!payload) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  req.user = payload;
  next();
}

app.get("/api/health", async (_req, res) => {
  res.json({
    ok: true,
    app: "shulkr",
    backend: "express",
    port,
    rootDir,
    dataDir,
    scriptDir,
    auth: { google: Boolean(googleClientId && googleClientSecret), local: true }
  });
});

app.post("/api/auth/local/signin", async (req, res, next) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) throw new Error("Email and password are required");
    const user = await findLocalUser(String(email));
    if (!user || user.password !== String(password)) throw new Error("Invalid email or password");
    const profile = await syncLocalProfile(user);
    const token = signAuthToken({ id: user.id, email: user.email, displayName: user.displayName });
    res.json({ token, user: { id: user.id, displayName: user.displayName, email: user.email, tier: profile.tier || "Premium" } });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/local/signup", async (req, res, next) => {
  try {
    const { displayName, email, password } = req.body || {};
    if (!email || !password) throw new Error("Email and password are required");
    if (password.length < 4) throw new Error("Password must be at least 4 characters");
    const user = await createLocalUser(String(displayName || ""), String(email), String(password));
    const profile = await syncLocalProfile(user);
    const token = signAuthToken({ id: user.id, email: user.email, displayName: user.displayName });
    res.status(201).json({ token, user: { id: user.id, displayName: user.displayName, email: user.email, tier: profile.tier || "Premium" } });
  } catch (error) {
    next(error);
  }
});

app.get("/auth/google", (req, res, next) => {
  if (!googleClientId || !googleClientSecret) {
    return res.status(503).json({ error: "Google OAuth is not configured. Set SHULKR_GOOGLE_CLIENT_ID and SHULKR_GOOGLE_CLIENT_SECRET." });
  }
  passport.authenticate("google", { scope: ["profile", "email"] })(req, res, next);
});

app.get("/auth/google/callback",
  passport.authenticate("google", { failureRedirect: `${webClientUrl}/?auth=error` }),
  async (req, res) => {
    const profile = await syncGoogleProfile(req.user);
    const token = signAuthToken(req.user);
    res.redirect(`${webClientUrl}/?token=${encodeURIComponent(token)}&name=${encodeURIComponent(profile.displayName)}&email=${encodeURIComponent(profile.email || "")}`);
  }
);

app.get("/api/auth/me", authMiddleware, async (req, res) => {
  const profile = await readJson(profilePath, defaultProfile());
  res.json({
    id: req.user.id,
    displayName: req.user.displayName,
    email: req.user.email,
    username: profile.displayName || req.user.displayName,
    tier: profile.tier || "Premium",
    avatar: profile.avatar || ""
  });
});

app.get("/api/profile", async (_req, res) => {
  res.json(await readJson(profilePath, defaultProfile()));
});

app.get("/api/clients", async (_req, res) => {
  res.json(await connectedClients());
});

app.get("/api/licenses", async (_req, res) => {
  res.json(await readJson(licensesPath, defaultLicenses()));
});

app.get("/api/libraries", async (_req, res) => {
  res.json(await readJson(librariesPath, defaultLibraries()));
});

app.post("/api/licenses", async (req, res) => {
  const licenses = await readJson(licensesPath, defaultLicenses());
  const body = req.body || {};
  const id = String(body.id || `${slug(body.displayName || body.userId || "user")}-${Date.now()}`);
  const next = {
    id,
    userId: String(body.userId || id),
    displayName: String(body.displayName || "Shulkr User"),
    tier: String(body.tier || "Free"),
    status: String(body.status || "active"),
    seats: Math.max(1, Number(body.seats || 1)),
    expiresAt: body.expiresAt || null,
    features: Array.isArray(body.features) ? body.features.map(String) : ["client"]
  };
  licenses.push(next);
  await writeJson(licensesPath, licenses);
  res.status(201).json(next);
});

app.post("/api/clients/heartbeat", async (req, res) => {
  res.json(await upsertClientHeartbeat(req.body || {}));
});

app.get("/api/control/state", async (_req, res) => {
  const [clients, scriptList, clientModules] = await Promise.all([
    connectedClients(), scripts(), readJson(clientModulesPath, defaultClientModules())
  ]);
  res.json({
    clients,
    scripts: scriptList,
    modules: clientModules,
    activity: controlActivity.slice(0, 20),
    overlays: ["Target HUD", "Coordinates", "Script Status", "NBT Peek", "FPS Counter", "Player Vitals", "Crosshair Inspector"]
  });
});

app.get("/api/stats/history", async (_req, res) => {
  const history = await readJson(statsHistoryPath, []);
  res.json(history);
});

app.get("/api/stats/summary", async (_req, res) => {
  const history = await readJson(statsHistoryPath, []);
  const totalRuntime = history.reduce((sum, p) => sum + (p.runtime || 0), 0);
  const totalRuns = history.reduce((sum, p) => sum + (p.runs || 0), 0);
  const totalFailsafes = history.reduce((sum, p) => sum + (p.failsafes || 0), 0);
  const totalExp = history.reduce((sum, p) => sum + (p.exp || 0), 0);
  const totalProfit = history.reduce((sum, p) => sum + (p.profit || 0), 0);
  res.json({
    runtime: totalRuntime,
    runs: totalRuns,
    failsafes: totalFailsafes,
    exp: totalExp,
    profit: totalProfit,
    sessions: history.length
  });
});

app.get("/api/stream/status", (_req, res) => {
  res.json(streamStatus());
});

app.post("/api/stream/start", (req, res, next) => {
  try {
    res.json(startStream(req.body || {}));
  } catch (error) {
    next(error);
  }
});

app.post("/api/stream/stop", (_req, res) => {
  res.json(stopStream());
});

app.get("/api/stream/mjpeg", (_req, res, next) => {
  try {
    pipeMjpeg(res);
  } catch (error) {
    next(error);
  }
});

app.post("/api/control/commands", async (req, res, next) => {
  try {
    const type = String(req.body?.type || "");
    const allowed = new Set(["run_script", "stop_scripts", "set_overlay", "set_renderer", "open_ui", "take_screenshot"]);
    if (!allowed.has(type)) throw new Error("Unsupported control command");
    const command = {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      clientId: String(req.body?.clientId || "local-user"),
      type,
      payload: req.body?.payload && typeof req.body.payload === "object" ? req.body.payload : {},
      createdAt: new Date().toISOString()
    };
    controlCommands.push(command);
    addControlActivity("Command queued", type.replaceAll("_", " "), "pending");
    res.status(202).json(command);
  } catch (error) {
    next(error);
  }
});

app.get("/api/control/commands", async (req, res) => {
  const clientId = String(req.query.clientId || "local-user");
  const delivered = controlCommands.filter((command) => command.clientId === clientId);
  for (const command of delivered) {
    const index = controlCommands.findIndex((candidate) => candidate.id === command.id);
    if (index >= 0) controlCommands.splice(index, 1);
  }
  res.json(delivered);
});

app.post("/api/control/commands/:id/ack", async (req, res) => {
  const ok = req.body?.ok !== false;
  addControlActivity(ok ? "Command completed" : "Command failed", String(req.body?.message || req.params.id), ok ? "ok" : "error");
  await recordStatsFromAck(req.body?.payload || {});
  res.json({ ok: true });
});

async function recordStatsFromAck(payload) {
  try {
    const history = await readJson(statsHistoryPath, []);
    const today = new Date().toISOString().slice(0, 10);
    let point = history.find((p) => new Date(p.at).toISOString().slice(0, 10) === today);
    if (!point) {
      point = { at: Date.now(), runtime: 0, runs: 0, failsafes: 0, exp: 0, profit: 0 };
      history.push(point);
    }
    if (payload.runtime) point.runtime += Number(payload.runtime) || 0;
    if (payload.runs) point.runs += Number(payload.runs) || 0;
    if (payload.failsafes) point.failsafes += Number(payload.failsafes) || 0;
    if (payload.exp) point.exp += Number(payload.exp) || 0;
    if (payload.profit) point.profit += Number(payload.profit) || 0;
    await writeJson(statsHistoryPath, history.slice(-90));
  } catch (error) {
    console.error("recordStatsFromAck error", error);
  }
}

app.post("/api/profile", async (req, res) => {
  const current = await readJson(profilePath, defaultProfile());
  const next = { ...current, ...(req.body || {}), lastSeenAt: new Date().toISOString() };
  await writeJson(profilePath, next);
  res.json(next);
});

app.patch("/api/profile", async (req, res) => {
  const current = await readJson(profilePath, defaultProfile());
  const next = { ...current, ...(req.body || {}), lastSeenAt: new Date().toISOString() };
  await writeJson(profilePath, next);
  res.json(next);
});

app.get("/api/library/scripts", async (_req, res) => {
  res.json(await libraryScripts());
});

app.post("/api/library/scripts", async (req, res, next) => {
  try {
    const body = typeof req.body === "string" ? { content: req.body } : (req.body || {});
    let payload = body;
    if (body.sourcePath) {
      const source = path.resolve(String(body.sourcePath));
      if (!hasScriptExtension(source)) {
        throw new Error("Unsupported script type: " + path.basename(source));
      }
      payload = {
        ...body,
        fileName: body.fileName || path.basename(source),
        name: body.name || path.basename(source, path.extname(source)),
        code: await fs.readFile(source, "utf8")
      };
    } else if (body.base64) {
      payload = { ...body, code: Buffer.from(String(body.base64), "base64").toString("utf8") };
    }
    res.status(201).json(await publishLibraryScript(payload));
  } catch (error) {
    next(error);
  }
});

app.get("/api/library/scripts/:id", async (req, res, next) => {
  try {
    const item = (await libraryScripts()).find((script) => script.id === req.params.id);
    if (!item) throw new Error("Published script not found");
    res.json(item);
  } catch (error) {
    next(error);
  }
});

app.delete("/api/library/scripts/:id", async (req, res, next) => {
  try {
    const items = await libraryScripts();
    const nextItems = items.filter((script) => script.id !== req.params.id);
    if (nextItems.length === items.length) throw new Error("Published script not found");
    await saveLibraryScripts(nextItems);
    res.json({ ok: true, id: req.params.id });
  } catch (error) {
    next(error);
  }
});

app.post("/api/library/scripts/:id/install", async (req, res, next) => {
  try {
    res.status(201).json(await installLibraryScript(req.params.id));
  } catch (error) {
    next(error);
  }
});

app.get("/api/client-modules", async (_req, res) => {
  res.json(await readJson(clientModulesPath, defaultClientModules()));
});

app.patch("/api/client-modules/:id", async (req, res, next) => {
  try {
    const items = await readJson(clientModulesPath, defaultClientModules());
    const index = items.findIndex((item) => item.id === req.params.id);
    if (index < 0) throw new Error("Module not found");
    items[index] = { ...items[index], ...(req.body || {}) };
    if (Object.prototype.hasOwnProperty.call(req.body || {}, "installed")) {
      items[index].status = req.body.installed ? "Installed" : "Available";
    }
    await saveClientModules(items);
    res.json(items[index]);
  } catch (error) {
    next(error);
  }
});

app.get("/api/scripts", async (_req, res) => {
  res.json(await scripts());
});

app.get("/api/scripts/folders", async (_req, res) => {
  res.json(await folders());
});

app.post("/api/scripts/folders", async (req, res, next) => {
  try {
    const folder = folderPathFromRequest(req.body?.path || req.body?.name);
    await fs.mkdir(folder, { recursive: true });
    res.status(201).json({ path: normalizeSlashes(path.relative(scriptDir, folder)), name: path.basename(folder) });
  } catch (error) {
    next(error);
  }
});

app.patch("/api/scripts/folders", async (req, res, next) => {
  try {
    const from = folderPathFromRequest(req.body?.path);
    const requested = req.body?.newPath || req.body?.name;
    if (!requested) {
      throw new Error("Missing new folder name");
    }
    let target;
    if (String(requested).includes("/") || String(requested).includes("\\")) {
      target = safeInside(scriptDir, path.join(scriptDir, safeFolderRelativePath(requested)));
    } else {
      target = safeInside(path.dirname(from), path.join(path.dirname(from), safeFolderRelativePath(requested)));
    }
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.rename(from, target);
    res.json({ path: normalizeSlashes(path.relative(scriptDir, target)), name: path.basename(target) });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/scripts/folders", async (req, res, next) => {
  try {
    const folder = folderPathFromRequest(req.body?.path || req.query.path);
    await fs.rm(folder, { recursive: true, force: true });
    res.json({ ok: true, path: normalizeSlashes(path.relative(scriptDir, folder)) });
  } catch (error) {
    next(error);
  }
});

app.post("/api/scripts", async (req, res, next) => {
  try {
    const body = typeof req.body === "string" ? { content: req.body } : (req.body || {});
    let target;
    if (body.sourcePath) {
      const source = path.resolve(String(body.sourcePath));
      if (!hasScriptExtension(source)) {
        throw new Error("Unsupported script type: " + path.basename(source));
      }
      target = body.overwrite ? safeInside(scriptDir, path.join(scriptDir, safeScriptName(source))) : await uniqueScriptPath(path.basename(source));
      await fs.copyFile(source, target);
    } else {
      target = body.overwrite
        ? safeInside(scriptDir, path.join(scriptDir, safeScriptRelativePath(body.name)))
        : await uniqueScriptPath(body.name || "UploadedScript.py");
      const content = body.base64
        ? Buffer.from(String(body.base64), "base64").toString("utf8")
        : String(body.content || "");
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.writeFile(target, content, "utf8");
    }
    res.status(201).json(await scriptSummary(target));
  } catch (error) {
    next(error);
  }
});

app.patch("/api/scripts", async (req, res, next) => {
  try {
    const from = scriptPathFromRequest(req.body?.path);
    const requested = req.body?.newPath || req.body?.name;
    if (!requested) {
      throw new Error("Missing new script name");
    }
    let target;
    if (String(requested).includes("/") || String(requested).includes("\\")) {
      target = safeInside(scriptDir, path.join(scriptDir, safeScriptRelativePath(requested)));
    } else {
      target = safeInside(path.dirname(from), path.join(path.dirname(from), safeScriptRelativePath(requested)));
    }
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.rename(from, target);
    res.json(await scriptSummary(target));
  } catch (error) {
    next(error);
  }
});

app.get("/api/scripts/read", async (req, res, next) => {
  try {
    const file = scriptPathFromRequest(req.query.path);
    res.json({ path: normalizeSlashes(path.relative(scriptDir, file)), content: await fs.readFile(file, "utf8") });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/scripts", async (req, res, next) => {
  try {
    const file = scriptPathFromRequest(req.body?.path || req.query.path);
    await fs.rm(file, { force: true });
    res.json({ ok: true, path: normalizeSlashes(path.relative(scriptDir, file)) });
  } catch (error) {
    next(error);
  }
});

app.get("/api/modules", async (_req, res) => {
  res.json(await scriptModules());
});

app.get("/api/libraries/scripts", async (_req, res) => {
  const paths = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  res.json(Array.isArray(paths) ? [...new Set(paths.map(normalizeSlashes).filter(Boolean))].sort() : defaultLibraryScriptPaths());
});

app.get("/api/modules/scripts", async (_req, res) => {
  const paths = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
  res.json(Array.isArray(paths) ? [...new Set(paths.map(normalizeSlashes).filter(Boolean))].sort() : defaultLibraryScriptPaths());
});

app.patch("/api/modules/:id", async (req, res, next) => {
  try {
    const modules = await readJson(librariesPath, defaultLibraries());
    const index = modules.findIndex((item) => item.id === req.params.id);
    if (index < 0) throw new Error("Module not found");
    modules[index] = { ...modules[index], ...(req.body || {}) };
    if (Object.prototype.hasOwnProperty.call(req.body || {}, "installed")) {
      modules[index].status = req.body.installed ? "Installed" : "Available";
    }
    await saveLibraries(modules);
    res.json(modules[index]);
  } catch (error) {
    next(error);
  }
});

app.patch("/api/modules/scripts", async (req, res, next) => {
  try {
    const body = req.body || {};
    const relative = normalizeSlashes(body.path || "");
    if (!relative || relative.includes("\0")) {
      throw new Error("Missing script path");
    }
    safeInside(scriptDir, path.join(scriptDir, relative));
    const current = await readJson(libraryScriptPathsPath, defaultLibraryScriptPaths());
    const nextPaths = new Set(Array.isArray(current) ? current.map(normalizeSlashes).filter(Boolean) : defaultLibraryScriptPaths());
    if (body.module === false || body.module === "false") {
      nextPaths.delete(relative);
    } else {
      nextPaths.add(relative);
    }
    const sorted = [...nextPaths].sort();
    await writeJson(libraryScriptPathsPath, sorted);
    res.json({ path: relative, module: nextPaths.has(relative), scripts: sorted });
  } catch (error) {
    next(error);
  }
});

app.get("/api/templates", async (_req, res) => {
  res.json(await readJson(templatesPath, defaultTemplates()));
});

app.post("/api/templates", async (req, res) => {
  const templates = await readJson(templatesPath, defaultTemplates());
  const normalized = normalizeTemplate(req.body || {});
  const index = templates.findIndex((item) => item.id === normalized.id);
  if (index >= 0) {
    templates[index] = normalized;
  } else {
    templates.push(normalized);
  }
  await writeJson(templatesPath, templates);
  res.status(201).json(normalized);
});

app.put("/api/templates", async (req, res) => {
  const templates = await readJson(templatesPath, defaultTemplates());
  const normalized = normalizeTemplate(req.body || {});
  const index = templates.findIndex((item) => item.id === normalized.id);
  if (index >= 0) {
    templates[index] = normalized;
  } else {
    templates.push(normalized);
  }
  await writeJson(templatesPath, templates);
  res.json(normalized);
});

app.delete("/api/templates/:id", async (req, res, next) => {
  try {
    const templates = await readJson(templatesPath, defaultTemplates());
    const id = String(req.params.id || "");
    const nextTemplates = templates.filter((template) => template.id !== id);
    if (nextTemplates.length === templates.length) {
      throw new Error("Template not found");
    }
    await writeJson(templatesPath, nextTemplates);
    res.json({ ok: true, id });
  } catch (error) {
    next(error);
  }
});

app.post("/api/templates/use", async (req, res, next) => {
  try {
    res.status(201).json(await createScriptFromTemplate(String(req.body?.id || "")));
  } catch (error) {
    next(error);
  }
});

app.get("/api/stats", async (_req, res) => {
  const [scriptList, templates, publishedScripts, clientModules] = await Promise.all([
    scripts(),
    readJson(templatesPath, defaultTemplates()),
    libraryScripts(),
    readJson(clientModulesPath, defaultClientModules())
  ]);
  const modules = await scriptModules(scriptList);
  res.json({
    scripts: scriptList.length,
    installedModules: modules.length,
    installedLibraries: modules.length,
    installedClientModules: Array.isArray(clientModules) ? clientModules.filter((item) => item.installed !== false).length : 0,
    templates: templates.length,
    publishedScripts: publishedScripts.length,
    scriptDir,
    dataDir
  });
});

app.get("/api/snapshot", async (_req, res) => {
  res.json(await snapshotData());
});

app.use("/web", express.static(webClientDist));
app.get("/web/*", (_req, res, next) => {
  const indexPath = path.join(webClientDist, "index.html");
  if (fsSync.existsSync(indexPath)) {
    res.sendFile(indexPath);
  } else {
    next();
  }
});

app.use("/admin", express.static(publicDir));
app.get("/", (_req, res) => {
  const webIndex = path.join(webClientDist, "index.html");
  res.redirect(fsSync.existsSync(webIndex) ? "/web/" : "/admin/");
});

app.use((error, _req, res, _next) => {
  res.status(400).json({ error: error.message || "Backend error" });
});

ensureReady()
  .then(() => {
    app.listen(port, "127.0.0.1", () => {
      console.log(`Shulkr Express backend running at http://127.0.0.1:${port}`);
      console.log(`Scripts: ${scriptDir}`);
      console.log(`Data: ${dataDir}`);
    });
  })
  .catch((error) => {
    console.error("Failed to start Shulkr backend", error);
    process.exitCode = 1;
  });
