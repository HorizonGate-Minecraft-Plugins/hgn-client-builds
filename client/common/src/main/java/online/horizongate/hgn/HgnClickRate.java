package online.horizongate.hgn;

/** Bounded monotonic one-second click history; never consumes game input. */
public final class HgnClickRate {
	public static final HgnClickRate INSTANCE = new HgnClickRate();
	public static final int CAPACITY = 1024;
	private final long[][] times = new long[2][CAPACITY];
	private final int[] next = new int[2];
	private final int[] size = new int[2];

	public void record(int button, long now) {
		if (button < 0 || button > 1) return;
		times[button][next[button]] = now;
		next[button] = (next[button] + 1) % CAPACITY;
		size[button] = Math.min(CAPACITY, size[button] + 1);
	}

	public int count(int button, long now) {
		if (button < 0 || button > 1) return 0;
		int count = 0;
		for (int index = 0; index < size[button]; index++) {
			long age = now - times[button][index];
			if (age >= 0 && age < 1_000_000_000L) count++;
		}
		return count;
	}

	public String label(long now) {
		int left = count(0, now);
		int right = count(1, now);
		return left + (left == CAPACITY ? "+" : "") + " | "
			+ right + (right == CAPACITY ? "+" : "") + " CPS";
	}
}
