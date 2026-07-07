package dev.triton.ui.client.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.triton.ui.client.config.FluxusConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class FluxusAppState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Type MODULE_LIST = new TypeToken<List<ModuleItem>>() {}.getType();
	private static final Type CLIENT_MODULE_LIST = new TypeToken<List<ClientModuleItem>>() {}.getType();
	private static final Type TEMPLATE_LIST = new TypeToken<List<TemplateItem>>() {}.getType();
	private static final Type LIBRARY_SCRIPT_LIST = new TypeToken<List<LibraryScriptItem>>() {}.getType();
	private static final Type SCRIPT_LIST = new TypeToken<List<ScriptSummary>>() {}.getType();
	private static final Type FOLDER_LIST = new TypeToken<List<FolderSummary>>() {}.getType();
	private static final Type REMOTE_COMMAND_LIST = new TypeToken<List<RemoteCommand>>() {}.getType();
	private static final Type STRING_SET = new TypeToken<Set<String>>() {}.getType();
	private static final FluxusAppState INSTANCE = new FluxusAppState();
	private static final String BACKEND_URL = System.getProperty("shulkr.backend.url",
			System.getProperty("shulk.backend.url", "http://127.0.0.1:50991"));

	private final Path backendDataDir = Path.of(System.getProperty("user.dir"), "shulkr-backend");
	private final Path appDir = backendDataDir;
	private final Path profilePath = backendDataDir.resolve("profile.json");
	private final Path modulesPath = backendDataDir.resolve("modules.json");
	private final Path moduleScriptsPath = backendDataDir.resolve("module-scripts.json");
	private final Path templatesPath = backendDataDir.resolve("templates.json");
	private final Path libraryScriptsPath = backendDataDir.resolve("library-scripts.json");
	private final Path scriptDir = Path.of(System.getProperty("user.dir"), "minescript");
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private final ScheduledExecutorService backendHeartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Shulkr Backend Heartbeat");
		thread.setDaemon(true);
		return thread;
	});
	private Profile profile = Profile.defaults();
	private boolean backendOnline = true;
	private List<ModuleItem> modules = List.of();
	private List<ClientModuleItem> clientModules = List.of();
	private List<TemplateItem> templates = List.of();
	private List<LibraryScriptItem> libraryScripts = List.of();
	private Set<String> moduleScriptPaths = Set.of();
	private boolean heartbeatStarted;
	private final ConcurrentLinkedQueue<RemoteCommand> remoteCommands = new ConcurrentLinkedQueue<>();
	private volatile Map<String, Object> clientTelemetry = Map.of();

	public static FluxusAppState get() {
		return INSTANCE;
	}

	public synchronized void initialize() {
		try {
			Files.createDirectories(appDir);
			Files.createDirectories(backendDataDir);
			Files.createDirectories(scriptDir);
			seedIfMissing(profilePath, "/assets/triton-ui/data/profile.json");
			seedIfMissing(modulesPath, "/assets/triton-ui/data/modules.json");
			seedJsonIfMissing(moduleScriptsPath, defaultUtilityModulePaths());
			seedIfMissing(templatesPath, "/assets/triton-ui/data/templates.json");
			seedJsonIfMissing(libraryScriptsPath, List.of());
			backendOnline = probeBackend();
			reload();
			saveProfile(profile.withStatus(backendOnline ? profile.status() : "Server is offline")
					.withLastSeen(Instant.now().toString()));
			startBackendHeartbeat();
		} catch (IOException e) {
			profile = profile.withStatus("Config error: " + e.getMessage());
		}
	}

	public synchronized void reload() {
		profile = readJson(profilePath, Profile.class, Profile.defaults()).normalized();
		backendOnline = probeBackend();
		if (!backendOnline) {
			profile = profile.withStatus("Server is offline");
		}
		modules = backendList("/api/modules", MODULE_LIST, readJson(modulesPath, MODULE_LIST, List.of()));
		clientModules = backendList("/api/client-modules", CLIENT_MODULE_LIST, List.of());
		moduleScriptPaths = new HashSet<>(backendList("/api/modules/scripts", STRING_SET, readJson(moduleScriptsPath, STRING_SET, defaultUtilityModulePaths())));
		if (moduleScriptPaths.removeAll(legacyAutoModulePaths())) {
			writeJson(moduleScriptsPath, moduleScriptPaths);
		}
		if (moduleScriptPaths.isEmpty()) {
			moduleScriptPaths = new HashSet<>(defaultUtilityModulePaths());
			writeJson(moduleScriptsPath, moduleScriptPaths);
		}
		templates = backendList("/api/templates", TEMPLATE_LIST, readJson(templatesPath, TEMPLATE_LIST, List.of()));
		libraryScripts = backendList("/api/library/scripts", LIBRARY_SCRIPT_LIST, readJson(libraryScriptsPath, LIBRARY_SCRIPT_LIST, List.<LibraryScriptItem>of())).stream()
				.map(LibraryScriptItem::normalized)
				.sorted(Comparator.comparingLong(LibraryScriptItem::publishedAt).reversed())
				.toList();
		writeJson(modulesPath, modules);
		writeJson(templatesPath, templates);
		writeJson(libraryScriptsPath, libraryScripts);
	}

	public synchronized Profile profile() {
		return profile;
	}

	public synchronized boolean backendOnline() {
		return backendOnline;
	}

	public synchronized List<ClientModuleItem> clientModules() {
		return List.copyOf(clientModules);
	}

	public synchronized ClientModuleItem clientModule(String id) {
		return clientModules.stream().filter(module -> module.id().equals(id)).findFirst().orElse(null);
	}

	public synchronized ClientModuleItem setClientModuleInstalled(String id, boolean installed) {
		ClientModuleItem updated = backendPatch("/api/client-modules/" + url(id), Map.of("installed", installed), ClientModuleItem.class);
		if (updated == null) return null;
		List<ClientModuleItem> next = new ArrayList<>(clientModules);
		for (int i = 0; i < next.size(); i++) {
			if (next.get(i).id().equals(id)) {
				next.set(i, updated);
				clientModules = List.copyOf(next);
				return updated;
			}
		}
		next.add(updated);
		clientModules = List.copyOf(next);
		return updated;
	}

	public synchronized void saveProfile(Profile next) {
		profile = next;
		writeJson(profilePath, profile);
	}

	public synchronized Profile updateProfile(Map<String, ?> fields) {
		Profile next = profile;
		if (fields.containsKey("displayName")) {
			next = next.withDisplayName(String.valueOf(fields.get("displayName")));
		}
		if (fields.containsKey("tier")) {
			next = next.withTier(String.valueOf(fields.get("tier")));
		}
		if (fields.containsKey("activePage")) {
			next = next.withActivePage(String.valueOf(fields.get("activePage")));
		}
		if (fields.containsKey("uiScale")) {
			next = next.withUiScale(toInt(fields.get("uiScale"), next.uiScale()));
		}
		saveProfile(next.withLastSeen(Instant.now().toString()));
		return profile;
	}

	public synchronized List<ModuleItem> modules() {
		List<ModuleItem> result = new ArrayList<>();
		for (ScriptSummary script : localScripts()) {
			if (moduleScriptPaths.contains(normalizeRelative(script.path()))) {
				String moduleName = script.name().replaceFirst("\\.[^.]+$", "");
				result.add(new ModuleItem(
						"local:" + script.path(),
						moduleName,
						"local module",
						script.extension().toUpperCase(Locale.ROOT),
						script.description().isBlank() ? "Marked as a reusable Shulkr module." : script.description(),
						script.extension().equalsIgnoreCase("pyj") ? "Pyjinn" : "Python",
						script.extension().equalsIgnoreCase("pyj") ? "route-solid.png" : "code-solid.png",
						"Installed",
						true,
						true));
			}
		}
		return List.copyOf(result);
	}

	public synchronized boolean isScriptModule(String relativePath) {
		return moduleScriptPaths.contains(normalizeRelative(relativePath));
	}

	public synchronized void setScriptModule(String relativePath, boolean module) {
		Set<String> next = new HashSet<>(moduleScriptPaths);
		String normalized = normalizeRelative(relativePath);
		if (module) {
			next.add(normalized);
		} else {
			next.remove(normalized);
		}
		ModuleScriptResponse backendResponse = backendPatch("/api/modules/scripts", Map.of("path", normalized, "module", module), ModuleScriptResponse.class);
		if (backendResponse != null && backendResponse.scripts() != null) {
			next = new HashSet<>(backendResponse.scripts().stream().map(this::normalizeRelative).toList());
		}
		moduleScriptPaths = next;
		writeJson(moduleScriptsPath, moduleScriptPaths);
	}

	private String normalizeRelative(String relativePath) {
		return relativePath == null ? "" : relativePath.replace('\\', '/').replaceAll("^/+", "");
	}

	private Set<String> defaultUtilityModulePaths() {
		return Set.of();
	}

	private Set<String> legacyAutoModulePaths() {
		return Set.of(
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
		);
	}

	public synchronized ModuleItem setModuleInstalled(String id, boolean installed) {
		List<ModuleItem> next = new ArrayList<>(modules);
		for (int i = 0; i < next.size(); i++) {
			ModuleItem module = next.get(i);
			if (module.id().equals(id)) {
				ModuleItem updated = new ModuleItem(module.id(), module.name(), module.author(), module.version(),
						module.description(), module.category(), module.icon(), installed ? "Installed" : "Available",
						installed, module.favorite());
				next.set(i, updated);
				modules = next;
				writeJson(modulesPath, modules);
				return updated;
			}
		}
		return null;
	}

	public synchronized List<TemplateItem> templates() {
		return List.copyOf(templates);
	}

	public synchronized List<LibraryScriptItem> libraryScripts() {
		reloadLibraryScripts();
		return List.copyOf(libraryScripts);
	}

	public synchronized LibraryScriptItem libraryScript(String id) {
		reloadLibraryScripts();
		return libraryScripts.stream()
				.filter(script -> script.id().equals(id))
				.findFirst()
				.orElse(null);
	}

	public synchronized LibraryScriptItem publishLibraryScript(Path source) throws IOException {
		return publishLibraryScript(source, "", "", "", List.of(), "", "");
	}

	public synchronized LibraryScriptItem publishLibraryScript(Path source, String requestedName, String requestedAuthor,
			String requestedAbout, List<String> requestedTags, String requestedIcon, String requestedFileName) throws IOException {
		if (source == null || Files.notExists(source) || !Files.isRegularFile(source)) {
			throw new IOException("Source script does not exist");
		}
		if (!hasScriptExtension(source.getFileName().toString())) {
			throw new IOException("Unsupported script type: " + source.getFileName());
		}
		String code = Files.readString(source, StandardCharsets.UTF_8);
		String fileName = requestedFileName == null || requestedFileName.isBlank()
				? source.getFileName().toString()
				: safeScriptName(requestedFileName);
		String name = requestedName == null || requestedName.isBlank()
				? stripExtension(fileName).replace('-', ' ').replace('_', ' ').trim()
				: requestedName.trim();
		if (name.isBlank()) {
			name = fileName;
		}
		String category = scriptCategory(fileName + " " + String.join(" ", requestedTags == null ? List.of() : requestedTags));
		List<String> tags = requestedTags == null || requestedTags.isEmpty()
				? List.of(extension(fileName).toUpperCase(Locale.ROOT), category)
				: requestedTags.stream().map(String::trim).filter(tag -> !tag.isBlank()).distinct().toList();
		if (tags.isEmpty()) {
			tags = List.of(extension(fileName).toUpperCase(Locale.ROOT), category);
		}
		LibraryScriptItem item = new LibraryScriptItem(
				uniqueLibraryId(name),
				name,
				requestedAuthor == null || requestedAuthor.isBlank() ? profile.displayName() : requestedAuthor.trim(),
				requestedAbout == null || requestedAbout.isBlank() ? descriptionFromContent(code) : requestedAbout.trim(),
				category,
				tags,
				"1.0.0",
				requestedIcon == null || requestedIcon.isBlank() ? scriptIconName(fileName) : requestedIcon.trim(),
				fileName,
				code,
				0,
				0,
				System.currentTimeMillis(),
				System.currentTimeMillis()
		).normalized();
		LibraryScriptItem backendItem = backendPost("/api/library/scripts", item, LibraryScriptItem.class);
		if (backendItem != null) {
			reloadLibraryScripts();
			return backendItem.normalized();
		}
		List<LibraryScriptItem> next = new ArrayList<>(libraryScripts());
		next.add(item);
		libraryScripts = next;
		writeJson(libraryScriptsPath, libraryScripts);
		return item;
	}

	public synchronized ScriptSummary installLibraryScript(String id) throws IOException {
		ScriptSummary installed = backendPost("/api/library/scripts/" + id + "/install", Map.of(), ScriptSummary.class);
		if (installed != null) {
			return installed;
		}
		LibraryScriptItem item = libraryScript(id);
		if (item == null) {
			throw new IOException("Script is not published: " + id);
		}
		return writeScript(item.fileName(), item.code(), false);
	}

	public synchronized boolean deleteLibraryScript(String id) {
		if (backendDelete("/api/library/scripts/" + id)) {
			reloadLibraryScripts();
			return true;
		}
		reloadLibraryScripts();
		List<LibraryScriptItem> next = libraryScripts.stream()
				.filter(script -> !script.id().equals(id))
				.toList();
		boolean deleted = next.size() != libraryScripts.size();
		if (deleted) {
			libraryScripts = next;
			writeJson(libraryScriptsPath, libraryScripts);
		}
		return deleted;
	}

	public synchronized TemplateItem template(String id) {
		return templates.stream()
				.filter(template -> template.id().equals(id))
				.findFirst()
				.orElse(templates.isEmpty() ? TemplateItem.defaults() : templates.getFirst());
	}

	public synchronized TemplateItem upsertTemplate(TemplateItem template) {
		TemplateItem normalized = template.normalized();
		List<TemplateItem> next = new ArrayList<>(templates);
		boolean replaced = false;
		for (int i = 0; i < next.size(); i++) {
			if (next.get(i).id().equals(normalized.id())) {
				next.set(i, normalized);
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			next.add(normalized);
		}
		templates = next;
		writeJson(templatesPath, templates);
		return normalized;
	}

	public synchronized ScriptSummary createScriptFromTemplate(String templateId) throws IOException {
		TemplateItem template = template(templateId);
		Files.createDirectories(scriptDir);
		String fileName = uniqueScriptName(slugToTitle(template.id()) + ".py");
		Path target = scriptDir.resolve(fileName);
		String script = template.script() == null || template.script().isBlank()
				? "# " + template.name() + "\n\nimport minescript as ms\n\nms.echo(\"" + template.name().replace("\"", "\\\"") + " loaded\")\n"
				: template.script();
		if (!script.startsWith("#")) {
			script = "# " + template.name() + " - generated from Shulkr Templates\n" + script;
		}
		Files.writeString(target, script, StandardCharsets.UTF_8);
		return summaryFor(target);
	}

	public List<ScriptSummary> scripts() {
		return backendList("/api/scripts", SCRIPT_LIST, localScripts());
	}

	private List<ScriptSummary> localScripts() {
		List<ScriptSummary> result = new ArrayList<>();
		try {
			Files.createDirectories(scriptDir);
			try (var stream = Files.walk(scriptDir, 3)) {
				stream.filter(Files::isRegularFile)
						.filter(path -> !isHiddenScriptPath(path))
						.filter(path -> hasScriptExtension(path.getFileName().toString()))
						.sorted(Comparator.comparing(path -> scriptDir.relativize(path).toString().toLowerCase(Locale.ROOT)))
						.forEach(path -> result.add(summaryFor(path)));
			}
		} catch (IOException ignored) {
		}
		return result;
	}

	public List<FolderSummary> scriptFolders() {
		return backendList("/api/scripts/folders", FOLDER_LIST, localScriptFolders());
	}

	private List<FolderSummary> localScriptFolders() {
		List<FolderSummary> result = new ArrayList<>();
		try {
			Files.createDirectories(scriptDir);
			try (var stream = Files.walk(scriptDir, 2)) {
				stream.filter(Files::isDirectory)
						.filter(path -> !path.equals(scriptDir))
						.filter(path -> !isHiddenScriptPath(path.resolve("placeholder.py")))
						.sorted(Comparator.comparing(path -> scriptDir.relativize(path).toString().toLowerCase(Locale.ROOT)))
						.forEach(path -> result.add(new FolderSummary(scriptDir.relativize(path).toString().replace('\\', '/'), path.getFileName().toString())));
			}
		} catch (IOException ignored) {
		}
		return result;
	}

	public synchronized String readScript(String relativePath) throws IOException {
		ReadScriptResponse response = backendGet("/api/scripts/read?path=" + url(relativePath), ReadScriptResponse.class);
		if (response != null) {
			return response.content() == null ? "" : response.content();
		}
		Path path = scriptDir.resolve(relativePath).normalize();
		if (!path.startsWith(scriptDir)) {
			throw new IOException("Script path must stay inside " + scriptDir);
		}
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	public synchronized ScriptSummary writeScript(String requestedName, String content, boolean overwrite) throws IOException {
		Set<Path> existing = snapshotLocalScriptFiles();
		long requestStartedAt = System.currentTimeMillis();
		ScriptSummary backendSummary = backendPost("/api/scripts", Map.of(
				"name", requestedName == null || requestedName.isBlank() ? "UploadedScript.py" : requestedName,
				"content", content == null ? "" : content,
				"overwrite", overwrite
		), ScriptSummary.class);
		if (backendSummary != null) {
			return backendSummary;
		}
		ScriptSummary backendFallback = detectBackendCreatedScript(existing, requestedName, requestStartedAt);
		if (backendFallback != null) {
			return backendFallback;
		}
		Files.createDirectories(scriptDir);
		String safeName = safeScriptName(requestedName == null || requestedName.isBlank() ? "UploadedScript.py" : requestedName);
		Path target = scriptDir.resolve(safeName).normalize();
		if (!target.startsWith(scriptDir)) {
			throw new IOException("Script path must stay inside " + scriptDir);
		}
		if (!overwrite) {
			target = scriptDir.resolve(uniqueScriptName(target.getFileName().toString()));
		}
		Files.createDirectories(target.getParent());
		Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
		return summaryFor(target);
	}

	public synchronized ScriptSummary writeScriptBase64(String requestedName, String base64, boolean overwrite) throws IOException {
		byte[] decoded = Base64.getDecoder().decode(base64 == null ? "" : base64);
		return writeScript(requestedName, new String(decoded, StandardCharsets.UTF_8), overwrite);
	}

	public synchronized ScriptSummary importScript(Path source, boolean overwrite) throws IOException {
		if (source == null || Files.notExists(source) || !Files.isRegularFile(source)) {
			throw new IOException("Source script does not exist");
		}
		if (!hasScriptExtension(source.getFileName().toString())) {
			throw new IOException("Unsupported script type: " + source.getFileName());
		}
		Set<Path> existing = snapshotLocalScriptFiles();
		long requestStartedAt = System.currentTimeMillis();
		ScriptSummary backendSummary = backendPost("/api/scripts", Map.of(
				"sourcePath", source.toAbsolutePath().toString(),
				"overwrite", overwrite
		), ScriptSummary.class);
		if (backendSummary != null) {
			return backendSummary;
		}
		ScriptSummary backendFallback = detectBackendCreatedScript(existing, source.getFileName().toString(), requestStartedAt);
		if (backendFallback != null) {
			return backendFallback;
		}
		Files.createDirectories(scriptDir);
		String safeName = safeScriptName(source.getFileName().toString());
		Path target = scriptDir.resolve(overwrite ? safeName : uniqueScriptName(safeName));
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		return summaryFor(target);
	}

	public synchronized boolean deleteScript(String relativePath) {
		if (backendDelete("/api/scripts?path=" + url(relativePath))) {
			return true;
		}
		try {
			Path target = scriptDir.resolve(relativePath).normalize();
			if (!target.startsWith(scriptDir)) {
				return false;
			}
			return Files.deleteIfExists(target);
		} catch (IOException e) {
			return false;
		}
	}

	public synchronized ScriptSummary renameScript(String relativePath, String requestedName) throws IOException {
		ScriptSummary backendSummary = backendPatch("/api/scripts", Map.of("path", relativePath, "name", requestedName), ScriptSummary.class);
		if (backendSummary != null) {
			return backendSummary;
		}
		Path source = scriptDir.resolve(relativePath).normalize();
		Path target = source.resolveSibling(requestedName);
		if (!source.startsWith(scriptDir) || !target.startsWith(scriptDir)) {
			throw new IOException("Script path must stay inside " + scriptDir);
		}
		Files.move(source, target);
		return summaryFor(target);
	}

	public synchronized FolderSummary createFolder(String relativePath) throws IOException {
		FolderSummary backendSummary = backendPost("/api/scripts/folders", Map.of("path", relativePath), FolderSummary.class);
		if (backendSummary != null) {
			return backendSummary;
		}
		Path target = scriptDir.resolve(relativePath).normalize();
		if (!target.startsWith(scriptDir)) {
			throw new IOException("Folder path must stay inside " + scriptDir);
		}
		Files.createDirectories(target);
		return new FolderSummary(scriptDir.relativize(target).toString().replace('\\', '/'), target.getFileName().toString());
	}

	public synchronized FolderSummary renameFolder(String relativePath, String requestedName) throws IOException {
		FolderSummary backendSummary = backendPatch("/api/scripts/folders", Map.of("path", relativePath, "name", requestedName), FolderSummary.class);
		if (backendSummary != null) {
			return backendSummary;
		}
		Path source = scriptDir.resolve(relativePath).normalize();
		Path target = source.resolveSibling(requestedName);
		if (!source.startsWith(scriptDir) || !target.startsWith(scriptDir)) {
			throw new IOException("Folder path must stay inside " + scriptDir);
		}
		Files.move(source, target);
		return new FolderSummary(scriptDir.relativize(target).toString().replace('\\', '/'), target.getFileName().toString());
	}

	public AppStats stats() {
		List<ScriptSummary> scripts = scripts();
		long installedModules = modules().stream().filter(ModuleItem::installed).count();
		return new AppStats(scripts.size(), installedModules, templates().size(), scriptDir.toString(), appDir.toString());
	}

	public Snapshot snapshot(FluxusConfig config) {
		return new Snapshot(profile(), stats(), scripts(), modules(), templates(), config);
	}

	public Path appDir() {
		return appDir;
	}

	public Path scriptDir() {
		return scriptDir;
	}

	public String toJson(Object value) {
		return GSON.toJson(value);
	}

	private synchronized void startBackendHeartbeat() {
		if (heartbeatStarted) {
			return;
		}
		heartbeatStarted = true;
		backendHeartbeat.scheduleAtFixedRate(this::sendBackendHeartbeat, 0, 5, TimeUnit.SECONDS);
		backendHeartbeat.scheduleAtFixedRate(this::fetchRemoteCommands, 1, 1, TimeUnit.SECONDS);
	}

	public void updateClientTelemetry(Map<String, Object> telemetry) {
		clientTelemetry = telemetry == null ? Map.of() : Map.copyOf(telemetry);
	}

	public List<RemoteCommand> drainRemoteCommands() {
		List<RemoteCommand> drained = new ArrayList<>();
		RemoteCommand command;
		while ((command = remoteCommands.poll()) != null) drained.add(command);
		return drained;
	}

	public void acknowledgeRemoteCommand(RemoteCommand command, boolean ok, String message) {
		if (command == null) return;
		HttpRequest request = HttpRequest.newBuilder()
				.uri(backendUri("/api/control/commands/" + url(command.id()) + "/ack"))
				.timeout(Duration.ofSeconds(3))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("ok", ok, "message", message == null ? "" : message)), StandardCharsets.UTF_8))
				.build();
		httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
	}

	private void fetchRemoteCommands() {
		if (!backendOnline) return;
		List<RemoteCommand> fetched = backendList("/api/control/commands?clientId=" + url(profile.id()), REMOTE_COMMAND_LIST, List.of());
		remoteCommands.addAll(fetched);
	}

	private void sendBackendHeartbeat() {
		Profile current;
		synchronized (this) {
			current = profile.normalized().withLastSeen(Instant.now().toString());
			profile = current;
			writeJson(profilePath, profile);
		}
		Map<String, Object> payload = new HashMap<>(clientTelemetry);
		payload.put("id", current.id());
		payload.put("displayName", current.displayName());
		payload.put("tier", current.tier());
		payload.put("status", "Connected");
		payload.put("minecraft", "Minecraft " + System.getProperty("minecraft.version", "26.1.2"));
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(BACKEND_URL + "/api/clients/heartbeat"))
				.timeout(Duration.ofSeconds(3))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
				.build();
		httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
	}

	private boolean probeBackend() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri("/api/health"))
					.timeout(Duration.ofSeconds(2))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	private URI backendUri(String path) {
		return URI.create(BACKEND_URL + path);
	}

	private <T> T backendList(String path, Type type, T fallback) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri(path))
					.timeout(Duration.ofSeconds(2))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				T value = GSON.fromJson(response.body(), type);
				return value == null ? fallback : value;
			}
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		return fallback;
	}

	private <T> T backendGet(String path, Class<T> type) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri(path))
					.timeout(Duration.ofSeconds(3))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return GSON.fromJson(response.body(), type);
			}
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		return null;
	}

	private <T> T backendPost(String path, Object payload, Class<T> type) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri(path))
					.timeout(Duration.ofSeconds(4))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return GSON.fromJson(response.body(), type);
			}
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		return null;
	}

	private <T> T backendPatch(String path, Object payload, Class<T> type) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri(path))
					.timeout(Duration.ofSeconds(4))
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return GSON.fromJson(response.body(), type);
			}
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		return null;
	}

	private boolean backendDelete(String path) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(backendUri(path))
					.timeout(Duration.ofSeconds(3))
					.DELETE()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (IOException | InterruptedException | RuntimeException ignored) {
			if (ignored instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		return false;
	}

	private String url(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private ScriptSummary summaryFor(Path path) {
		try {
			Path relative = scriptDir.relativize(path);
			long modified = Files.getLastModifiedTime(path).toMillis();
			long size = Files.size(path);
			return new ScriptSummary(relative.toString().replace('\\', '/'), path.getFileName().toString(), extension(path.getFileName().toString()), size, modified, descriptionFor(path));
		} catch (IOException e) {
			return new ScriptSummary(path.getFileName().toString(), path.getFileName().toString(), extension(path.getFileName().toString()), 0, 0, "");
		}
	}

	private String descriptionFor(Path path) {
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String trimmed = line.trim();
				if (trimmed.startsWith("#")) {
					String comment = trimmed.replaceFirst("^#+", "").trim();
					if (!comment.isBlank()) {
						return comment;
					}
				}
				if (!trimmed.isBlank() && !trimmed.startsWith("import ") && !trimmed.startsWith("from ")) {
					return trimmed.length() > 90 ? trimmed.substring(0, 87) + "..." : trimmed;
				}
			}
		} catch (IOException ignored) {
		}
		return "";
	}

	private void seedIfMissing(Path target, String resource) throws IOException {
		if (Files.exists(target)) {
			return;
		}
		try (InputStream input = FluxusAppState.class.getResourceAsStream(resource)) {
			if (input == null) {
				throw new IOException("Missing resource " + resource);
			}
			Files.copy(input, target);
		}
	}

	private <T> T readJson(Path path, Class<T> type, T fallback) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			T value = GSON.fromJson(reader, type);
			return value == null ? fallback : value;
		} catch (IOException e) {
			return fallback;
		}
	}

	private <T> T readJson(Path path, Type type, T fallback) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			T value = GSON.fromJson(reader, type);
			return value == null ? fallback : value;
		} catch (IOException e) {
			return fallback;
		}
	}

	private void writeJson(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private void seedJsonIfMissing(Path target, Object value) throws IOException {
		if (Files.exists(target)) {
			return;
		}
		writeJson(target, value);
	}

	private void reloadLibraryScripts() {
		libraryScripts = readJson(libraryScriptsPath, LIBRARY_SCRIPT_LIST, List.<LibraryScriptItem>of()).stream()
				.map(LibraryScriptItem::normalized)
				.sorted(Comparator.comparingLong(LibraryScriptItem::publishedAt).reversed())
				.toList();
	}

	private boolean hasScriptExtension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".py") || lower.endsWith(".pyj") || lower.endsWith(".lua") || lower.endsWith(".js") || lower.endsWith(".txt");
	}

	private boolean isHiddenScriptPath(Path path) {
		Path relative = scriptDir.relativize(path);
		if (relative.getNameCount() == 0) {
			return false;
		}
		String root = relative.getName(0).toString().toLowerCase(Locale.ROOT);
		if (root.equals("system") || root.equals("templates") || root.equals("plugins")
				|| root.equals("plugins_disabled") || root.equals("exports") || root.equals("blockpacks")) {
			return true;
		}
		return relative.getFileName().toString().equalsIgnoreCase("config.txt");
	}

	private String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	private String uniqueScriptName(String fileName) {
		Path base = scriptDir.resolve(fileName);
		if (!Files.exists(base)) {
			return fileName;
		}
		String stem = fileName;
		String ext = "";
		int dot = fileName.lastIndexOf('.');
		if (dot > 0) {
			stem = fileName.substring(0, dot);
			ext = fileName.substring(dot);
		}
		for (int i = 2; i < 1000; i++) {
			String candidate = stem + "-" + i + ext;
			if (!Files.exists(scriptDir.resolve(candidate))) {
				return candidate;
			}
		}
		return stem + "-" + System.currentTimeMillis() + ext;
	}

	private String safeScriptName(String fileName) {
		String normalized = fileName.replace('\\', '/');
		int slash = normalized.lastIndexOf('/');
		if (slash >= 0) {
			normalized = normalized.substring(slash + 1);
		}
		normalized = normalized.replaceAll("[\\r\\n\\t]", "").trim();
		if (normalized.isBlank()) {
			normalized = "UploadedScript.py";
		}
		if (!hasScriptExtension(normalized)) {
			normalized += ".py";
		}
		return normalized;
	}

	private String uniqueLibraryId(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (base.isBlank()) {
			base = "script";
		}
		String candidate = base;
		int suffix = 2;
		while (libraryScript(candidate) != null) {
			candidate = base + "-" + suffix++;
		}
		return candidate;
	}

	private Set<Path> snapshotLocalScriptFiles() {
		Set<Path> files = new HashSet<>();
		try {
			Files.createDirectories(scriptDir);
			try (var stream = Files.walk(scriptDir, 3)) {
				stream.filter(Files::isRegularFile)
						.filter(path -> !isHiddenScriptPath(path))
						.filter(path -> hasScriptExtension(path.getFileName().toString()))
						.map(Path::normalize)
						.forEach(files::add);
			}
		} catch (IOException ignored) {
		}
		return files;
	}

	private ScriptSummary detectBackendCreatedScript(Set<Path> existing, String requestedName, long requestStartedAt) {
		String requestedFileName = requestedName == null || requestedName.isBlank()
				? "UploadedScript.py"
				: Path.of(requestedName.replace('\\', '/')).getFileName().toString();
		String requestedStem = stripExtension(requestedFileName);
		String requestedExtension = extension(requestedFileName);
		try {
			try (var stream = Files.walk(scriptDir, 3)) {
				return stream.filter(Files::isRegularFile)
						.filter(path -> !isHiddenScriptPath(path))
						.filter(path -> hasScriptExtension(path.getFileName().toString()))
						.map(Path::normalize)
						.filter(path -> !existing.contains(path))
						.filter(path -> matchesRequestedScript(path.getFileName().toString(), requestedStem, requestedExtension))
						.filter(path -> modifiedAt(path) >= requestStartedAt - 1500L)
						.max(Comparator.comparingLong(this::modifiedAt))
						.map(this::summaryFor)
						.orElse(null);
			}
		} catch (IOException ignored) {
			return null;
		}
	}

	private boolean matchesRequestedScript(String fileName, String requestedStem, String requestedExtension) {
		String ext = extension(fileName);
		if (!ext.equalsIgnoreCase(requestedExtension)) {
			return false;
		}
		String stem = stripExtension(fileName);
		return stem.equals(requestedStem) || stem.startsWith(requestedStem + "-");
	}

	private long modifiedAt(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException ignored) {
			return Long.MIN_VALUE;
		}
	}

	private String descriptionFromContent(String code) {
		for (String line : code.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("#")) {
				String comment = trimmed.replaceFirst("^#+", "").trim();
				if (!comment.isBlank()) {
					return comment;
				}
			}
		}
		return "Published Shulkr script.";
	}

	private String scriptCategory(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.contains("farm") || lower.contains("crop") || lower.contains("mine")) return "Farming";
		if (lower.contains("combat") || lower.contains("killaura") || lower.contains("crystal")) return "Combat";
		if (lower.contains("build") || lower.contains("chunk") || lower.contains("world")) return "World";
		if (lower.contains("speed") || lower.contains("nofall") || lower.contains("fullbright") || lower.contains("haste")
				|| lower.contains("jump") || lower.contains("fire") || lower.contains("water") || lower.contains("saturation")
				|| lower.contains("cleanup") || lower.contains("chat") || lower.contains("sort") || lower.contains("inventory")
				|| lower.contains("config")) return "Utility";
		return "Other";
	}

	private String scriptIconName(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.contains("speed") || lower.contains("jump")) return "route-solid.png";
		if (lower.contains("nofall") || lower.contains("fire") || lower.contains("water")) return "droplet-solid.png";
		if (lower.contains("fullbright")) return "bell-solid.png";
		if (lower.contains("haste") || lower.contains("cleanup")) return "broom-solid.png";
		if (lower.contains("farm")) return "user-solid.png";
		if (lower.contains("combat") || lower.contains("mine")) return "broom-solid.png";
		if (lower.contains("build") || lower.contains("chunk")) return "box-open-solid.png";
		if (lower.contains("chat")) return "code-solid.png";
		return "code-solid.png";
	}

	private String slugToTitle(String id) {
		StringBuilder builder = new StringBuilder();
		for (String part : id.split("[-_\\s]+")) {
			if (part.isBlank()) {
				continue;
			}
			builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) {
				builder.append(part.substring(1));
			}
		}
		return builder.isEmpty() ? "TemplateScript" : builder.toString();
	}

	private int toInt(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public record Profile(String id, String displayName, String tier, String avatar, String activePage,
			boolean connected, int uiScale, String status, String createdAt, String lastSeenAt) {
		public static Profile defaults() {
			String now = Instant.now().toString();
			return new Profile("local-user", "EnderUser", "Premium", "user-solid.png", "Dashboard", true, 100, "Ready", now, now);
		}

		public Profile normalized() {
			Profile fallback = defaults();
			return new Profile(
					id == null || id.isBlank() ? fallback.id : id,
					displayName == null || displayName.isBlank() ? fallback.displayName : displayName,
					tier == null || tier.isBlank() ? fallback.tier : tier,
					avatar == null || avatar.isBlank() ? fallback.avatar : avatar,
					activePage == null || activePage.isBlank() ? fallback.activePage : activePage,
					connected,
					uiScale <= 0 ? fallback.uiScale : uiScale,
					status == null || status.isBlank() ? fallback.status : status,
					createdAt == null || createdAt.isBlank() || createdAt.equals("seed") ? fallback.createdAt : createdAt,
					lastSeenAt == null || lastSeenAt.isBlank() || lastSeenAt.equals("seed") ? fallback.lastSeenAt : lastSeenAt
			);
		}

		public Profile withDisplayName(String value) {
			return new Profile(id, value, tier, avatar, activePage, connected, uiScale, status, createdAt, lastSeenAt);
		}

		public Profile withTier(String value) {
			return new Profile(id, displayName, value, avatar, activePage, connected, uiScale, status, createdAt, lastSeenAt);
		}

		public Profile withActivePage(String value) {
			return new Profile(id, displayName, tier, avatar, value, connected, uiScale, status, createdAt, lastSeenAt);
		}

		public Profile withUiScale(int value) {
			return new Profile(id, displayName, tier, avatar, activePage, connected, value, status, createdAt, lastSeenAt);
		}

		public Profile withStatus(String value) {
			return new Profile(id, displayName, tier, avatar, activePage, connected, uiScale, value, createdAt, lastSeenAt);
		}

		public Profile withLastSeen(String value) {
			return new Profile(id, displayName, tier, avatar, activePage, connected, uiScale, status, createdAt, value);
		}
	}

	public record ModuleItem(String id, String name, String author, String version, String description,
			String category, String icon, String status, boolean installed, boolean favorite) {}

	public record ClientModuleItem(String id, String name, String author, String version, String description,
			String category, String icon, String status, boolean installed, boolean favorite, String openUrl) {}

	public record RemoteCommand(String id, String clientId, String type, Map<String, Object> payload, String createdAt) {}

	public record TemplateItem(String id, String name, String category, String description, String difficulty,
			int blocks, String icon, String badge, String script) {
		public static TemplateItem defaults() {
			return new TemplateItem("starter", "Starter Script", "Utility", "A tiny Minescript starter.", "Easy", 3,
					"code-solid.png", "New", "import minescript as ms\n\nms.echo(\"Starter loaded\")\n");
		}

		public TemplateItem normalized() {
			TemplateItem fallback = defaults();
			String safeName = name == null || name.isBlank() ? fallback.name : name;
			String safeId = id == null || id.isBlank() ? safeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "") : id;
			return new TemplateItem(
					safeId.isBlank() ? fallback.id : safeId,
					safeName,
					category == null || category.isBlank() ? fallback.category : category,
					description == null || description.isBlank() ? fallback.description : description,
					difficulty == null || difficulty.isBlank() ? fallback.difficulty : difficulty,
					blocks <= 0 ? fallback.blocks : blocks,
					icon == null || icon.isBlank() ? fallback.icon : icon,
					badge == null ? "" : badge,
					script == null || script.isBlank() ? fallback.script : script
			);
		}
	}

	public record LibraryScriptItem(String id, String name, String author, String about, String category,
			List<String> tags, String version, String icon, String fileName, String code,
			int downloads, int stars, long publishedAt, long updatedAt) {
		public LibraryScriptItem normalized() {
			String safeName = name == null || name.isBlank() ? "Untitled Script" : name;
			String safeId = id == null || id.isBlank()
					? safeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "")
					: id;
			String safeFile = fileName == null || fileName.isBlank() ? safeName.replaceAll("[^A-Za-z0-9]+", "") + ".py" : fileName;
			return new LibraryScriptItem(
					safeId.isBlank() ? "script" : safeId,
					safeName,
					author == null || author.isBlank() ? "Shulkr user" : author,
					about == null || about.isBlank() ? "Published Shulkr script." : about,
					category == null || category.isBlank() ? "Other" : category,
					tags == null ? List.of("Python", "Other") : List.copyOf(tags),
					version == null || version.isBlank() ? "1.0.0" : version,
					icon == null || icon.isBlank() ? "code-solid.png" : icon,
					safeFile,
					code == null ? "" : code,
					Math.max(0, downloads),
					Math.max(0, stars),
					publishedAt <= 0 ? System.currentTimeMillis() : publishedAt,
					updatedAt <= 0 ? System.currentTimeMillis() : updatedAt
			);
		}
	}

	public record ScriptSummary(String path, String name, String extension, long sizeBytes, long modifiedAt, String description) {}

	public record FolderSummary(String path, String name) {}

	private record ReadScriptResponse(String path, String content) {}

	private record ModuleScriptResponse(String path, boolean module, List<String> scripts) {}

	public record AppStats(int scripts, long installedModules, int templates, String scriptDir, String appDir) {}

	public record Snapshot(Profile profile, AppStats stats, List<ScriptSummary> scripts,
			List<ModuleItem> modules, List<TemplateItem> templates, FluxusConfig settings) {}
}
