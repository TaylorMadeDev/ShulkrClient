package dev.triton.ui.client.widget;

import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.render.TritonDraw;
import dev.triton.ui.client.render.TritonText;
import dev.triton.ui.client.theme.TritonTheme;
import dev.triton.ui.client.util.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

public final class TritonDashboard extends TritonWidget {
	private final List<NavItem> navItems = List.of(
			new NavItem("Modules", "MD"),
			new NavItem("Buttons", "BT"),
			new NavItem("Inputs", "IN"),
			new NavItem("Sliders", "SL"),
			new NavItem("Toggles", "TG"),
			new NavItem("Scrollbars", "SC"),
			new NavItem("Theme", "TH")
	);
	private final List<String> filters = List.of("All", "Combat", "Movement", "Player", "Render", "World", "Misc", "Exploit", "Ghost");
	private final List<ModuleCard> modules = new ArrayList<>();
	private final List<ModuleCard> buttons = new ArrayList<>();
	private final List<ModuleCard> inputs = new ArrayList<>();
	private final List<ModuleCard> sliders = new ArrayList<>();
	private final List<ModuleCard> toggles = new ArrayList<>();
	private final List<ModuleCard> scrollbars = new ArrayList<>();
	private final List<ModuleCard> themes = new ArrayList<>();
	private int activeNav;
	private int activeFilter;
	private int scroll;
	private int maxScroll;
	private Rect gridClip = new Rect(0, 0, 0, 0);

	public TritonDashboard() {
		modules.add(new ModuleCard("KillAura", "Attack players", "KA", true));
		modules.add(new ModuleCard("Velocity", "Anti knockback", "VL", true));
		modules.add(new ModuleCard("Scaffold", "Bridge assist", "SF", true));
		modules.add(new ModuleCard("NoSlow", "Remove slowdown", "NS", true));
		modules.add(new ModuleCard("Sprint", "Force sprint", "SP", true));
		modules.add(new ModuleCard("LongJump", "Jump further", "LJ", false));
		modules.add(new ModuleCard("AutoTotem", "Pop totems", "AT", true));
		modules.add(new ModuleCard("ChestStealer", "Steal chests", "CS", true));
		modules.add(new ModuleCard("ESP", "See everything", "EP", true));
		modules.add(new ModuleCard("FullBright", "Full bright", "FB", true));
		modules.add(new ModuleCard("XRay", "See through blocks", "XR", false));
		modules.add(new ModuleCard("NameTags", "Show players", "NT", true));
		modules.add(new ModuleCard("Freecam", "Camera mode", "FC", false));
		modules.add(new ModuleCard("HitBoxes", "Show hitboxes", "HB", true));
		modules.add(new ModuleCard("AntiBot", "Ignore bots", "AB", true));
		modules.add(new ModuleCard("AutoFish", "Fish automatically", "AF", false));

		buttons.add(new ModuleCard("Primary Button", "Main action glow", "PB", true));
		buttons.add(new ModuleCard("Ghost Button", "Low emphasis action", "GB", false));
		buttons.add(new ModuleCard("Danger Button", "Destructive action", "DB", true));
		buttons.add(new ModuleCard("Icon Button", "Compact toolbar use", "IB", true));
		buttons.add(new ModuleCard("Split Button", "Action plus menu", "SB", false));
		buttons.add(new ModuleCard("Dock Button", "Bottom nav target", "DK", true));

		inputs.add(new ModuleCard("Search Field", "Module filtering", "SF", true));
		inputs.add(new ModuleCard("Text Box", "Config values", "TB", true));
		inputs.add(new ModuleCard("Command Bar", "Palette input", "CB", false));
		inputs.add(new ModuleCard("Number Field", "Precise tuning", "NF", true));
		inputs.add(new ModuleCard("Keybind Capture", "Press to record", "KC", false));
		inputs.add(new ModuleCard("Tag Input", "Chip entry", "TI", true));

		sliders.add(new ModuleCard("Glow Slider", "Blue intensity", "GS", true));
		sliders.add(new ModuleCard("Opacity Slider", "Glass strength", "OS", true));
		sliders.add(new ModuleCard("Speed Slider", "Animation rate", "SS", false));
		sliders.add(new ModuleCard("Range Slider", "Min and max", "RS", true));
		sliders.add(new ModuleCard("Stepper", "Ticked numeric", "ST", false));
		sliders.add(new ModuleCard("Volume Bar", "Audio setting", "VB", true));

		toggles.add(new ModuleCard("Switch", "Binary setting", "SW", true));
		toggles.add(new ModuleCard("Checkbox", "Classic choice", "CB", false));
		toggles.add(new ModuleCard("Radio Group", "Single option", "RG", true));
		toggles.add(new ModuleCard("Segmented", "Mode picker", "SG", true));
		toggles.add(new ModuleCard("Chip Toggle", "Filter chip", "CT", false));
		toggles.add(new ModuleCard("Power Toggle", "Module state", "PT", true));

		scrollbars.add(new ModuleCard("List Scroll", "Dense rows", "LS", true));
		scrollbars.add(new ModuleCard("Grid Scroll", "Module cards", "GS", true));
		scrollbars.add(new ModuleCard("Thin Rail", "Reference style", "TR", true));
		scrollbars.add(new ModuleCard("Hover Thumb", "Appears on hover", "HT", false));
		scrollbars.add(new ModuleCard("Paged Scroll", "Section snapping", "PS", false));
		scrollbars.add(new ModuleCard("Momentum", "Smooth wheel feel", "MM", true));
		scrollbars.add(new ModuleCard("Overflow Card", "Clipped content", "OC", true));
		scrollbars.add(new ModuleCard("Nested Scroll", "Panel inside panel", "NS", false));
		scrollbars.add(new ModuleCard("Scrollbar Skin", "Blue electric", "SK", true));
		scrollbars.add(new ModuleCard("Sticky Header", "Pinned title", "SH", true));

		themes.add(new ModuleCard("Blue Glass", "Reference palette", "BG", true));
		themes.add(new ModuleCard("Deep Navy", "Low glare base", "DN", true));
		themes.add(new ModuleCard("Cyan Accent", "Hot edge color", "CA", true));
		themes.add(new ModuleCard("Status Colors", "Info and warning", "SC", false));
		themes.add(new ModuleCard("Sidebar Rail", "Brand surface", "SR", true));
		themes.add(new ModuleCard("Right Panels", "Utility cards", "RP", true));
	}

	@Override
	public boolean fillsScreen() {
		return true;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		Rect screen = bounds();
		renderBackdrop(graphics, screen);
		int sidebarWidth = Math.min(188, Math.max(148, screen.width() / 5));
		int rightWidth = Math.min(210, Math.max(0, screen.width() / 4));
		if (screen.width() < 720) {
			rightWidth = 0;
			sidebarWidth = 132;
		}
		Rect sidebar = new Rect(0, 0, sidebarWidth, screen.height());
		Rect right = new Rect(screen.right() - rightWidth, 0, rightWidth, screen.height());
		Rect main = new Rect(sidebar.right(), 0, screen.width() - sidebarWidth - rightWidth, screen.height());

		renderSidebar(graphics, font, theme, sidebar, mouseX, mouseY);
		renderTop(graphics, font, theme, main, right, mouseX, mouseY);
		renderMain(graphics, font, theme, main, mouseX, mouseY);
		if (rightWidth > 0) {
			renderRightRail(graphics, font, theme, right);
		}
		renderBottomDock(graphics, font, theme, main);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		Rect screen = bounds();
		int sidebarWidth = Math.min(188, Math.max(148, screen.width() / 5));
		if (screen.width() < 720) {
			sidebarWidth = 132;
		}
		for (int i = 0; i < navItems.size(); i++) {
			Rect row = new Rect(12, 82 + i * 34, sidebarWidth - 24, 28);
			if (row.contains(event.x(), event.y())) {
				activeNav = i;
				scroll = 0;
				return true;
			}
		}

		Rect main = mainRect();
		int filterY = main.y() + 108;
		int x = main.x() + 24;
		for (int i = 0; i < filters.size(); i++) {
			int w = 32 + filters.get(i).length() * 6;
			Rect pill = new Rect(x, filterY, w, 24);
			if (pill.contains(event.x(), event.y())) {
				activeFilter = i;
				return true;
			}
			x += w + 8;
		}

		List<ModuleCard> cards = currentCards();
		for (int i = 0; i < cards.size(); i++) {
			Rect card = cardRect(i);
			if (card.contains(event.x(), event.y())) {
				cards.get(i).enabled = !cards.get(i).enabled;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!gridClip.contains(mouseX, mouseY) || maxScroll <= 0) {
			return false;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 26.0)));
		return true;
	}

	private void renderBackdrop(GuiGraphicsExtractor graphics, Rect screen) {
		graphics.fillGradient(0, 0, screen.width(), screen.height(), Color.rgba(5, 12, 26, 205), Color.rgba(3, 18, 35, 222));
		graphics.fillGradient(screen.width() / 4, 0, screen.width(), screen.height() / 2, Color.rgba(35, 96, 177, 54), Color.rgba(5, 12, 26, 0));
		graphics.fillGradient(0, screen.height() / 2, screen.width(), screen.height(), Color.rgba(5, 12, 26, 0), Color.rgba(14, 93, 88, 40));
	}

	private void renderSidebar(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect sidebar, int mouseX, int mouseY) {
		graphics.fillGradient(sidebar.x(), sidebar.y(), sidebar.right(), sidebar.bottom(), Color.rgba(3, 8, 17, 236), Color.rgba(2, 16, 27, 226));
		graphics.fill(sidebar.right() - 1, 0, sidebar.right(), sidebar.bottom(), Color.rgba(70, 135, 230, 55));
		renderLogo(graphics, theme, sidebar.x() + 22, sidebar.y() + 24);
		graphics.text(font, TritonText.smooth("TRITON"), sidebar.x() + 66, sidebar.y() + 27, theme.text(), false);
		graphics.text(font, TritonText.smooth("CLIENT"), sidebar.x() + 66, sidebar.y() + 44, theme.accent(), false);

		for (int i = 0; i < navItems.size(); i++) {
			Rect row = new Rect(sidebar.x() + 12, sidebar.y() + 82 + i * 34, sidebar.width() - 24, 28);
			boolean active = i == activeNav;
			boolean hovered = row.contains(mouseX, mouseY);
			if (active) {
				TritonDraw.skin(graphics, TritonDraw.NAV_ACTIVE, row, 12);
			} else if (hovered) {
				TritonDraw.skin(graphics, TritonDraw.PILL, row, 12);
			}
			if (active) {
				TritonDraw.roundedRect(graphics, new Rect(row.x(), row.y() + 3, 3, row.height() - 6), 2, theme.accentHot());
			}
			graphics.text(font, navItems.get(i).icon(), row.x() + 10, row.y() + 10, active ? theme.text() : theme.mutedText(), false);
			graphics.text(font, navItems.get(i).label(), row.x() + 38, row.y() + 10, active || hovered ? theme.text() : theme.mutedText(), false);
		}

		Rect status = new Rect(sidebar.x() + 12, sidebar.bottom() - 58, sidebar.width() - 24, 44);
		TritonDraw.skin(graphics, TritonDraw.PANEL_SOFT, status, 14);
		TritonDraw.roundedRect(graphics, new Rect(status.x() + 10, status.y() + 12, 6, 6), 3, theme.success());
		graphics.text(font, TritonText.smooth("Connected"), status.x() + 23, status.y() + 10, theme.text(), false);
		graphics.text(font, TritonText.smooth("Minecraft 26.1.2"), status.x() + 10, status.y() + 26, theme.mutedText(), false);
	}

	private void renderTop(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect main, Rect right, int mouseX, int mouseY) {
		Rect search = new Rect(main.x() + 20, main.y() + 18, Math.min(220, main.width() - 40), 30);
		TritonDraw.skin(graphics, TritonDraw.SEARCH, search, 14);
		graphics.text(font, TritonText.smooth("Search modules..."), search.x() + 35, search.y() + 11, theme.mutedText(), false);
		graphics.outline(search.x() + 14, search.y() + 9, 8, 8, Color.rgba(162, 186, 222, 180));
		graphics.fill(search.x() + 23, search.y() + 18, search.x() + 27, search.y() + 19, Color.rgba(162, 186, 222, 180));

		if (right.width() > 0) {
			graphics.fillGradient(right.x(), right.y(), right.right(), right.y() + 64, Color.rgba(12, 25, 45, 224), Color.rgba(8, 16, 31, 214));
			graphics.text(font, TritonText.smooth(Minecraft.getInstance().getUser().getName()), right.x() + 68, right.y() + 22, theme.text(), false);
			graphics.text(font, TritonText.smooth("Premium"), right.x() + 68, right.y() + 39, theme.accent(), false);
			TritonDraw.skin(graphics, TritonDraw.PILL_ACTIVE, new Rect(right.x() + 28, right.y() + 16, 28, 28), 12);
		}
	}

	private void renderMain(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect main, int mouseX, int mouseY) {
		Rect panel = new Rect(main.x() + 14, main.y() + 58, main.width() - 28, main.height() - 92);
		TritonDraw.skin(graphics, TritonDraw.PANEL, panel, 16);

		graphics.text(font, TritonText.smooth(activeTitle()), panel.x() + 48, panel.y() + 23, theme.text(), false);
		graphics.text(font, TritonText.smooth(activeNav == 0 ? "127 modules" : componentCountText()), panel.right() - 116, panel.y() + 25, theme.mutedText(), false);
		TritonDraw.skin(graphics, TritonDraw.PILL_ACTIVE, new Rect(panel.x() + 22, panel.y() + 19, 20, 20), 12);
		graphics.text(font, TritonText.smooth(navItems.get(activeNav).icon()), panel.x() + 27, panel.y() + 26, theme.accentHot(), false);
		renderViewButtons(graphics, theme, panel.right() - 52, panel.y() + 17);

		int filterY = panel.y() + 62;
		int x = panel.x() + 20;
		for (int i = 0; i < filters.size(); i++) {
			int w = 32 + filters.get(i).length() * 6;
			if (x + w > panel.right() - 66) {
				break;
			}
			Rect pill = new Rect(x, filterY, w, 24);
			boolean active = i == activeFilter;
			TritonDraw.skin(graphics, active ? TritonDraw.PILL_ACTIVE : TritonDraw.PILL, pill, 12);
			graphics.centeredText(font, TritonText.smooth(filters.get(i)), pill.centerX(), pill.y() + 8, active ? theme.text() : theme.mutedText());
			x += w + 8;
		}

		gridClip = new Rect(panel.x() + 20, panel.y() + 102, panel.width() - 40, panel.height() - 122);
		graphics.enableScissor(gridClip.x(), gridClip.y(), gridClip.right(), gridClip.bottom());
		List<ModuleCard> cards = currentCards();
		for (int i = 0; i < cards.size(); i++) {
			Rect card = cardRect(i);
			renderModuleCard(graphics, font, theme, card, cards.get(i), mouseX, mouseY);
		}
		graphics.disableScissor();
		renderGridScrollbar(graphics, theme, new Rect(panel.right() - 14, gridClip.y(), 4, gridClip.height()));
	}

	private void renderRightRail(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect right) {
		Rect info = new Rect(right.x() + 16, right.y() + 76, right.width() - 28, 110);
		sidePanel(graphics, info, theme);
		graphics.text(font, TritonText.smooth("Info"), info.x() + 14, info.y() + 14, theme.text(), false);
		renderLogo(graphics, theme, info.x() + 20, info.y() + 44);
		graphics.text(font, TritonText.smooth("Triton Client"), info.x() + 72, info.y() + 48, theme.text(), false);
		graphics.text(font, TritonText.smooth("Premium UI library"), info.x() + 72, info.y() + 64, theme.mutedText(), false);
		graphics.text(font, TritonText.smooth("1.0.0"), info.x() + 72, info.y() + 82, theme.mutedText(), false);

		Rect active = new Rect(right.x() + 16, info.bottom() + 14, right.width() - 28, 198);
		sidePanel(graphics, active, theme);
		graphics.text(font, TritonText.smooth("Active Modules"), active.x() + 14, active.y() + 14, theme.text(), false);
		String[] names = {"KillAura", "Velocity", "Scaffold", "ESP", "Sprint", "AutoTotem"};
		int[] colors = {theme.danger(), theme.accent(), theme.accentHot(), Color.rgba(178, 96, 255, 255), theme.success(), Color.rgba(255, 172, 55, 255)};
		for (int i = 0; i < names.length; i++) {
			Rect row = new Rect(active.x() + 10, active.y() + 36 + i * 24, active.width() - 20, 22);
			TritonDraw.skin(graphics, TritonDraw.PANEL_SOFT, row, 10);
			graphics.text(font, TritonText.smooth(names[i]), row.x() + 25, row.y() + 7, colors[i], false);
			graphics.text(font, TritonText.smooth("x"), row.right() - 14, row.y() + 7, theme.mutedText(), false);
		}
		TritonDraw.skin(graphics, TritonDraw.PILL, new Rect(active.x() + 14, active.bottom() - 30, active.width() - 28, 20), 12);
		graphics.centeredText(font, TritonText.smooth("Clear All"), active.centerX(), active.bottom() - 24, theme.text());

		Rect keybinds = new Rect(right.x() + 16, active.bottom() + 14, right.width() - 28, 132);
		sidePanel(graphics, keybinds, theme);
		graphics.text(font, TritonText.smooth("Keybinds"), keybinds.x() + 14, keybinds.y() + 14, theme.text(), false);
		String[] binds = {"KillAura [R]", "Velocity [V]", "ESP [Y]", "HUD [Right Shift]"};
		for (int i = 0; i < binds.length; i++) {
			graphics.text(font, TritonText.smooth(binds[i]), keybinds.x() + 18, keybinds.y() + 40 + i * 16, theme.mutedText(), false);
		}
	}

	private void renderBottomDock(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect main) {
		Rect dock = new Rect(main.centerX() - 150, main.bottom() - 36, 300, 34);
		TritonDraw.skin(graphics, TritonDraw.DOCK, dock, 18);
		String[] items = {"HOME", "COMBAT", "PLAYER", "CONFIG"};
		for (int i = 0; i < items.length; i++) {
			int x = dock.x() + 34 + i * 70;
			graphics.text(font, TritonText.smooth(items[i]), x, dock.y() + 12, i == 0 ? theme.accentHot() : theme.mutedText(), false);
			if (i == 0) {
				graphics.fill(x - 4, dock.bottom() - 3, x + 34, dock.bottom() - 1, theme.accent());
			}
		}
	}

	private void renderLogo(GuiGraphicsExtractor graphics, TritonTheme theme, int x, int y) {
		graphics.fillGradient(x + 10, y, x + 16, y + 44, theme.accentHot(), theme.accent());
		graphics.fillGradient(x, y + 8, x + 6, y + 38, Color.rgba(170, 210, 255, 255), theme.accent());
		graphics.fillGradient(x + 20, y + 8, x + 26, y + 38, Color.rgba(170, 210, 255, 255), theme.accent());
		graphics.fill(x + 4, y + 34, x + 13, y + 42, theme.accent());
		graphics.fill(x + 13, y + 34, x + 22, y + 42, theme.accentHot());
	}

	private void renderViewButtons(GuiGraphicsExtractor graphics, TritonTheme theme, int x, int y) {
		TritonDraw.skin(graphics, TritonDraw.PILL_ACTIVE, new Rect(x, y, 22, 22), 12);
		graphics.text(graphicsTextFont(), TritonText.smooth("[]"), x + 5, y + 8, theme.text(), false);
		TritonDraw.skin(graphics, TritonDraw.PILL, new Rect(x + 28, y, 22, 22), 12);
	}

	private Font graphicsTextFont() {
		return Minecraft.getInstance().font;
	}

	private void renderModuleCard(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, Rect card, ModuleCard module, int mouseX, int mouseY) {
		boolean hovered = card.contains(mouseX, mouseY);
		int top = hovered ? Color.rgba(21, 44, 78, 224) : Color.rgba(14, 30, 55, 205);
		int bottom = hovered ? Color.rgba(12, 31, 61, 230) : Color.rgba(9, 22, 43, 218);
		TritonDraw.skin(graphics, hovered ? TritonDraw.CARD_HOT : TritonDraw.CARD, card, 12);
		graphics.text(font, TritonText.smooth(module.icon), card.x() + 18, card.y() + 28, module.enabled ? theme.accentHot() : theme.mutedText(), false);
		graphics.text(font, TritonText.smooth(module.name), card.x() + 52, card.y() + 25, theme.text(), false);
		graphics.text(font, TritonText.smooth(module.description), card.x() + 52, card.y() + 41, theme.mutedText(), false);
		graphics.text(font, TritonText.smooth("*"), card.right() - 21, card.y() + 18, theme.mutedText(), false);
		renderTinyToggle(graphics, theme, new Rect(card.right() - 54, card.bottom() - 29, 34, 14), module.enabled);
	}

	private void renderTinyToggle(GuiGraphicsExtractor graphics, TritonTheme theme, Rect rect, boolean enabled) {
		TritonDraw.skin(graphics, enabled ? TritonDraw.TOGGLE_ON : TritonDraw.TOGGLE_OFF, rect, 16);
		int knobX = enabled ? rect.right() - 12 : rect.x() + 3;
		TritonDraw.roundedRect(graphics, new Rect(knobX, rect.y() + 3, 8, rect.height() - 6), 4, Color.rgba(238, 246, 255, 255));
	}

	private void sidePanel(GuiGraphicsExtractor graphics, Rect panel, TritonTheme theme) {
		TritonDraw.skin(graphics, TritonDraw.PANEL_SOFT, panel, 14);
	}

	private void renderGridScrollbar(GuiGraphicsExtractor graphics, TritonTheme theme, Rect rail) {
		TritonDraw.roundedRect(graphics, rail, 2, Color.rgba(4, 14, 30, 170));
		if (maxScroll <= 0) {
			TritonDraw.roundedRect(graphics, rail, 2, Color.rgba(74, 146, 255, 70));
			return;
		}
		int knobHeight = Math.max(28, rail.height() * rail.height() / (rail.height() + maxScroll));
		int knobY = rail.y() + Math.round((rail.height() - knobHeight) * (scroll / (float) maxScroll));
		TritonDraw.roundedGradient(graphics, new Rect(rail.x() - 1, knobY, rail.width() + 2, knobHeight), 3, theme.accent(), theme.accentHot());
	}

	private Rect mainRect() {
		Rect screen = bounds();
		int sidebarWidth = Math.min(188, Math.max(148, screen.width() / 5));
		int rightWidth = Math.min(210, Math.max(0, screen.width() / 4));
		if (screen.width() < 720) {
			rightWidth = 0;
			sidebarWidth = 132;
		}
		return new Rect(sidebarWidth, 0, screen.width() - sidebarWidth - rightWidth, screen.height());
	}

	private Rect cardRect(int index) {
		Rect main = mainRect();
		Rect panel = new Rect(main.x() + 14, main.y() + 58, main.width() - 28, main.height() - 92);
		Rect clip = new Rect(panel.x() + 20, panel.y() + 102, panel.width() - 40, panel.height() - 122);
		int columns = Math.max(1, Math.min(4, clip.width() / 150));
		int gap = 10;
		int cardWidth = (clip.width() - gap * (columns - 1) - 10) / columns;
		int cardHeight = activeNav == 0 ? 70 : 62;
		int col = index % columns;
		int row = index / columns;
		int cardCount = currentCards().size();
		int contentHeight = ((cardCount + columns - 1) / columns) * (cardHeight + gap) - gap;
		maxScroll = Math.max(0, contentHeight - clip.height());
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		return new Rect(clip.x() + col * (cardWidth + gap), clip.y() + row * (cardHeight + gap) - scroll, cardWidth, cardHeight);
	}

	private List<ModuleCard> currentCards() {
		return switch (activeNav) {
			case 1 -> buttons;
			case 2 -> inputs;
			case 3 -> sliders;
			case 4 -> toggles;
			case 5 -> scrollbars;
			case 6 -> themes;
			default -> modules;
		};
	}

	private String activeTitle() {
		return switch (activeNav) {
			case 1 -> "Buttons";
			case 2 -> "Inputs";
			case 3 -> "Sliders";
			case 4 -> "Toggles";
			case 5 -> "Scrollbars";
			case 6 -> "Theme";
			default -> "Modules";
		};
	}

	private String componentCountText() {
		return activeNav == 5 ? "Wheel-ready" : "Component demo";
	}

	private record NavItem(String label, String icon) {
	}

	private static final class ModuleCard {
		private final String name;
		private final String description;
		private final String icon;
		private boolean enabled;

		private ModuleCard(String name, String description, String icon, boolean enabled) {
			this.name = name;
			this.description = description;
			this.icon = icon;
			this.enabled = enabled;
		}
	}
}
