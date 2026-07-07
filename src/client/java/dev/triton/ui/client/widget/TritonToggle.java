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

public final class TritonToggle extends TritonWidget {
	private final Component label;
	private boolean value;
	private float position;

	public TritonToggle(Component label, boolean value) {
		this.label = label;
		this.value = value;
		this.position = value ? 1.0F : 0.0F;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 26;
	}

	@Override
	public void tick() {
		position = Ease.approach(position, value ? 1.0F : 0.0F, 0.23F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		Rect rect = bounds();
		boolean hovered = hovered(mouseX, mouseY);
		graphics.text(font, label, rect.x(), rect.y() + 8, hovered ? theme.text() : theme.mutedText(), false);
		Rect track = new Rect(rect.right() - 48, rect.y() + 4, 48, 18);
		TritonDraw.field(graphics, track, theme, value, hovered);
		int knobX = track.x() + 3 + Math.round(position * 27.0F);
		int knobColor = value ? theme.accent() : Color.rgba(160, 168, 180, 255);
		graphics.fill(knobX, track.y() + 3, knobX + 12, track.y() + 15, knobColor);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered(event.x(), event.y())) {
			value = !value;
			return true;
		}
		return false;
	}
}
