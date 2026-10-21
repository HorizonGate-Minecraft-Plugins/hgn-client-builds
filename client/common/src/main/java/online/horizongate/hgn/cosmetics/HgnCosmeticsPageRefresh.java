package online.horizongate.hgn.cosmetics;

import java.util.concurrent.TimeUnit;

/** Issues one refresh per Cosmetics screen opening and limits early repeat polls. */
public final class HgnCosmeticsPageRefresh {
	private static final long MIN_SUCCESS_AGE_NANOS = TimeUnit.SECONDS.toNanos(5L);
	private boolean requested;

	public void requestOnce(Runnable request) {
		if (requested) return;
		requested = true;
		request.run();
	}

	static boolean canBypassScheduledRefresh(long now, long lastAccepted, int failures) {
		return failures == 0 && lastAccepted != 0L && now >= lastAccepted
			&& now - lastAccepted >= MIN_SUCCESS_AGE_NANOS;
	}
}
