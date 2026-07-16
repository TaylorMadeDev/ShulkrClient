package dev.triton.ui.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MODULE_STATE_LIST = new TypeToken<List<StoredModuleState>>() {}.getType();
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final Map<String, ClientModule> modules = new LinkedHashMap<>();
	private final Path statePath = Path.of(System.getProperty("user.dir"), "config", "shulkr-runtime-modules.json");
	private boolean initialized;

	public static ModuleManager get() {
		return INSTANCE;
	}

	private ModuleManager() {}

	public synchronized void initialize() {
		if (initialized) {
			return;
		}
		registerDefaults();
		loadState();
		initialized = true;
	}

	private void registerDefaults() {
		register(new FullbrightModule());
		register(new AutoSprintModule());
		register(new OverlaySyncModule());
		register(new XrayModule());
	}

	public synchronized void register(ClientModule module) {
		modules.put(module.id(), module);
	}

	public synchronized List<ClientModule> modules() {
		return List.copyOf(modules.values());
	}

	public synchronized ClientModule module(String id) {
		return modules.get(id);
	}

	public synchronized List<String> enabledNames() {
		return modules.values().stream().filter(ClientModule::enabled).map(ClientModule::name).toList();
	}

	public synchronized boolean setModuleEnabled(String id, boolean enabled) {
		ClientModule module = modules.get(id);
		if (module == null) {
			return false;
		}
		module.setEnabled(Minecraft.getInstance(), enabled);
		saveState();
		return true;
	}

	public synchronized void onClientTick(Minecraft client) {
		for (ClientModule module : modules.values()) {
			if (module.enabled()) {
				module.onTick(client);
			}
		}
	}

	public synchronized boolean handleKeyPress(Minecraft client, int key, int action) {
		if (action != 1) {
			return false;
		}
		for (ClientModule module : modules.values()) {
			if (module.keybind() == key && key != InputConstants.UNKNOWN.getValue()) {
				module.toggle(client);
				saveState();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal(module.name() + (module.enabled() ? " enabled" : " disabled")));
				}
				return true;
			}
		}
		return false;
	}

	public synchronized void appendTelemetry(Map<String, Object> telemetry) {
		telemetry.put("modules", summaries());
		telemetry.put("enabledModules", enabledNames());
	}

	public synchronized List<RuntimeModuleSummary> summaries() {
		List<RuntimeModuleSummary> result = new ArrayList<>();
		for (ClientModule module : modules.values()) {
			result.add(new RuntimeModuleSummary(
					module.id(),
					module.name(),
					module.category().name().toLowerCase(Locale.ROOT),
					module.description(),
					module.enabled(),
					module.keybind(),
					module.settings().stream().map(setting -> new RuntimeModuleSettingSummary(
							setting.id(),
							setting.label(),
							setting.description(),
							String.valueOf(setting.value()))).toList()
			));
		}
		return result;
	}

	private void loadState() {
		try {
			if (Files.notExists(statePath)) {
				saveState();
				return;
			}
			List<StoredModuleState> states = GSON.fromJson(Files.readString(statePath, StandardCharsets.UTF_8), MODULE_STATE_LIST);
			if (states == null) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			for (StoredModuleState state : states) {
				ClientModule module = modules.get(state.id());
				if (module == null) {
					continue;
				}
				module.setKeybind(state.keybind());
				module.setEnabled(client, state.enabled());
			}
		} catch (IOException ignored) {
		}
	}

	public synchronized void saveState() {
		try {
			Files.createDirectories(statePath.getParent());
			List<StoredModuleState> states = modules.values().stream()
					.map(module -> new StoredModuleState(module.id(), module.enabled(), module.keybind()))
					.toList();
			Files.writeString(statePath, GSON.toJson(states) + "\n", StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private record StoredModuleState(String id, boolean enabled, int keybind) {}

	public record RuntimeModuleSettingSummary(String id, String label, String description, String value) {}

	public record RuntimeModuleSummary(String id, String name, String category, String description,
									 boolean enabled, int keybind, List<RuntimeModuleSettingSummary> settings) {}
}
