import online.horizongate.hgn.ui.HgnDoubleClick;

public final class HgnDoubleClickTest {
	public static void main(String[] args) {
		HgnDoubleClick click = new HgnDoubleClick();
		check(!click.activate("A", 0), "First click must only select");
		check(click.activate("A", 249_999_999), "Second click must join");
		check(!click.activate("A", 250_000_000), "Triple click must not join twice");
		check(!click.activate("B", 260_000_000), "Switching servers must only select");
		check(!click.activate("A", 270_000_000), "Returning to a server must start a new pair");
		check(!click.activate("A", 520_000_000), "Clicks at the timeout boundary must not join");
		click.reset();
		check(!click.activate("A", 521_000_000), "Changing pages must reset double click");
		check(!click.activate("A", 500_000_000), "Backwards timestamps must not join");
		System.out.println("Double click: first, repeated, triple, switching, timeout and reset cases passed");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
