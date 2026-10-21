package online.horizongate.hgn.ui;

/** Shared reference coordinates for rendered surfaces and native widget bounds. */
public record HgnDashboardLayout(float scale, int offsetX, int offsetY) {
	public static final int WIDTH = 836;
	public static final int HEIGHT = 471;

	public static HgnDashboardLayout resolve(int width, int height) {
		float scale = Math.min(width / (float) WIDTH, height / (float) HEIGHT);
		return new HgnDashboardLayout(scale, Math.round((width - WIDTH * scale) / 2),
			Math.round((height - HEIGHT * scale) / 2));
	}

	public int x(int value) {
		return offsetX + Math.round(value * scale);
	}

	public int y(int value) {
		return offsetY + Math.round(value * scale);
	}

	public int size(int value) {
		return Math.max(1, Math.round(value * scale));
	}

	public HgnRect rect(int x, int y, int width, int height) {
		return new HgnRect(x(x), y(y), Math.max(1, x(x + width) - x(x)),
			Math.max(1, y(y + height) - y(y)));
	}

	public HgnRect rect(HgnRect reference) {
		return rect(reference.x(), reference.y(), reference.width(), reference.height());
	}

	public HgnRect profilePreviewModel() {
		return rect(696, 96, 124, 106);
	}

	public HgnRect gatewayForegroundModel() {
		return rect(215, 202, 145, 149);
	}

	public HgnRect profilePreviewLabel() {
		return rect(696, 211, 124, 14);
	}
}
