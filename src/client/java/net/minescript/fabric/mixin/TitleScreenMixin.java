package net.minescript.fabric.mixin;

import dev.triton.ui.client.screen.ShulkrTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void shulkr$replaceMainMenu(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && !(client.screen instanceof ShulkrTitleScreen)) {
			client.setScreen(new ShulkrTitleScreen());
		}
	}
}
