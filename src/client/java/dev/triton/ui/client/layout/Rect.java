package dev.triton.ui.client.layout;

public record Rect(int x, int y, int width, int height) {
	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}

	public int centerX() {
		return x + width / 2;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
	}

	public Rect inset(Insets insets) {
		return new Rect(
				x + insets.left(),
				y + insets.top(),
				Math.max(0, width - insets.horizontal()),
				Math.max(0, height - insets.vertical())
		);
	}
}
