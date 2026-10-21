package online.horizongate.hgn.ui;

/** Prevents the optional second player render from competing with a slow menu. */
public final class HgnHeroPreviewPolicy {
	public static final int START_FPS = 45;
	public static final int STOP_FPS = 24;
	private static final int SLOW_SAMPLES_TO_STOP = 3;
	private boolean started;
	private boolean stoppedForScreen;
	private int slowSamples;

	public boolean shouldRender(boolean wide, boolean lowSceneQuality, int fps) {
		if (!wide || lowSceneQuality || stoppedForScreen || fps <= 0) return false;
		if (fps < STOP_FPS) {
			if (++slowSamples >= SLOW_SAMPLES_TO_STOP) stoppedForScreen = true;
			return false;
		}
		slowSamples = 0;
		if (fps >= START_FPS) started = true;
		return started;
	}
}
