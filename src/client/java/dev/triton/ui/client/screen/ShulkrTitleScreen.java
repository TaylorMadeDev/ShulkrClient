package dev.triton.ui.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.triton.ui.TritonUI;
import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.modern.TritonModernFragment;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.util.Color;
import icyllis.modernui.mc.MuiModApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ShulkrTitleScreen extends Screen {
	private static final int TEXT = Color.rgba(246, 244, 255, 255);
	private static final int MUTED = Color.rgba(188, 194, 222, 230);
	private static final int FAINT = Color.rgba(136, 146, 182, 190);
	private static final int PURPLE = Color.rgba(164, 88, 255, 255);
	private static final int CYAN = Color.rgba(92, 218, 255, 255);
	private static final int PANEL = Color.rgba(9, 14, 27, 190);
	private static final int PANEL_HOVER = Color.rgba(23, 27, 48, 224);
	private static final int STROKE = Color.rgba(116, 126, 168, 108);
	private static final int STROKE_HOT = Color.rgba(181, 112, 255, 180);
	private static final Identifier MENU_LOGO = TritonUI.id("textures/icons/shulkr-icons.png");
	private static final int MENU_LOGO_WIDTH = 1280;
	private static final int MENU_LOGO_HEIGHT = 720;
	private final List<MenuButton> buttons = new ArrayList<>();
	private int ticks;

	public ShulkrTitleScreen() {
		super(Component.literal("Shulkr"));
	}

	@Override
	protected void init() {
		rebuildButtons();
	}

	@Override
	public void tick() {
		ticks++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		float time = ticks + tickDelta;
		rebuildButtons();
		drawSpace(graphics, time);
		drawHero(graphics, font, mouseX, mouseY, time);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_U) {
			MuiModApi.openScreen(new TritonModernFragment());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (MenuButton button : buttons) {
			if (button.bounds().contains(event.x(), event.y())) {
				button.action().run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void rebuildButtons() {
		buttons.clear();
		TitleLayout layout = layout();
		int utilityWidth = (layout.buttonWidth() - layout.utilityGap()) / 2;
		buttons.add(new MenuButton("Singleplayer", "Open your worlds", new Rect(layout.buttonX(), layout.startY(), layout.buttonWidth(), layout.buttonHeight()),
				() -> minecraft.setScreen(new SelectWorldScreen(this))));
		buttons.add(new MenuButton("Multiplayer", "Join servers and LAN worlds",
				new Rect(layout.buttonX(), layout.startY() + layout.buttonHeight() + layout.buttonGap(), layout.buttonWidth(), layout.buttonHeight()),
				() -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
		buttons.add(new MenuButton("Open Shulkr Settings", "Configure scripts, overlays and controls",
				new Rect(layout.buttonX(), layout.startY() + (layout.buttonHeight() + layout.buttonGap()) * 2, layout.buttonWidth(), layout.primaryHeight()),
				() -> MuiModApi.openScreen(new TritonModernFragment("Settings"))));
		int utilityY = layout.startY() + layout.buttonHeight() * 2 + layout.primaryHeight() + layout.buttonGap() * 3;
		buttons.add(new MenuButton("Options", "Video, controls, resource packs", new Rect(layout.buttonX(), utilityY, utilityWidth, layout.utilityHeight()),
				() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options, false))));
		buttons.add(new MenuButton("Quit", "Close Minecraft", new Rect(layout.buttonX() + utilityWidth + layout.utilityGap(), utilityY, utilityWidth, layout.utilityHeight()),
				() -> minecraft.stop()));
	}

	private void drawSpace(GuiGraphicsExtractor graphics, float time) {
		graphics.fillGradient(0, 0, width, height, Color.rgba(2, 4, 13, 255), Color.rgba(6, 15, 32, 255));
		graphics.fillGradient(0, 0, width, Math.max(1, height / 2), Color.rgba(34, 20, 72, 116), Color.rgba(5, 13, 31, 0));
		graphics.fillGradient(width / 3, 0, width, height, Color.rgba(15, 79, 114, 82), Color.rgba(0, 0, 0, 0));
		graphics.fillGradient(0, height / 2, width, height, Color.rgba(0, 0, 0, 0), Color.rgba(2, 6, 17, 228));
		// fake space shader because i am absolutely not writing an actual shader for a title screen
		drawAurora(graphics, time);
		drawDeepGrid(graphics, time);

		// deterministic stars so resizing the window does not reshuffle the entire galaxy like a psychopath
		for (int i = 0; i < 118; i++) {
			int x = positiveModulo(i * 97 + 31 + (int) (time * (i % 4 + 1) * 0.08F), Math.max(1, width));
			int y = positiveModulo(i * 53 + 19, Math.max(1, height));
			float twinkle = (float) ((Math.sin((time * 0.045F) + i * 0.71F) + 1.0F) * 0.5F);
			int alpha = 58 + (int) (twinkle * 142.0F);
			int size = i % 13 == 0 ? 2 : 1;
			graphics.fill(x, y, x + size, y + size, Color.rgba(205, 221, 255, alpha));
		}

		drawPlanet(graphics, time);
		drawConstellation(graphics, time);
		drawDriftingShards(graphics, time);
		drawShootingStar(graphics, time, 0.0F, Color.rgba(184, 118, 255, 210));
		drawShootingStar(graphics, time, 78.0F, Color.rgba(90, 222, 255, 175));
		drawShootingStar(graphics, time, 151.0F, Color.rgba(255, 244, 196, 155));
		drawShootingStar(graphics, time, 233.0F, Color.rgba(255, 128, 200, 180));
		drawScanlines(graphics, time);
	}

	private void drawAurora(GuiGraphicsExtractor graphics, float time) {
		for (int i = 0; i < 7; i++) {
			int y = (int) (height * 0.12F + i * 22 + Math.sin(time * 0.017F + i) * 9.0F);
			int left = -80 + i * 36;
			int right = width + 120 - i * 16;
			int color = i % 2 == 0 ? Color.rgba(135, 66, 255, 34) : Color.rgba(61, 218, 255, 26);
			graphics.fillGradient(left, y, right, y + 34, color, Color.rgba(0, 0, 0, 0));
		}
	}

	private void drawDeepGrid(GuiGraphicsExtractor graphics, float time) {
		int horizon = (int) (height * 0.72F);
		// this is just perspective vibes. no geometry degree required thank god.
		for (int i = 0; i < 11; i++) {
			int y = horizon + i * i * 3;
			int alpha = Math.max(0, 44 - i * 3);
			graphics.fill(0, y, width, y + 1, Color.rgba(90, 224, 255, alpha));
		}
		for (int i = -8; i < 18; i++) {
			int base = width / 2 + i * 66 + (int) (Math.sin(time * 0.01F) * 5.0F);
			drawLineApprox(graphics, base, horizon, base + i * 24, height, Color.rgba(135, 75, 255, 28));
		}
	}

	private void drawShootingStar(GuiGraphicsExtractor graphics, float time, float offset, int color) {
		float period = 185.0F;
		float phase = ((time * 1.85F + offset) % period) / period;
		int startX = width + 120;
		int startY = 36 + positiveModulo((int) offset * 3 + 20, Math.max(80, height / 2));
		int x = (int) (startX - phase * (width + 260));
		int y = (int) (startY + phase * 215.0F);
		// if anyone asks this is a particle system. it is not. it is rectangles with confidence.
		for (int i = 0; i < 42; i++) {
			int alpha = Math.max(0, ((color >>> 24) & 255) - i * 5);
			int segmentColor = (color & 0x00FFFFFF) | (alpha << 24);
			int sx = x + i * 6;
			int sy = y - i * 3;
			graphics.fill(sx, sy, sx + Math.max(2, 17 - i / 3), sy + 2, segmentColor);
		}
		graphics.fill(x - 3, y - 3, x + 5, y + 5, Color.rgba(255, 255, 255, 240));
		graphics.fill(x - 7, y - 1, x + 9, y + 2, (color & 0x00FFFFFF) | 0x88000000);
	}

	private void drawPlanet(GuiGraphicsExtractor graphics, float time) {
		int planetSize = Math.max(70, Math.min(132, width / 9));
		int x = width - planetSize - 42;
		int y = 48 + (int) (Math.sin(time * 0.012F) * 8.0F);
		TritonDraw.roundedRect(graphics, new Rect(x - 18, y - 18, planetSize + 36, planetSize + 36), planetSize / 2,
				Color.rgba(94, 65, 170, 36));
		TritonDraw.roundedRect(graphics, new Rect(x, y, planetSize, planetSize), planetSize / 2,
				Color.rgba(39, 28, 82, 138));
		graphics.fillGradient(x + 8, y + 12, x + planetSize - 8, y + planetSize / 2,
				Color.rgba(165, 103, 255, 62), Color.rgba(40, 220, 255, 20));
		graphics.fill(x - 20, y + planetSize / 2 - 2, x + planetSize + 20, y + planetSize / 2 + 1,
				Color.rgba(191, 142, 255, 64));
	}

	private void drawDriftingShards(GuiGraphicsExtractor graphics, float time) {
		for (int i = 0; i < 9; i++) {
			int x = positiveModulo(60 + i * 211 + (int) (time * (0.32F + i * 0.03F)), Math.max(width, 1));
			int y = 70 + positiveModulo(i * 79 + (int) (Math.sin(time * 0.013F + i) * 30.0F), Math.max(120, height - 160));
			int color = i % 3 == 0 ? Color.rgba(166, 92, 255, 82) : i % 3 == 1 ? Color.rgba(76, 218, 255, 70) : Color.rgba(255, 255, 255, 42);
			graphics.fill(x, y, x + 18, y + 2, color);
			graphics.fill(x + 8, y - 8, x + 10, y + 10, Color.rgba(255, 255, 255, 30));
		}
	}

	private void drawConstellation(GuiGraphicsExtractor graphics, float time) {
		int baseX = Math.max(22, width / 8);
		int baseY = Math.max(26, height / 5);
		int[][] points = {
				{baseX, baseY},
				{baseX + 54, baseY + 24},
				{baseX + 112, baseY + 4},
				{baseX + 168, baseY + 42},
				{baseX + 222, baseY + 28}
		};
		int alpha = 54 + (int) ((Math.sin(time * 0.035F) + 1.0F) * 24.0F);
		for (int i = 0; i < points.length; i++) {
			graphics.fill(points[i][0] - 2, points[i][1] - 2, points[i][0] + 3, points[i][1] + 3,
					Color.rgba(214, 226, 255, alpha + 56));
			if (i > 0) {
				drawLineApprox(graphics, points[i - 1][0], points[i - 1][1], points[i][0], points[i][1],
						Color.rgba(160, 176, 255, alpha));
			}
		}
	}

	private void drawLineApprox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		for (int i = 0; i <= steps; i += 3) {
			float t = steps == 0 ? 0.0F : i / (float) steps;
			int x = (int) (x1 + (x2 - x1) * t);
			int y = (int) (y1 + (y2 - y1) * t);
			graphics.fill(x, y, x + 2, y + 1, color);
		}
	}

	private void drawHero(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float time) {
		TitleLayout layout = layout();
		Rect panel = layout.panel();

		drawNebulaGlow(graphics, panel, time);
		int radius = layout.compact() ? 14 : 20;
		TritonDraw.roundedRect(graphics, new Rect(panel.x() - 1, panel.y() - 1, panel.width() + 2, panel.height() + 2), radius, STROKE);
		TritonDraw.roundedRect(graphics, panel, radius, PANEL);
		graphics.fillGradient(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + layout.headerHeight() + layout.padding(),
				Color.rgba(105, 68, 177, 52), Color.rgba(8, 14, 26, 0));
		drawHeader(graphics, font, layout);

		for (MenuButton button : buttons) {
			drawMenuButton(graphics, font, button, button.bounds().contains(mouseX, mouseY), button.label().equals("Open Shulkr Settings"), time);
		}
	}

	private void drawMenuLogo(GuiGraphicsExtractor graphics, Rect bounds, int maxWidth, float time) {
		int logoWidth = Math.min(maxWidth, bounds.width());
		if (logoWidth <= 0) {
			return;
		}
		int logoHeight = Math.max(1, logoWidth * MENU_LOGO_HEIGHT / MENU_LOGO_WIDTH);
		int x = bounds.centerX() - logoWidth / 2;
		int y = bounds.y() + (bounds.height() - logoHeight) / 2;
		int glowAlpha = 24 + (int) ((Math.sin(time * 0.05F) + 1.0F) * 12.0F);
		graphics.fillGradient(x - 18, y + logoHeight / 3, x + logoWidth + 18, y + logoHeight + 8,
				Color.rgba(140, 78, 255, glowAlpha), Color.rgba(0, 0, 0, 0));
		graphics.blit(RenderPipelines.GUI_TEXTURED, MENU_LOGO, x, y, 0, 0, logoWidth, logoHeight,
				MENU_LOGO_WIDTH, MENU_LOGO_HEIGHT, MENU_LOGO_WIDTH, MENU_LOGO_HEIGHT);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, TitleLayout layout) {
		Rect panel = layout.panel();
		Rect header = new Rect(panel.x() + layout.padding(), panel.y() + layout.padding(),
				panel.width() - layout.padding() * 2, layout.headerHeight());
		graphics.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), Color.rgba(132, 142, 182, 76));
		drawMenuLogo(graphics, header, Math.min(header.width() - 24, layout.compact() ? 240 : 340), ticks);
	}

	private void drawNebulaGlow(GuiGraphicsExtractor graphics, Rect panel, float time) {
		int pulse = 40 + (int) ((Math.sin(time * 0.045F) + 1.0F) * 28.0F);
		graphics.fillGradient(panel.x() - 90, panel.y() - 56, panel.right() + 80, panel.y() + 44,
				Color.rgba(150, 78, 255, pulse), Color.rgba(0, 0, 0, 0));
		graphics.fillGradient(panel.x() + 40, panel.bottom() - 40, panel.right() + 130, panel.bottom() + 45,
				Color.rgba(52, 194, 255, 34), Color.rgba(0, 0, 0, 0));
	}


	private void drawScanlines(GuiGraphicsExtractor graphics, float time) {
		int offset = positiveModulo((int) (time * 0.35F), 6);
		for (int y = offset; y < height; y += 6) {
			graphics.fill(0, y, width, y + 1, Color.rgba(255, 255, 255, 8));
		}
	}

	private void drawMenuButton(GuiGraphicsExtractor graphics, Font font, MenuButton button, boolean hovered, boolean primary, float time) {
		Rect rect = button.bounds();
		int fill = primary ? Color.rgba(92, 45, 180, hovered ? 238 : 214) : hovered ? PANEL_HOVER : Color.rgba(14, 20, 36, 188);
		int stroke = hovered || primary ? STROKE_HOT : STROKE;
		int radius = Math.min(10, Math.max(4, rect.height() / 4));
		TritonDraw.roundedRect(graphics, new Rect(rect.x() - 1, rect.y() - 1, rect.width() + 2, rect.height() + 2), radius, stroke);
		TritonDraw.roundedRect(graphics, rect, radius, fill);
		if (hovered) {
			graphics.fill(rect.x() + 2, rect.y() + 7, rect.x() + 4, rect.bottom() - 7, primary ? CYAN : PURPLE);
		}
		boolean showSubtitle = rect.width() > 220 && rect.height() >= 38;
		int textY = showSubtitle ? rect.y() + 7 : rect.y() + Math.max(0, (rect.height() - font.lineHeight) / 2);
		graphics.text(font, button.label(), rect.x() + 15, textY, TEXT, false);
		if (showSubtitle) {
			graphics.text(font, button.subtitle(), rect.x() + 15, rect.y() + 22, primary ? Color.rgba(221, 204, 255, 230) : FAINT, false);
		}
		graphics.text(font, ">", rect.right() - 20, rect.y() + rect.height() / 2 - 4, hovered ? TEXT : MUTED, false);
	}

	private int positiveModulo(int value, int mod) {
		int result = value % mod;
		return result < 0 ? result + mod : result;
	}

	private TitleLayout layout() {
		int horizontalMargin = Math.max(6, Math.min(36, width / 12));
		int verticalMargin = Math.max(6, Math.min(28, height / 12));
		int panelWidth = Math.max(1, Math.min(540, width - horizontalMargin * 2));
		int panelHeight = Math.max(1, Math.min(286, height - verticalMargin * 2));
		int panelX = (width - panelWidth) / 2;
		int panelY = (height - panelHeight) / 2;
		boolean compact = panelHeight < 250 || panelWidth < 440;
		int padding = Math.max(5, Math.min(compact ? 12 : 20, panelWidth / 7));
		int headerHeight = compact ? 44 : 52;
		int startY = panelY + padding + headerHeight + (compact ? 4 : 10);
		int contentHeight = Math.max(4, panelY + panelHeight - padding - startY);
		int buttonGap = compact ? 5 : 7;
		int rowHeight = Math.max(1, Math.min(36, (contentHeight - buttonGap * 3) / 4));
		int buttonWidth = Math.max(1, Math.min(360, panelWidth - padding * 2));
		int buttonX = panelX + (panelWidth - buttonWidth) / 2;
		int utilityGap = Math.max(4, Math.min(10, buttonWidth / 12));
		return new TitleLayout(
				new Rect(panelX, panelY, panelWidth, panelHeight),
				compact,
				padding,
				buttonX,
				buttonWidth,
				startY,
				rowHeight,
				rowHeight,
				rowHeight,
				buttonGap,
				utilityGap,
				headerHeight
		);
	}

	private record MenuButton(String label, String subtitle, Rect bounds, Runnable action) {
	}

	private record TitleLayout(
			Rect panel,
			boolean compact,
			int padding,
			int buttonX,
			int buttonWidth,
			int startY,
			int buttonHeight,
			int primaryHeight,
			int utilityHeight,
			int buttonGap,
			int utilityGap,
			int headerHeight
	) {
	}
}
