package dev.triton.ui.client.widget;

import dev.triton.ui.client.animation.Ease;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.util.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public final class TritonSlider extends TritonWidget {
	private final Component label;
	private final float min;
	private final float max;
	private float value;
	private float displayValue;
	private boolean dragging;

	public TritonSlider(Component label, float min, float max, float value) {
		this.label = label;
		this.min = min;
		this.max = max;
		this.value = clamp(value);
		this.displayValue = this.value;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 34;
	}

	@Override
	public void tick() {
		displayValue = Ease.approach(displayValue, value, 0.22F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		Rect rect = bounds();
		float normalized = (displayValue - min) / (max - min);
		graphics.text(font, label, rect.x(), rect.y(), theme.text(), false);
		graphics.text(font, String.format("%.0f", value), rect.right() - 24, rect.y(), theme.mutedText(), false);
		Rect track = new Rect(rect.x(), rect.y() + 18, rect.width(), 8);
		TritonDraw.field(graphics, track, theme, dragging, hovered(mouseX, mouseY));
		int fillRight = track.x() + Math.round(track.width() * normalized);
		graphics.fillGradient(track.x(), track.y(), fillRight, track.bottom(), theme.accent(), theme.accentHot());
		graphics.fill(fillRight - 2, track.y() - 3, fillRight + 2, track.bottom() + 3, Color.rgba(244, 248, 255, 255));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered(event.x(), event.y())) {
			dragging = true;
			setFromMouse(event.x());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			setFromMouse(event.x());
			return true;
		}
		return false;
	}

	private void setFromMouse(double mouseX) {
		float normalized = (float) ((mouseX - bounds().x()) / Math.max(1.0, bounds().width()));
		value = min + (max - min) * Math.max(0.0F, Math.min(1.0F, normalized));
	}

	private float clamp(float candidate) {
		return Math.max(min, Math.min(max, candidate));
	}
}
