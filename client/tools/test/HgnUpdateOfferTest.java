import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import online.horizongate.hgn.ui.HgnUpdateOfferLayout;
import online.horizongate.hgn.update.HgnUpdateOffer;
import online.horizongate.hgn.update.HgnUpdateOfferBridge;
import online.horizongate.hgn.update.HgnUpdateOfferPresenter;

public final class HgnUpdateOfferTest {
	private static int cases;
	private static final String AVAILABLE = "{\"protocol\":1,\"status\":\"available\",\"can_update\":false,\"reason\":\"restart_unavailable\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":\"1.8.6-alpha.1\",\"release_name\":\"ALPHA-V1.8.6\"}";
	private static final String CURRENT = "{\"protocol\":1,\"status\":\"current\",\"can_update\":false,\"reason\":\"no_newer_release\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":null,\"release_name\":null}";

	public static void main(String[] args) {
		check(HgnUpdateOffer.parse(AVAILABLE).status().equals("available"), "Available information");
		check(HgnUpdateOffer.parse(CURRENT).title().equals("No newer release found"), "Current is scoped, not globally latest");
		check(HgnUpdateOffer.parse(CURRENT).detail().contains("checked feed"), "Feed scope disclosed");
		check(HgnUpdateOffer.parse(AVAILABLE).narration().contains("1.8.6-alpha.1"), "Complete candidate is narrated");
		for (String invalid : new String[] {null, "", "[]", "{}", "null", "x".repeat(2049),
			AVAILABLE.replace("false", "true"), AVAILABLE.replace("false", "\"false\""),
			AVAILABLE.replace("\"protocol\":1", "\"protocol\":1.0"),
			AVAILABLE.replace("\"protocol\":1", "\"protocol\":\"1\""),
			AVAILABLE.replace("\"protocol\":1", "\"protocol\":1e0"),
			AVAILABLE.replace("\"protocol\":1", "\"protocol\":1,\"protocol\":1"),
			AVAILABLE.replace("\"protocol\"", "protocol"), AVAILABLE + "{}",
			AVAILABLE.replace("restart_unavailable", "restart_ready"),
			AVAILABLE.replace("1.8.6-alpha.1", "01.8.6"),
			AVAILABLE.replace("1.8.6-alpha.1", "1.8.6-alpha.01"),
			AVAILABLE.replace("ALPHA-V1.8.6", "https://private.invalid"),
			AVAILABLE.replace("ALPHA-V1.8.6", "a".repeat(129)),
			AVAILABLE.replace("}", ",\"private\":\"secret\"}"),
			CURRENT.replace("\"release_name\":null", "\"release_name\":false"),
			CURRENT.replace(",\"release_name\":null", "")}) {
			check(HgnUpdateOffer.parse(invalid).equals(HgnUpdateOffer.unavailable()), "Malformed response is sanitized");
		}
		for (String reason : List.of("baseline_unknown", "check_failed", "bridge_unavailable")) {
			String json = "{\"protocol\":1,\"status\":\"unavailable\",\"can_update\":false,\"reason\":\"" + reason + "\",\"current_version\":null,\"candidate_version\":null,\"release_name\":null}";
			check(HgnUpdateOffer.parse(json).reason().equals(reason), "Known unavailable reason");
		}
		check(HgnUpdateOffer.parse("{\"protocol\":1,\"status\":\"not_ready\",\"can_update\":false,\"reason\":\"session_not_running\",\"current_version\":null,\"candidate_version\":null,\"release_name\":null}").status().equals("not_ready"), "Not ready is distinct");
		lifecycle();
		bridge();
		layout();
		System.out.println("Update offers: " + cases + " schema, lifecycle, bridge and layout checks passed");
	}

	private static void lifecycle() {
		List<Runnable> dispatched = new ArrayList<>();
		List<CompletableFuture<String>> responses = new ArrayList<>();
		var presenter = new HgnUpdateOfferPresenter(() -> {
			var result = new CompletableFuture<String>();
			responses.add(result);
			return result;
		}, dispatched::add);
		presenter.check(0);
		presenter.check(1);
		check(responses.size() == 1 && presenter.pending(), "Requests are nonblocking and singleflight");
		check(presenter.offer().status().equals("checking"), "Checking is only pending state");
		responses.getFirst().complete(AVAILABLE);
		check(presenter.pending(), "Completion cannot publish off the game thread");
		dispatched.removeFirst().run();
		check(!presenter.pending() && presenter.offer().status().equals("available"), "Game thread publishes information");
		presenter.check(2);
		check(presenter.offer().currentVersion() == null, "Recheck immediately discards stale evidence");
		presenter.tick(TimeUnit.SECONDS.toNanos(16) + 2);
		check(!presenter.pending() && presenter.offer().status().equals("unavailable"), "Deadline closes pending state");
		presenter.check(TimeUnit.SECONDS.toNanos(17));
		responses.get(1).complete(AVAILABLE);
		dispatched.removeFirst().run();
		check(presenter.pending(), "Timed-out response cannot replace a newer pending check");
		responses.get(2).complete(CURRENT);
		presenter.close();
		dispatched.removeFirst().run();
		check(presenter.offer().status().equals("checking"), "Closure invalidates queued publication");
		presenter.check(TimeUnit.SECONDS.toNanos(30));
		check(responses.size() == 3 && presenter.closed(), "Closed screen cannot issue new work");
		var reopened = new HgnUpdateOfferPresenter(() -> CompletableFuture.completedFuture(CURRENT), Runnable::run);
		reopened.check(0);
		check(reopened.offer().status().equals("current"), "Replacement owns an independent result");
		for (int mode = 0; mode < 3; mode++) {
			int failure = mode;
			var unavailable = new HgnUpdateOfferPresenter(mode == 0 ? null : () -> {
				if (failure == 1) throw new IllegalStateException("private detail");
				return CompletableFuture.failedFuture(new IllegalStateException("private detail"));
			}, Runnable::run);
			unavailable.check(0);
			check(unavailable.offer().equals(HgnUpdateOffer.unavailable()) && !unavailable.pending(), "Missing or failed bridge sanitizes details");
		}
	}

	private static void bridge() {
		check(HgnUpdateOfferBridge.resolve(new ClassLoader(null) {}) == null, "No fallback transport");
		var sender = HgnUpdateOfferBridge.resolve(ClassLoader.getSystemClassLoader());
		check(sender != null, "Optional JDK facade resolves");
		com.modrinth.theseus.rpc.HgnUpdateBridge.offer = AVAILABLE;
		check(sender.get().toCompletableFuture().join().equals(AVAILABLE), "Zero-argument facade retains response");
		com.modrinth.theseus.rpc.HgnUpdateBridge.fail = true;
		check(sender.get().toCompletableFuture().isCompletedExceptionally(), "Reflection failure is bounded unavailability");
		com.modrinth.theseus.rpc.HgnUpdateBridge.fail = false;
	}

	private static void layout() {
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			var layout = HgnUpdateOfferLayout.resolve(size[0], size[1]);
			for (var bounds : List.of(layout.panel(), layout.content(), layout.check(), layout.back())) {
				check(bounds.x() >= 0 && bounds.y() >= 0 && bounds.right() <= size[0] && bounds.bottom() <= size[1], "Alert bounds stay in viewport");
			}
			check(layout.content().bottom() < layout.check().y(), "Status does not overlap actions");
			check(layout.check().right() < layout.back().x() && layout.check().height() >= 20, "Actions remain distinct and readable");
		}
	}

	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
		cases++;
	}
}
