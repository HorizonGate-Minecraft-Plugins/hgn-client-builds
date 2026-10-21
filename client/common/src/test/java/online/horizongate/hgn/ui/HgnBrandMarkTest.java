package online.horizongate.hgn.ui;

public final class HgnBrandMarkTest {
	private HgnBrandMarkTest() {
	}

	public static void main(String[] args) {
		for (int[] size : new int[][] {
			{ 1, 1 }, { 7, 7 }, { 28, 18 }, { 46, 29 },
			{ 54, 38 }, { 73, 49 }, { 73, 69 }, { 92, 41 }, { 100, 50 }
		}) {
			checkBounds(11, 17, size[0], size[1], false);
			checkBounds(11, 17, size[0], size[1], true);
		}
		checkShape(28, 18);
		checkShape(73, 69);
		for (int[] viewport : new int[][] { { 836, 471 }, { 1280, 720 } }) {
			HgnDashboardLayout canvas = HgnDashboardLayout.resolve(viewport[0], viewport[1]);
			HgnRect headerMark = new HgnReferenceShellLayout(canvas).wordmark();
			if (headerMark.right() >= canvas.x(108) || headerMark.bottom() > canvas.y(88)) {
				throw new AssertionError("Emblem overlaps the header or navigation");
			}
		}
		if (!HgnBrandMark.layout(0, 0, 0, 12, false).strokes().isEmpty()) {
			throw new AssertionError("Zero-width mark must not draw");
		}
	}

	private static void checkBounds(int x, int y, int width, int height, boolean caption) {
		HgnBrandMark.Geometry geometry = HgnBrandMark.layout(x, y, width, height, caption);
		HgnRect mark = geometry.mark();
		if (mark.width() <= 0 || mark.height() <= 0 || mark.x() < x || mark.y() < y
			|| mark.right() > x + width || mark.bottom() > y + height
			|| geometry.captionTop() < mark.bottom() || geometry.captionTop() > y + height) {
			throw new AssertionError("Mark or caption escaped " + width + "x" + height);
		}
		if (geometry.strokes().size() != 9) throw new AssertionError("Missing H strokes");
		for (HgnBrandMark.Stroke stroke : geometry.strokes()) {
			HgnRect box = stroke.bounds();
			if (box.width() <= 0 || box.height() <= 0 || box.x() < mark.x()
				|| box.y() < mark.y() || box.right() > mark.right()
				|| box.bottom() > mark.bottom()) {
				throw new AssertionError("Stroke escaped " + width + "x" + height);
			}
		}
	}

	private static void checkShape(int width, int height) {
		HgnBrandMark.Geometry geometry = HgnBrandMark.layout(0, 0, width, height, false);
		HgnRect mark = geometry.mark();
		int middleX = mark.centerX();
		int middleY = mark.centerY();
		int upperY = mark.y() + mark.height() / 5;
		if (covered(geometry, middleX, upperY) || !covered(geometry, middleX, middleY)
			|| !covered(geometry, mark.x() + mark.width() / 5, upperY)
			|| !covered(geometry, mark.x() + mark.width() * 4 / 5, upperY)) {
			throw new AssertionError("Brand mark lost its H silhouette");
		}
	}

	private static boolean covered(HgnBrandMark.Geometry geometry, int x, int y) {
		for (HgnBrandMark.Stroke stroke : geometry.strokes()) {
			if (stroke.bounds().contains(x, y)) return true;
		}
		return false;
	}
}
