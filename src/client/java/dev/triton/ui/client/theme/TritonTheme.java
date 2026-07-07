package dev.triton.ui.client.theme;

import dev.triton.ui.client.util.Color;

public record TritonTheme(
		int backdropTop,
		int backdropBottom,
		int panelTop,
		int panelBottom,
		int panelStroke,
		int text,
		int mutedText,
		int accent,
		int accentHot,
		int success,
		int danger
) {
	public static TritonTheme abyss() {
		return new TritonTheme(
				Color.rgba(4, 12, 28, 240),
				Color.rgba(8, 29, 61, 248),
				Color.rgba(18, 48, 94, 238),
				Color.rgba(8, 24, 52, 246),
				Color.rgba(116, 174, 255, 120),
				Color.rgba(244, 248, 255, 255),
				Color.rgba(177, 205, 238, 255),
				Color.rgba(37, 167, 255, 255),
				Color.rgba(78, 229, 255, 255),
				Color.rgba(108, 235, 178, 255),
				Color.rgba(255, 104, 133, 255)
		);
	}
}
