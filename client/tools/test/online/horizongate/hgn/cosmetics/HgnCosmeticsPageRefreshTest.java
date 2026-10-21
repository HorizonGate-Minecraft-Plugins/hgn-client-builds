package online.horizongate.hgn.cosmetics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks page lifecycle deduplication and the successful-snapshot freshness gate. */
public final class HgnCosmeticsPageRefreshTest {
	public static void main(String[] args) {
		AtomicInteger requests = new AtomicInteger();
		HgnCosmeticsPageRefresh page = new HgnCosmeticsPageRefresh();
		page.requestOnce(requests::incrementAndGet);
		page.requestOnce(requests::incrementAndGet);
		page.requestOnce(requests::incrementAndGet);
		check(requests.get() == 1, "Resizing or reinitializing requested Cosmetics again");
		new HgnCosmeticsPageRefresh().requestOnce(requests::incrementAndGet);
		check(requests.get() == 2, "A new Cosmetics opening did not request a snapshot");

		long now = TimeUnit.SECONDS.toNanos(100L);
		check(!HgnCosmeticsPageRefresh.canBypassScheduledRefresh(now, 0L, 0),
			"A missing verified snapshot bypassed its scheduled refresh");
		check(!HgnCosmeticsPageRefresh.canBypassScheduledRefresh(
			now, now - TimeUnit.SECONDS.toNanos(4L), 0),
			"A recently verified snapshot caused another backend request");
		check(HgnCosmeticsPageRefresh.canBypassScheduledRefresh(
			now, now - TimeUnit.SECONDS.toNanos(5L), 0),
			"Opening Cosmetics did not refresh an older verified snapshot");
		check(!HgnCosmeticsPageRefresh.canBypassScheduledRefresh(
			now, now - TimeUnit.SECONDS.toNanos(30L), 1),
			"Opening Cosmetics ignored a failed request's retry backoff");
		check(!HgnCosmeticsPageRefresh.canBypassScheduledRefresh(now, now + 1L, 0),
			"A future snapshot timestamp bypassed the scheduled refresh");
		System.out.println("Cosmetics page refresh lifecycle and retry policy passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
