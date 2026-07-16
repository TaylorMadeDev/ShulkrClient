package dev.triton.ui.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.triton.ui.script.ShortcutBinding;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FluxusConfig {
	private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
	private static final Pattern BOOLEAN_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(true|false)");
	private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shulkr-client.json");
	private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shulk-client.json");

	public static Path configPath() { return CONFIG_PATH; }

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
	private boolean pythonAutocomplete = true;
	private boolean inlineSuggestions = true;
	private boolean hoverDocumentation = true;
	private boolean signatureHelp = true;
	private boolean autoCloseBracketsAndQuotes = true;
	private boolean bracketPairHighlighting = true;
	private boolean wordWrap;
	private int editorTabSize = 4;
	private boolean convertTabsToSpaces = true;
	private boolean formatPastedIndentation = true;
	private int editorFontSize = 12;
	private int editorLineHeight = 18;
	private String cursorStyle = "Line";
	private boolean smoothCursorAnimation = true;
	private boolean showLineNumbers = true;
	private boolean highlightCurrentLine = true;
	private boolean renderWhitespace;
	private boolean showIndentationGuides = true;
	private boolean showMinimap = true;
	private String autosaveMode = "After Delay";
	private int autosaveDelay = 1000;
	private boolean trimTrailingWhitespaceOnSave = true;
	private boolean insertFinalNewline = true;
	private boolean createBackupBeforeSaving = true;
	private int maximumBackupCount = 25;
	private boolean restoreUnsavedTabs = true;
	private boolean confirmCloseUnsaved = true;
	private boolean saveBeforeRunning = true;
	private boolean stopPreviousBeforeRunning = true;
	private boolean clearOutputBeforeRunning;
	private boolean openOutputOnRun = true;
	private boolean focusOutputOnError = true;
	private int executionTimeoutSeconds;
	private String workingDirectoryMode = "Script Folder";
	private String customWorkingDirectory = "";
	private boolean confirmDangerousScripts = true;
	private boolean stopScriptsOnWorldLeave = true;
	private boolean developerMode;
	private boolean showInternalScriptIds;
	private boolean showAdvancedRuntimeDetails;
	private boolean showDebugTooltips;
	private boolean verboseClientLogging;
	private boolean verboseMinescriptLogging;
	private int scriptWorkerLimit = 2;
	private int maximumConcurrentScripts = 4;
	private String executionThreadPriority = "Normal";
	private boolean backgroundScriptThrottling = true;
	private boolean pauseBackgroundScriptsWhenUnfocused;
	private int maximumRuntimeLogEntries = 500;
	private int runtimeLogBufferSizeKb = 1024;
	private int scriptStartupTimeoutSeconds = 15;
	private int clientBridgeReconnectDelaySeconds = 5;
	private boolean autoReconnectDashboard = true;
	private boolean reduceUiUpdatesWhileScriptRunning = true;
	private boolean hidePlayerNamesInCaptures = true;
	private boolean hideServerAddressesInCaptures = true;
	private boolean hideCoordinatesInCaptures = true;
	private boolean redactWindowsUsernamesAndPaths = true;
	private boolean redactAccountAndDeviceIdentifiers = true;
	private String telemetryMode = "Local diagnostics only";
	private String networkPermissionPolicy = "Ask every time";
	private String fileReadPermissionPolicy = "Allow trusted scripts";
	private String fileWritePermissionPolicy = "Ask every time";
	private String clipboardPermissionPolicy = "Ask every time";
	private String externalProcessPermissionPolicy = "Always block";
	private String chatPermissionPolicy = "Ask every time";
	private String movementPermissionPolicy = "Ask every time";
	private String worldActionPermissionPolicy = "Ask every time";
	private boolean rememberScriptPermissions = true;
	private boolean confirmUntrustedScripts = true;
	private boolean confirmDestructiveActions = true;
	private boolean oneMovementAutomationAtATime = true;
	private boolean stopScriptsOnServerChange = true;
	private boolean stopScriptsWhenClientCloses = true;
	private boolean pauseAutomationWhenMenuOpen;
	private int defaultScriptTimeoutSeconds;
	private int maximumScriptRuntimeSeconds;
	private int emergencyStopKey = 259;
	private String emergencyStopShortcut = new ShortcutBinding(259, ShortcutBinding.CTRL | ShortcutBinding.SHIFT).serialize();
	private boolean saveScriptExecutionHistory = true;
	private boolean saveRuntimeLogs = true;
	private String logRetention = "7 days";
	private boolean saveRecentlyOpenedScripts = true;
	private boolean saveSearchHistory = true;
	private boolean includeLocalPathsInDiagnostics;
	private boolean blockNetworkByDefault = true;
	private int openMenuKey = 85;
	private int overlayEditKey = -1;
	private int runLastScriptKey = 117;
	private String openMenuShortcut = new ShortcutBinding(85, 0).serialize();
	private String overlayEditShortcut = "";
	private String runLastScriptShortcut = new ShortcutBinding(117, 0).serialize();
	private String scriptShortcutData = "";
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
			boolean legacyAutocomplete = readBoolean(json, "inlineAutocomplete", config.pythonAutocomplete);
			config.pythonAutocomplete = readBoolean(json, "pythonAutocomplete", legacyAutocomplete);
			config.inlineSuggestions = readBoolean(json, "inlineSuggestions", legacyAutocomplete);
			config.hoverDocumentation = readBoolean(json, "hoverDocumentation", config.hoverDocumentation);
			config.signatureHelp = readBoolean(json, "signatureHelp", config.signatureHelp);
			config.autoCloseBracketsAndQuotes = readBoolean(json, "autoCloseBracketsAndQuotes", config.autoCloseBracketsAndQuotes);
			config.bracketPairHighlighting = readBoolean(json, "bracketPairHighlighting", config.bracketPairHighlighting);
			config.wordWrap = readBoolean(json, "wordWrap", config.wordWrap);
			config.editorTabSize = clamp(readInt(json, "editorTabSize", config.editorTabSize), 2, 8);
			config.convertTabsToSpaces = readBoolean(json, "convertTabsToSpaces", config.convertTabsToSpaces);
			config.formatPastedIndentation = readBoolean(json, "formatPastedIndentation", config.formatPastedIndentation);
			config.editorFontSize = clamp(readInt(json, "editorFontSize", config.editorFontSize), 10, 24);
			config.editorLineHeight = clamp(readInt(json, "editorLineHeight", config.editorLineHeight), 14, 36);
			config.cursorStyle = readString(json, "cursorStyle", config.cursorStyle);
			config.smoothCursorAnimation = readBoolean(json, "smoothCursorAnimation", config.smoothCursorAnimation);
			config.showLineNumbers = readBoolean(json, "showLineNumbers", config.showLineNumbers);
			config.highlightCurrentLine = readBoolean(json, "highlightCurrentLine", config.highlightCurrentLine);
			config.renderWhitespace = readBoolean(json, "renderWhitespace", config.renderWhitespace);
			config.showIndentationGuides = readBoolean(json, "showIndentationGuides", config.showIndentationGuides);
			config.showMinimap = readBoolean(json, "showMinimap", config.showMinimap);
			String legacyAutosave = readBoolean(json, "autosaveScripts", true) ? "After Delay" : "Off";
			config.autosaveMode = readString(json, "autosaveMode", legacyAutosave);
			config.autosaveDelay = clamp(readInt(json, "autosaveDelay", config.autosaveDelay), 250, 5000);
			config.trimTrailingWhitespaceOnSave = readBoolean(json, "trimTrailingWhitespaceOnSave", config.trimTrailingWhitespaceOnSave);
			config.insertFinalNewline = readBoolean(json, "insertFinalNewline", config.insertFinalNewline);
			config.createBackupBeforeSaving = readBoolean(json, "createBackupBeforeSaving", config.createBackupBeforeSaving);
			config.maximumBackupCount = clamp(readInt(json, "maximumBackupCount", readInt(json, "backupHistory", config.maximumBackupCount)), 0, 100);
			config.restoreUnsavedTabs = readBoolean(json, "restoreUnsavedTabs", config.restoreUnsavedTabs);
			config.confirmCloseUnsaved = readBoolean(json, "confirmCloseUnsaved", config.confirmCloseUnsaved);
			config.saveBeforeRunning = readBoolean(json, "saveBeforeRunning", config.saveBeforeRunning);
			config.stopPreviousBeforeRunning = readBoolean(json, "stopPreviousBeforeRunning", config.stopPreviousBeforeRunning);
			config.clearOutputBeforeRunning = readBoolean(json, "clearOutputBeforeRunning", config.clearOutputBeforeRunning);
			config.openOutputOnRun = readBoolean(json, "openOutputOnRun", config.openOutputOnRun);
			config.focusOutputOnError = readBoolean(json, "focusOutputOnError", config.focusOutputOnError);
			config.executionTimeoutSeconds = clamp(readInt(json, "executionTimeoutSeconds", config.executionTimeoutSeconds), 0, 300);
			config.workingDirectoryMode = readString(json, "workingDirectoryMode", config.workingDirectoryMode);
			config.customWorkingDirectory = readString(json, "customWorkingDirectory", config.customWorkingDirectory);
			config.confirmDangerousScripts = readBoolean(json, "confirmDangerousScripts", readBoolean(json, "confirmDestructiveScripts", config.confirmDangerousScripts));
			config.stopScriptsOnWorldLeave = readBoolean(json, "stopScriptsOnWorldLeave", config.stopScriptsOnWorldLeave);
			config.developerMode = readBoolean(json, "developerMode", config.developerMode);
			config.showInternalScriptIds = readBoolean(json, "showInternalScriptIds", config.showInternalScriptIds);
			config.showAdvancedRuntimeDetails = readBoolean(json, "showAdvancedRuntimeDetails", config.showAdvancedRuntimeDetails);
			config.showDebugTooltips = readBoolean(json, "showDebugTooltips", config.showDebugTooltips);
			config.verboseClientLogging = readBoolean(json, "verboseClientLogging", config.verboseClientLogging);
			config.verboseMinescriptLogging = readBoolean(json, "verboseMinescriptLogging", config.verboseMinescriptLogging);
			config.scriptWorkerLimit = clamp(readInt(json, "scriptWorkerLimit", config.scriptWorkerLimit), 1, 8);
			config.maximumConcurrentScripts = clamp(readInt(json, "maximumConcurrentScripts", config.maximumConcurrentScripts), 1, 16);
			config.executionThreadPriority = readString(json, "executionThreadPriority", config.executionThreadPriority);
			if (!validChoice(config.executionThreadPriority, "Low", "Normal", "High")) config.executionThreadPriority = "Normal";
			config.backgroundScriptThrottling = readBoolean(json, "backgroundScriptThrottling", config.backgroundScriptThrottling);
			config.pauseBackgroundScriptsWhenUnfocused = readBoolean(json, "pauseBackgroundScriptsWhenUnfocused", config.pauseBackgroundScriptsWhenUnfocused);
			config.maximumRuntimeLogEntries = clamp(readInt(json, "maximumRuntimeLogEntries", config.maximumRuntimeLogEntries), 100, 5000);
			config.runtimeLogBufferSizeKb = clamp(readInt(json, "runtimeLogBufferSizeKb", config.runtimeLogBufferSizeKb), 64, 8192);
			config.scriptStartupTimeoutSeconds = clamp(readInt(json, "scriptStartupTimeoutSeconds", config.scriptStartupTimeoutSeconds), 1, 120);
			config.clientBridgeReconnectDelaySeconds = clamp(readInt(json, "clientBridgeReconnectDelaySeconds", config.clientBridgeReconnectDelaySeconds), 1, 60);
			config.autoReconnectDashboard = readBoolean(json, "autoReconnectDashboard", config.autoReconnectDashboard);
			config.reduceUiUpdatesWhileScriptRunning = readBoolean(json, "reduceUiUpdatesWhileScriptRunning", config.reduceUiUpdatesWhileScriptRunning);
			config.hidePlayerNamesInCaptures = readBoolean(json, "hidePlayerNamesInCaptures", config.hidePlayerNamesInCaptures);
			config.hideServerAddressesInCaptures = readBoolean(json, "hideServerAddressesInCaptures", config.hideServerAddressesInCaptures);
			config.hideCoordinatesInCaptures = readBoolean(json, "hideCoordinatesInCaptures", config.hideCoordinatesInCaptures);
			config.redactWindowsUsernamesAndPaths = readBoolean(json, "redactWindowsUsernamesAndPaths", config.redactWindowsUsernamesAndPaths);
			config.redactAccountAndDeviceIdentifiers = readBoolean(json, "redactAccountAndDeviceIdentifiers", config.redactAccountAndDeviceIdentifiers);
			config.telemetryMode = choice(readString(json, "telemetryMode", config.telemetryMode), config.telemetryMode,
					"Off", "Local diagnostics only", "Anonymous diagnostics");
			config.networkPermissionPolicy = permissionPolicy(json, "networkPermissionPolicy", config.networkPermissionPolicy);
			config.fileReadPermissionPolicy = permissionPolicy(json, "fileReadPermissionPolicy", config.fileReadPermissionPolicy);
			config.fileWritePermissionPolicy = permissionPolicy(json, "fileWritePermissionPolicy", config.fileWritePermissionPolicy);
			config.clipboardPermissionPolicy = permissionPolicy(json, "clipboardPermissionPolicy", config.clipboardPermissionPolicy);
			config.externalProcessPermissionPolicy = permissionPolicy(json, "externalProcessPermissionPolicy", config.externalProcessPermissionPolicy);
			config.chatPermissionPolicy = permissionPolicy(json, "chatPermissionPolicy", config.chatPermissionPolicy);
			config.movementPermissionPolicy = permissionPolicy(json, "movementPermissionPolicy", config.movementPermissionPolicy);
			config.worldActionPermissionPolicy = permissionPolicy(json, "worldActionPermissionPolicy", config.worldActionPermissionPolicy);
			config.rememberScriptPermissions = readBoolean(json, "rememberScriptPermissions", config.rememberScriptPermissions);
			config.confirmUntrustedScripts = readBoolean(json, "confirmUntrustedScripts", config.confirmUntrustedScripts);
			config.confirmDestructiveActions = readBoolean(json, "confirmDestructiveActions", config.confirmDestructiveActions);
			config.oneMovementAutomationAtATime = readBoolean(json, "oneMovementAutomationAtATime", config.oneMovementAutomationAtATime);
			config.stopScriptsOnServerChange = readBoolean(json, "stopScriptsOnServerChange", config.stopScriptsOnServerChange);
			config.stopScriptsWhenClientCloses = readBoolean(json, "stopScriptsWhenClientCloses", config.stopScriptsWhenClientCloses);
			config.pauseAutomationWhenMenuOpen = readBoolean(json, "pauseAutomationWhenMenuOpen", config.pauseAutomationWhenMenuOpen);
			config.defaultScriptTimeoutSeconds = clamp(readInt(json, "defaultScriptTimeoutSeconds", config.defaultScriptTimeoutSeconds), 0, 3600);
			config.maximumScriptRuntimeSeconds = clamp(readInt(json, "maximumScriptRuntimeSeconds", config.maximumScriptRuntimeSeconds), 0, 86400);
			config.emergencyStopKey = readInt(json, "emergencyStopKey", config.emergencyStopKey);
			config.emergencyStopShortcut = readString(json, "emergencyStopShortcut", config.emergencyStopShortcut);
			config.saveScriptExecutionHistory = readBoolean(json, "saveScriptExecutionHistory", config.saveScriptExecutionHistory);
			config.saveRuntimeLogs = readBoolean(json, "saveRuntimeLogs", config.saveRuntimeLogs);
			config.logRetention = choice(readString(json, "logRetention", config.logRetention), config.logRetention,
					"Session only", "1 day", "7 days", "30 days");
			config.saveRecentlyOpenedScripts = readBoolean(json, "saveRecentlyOpenedScripts", config.saveRecentlyOpenedScripts);
			config.saveSearchHistory = readBoolean(json, "saveSearchHistory", config.saveSearchHistory);
			config.includeLocalPathsInDiagnostics = readBoolean(json, "includeLocalPathsInDiagnostics", config.includeLocalPathsInDiagnostics);
			config.blockNetworkByDefault = readBoolean(json, "blockNetworkByDefault", config.blockNetworkByDefault);
			if (!json.contains("\"networkPermissionPolicy\"")) {
				config.networkPermissionPolicy = config.blockNetworkByDefault ? "Always block" : "Ask every time";
			}
			config.openMenuKey = readInt(json, "openMenuKey", config.openMenuKey);
			config.overlayEditKey = readInt(json, "overlayEditKey", config.overlayEditKey);
			config.runLastScriptKey = readInt(json, "runLastScriptKey", config.runLastScriptKey);
			config.openMenuShortcut = readString(json, "openMenuShortcut", new ShortcutBinding(config.openMenuKey, 0).serialize());
			config.overlayEditShortcut = readString(json, "overlayEditShortcut", config.overlayEditKey < 0 ? "" : new ShortcutBinding(config.overlayEditKey, 0).serialize());
			config.runLastScriptShortcut = readString(json, "runLastScriptShortcut", new ShortcutBinding(config.runLastScriptKey, 0).serialize());
			config.scriptShortcutData = readString(json, "scriptShortcutData", "");
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

	public boolean pythonAutocomplete() { return pythonAutocomplete; }
	public boolean inlineSuggestions() { return inlineSuggestions; }
	public boolean hoverDocumentation() { return hoverDocumentation; }
	public boolean signatureHelp() { return signatureHelp; }
	public boolean autoCloseBracketsAndQuotes() { return autoCloseBracketsAndQuotes; }
	public boolean bracketPairHighlighting() { return bracketPairHighlighting; }
	public boolean wordWrap() { return wordWrap; }
	public int editorTabSize() { return editorTabSize; }
	public boolean convertTabsToSpaces() { return convertTabsToSpaces; }
	public boolean formatPastedIndentation() { return formatPastedIndentation; }
	public int editorFontSize() { return editorFontSize; }
	public int editorLineHeight() { return editorLineHeight; }
	public String cursorStyle() { return cursorStyle; }
	public boolean smoothCursorAnimation() { return smoothCursorAnimation; }
	public boolean showLineNumbers() { return showLineNumbers; }
	public boolean highlightCurrentLine() { return highlightCurrentLine; }
	public boolean renderWhitespace() { return renderWhitespace; }
	public boolean showIndentationGuides() { return showIndentationGuides; }
	public boolean showMinimap() { return showMinimap; }
	public String autosaveMode() { return autosaveMode; }
	public int autosaveDelay() { return autosaveDelay; }
	public boolean trimTrailingWhitespaceOnSave() { return trimTrailingWhitespaceOnSave; }
	public boolean insertFinalNewline() { return insertFinalNewline; }
	public boolean createBackupBeforeSaving() { return createBackupBeforeSaving; }
	public int maximumBackupCount() { return maximumBackupCount; }
	public boolean restoreUnsavedTabs() { return restoreUnsavedTabs; }
	public boolean confirmCloseUnsaved() { return confirmCloseUnsaved; }
	public boolean saveBeforeRunning() { return saveBeforeRunning; }
	public boolean stopPreviousBeforeRunning() { return stopPreviousBeforeRunning; }
	public boolean clearOutputBeforeRunning() { return clearOutputBeforeRunning; }
	public boolean openOutputOnRun() { return openOutputOnRun; }
	public boolean focusOutputOnError() { return focusOutputOnError; }
	public int executionTimeoutSeconds() { return executionTimeoutSeconds; }
	public String workingDirectoryMode() { return workingDirectoryMode; }
	public String customWorkingDirectory() { return customWorkingDirectory; }
	public boolean confirmDangerousScripts() { return confirmDangerousScripts; }
	public boolean stopScriptsOnWorldLeave() { return stopScriptsOnWorldLeave; }
	public boolean developerMode() { return developerMode; }
	public boolean showInternalScriptIds() { return showInternalScriptIds; }
	public boolean showAdvancedRuntimeDetails() { return showAdvancedRuntimeDetails; }
	public boolean showDebugTooltips() { return showDebugTooltips; }
	public boolean verboseClientLogging() { return verboseClientLogging; }
	public boolean verboseMinescriptLogging() { return verboseMinescriptLogging; }
	public int scriptWorkerLimit() { return scriptWorkerLimit; }
	public int maximumConcurrentScripts() { return maximumConcurrentScripts; }
	public String executionThreadPriority() { return executionThreadPriority; }
	public boolean backgroundScriptThrottling() { return backgroundScriptThrottling; }
	public boolean pauseBackgroundScriptsWhenUnfocused() { return pauseBackgroundScriptsWhenUnfocused; }
	public int maximumRuntimeLogEntries() { return maximumRuntimeLogEntries; }
	public int runtimeLogBufferSizeKb() { return runtimeLogBufferSizeKb; }
	public int scriptStartupTimeoutSeconds() { return scriptStartupTimeoutSeconds; }
	public int clientBridgeReconnectDelaySeconds() { return clientBridgeReconnectDelaySeconds; }
	public boolean autoReconnectDashboard() { return autoReconnectDashboard; }
	public boolean reduceUiUpdatesWhileScriptRunning() { return reduceUiUpdatesWhileScriptRunning; }
	public boolean hidePlayerNamesInCaptures() { return hidePlayerNamesInCaptures; }
	public boolean hideServerAddressesInCaptures() { return hideServerAddressesInCaptures; }
	public boolean hideCoordinatesInCaptures() { return hideCoordinatesInCaptures; }
	public boolean redactWindowsUsernamesAndPaths() { return redactWindowsUsernamesAndPaths; }
	public boolean redactAccountAndDeviceIdentifiers() { return redactAccountAndDeviceIdentifiers; }
	public String telemetryMode() { return telemetryMode; }
	public boolean rememberScriptPermissions() { return rememberScriptPermissions; }
	public boolean confirmUntrustedScripts() { return confirmUntrustedScripts; }
	public boolean confirmDestructiveActions() { return confirmDestructiveActions; }
	public boolean oneMovementAutomationAtATime() { return oneMovementAutomationAtATime; }
	public boolean stopScriptsOnServerChange() { return stopScriptsOnServerChange; }
	public boolean stopScriptsWhenClientCloses() { return stopScriptsWhenClientCloses; }
	public boolean pauseAutomationWhenMenuOpen() { return pauseAutomationWhenMenuOpen; }
	public int defaultScriptTimeoutSeconds() { return defaultScriptTimeoutSeconds; }
	public int maximumScriptRuntimeSeconds() { return maximumScriptRuntimeSeconds; }
	public boolean saveScriptExecutionHistory() { return saveScriptExecutionHistory; }
	public boolean saveRuntimeLogs() { return saveRuntimeLogs; }
	public String logRetention() { return logRetention; }
	public boolean saveRecentlyOpenedScripts() { return saveRecentlyOpenedScripts; }
	public boolean saveSearchHistory() { return saveSearchHistory; }
	public boolean includeLocalPathsInDiagnostics() { return includeLocalPathsInDiagnostics; }
	public ShortcutBinding emergencyStopShortcut() { return ShortcutBinding.parse(emergencyStopShortcut, new ShortcutBinding(emergencyStopKey, ShortcutBinding.CTRL | ShortcutBinding.SHIFT)); }
	public String permissionPolicy(String permissionName) {
		return switch (permissionName) {
			case "NETWORK" -> networkPermissionPolicy;
			case "FILE_READ" -> fileReadPermissionPolicy;
			case "FILE_WRITE" -> fileWritePermissionPolicy;
			case "CLIPBOARD" -> clipboardPermissionPolicy;
			case "EXTERNAL_PROCESS" -> externalProcessPermissionPolicy;
			case "CHAT" -> chatPermissionPolicy;
			case "MOVEMENT" -> movementPermissionPolicy;
			case "WORLD_ACTION" -> worldActionPermissionPolicy;
			default -> "Ask every time";
		};
	}

	public boolean blockNetworkByDefault() {
		return blockNetworkByDefault;
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

	public ShortcutBinding openMenuShortcut() {
		return ShortcutBinding.parse(openMenuShortcut, new ShortcutBinding(openMenuKey, 0));
	}

	public ShortcutBinding overlayEditShortcut() {
		return ShortcutBinding.parse(overlayEditShortcut, overlayEditKey < 0 ? ShortcutBinding.unbound() : new ShortcutBinding(overlayEditKey, 0));
	}

	public ShortcutBinding runLastScriptShortcut() {
		return ShortcutBinding.parse(runLastScriptShortcut, new ShortcutBinding(runLastScriptKey, 0));
	}

	public Map<String, ShortcutBinding> scriptShortcuts() {
		Map<String, ShortcutBinding> result = new LinkedHashMap<>();
		if (scriptShortcutData.isBlank()) return result;
		Base64.Decoder decoder = Base64.getUrlDecoder();
		for (String entry : scriptShortcutData.split(",")) {
			int equals = entry.indexOf('=');
			if (equals < 1) continue;
			try {
				String id = new String(decoder.decode(entry.substring(0, equals)), StandardCharsets.UTF_8);
				String binding = new String(decoder.decode(entry.substring(equals + 1)), StandardCharsets.UTF_8);
				ShortcutBinding parsed = ShortcutBinding.parse(binding, ShortcutBinding.unbound());
				if (parsed.bound()) result.put(id, parsed);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return result;
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

	public void setPythonAutocomplete(boolean value) { pythonAutocomplete = value; }
	public void setInlineSuggestions(boolean value) { inlineSuggestions = value; }
	public void setHoverDocumentation(boolean value) { hoverDocumentation = value; }
	public void setSignatureHelp(boolean value) { signatureHelp = value; }
	public void setAutoCloseBracketsAndQuotes(boolean value) { autoCloseBracketsAndQuotes = value; }
	public void setBracketPairHighlighting(boolean value) { bracketPairHighlighting = value; }
	public void setWordWrap(boolean value) { wordWrap = value; }
	public void setEditorTabSize(int value) { editorTabSize = value == 2 || value == 8 ? value : 4; }
	public void setConvertTabsToSpaces(boolean value) { convertTabsToSpaces = value; }
	public void setFormatPastedIndentation(boolean value) { formatPastedIndentation = value; }
	public void setEditorFontSize(int value) { editorFontSize = clamp(value, 10, 24); }
	public void setEditorLineHeight(int value) { editorLineHeight = clamp(value, 14, 36); }
	public void setCursorStyle(String value) { cursorStyle = value; }
	public void setSmoothCursorAnimation(boolean value) { smoothCursorAnimation = value; }
	public void setShowLineNumbers(boolean value) { showLineNumbers = value; }
	public void setHighlightCurrentLine(boolean value) { highlightCurrentLine = value; }
	public void setRenderWhitespace(boolean value) { renderWhitespace = value; }
	public void setShowIndentationGuides(boolean value) { showIndentationGuides = value; }
	public void setShowMinimap(boolean value) { showMinimap = value; }
	public void setAutosaveMode(String value) { autosaveMode = value; }
	public void setAutosaveDelay(int value) { autosaveDelay = clamp(value, 250, 5000); }
	public void setTrimTrailingWhitespaceOnSave(boolean value) { trimTrailingWhitespaceOnSave = value; }
	public void setInsertFinalNewline(boolean value) { insertFinalNewline = value; }
	public void setCreateBackupBeforeSaving(boolean value) { createBackupBeforeSaving = value; }
	public void setMaximumBackupCount(int value) { maximumBackupCount = clamp(value, 0, 100); }
	public void setRestoreUnsavedTabs(boolean value) { restoreUnsavedTabs = value; }
	public void setConfirmCloseUnsaved(boolean value) { confirmCloseUnsaved = value; }
	public void setSaveBeforeRunning(boolean value) { saveBeforeRunning = value; }
	public void setStopPreviousBeforeRunning(boolean value) { stopPreviousBeforeRunning = value; }
	public void setClearOutputBeforeRunning(boolean value) { clearOutputBeforeRunning = value; }
	public void setOpenOutputOnRun(boolean value) { openOutputOnRun = value; }
	public void setFocusOutputOnError(boolean value) { focusOutputOnError = value; }
	public void setExecutionTimeoutSeconds(int value) { executionTimeoutSeconds = clamp(value, 0, 300); }
	public void setWorkingDirectoryMode(String value) { workingDirectoryMode = value; }
	public void setCustomWorkingDirectory(String value) { customWorkingDirectory = value == null ? "" : value; }
	public void setConfirmDangerousScripts(boolean value) { confirmDangerousScripts = value; }
	public void setStopScriptsOnWorldLeave(boolean value) { stopScriptsOnWorldLeave = value; }
	public void setDeveloperMode(boolean value) { developerMode = value; }
	public void setShowInternalScriptIds(boolean value) { showInternalScriptIds = value; }
	public void setShowAdvancedRuntimeDetails(boolean value) { showAdvancedRuntimeDetails = value; }
	public void setShowDebugTooltips(boolean value) { showDebugTooltips = value; }
	public void setVerboseClientLogging(boolean value) { verboseClientLogging = value; }
	public void setVerboseMinescriptLogging(boolean value) { verboseMinescriptLogging = value; }
	public void setScriptWorkerLimit(int value) { scriptWorkerLimit = clamp(value, 1, 8); }
	public void setMaximumConcurrentScripts(int value) { maximumConcurrentScripts = clamp(value, 1, 16); }
	public void setExecutionThreadPriority(String value) { executionThreadPriority = validChoice(value, "Low", "Normal", "High") ? value : "Normal"; }
	public void setBackgroundScriptThrottling(boolean value) { backgroundScriptThrottling = value; }
	public void setPauseBackgroundScriptsWhenUnfocused(boolean value) { pauseBackgroundScriptsWhenUnfocused = value; }
	public void setMaximumRuntimeLogEntries(int value) { maximumRuntimeLogEntries = clamp(value, 100, 5000); }
	public void setRuntimeLogBufferSizeKb(int value) { runtimeLogBufferSizeKb = clamp(value, 64, 8192); }
	public void setScriptStartupTimeoutSeconds(int value) { scriptStartupTimeoutSeconds = clamp(value, 1, 120); }
	public void setClientBridgeReconnectDelaySeconds(int value) { clientBridgeReconnectDelaySeconds = clamp(value, 1, 60); }
	public void setAutoReconnectDashboard(boolean value) { autoReconnectDashboard = value; }
	public void setReduceUiUpdatesWhileScriptRunning(boolean value) { reduceUiUpdatesWhileScriptRunning = value; }
	public void setHidePlayerNamesInCaptures(boolean value) { hidePlayerNamesInCaptures = value; }
	public void setHideServerAddressesInCaptures(boolean value) { hideServerAddressesInCaptures = value; }
	public void setHideCoordinatesInCaptures(boolean value) { hideCoordinatesInCaptures = value; }
	public void setRedactWindowsUsernamesAndPaths(boolean value) { redactWindowsUsernamesAndPaths = value; }
	public void setRedactAccountAndDeviceIdentifiers(boolean value) { redactAccountAndDeviceIdentifiers = value; }
	public void setTelemetryMode(String value) { telemetryMode = choice(value, telemetryMode, "Off", "Local diagnostics only", "Anonymous diagnostics"); }
	public void setRememberScriptPermissions(boolean value) { rememberScriptPermissions = value; }
	public void setConfirmUntrustedScripts(boolean value) { confirmUntrustedScripts = value; }
	public void setConfirmDestructiveActions(boolean value) { confirmDestructiveActions = value; }
	public void setOneMovementAutomationAtATime(boolean value) { oneMovementAutomationAtATime = value; }
	public void setStopScriptsOnServerChange(boolean value) { stopScriptsOnServerChange = value; }
	public void setStopScriptsWhenClientCloses(boolean value) { stopScriptsWhenClientCloses = value; }
	public void setPauseAutomationWhenMenuOpen(boolean value) { pauseAutomationWhenMenuOpen = value; }
	public void setDefaultScriptTimeoutSeconds(int value) { defaultScriptTimeoutSeconds = clamp(value, 0, 3600); }
	public void setMaximumScriptRuntimeSeconds(int value) { maximumScriptRuntimeSeconds = clamp(value, 0, 86400); }
	public void setSaveScriptExecutionHistory(boolean value) { saveScriptExecutionHistory = value; }
	public void setSaveRuntimeLogs(boolean value) { saveRuntimeLogs = value; }
	public void setLogRetention(String value) { logRetention = choice(value, logRetention, "Session only", "1 day", "7 days", "30 days"); }
	public void setSaveRecentlyOpenedScripts(boolean value) { saveRecentlyOpenedScripts = value; }
	public void setSaveSearchHistory(boolean value) { saveSearchHistory = value; }
	public void setIncludeLocalPathsInDiagnostics(boolean value) { includeLocalPathsInDiagnostics = value; }
	public void setEmergencyStopShortcut(ShortcutBinding binding) {
		emergencyStopShortcut = binding == null ? "" : binding.serialize();
		emergencyStopKey = binding == null ? -1 : binding.key();
	}
	public void setPermissionPolicy(String permissionName, String value) {
		String policy = choice(value, "Ask every time", "Ask every time", "Allow trusted scripts", "Always block");
		switch (permissionName) {
			case "NETWORK" -> networkPermissionPolicy = policy;
			case "FILE_READ" -> fileReadPermissionPolicy = policy;
			case "FILE_WRITE" -> fileWritePermissionPolicy = policy;
			case "CLIPBOARD" -> clipboardPermissionPolicy = policy;
			case "EXTERNAL_PROCESS" -> externalProcessPermissionPolicy = policy;
			case "CHAT" -> chatPermissionPolicy = policy;
			case "MOVEMENT" -> movementPermissionPolicy = policy;
			case "WORLD_ACTION" -> worldActionPermissionPolicy = policy;
		}
	}

	public void setBlockNetworkByDefault(boolean blockNetworkByDefault) {
		this.blockNetworkByDefault = blockNetworkByDefault;
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

	public void setOpenMenuShortcut(ShortcutBinding binding) {
		openMenuShortcut = binding == null ? "" : binding.serialize();
		openMenuKey = binding == null ? -1 : binding.key();
	}

	public void setOverlayEditShortcut(ShortcutBinding binding) {
		overlayEditShortcut = binding == null ? "" : binding.serialize();
		overlayEditKey = binding == null ? -1 : binding.key();
	}

	public void setRunLastScriptShortcut(ShortcutBinding binding) {
		runLastScriptShortcut = binding == null ? "" : binding.serialize();
		runLastScriptKey = binding == null ? -1 : binding.key();
	}

	public void setScriptShortcuts(Map<String, ShortcutBinding> shortcuts) {
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, ShortcutBinding> entry : shortcuts.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().bound()) continue;
			if (!result.isEmpty()) result.append(',');
			result.append(encoder.encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8)))
					.append('=')
					.append(encoder.encodeToString(entry.getValue().serialize().getBytes(StandardCharsets.UTF_8)));
		}
		scriptShortcutData = result.toString();
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
				+ "  \"pythonAutocomplete\": " + pythonAutocomplete + ",\n"
				+ "  \"inlineSuggestions\": " + inlineSuggestions + ",\n"
				+ "  \"hoverDocumentation\": " + hoverDocumentation + ",\n"
				+ "  \"signatureHelp\": " + signatureHelp + ",\n"
				+ "  \"autoCloseBracketsAndQuotes\": " + autoCloseBracketsAndQuotes + ",\n"
				+ "  \"bracketPairHighlighting\": " + bracketPairHighlighting + ",\n"
				+ "  \"wordWrap\": " + wordWrap + ",\n"
				+ "  \"editorTabSize\": " + editorTabSize + ",\n"
				+ "  \"convertTabsToSpaces\": " + convertTabsToSpaces + ",\n"
				+ "  \"formatPastedIndentation\": " + formatPastedIndentation + ",\n"
				+ "  \"editorFontSize\": " + editorFontSize + ",\n"
				+ "  \"editorLineHeight\": " + editorLineHeight + ",\n"
				+ "  \"cursorStyle\": \"" + escape(cursorStyle) + "\",\n"
				+ "  \"smoothCursorAnimation\": " + smoothCursorAnimation + ",\n"
				+ "  \"showLineNumbers\": " + showLineNumbers + ",\n"
				+ "  \"highlightCurrentLine\": " + highlightCurrentLine + ",\n"
				+ "  \"renderWhitespace\": " + renderWhitespace + ",\n"
				+ "  \"showIndentationGuides\": " + showIndentationGuides + ",\n"
				+ "  \"showMinimap\": " + showMinimap + ",\n"
				+ "  \"autosaveMode\": \"" + escape(autosaveMode) + "\",\n"
				+ "  \"autosaveDelay\": " + autosaveDelay + ",\n"
				+ "  \"trimTrailingWhitespaceOnSave\": " + trimTrailingWhitespaceOnSave + ",\n"
				+ "  \"insertFinalNewline\": " + insertFinalNewline + ",\n"
				+ "  \"createBackupBeforeSaving\": " + createBackupBeforeSaving + ",\n"
				+ "  \"maximumBackupCount\": " + maximumBackupCount + ",\n"
				+ "  \"restoreUnsavedTabs\": " + restoreUnsavedTabs + ",\n"
				+ "  \"confirmCloseUnsaved\": " + confirmCloseUnsaved + ",\n"
				+ "  \"saveBeforeRunning\": " + saveBeforeRunning + ",\n"
				+ "  \"stopPreviousBeforeRunning\": " + stopPreviousBeforeRunning + ",\n"
				+ "  \"clearOutputBeforeRunning\": " + clearOutputBeforeRunning + ",\n"
				+ "  \"openOutputOnRun\": " + openOutputOnRun + ",\n"
				+ "  \"focusOutputOnError\": " + focusOutputOnError + ",\n"
				+ "  \"executionTimeoutSeconds\": " + executionTimeoutSeconds + ",\n"
				+ "  \"workingDirectoryMode\": \"" + escape(workingDirectoryMode) + "\",\n"
				+ "  \"customWorkingDirectory\": \"" + escape(customWorkingDirectory) + "\",\n"
				+ "  \"confirmDangerousScripts\": " + confirmDangerousScripts + ",\n"
				+ "  \"stopScriptsOnWorldLeave\": " + stopScriptsOnWorldLeave + ",\n"
				+ "  \"developerMode\": " + developerMode + ",\n"
				+ "  \"showInternalScriptIds\": " + showInternalScriptIds + ",\n"
				+ "  \"showAdvancedRuntimeDetails\": " + showAdvancedRuntimeDetails + ",\n"
				+ "  \"showDebugTooltips\": " + showDebugTooltips + ",\n"
				+ "  \"verboseClientLogging\": " + verboseClientLogging + ",\n"
				+ "  \"verboseMinescriptLogging\": " + verboseMinescriptLogging + ",\n"
				+ "  \"scriptWorkerLimit\": " + scriptWorkerLimit + ",\n"
				+ "  \"maximumConcurrentScripts\": " + maximumConcurrentScripts + ",\n"
				+ "  \"executionThreadPriority\": \"" + escape(executionThreadPriority) + "\",\n"
				+ "  \"backgroundScriptThrottling\": " + backgroundScriptThrottling + ",\n"
				+ "  \"pauseBackgroundScriptsWhenUnfocused\": " + pauseBackgroundScriptsWhenUnfocused + ",\n"
				+ "  \"maximumRuntimeLogEntries\": " + maximumRuntimeLogEntries + ",\n"
				+ "  \"runtimeLogBufferSizeKb\": " + runtimeLogBufferSizeKb + ",\n"
				+ "  \"scriptStartupTimeoutSeconds\": " + scriptStartupTimeoutSeconds + ",\n"
				+ "  \"clientBridgeReconnectDelaySeconds\": " + clientBridgeReconnectDelaySeconds + ",\n"
				+ "  \"autoReconnectDashboard\": " + autoReconnectDashboard + ",\n"
				+ "  \"reduceUiUpdatesWhileScriptRunning\": " + reduceUiUpdatesWhileScriptRunning + ",\n"
				+ "  \"hidePlayerNamesInCaptures\": " + hidePlayerNamesInCaptures + ",\n"
				+ "  \"hideServerAddressesInCaptures\": " + hideServerAddressesInCaptures + ",\n"
				+ "  \"hideCoordinatesInCaptures\": " + hideCoordinatesInCaptures + ",\n"
				+ "  \"redactWindowsUsernamesAndPaths\": " + redactWindowsUsernamesAndPaths + ",\n"
				+ "  \"redactAccountAndDeviceIdentifiers\": " + redactAccountAndDeviceIdentifiers + ",\n"
				+ "  \"telemetryMode\": \"" + escape(telemetryMode) + "\",\n"
				+ "  \"networkPermissionPolicy\": \"" + escape(networkPermissionPolicy) + "\",\n"
				+ "  \"fileReadPermissionPolicy\": \"" + escape(fileReadPermissionPolicy) + "\",\n"
				+ "  \"fileWritePermissionPolicy\": \"" + escape(fileWritePermissionPolicy) + "\",\n"
				+ "  \"clipboardPermissionPolicy\": \"" + escape(clipboardPermissionPolicy) + "\",\n"
				+ "  \"externalProcessPermissionPolicy\": \"" + escape(externalProcessPermissionPolicy) + "\",\n"
				+ "  \"chatPermissionPolicy\": \"" + escape(chatPermissionPolicy) + "\",\n"
				+ "  \"movementPermissionPolicy\": \"" + escape(movementPermissionPolicy) + "\",\n"
				+ "  \"worldActionPermissionPolicy\": \"" + escape(worldActionPermissionPolicy) + "\",\n"
				+ "  \"rememberScriptPermissions\": " + rememberScriptPermissions + ",\n"
				+ "  \"confirmUntrustedScripts\": " + confirmUntrustedScripts + ",\n"
				+ "  \"confirmDestructiveActions\": " + confirmDestructiveActions + ",\n"
				+ "  \"oneMovementAutomationAtATime\": " + oneMovementAutomationAtATime + ",\n"
				+ "  \"stopScriptsOnServerChange\": " + stopScriptsOnServerChange + ",\n"
				+ "  \"stopScriptsWhenClientCloses\": " + stopScriptsWhenClientCloses + ",\n"
				+ "  \"pauseAutomationWhenMenuOpen\": " + pauseAutomationWhenMenuOpen + ",\n"
				+ "  \"defaultScriptTimeoutSeconds\": " + defaultScriptTimeoutSeconds + ",\n"
				+ "  \"maximumScriptRuntimeSeconds\": " + maximumScriptRuntimeSeconds + ",\n"
				+ "  \"emergencyStopKey\": " + emergencyStopKey + ",\n"
				+ "  \"emergencyStopShortcut\": \"" + escape(emergencyStopShortcut) + "\",\n"
				+ "  \"saveScriptExecutionHistory\": " + saveScriptExecutionHistory + ",\n"
				+ "  \"saveRuntimeLogs\": " + saveRuntimeLogs + ",\n"
				+ "  \"logRetention\": \"" + escape(logRetention) + "\",\n"
				+ "  \"saveRecentlyOpenedScripts\": " + saveRecentlyOpenedScripts + ",\n"
				+ "  \"saveSearchHistory\": " + saveSearchHistory + ",\n"
				+ "  \"includeLocalPathsInDiagnostics\": " + includeLocalPathsInDiagnostics + ",\n"
				+ "  \"blockNetworkByDefault\": " + blockNetworkByDefault + ",\n"
				+ "  \"openMenuKey\": " + openMenuKey + ",\n"
				+ "  \"overlayEditKey\": " + overlayEditKey + ",\n"
				+ "  \"runLastScriptKey\": " + runLastScriptKey + ",\n"
				+ "  \"openMenuShortcut\": \"" + escape(openMenuShortcut) + "\",\n"
				+ "  \"overlayEditShortcut\": \"" + escape(overlayEditShortcut) + "\",\n"
				+ "  \"runLastScriptShortcut\": \"" + escape(runLastScriptShortcut) + "\",\n"
				+ "  \"scriptShortcutData\": \"" + escape(scriptShortcutData) + "\",\n"
				+ "  \"lastScriptPath\": \"" + escape(lastScriptPath) + "\"\n"
				+ "}\n";
	}

	public static List<String> validateJson(String json) {
		List<String> errors = new ArrayList<>();
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				errors.add("Line 1: configuration root must be a JSON object.");
				return errors;
			}
			JsonObject object = parsed.getAsJsonObject();
			validateInt(json, object, "scriptWorkerLimit", 1, 8, errors);
			validateInt(json, object, "maximumConcurrentScripts", 1, 16, errors);
			validateInt(json, object, "maximumRuntimeLogEntries", 100, 5000, errors);
			validateInt(json, object, "runtimeLogBufferSizeKb", 64, 8192, errors);
			validateInt(json, object, "scriptStartupTimeoutSeconds", 1, 120, errors);
			validateInt(json, object, "clientBridgeReconnectDelaySeconds", 1, 60, errors);
			validateInt(json, object, "defaultScriptTimeoutSeconds", 0, 3600, errors);
			validateInt(json, object, "maximumScriptRuntimeSeconds", 0, 86400, errors);
			for (String key : List.of("developerMode", "showInternalScriptIds", "showAdvancedRuntimeDetails", "showDebugTooltips",
					"verboseClientLogging", "verboseMinescriptLogging", "backgroundScriptThrottling", "pauseBackgroundScriptsWhenUnfocused",
					"autoReconnectDashboard", "reduceUiUpdatesWhileScriptRunning", "hidePlayerNamesInCaptures", "hideServerAddressesInCaptures",
					"hideCoordinatesInCaptures", "redactWindowsUsernamesAndPaths", "redactAccountAndDeviceIdentifiers", "rememberScriptPermissions",
					"confirmUntrustedScripts", "confirmDestructiveActions", "oneMovementAutomationAtATime", "stopScriptsOnServerChange",
					"stopScriptsWhenClientCloses", "pauseAutomationWhenMenuOpen", "saveScriptExecutionHistory", "saveRuntimeLogs",
					"saveRecentlyOpenedScripts", "saveSearchHistory", "includeLocalPathsInDiagnostics")) validateBoolean(json, object, key, errors);
			validateChoice(json, object, "telemetryMode", errors, "Off", "Local diagnostics only", "Anonymous diagnostics");
			validateChoice(json, object, "logRetention", errors, "Session only", "1 day", "7 days", "30 days");
			for (String key : List.of("networkPermissionPolicy", "fileReadPermissionPolicy", "fileWritePermissionPolicy", "clipboardPermissionPolicy",
					"externalProcessPermissionPolicy", "chatPermissionPolicy", "movementPermissionPolicy", "worldActionPermissionPolicy")) {
				validateChoice(json, object, key, errors, "Ask every time", "Allow trusted scripts", "Always block");
			}
			if (object.has("executionThreadPriority") && !validChoice(object.get("executionThreadPriority").getAsString(), "Low", "Normal", "High")) {
				errors.add("Line " + lineForKey(json, "executionThreadPriority") + ": executionThreadPriority must be Low, Normal, or High.");
			}
		} catch (JsonParseException | IllegalStateException | UnsupportedOperationException error) {
			errors.add(error.getMessage() == null ? "Invalid JSON." : error.getMessage());
		}
		return errors;
	}

	private static void validateInt(String json, JsonObject object, String key, int min, int max, List<String> errors) {
		if (!object.has(key)) return;
		try {
			int value = object.get(key).getAsInt();
			if (value < min || value > max) errors.add("Line " + lineForKey(json, key) + ": " + key + " must be between " + min + " and " + max + ".");
		} catch (RuntimeException error) {
			errors.add("Line " + lineForKey(json, key) + ": " + key + " must be an integer.");
		}
	}

	private static void validateBoolean(String json, JsonObject object, String key, List<String> errors) {
		if ((object.has(key) && !object.get(key).isJsonPrimitive())
				|| (object.has(key) && !object.get(key).getAsJsonPrimitive().isBoolean())) {
			errors.add("Line " + lineForKey(json, key) + ": " + key + " must be true or false.");
		}
	}

	private static void validateChoice(String json, JsonObject object, String key, List<String> errors, String... choices) {
		if (!object.has(key)) return;
		try {
			String value = object.get(key).getAsString();
			if (!validChoice(value, choices)) errors.add("Line " + lineForKey(json, key) + ": " + key + " must be one of " + String.join(", ", choices) + ".");
		} catch (RuntimeException error) {
			errors.add("Line " + lineForKey(json, key) + ": " + key + " must be text.");
		}
	}

	private static int lineForKey(String json, String key) {
		int index = json.indexOf('"' + key + '"');
		if (index < 0) return 1;
		int line = 1;
		for (int i = 0; i < index; i++) if (json.charAt(i) == '\n') line++;
		return line;
	}

	private static boolean validChoice(String value, String... choices) {
		if (value == null) return false;
		for (String choice : choices) if (choice.equals(value)) return true;
		return false;
	}

	private static String choice(String value, String fallback, String... choices) {
		return validChoice(value, choices) ? value : fallback;
	}

	private static String permissionPolicy(String json, String field, String fallback) {
		return choice(readString(json, field, fallback), fallback, "Ask every time", "Allow trusted scripts", "Always block");
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
