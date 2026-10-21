package online.horizongate.hgn.update;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Resolves only the launcher's existing system-loader facade; there is no fallback transport. */
public final class HgnUpdateDestinationBridge {
	private static final String HELPER = "com.modrinth.theseus.rpc.HgnUpdateBridge";

	private HgnUpdateDestinationBridge() {
	}

	public static Function<String, CompletionStage<String>> resolve(ClassLoader systemLoader) {
		try {
			Class<?> helper = Class.forName(HELPER, false, systemLoader);
			Method report = helper.getMethod("reportDestination", String.class);
			if (!Modifier.isStatic(report.getModifiers()) || !CompletionStage.class.isAssignableFrom(report.getReturnType())) return null;
			return json -> invoke(report, json);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
			return null;
		}
	}

	private static CompletionStage<String> invoke(Method report, String json) {
		try {
			Object response = report.invoke(null, json);
			if (!(response instanceof CompletionStage<?> stage)) return CompletableFuture.failedFuture(new IllegalStateException("Bridge unavailable"));
			return stage.thenApply(value -> value instanceof String text ? text : "");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
			return CompletableFuture.failedFuture(new IllegalStateException("Bridge unavailable"));
		}
	}
}
