package online.horizongate.hgn.ui;

import java.util.List;

/** Bounded, code-drawn HorizonGate H emblem for compact and wide client headers. */
public final class HgnBrandMark {
	public record Stroke(HgnRect bounds, int color) {
	}

	public record Geometry(HgnRect mark, int captionTop, List<Stroke> strokes) {
	}

	private HgnBrandMark() {
	}

	public static Geometry layout(int x, int y, int width, int height, boolean caption) {
		if (width <= 0 || height <= 0) {
			return new Geometry(new HgnRect(x, y, 0, 0), y, List.of());
		}
		int captionHeight = caption && height >= 14
			? Math.max(6, Math.min(12, height / 4)) : 0;
		int availableHeight = height - captionHeight;
		int markHeight = Math.min(availableHeight, Math.max(1, Math.round(width / 0.84f)));
		int markWidth = Math.min(width, Math.max(1, Math.round(markHeight * 0.84f)));
		HgnRect mark = new HgnRect(x + (width - markWidth) / 2,
			y + (availableHeight - markHeight) / 2, markWidth, markHeight);
		List<Stroke> strokes = List.of(
			new Stroke(rect(mark, 8, 0, 31, 100), 0xFF0879AE),
			new Stroke(rect(mark, 69, 0, 92, 100), 0xFF0879AE),
			new Stroke(rect(mark, 27, 40, 73, 61), 0xFF068DBF),
			new Stroke(rect(mark, 15, 5, 26, 95), 0xFF36E1FF),
			new Stroke(rect(mark, 74, 5, 85, 95), 0xFF36E1FF),
			new Stroke(rect(mark, 25, 46, 75, 55), 0xFF57EFFF),
			new Stroke(rect(mark, 3, 10, 7, 90), 0xFF45D8FF),
			new Stroke(rect(mark, 93, 10, 97, 90), 0xFF45D8FF),
			new Stroke(rect(mark, 16, 8, 19, 91), 0xFFB8FCFF));
		return new Geometry(mark, y + availableHeight, strokes);
	}

	private static HgnRect rect(HgnRect mark, int left, int top, int right, int bottom) {
		int x1 = mark.x() + Math.min(mark.width() - 1,
			Math.round(mark.width() * left / 100.0f));
		int y1 = mark.y() + Math.min(mark.height() - 1,
			Math.round(mark.height() * top / 100.0f));
		int x2 = Math.max(x1 + 1, mark.x() + Math.min(mark.width(),
			Math.round(mark.width() * right / 100.0f)));
		int y2 = Math.max(y1 + 1, mark.y() + Math.min(mark.height(),
			Math.round(mark.height() * bottom / 100.0f)));
		return new HgnRect(x1, y1, x2 - x1, y2 - y1);
	}
}
