import online.horizongate.hgn.ui.HgnRaceHudLayout;

public final class HgnRaceHudLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int[] viewport : new int[][] {{214, 120}, {320, 180}, {427, 240},
			{550, 350}, {854, 480}, {1280, 720}}) {
			for (int top : new int[] {46, 106}) {
				for (int bars = 0; bars <= HgnRaceHudLayout.MAX_BARS; bars++) {
					for (float size : new float[] {0.78f, 0.90f, 1.0f}) {
						HgnRaceHudLayout layout = HgnRaceHudLayout.resolve(viewport[0], viewport[1],
							top, bars, size);
						if (layout.visible()) {
							int naturalHeight = HgnRaceHudLayout.HEADER_HEIGHT + (bars == 0 ? 0
								: 3 + bars * HgnRaceHudLayout.BAR_HEIGHT
									+ (bars - 1) * HgnRaceHudLayout.BAR_GAP);
							if (top + Math.round(naturalHeight * layout.scale()) > viewport[1] - 52
								|| Math.round(HgnRaceHudLayout.HEADER_WIDTH * layout.scale())
								> Math.round(viewport[0] * 0.44f) + 1) {
								throw new AssertionError("Race HUD crossed native survival lane: " + layout);
							}
						}
						cases++;
					}
				}
			}
		}
		var compact = HgnRaceHudLayout.resolve(320, 180, 46, 5, 0.78f);
		if (!compact.visible() || compact.scale() < 0.60f) {
			throw new AssertionError("Compact race HUD still unreadably small: " + compact);
		}
		var roomy = HgnRaceHudLayout.resolve(550, 350, 46, 5, 0.78f);
		if (!roomy.visible() || Math.abs(roomy.scale() - 0.78f) > 0.001f) {
			throw new AssertionError("Roomy race HUD did not retain requested size: " + roomy);
		}
		var crowded = HgnRaceHudLayout.resolve(320, 180, 106, 5, 0.78f);
		if (crowded.visible()) throw new AssertionError("Race HUD covered hotbar under boss bars");
		System.out.println("Race HUD layout: " + cases + " cases passed");
	}
}
