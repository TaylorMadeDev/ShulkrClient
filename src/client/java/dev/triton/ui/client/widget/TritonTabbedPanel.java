package dev.triton.ui.client.widget;

import dev.triton.ui.client.animation.Ease;
import dev.triton.ui.client.layout.Insets;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.util.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

public final class TritonTabbedPanel extends TritonWidget {
	private final Component title;
	private final Component subtitle;
	private final List<Tab> tabs = new ArrayList<>();
	private final Insets padding = Insets.all(16);
	private int activeTab;
	private int scroll;
	private int maxScroll;
	private Rect contentClip = new Rect(0, 0, 0, 0);
	private float accentPulse;

	public TritonTabbedPanel(Component title, Component subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	public TritonTabbedPanel tab(Component label, Component description, List<TritonWidget> widgets) {
		tabs.add(new Tab(label, description, widgets));
		return this;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 402;
	}

	@Override
	public void tick() {
		accentPulse = Ease.approach(accentPulse, 1.0F, 0.03F);
		if (accentPulse > 0.99F) {
			accentPulse = 0.0F;
		}
		for (Tab tab : tabs) {
			for (TritonWidget widget : tab.widgets()) {
				widget.tick();
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		TritonDraw.panel(graphics, bounds(), theme);
		Rect inner = bounds().inset(padding);
		int sidebarWidth = Math.min(142, Math.max(118, inner.width() / 4));
		Rect sidebar = new Rect(inner.x(), inner.y(), sidebarWidth, inner.height());
		Rect content = new Rect(sidebar.right() + 16, inner.y(), inner.right() - sidebar.right() - 16, inner.height());

		graphics.text(font, title, sidebar.x(), sidebar.y(), theme.text(), false);
		graphics.text(font, subtitle, sidebar.x(), sidebar.y() + font.lineHeight + 4, theme.mutedText(), false);
		renderTabs(graphics, font, theme, mouseX, mouseY, sidebar.y() + 42, sidebarWidth);

		Tab tab = activeTab();
		graphics.text(font, tab.label(), content.x(), content.y(), theme.text(), false);
		graphics.textWithWordWrap(font, tab.description(), content.x(), content.y() + font.lineHeight + 5, content.width(), theme.mutedText());

		contentClip = new Rect(content.x(), content.y() + 44, content.width() - 8, content.height() - 52);
		layoutActive(font, contentClip);
		graphics.enableScissor(contentClip.x(), contentClip.y(), contentClip.right(), contentClip.bottom());
		for (TritonWidget widget : tab.widgets()) {
			widget.extractRenderState(graphics, font, theme, mouseX, mouseY, tickDelta);
		}
		graphics.disableScissor();
		renderScrollbar(graphics, theme, new Rect(content.right() - 4, contentClip.y(), 4, contentClip.height()));
		TritonDraw.glowBar(graphics, bounds(), theme.accent(), theme.accentHot(), Ease.inOutQuart(accentPulse));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		Rect inner = bounds().inset(padding);
		int sidebarWidth = Math.min(142, Math.max(118, inner.width() / 4));
		int tabY = inner.y() + 42;
		for (int i = 0; i < tabs.size(); i++) {
			Rect tabRect = new Rect(inner.x(), tabY + i * 32, sidebarWidth, 26);
			if (tabRect.contains(event.x(), event.y())) {
				activeTab = i;
				scroll = 0;
				return true;
			}
		}
		if (!contentClip.contains(event.x(), event.y())) {
			return false;
		}
		List<TritonWidget> widgets = activeTab().widgets();
		for (int i = widgets.size() - 1; i >= 0; i--) {
			if (widgets.get(i).mouseClicked(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (TritonWidget widget : activeTab().widgets()) {
			if (widget.mouseReleased(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		for (TritonWidget widget : activeTab().widgets()) {
			if (widget.mouseDragged(event, dragX, dragY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!contentClip.contains(mouseX, mouseY) || maxScroll <= 0) {
			return false;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 22.0)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (TritonWidget widget : activeTab().widgets()) {
			if (widget.keyPressed(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (TritonWidget widget : activeTab().widgets()) {
			if (widget.charTyped(event)) {
				return true;
			}
		}
		return false;
	}

	private void renderTabs(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, int y, int width) {
		for (int i = 0; i < tabs.size(); i++) {
			Rect tabRect = new Rect(bounds().x() + padding.left(), y + i * 32, width, 26);
			boolean active = i == activeTab;
			boolean hovered = tabRect.contains(mouseX, mouseY);
			int top = active ? Color.mix(theme.panelTop(), theme.accent(), 0.34F) : Color.alpha(theme.panelTop(), hovered ? 220 : 150);
			int bottom = active ? Color.mix(theme.panelBottom(), theme.accentHot(), 0.18F) : Color.alpha(theme.panelBottom(), hovered ? 230 : 165);
			graphics.fillGradient(tabRect.x(), tabRect.y(), tabRect.right(), tabRect.bottom(), top, bottom);
			graphics.outline(tabRect.x(), tabRect.y(), tabRect.width(), tabRect.height(), active ? theme.accent() : Color.alpha(theme.panelStroke(), hovered ? 150 : 80));
			graphics.text(font, tabs.get(i).label(), tabRect.x() + 9, tabRect.y() + 9, active || hovered ? theme.text() : theme.mutedText(), false);
			if (active) {
				graphics.fill(tabRect.x(), tabRect.y(), tabRect.x() + 3, tabRect.bottom(), theme.accentHot());
			}
		}
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics, TritonTheme theme, Rect rail) {
		graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), Color.rgba(6, 20, 45, 150));
		if (maxScroll <= 0) {
			graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), Color.rgba(82, 156, 255, 80));
			return;
		}
		int knobHeight = Math.max(26, rail.height() * rail.height() / (rail.height() + maxScroll));
		int knobY = rail.y() + Math.round((rail.height() - knobHeight) * (scroll / (float) maxScroll));
		graphics.fillGradient(rail.x() - 1, knobY, rail.right() + 1, knobY + knobHeight, theme.accent(), theme.accentHot());
	}

	private void layoutActive(Font font, Rect clip) {
		int y = clip.y() - scroll;
		for (TritonWidget widget : activeTab().widgets()) {
			int height = widget.preferredHeight(font, clip.width() - 12);
			widget.bounds(new Rect(clip.x(), y, clip.width() - 12, height));
			y += height + 10;
		}
		int contentHeight = y + scroll - clip.y() - 10;
		maxScroll = Math.max(0, contentHeight - clip.height());
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	private Tab activeTab() {
		if (tabs.isEmpty()) {
			return new Tab(Component.literal("Empty"), Component.literal("No widgets registered."), List.of());
		}
		activeTab = Math.max(0, Math.min(activeTab, tabs.size() - 1));
		return tabs.get(activeTab);
	}

	private record Tab(Component label, Component description, List<TritonWidget> widgets) {
	}
}
