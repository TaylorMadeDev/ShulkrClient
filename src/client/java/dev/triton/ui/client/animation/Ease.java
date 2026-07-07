package dev.triton.ui.client.animation;

public final class Ease {
	private Ease() {
	}

	public static float outCubic(float value) {
		float t = clamp(value) - 1.0F;
		return t * t * t + 1.0F;
	}

	public static float inOutQuart(float value) {
		float t = clamp(value);
		return t < 0.5F ? 8.0F * t * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 4.0F) / 2.0F;
	}

	public static float clamp(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	public static float approach(float current, float target, float speed) {
		return current + (target - current) * clamp(speed);
	}
}
