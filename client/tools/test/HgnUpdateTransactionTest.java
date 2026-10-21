import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import online.horizongate.hgn.update.HgnUpdateProtocol;
import online.horizongate.hgn.update.HgnUpdateTransaction;
import online.horizongate.hgn.update.HgnUpdateTransactionBridge;

public final class HgnUpdateTransactionTest {
	private static int cases;
	private static final String ID = "ea287614-54db-43ca-8bbd-ce38ed5712b5";
	private static final String OFFER = "{\"protocol\":2,\"status\":\"available\",\"can_update\":true,\"reason\":\"ready\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":\"1.8.6-alpha.1\",\"release_name\":\"ALPHA-V1.8.6\",\"offer_id\":\"" + ID + "\"}";

	public static void main(String[] args) {
		protocol(); success(); refusal(); cancellation(); deadlines(); slowDisconnect(); bridge();
		System.out.println("Update transactions: " + cases + " protocol, explicit-consent, cancellation, timeout and handoff checks passed; no game actions executed");
	}

	private static void protocol() {
		check(HgnUpdateProtocol.offer(OFFER).canUpdate(), "Exact actionable offer");
		check(!HgnUpdateProtocol.offer(OFFER.replace(ID, "00000000-0000-0000-0000-000000000000")).canUpdate(), "Nil UUID refuses authority");
		for (String malformed : new String[] { null, "", "{}", "[]", OFFER + "{}", OFFER.replace("\"protocol\":2", "\"protocol\":2,\"protocol\":2"),
			OFFER.replace("\"protocol\":2", "\"protocol\":2.0"), OFFER.replace("true", "\"true\""), OFFER.replace("true", "false"),
			OFFER.replace(ID, ID.toUpperCase()), OFFER.replace("1.8.6-alpha.1", "1.8.6-alpha.01"),
			OFFER.replace("}", ",\"url\":\"http://example.invalid\"}"), OFFER.replace("ready", "secret text") })
			check(!HgnUpdateProtocol.offer(malformed).canUpdate(), "Malformed authority refused");
		for (String status : List.of("preparing", "ready", "armed", "exit_authorized", "cancelled", "failed"))
			check(HgnUpdateProtocol.reply(reply(status), ID).status().equals(status), "Known status");
		check(HgnUpdateProtocol.reply(reply("armed"), "fa287614-54db-43ca-8bbd-ce38ed5712b5").status().equals("failed"), "Exact response identity");
		check(HgnUpdateProtocol.reply(reply("success"), ID).status().equals("failed"), "Unknown status fails closed");
	}

	private static void success() {
		Fixture f = new Fixture(); f.review();
		check(f.calls.isEmpty() && f.disconnected == 0, "Discovery never implies consent");
		f.controller.accept(1); f.controller.accept(2);
		check(f.calls.equals(List.of(HgnUpdateTransaction.Action.PREPARE)), "Explicit click is singleflight");
		f.complete("preparing"); f.controller.tick(1_000_000_001L);
		check(f.last() == HgnUpdateTransaction.Action.POLL, "Preparing polls without fabricated progress");
		f.complete("ready"); check(f.last() == HgnUpdateTransaction.Action.ARM, "Ready arms only captured consent");
		f.closeDuringDisconnect = true; f.complete("armed");
		check(f.disconnected == 1 && f.stopped == 0 && f.last() == HgnUpdateTransaction.Action.DISCONNECTED, "Native disconnect completion before ack, no early stop");
		f.complete("exit_authorized");
		check(f.stopped == 1 && f.controller.phase() == HgnUpdateTransaction.Phase.EXITING, "Exit only after exact authorization even when native screen removal closes view");
		f.controller.tick(99_000_000_000L); f.controller.cancel();
		check(f.stopped == 1, "No repeated stop or post-arm cancellation");
	}

	private static void refusal() {
		Fixture f = new Fixture(); f.review(); f.revision = 3; f.controller.accept(1);
		check(f.calls.isEmpty(), "A-B-A revision cannot accept old offer");
		f = new Fixture(); f.revision = -1; f.review(); f.controller.accept(1);
		check(f.calls.isEmpty(), "Draft or unsupported context cannot prepare");
		f = new Fixture(); f.revision = -1; f.unsupportedReason = "singleplayer_unavailable"; f.review(); f.controller.accept(1);
		check(f.calls.isEmpty() && f.disconnected == 0 && f.stopped == 0, "Singleplayer cannot prepare, disconnect or stop even when native offer is actionable");
		check(f.controller.detail().contains("Singleplayer restart is unavailable") && f.controller.detail().contains("save success cannot be confirmed"), "Singleplayer limitation is explicit rather than a durability disclaimer");
		f = new Fixture(); f.review(); f.controller.accept(60_000_000_000L);
		check(f.calls.isEmpty() && f.controller.phase() == HgnUpdateTransaction.Phase.FAILED, "Expired offer requires fresh review");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready"); f.revision++;
		f.complete("armed"); check(f.disconnected == 0 && f.stopped == 0, "Context change while arming refuses disconnect");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready"); f.controller.close(); f.complete("armed");
		check(f.disconnected == 0, "External close during arm cannot start disconnect");
		for (int mode = 0; mode < 3; mode++) {
			f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready");
			f.disconnectResult = mode == 2; f.disconnectThrows = mode == 1;
			f.complete("armed");
			if (mode == 2) f.complete("failed");
			check(f.stopped == 0 && f.controller.phase() == HgnUpdateTransaction.Phase.FAILED, "Disconnect or acknowledgment failure cannot stop JVM");
		}
		f = new Fixture(); f.controller.check(0); f.controller.close(); f.offer.complete(OFFER); f.drain();
		check(!f.controller.canAccept() && f.calls.isEmpty(), "Late offer after close cannot authorize");
	}

	private static void cancellation() {
		Fixture f = new Fixture(); f.review(); f.controller.accept(1);
		CompletableFuture<String> stale = f.replies.getFirst(); f.controller.cancel();
		check(f.last() == HgnUpdateTransaction.Action.CANCEL && f.controller.phase() == HgnUpdateTransaction.Phase.CANCELLING, "Cancellation is pending, not fabricated success");
		stale.complete(reply("ready")); f.drain();
		check(f.last() == HgnUpdateTransaction.Action.CANCEL && f.disconnected == 0, "Late ready cannot arm after cancel");
		f.complete("cancelled"); check(f.controller.phase() == HgnUpdateTransaction.Phase.CANCELLED, "Native confirms cancellation");
		f = new Fixture(); f.review(); f.controller.accept(1); f.controller.close(); f.complete("armed");
		check(f.controller.phase() == HgnUpdateTransaction.Phase.FAILED && f.disconnected == 0, "Refused cancellation never claims cancelled or disconnects");
		f = new Fixture(); f.review(); f.controller.accept(1); f.revision++; f.controller.tick(2);
		check(f.last() == HgnUpdateTransaction.Action.CANCEL, "Changing destination cancels preparation");
	}

	private static void deadlines() {
		Fixture f = new Fixture(); f.controller.check(0); f.controller.tick(21_000_000_000L);
		check(f.controller.phase() == HgnUpdateTransaction.Phase.FAILED, "Bounded offer deadline");
		f.offer.complete(OFFER); f.drain(); check(!f.controller.canAccept(), "Late timed-out offer cannot revive state");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready");
		CompletableFuture<String> late = f.replies.getLast(); f.controller.tick(16_000_000_001L);
		late.complete(reply("armed")); f.drain(); check(f.disconnected == 0, "Late arm after deadline never exits");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("preparing"); f.controller.tick(120_000_000_001L);
		check(f.last() == HgnUpdateTransaction.Action.CANCEL, "Overall preparation bounded independently from polling");
	}

	private static void bridge() {
		check(HgnUpdateTransactionBridge.resolve(new ClassLoader(null) {}) == null, "No fallback authority for old launcher");
		var bridge = HgnUpdateTransactionBridge.resolve(ClassLoader.getSystemClassLoader());
		check(bridge != null, "JDK-only v2 methods resolve");
		com.modrinth.theseus.rpc.HgnUpdateBridge.offer = OFFER;
		check(bridge.offer().toCompletableFuture().join().equals(OFFER), "Offer has no arguments");
		for (var action : HgnUpdateTransaction.Action.values()) {
			com.modrinth.theseus.rpc.HgnUpdateBridge.transaction = reply("ready");
			bridge.call(action, ID).toCompletableFuture().join();
			check(ID.equals(com.modrinth.theseus.rpc.HgnUpdateBridge.received), "Only exact UUID crosses bridge");
		}
	}

	private static void slowDisconnect() {
		Fixture f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready");
		f.clock = 40_000_000_000L;
		f.complete("armed");
		f.controller.tick(40_000_000_001L);
		check(f.controller.phase() == HgnUpdateTransaction.Phase.AUTHORIZING, "Slow native save starts a fresh acknowledgement deadline");
		f.complete("exit_authorized"); check(f.stopped == 1, "Slow save can still finish exact authorized handoff");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready"); f.complete("armed");
		f.epoch += 2; f.complete("exit_authorized");
		check(f.stopped == 0 && f.controller.phase() == HgnUpdateTransaction.Phase.FAILED, "Enter-and-leave another world while awaiting acknowledgment cannot exit on stale authority");
		f = new Fixture(); f.review(); f.controller.accept(1); f.complete("ready");
		f.revisionThrows = true; f.complete("armed");
		check(f.controller.phase() == HgnUpdateTransaction.Phase.FAILED && f.disconnected == 0, "Callback runtime failure is contained");
	}

	private static final class Fixture implements HgnUpdateTransaction.Transport, HgnUpdateTransaction.Effects {
		final List<Runnable> queue = new ArrayList<>();
		final List<HgnUpdateTransaction.Action> calls = new ArrayList<>();
		final List<CompletableFuture<String>> replies = new ArrayList<>();
		final CompletableFuture<String> offer = new CompletableFuture<>();
		long clock = 2;
		final HgnUpdateTransaction controller = new HgnUpdateTransaction(this, this, queue::add, () -> clock);
		long revision = 1;
		long epoch = 1;
		String unsupportedReason = "context_unsupported";
		int disconnected, stopped;
		boolean disconnectResult = true, disconnectThrows, closeDuringDisconnect, revisionThrows;
		public CompletionStage<String> offer() { return offer; }
		public CompletionStage<String> call(HgnUpdateTransaction.Action action, String id) {
			check(ID.equals(id), "Captured offer UUID retained"); calls.add(action);
			var result = new CompletableFuture<String>(); replies.add(result); return result;
		}
		public long revision() { if (revisionThrows) throw new IllegalStateException("private detail"); return revision; }
		public String unsupportedReason() { return unsupportedReason; }
		public long epoch() { return epoch; }
		public boolean disconnectAndVerify() {
			disconnected++; if (closeDuringDisconnect) controller.close();
			controller.tick(2);
			if (disconnectThrows) throw new IllegalStateException("private detail");
			return disconnectResult;
		}
		public void stop() { stopped++; }
		void review() { controller.check(0); offer.complete(OFFER); drain(); }
		void complete(String status) { replies.getLast().complete(reply(status)); drain(); }
		void drain() { while (!queue.isEmpty()) queue.removeFirst().run(); }
		HgnUpdateTransaction.Action last() { return calls.getLast(); }
	}
	private static String reply(String status) { return "{\"protocol\":2,\"transaction_id\":\"" + ID + "\",\"status\":\"" + status + "\",\"reason\":\"ready\"}"; }
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); cases++; }
}
