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

public final class TritonButton extends TritonWidget {
	private final Component label;
	private final Runnable action;
	private float hover;

	public TritonButton(Component label, Runnable action) {
		this.label = label;
		this.action = action;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 28;
	}

	@Override
	public void tick() {
		hover = Ease.approach(hover, 0.0F, 0.18F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		boolean hovered = hovered(mouseX, mouseY);
		hover = Ease.approach(hover, hovered ? 1.0F : 0.0F, 0.28F);
		Rect rect = bounds();
		int top = Color.mix(theme.panelTop(), theme.accent(), 0.20F + hover * 0.25F);
		int bottom = Color.mix(theme.panelBottom(), theme.accentHot(), hover * 0.22F);
		TritonDraw.shadow(graphics, rect, Color.rgba(0, 0, 0, 82));
		graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(), top, bottom);
		graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? theme.accent() : theme.panelStroke());
		graphics.centeredText(font, label, rect.centerX(), rect.y() + (rect.height() - font.lineHeight) / 2, theme.text());
		if (hovered) {
			TritonDraw.glowBar(graphics, rect, theme.accent(), theme.accentHot(), hover);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered(event.x(), event.y())) {
			action.run();
			return true;
		}
		return false;
	}
}
