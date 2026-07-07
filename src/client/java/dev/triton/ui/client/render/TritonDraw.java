package dev.triton.ui.client.render;

import dev.triton.ui.TritonUI;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.util.Color;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TritonDraw {
	public static final Identifier PANEL = TritonUI.id("textures/ui/panel.png");
	public static final Identifier PANEL_SOFT = TritonUI.id("textures/ui/panel_soft.png");
	public static final Identifier CARD = TritonUI.id("textures/ui/card.png");
	public static final Identifier CARD_HOT = TritonUI.id("textures/ui/card_hot.png");
	public static final Identifier SEARCH = TritonUI.id("textures/ui/search.png");
	public static final Identifier PILL = TritonUI.id("textures/ui/pill.png");
	public static final Identifier PILL_ACTIVE = TritonUI.id("textures/ui/pill_active.png");
	public static final Identifier NAV_ACTIVE = TritonUI.id("textures/ui/nav_active.png");
	public static final Identifier DOCK = TritonUI.id("textures/ui/dock.png");
	public static final Identifier TOGGLE_ON = TritonUI.id("textures/ui/toggle_on.png");
	public static final Identifier TOGGLE_OFF = TritonUI.id("textures/ui/toggle_off.png");
	private static final int SKIN_SIZE = 64;

	private TritonDraw() {
	}

	public static void scrim(GuiGraphicsExtractor graphics, TritonTheme theme, int width, int height) {
		graphics.fillGradient(0, 0, width, height, theme.backdropTop(), theme.backdropBottom());
		graphics.fillGradient(0, 0, width, Math.max(1, height / 2), Color.rgba(49, 153, 255, 44), Color.rgba(81, 235, 255, 8));
		for (int x = 0; x < width; x += 24) {
			graphics.fill(x, 0, x + 1, height, Color.rgba(110, 183, 255, 11));
		}
		for (int y = 0; y < height; y += 24) {
			graphics.fill(0, y, width, y + 1, Color.rgba(110, 183, 255, 9));
		}
	}

	public static void panel(GuiGraphicsExtractor graphics, Rect rect, TritonTheme theme) {
		shadow(graphics, rect, Color.rgba(0, 0, 0, 80));
		graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(), theme.panelTop(), theme.panelBottom());
		graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), theme.panelStroke());
		graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, Color.rgba(255, 255, 255, 36));
		graphics.fillGradient(rect.x() + 1, rect.y() + 2, rect.x() + 4, rect.bottom() - 2, Color.rgba(72, 190, 255, 70), Color.rgba(78, 229, 255, 8));
	}

	public static void field(GuiGraphicsExtractor graphics, Rect rect, TritonTheme theme, boolean active, boolean hovered) {
		int top = active ? Color.mix(theme.panelTop(), theme.accent(), 0.18F) : theme.panelTop();
		int bottom = hovered ? Color.mix(theme.panelBottom(), theme.accentHot(), 0.13F) : theme.panelBottom();
		roundedBorderedGradient(graphics, rect, 5, top, bottom, active ? theme.accent() : theme.panelStroke(), 1);
	}

	public static void glowBar(GuiGraphicsExtractor graphics, Rect rect, int leftColor, int rightColor, float pulse) {
		int y = rect.y() + rect.height() - 2;
		graphics.fillGradient(rect.x(), y, rect.right(), y + 2, leftColor, rightColor);
		int alpha = 28 + (int) (30.0F * pulse);
		graphics.fillGradient(rect.x(), y - 3, rect.right(), y, Color.alpha(leftColor, alpha), Color.alpha(rightColor, alpha));
	}

	public static void title(GuiGraphicsExtractor graphics, Font font, Component title, Component subtitle, Rect rect, TritonTheme theme) {
		graphics.text(font, title, rect.x(), rect.y(), theme.text(), false);
		graphics.text(font, subtitle, rect.x(), rect.y() + font.lineHeight + 4, theme.mutedText(), false);
	}

	public static void shadow(GuiGraphicsExtractor graphics, Rect rect, int color) {
		roundedRect(graphics, new Rect(rect.x() + 4, rect.y() + 5, rect.width(), rect.height()), 8, Color.alpha(color, 26));
		roundedRect(graphics, new Rect(rect.x() + 2, rect.y() + 3, rect.width(), rect.height()), 8, Color.alpha(color, 44));
	}

	public static void skin(GuiGraphicsExtractor graphics, Identifier texture, Rect rect, int slice) {
		int corner = Math.min(slice, Math.min(rect.width(), rect.height()) / 2);
		int centerWidth = Math.max(0, rect.width() - corner * 2);
		int centerHeight = Math.max(0, rect.height() - corner * 2);
		int sourceCenter = SKIN_SIZE - corner * 2;

		blit(graphics, texture, rect.x(), rect.y(), corner, corner, 0, 0, corner, corner);
		blit(graphics, texture, rect.right() - corner, rect.y(), corner, corner, SKIN_SIZE - corner, 0, corner, corner);
		blit(graphics, texture, rect.x(), rect.bottom() - corner, corner, corner, 0, SKIN_SIZE - corner, corner, corner);
		blit(graphics, texture, rect.right() - corner, rect.bottom() - corner, corner, corner, SKIN_SIZE - corner, SKIN_SIZE - corner, corner, corner);

		if (centerWidth > 0) {
			blit(graphics, texture, rect.x() + corner, rect.y(), centerWidth, corner, corner, 0, sourceCenter, corner);
			blit(graphics, texture, rect.x() + corner, rect.bottom() - corner, centerWidth, corner, corner, SKIN_SIZE - corner, sourceCenter, corner);
		}
		if (centerHeight > 0) {
			blit(graphics, texture, rect.x(), rect.y() + corner, corner, centerHeight, 0, corner, corner, sourceCenter);
			blit(graphics, texture, rect.right() - corner, rect.y() + corner, corner, centerHeight, SKIN_SIZE - corner, corner, corner, sourceCenter);
		}
		if (centerWidth > 0 && centerHeight > 0) {
			blit(graphics, texture, rect.x() + corner, rect.y() + corner, centerWidth, centerHeight, corner, corner, sourceCenter, sourceCenter);
		}
	}

	private static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height, int u, int v, int sourceWidth, int sourceHeight) {
		if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, sourceWidth, sourceHeight, SKIN_SIZE, SKIN_SIZE);
	}

	public static void roundedBorderedGradient(GuiGraphicsExtractor graphics, Rect rect, int radius, int top, int bottom, int border, int thickness) {
		roundedRect(graphics, rect, radius, border);
		Rect inner = new Rect(
				rect.x() + thickness,
				rect.y() + thickness,
				Math.max(0, rect.width() - thickness * 2),
				Math.max(0, rect.height() - thickness * 2)
		);
		roundedGradient(graphics, inner, Math.max(0, radius - thickness), top, bottom);
	}

	public static void roundedGradient(GuiGraphicsExtractor graphics, Rect rect, int radius, int top, int bottom) {
		int safeRadius = Math.min(radius, Math.min(rect.width(), rect.height()) / 2);
		for (int y = 0; y < rect.height(); y++) {
			float amount = rect.height() <= 1 ? 0.0F : y / (float) (rect.height() - 1);
			int color = Color.mix(top, bottom, amount);
			int inset = roundedInset(safeRadius, y, rect.height());
			graphics.fill(rect.x() + inset, rect.y() + y, rect.right() - inset, rect.y() + y + 1, color);
		}
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, Rect rect, int radius, int color) {
		int safeRadius = Math.min(radius, Math.min(rect.width(), rect.height()) / 2);
		for (int y = 0; y < rect.height(); y++) {
			int inset = roundedInset(safeRadius, y, rect.height());
			graphics.fill(rect.x() + inset, rect.y() + y, rect.right() - inset, rect.y() + y + 1, color);
		}
	}

	private static int roundedInset(int radius, int y, int height) {
		if (radius <= 0) {
			return 0;
		}
		int distanceFromEdge = y < radius ? y : height - 1 - y;
		if (distanceFromEdge >= radius) {
			return 0;
		}
		double centerDistance = radius - distanceFromEdge - 0.5D;
		double span = Math.sqrt(Math.max(0.0D, radius * radius - centerDistance * centerDistance));
		return Math.max(0, radius - (int) Math.ceil(span));
	}
}
