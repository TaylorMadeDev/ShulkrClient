package dev.triton.ui.client.widget;

import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.theme.TritonTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public abstract class TritonWidget {
	private Rect bounds = new Rect(0, 0, 0, 0);
	private boolean focused;

	public abstract int preferredHeight(Font font, int availableWidth);

	public abstract void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta);

	public boolean fillsScreen() {
		return false;
	}

	public void tick() {
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		return false;
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		return false;
	}

	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	public Rect bounds() {
		return bounds;
	}

	public void bounds(Rect bounds) {
		this.bounds = bounds;
	}

	public boolean hovered(double mouseX, double mouseY) {
		return bounds.contains(mouseX, mouseY);
	}

	public boolean focused() {
		return focused;
	}

	public void focused(boolean focused) {
		this.focused = focused;
	}
}
