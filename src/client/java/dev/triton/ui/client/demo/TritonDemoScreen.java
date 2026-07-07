package dev.triton.ui.client.demo;

import dev.triton.ui.client.screen.TritonScreen;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.widget.TritonDashboard;
import net.minecraft.network.chat.Component;

public final class TritonDemoScreen extends TritonScreen {
	public TritonDemoScreen() {
		super(Component.literal("Triton UI"), TritonTheme.abyss());
		addTritonWidget(new TritonDashboard());
	}
}
