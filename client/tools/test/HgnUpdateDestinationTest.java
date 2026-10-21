import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import online.horizongate.hgn.update.HgnUpdateDestination;
import online.horizongate.hgn.update.HgnUpdateDestinationBridge;
import online.horizongate.hgn.update.HgnUpdateDestinationReporter;

public final class HgnUpdateDestinationTest {
	private static int cases;

	public static void main(String[] args) {
		Object world = new Object();
		Object connection = new Object();
		String none = "{\"protocol\":1,\"generation\":1,\"kind\":\"none\"}";
		check(HgnUpdateDestination.none().report(1).equals(none), "None wire shape");
		var original = HgnUpdateDestination.world(world, "my actual 世界 folder");
		check(original.report(2).equals("{\"protocol\":1,\"generation\":2,\"kind\":\"world\",\"folder\":\"my actual 世界 folder\"}"), "Exact folder wire shape");
		check(HgnUpdateDestination.server(connection, "[::1]:25566").report(3).endsWith("\"address\":\"[::1]:25566\"}"), "IPv6 endpoint preserved");
		check(HgnUpdateDestination.server(connection, "Example.invalid").report(3).contains("Example.invalid"), "Hostname spelling preserved");
		check(HgnUpdateDestination.world(world, "a\"b\\c").report(1).contains("a\\\"b\\\\c"), "JSON escaping");
		check(original.sameDestination(HgnUpdateDestination.world(world, "my actual 世界 folder")), "Same world survives dimension observations");
		check(!original.sameDestination(HgnUpdateDestination.world(new Object(), "my actual 世界 folder")), "Reopened world invalidates prior owner");
		check(!original.sameDestination(HgnUpdateDestination.world(world, "different folder")), "Changed folder invalidates owner");
		check(!HgnUpdateDestination.server(connection, "host").sameDestination(HgnUpdateDestination.server(new Object(), "host")), "Reconnected endpoint invalidates prior connection");
		check(!HgnUpdateDestination.server(connection, "host").sameDestination(HgnUpdateDestination.world(connection, "host")), "Destination kinds distinct");
		for (String invalid : new String[] {null, "", " ", "line\nbreak", "null\0byte", "\uD800", "\uDC00", "x".repeat(256)}) {
			check(HgnUpdateDestination.world(world, invalid).report(1).equals(none), "Invalid world observation clears destination");
		}
		for (String invalid : new String[] {null, "", "line\rbreak", "世界.invalid", "x".repeat(321)}) {
			check(HgnUpdateDestination.server(connection, invalid).report(1).equals(none), "Invalid server observation clears destination");
		}
		check(HgnUpdateDestination.world(null, "world").report(1).equals(none), "Missing world owner clears destination");
		check(HgnUpdateDestination.server(null, "host").report(1).equals(none), "Missing connection clears destination");
		check(!original.toString().contains("folder"), "Diagnostic string keeps destination private");
		for (long invalid : new long[] {0, -1, HgnUpdateDestination.MAX_GENERATION + 1}) {
			try {
				original.report(invalid);
				throw new AssertionError("Invalid sequence accepted");
			} catch (IllegalArgumentException expected) { cases++; }
		}
		check(original.report(HgnUpdateDestination.MAX_GENERATION).contains("9007199254740991"), "Largest exact sequence supported");
		lifecycle(original, world);
		boundedAndFailed(original);
		bridge(original);
		System.out.println("Update destination reports: " + cases + " lifecycle, privacy, ordering and bridge checks passed");
	}

	private static void lifecycle(HgnUpdateDestination original, Object world) {
		List<String> reports = new ArrayList<>();
		List<CompletableFuture<String>> responses = new ArrayList<>();
		var reporter = new HgnUpdateDestinationReporter(json -> {
			reports.add(json);
			var response = new CompletableFuture<String>();
			responses.add(response);
			return response;
		});
		reporter.observe(original);
		reporter.tick(0);
		check(reports.size() == 1 && reports.getFirst().equals(original.report(1)), "Initial report sent once");
		reporter.observe(HgnUpdateDestination.world(world, "B"));
		reporter.observe(original);
		reporter.tick(1);
		check(reports.size() == 1, "At most one pending report");
		responses.getFirst().complete("ignored non-actionable response");
		reporter.tick(2);
		check(reports.size() == 2 && reports.get(1).equals(original.report(2)), "A-B-A invalidates prior intent even while reporting");
		responses.get(1).complete("ignored");
		reporter.observe(HgnUpdateDestination.world(world, "my actual 世界 folder"));
		reporter.tick(seconds(19));
		check(reports.size() == 2, "Unchanged destination is not sent every tick");
		reporter.tick(seconds(20) + 2);
		check(reports.size() == 3 && reports.get(2).equals(original.report(3)), "Heartbeat advances report sequence");
		responses.get(2).complete("ignored");
		reporter.observe(null);
		reporter.tick(seconds(21));
		check(reports.size() == 4 && reports.get(3).equals(HgnUpdateDestination.none().report(4)), "Menu/disconnect explicitly clears destination");
	}

	private static void boundedAndFailed(HgnUpdateDestination original) {
		List<String> reports = new ArrayList<>();
		var oldResponse = new CompletableFuture<String>();
		var reporter = new HgnUpdateDestinationReporter(json -> {
			reports.add(json);
			return reports.size() == 1 ? oldResponse : CompletableFuture.completedFuture("ignored");
		});
		reporter.observe(original);
		reporter.tick(0);
		reporter.observe(HgnUpdateDestination.none());
		reporter.tick(seconds(5));
		check(reports.size() == 1, "Pending request stays serial before deadline");
		reporter.tick(seconds(6));
		check(reports.size() == 2 && reports.get(1).equals(HgnUpdateDestination.none().report(2)), "Stalled report cannot block newer observations forever");
		oldResponse.complete("late stale completion");
		reporter.tick(seconds(7));
		check(reports.size() == 2, "Late completion cannot restore earlier destination");
		for (boolean throwDirectly : new boolean[] {false, true}) {
			List<String> failures = new ArrayList<>();
			var failing = new HgnUpdateDestinationReporter(json -> {
				failures.add(json);
				if (throwDirectly) throw new IllegalStateException("mock unavailable");
				return CompletableFuture.failedFuture(new IllegalStateException("mock unavailable"));
			});
			failing.tick(0);
			failing.tick(1);
			check(failures.size() == 1, "Unavailable bridge does not spin every tick");
			failing.tick(seconds(20));
			check(failures.size() == 2 && failures.get(1).contains("\"generation\":2"), "Retry advances sequence at bounded cadence");
		}
	}

	private static void bridge(HgnUpdateDestination original) {
		check(HgnUpdateDestinationBridge.resolve(new ClassLoader(null) {}) == null, "Missing helper has no fallback transport");
		var sender = HgnUpdateDestinationBridge.resolve(ClassLoader.getSystemClassLoader());
		check(sender != null, "JDK facade resolves through supplied system loader");
		String payload = original.report(1);
		check(sender.apply(payload).toCompletableFuture().join().equals("test-only"), "Only JDK value crosses facade boundary");
		check(com.modrinth.theseus.rpc.HgnUpdateBridge.received.equals(payload), "Payload reaches exact facade method unchanged");
		com.modrinth.theseus.rpc.HgnUpdateBridge.fail = true;
		check(sender.apply(payload).toCompletableFuture().isCompletedExceptionally(), "Reflection failure is unavailable, not a game-thread crash");
		com.modrinth.theseus.rpc.HgnUpdateBridge.fail = false;
	}

	private static long seconds(long value) {
		return TimeUnit.SECONDS.toNanos(value);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
		cases++;
	}
}
