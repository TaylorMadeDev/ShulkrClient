package dev.triton.ui.client.advanced;

import dev.triton.ui.client.app.FluxusAppState;
import dev.triton.ui.client.config.FluxusConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdvancedSettingsService {
	public enum State { SUCCESS, WARNING, FAILURE, REPAIR_AVAILABLE }

	public record StorageSnapshot(Map<String, Long> categories, long totalBytes, Instant measuredAt) {}
	public record Diagnostic(String name, State state, String detail, String repairKey) {}
	public record OperationResult(boolean success, String message, long bytes, int files) {}
	public record ConfigBackup(Path path, long size, Instant modifiedAt) {}

	private static final Pattern PYTHON_LINE = Pattern.compile("(?m)^\\s*python\\s*=\\s*\"([^\"]+)\"\\s*$");
	private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
	private static final Set<String> REQUIRED_ASSETS = Set.of(
			"assets/triton-ui/icon.png",
			"assets/triton-ui/font/smooth.json",
			"assets/triton-ui/textures/icons/gear-solid.png",
			"assets/triton-ui/textures/icons/check-double-solid.png");

	private final Path gameDir;
	private final Path configFile;
	private final Path configDir;
	private final Path scriptDir;
	private final Path logDir;
	private final Path cacheRoot;
	private final Path backupDir;

	public AdvancedSettingsService(Path gameDir, Path configFile) {
		this.gameDir = gameDir.toAbsolutePath().normalize();
		this.configFile = configFile.toAbsolutePath().normalize();
		this.configDir = this.configFile.getParent();
		this.scriptDir = this.gameDir.resolve("minescript").normalize();
		this.logDir = this.gameDir.resolve("logs").normalize();
		this.cacheRoot = this.configDir.resolve("shulkr-cache").normalize();
		this.backupDir = this.configDir.resolve("shulkr-config-backups").normalize();
	}

	public StorageSnapshot measureStorage() throws IOException {
		Map<String, Long> categories = new LinkedHashMap<>();
		categories.put("UI cache", sizeOf(cacheRoot.resolve("ui")));
		categories.put("Script cache", sizeOfAll(List.of(scriptDir.resolve("__pycache__"), scriptDir.resolve("shulkr_runtime"), scriptDir.resolve("shulkr_config"))));
		categories.put("Thumbnails and icons", sizeOfAll(List.of(cacheRoot.resolve("thumbnails"), cacheRoot.resolve("icons"))));
		categories.put("Runtime logs", runtimeLogFiles().stream().mapToLong(this::safeSize).sum());
		categories.put("Backup storage", sizeOfAll(List.of(scriptDir.resolve(".shulkr-backups"), backupDir)));
		long total = categories.values().stream().mapToLong(Long::longValue).sum()
				+ sizeOf(configFile) + sizeOf(gameDir.resolve("shulkr-backend"))
				+ sizeOf(gameDir.resolve("shulkr-diagnostics"));
		return new StorageSnapshot(Collections.unmodifiableMap(new LinkedHashMap<>(categories)), total, Instant.now());
	}

	public OperationResult clearUiCache() throws IOException {
		return clearDirectoryContents(cacheRoot.resolve("ui"), "UI cache");
	}

	public OperationResult clearScriptCache() throws IOException {
		return clearAllowedTargets(List.of(scriptDir.resolve("__pycache__"), scriptDir.resolve("shulkr_runtime"), scriptDir.resolve("shulkr_config")), "script cache");
	}

	public OperationResult clearThumbnailAndIconCache() throws IOException {
		return clearAllowedTargets(List.of(cacheRoot.resolve("thumbnails"), cacheRoot.resolve("icons")), "thumbnail and icon cache");
	}

	public OperationResult clearRuntimeLogs() throws IOException {
		if (Files.notExists(logDir)) return new OperationResult(true, "No removable runtime logs were found.", 0, 0);
		long bytes = 0;
		int files = 0;
		for (Path path : runtimeLogFiles()) {
			if (path.getFileName().toString().equalsIgnoreCase("shulkr-latest.log")) continue;
			ensureAllowed(path);
			bytes += Files.size(path);
			if (Files.deleteIfExists(path)) files++;
		}
		deleteEmptyDirectories(logDir.resolve("telemetry"));
		return new OperationResult(true, "Removed " + files + " rotated or telemetry log file(s). Active logs were preserved.", bytes, files);
	}

	public OperationResult clearOldScriptBackups() throws IOException {
		Path root = scriptDir.resolve(".shulkr-backups");
		if (Files.notExists(root)) return new OperationResult(true, "No script backups were found.", 0, 0);
		Instant cutoff = Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(7));
		long bytes = 0;
		int files = 0;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				if (!Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) continue;
				ensureAllowed(path);
				bytes += Files.size(path);
				if (Files.deleteIfExists(path)) files++;
			}
		}
		deleteEmptyDirectories(root);
		return new OperationResult(true, "Removed " + files + " script backup(s) older than 7 days.", bytes, files);
	}

	public Path cacheFolder() throws IOException {
		Files.createDirectories(cacheRoot);
		return cacheRoot;
	}

	public List<Diagnostic> runFullDiagnostic() {
		List<Diagnostic> results = new ArrayList<>();
		results.add(validateConfiguration());
		results.add(validateScriptDirectories());
		results.add(validateAssets());
		results.add(testMinescriptConnection());
		results.add(testPython());
		results.add(testDashboardConnection());
		return List.copyOf(results);
	}

	public Diagnostic testDashboardConnection() {
		long started = System.nanoTime();
		boolean connected = FluxusAppState.get().testBackendConnection();
		long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		return new Diagnostic("Dashboard connection", connected ? State.SUCCESS : State.WARNING,
				connected ? "Backend health check succeeded in " + millis + " ms." : "Backend health check failed; the dashboard may be offline.", "");
	}

	public Diagnostic testMinescriptConnection() {
		Path config = scriptDir.resolve("config.txt");
		Path exec = scriptDir.resolve("system/exec");
		Path pyj = scriptDir.resolve("system/pyj");
		if (Files.isRegularFile(config) && Files.isDirectory(exec) && Files.isDirectory(pyj)) {
			return new Diagnostic("Minescript connection", State.SUCCESS, "Config, system/exec, and system/pyj are available.", "");
		}
		return new Diagnostic("Minescript connection", State.REPAIR_AVAILABLE, "Required Minescript paths are missing.", "directories");
	}

	public Diagnostic testPython() {
		Path minescriptConfig = scriptDir.resolve("config.txt");
		if (Files.notExists(minescriptConfig)) return new Diagnostic("Configured Python", State.FAILURE, "Minescript config.txt does not exist.", "directories");
		try {
			Matcher matcher = PYTHON_LINE.matcher(Files.readString(minescriptConfig, StandardCharsets.UTF_8));
			if (!matcher.find()) return new Diagnostic("Configured Python", State.WARNING, "No Python executable is configured.", "");
			Path executable = Path.of(matcher.group(1)).toAbsolutePath().normalize();
			if (!Files.isRegularFile(executable)) return new Diagnostic("Configured Python", State.FAILURE, "Configured executable does not exist.", "");
			Process process = new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true).start();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return new Diagnostic("Configured Python", State.FAILURE, "Python did not respond within 5 seconds.", "");
			}
			String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			return new Diagnostic("Configured Python", process.exitValue() == 0 ? State.SUCCESS : State.FAILURE,
					version.isBlank() ? "Python exited with code " + process.exitValue() + "." : version, "");
		} catch (IOException error) {
			return new Diagnostic("Configured Python", State.FAILURE, "Python check failed: " + safeMessage(error), "");
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return new Diagnostic("Configured Python", State.FAILURE, "Python check was interrupted.", "");
		}
	}

	public Diagnostic validateConfiguration() {
		if (Files.notExists(configFile)) return new Diagnostic("Local configuration", State.REPAIR_AVAILABLE, "Configuration file is missing.", "directories");
		try {
			List<String> errors = FluxusConfig.validateJson(Files.readString(configFile, StandardCharsets.UTF_8));
			return errors.isEmpty()
					? new Diagnostic("Local configuration", State.SUCCESS, "Configuration JSON and numeric limits are valid.", "")
					: new Diagnostic("Local configuration", State.REPAIR_AVAILABLE, String.join(" ", errors), "config-values");
		} catch (IOException error) {
			return new Diagnostic("Local configuration", State.FAILURE, "Could not read configuration: " + safeMessage(error), "");
		}
	}

	public Diagnostic validateScriptDirectories() {
		List<String> missing = new ArrayList<>();
		for (Path path : requiredDirectories()) if (!Files.isDirectory(path)) missing.add(gameDir.relativize(path).toString());
		return missing.isEmpty()
				? new Diagnostic("Script directories", State.SUCCESS, "All required local directories exist.", "")
				: new Diagnostic("Script directories", State.REPAIR_AVAILABLE, "Missing: " + String.join(", ", missing), "directories");
	}

	public Diagnostic validateAssets() {
		var container = FabricLoader.getInstance().getModContainer("triton-ui");
		if (container.isEmpty()) return new Diagnostic("Required assets", State.FAILURE, "The triton-ui mod container is unavailable.", "");
		List<String> missing = REQUIRED_ASSETS.stream().filter(asset -> container.get().findPath(asset).isEmpty()).sorted().toList();
		return missing.isEmpty()
				? new Diagnostic("Required assets", State.SUCCESS, "All required UI assets are packaged.", "")
				: new Diagnostic("Required assets", State.FAILURE, "Missing packaged assets: " + String.join(", ", missing), "");
	}

	public OperationResult repairMissingDirectories() throws IOException {
		int created = 0;
		for (Path path : requiredDirectories()) {
			ensureAllowed(path);
			if (Files.notExists(path)) {
				Files.createDirectories(path);
				created++;
			}
		}
		return new OperationResult(true, "Created " + created + " missing director" + (created == 1 ? "y." : "ies."), 0, created);
	}

	public OperationResult repairInvalidConfigValues() throws IOException {
		if (Files.notExists(configFile)) {
			Files.createDirectories(configDir);
			FluxusConfig defaults = FluxusConfig.load();
			defaults.save();
			return new OperationResult(true, "Created a default configuration file.", 0, 1);
		}
		String json = Files.readString(configFile, StandardCharsets.UTF_8);
		try {
			com.google.gson.JsonParser.parseString(json);
		} catch (RuntimeException error) {
			return new OperationResult(false, "JSON syntax must be repaired in the raw configuration editor first.", 0, 0);
		}
		createConfigBackup();
		FluxusConfig repaired = FluxusConfig.load();
		repaired.save();
		List<String> remaining = FluxusConfig.validateJson(Files.readString(configFile, StandardCharsets.UTF_8));
		return new OperationResult(remaining.isEmpty(), remaining.isEmpty() ? "Invalid numeric values were clamped and saved." : String.join(" ", remaining), 0, 1);
	}

	public ConfigBackup createConfigBackup() throws IOException {
		if (!Files.isRegularFile(configFile)) throw new IOException("Configuration file does not exist.");
		Files.createDirectories(backupDir);
		Path destination = backupDir.resolve("shulkr-client-" + FILE_TIME.format(Instant.now()) + ".json");
		int suffix = 2;
		while (Files.exists(destination)) destination = backupDir.resolve("shulkr-client-" + FILE_TIME.format(Instant.now()) + "-" + suffix++ + ".json");
		Files.copy(configFile, destination);
		return new ConfigBackup(destination, Files.size(destination), Files.getLastModifiedTime(destination).toInstant());
	}

	public List<ConfigBackup> configBackups() throws IOException {
		if (Files.notExists(backupDir)) return List.of();
		try (var files = Files.list(backupDir)) {
			return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json"))
					.map(path -> {
						try { return new ConfigBackup(path, Files.size(path), Files.getLastModifiedTime(path).toInstant()); }
						catch (IOException ignored) { return null; }
					}).filter(java.util.Objects::nonNull)
					.sorted(Comparator.comparing((ConfigBackup backup) -> backup.path().getFileName().toString()).reversed()).toList();
		}
	}

	public OperationResult restoreLatestConfigBackup() throws IOException {
		List<ConfigBackup> backups = configBackups();
		if (backups.isEmpty()) return new OperationResult(false, "No configuration backups are available.", 0, 0);
		return restoreConfigBackup(backups.getFirst());
	}

	public OperationResult restoreConfigBackup(ConfigBackup selected) throws IOException {
		if (selected == null) return new OperationResult(false, "No configuration backup was selected.", 0, 0);
		ensureAllowed(selected.path());
		if (!selected.path().normalize().startsWith(backupDir) || !Files.isRegularFile(selected.path())) {
			return new OperationResult(false, "The selected backup is not available in the managed backup folder.", 0, 0);
		}
		String json = Files.readString(selected.path(), StandardCharsets.UTF_8);
		List<String> errors = FluxusConfig.validateJson(json);
		if (!errors.isEmpty()) return new OperationResult(false, "Backup is invalid: " + String.join(" ", errors), 0, 0);
		createConfigBackup();
		Files.copy(selected.path(), configFile, StandardCopyOption.REPLACE_EXISTING);
		return new OperationResult(true, "Restored " + selected.path().getFileName() + ".", selected.size(), 1);
	}

	public String formatConfigurationJson(String json) {
		return PRETTY_JSON.toJson(JsonParser.parseString(json)) + "\n";
	}

	public OperationResult applyRawConfiguration(String json) throws IOException {
		List<String> errors = FluxusConfig.validateJson(json);
		if (!errors.isEmpty()) return new OperationResult(false, String.join(" ", errors), 0, 0);
		createConfigBackup();
		Path temporary = configDir.resolve(configFile.getFileName() + ".tmp");
		Files.writeString(temporary, PRETTY_JSON.toJson(JsonParser.parseString(json)) + "\n", StandardCharsets.UTF_8);
		try {
			Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return new OperationResult(true, "Configuration validated, backed up, and applied.", Files.size(configFile), 1);
	}

	public Path exportDiagnosticReport(List<Diagnostic> diagnostics, StorageSnapshot storage, FluxusConfig config) throws IOException {
		Path exportDir = gameDir.resolve("shulkr-diagnostics").normalize();
		ensureAllowed(exportDir);
		Files.createDirectories(exportDir);
		StringBuilder report = new StringBuilder("Shulkr Client Diagnostic Report\nGenerated: ").append(Instant.now()).append("\n\n");
		for (Diagnostic diagnostic : diagnostics) report.append(diagnostic.state()).append(" | ").append(diagnostic.name()).append(" | ").append(diagnostic.detail()).append('\n');
		if (storage != null) {
			report.append("\nStorage\n");
			storage.categories().forEach((name, bytes) -> report.append(name).append(": ").append(bytes).append(" bytes\n"));
			report.append("Total Shulkr local storage: ").append(storage.totalBytes()).append(" bytes\n");
		}
		report.append("\nRuntime settings\nWorker limit: ").append(config.scriptWorkerLimit())
				.append("\nConcurrent scripts: ").append(config.maximumConcurrentScripts())
				.append("\nThread priority: ").append(config.executionThreadPriority()).append('\n');
		String redacted = redact(report.toString());
		Path destination = exportDir.resolve("shulkr-diagnostic-" + FILE_TIME.format(Instant.now()) + ".txt");
		Files.writeString(destination, redacted, StandardCharsets.UTF_8);
		return destination;
	}

	public String redact(String text) {
		String redacted = text == null ? "" : text;
		String username = System.getProperty("user.name", "");
		String home = System.getProperty("user.home", "");
		for (String sensitive : List.of(gameDir.toString(), home, username)) {
			if (sensitive == null || sensitive.isBlank()) continue;
			redacted = redacted.replaceAll("(?i)" + Pattern.quote(sensitive), Matcher.quoteReplacement(sensitive.equals(username) ? "<user>" : "<local-path>"));
		}
		redacted = redacted.replaceAll("(?i)(device|hardware|account|license)(Id)?\\s*[:=]\\s*[^\\s]+", "$1$2=<redacted>");
		redacted = redacted.replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", "<identifier>");
		return redacted;
	}

	public Path configFile() { return configFile; }
	public Path configFolder() { return configDir; }
	public Path logFolder() { return logDir; }
	public Path backupFolder() { return backupDir; }

	private List<Path> requiredDirectories() {
		return List.of(configDir, scriptDir, scriptDir.resolve("system/exec"), scriptDir.resolve("system/pyj"), logDir, cacheRoot, backupDir);
	}

	private List<Path> runtimeLogFiles() throws IOException {
		if (Files.notExists(logDir)) return List.of();
		try (var paths = Files.walk(logDir)) {
			return paths.filter(Files::isRegularFile).filter(path -> {
				String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
				return path.startsWith(logDir.resolve("telemetry")) || name.contains("shulkr") || name.contains("triton") || name.contains("minescript");
			}).toList();
		}
	}

	private long sizeOfAll(List<Path> roots) throws IOException {
		long total = 0;
		for (Path root : roots) total += sizeOf(root);
		return total;
	}

	private long sizeOf(Path root) throws IOException {
		ensureAllowed(root);
		if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return 0;
		if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) return Files.size(root);
		try (var paths = Files.walk(root)) {
			return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).mapToLong(this::safeSize).sum();
		}
	}

	private long safeSize(Path path) {
		try { return Files.size(path); } catch (IOException ignored) { return 0; }
	}

	private OperationResult clearAllowedTargets(List<Path> targets, String label) throws IOException {
		long bytes = 0;
		int files = 0;
		for (Path target : targets) {
			OperationResult result = clearDirectoryContents(target, label);
			bytes += result.bytes();
			files += result.files();
		}
		return new OperationResult(true, "Removed " + files + " " + label + " file(s).", bytes, files);
	}

	private OperationResult clearDirectoryContents(Path target, String label) throws IOException {
		ensureAllowed(target);
		if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) return new OperationResult(true, "No " + label + " files were found.", 0, 0);
		long bytes = 0;
		int files = 0;
		try (var paths = Files.walk(target)) {
			List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
			for (Path path : ordered) {
				if (path.equals(target)) continue;
				ensureAllowed(path);
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					bytes += Files.size(path);
					if (Files.deleteIfExists(path)) files++;
				} else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(path);
			}
		}
		return new OperationResult(true, "Removed " + files + " " + label + " file(s).", bytes, files);
	}

	private void deleteEmptyDirectories(Path root) throws IOException {
		if (Files.notExists(root)) return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				if (Files.isDirectory(path) && !path.equals(root)) {
					try (var children = Files.list(path)) { if (children.findAny().isEmpty()) Files.deleteIfExists(path); }
				}
			}
		}
	}

	private void ensureAllowed(Path path) throws IOException {
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(gameDir)) throw new IOException("Refusing filesystem access outside the Minecraft directory: " + normalized);
	}

	private static String safeMessage(Exception error) {
		return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
	}
}
