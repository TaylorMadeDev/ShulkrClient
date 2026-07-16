package dev.triton.ui.client;

import dev.triton.ui.TritonUI;
import dev.triton.ui.client.app.FluxusAppState;
import dev.triton.ui.client.config.FluxusConfig;
import dev.triton.ui.client.module.ModuleManager;
import dev.triton.ui.client.modern.TritonModernFragment;
import dev.triton.ui.client.privacy.PrivacyService;
import dev.triton.ui.client.privacy.PrivacyService.Assessment;
import dev.triton.ui.client.privacy.PrivacyService.Decision;
import dev.triton.ui.client.privacy.PrivacyService.Permission;
import dev.triton.ui.client.script.ScriptSettingsRuntime;
import dev.triton.ui.script.ShortcutBinding;
import icyllis.modernui.mc.MuiModApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minescript.fabric.fluxus.ShulkrHudOverlay;
import net.minescript.common.Minescript;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class TritonUIClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("shulkr");
	private static final Category CATEGORY = Category.register(TritonUI.id("controls"));
	private static KeyMapping openDemo;
	private static KeyMapping editOverlay;
	private static KeyMapping runLastScript;
	private static FluxusConfig config;
	private static FluxusAppState appState;
	private static TritonModernFragment activeModernFragment;
	private static int telemetryTicks;
	private static boolean modernUiSearchConflictDisabled;
	private static String remoteActiveScript = "";
	private static ModuleManager moduleManager;
	private static boolean wasInWorld;
	private static boolean wasWindowActive = true;
	private static boolean suspendedForWindowFocus;
	private static boolean suspendedForMenu;
	private static String lastServerIdentity = "";
	private static boolean movementAutomationActive;
	private static PrivacyService privacyService;
	private static final ScheduledExecutorService SAFETY_TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "shulkr-runtime-safety");
		thread.setDaemon(true);
		return thread;
	});
	private static final Set<String> runningShortcutScripts = new HashSet<>();

	@Override
	public void onInitializeClient() {
		config = FluxusConfig.load();
		privacyService = new PrivacyService();
		privacyService.applyConfig(config);
		appState = FluxusAppState.get();
		appState.initialize();
		applyAdvancedRuntimeConfig();
		moduleManager = ModuleManager.get();
		moduleManager.initialize();
		openDemo = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triton-ui.open_demo",
				InputConstants.Type.KEYSYM,
				config.openMenuKey(),
				CATEGORY
		));
		editOverlay = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triton-ui.edit_overlay",
				InputConstants.Type.KEYSYM,
				config.overlayEditKey(),
				CATEGORY
		));
		runLastScript = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triton-ui.run_last_script",
				InputConstants.Type.KEYSYM,
				config.runLastScriptKey(),
				CATEGORY
		));
		applySavedKeybindings();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			disableModernUiSearchConflict();
			boolean inWorld = client.level != null && client.player != null;
			if (wasInWorld && !inWorld && config.stopScriptsOnWorldLeave()) {
				emergencyStop(client, "Stopped scripts after leaving the world.");
			}
			wasInWorld = inWorld;
			String serverIdentity = inWorld ? (client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip) : "";
			if (!serverIdentity.isBlank()) {
				if (!lastServerIdentity.isBlank() && !lastServerIdentity.equals(serverIdentity) && config.stopScriptsOnServerChange()) {
					emergencyStop(client, "Stopped scripts because the active server changed.");
				}
				lastServerIdentity = serverIdentity;
			}
			boolean windowActive = client.isWindowActive();
			if (config.pauseBackgroundScriptsWhenUnfocused() && wasWindowActive && !windowActive && Minescript.activeJobCount() > 0) {
				suspendedForWindowFocus = true;
				Minescript.runEditorCommandAsync("suspend", handled -> verboseLog("Suspended Minescript jobs while Minecraft is unfocused: {}", handled));
			} else if (suspendedForWindowFocus && windowActive) {
				suspendedForWindowFocus = false;
				Minescript.runEditorCommandAsync("resume", handled -> verboseLog("Resumed Minescript jobs after Minecraft regained focus: {}", handled));
			}
			wasWindowActive = windowActive;
			boolean menuOpen = client.screen != null && activeModernFragment == null;
			if (config.pauseAutomationWhenMenuOpen() && menuOpen && !suspendedForMenu && Minescript.activeJobCount() > 0) {
				suspendedForMenu = true;
				Minescript.runEditorCommandAsync("suspend", handled -> verboseLog("Paused automation while a menu is open: {}", handled));
			} else if (suspendedForMenu && (!menuOpen || !config.pauseAutomationWhenMenuOpen())) {
				suspendedForMenu = false;
				Minescript.runEditorCommandAsync("resume", handled -> verboseLog("Resumed automation after closing the menu: {}", handled));
			}
			if (Minescript.activeJobCount() == 0) movementAutomationActive = false;
			moduleManager.onClientTick(client);
			if (++telemetryTicks >= 20) {
				telemetryTicks = 0;
				updateRemoteTelemetry(client);
			}
			for (FluxusAppState.RemoteCommand command : appState.drainRemoteCommands()) {
				handleRemoteCommand(client, command);
			}
			while (openDemo.consumeClick()) {
				if (ShulkrHudOverlay.editMode()) {
					ShulkrHudOverlay.setEditMode(false);
					MuiModApi.openScreen(new TritonModernFragment("Overlays"));
				} else {
					MuiModApi.openScreen(new TritonModernFragment());
				}
			}
			while (editOverlay.consumeClick()) {
				boolean wasEditing = ShulkrHudOverlay.editMode();
				ShulkrHudOverlay.setRendererActive(true);
				ShulkrHudOverlay.setEditMode(!wasEditing);
				if (wasEditing) {
					MuiModApi.openScreen(new TritonModernFragment("Overlays"));
				} else {
					client.setScreen(null);
				}
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal(
							ShulkrHudOverlay.editMode()
									? "Shulkr overlay edit mode enabled. Drag widgets around."
									: "Shulkr overlay edit mode disabled."));
				}
			}
			while (runLastScript.consumeClick()) {
				runStoredScript(client);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (config.stopScriptsWhenClientCloses()) Minescript.emergencyStop();
		});
	}

	private static void disableModernUiSearchConflict() {
		if (modernUiSearchConflictDisabled) return;
		KeyMapping modernUiCenter = KeyMapping.get("key.modernui.openCenter");
		if (modernUiCenter == null) return;
		modernUiCenter.setKey(InputConstants.UNKNOWN);
		KeyMapping.resetMapping();
		modernUiSearchConflictDisabled = true;
		LOGGER.info("Disabled the conflicting ModernUI K binding so Shulkr can use Ctrl+K search.");
	}

	private static void applySavedKeybindings() {
		if (openDemo != null) {
			openDemo.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.openMenuShortcut().modifiers() == 0 ? config.openMenuShortcut().key() : InputConstants.UNKNOWN.getValue()));
		}
		if (editOverlay != null) {
			editOverlay.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.overlayEditShortcut().modifiers() == 0 ? config.overlayEditShortcut().key() : InputConstants.UNKNOWN.getValue()));
		}
		if (runLastScript != null) {
			runLastScript.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.runLastScriptShortcut().modifiers() == 0 ? config.runLastScriptShortcut().key() : InputConstants.UNKNOWN.getValue()));
		}
	}

	private static void updateRemoteTelemetry(Minecraft client) {
		if (config.telemetryMode().equals("Off")) {
			appState.updateClientTelemetry(Map.of());
			return;
		}
		Map<String, Object> telemetry = new HashMap<>();
		boolean anonymous = config.telemetryMode().equals("Anonymous diagnostics");
		telemetry.put("fps", client.getFps());
		telemetry.put("rendererActive", ShulkrHudOverlay.rendererActive());
		telemetry.put("overlays", ShulkrHudOverlay.visibleNames());
		telemetry.put("activeScript", remoteActiveScript);
		moduleManager.appendTelemetry(telemetry);
		if (client.player != null && client.level != null) {
			telemetry.put("position", anonymous || config.hideCoordinatesInCaptures() ? "Hidden by privacy settings" : String.format("%.1f, %.1f, %.1f", client.player.getX(), client.player.getY(), client.player.getZ()));
			telemetry.put("world", client.level.dimension().toString());
			telemetry.put("server", anonymous || config.hideServerAddressesInCaptures() ? "Hidden by privacy settings" : client.getCurrentServer() == null ? "Singleplayer" : client.getCurrentServer().ip);
		} else {
			telemetry.put("position", "-");
			telemetry.put("world", "Main menu");
			telemetry.put("server", "Not connected");
		}
		appState.updateClientTelemetry(telemetry);
	}

	private static void handleRemoteCommand(Minecraft client, FluxusAppState.RemoteCommand command) {
		Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
		switch (command.type()) {
			case "run_script" -> {
				String path = String.valueOf(payload.getOrDefault("path", ""));
				Path scriptPath = ScriptSettingsRuntime.scriptDirectory().resolve(path).normalize();
				if (!java.nio.file.Files.isRegularFile(scriptPath) && !path.toLowerCase(java.util.Locale.ROOT).endsWith(".py")) {
					scriptPath = ScriptSettingsRuntime.scriptDirectory().resolve(path + ".py").normalize();
				}
				if (!scriptPath.startsWith(ScriptSettingsRuntime.scriptDirectory())) {
					appState.acknowledgeRemoteCommand(command, false, "Blocked: script path is outside the Minescript directory");
					break;
				}
				if (!java.nio.file.Files.isRegularFile(scriptPath)) {
					appState.acknowledgeRemoteCommand(command, false, "Blocked: script file does not exist");
					break;
				}
				String commandName = path.replace('\\', '/');
				int dot = commandName.lastIndexOf('.');
				if (dot > 0) commandName = commandName.substring(0, dot);
				if (Minescript.activeJobCount() >= config.maximumConcurrentScripts()) {
					appState.acknowledgeRemoteCommand(command, false, "Blocked: maximum concurrent script limit reached");
					break;
				}
				startAuthorizedScript(scriptPath, commandName, false, result -> {
					if (result.started()) rememberLastScript(path);
					appState.acknowledgeRemoteCommand(command, result.started(), result.started() ? "Started " + path : result.message());
				});
			}
			case "stop_scripts" -> Minescript.runEditorCommandAsync("killjob -1", handled -> {
				if (handled) remoteActiveScript = "";
				appState.acknowledgeRemoteCommand(command, handled, handled ? "Stopped all scripts" : "Stop command was not handled");
			});
			case "set_overlay" -> {
				String name = String.valueOf(payload.getOrDefault("name", ""));
				boolean visible = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("visible", true)));
				ShulkrHudOverlay.setWidgetVisible(name, visible);
				appState.acknowledgeRemoteCommand(command, true, (visible ? "Enabled " : "Disabled ") + name);
			}
			case "set_renderer" -> {
				boolean visible = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("visible", true)));
				ShulkrHudOverlay.setRendererActive(visible);
				appState.acknowledgeRemoteCommand(command, true, visible ? "Overlay renderer enabled" : "Overlay renderer hidden");
			}
			case "open_ui" -> {
				MuiModApi.openScreen(new TritonModernFragment());
				appState.acknowledgeRemoteCommand(command, true, "Opened Shulkr client UI");
			}
			case "take_screenshot" -> Minescript.runEditorCommandAsync("screenshot", handled -> {
				appState.acknowledgeRemoteCommand(command, handled, handled ? "Screenshot command sent" : "Screenshot command was not handled");
			});
			case "send_chat" -> {
				String message = String.valueOf(payload.getOrDefault("message", "")).trim();
				if (message.isEmpty() || client.player == null || client.player.connection == null) {
					appState.acknowledgeRemoteCommand(command, false, "Chat message could not be sent");
					return;
				}
				client.player.connection.sendChat(message);
				appState.acknowledgeRemoteCommand(command, true, "Sent chat: " + message);
			}
			default -> appState.acknowledgeRemoteCommand(command, false, "Unknown command " + command.type());
		}
	}

	public static FluxusConfig config() {
		return config;
	}

	public static void reloadConfig() {
		config = FluxusConfig.load();
		if (privacyService != null) privacyService.applyConfig(config);
		applySavedKeybindings();
		applyAdvancedRuntimeConfig();
	}

	private static void applyAdvancedRuntimeConfig() {
		if (config == null) return;
		if (appState != null) appState.applyRuntimeConfig(config);
		if (Minescript.config != null) {
			Minescript.config.setDebugOutptut(config.verboseMinescriptLogging());
			Minescript.enableDebugPyjinnLogging(config.verboseMinescriptLogging());
		}
		if (!config.pauseBackgroundScriptsWhenUnfocused() && suspendedForWindowFocus) {
			suspendedForWindowFocus = false;
			Minescript.runEditorCommandAsync("resume", handled -> verboseLog("Resumed jobs after disabling focus pause: {}", handled));
		}
		verboseLog("Applied advanced runtime configuration (workers={}, concurrent={}, reconnect={}s).",
				config.scriptWorkerLimit(), config.maximumConcurrentScripts(), config.clientBridgeReconnectDelaySeconds());
	}

	public static void verboseLog(String message, Object... arguments) {
		if (config != null && config.verboseClientLogging()) LOGGER.info(message, arguments);
	}

	public static FluxusAppState appState() {
		return appState;
	}

	public static PrivacyService privacyService() { return privacyService; }

	public record ScriptStartResult(boolean started, Decision decision, String message, Assessment assessment) {}

	public static Assessment assessScript(Path script) { return privacyService.assess(script, config); }

	public static void startAuthorizedScript(Path script, String command, boolean userApproved, Consumer<ScriptStartResult> callback) {
		Assessment assessment = privacyService.assess(script, config);
		if (assessment.decision() == Decision.BLOCK || (assessment.decision() == Decision.ASK && !userApproved)) {
			callback.accept(new ScriptStartResult(false, assessment.decision(), assessment.message(), assessment));
			return;
		}
		if (userApproved && assessment.decision() == Decision.ASK) {
			try { privacyService.approve(assessment, config); }
			catch (Exception error) { LOGGER.error("Could not persist script permission approval", error); }
		}
		boolean movement = assessment.requested().contains(Permission.MOVEMENT) || assessment.requested().contains(Permission.WORLD_ACTION);
		if (movement && config.oneMovementAutomationAtATime() && movementAutomationActive && Minescript.activeJobCount() > 0) {
			callback.accept(new ScriptStartResult(false, Decision.BLOCK, "Blocked: another movement automation is already active.", assessment));
			return;
		}
		Minescript.runEditorCommandAsync(command, handled -> {
			privacyService.recordExecution(script, handled, config);
			if (handled) {
				privacyService.recordRecentScript(script, config);
				movementAutomationActive |= movement;
				scheduleSafetyTimeout();
			}
			callback.accept(new ScriptStartResult(handled, handled ? Decision.ALLOW : Decision.BLOCK,
					handled ? "Script started." : "Minescript could not start the script.", assessment));
		});
	}

	private static void scheduleSafetyTimeout() {
		int configured = config.defaultScriptTimeoutSeconds();
		int maximum = config.maximumScriptRuntimeSeconds();
		int seconds = configured <= 0 ? maximum : maximum <= 0 ? configured : Math.min(configured, maximum);
		if (seconds <= 0) return;
		SAFETY_TIMER.schedule(() -> {
			if (Minescript.activeJobCount() > 0) Minecraft.getInstance().execute(() -> emergencyStop(Minecraft.getInstance(), "Emergency stop: script runtime limit reached."));
		}, seconds, TimeUnit.SECONDS);
	}

	public static void emergencyStop(Minecraft client, String message) {
		int stopped = Minescript.emergencyStop();
		movementAutomationActive = false;
		suspendedForMenu = false;
		suspendedForWindowFocus = false;
		if (message != null && !message.isBlank()) notifyClient(client, message + " (" + stopped + " job(s))");
		LOGGER.warn("{} Released all movement, camera, attack, and use inputs; stopped {} job(s).", message, stopped);
	}

	public static int shortcutKey(String action) {
		return shortcutBinding(action).key();
	}

	public static ShortcutBinding shortcutBinding(String action) {
		String id = String.valueOf(action);
		if (id.startsWith("script:")) return config.scriptShortcuts().getOrDefault(id.substring("script:".length()), ShortcutBinding.unbound());
		return switch (id) {
			case "open-ui" -> config.openMenuShortcut();
			case "overlay-edit" -> config.overlayEditShortcut();
			case "run-last-script" -> config.runLastScriptShortcut();
			case "privacy-emergency-stop" -> config.emergencyStopShortcut();
			default -> ShortcutBinding.unbound();
		};
	}

	public static void setShortcutKey(String action, int key) {
		setShortcutBinding(action, key < 0 ? ShortcutBinding.unbound() : new ShortcutBinding(key, 0));
	}

	public static String shortcutConflict(String action, ShortcutBinding candidate) {
		if (candidate == null || !candidate.bound()) return "";
		for (String appAction : new String[]{"open-ui", "overlay-edit", "run-last-script", "privacy-emergency-stop"}) {
			if (!appAction.equals(action) && candidate.equals(shortcutBinding(appAction))) return appAction;
		}
		for (Map.Entry<String, ShortcutBinding> entry : config.scriptShortcuts().entrySet()) {
			String scriptAction = "script:" + entry.getKey();
			if (!scriptAction.equals(action) && candidate.equals(entry.getValue())) return scriptAction;
		}
		return "";
	}

	public static boolean setShortcutBinding(String action, ShortcutBinding binding) {
		if (!shortcutConflict(action, binding).isBlank()) return false;
			switch (String.valueOf(action)) {
			case "open-ui" -> config.setOpenMenuShortcut(binding);
			case "overlay-edit" -> config.setOverlayEditShortcut(binding);
			case "run-last-script" -> config.setRunLastScriptShortcut(binding);
			case "privacy-emergency-stop" -> config.setEmergencyStopShortcut(binding);
			default -> {
				if (!String.valueOf(action).startsWith("script:")) return false;
				Map<String, ShortcutBinding> scripts = config.scriptShortcuts();
				String id = String.valueOf(action).substring("script:".length());
				if (binding == null || !binding.bound()) scripts.remove(id); else scripts.put(id, binding);
				config.setScriptShortcuts(scripts);
			}
		}
		config.save();
		applySavedKeybindings();
		return true;
	}

	public static String lastRunScriptPath() {
		return config.lastScriptPath();
	}

	public static void rememberLastScript(String path) {
		String normalized = path == null ? "" : path.replace('\\', '/').trim();
		remoteActiveScript = normalized;
		config.setLastScriptPath(normalized);
		config.save();
	}

	private static void runStoredScript(Minecraft client) {
		String path = config.lastScriptPath();
		if (path == null || path.isBlank()) {
			notifyClient(client, "No previous script has been run yet.");
			return;
		}
		String commandName = path.replace('\\', '/');
		int dot = commandName.lastIndexOf('.');
		if (dot > 0) {
			commandName = commandName.substring(0, dot);
		}
		String scriptLabel = path;
		Path script = ScriptSettingsRuntime.scriptDirectory().resolve(path).normalize();
		startAuthorizedScript(script, commandName, false, result -> {
			if (result.started()) {
				rememberLastScript(scriptLabel);
			}
			notifyClient(client, result.started() ? "Ran last script: " + scriptLabel : result.message());
		});
	}

	private static void notifyClient(Minecraft client, String message) {
		if (client != null && client.player != null) {
			client.player.sendOverlayMessage(Component.literal(message));
		}
	}

	public static void setActiveModernFragment(TritonModernFragment fragment) {
		activeModernFragment = fragment;
	}

	public static void clearActiveModernFragment(TritonModernFragment fragment) {
		if (activeModernFragment == fragment) activeModernFragment = null;
	}

	public static boolean handleGlobalKey(int key, int action, int modifiers) {
		Minecraft client = Minecraft.getInstance();
		TritonModernFragment fragment = activeModernFragment;
		if (action != 1) return false;
		if (config.emergencyStopShortcut().matches(key, modifiers)) {
			emergencyStop(client, "Emergency stop activated.");
			return true;
		}
		if (fragment != null && fragment.handleGlobalKey(key, action, modifiers)) return true;
		if (key == InputConstants.KEY_K && (modifiers & ShortcutBinding.CTRL) != 0) {
			MuiModApi.openScreen(TritonModernFragment.forGlobalSearch());
			return true;
		}
		if (fragment != null && fragment.shortcutsSuppressed()) return false;
		if (fragment == null && client.screen != null) return false;
		if (config.openMenuShortcut().modifiers() != 0 && config.openMenuShortcut().matches(key, modifiers)) {
			if (fragment != null) client.setScreen(null); else MuiModApi.openScreen(new TritonModernFragment());
			return true;
		}
		if (config.overlayEditShortcut().modifiers() != 0 && config.overlayEditShortcut().matches(key, modifiers)) {
			ShulkrHudOverlay.setRendererActive(true);
			ShulkrHudOverlay.setEditMode(!ShulkrHudOverlay.editMode());
			return true;
		}
		if (config.runLastScriptShortcut().modifiers() != 0 && config.runLastScriptShortcut().matches(key, modifiers)) {
			runStoredScript(client);
			return true;
		}
		for (Map.Entry<String, ShortcutBinding> entry : config.scriptShortcuts().entrySet()) {
			if (entry.getValue().matches(key, modifiers)) {
				runScriptShortcut(client, entry.getKey());
				return true;
			}
		}
		if (moduleManager != null && moduleManager.handleKeyPress(client, key, action)) {
			return true;
		}
		return false;
	}

	private static void runScriptShortcut(Minecraft client, String scriptId) {
		if (Minescript.activeJobCount() >= config.maximumConcurrentScripts()) {
			notifyClient(client, "Script blocked: maximum concurrent script limit reached.");
			return;
		}
		synchronized (runningShortcutScripts) {
			if (!runningShortcutScripts.add(scriptId)) {
				notifyClient(client, "That script shortcut is already starting.");
				return;
			}
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				var script = ScriptSettingsRuntime.resolveScript(scriptId);
				if (script == null) throw new IllegalStateException("The assigned script is missing.");
				return ScriptSettingsRuntime.prepare(script);
			} catch (Exception error) {
				throw new RuntimeException(error);
			}
		}).whenComplete((prepared, error) -> client.execute(() -> {
			if (error != null) {
				runningShortcutScripts.remove(scriptId);
				notifyClient(client, "Script shortcut failed: " + (error.getCause() == null ? error.getMessage() : error.getCause().getMessage()));
				LOGGER.error("Failed to prepare script shortcut {}", scriptId, error);
				return;
			}
			Path authorizedScript;
			try { authorizedScript = ScriptSettingsRuntime.resolveScript(scriptId); }
			catch (Exception resolveError) {
				runningShortcutScripts.remove(scriptId);
				notifyClient(client, "Script shortcut failed: the assigned script could not be resolved.");
				return;
			}
			startAuthorizedScript(authorizedScript, prepared.commandPath(), false, result -> client.execute(() -> {
				runningShortcutScripts.remove(scriptId);
				if (result.started()) rememberLastScript(ScriptSettingsRuntime.scriptDirectory().relativize(authorizedScript).toString());
				notifyClient(client, result.started() ? "Started script shortcut." : result.message());
			}));
		}));
	}
}
