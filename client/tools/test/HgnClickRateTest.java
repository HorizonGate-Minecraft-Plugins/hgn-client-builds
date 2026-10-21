import online.horizongate.hgn.HgnClickRate;

public final class HgnClickRateTest {
	public static void main(String[] args) {
		var counter = new HgnClickRate();
		if (!counter.label(0).equals("0 | 0 CPS")) throw new AssertionError("empty");
		counter.record(0, 0);
		counter.record(0, 500_000_000L);
		counter.record(1, 500_000_000L);
		counter.record(2, 500_000_000L);
		if (!counter.label(999_999_999L).equals("2 | 1 CPS")) throw new AssertionError("split");
		if (!counter.label(1_000_000_000L).equals("1 | 1 CPS")) throw new AssertionError("boundary");
		if (!counter.label(1_500_000_000L).equals("0 | 0 CPS")) throw new AssertionError("expiry");
		for (int index = 0; index < HgnClickRate.CAPACITY + 10; index++) counter.record(0, 2_000_000_000L);
		if (!counter.label(2_000_000_000L).equals("1024+ | 0 CPS")) throw new AssertionError("bounded");
		if (counter.count(0, 1_000_000_000L) != 0) throw new AssertionError("future");
		if (counter.count(-1, 2_000_000_000L) != 0) throw new AssertionError("invalid");
		System.out.println("Click rate: empty, split, boundary, expiry, bounded, future and invalid cases passed");
	}
}
