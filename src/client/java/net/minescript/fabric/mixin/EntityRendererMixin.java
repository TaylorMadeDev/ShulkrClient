package net.minescript.fabric.mixin;

import dev.triton.ui.client.TritonUIClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void shulkr$hidePlayerNamesInCaptures(Entity entity, double distance, CallbackInfoReturnable<Boolean> result) {
		if (entity instanceof Player && TritonUIClient.config() != null && TritonUIClient.config().hidePlayerNamesInCaptures()) {
			result.setReturnValue(false);
		}
	}
}
