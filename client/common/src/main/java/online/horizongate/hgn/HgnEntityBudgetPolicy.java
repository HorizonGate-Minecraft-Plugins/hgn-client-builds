package online.horizongate.hgn;

/** Pure rendering policy; it cannot mutate a world or an entity. */
public final class HgnEntityBudgetPolicy {
	public static final int CROWD_THRESHOLD = 128;
	public static final int HIGH_CROWD_THRESHOLD = 512;
	public static final int MAX_SCAN = 4096;
	public static final double DISTANCE_SQUARED = 48.0D * 48.0D;

	private HgnEntityBudgetPolicy() {
	}

	public static double cullingDistanceSquared(int clutterCount) {
		if (clutterCount > HIGH_CROWD_THRESHOLD) return 32.0D * 32.0D;
		if (clutterCount > 256) return 40.0D * 40.0D;
		return DISTANCE_SQUARED;
	}

	public static boolean skip(boolean enabled, int clutterCount, boolean eligible,
		boolean protectedEntity, double distanceSquared) {
		return enabled && clutterCount > CROWD_THRESHOLD && eligible && !protectedEntity
			&& Double.isFinite(distanceSquared)
			&& distanceSquared > cullingDistanceSquared(clutterCount);
	}
}
