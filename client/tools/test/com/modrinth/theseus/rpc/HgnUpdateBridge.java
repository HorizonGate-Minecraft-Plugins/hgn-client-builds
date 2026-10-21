package com.modrinth.theseus.rpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test-only facade. Not part of main output or any distributed client jar. */
public final class HgnUpdateBridge {
	public static String received;
	public static boolean fail;
	public static String offer;
	public static String method;
	public static String transaction;

	public static CompletableFuture<String> queryOfferV2() { return queryOffer(); }
	public static CompletableFuture<String> prepareUpdate(String id) { return action("prepare", id); }
	public static CompletableFuture<String> pollUpdate(String id) { return action("poll", id); }
	public static CompletableFuture<String> armUpdate(String id) { return action("arm", id); }
	public static CompletableFuture<String> disconnectedUpdate(String id) { return action("disconnected", id); }
	public static CompletableFuture<String> cancelUpdate(String id) { return action("cancel", id); }
	private static CompletableFuture<String> action(String action, String id) {
		method = action; received = id;
		if (fail) throw new IllegalStateException("Test unavailable");
		return CompletableFuture.completedFuture(transaction);
	}

	public static CompletableFuture<String> queryOffer() {
		if (fail) throw new IllegalStateException("Test unavailable");
		return CompletableFuture.completedFuture(offer);
	}

	public static CompletionStage<String> reportDestination(String json) {
		if (fail) throw new IllegalStateException("Test unavailable");
		received = json;
		return CompletableFuture.completedFuture("test-only");
	}
}
