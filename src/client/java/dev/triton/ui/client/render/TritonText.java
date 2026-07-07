package dev.triton.ui.client.render;

import dev.triton.ui.TritonUI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

public final class TritonText {
	public static final FontDescription.Resource SMOOTH = new FontDescription.Resource(TritonUI.id("smooth"));

	private TritonText() {
	}

	public static Component smooth(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(SMOOTH));
	}
}
