package online.horizongate.hgn.ui;

/** Immutable logical-pixel rectangle used by every clean-room layout. */
public record HgnRect(int x, int y, int width, int height) {
	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}

	public int centerX() {
		return x + width / 2;
	}

	public int centerY() {
		return y + height / 2;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
	}

	public HgnRect inset(int amount) {
		int safe = Math.max(0, Math.min(amount, Math.min(width, height) / 2));
		return new HgnRect(x + safe, y + safe, width - safe * 2, height - safe * 2);
	}
}
