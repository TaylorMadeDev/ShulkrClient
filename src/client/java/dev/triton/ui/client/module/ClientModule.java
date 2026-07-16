package dev.triton.ui.client.module;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ClientModule {
	private final String id;
	private final String name;
	private final String description;
	private final ModuleCategory category;
	private final int defaultKeybind;
	private int keybind;
	private boolean enabled;
	private final List<ModuleSetting<?>> settings = new ArrayList<>();

	protected ClientModule(String id, String name, String description, ModuleCategory category, int defaultKeybind) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.defaultKeybind = defaultKeybind;
		this.keybind = defaultKeybind;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final ModuleCategory category() {
		return category;
	}

	public final int defaultKeybind() {
		return defaultKeybind;
	}

	public final int keybind() {
		return keybind;
	}

	public final void setKeybind(int keybind) {
		this.keybind = keybind;
	}

	public final boolean enabled() {
		return enabled;
	}

	public final void setEnabled(Minecraft client, boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable(client);
		} else {
			onDisable(client);
		}
	}

	public final void toggle(Minecraft client) {
		setEnabled(client, !enabled);
	}

	protected final <T extends ModuleSetting<?>> T addSetting(T setting) {
		settings.add(setting);
		return setting;
	}

	public final List<ModuleSetting<?>> settings() {
		return List.copyOf(settings);
	}

	protected void onEnable(Minecraft client) {}

	protected void onDisable(Minecraft client) {}

	public void onTick(Minecraft client) {}

	public void appendTelemetry(Map<String, Object> telemetry) {}
}
