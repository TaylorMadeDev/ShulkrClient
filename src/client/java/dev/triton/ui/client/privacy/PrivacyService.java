package dev.triton.ui.client.privacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.triton.ui.client.config.FluxusConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistent privacy state and the single script-permission decision point. */
public final class PrivacyService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)(?:[a-z]:\\\\|[a-z]:/)[^\\r\\n\\t\"']+");
	private static final Pattern IDENTIFIER = Pattern.compile("(?i)(?:device|hardware|account|license|client)[-_ ]?id\\s*[:=]\\s*[^\\s,;]+|[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	public enum Permission {
		NETWORK("Network access"), FILE_READ("File read access"), FILE_WRITE("File write access"),
		CLIPBOARD("Clipboard access"), EXTERNAL_PROCESS("Starting external processes"), CHAT("Sending chat messages"),
		MOVEMENT("Controlling movement and camera"), WORLD_ACTION("Mining, placing, and attacking");

		private final String label;
		Permission(String label) { this.label = label; }
		public String label() { return label; }
	}

	public enum Decision { ALLOW, ASK, BLOCK }

	public record Assessment(String scriptId, Path path, boolean trusted, String source,
			Set<Permission> requested, String requestFingerprint, Decision decision, String message) {}
	public record SavedApproval(String scriptId, String scriptName, String source, Set<Permission> permissions, Instant approvedAt) {}
	public record ClearResult(boolean success, String message, int filesCleared) {}

	private final Path gameDir;
	private final Path scriptDir;
	private final Path dataDir;
	private final Path approvalsFile;
	private final Path sourcesFile;
	private final Path executionHistoryFile;
	private final Path runtimeLogFile;
	private final Path recentScriptsFile;
	private final Path searchHistoryFile;
	private final Map<String, Approval> approvals = new LinkedHashMap<>();
	private final Map<String, String> sources = new LinkedHashMap<>();
	private boolean retentionInitialized;

	public PrivacyService() {
		gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		scriptDir = gameDir.resolve("minescript").normalize();
		dataDir = gameDir.resolve("config").resolve("shulkr-privacy").normalize();
		approvalsFile = dataDir.resolve("script-permissions.json");
		sourcesFile = dataDir.resolve("script-sources.json");
		executionHistoryFile = dataDir.resolve("execution-history.jsonl");
		runtimeLogFile = dataDir.resolve("runtime.log");
		recentScriptsFile = dataDir.resolve("recent-scripts.txt");
		searchHistoryFile = dataDir.resolve("search-history.txt");
		load();
	}

	public synchronized Assessment assess(Path script, FluxusConfig config) {
		Path normalized = normalizeScript(script);
		String id = stableScriptId(normalized);
		String source = sources.getOrDefault(id, inferSource(normalized));
		boolean trusted = source.equals("Local");
		Set<Permission> requested = detectPermissions(normalized);
		String fingerprint = fingerprint(requested.stream().map(Enum::name).sorted().toList());
		Approval approval = approvals.get(id);
		if (config.rememberScriptPermissions() && approval != null && approval.requestFingerprint.equals(fingerprint)) {
			return new Assessment(id, normalized, trusted, source, requested, fingerprint, Decision.ALLOW,
					"Previously approved for the same requested permissions.");
		}
		List<String> blocked = new ArrayList<>();
		List<String> approvalRequired = new ArrayList<>();
		for (Permission permission : requested) {
			String policy = config.permissionPolicy(permission.name());
			if (policy.equals("Always block")) blocked.add(permission.label());
			else if (policy.equals("Ask every time") || (policy.equals("Allow trusted scripts") && !trusted)) approvalRequired.add(permission.label());
		}
		if (!blocked.isEmpty()) {
			return new Assessment(id, normalized, trusted, source, requested, fingerprint, Decision.BLOCK,
					"Blocked by policy: " + String.join(", ", blocked) + ".");
		}
		if (!trusted && config.confirmUntrustedScripts()) approvalRequired.add("untrusted script source");
		if (!approvalRequired.isEmpty()) {
			return new Assessment(id, normalized, trusted, source, requested, fingerprint, Decision.ASK,
					"Approval required for " + String.join(", ", approvalRequired.stream().distinct().toList()) + ".");
		}
		return new Assessment(id, normalized, trusted, source, requested, fingerprint, Decision.ALLOW,
				requested.isEmpty() ? "No sensitive permissions detected." : "Allowed by the configured trusted-script policies.");
	}

	public synchronized void applyConfig(FluxusConfig config) {
		try {
			if (!retentionInitialized && config.logRetention().equals("Session only")) {
				Files.deleteIfExists(executionHistoryFile);
				Files.deleteIfExists(runtimeLogFile);
			}
			retentionInitialized = true;
			applyRetention(config);
		} catch (IOException ignored) {}
	}

	public synchronized void approve(Assessment assessment, FluxusConfig config) throws IOException {
		if (!config.rememberScriptPermissions()) return;
		approvals.put(assessment.scriptId(), new Approval(assessment.path().getFileName().toString(), assessment.source(),
				assessment.requestFingerprint(), assessment.requested().stream().map(Enum::name).sorted().toList(), Instant.now().toString()));
		saveApprovals();
	}

	public synchronized void markUntrusted(Path script, String source) throws IOException {
		Path normalized = normalizeScript(script);
		String id = stableScriptId(normalized);
		sources.put(id, source == null || source.isBlank() ? "Imported" : source);
		approvals.remove(id);
		saveSources();
		saveApprovals();
	}

	public synchronized List<SavedApproval> savedApprovals() {
		return approvals.entrySet().stream().map(entry -> {
			Approval approval = entry.getValue();
			EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
			for (String value : approval.permissions) {
				try { permissions.add(Permission.valueOf(value)); } catch (IllegalArgumentException ignored) {}
			}
			Instant approved;
			try { approved = Instant.parse(approval.approvedAt); } catch (RuntimeException ignored) { approved = Instant.EPOCH; }
			return new SavedApproval(entry.getKey(), approval.scriptName, approval.source, Set.copyOf(permissions), approved);
		}).sorted(Comparator.comparing(SavedApproval::approvedAt).reversed()).toList();
	}

	public synchronized ClearResult revokeAllPermissions() throws IOException {
		int count = approvals.size();
		approvals.clear();
		saveApprovals();
		return new ClearResult(true, count == 0 ? "No saved script permissions existed." : "Revoked " + count + " saved script permission set(s).", count);
	}

	public synchronized void recordExecution(Path script, boolean started, FluxusConfig config) {
		if (!config.saveScriptExecutionHistory() && !config.saveRuntimeLogs()) return;
		try {
			Files.createDirectories(dataDir);
			String path = config.includeLocalPathsInDiagnostics() ? normalizeScript(script).toString() : normalizeScript(script).getFileName().toString();
			if (config.saveScriptExecutionHistory()) {
				JsonObject row = new JsonObject();
				row.addProperty("time", Instant.now().toString());
				row.addProperty("script", path);
				row.addProperty("started", started);
				Files.writeString(executionHistoryFile, GSON.toJson(row) + System.lineSeparator(), StandardCharsets.UTF_8,
						java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			}
			if (config.saveRuntimeLogs()) {
				String line = Instant.now() + " | " + (started ? "STARTED" : "BLOCKED") + " | " + path + System.lineSeparator();
				Files.writeString(runtimeLogFile, redact(line, config), StandardCharsets.UTF_8,
						java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			}
			applyRetention(config);
		} catch (IOException ignored) {}
	}

	public synchronized void recordRecentScript(Path script, FluxusConfig config) {
		if (!config.saveRecentlyOpenedScripts()) return;
		appendDistinct(recentScriptsFile, normalizeScript(script).toString(), 50);
	}

	public synchronized void recordSearch(String query, FluxusConfig config) {
		if (!config.saveSearchHistory() || query == null || query.isBlank()) return;
		appendDistinct(searchHistoryFile, query.trim(), 100);
	}

	public synchronized ClearResult clearExecutionHistory() throws IOException { return clearFile(executionHistoryFile, "execution history"); }
	public synchronized ClearResult clearRecentScripts() throws IOException { return clearFile(recentScriptsFile, "recent scripts"); }
	public synchronized ClearResult clearSearchHistory() throws IOException { return clearFile(searchHistoryFile, "search history"); }

	public synchronized ClearResult clearRuntimeLogs() throws IOException {
		Path logDir = gameDir.resolve("logs").normalize();
		int count = 0;
		if (Files.isDirectory(logDir)) {
			try (var paths = Files.walk(logDir)) {
				for (Path path : paths.filter(Files::isRegularFile).toList()) {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if ((name.contains("shulkr") || name.contains("triton") || name.contains("minescript") || path.startsWith(logDir.resolve("telemetry")))
							&& !name.equals("latest.log")) {
						Files.deleteIfExists(path);
						count++;
					}
				}
			}
		}
		if (Files.deleteIfExists(runtimeLogFile)) count++;
		return new ClearResult(true, "Cleared " + count + " managed runtime log file(s); the active Minecraft log was preserved.", count);
	}

	public synchronized ClearResult clearAllLocalPrivacyData() throws IOException {
		int count = 0;
		for (Path path : List.of(approvalsFile, executionHistoryFile, runtimeLogFile, recentScriptsFile, searchHistoryFile)) {
			if (Files.deleteIfExists(path)) count++;
		}
		approvals.clear();
		return new ClearResult(true, "Cleared all local privacy history and saved permissions. Security provenance markers were preserved.", count);
	}

	public String redact(String text, FluxusConfig config) {
		String result = text == null ? "" : text;
		if (config.redactWindowsUsernamesAndPaths()) {
			String user = System.getProperty("user.name", "");
			String home = System.getProperty("user.home", "");
			if (!user.isBlank()) result = result.replaceAll("(?i)" + Pattern.quote(user), "<user>");
			if (!home.isBlank()) result = result.replaceAll("(?i)" + Pattern.quote(home), "<local-path>");
			Matcher matcher = WINDOWS_PATH.matcher(result);
			result = matcher.replaceAll("<local-path>");
		}
		if (config.redactAccountAndDeviceIdentifiers()) result = IDENTIFIER.matcher(result).replaceAll("<identifier>");
		return result;
	}

	private Set<Permission> detectPermissions(Path script) {
		EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
		String code;
		try { code = Files.readString(script, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT); }
		catch (IOException ignored) { return result; }
		if (contains(code, "requests", "urllib", "http.client", "socket", "websocket")) result.add(Permission.NETWORK);
		if (contains(code, "open(", "read_text", "read_bytes", "pathlib", "os.listdir", "os.walk")) result.add(Permission.FILE_READ);
		if (contains(code, "write(", "write_text", "write_bytes", "unlink(", "remove(", "rmtree", "rename(")) result.add(Permission.FILE_WRITE);
		if (contains(code, "clipboard", "pyperclip")) result.add(Permission.CLIPBOARD);
		if (contains(code, "subprocess", "os.system", "popen(", "startfile(")) result.add(Permission.EXTERNAL_PROCESS);
		if (contains(code, "ms.chat(", "execute(\"say ", "execute('say ")) result.add(Permission.CHAT);
		if (contains(code, "player_press_", "player_set_orientation", "player_look", "player_position", "keybind")) result.add(Permission.MOVEMENT);
		if (contains(code, "player_press_attack", "player_press_use", "player_press_pick", "mine", "place_block", "attack")) result.add(Permission.WORLD_ACTION);
		return Set.copyOf(result);
	}

	private String inferSource(Path script) {
		String value = script.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
		if (value.contains("script-hub") || value.contains("downloads") || value.contains("imports")) return "Imported";
		return script.startsWith(scriptDir) ? "Local" : "External";
	}

	private String stableScriptId(Path script) {
		String identity;
		try { identity = scriptDir.relativize(script).toString(); }
		catch (IllegalArgumentException ignored) { identity = script.toString(); }
		return fingerprint(List.of(identity.replace('\\', '/').toLowerCase(Locale.ROOT)));
	}

	private Path normalizeScript(Path script) {
		return (script == null ? scriptDir.resolve("unknown.py") : script).toAbsolutePath().normalize();
	}

	private static boolean contains(String value, String... needles) {
		for (String needle : needles) if (value.contains(needle)) return true;
		return false;
	}

	private static String fingerprint(List<String> values) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes).substring(0, 24);
		} catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}

	private void load() {
		loadMap(approvalsFile, (id, value) -> {
			if (!value.isJsonObject()) return;
			JsonObject item = value.getAsJsonObject();
			List<String> permissions = new ArrayList<>();
			if (item.has("permissions") && item.get("permissions").isJsonArray()) for (JsonElement permission : item.getAsJsonArray("permissions")) permissions.add(permission.getAsString());
			approvals.put(id, new Approval(string(item, "scriptName", "Unknown"), string(item, "source", "Unknown"),
					string(item, "requestFingerprint", ""), permissions, string(item, "approvedAt", Instant.EPOCH.toString())));
		});
		loadMap(sourcesFile, (id, value) -> { if (value.isJsonPrimitive()) sources.put(id, value.getAsString()); });
	}

	private void loadMap(Path file, java.util.function.BiConsumer<String, JsonElement> consumer) {
		if (!Files.isRegularFile(file)) return;
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) consumer.accept(entry.getKey(), entry.getValue());
		} catch (IOException | RuntimeException ignored) {}
	}

	private void saveApprovals() throws IOException {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Approval> entry : approvals.entrySet()) {
			Approval approval = entry.getValue();
			JsonObject item = new JsonObject();
			item.addProperty("scriptName", approval.scriptName);
			item.addProperty("source", approval.source);
			item.addProperty("requestFingerprint", approval.requestFingerprint);
			JsonArray permissions = new JsonArray();
			approval.permissions.forEach(permissions::add);
			item.add("permissions", permissions);
			item.addProperty("approvedAt", approval.approvedAt);
			root.add(entry.getKey(), item);
		}
		atomicWrite(approvalsFile, GSON.toJson(root));
	}

	private void saveSources() throws IOException {
		JsonObject root = new JsonObject();
		sources.forEach(root::addProperty);
		atomicWrite(sourcesFile, GSON.toJson(root));
	}

	private void atomicWrite(Path destination, String content) throws IOException {
		Files.createDirectories(dataDir);
		Path temporary = Files.createTempFile(dataDir, destination.getFileName().toString(), ".tmp");
		Files.writeString(temporary, content, StandardCharsets.UTF_8);
		try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
		catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
	}

	private void appendDistinct(Path file, String value, int maximum) {
		try {
			List<String> lines = Files.isRegularFile(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8)) : new ArrayList<>();
			lines.remove(value);
			lines.add(0, value);
			if (lines.size() > maximum) lines = new ArrayList<>(lines.subList(0, maximum));
			Files.createDirectories(dataDir);
			Files.write(file, lines, StandardCharsets.UTF_8);
		} catch (IOException ignored) {}
	}

	private void applyRetention(FluxusConfig config) throws IOException {
		String retention = config.logRetention();
		if (retention.equals("Session only")) return;
		int days = switch (retention) { case "1 day" -> 1; case "7 days" -> 7; default -> 30; };
		Instant cutoff = Instant.now().minus(Duration.ofDays(days));
		if (Files.isRegularFile(executionHistoryFile)) {
			List<String> retained = new ArrayList<>();
			for (String line : Files.readAllLines(executionHistoryFile, StandardCharsets.UTF_8)) {
				try {
					JsonObject row = JsonParser.parseString(line).getAsJsonObject();
					if (!Instant.parse(row.get("time").getAsString()).isBefore(cutoff)) retained.add(line);
				} catch (RuntimeException ignored) {}
			}
			Files.write(executionHistoryFile, retained, StandardCharsets.UTF_8);
		}
		if (Files.isRegularFile(runtimeLogFile)) {
			List<String> retainedLogs = new ArrayList<>();
			for (String line : Files.readAllLines(runtimeLogFile, StandardCharsets.UTF_8)) {
				try {
					String timestamp = line.substring(0, line.indexOf(" | "));
					if (!Instant.parse(timestamp).isBefore(cutoff)) retainedLogs.add(line);
				} catch (RuntimeException ignored) {}
			}
			Files.write(runtimeLogFile, retainedLogs, StandardCharsets.UTF_8);
		}
	}

	private ClearResult clearFile(Path file, String label) throws IOException {
		boolean existed = Files.deleteIfExists(file);
		return new ClearResult(true, existed ? "Cleared " + label + "." : "No " + label + " was stored.", existed ? 1 : 0);
	}

	private static String string(JsonObject object, String key, String fallback) {
		try { return object.has(key) ? object.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private record Approval(String scriptName, String source, String requestFingerprint, List<String> permissions, String approvedAt) {}
}
