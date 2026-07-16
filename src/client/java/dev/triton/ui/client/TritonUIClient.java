package dev.triton.ui.client;

import dev.triton.ui.TritonUI;
import dev.triton.ui.client.app.FluxusAppState;
import dev.triton.ui.client.config.FluxusConfig;
import dev.triton.ui.client.module.ModuleManager;
import dev.triton.ui.client.modern.TritonModernFragment;
import icyllis.modernui.mc.MuiModApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import java.util.Map;

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
	private static String remoteActiveScript = "";
	private static ModuleManager moduleManager;

	@Override
	public void onInitializeClient() {
		config = FluxusConfig.load();
		appState = FluxusAppState.get();
		appState.initialize();
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
	}

	private static void applySavedKeybindings() {
		if (openDemo != null) {
			openDemo.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.openMenuKey()));
		}
		if (editOverlay != null) {
			editOverlay.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.overlayEditKey()));
		}
		if (runLastScript != null) {
			runLastScript.setKey(InputConstants.Type.KEYSYM.getOrCreate(config.runLastScriptKey()));
		}
	}

	private static void updateRemoteTelemetry(Minecraft client) {
		Map<String, Object> telemetry = new HashMap<>();
		telemetry.put("fps", client.getFps());
		telemetry.put("rendererActive", ShulkrHudOverlay.rendererActive());
		telemetry.put("overlays", ShulkrHudOverlay.visibleNames());
		telemetry.put("activeScript", remoteActiveScript);
		moduleManager.appendTelemetry(telemetry);
		if (client.player != null && client.level != null) {
			telemetry.put("position", String.format("%.1f, %.1f, %.1f", client.player.getX(), client.player.getY(), client.player.getZ()));
			telemetry.put("world", client.level.dimension().toString());
			telemetry.put("server", client.getCurrentServer() == null ? "Singleplayer" : client.getCurrentServer().ip);
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
				String commandName = path.replace('\\', '/');
				int dot = commandName.lastIndexOf('.');
				if (dot > 0) commandName = commandName.substring(0, dot);
				Minescript.runEditorCommandAsync(commandName, handled -> {
					if (handled) rememberLastScript(path);
					appState.acknowledgeRemoteCommand(command, handled, handled ? "Started " + path : "Failed to start " + path);
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

	public static FluxusAppState appState() {
		return appState;
	}

	public static int shortcutKey(String action) {
		return switch (String.valueOf(action)) {
			case "open-ui" -> config.openMenuKey();
			case "overlay-edit" -> config.overlayEditKey();
			case "run-last-script" -> config.runLastScriptKey();
			default -> InputConstants.UNKNOWN.getValue();
		};
	}

	public static void setShortcutKey(String action, int key) {
		switch (String.valueOf(action)) {
			case "open-ui" -> config.setOpenMenuKey(key);
			case "overlay-edit" -> config.setOverlayEditKey(key);
			case "run-last-script" -> config.setRunLastScriptKey(key);
			default -> {
				return;
			}
		}
		config.save();
		applySavedKeybindings();
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
		Minescript.runEditorCommandAsync(commandName, handled -> {
			if (handled) {
				rememberLastScript(scriptLabel);
			}
			notifyClient(client, handled ? "Ran last script: " + scriptLabel : "Failed to run last script: " + scriptLabel);
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

	public static boolean handleGlobalKey(int key, int action, int modifiers) {
		Minecraft client = Minecraft.getInstance();
		if (moduleManager != null && moduleManager.handleKeyPress(client, key, action)) {
			return true;
		}
		return activeModernFragment != null && activeModernFragment.handleGlobalKey(key, action, modifiers);
	}
}
