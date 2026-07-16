package dev.triton.ui.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minescript.fabric.fluxus.ShulkrHudOverlay;

public final class OverlaySyncModule extends ClientModule {
	public OverlaySyncModule() {
		super("overlay_renderer", "Overlay Renderer", "Keeps the Shulkr HUD renderer active while enabled.", ModuleCategory.MISC, InputConstants.KEY_H);
	}

	@Override
	protected void onEnable(Minecraft client) {
		ShulkrHudOverlay.setRendererActive(true);
	}

	@Override
	protected void onDisable(Minecraft client) {
		ShulkrHudOverlay.setRendererActive(false);
	}
}
