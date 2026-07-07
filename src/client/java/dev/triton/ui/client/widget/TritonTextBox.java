package dev.triton.ui.client.widget;

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

public final class TritonTextBox extends TritonWidget {
	private final Component label;
	private String value;
	private int cursor;
	private int tick;

	public TritonTextBox(Component label, String value) {
		this.label = label;
		this.value = value;
		this.cursor = value.length();
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return 40;
	}

	@Override
	public void tick() {
		tick++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		Rect rect = bounds();
		graphics.text(font, label, rect.x(), rect.y(), theme.mutedText(), false);
		Rect field = new Rect(rect.x(), rect.y() + 13, rect.width(), 23);
		TritonDraw.field(graphics, field, theme, focused(), hovered(mouseX, mouseY));
		String visible = font.plainSubstrByWidth(value, field.width() - 16, true);
		graphics.text(font, visible, field.x() + 8, field.y() + 7, theme.text(), false);
		if (focused() && tick / 10 % 2 == 0) {
			int cursorX = field.x() + 8 + font.width(font.plainSubstrByWidth(value.substring(0, cursor), field.width() - 16, true));
			graphics.fill(cursorX, field.y() + 5, cursorX + 1, field.bottom() - 5, Color.alpha(theme.accent(), 220));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered(event.x(), event.y())) {
			focused(true);
			cursor = value.length();
			return true;
		}
		focused(false);
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!focused()) {
			return false;
		}
		if (event.key() == InputConstants.KEY_BACKSPACE && cursor > 0) {
			value = value.substring(0, cursor - 1) + value.substring(cursor);
			cursor--;
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE && cursor < value.length()) {
			value = value.substring(0, cursor) + value.substring(cursor + 1);
			return true;
		}
		if (event.key() == InputConstants.KEY_LEFT && cursor > 0) {
			cursor--;
			return true;
		}
		if (event.key() == InputConstants.KEY_RIGHT && cursor < value.length()) {
			cursor++;
			return true;
		}
		if (event.key() == InputConstants.KEY_HOME) {
			cursor = 0;
			return true;
		}
		if (event.key() == InputConstants.KEY_END) {
			cursor = value.length();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!focused() || !event.isAllowedChatCharacter()) {
			return false;
		}
		String typed = event.codepointAsString();
		value = value.substring(0, cursor) + typed + value.substring(cursor);
		cursor += typed.length();
		return true;
	}
}
