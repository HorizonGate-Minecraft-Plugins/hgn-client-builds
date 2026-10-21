import online.horizongate.hgn.HgnEntityBudgetPolicy;

public final class HgnEntityBudgetPolicyTest {
	public static void main(String[] args) {
		int checks = 0;
		for (int count : new int[] {0, 127, 128, 129, 256, 257, 512, 513, 4096}) {
			double limit = count > 512 ? 1024 : count > 256 ? 1600 : 2304;
			if (HgnEntityBudgetPolicy.cullingDistanceSquared(count) != limit) {
				throw new AssertionError("Wrong adaptive distance for item/XP count " + count);
			}
			for (boolean enabled : new boolean[] {false, true}) {
				for (boolean eligible : new boolean[] {false, true}) {
					for (boolean protectedEntity : new boolean[] {false, true}) {
						for (double distance : new double[] {-1, 0, 1024, 1025, 1600, 1601,
							2304, 2305, Double.NaN, Double.POSITIVE_INFINITY}) {
							boolean expected = enabled && count > 128 && eligible && !protectedEntity
								&& Double.isFinite(distance) && distance > limit;
							if (HgnEntityBudgetPolicy.skip(enabled, count, eligible, protectedEntity, distance) != expected) {
									throw new AssertionError("Unsafe item/XP-render decision at case " + checks);
							}
							checks++;
						}
					}
				}
			}
		}
		System.out.println("Crowded item/XP rendering: " + checks
			+ " threshold, adaptive-distance, opt-out and protected-entity cases passed; no FPS claim.");
	}
}
