package dev.triton.ui.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class FullbrightModule extends ClientModule {
	private double previousGamma = 1.0D;

	public FullbrightModule() {
		super("fullbright", "Fullbright", "Forces max brightness while enabled.", ModuleCategory.RENDER, InputConstants.KEY_F6);
	}

	@Override
	protected void onEnable(Minecraft client) {
		previousGamma = client.options.gamma().get();
		client.options.gamma().set(16.0D);
	}

	@Override
	protected void onDisable(Minecraft client) {
		client.options.gamma().set(previousGamma);
	}
}
