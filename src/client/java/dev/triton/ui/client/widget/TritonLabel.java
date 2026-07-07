package dev.triton.ui.client.widget;

import dev.triton.ui.client.layout.Rect;
import dev.triton.ui.client.theme.TritonTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class TritonLabel extends TritonWidget {
	private final Component text;
	private final Component detail;

	public TritonLabel(Component text, Component detail) {
		this.text = text;
		this.detail = detail;
	}

	@Override
	public int preferredHeight(Font font, int availableWidth) {
		return font.wordWrapHeight(detail, availableWidth) + font.lineHeight + 8;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, TritonTheme theme, int mouseX, int mouseY, float tickDelta) {
		Rect rect = bounds();
		graphics.text(font, text, rect.x(), rect.y(), theme.text(), false);
		graphics.textWithWordWrap(font, detail, rect.x(), rect.y() + font.lineHeight + 5, rect.width(), theme.mutedText());
	}
}
