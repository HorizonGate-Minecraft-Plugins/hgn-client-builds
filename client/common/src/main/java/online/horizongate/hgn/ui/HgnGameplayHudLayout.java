package online.horizongate.hgn.ui;

/** Reserves native boss, effect and bottom gameplay lanes before placing readouts. */
public record HgnGameplayHudLayout(HgnRect performance, HgnRect context, int raceTop, boolean readoutsVisible) {
	public static HgnGameplayHudLayout resolve(int width, int height, boolean bosses, boolean effects) {
		return resolve(width, height, bosses, effects, 1.0f);
	}

	public static HgnGameplayHudLayout resolve(int width, int height, boolean bosses, boolean effects, float scale) {
		scale = Math.max(0.78f, Math.min(1.0f, Float.isFinite(scale) ? scale : 1.0f));
		int margin = 5;
		int available = Math.max(1, (width - margin * 3) / 2);
		int top = bosses ? height / 3 + margin : margin;
		int cardHeight = (int) Math.ceil(46 * scale);
		HgnRect performance = new HgnRect(margin, top, Math.min(Math.round(100 * scale), available), cardHeight);
		int contextWidth = Math.min(Math.round(142 * scale), available);
		int contextTop = effects ? Math.max(top, 60) : top;
		HgnRect context = new HgnRect(width - margin - contextWidth, contextTop, contextWidth,
			(int) Math.ceil(46 * scale));
		boolean visible = width >= 160 && performance.bottom() <= height - 60 && context.bottom() <= height - 60;
		return new HgnGameplayHudLayout(performance, context, performance.bottom() + margin, visible);
	}
}
