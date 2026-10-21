package online.horizongate.hgn.ui;

/** The native connection status retains its own position and renderer. */
public record HgnConnectionLayout(HgnRect frame, HgnRect heading, HgnRect status,
	HgnRect activity, HgnRect hint, HgnRect cancel) {
	public static boolean supported(int width, int height) { return width >= 320 && height >= 180; }

	public static HgnConnectionLayout resolve(int width, int height) {
		if (!supported(width, height)) throw new IllegalArgumentException("Unsupported connection viewport");
		int frameWidth = Math.min(560, width - 24);
		int x = (width - frameWidth) / 2;
		int statusY = height / 2 - 50;
		int top = Math.max(8, statusY - 46);
		int cancelY = Math.min(height - 32, statusY + 100);
		int cancelWidth = Math.min(240, frameWidth - 32);
		return new HgnConnectionLayout(new HgnRect(x, top, frameWidth, cancelY + 32 - top),
			new HgnRect(x + 12, Math.max(top + 10, statusY - 24), frameWidth - 24, 9),
			new HgnRect(x + 12, statusY, frameWidth - 24, 9),
			new HgnRect(x + 24, statusY + 34, frameWidth - 48, 5),
			new HgnRect(x + 12, statusY + 58, frameWidth - 24, 9),
			new HgnRect((width - cancelWidth) / 2, cancelY, cancelWidth, 20));
	}
}
