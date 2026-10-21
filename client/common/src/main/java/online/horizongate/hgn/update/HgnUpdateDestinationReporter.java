package online.horizongate.hgn.update;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Serial, bounded reporting. Replies never authorize game exit, file changes or an update UI. */
public final class HgnUpdateDestinationReporter {
	private static final long HEARTBEAT = TimeUnit.SECONDS.toNanos(20);
	private static final long DEADLINE = TimeUnit.SECONDS.toNanos(6);
	private final Function<String, CompletionStage<String>> sender;
	private HgnUpdateDestination current = HgnUpdateDestination.none();
	private long revision;
	private long submittedRevision = -1;
	private long generation;
	private long submittedAt;
	private CompletableFuture<Void> pending;

	public HgnUpdateDestinationReporter(Function<String, CompletionStage<String>> sender) {
		this.sender = Objects.requireNonNull(sender);
	}

	/** Observe every lifecycle change, including A-to-B-to-A while a prior report is pending. */
	public void observe(HgnUpdateDestination destination) {
		HgnUpdateDestination next = destination == null ? HgnUpdateDestination.none() : destination;
		if (!current.sameDestination(next)) {
			current = next;
			revision++;
		}
	}

	/** Called on the game thread; never waits for a response or performs filesystem/network work. */
	public void tick(long now) {
		if (pending != null) {
			if (!pending.isDone() && now - submittedAt < DEADLINE) return;
			pending = null;
		}
		if (generation >= HgnUpdateDestination.MAX_GENERATION) return;
		if (generation > 0 && submittedRevision == revision && now - submittedAt < HEARTBEAT) return;
		submittedAt = now;
		submittedRevision = revision;
		String report = current.report(++generation);
		CompletableFuture<Void> completion = new CompletableFuture<>();
		pending = completion;
		try {
			CompletionStage<String> response = Objects.requireNonNull(sender.apply(report));
			response.whenComplete((ignored, failure) -> {
				if (failure == null) completion.complete(null);
				else completion.completeExceptionally(failure);
			});
		} catch (RuntimeException | LinkageError unavailable) {
			completion.completeExceptionally(unavailable);
		}
	}
}
