package online.horizongate.hgn.ui;

/** Keeps the server-provided race HUD readable without covering Minecraft's survival lane. */
public record HgnRaceHudLayout(float scale, boolean visible) {
	public static final int HEADER_WIDTH = 156;
	public static final int HEADER_HEIGHT = 44;
	public static final int BAR_HEIGHT = 14;
	public static final int BAR_GAP = 1;
	public static final int MAX_BARS = 5;

	private static final int BAR_TOP_GAP = 3;
	private static final int BOTTOM_SAFE_AREA = 52;
	private static final float MAX_WIDTH_SHARE = 0.44f;
	private static final float MIN_READABLE_SCALE = 0.50f;

	public static HgnRaceHudLayout resolve(int width, int height, int top,
		int metricCount, float requestedScale) {
		int bars = Math.max(0, Math.min(MAX_BARS, metricCount));
		int moduleHeight = HEADER_HEIGHT + (bars == 0 ? 0
			: BAR_TOP_GAP + bars * BAR_HEIGHT + (bars - 1) * BAR_GAP);
		int freeHeight = height - BOTTOM_SAFE_AREA - top;
		if (width <= 0 || freeHeight <= 0) return new HgnRaceHudLayout(0.0f, false);
		float widthScale = width * MAX_WIDTH_SHARE / HEADER_WIDTH;
		float heightScale = freeHeight / (float) moduleHeight;
		float desired = Float.isFinite(requestedScale) ? requestedScale : 1.0f;
		float scale = Math.min(Math.max(0.0f, desired), Math.min(widthScale, heightScale));
		return new HgnRaceHudLayout(scale, scale >= MIN_READABLE_SCALE);
	}
}
