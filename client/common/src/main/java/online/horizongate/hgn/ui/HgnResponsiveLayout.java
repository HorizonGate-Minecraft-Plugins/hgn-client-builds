package online.horizongate.hgn.ui;

/** Responsive geometry shared by the HGN dashboard and catalogue screens. */
public record HgnResponsiveLayout(
	Mode mode,
	HgnRect screen,
	HgnRect rail,
	HgnRect content,
	HgnRect profile,
	int margin,
	int gap) {

	public enum Mode {
		WIDE,
		COMPACT,
		MINIMUM
	}

	public static HgnResponsiveLayout resolve(int width, int height) {
		HgnRect screen = new HgnRect(0, 0, width, height);
		int shortest = Math.max(1, Math.min(width, height));
		int margin = clamp(Math.round(shortest * 0.018f), 4, 14);
		int gap = clamp(Math.round(shortest * 0.014f), 3, 10);
		int usableWidth = Math.max(1, width - margin * 2);
		int usableHeight = Math.max(1, height - margin * 2);

		if (width >= 680 && height >= 380) {
			int railWidth = clamp(Math.round(usableWidth * 0.205f), 132, 248);
			int profileWidth = clamp(Math.round(usableWidth * 0.205f), 136, 254);
			int contentWidth = Math.max(260,
				usableWidth - railWidth - profileWidth - gap * 2);
			HgnRect rail = new HgnRect(margin, margin, railWidth, usableHeight);
			HgnRect content = new HgnRect(rail.right() + gap, margin,
				contentWidth, usableHeight);
			HgnRect profile = new HgnRect(content.right() + gap, margin,
				Math.max(1, width - margin - content.right() - gap), usableHeight);
			return new HgnResponsiveLayout(Mode.WIDE, screen, rail, content,
				profile, margin, gap);
		}

		boolean minimum = width < 420 || height < 260;
		int railWidth = clamp(Math.round(usableWidth * (minimum ? 0.245f : 0.225f)),
			minimum ? 82 : 96, minimum ? 108 : 138);
		HgnRect rail = new HgnRect(margin, margin, railWidth, usableHeight);
		HgnRect content = new HgnRect(rail.right() + gap, margin,
			Math.max(1, width - margin - rail.right() - gap), usableHeight);
		return new HgnResponsiveLayout(minimum ? Mode.MINIMUM : Mode.COMPACT,
			screen, rail, content, new HgnRect(width, margin, 0, usableHeight),
			margin, gap);
	}

	public boolean hasProfileColumn() {
		return mode == Mode.WIDE && profile.width() > 0;
	}

	public boolean dense() {
		return mode != Mode.WIDE;
	}

	public boolean minimum() {
		return mode == Mode.MINIMUM;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
