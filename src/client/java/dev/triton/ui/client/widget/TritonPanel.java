package dev.triton.ui.client.widget;

import dev.triton.ui.client.layout.Insets;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.theme.TritonTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TritonPanel extends TritonWidget {
	private final Component title;
	private final Component subtitle;
	private final List<TritonWidget> children = new ArrayList<>();
	private final Insets padding = Insets.all(16);
	private int gap = 10;

	public TritonPanel(Component title, Component subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	public TritonPanel add(TritonWidget widget) {
		children.add(widget);
		return this;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		int height = padding.vertical() + font.lineHeight * 2 + 20;
		for (TritonWidget child : children) {
			height += child.preferredHeight(font, availableWidth - padding.horizontal()) + gap;
		}
		return height - gap;
	}

	public void layout(Font font) {
		Rect content = bounds().inset(padding);
		int y = content.y() + font.lineHeight * 2 + 20;
		for (TritonWidget child : children) {
			int height = child.preferredHeight(font, content.width());
			child.bounds(new Rect(content.x(), y, content.width(), height));
			y += height + gap;
		}
	}

	@Override
	public void tick() {
		for (TritonWidget child : children) {
			child.tick();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		layout(font);
		TritonDraw.panel(graphics, bounds(), theme);
		Rect titleRect = bounds().inset(padding);
		TritonDraw.title(graphics, font, title, subtitle, titleRect, theme);
		TritonDraw.glowBar(graphics, bounds(), theme.accent(), theme.accentHot(), (float) ((Math.sin(System.nanoTime() / 450_000_000.0) + 1.0) * 0.5));
		for (TritonWidget child : children) {
			child.extractRenderState(graphics, font, theme, mouseX, mouseY, tickDelta);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		for (int i = children.size() - 1; i >= 0; i--) {
			if (children.get(i).mouseClicked(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (TritonWidget child : children) {
			if (child.mouseReleased(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		for (TritonWidget child : children) {
			if (child.mouseDragged(event, dragX, dragY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		for (TritonWidget child : children) {
			if (child.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (TritonWidget child : children) {
			if (child.keyPressed(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (TritonWidget child : children) {
			if (child.charTyped(event)) {
				return true;
			}
		}
		return false;
	}
}
