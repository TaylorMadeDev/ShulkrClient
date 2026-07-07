package dev.triton.ui.client.util;

public final class Color {
	private Color() {
	}

	public static int rgba(int red, int green, int blue, int alpha) {
		return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
	}

	public static int alpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (clamp(alpha) << 24);
	}

	public static int mix(int a, int b, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int ar = (a >> 16) & 255;
		int ag = (a >> 8) & 255;
		int ab = a & 255;
		int aa = (a >> 24) & 255;
		int br = (b >> 16) & 255;
		int bg = (b >> 8) & 255;
		int bb = b & 255;
		int ba = (b >> 24) & 255;
		return rgba(
				(int) (ar + (br - ar) * t),
				(int) (ag + (bg - ag) * t),
				(int) (ab + (bb - ab) * t),
				(int) (aa + (ba - aa) * t)
		);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
