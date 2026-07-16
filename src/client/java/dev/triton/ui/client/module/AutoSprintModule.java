package dev.triton.ui.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class AutoSprintModule extends ClientModule {
	private final BooleanModuleSetting requireForward = addSetting(
			new BooleanModuleSetting("require_forward", "Require forward input", "Only sprint while the forward key is held.", true)
	);

	public AutoSprintModule() {
		super("auto_sprint", "Auto Sprint", "Automatically sprints while you move.", ModuleCategory.MOVEMENT, InputConstants.KEY_G);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.options == null) {
			return;
		}
		boolean movingForward = client.options.keyUp.isDown();
		boolean movingSideways = client.options.keyLeft.isDown() || client.options.keyRight.isDown();
		boolean moving = requireForward.enabled() ? movingForward : (movingForward || movingSideways);
		if (moving && !client.player.isShiftKeyDown() && !client.player.isUsingItem()) {
			client.player.setSprinting(true);
		}
	}
}
