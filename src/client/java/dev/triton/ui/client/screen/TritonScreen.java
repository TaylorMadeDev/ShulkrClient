package dev.triton.ui.client.screen;

import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.widget.TritonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TritonScreen extends Screen {
	private final List<TritonWidget> widgets = new ArrayList<>();
	private final TritonTheme theme;

	protected TritonScreen(Component title, TritonTheme theme) {
		super(title);
		this.theme = theme;
	}

	protected void addTritonWidget(TritonWidget widget) {
		widgets.add(widget);
	}

	@Override
	protected void init() {
		layout();
	}

	@Override
	public void tick() {
		for (TritonWidget widget : widgets) {
			widget.tick();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		TritonDraw.scrim(graphics, theme, width, height);
		layout();
		for (TritonWidget widget : widgets) {
			widget.extractRenderState(graphics, font, theme, mouseX, mouseY, tickDelta);
		}
		super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			if (widgets.get(i).mouseClicked(event)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (TritonWidget widget : widgets) {
			if (widget.mouseReleased(event)) {
				return true;
			}
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		for (TritonWidget widget : widgets) {
			if (widget.mouseDragged(event, dragX, dragY)) {
				return true;
			}
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		for (TritonWidget widget : widgets) {
			if (widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (TritonWidget widget : widgets) {
			if (widget.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (TritonWidget widget : widgets) {
			if (widget.charTyped(event)) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	protected void layout() {
		int panelWidth = Math.min(740, Math.max(320, width - 32));
		int x = (width - panelWidth) / 2;
		int y = Math.max(22, (height - totalHeight(panelWidth)) / 2);
		for (TritonWidget widget : widgets) {
			if (widget.fillsScreen()) {
				widget.bounds(new Rect(0, 0, width, height));
				continue;
			}
			int preferredHeight = widget.preferredHeight(font, panelWidth);
			widget.bounds(new Rect(x, y, panelWidth, preferredHeight));
			y += preferredHeight + 14;
		}
	}

	private int totalHeight(int panelWidth) {
		int total = 0;
		for (TritonWidget widget : widgets) {
			total += widget.preferredHeight(font, panelWidth) + 14;
		}
		return Math.max(0, total - 14);
	}
}
