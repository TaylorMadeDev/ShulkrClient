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
	private String density = "Comfortable";
	private String sidebarWidth = "300 px";
	private String navigationMode = "Expanded sidebar";
	private String contentWidth = "Wide";
	private String rightPanelBehaviour = "Always visible";
	private String pageSpacing = "Comfortable";
	private String headerBehaviour = "Static";
	private String cornerRadius = "Soft";
	private int panelTransparency = 100;
	private int backgroundBlur = 12;
	private String borderStrength = "Subtle";
	private int glowIntensity = 100;
	private String animationSpeed = "Normal";
	private int uiScale = 100;
	private int fontSize = 100;
	private int iconSize = 100;
	private boolean reduceMotion;
	private String layoutPreset = "Balanced";
	private String defaultPage = "Dashboard";
	private boolean rememberLastPage = true;
	private boolean autosaveScripts = true;
	private boolean ruffDiagnostics = true;
	private boolean inlineAutocomplete = true;
	private boolean confirmDestructiveScripts = true;
	private boolean blockNetworkByDefault = true;
	private int backupHistory = 25;
	private int openMenuKey = 85;
	private int overlayEditKey = -1;
	private int runLastScriptKey = 117;
	private String lastScriptPath = "";

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
			config.density = readString(json, "density", config.density);
			config.sidebarWidth = readString(json, "sidebarWidth", config.sidebarWidth);
			config.navigationMode = readString(json, "navigationMode", config.navigationMode);
			config.contentWidth = readString(json, "contentWidth", config.contentWidth);
			config.rightPanelBehaviour = readString(json, "rightPanelBehaviour", config.rightPanelBehaviour);
			config.pageSpacing = readString(json, "pageSpacing", config.pageSpacing);
			config.headerBehaviour = readString(json, "headerBehaviour", config.headerBehaviour);
			config.cornerRadius = readString(json, "cornerRadius", config.cornerRadius);
			config.panelTransparency = clamp(readInt(json, "panelTransparency", config.panelTransparency), 55, 100);
			config.backgroundBlur = clamp(readInt(json, "backgroundBlur", config.backgroundBlur), 0, 32);
			config.borderStrength = readString(json, "borderStrength", config.borderStrength);
			config.glowIntensity = clamp(readInt(json, "glowIntensity", config.glowIntensity), 0, 100);
			config.animationSpeed = readString(json, "animationSpeed", config.animationSpeed);
			config.uiScale = clamp(readInt(json, "uiScale", config.uiScale), 85, 115);
			config.fontSize = clamp(readInt(json, "fontSize", config.fontSize), 85, 120);
			config.iconSize = clamp(readInt(json, "iconSize", config.iconSize), 80, 125);
			config.reduceMotion = readBoolean(json, "reduceMotion", config.reduceMotion);
			config.layoutPreset = readString(json, "layoutPreset", config.layoutPreset);
			if (!json.contains("\"layoutPreset\"")) {
				config.layoutPreset = "Custom";
			}
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
			config.openMenuKey = readInt(json, "openMenuKey", config.openMenuKey);
			config.overlayEditKey = readInt(json, "overlayEditKey", config.overlayEditKey);
			config.runLastScriptKey = readInt(json, "runLastScriptKey", config.runLastScriptKey);
			config.lastScriptPath = readString(json, "lastScriptPath", config.lastScriptPath);
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

	public String density() {
		return density;
	}

	public String sidebarWidth() {
		return sidebarWidth;
	}

	public String navigationMode() {
		return navigationMode;
	}

	public String contentWidth() {
		return contentWidth;
	}

	public String rightPanelBehaviour() {
		return rightPanelBehaviour;
	}

	public String pageSpacing() {
		return pageSpacing;
	}

	public String headerBehaviour() {
		return headerBehaviour;
	}

	public String cornerRadius() {
		return cornerRadius;
	}

	public int panelTransparency() {
		return panelTransparency;
	}

	public int backgroundBlur() {
		return backgroundBlur;
	}

	public String borderStrength() {
		return borderStrength;
	}

	public int glowIntensity() {
		return glowIntensity;
	}

	public String animationSpeed() {
		return animationSpeed;
	}

	public int uiScale() {
		return uiScale;
	}

	public int fontSize() {
		return fontSize;
	}

	public int iconSize() {
		return iconSize;
	}

	public boolean reduceMotion() {
		return reduceMotion;
	}

	public String layoutPreset() {
		return layoutPreset;
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

	public int openMenuKey() {
		return openMenuKey;
	}

	public int overlayEditKey() {
		return overlayEditKey;
	}

	public int runLastScriptKey() {
		return runLastScriptKey;
	}

	public String lastScriptPath() {
		return lastScriptPath;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public void setAccent(String accent) {
		this.accent = accent;
	}

	public void setDensity(String density) {
		this.density = density;
	}

	public void setSidebarWidth(String sidebarWidth) {
		this.sidebarWidth = sidebarWidth;
	}

	public void setNavigationMode(String navigationMode) {
		this.navigationMode = navigationMode;
	}

	public void setContentWidth(String contentWidth) {
		this.contentWidth = contentWidth;
	}

	public void setRightPanelBehaviour(String rightPanelBehaviour) {
		this.rightPanelBehaviour = rightPanelBehaviour;
	}

	public void setPageSpacing(String pageSpacing) {
		this.pageSpacing = pageSpacing;
	}

	public void setHeaderBehaviour(String headerBehaviour) {
		this.headerBehaviour = headerBehaviour;
	}

	public void setCornerRadius(String cornerRadius) {
		this.cornerRadius = cornerRadius;
	}

	public void setPanelTransparency(int panelTransparency) {
		this.panelTransparency = clamp(panelTransparency, 55, 100);
	}

	public void setBackgroundBlur(int backgroundBlur) {
		this.backgroundBlur = clamp(backgroundBlur, 0, 32);
	}

	public void setBorderStrength(String borderStrength) {
		this.borderStrength = borderStrength;
	}

	public void setGlowIntensity(int glowIntensity) {
		this.glowIntensity = clamp(glowIntensity, 0, 100);
	}

	public void setAnimationSpeed(String animationSpeed) {
		this.animationSpeed = animationSpeed;
	}

	public void setUiScale(int uiScale) {
		this.uiScale = clamp(uiScale, 85, 115);
	}

	public void setFontSize(int fontSize) {
		this.fontSize = clamp(fontSize, 85, 120);
	}

	public void setIconSize(int iconSize) {
		this.iconSize = clamp(iconSize, 80, 125);
	}

	public void setReduceMotion(boolean reduceMotion) {
		this.reduceMotion = reduceMotion;
	}

	public void setLayoutPreset(String layoutPreset) {
		this.layoutPreset = layoutPreset;
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

	public void setOpenMenuKey(int openMenuKey) {
		this.openMenuKey = openMenuKey;
	}

	public void setOverlayEditKey(int overlayEditKey) {
		this.overlayEditKey = overlayEditKey;
	}

	public void setRunLastScriptKey(int runLastScriptKey) {
		this.runLastScriptKey = runLastScriptKey;
	}

	public void setLastScriptPath(String lastScriptPath) {
		this.lastScriptPath = lastScriptPath == null ? "" : lastScriptPath;
	}

	private String toJson() {
		return "{\n"
				+ "  \"theme\": \"" + escape(theme) + "\",\n"
				+ "  \"accent\": \"" + escape(accent) + "\",\n"
				+ "  \"density\": \"" + escape(density) + "\",\n"
				+ "  \"sidebarWidth\": \"" + escape(sidebarWidth) + "\",\n"
				+ "  \"navigationMode\": \"" + escape(navigationMode) + "\",\n"
				+ "  \"contentWidth\": \"" + escape(contentWidth) + "\",\n"
				+ "  \"rightPanelBehaviour\": \"" + escape(rightPanelBehaviour) + "\",\n"
				+ "  \"pageSpacing\": \"" + escape(pageSpacing) + "\",\n"
				+ "  \"headerBehaviour\": \"" + escape(headerBehaviour) + "\",\n"
				+ "  \"cornerRadius\": \"" + escape(cornerRadius) + "\",\n"
				+ "  \"panelTransparency\": " + panelTransparency + ",\n"
				+ "  \"backgroundBlur\": " + backgroundBlur + ",\n"
				+ "  \"borderStrength\": \"" + escape(borderStrength) + "\",\n"
				+ "  \"glowIntensity\": " + glowIntensity + ",\n"
				+ "  \"animationSpeed\": \"" + escape(animationSpeed) + "\",\n"
				+ "  \"uiScale\": " + uiScale + ",\n"
				+ "  \"fontSize\": " + fontSize + ",\n"
				+ "  \"iconSize\": " + iconSize + ",\n"
				+ "  \"reduceMotion\": " + reduceMotion + ",\n"
				+ "  \"layoutPreset\": \"" + escape(layoutPreset) + "\",\n"
				+ "  \"defaultPage\": \"" + escape(defaultPage) + "\",\n"
				+ "  \"rememberLastPage\": " + rememberLastPage + ",\n"
				+ "  \"autosaveScripts\": " + autosaveScripts + ",\n"
				+ "  \"ruffDiagnostics\": " + ruffDiagnostics + ",\n"
				+ "  \"inlineAutocomplete\": " + inlineAutocomplete + ",\n"
				+ "  \"confirmDestructiveScripts\": " + confirmDestructiveScripts + ",\n"
				+ "  \"blockNetworkByDefault\": " + blockNetworkByDefault + ",\n"
				+ "  \"backupHistory\": " + backupHistory + ",\n"
				+ "  \"openMenuKey\": " + openMenuKey + ",\n"
				+ "  \"overlayEditKey\": " + overlayEditKey + ",\n"
				+ "  \"runLastScriptKey\": " + runLastScriptKey + ",\n"
				+ "  \"lastScriptPath\": \"" + escape(lastScriptPath) + "\"\n"
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

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
