package dev.triton.ui.client.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FluxusConfig {
	private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
	private static final Pattern BOOLEAN_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(true|false)");
	private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shulkr-client.json");
	private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shulk-client.json");

	private String theme = "Dark glass";
	private String accent = "Shulkr purple";
	private String defaultPage = "Dashboard";
	private boolean rememberLastPage = true;
	private boolean autosaveScripts = true;
	private boolean ruffDiagnostics = true;
	private boolean inlineAutocomplete = true;
	private boolean confirmDestructiveScripts = true;
	private boolean blockNetworkByDefault = true;
	private int backupHistory = 25;

	public static FluxusConfig load() {
		FluxusConfig config = new FluxusConfig();
		if (!Files.exists(CONFIG_PATH) && Files.exists(LEGACY_CONFIG_PATH)) {
			try {
				Files.createDirectories(CONFIG_PATH.getParent());
				Files.copy(LEGACY_CONFIG_PATH, CONFIG_PATH);
			} catch (IOException ignored) {
			}
		}
		if (!Files.exists(CONFIG_PATH)) {
			config.save();
			return config;
		}
		try {
			String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
			config.theme = readString(json, "theme", config.theme);
			config.accent = readString(json, "accent", config.accent);
			if (config.accent.equals("Shulk purple")) {
				config.accent = "Shulkr purple";
			}
			config.defaultPage = readString(json, "defaultPage", config.defaultPage);
			config.rememberLastPage = readBoolean(json, "rememberLastPage", config.rememberLastPage);
			config.autosaveScripts = readBoolean(json, "autosaveScripts", config.autosaveScripts);
			config.ruffDiagnostics = readBoolean(json, "ruffDiagnostics", config.ruffDiagnostics);
			config.inlineAutocomplete = readBoolean(json, "inlineAutocomplete", config.inlineAutocomplete);
			config.confirmDestructiveScripts = readBoolean(json, "confirmDestructiveScripts", config.confirmDestructiveScripts);
			config.blockNetworkByDefault = readBoolean(json, "blockNetworkByDefault", config.blockNetworkByDefault);
			config.backupHistory = readInt(json, "backupHistory", config.backupHistory);
		} catch (IOException ignored) {
			config.save();
		}
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, toJson(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	public static Path path() {
		return CONFIG_PATH;
	}

	public String theme() {
		return theme;
	}

	public String accent() {
		return accent;
	}

	public String defaultPage() {
		return defaultPage;
	}

	public boolean rememberLastPage() {
		return rememberLastPage;
	}

	public boolean autosaveScripts() {
		return autosaveScripts;
	}

	public boolean ruffDiagnostics() {
		return ruffDiagnostics;
	}

	public boolean inlineAutocomplete() {
		return inlineAutocomplete;
	}

	public boolean confirmDestructiveScripts() {
		return confirmDestructiveScripts;
	}

	public boolean blockNetworkByDefault() {
		return blockNetworkByDefault;
	}

	public int backupHistory() {
		return backupHistory;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public void setAccent(String accent) {
		this.accent = accent;
	}

	public void setDefaultPage(String defaultPage) {
		this.defaultPage = defaultPage;
	}

	public void setRememberLastPage(boolean rememberLastPage) {
		this.rememberLastPage = rememberLastPage;
	}

	public void setAutosaveScripts(boolean autosaveScripts) {
		this.autosaveScripts = autosaveScripts;
	}

	public void setRuffDiagnostics(boolean ruffDiagnostics) {
		this.ruffDiagnostics = ruffDiagnostics;
	}

	public void setInlineAutocomplete(boolean inlineAutocomplete) {
		this.inlineAutocomplete = inlineAutocomplete;
	}

	public void setConfirmDestructiveScripts(boolean confirmDestructiveScripts) {
		this.confirmDestructiveScripts = confirmDestructiveScripts;
	}

	public void setBlockNetworkByDefault(boolean blockNetworkByDefault) {
		this.blockNetworkByDefault = blockNetworkByDefault;
	}

	public void setBackupHistory(int backupHistory) {
		this.backupHistory = backupHistory;
	}

	private String toJson() {
		return "{\n"
				+ "  \"theme\": \"" + escape(theme) + "\",\n"
				+ "  \"accent\": \"" + escape(accent) + "\",\n"
				+ "  \"defaultPage\": \"" + escape(defaultPage) + "\",\n"
				+ "  \"rememberLastPage\": " + rememberLastPage + ",\n"
				+ "  \"autosaveScripts\": " + autosaveScripts + ",\n"
				+ "  \"ruffDiagnostics\": " + ruffDiagnostics + ",\n"
				+ "  \"inlineAutocomplete\": " + inlineAutocomplete + ",\n"
				+ "  \"confirmDestructiveScripts\": " + confirmDestructiveScripts + ",\n"
				+ "  \"blockNetworkByDefault\": " + blockNetworkByDefault + ",\n"
				+ "  \"backupHistory\": " + backupHistory + "\n"
				+ "}\n";
	}

	private static String readString(String json, String field, String fallback) {
		Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
		return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : fallback;
	}

	private static boolean readBoolean(String json, String field, boolean fallback) {
		Matcher matcher = Pattern.compile(BOOLEAN_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
		return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
	}

	private static int readInt(String json, String field, int fallback) {
		Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
