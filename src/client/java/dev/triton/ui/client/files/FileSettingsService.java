package dev.triton.ui.client.files;

import dev.triton.ui.client.config.FluxusConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FileSettingsService {
	public enum FolderKind { SCRIPT, TEMPLATE, BACKUP }
	public record FolderState(Path path, boolean exists, boolean readable, boolean writable, String message) {
		public boolean healthy() { return exists && readable && writable; }
	}
	public record Backup(Path path, Path script, Instant createdAt, long bytes) {}
	public record Health(FolderState scripts, FolderState templates, FolderState backups,
			boolean minescriptPathMatches, int scriptCount, int templateCount, int backupCount,
			long backupBytes, Instant scannedAt) {}

	private final Path gameDirectory;
	private final Path defaultScripts;
	private final Path defaultTemplates;
	private final Path defaultBackups;

	public FileSettingsService(Path gameDirectory) {
		this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
		defaultScripts = this.gameDirectory.resolve("minescript").normalize();
		defaultTemplates = defaultScripts.resolve("templates").normalize();
		defaultBackups = this.gameDirectory.resolve("shulkr-backups").normalize();
	}

	public Path scripts(FluxusConfig config) { return configured(config.scriptFolderPath(), defaultScripts); }
	public Path templates(FluxusConfig config) { return configured(config.templateFolderPath(), defaultTemplates); }
	public Path backups(FluxusConfig config) { return configured(config.backupFolderPath(), defaultBackups); }
	public Path defaultPath(FolderKind kind) { return switch (kind) {
		case SCRIPT -> defaultScripts; case TEMPLATE -> defaultTemplates; case BACKUP -> defaultBackups;
	}; }

	private Path configured(String value, Path fallback) {
		if (value == null || value.isBlank()) return fallback;
		try { return Path.of(value).toAbsolutePath().normalize(); }
		catch (RuntimeException ignored) { return fallback; }
	}

	public FolderState validate(Path candidate, FolderKind kind, boolean createMissing) {
		if (candidate == null) return new FolderState(defaultPath(kind), false, false, false, "No folder selected");
		final Path path;
		try { path = candidate.toAbsolutePath().normalize(); }
		catch (RuntimeException error) { return new FolderState(candidate, false, false, false, "Invalid path: " + error.getMessage()); }
		if (path.getParent() == null) return new FolderState(path, Files.exists(path), false, false, "Filesystem roots are not allowed");
		Path saves = gameDirectory.resolve("saves").normalize();
		if (path.equals(saves) || path.startsWith(saves)) return new FolderState(path, Files.exists(path), false, false, "Minecraft world folders cannot be used");
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
			return new FolderState(path, true, false, false, "The selected path is a file");
		try {
			if (Files.notExists(path) && createMissing) Files.createDirectories(path);
			if (Files.notExists(path)) return new FolderState(path, false, false, false, "Folder does not exist");
			boolean readable = Files.isReadable(path);
			boolean writable = Files.isWritable(path) && probeWrite(path);
			String message = !readable ? "Folder is not readable" : !writable ? "Folder is not writable" : "Ready";
			return new FolderState(path, true, readable, writable, message);
		} catch (IOException | SecurityException error) {
			return new FolderState(path, Files.isDirectory(path), Files.isReadable(path), false, error.getMessage());
		}
	}

	private boolean probeWrite(Path folder) {
		Path probe = null;
		try {
			probe = Files.createTempFile(folder, ".shulkr-write-test-", ".tmp");
			Files.writeString(probe, "ok", StandardCharsets.UTF_8);
			return true;
		} catch (IOException | SecurityException ignored) { return false; }
		finally { if (probe != null) try { Files.deleteIfExists(probe); } catch (IOException ignored) {} }
	}

	public Health scan(FluxusConfig config) throws IOException {
		FolderState script = validate(scripts(config), FolderKind.SCRIPT, false);
		FolderState template = validate(templates(config), FolderKind.TEMPLATE, false);
		FolderState backup = validate(backups(config), FolderKind.BACKUP, false);
		int scriptCount = count(script.path(), path -> isScript(path));
		int templateCount = count(template.path(), path -> isTemplate(path));
		List<Backup> items = listBackups(config);
		long bytes = items.stream().mapToLong(Backup::bytes).sum();
		return new Health(script, template, backup, minescriptPathMatches(config), scriptCount, templateCount,
				items.size(), bytes, Instant.now());
	}

	private interface PathTest { boolean test(Path path); }
	private int count(Path root, PathTest test) throws IOException {
		if (!Files.isDirectory(root)) return 0;
		try (var stream = Files.walk(root, 8)) { return (int) stream.filter(Files::isRegularFile).filter(test::test).count(); }
	}

	private boolean isScript(Path path) { String n = path.getFileName().toString().toLowerCase(Locale.ROOT); return n.endsWith(".py") || n.endsWith(".pyj"); }
	private boolean isTemplate(Path path) { String n = path.getFileName().toString().toLowerCase(Locale.ROOT); return n.endsWith(".py") || n.endsWith(".pyj") || n.endsWith(".json"); }

	public Path createBackup(FluxusConfig config, Path script, String reason) throws IOException {
		Path scriptRoot = scripts(config).toAbsolutePath().normalize();
		Path source = script.toAbsolutePath().normalize();
		if (!source.startsWith(scriptRoot) || !Files.isRegularFile(source)) throw new IOException("Script is outside the configured script folder");
		Path root = backups(config).toAbsolutePath().normalize();
		FolderState state = validate(root, FolderKind.BACKUP, true);
		if (!state.healthy()) throw new IOException(state.message());
		Path relative = scriptRoot.relativize(source);
		Path folder = root.resolve("scripts").resolve(relative.getParent() == null ? Path.of("") : relative.getParent()).normalize();
		if (!folder.startsWith(root)) throw new IOException("Backup path escaped the configured backup folder");
		Files.createDirectories(folder);
		Path target = folder.resolve(relative.getFileName() + "." + System.currentTimeMillis() + "." + safeReason(reason) + ".bak");
		Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
		prunePerScript(config, source);
		deleteExpired(config);
		return target;
	}

	private String safeReason(String reason) { return reason == null ? "auto" : reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-"); }

	public List<Backup> listBackups(FluxusConfig config) throws IOException {
		Path root = backups(config).toAbsolutePath().normalize();
		Path scriptsRoot = root.resolve("scripts");
		if (!Files.isDirectory(scriptsRoot)) return List.of();
		try (var stream = Files.walk(scriptsRoot, 12)) {
			return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".bak"))
					.map(path -> toBackup(config, scriptsRoot, path)).filter(java.util.Objects::nonNull)
					.sorted(Comparator.comparing(Backup::createdAt).reversed()).toList();
		}
	}

	private Backup toBackup(FluxusConfig config, Path scriptsRoot, Path path) {
		try {
			Path rel = scriptsRoot.relativize(path);
			String name = rel.getFileName().toString();
			int marker = name.indexOf(".py.");
			if (marker < 0) marker = name.indexOf(".pyj.");
			if (marker < 0) return null;
			String originalName = name.substring(0, marker + (name.startsWith(".pyj", marker) ? 4 : 3));
			Path parent = rel.getParent();
			Path original = scripts(config).resolve(parent == null ? Path.of(originalName) : parent.resolve(originalName)).normalize();
			return new Backup(path, original, Files.getLastModifiedTime(path).toInstant(), Files.size(path));
		} catch (IOException error) { return null; }
	}

	private void prunePerScript(FluxusConfig config, Path script) throws IOException {
		int maximum = config.maximumBackupCount();
		List<Backup> matching = listBackups(config).stream().filter(item -> item.script().equals(script.toAbsolutePath().normalize())).toList();
		for (int i = Math.max(0, maximum); i < matching.size(); i++) deleteBackup(config, matching.get(i).path());
	}

	public Path restore(FluxusConfig config, Backup backup) throws IOException {
		assertBackupPath(config, backup.path());
		byte[] restoredContent = Files.readAllBytes(backup.path());
		if (Files.isRegularFile(backup.script())) createBackup(config, backup.script(), "pre-restore");
		Files.createDirectories(backup.script().getParent());
		Files.write(backup.script(), restoredContent);
		return backup.script();
	}

	public int deleteExpired(FluxusConfig config) throws IOException {
		if (config.backupRetention().equals("Never")) return 0;
		int days = Integer.parseInt(config.backupRetention().split(" ")[0]);
		Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
		int deleted = 0;
		for (Backup backup : listBackups(config)) if (backup.createdAt().isBefore(cutoff)) { deleteBackup(config, backup.path()); deleted++; }
		return deleted;
	}

	public void deleteBackup(FluxusConfig config, Path path) throws IOException {
		assertBackupPath(config, path);
		Files.deleteIfExists(path);
	}

	private void assertBackupPath(FluxusConfig config, Path path) throws IOException {
		Path root = backups(config).toAbsolutePath().normalize();
		Path target = path.toAbsolutePath().normalize();
		if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid backup path");
	}

	public Path exportConfigSnapshot(FluxusConfig config, Path minescriptConfig) throws IOException {
		Path folder = backups(config).resolve("config-snapshots").normalize();
		if (!folder.startsWith(backups(config))) throw new IOException("Snapshot path escaped backup folder");
		Files.createDirectories(folder);
		Path snapshot = folder.resolve("shulkr-config-" + System.currentTimeMillis() + ".json");
		Files.copy(FluxusConfig.path(), snapshot, StandardCopyOption.REPLACE_EXISTING);
		if (Files.isRegularFile(minescriptConfig)) Files.copy(minescriptConfig, folder.resolve("minescript-config-" + System.currentTimeMillis() + ".txt"));
		return snapshot;
	}

	public boolean minescriptPathMatches(FluxusConfig config) throws IOException {
		Path configFile = defaultScripts.resolve("config.txt");
		if (!Files.isRegularFile(configFile)) return false;
		String value = Files.readString(configFile, StandardCharsets.UTF_8).replace("\\\\", "/").replace('\\', '/');
		return value.contains(scripts(config).toString().replace('\\', '/'));
	}

	public void fixMinescriptPath(FluxusConfig config) throws IOException {
		Files.createDirectories(defaultScripts);
		Path file = defaultScripts.resolve("config.txt");
		String content = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "python=\"\"\n";
		String scriptPath = scripts(config).toString().replace('\\', '/');
		content = setConfig(content, "command_path", scriptPath + ";system/exec;");
		content = setConfig(content, "pyjinn_import_path", scriptPath + ";system/pyj;");
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private String setConfig(String content, String key, String value) {
		String line = key + "=\"" + value.replace("\"", "\\\"") + "\"";
		String regex = "(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=.*$";
		return java.util.regex.Pattern.compile(regex).matcher(content).find()
				? content.replaceFirst(regex, java.util.regex.Matcher.quoteReplacement(line))
				: content + (content.endsWith("\n") ? "" : "\n") + line + "\n";
	}
}
