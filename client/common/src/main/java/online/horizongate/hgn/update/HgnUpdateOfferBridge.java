package online.horizongate.hgn.update;

import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Optional JDK-only bridge on the existing authenticated launcher connection. */
public final class HgnUpdateOfferBridge {
	private HgnUpdateOfferBridge() {
	}

	public static Supplier<CompletionStage<String>> resolve(ClassLoader loader) {
		try {
			Class<?> helper = Class.forName("com.modrinth.theseus.rpc.HgnUpdateBridge", false, loader);
			var method = helper.getMethod("queryOffer");
			if (!Modifier.isStatic(method.getModifiers()) || !CompletionStage.class.isAssignableFrom(method.getReturnType())) return null;
			return () -> {
				try {
					Object value = method.invoke(null);
					if (value instanceof CompletionStage<?> stage) return stage.thenApply(reply -> reply instanceof String text ? text : "");
				} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
					return CompletableFuture.failedFuture(new IllegalStateException("Update check unavailable"));
				}
				return CompletableFuture.failedFuture(new IllegalStateException("Update check unavailable"));
			};
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
			return null;
		}
	}
}
