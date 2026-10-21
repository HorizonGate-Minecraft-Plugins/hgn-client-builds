package online.horizongate.hgn.update;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Screen-owned state. All publication runs on the supplied game-thread executor. */
public final class HgnUpdateOfferPresenter {
	private final Supplier<CompletionStage<String>> sender;
	private final Executor gameThread;
	private HgnUpdateOffer offer = HgnUpdateOffer.unavailable();
	private long generation;
	private long started;
	private boolean pending;
	private boolean closed;

	public HgnUpdateOfferPresenter(Supplier<CompletionStage<String>> sender, Executor gameThread) {
		this.sender = sender;
		this.gameThread = gameThread;
	}

	public void check(long now) {
		if (closed || pending || generation == Long.MAX_VALUE) return;
		long request = ++generation;
		started = now;
		pending = true;
		offer = HgnUpdateOffer.checking();
		try {
			if (sender == null) throw new IllegalStateException("Bridge unavailable");
			sender.get().whenComplete((response, failure) -> gameThread.execute(() -> {
				if (closed || !pending || generation != request) return;
				pending = false;
				offer = failure == null ? HgnUpdateOffer.parse(response) : HgnUpdateOffer.unavailable();
			}));
		} catch (RuntimeException | LinkageError unavailable) {
			pending = false;
			offer = HgnUpdateOffer.unavailable();
		}
	}

	public void tick(long now) {
		if (!closed && pending && now - started >= TimeUnit.SECONDS.toNanos(16)) {
			pending = false;
			offer = HgnUpdateOffer.unavailable();
		}
	}

	public void close() {
		closed = true;
		pending = false;
	}

	public boolean pending() { return pending; }
	public boolean closed() { return closed; }
	public HgnUpdateOffer offer() { return offer; }
}
