package dev.triton.ui.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.Map;

public final class XrayModule extends ClientModule {
	public XrayModule() {
		super("xray", "Xray", "Render pipeline slot reserved for the future Xray implementation.", ModuleCategory.RENDER, InputConstants.KEY_X);
	}

	@Override
	public void appendTelemetry(Map<String, Object> telemetry) {
		telemetry.put("xrayPrototype", enabled());
	}

	@Override
	protected void onEnable(Minecraft client) {
		if (client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}

	@Override
	protected void onDisable(Minecraft client) {
		if (client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}
}
