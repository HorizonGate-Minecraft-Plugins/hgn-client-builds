package online.horizongate.hgn.net;

public final class HgnDungeonFreshnessTest {
	private HgnDungeonFreshnessTest() {
	}

	public static void main(String[] args) {
		long receivedAt = 10_000L;
		check(!HgnDungeons.questFresh(receivedAt, 0), "Absent quest must not be fresh");
		check(!HgnDungeons.questFresh(receivedAt - 1, receivedAt),
			"A future quest timestamp must not be fresh");
		check(HgnDungeons.questFresh(receivedAt + 59_999_999_999L, receivedAt),
			"Recent verified quest should remain visible");
		check(!HgnDungeons.questFresh(receivedAt + 60_000_000_000L, receivedAt),
			"Unrefreshed quest must expire after 60 seconds");
		check(HgnDungeons.snapshotFresh(receivedAt + 4_999_999_999L, receivedAt),
			"Recent mob snapshot should remain visible");
		check(!HgnDungeons.snapshotFresh(receivedAt + 5_000_000_000L, receivedAt),
			"Unrefreshed mob snapshot must expire after five seconds");
		check(HgnDungeons.questFresh(Long.MIN_VALUE + 20, Long.MAX_VALUE - 20),
			"A monotonic clock rollover must preserve a fresh quest");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
