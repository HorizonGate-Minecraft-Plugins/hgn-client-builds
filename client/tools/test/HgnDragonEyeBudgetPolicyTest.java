import online.horizongate.hgn.HgnDragonEyeBudgetPolicy;

public final class HgnDragonEyeBudgetPolicyTest {
	public static void main(String[] args) {
		assertAttempts(-1, 0);
		assertAttempts(0, 0);
		assertAttempts(1, 64);
		assertAttempts(16, 64);
		assertAttempts(48, 192);
		assertAttempts(96, 256);
		assertAttempts(Integer.MAX_VALUE, 256);
		if (!HgnDragonEyeBudgetPolicy.finiteBounds(-1, 0, -1, 1, 2, 1)) {
			throw new AssertionError("Finite entity bounds must be drawable");
		}
		if (HgnDragonEyeBudgetPolicy.finiteBounds(2, 0, 0, 1, 1, 1)
			|| HgnDragonEyeBudgetPolicy.finiteBounds(Double.NaN, 0, 0, 1, 1, 1)
			|| HgnDragonEyeBudgetPolicy.finiteBounds(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1)) {
			throw new AssertionError("Malformed entity bounds must not enter the silhouette loops");
		}
		System.out.println("Dragon Eye entity sampling remains bounded; no world entities changed.");
	}

	private static void assertAttempts(int particles, int expected) {
		int actual = HgnDragonEyeBudgetPolicy.silhouetteSampleAttempts(particles);
		if (actual != expected) {
			throw new AssertionError("Unexpected sample cap for " + particles + ": " + actual);
		}
	}
}
