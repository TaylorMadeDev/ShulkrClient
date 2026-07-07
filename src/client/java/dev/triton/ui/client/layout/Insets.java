package dev.triton.ui.client.layout;

public record Insets(int top, int right, int bottom, int left) {
	public static Insets all(int value) {
		return new Insets(value, value, value, value);
	}

	public static Insets symmetric(int vertical, int horizontal) {
		return new Insets(vertical, horizontal, vertical, horizontal);
	}

	public int horizontal() {
		return left + right;
	}

	public int vertical() {
		return top + bottom;
	}
}
