package online.horizongate.hgn.ui;

import java.util.Objects;

/** Consumes a pair of activations on the same entry; switching entries breaks the pair. */
public final class HgnDoubleClick {
	private Object previous;
	private long at;

	public boolean activate(Object key, long now) {
		boolean joined = previous != null && Objects.equals(previous, key)
			&& now >= at && now - at < 250_000_000L;
		previous = joined ? null : key;
		at = now;
		return joined;
	}

	public void reset() { previous = null; }
}
