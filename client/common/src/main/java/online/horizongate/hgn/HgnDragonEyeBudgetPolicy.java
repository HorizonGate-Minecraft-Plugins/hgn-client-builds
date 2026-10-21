package online.horizongate.hgn;

/** Bounded sampling for optional Dragon Eye silhouettes, independent of entity simulation. */
public final class HgnDragonEyeBudgetPolicy {
	private static final int MAX_ENTITY_SAMPLE_ATTEMPTS = 256;

	private HgnDragonEyeBudgetPolicy() {
	}

	public static int silhouetteSampleAttempts(int remainingParticles) {
		if (remainingParticles <= 0) return 0;
		return (int) Math.min(MAX_ENTITY_SAMPLE_ATTEMPTS,
			Math.max(64L, (long) remainingParticles * 4L));
	}

	public static boolean finiteBounds(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ) {
		return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
			&& Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
			&& minX <= maxX && minY <= maxY && minZ <= maxZ;
	}
}
