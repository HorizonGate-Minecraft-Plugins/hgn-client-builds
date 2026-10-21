package online.horizongate.hgn.update;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Game-thread state machine. No path from discovery to prepare exists without accept(). */
public final class HgnUpdateTransaction {
	public enum Action {
		PREPARE("prepareUpdate"), POLL("pollUpdate"), ARM("armUpdate"), DISCONNECTED("disconnectedUpdate"), CANCEL("cancelUpdate");
		private final String method;
		Action(String method) { this.method = method; }
		public String method() { return method; }
	}
	public enum Phase { IDLE, CHECKING, REVIEW, PREPARING, ARMING, DISCONNECTING, AUTHORIZING, EXITING, CANCELLING, CANCELLED, FAILED }
	public interface Transport {
		CompletionStage<String> offer();
		CompletionStage<String> call(Action action, String id);
	}
	public interface Effects {
		/** Negative means unsupported. Must change for every observed destination transition. */
		long revision();
		default String unsupportedReason() { return "context_unsupported"; }
		/** Includes transitions back to no-world state; never resets across a handoff. */
		long epoch();
		boolean disconnectAndVerify();
		void stop();
	}
	private static final long SECOND = 1_000_000_000L;
	private final Transport transport;
	private final Effects effects;
	private final Executor gameThread;
	private final LongSupplier clock;
	private HgnUpdateProtocol.Offer offer = HgnUpdateProtocol.Offer.unavailable();
	private Phase phase = Phase.IDLE;
	private String reason = "";
	private long generation, revision, now, checkedAt, phaseStarted, callStarted, nextPoll, disconnectedEpoch;
	private boolean pending, closed;

	public HgnUpdateTransaction(Transport transport, Effects effects, Executor gameThread) {
		this(transport, effects, gameThread, System::nanoTime);
	}

	public HgnUpdateTransaction(Transport transport, Effects effects, Executor gameThread, LongSupplier clock) {
		this.transport = transport;
		this.effects = effects;
		this.gameThread = gameThread;
		this.clock = clock;
	}

	public void check(long time) {
		if (closed || busy()) return;
		now = time;
		revision = effects.revision();
		offer = HgnUpdateProtocol.Offer.unavailable();
		phase = Phase.CHECKING;
		reason = "";
		if (transport == null) { fail("bridge_unavailable"); return; }
		request(null, reply -> {
			offer = HgnUpdateProtocol.offer(reply);
			checkedAt = now;
			phase = Phase.REVIEW;
			if (revision < 0) reason = effects.unsupportedReason();
			else if (effects.revision() != revision) reason = "destination_changed";
		});
	}

	/** Only the explicit review-screen button invokes this method. */
	public void accept(long time) {
		if (!canAccept()) return;
		now = time;
		if (time - checkedAt >= 60 * SECOND) { fail("offer_expired"); return; }
		phase = Phase.PREPARING;
		phaseStarted = time;
		request(Action.PREPARE, this::prepared);
	}

	private void prepared(String json) {
		var reply = HgnUpdateProtocol.reply(json, offer.id());
		if ("preparing".equals(reply.status())) { nextPoll = now + SECOND; return; }
		if (!"ready".equals(reply.status())) { fail(reply.reason()); return; }
		if (!sameContext()) { cancel("destination_changed"); return; }
		phase = Phase.ARMING;
		phaseStarted = now;
		request(Action.ARM, this::armed);
	}

	private void armed(String json) {
		var reply = HgnUpdateProtocol.reply(json, offer.id());
		if (!"armed".equals(reply.status())) { fail(reply.reason()); return; }
		if (closed) { fail("consent_screen_closed"); return; }
		if (!sameContext()) { fail("destination_changed"); return; }
		phase = Phase.DISCONNECTING;
		try {
			if (!effects.disconnectAndVerify()) { fail("disconnect_unconfirmed"); return; }
		} catch (RuntimeException failure) { fail("disconnect_failed"); return; }
		now = clock.getAsLong();
		disconnectedEpoch = effects.epoch();
		if (disconnectedEpoch < 0) { fail("destination_changed"); return; }
		phase = Phase.AUTHORIZING;
		phaseStarted = now;
		request(Action.DISCONNECTED, response -> {
			var authorized = HgnUpdateProtocol.reply(response, offer.id());
			if (!"exit_authorized".equals(authorized.status())) { fail(authorized.reason()); return; }
			if (effects.epoch() != disconnectedEpoch) { fail("destination_changed"); return; }
			phase = Phase.EXITING;
			try { effects.stop(); } catch (RuntimeException failure) { fail("exit_failed"); }
		});
	}

	public void tick(long time) {
		now = time;
		if (phase == Phase.PREPARING && !sameContext()) { cancel("destination_changed"); return; }
		if (pending && time - callStarted >= (phase == Phase.CHECKING ? 21 : 16) * SECOND) { fail("bridge_timeout"); return; }
		if ((phase == Phase.PREPARING || phase == Phase.ARMING || phase == Phase.AUTHORIZING) && time - phaseStarted >= 120 * SECOND) {
			if (phase == Phase.PREPARING) cancel("prepare_timeout");
			else fail("transaction_timeout");
			return;
		}
		if (phase == Phase.PREPARING && !pending && time >= nextPoll) request(Action.POLL, this::prepared);
	}

	public boolean canAccept() {
		return !closed && phase == Phase.REVIEW && offer.canUpdate() && reason.isEmpty() && sameContext();
	}

	private boolean sameContext() { return revision >= 0 && effects.revision() == revision; }
	public boolean canCancel() { return phase == Phase.PREPARING; }
	public boolean committed() { return phase == Phase.ARMING || phase == Phase.DISCONNECTING || phase == Phase.AUTHORIZING || phase == Phase.EXITING; }
	public boolean busy() { return pending || canCancel() || committed() || phase == Phase.CANCELLING; }
	public boolean closed() { return closed; }
	public Phase phase() { return phase; }
	public HgnUpdateProtocol.Offer offer() { return offer; }
	public String reason() { return reason; }

	public void cancel() { if (canCancel()) cancel("user_cancelled"); }
	private void cancel(String why) {
		generation++;
		pending = false;
		phase = Phase.CANCELLING;
		reason = why;
		request(Action.CANCEL, json -> {
			var reply = HgnUpdateProtocol.reply(json, offer.id());
			if ("cancelled".equals(reply.status())) phase = Phase.CANCELLED;
			else fail("cancellation_unconfirmed");
		});
	}

	/** Screen closure revokes pre-arm continuation; committed native shutdown keeps its owner. */
	public void close() {
		if (closed) return;
		closed = true;
		if (canCancel()) cancel("screen_closed");
		else if (!committed() && phase != Phase.CANCELLING) { generation++; pending = false; }
	}

	private void fail(String why) {
		generation++;
		pending = false;
		phase = Phase.FAILED;
		reason = why;
	}

	private void request(Action action, Consumer<String> completion) {
		long token = ++generation;
		pending = true;
		callStarted = now;
		try {
			CompletionStage<String> request = action == null ? transport.offer() : transport.call(action, offer.id());
			if (request == null) throw new IllegalStateException();
			request.whenComplete((reply, failure) -> gameThread.execute(() -> {
				if (!pending || generation != token) return;
				pending = false;
				if (failure != null) fail("bridge_unavailable");
				else try { completion.accept(reply); }
				catch (RuntimeException invalidCompletion) { if (generation == token) fail("handoff_failed"); }
			}));
		} catch (RuntimeException failure) { if (generation == token) fail("bridge_unavailable"); }
	}

	public String title() {
		return switch (phase) {
			case CHECKING -> "Checking client releases";
			case PREPARING -> "Preparing verified update";
			case ARMING -> "Confirming restart handoff";
			case DISCONNECTING -> "Disconnecting from server";
			case AUTHORIZING -> "Waiting for exit authorization";
			case EXITING -> "Closing Minecraft";
			case CANCELLING -> "Requesting cancellation";
			case CANCELLED -> "Update cancelled";
			case FAILED -> "Update did not complete";
			default -> switch (offer.status()) {
				case "available" -> "A newer client release is available";
				case "current" -> "No newer release found";
				case "not_ready" -> "Launcher session is not ready";
				default -> "Release check unavailable";
			};
		};
	}

	public String detail() {
		return switch (phase) {
			case PREPARING -> "Downloading and verifying before leaving this game.";
			case ARMING, DISCONNECTING -> "Native disconnect is required. Do not force-close.";
			case AUTHORIZING, EXITING -> "The launcher will install only after this JVM exits.";
			case CANCELLING -> "The launcher has not confirmed cancellation yet.";
			case CANCELLED -> "The launcher confirmed cancellation. Minecraft stays open.";
			case FAILED -> "Automatic restart did not complete. Check the launcher. " + reason;
			case CHECKING -> "Waiting for this session's launcher check.";
			default -> "singleplayer_unavailable".equals(reason) ? "Singleplayer restart is unavailable: save success cannot be confirmed."
				: "report_draft_pending".equals(reason) ? "Resolve the pending report draft before updating."
				: !reason.isEmpty() ? "Destination changed or unsupported. Return and recheck."
				: canAccept() ? "Update and restart attempts to rejoin this multiplayer server."
				: "current".equals(offer.status()) ? "No newer compatible release in the checked feed."
				: "Restart is unavailable for this session or context.";
		};
	}
}
