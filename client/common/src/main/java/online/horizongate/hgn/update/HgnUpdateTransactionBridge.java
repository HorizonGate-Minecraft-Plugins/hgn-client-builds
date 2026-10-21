package online.horizongate.hgn.update;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Optional, JDK-signature-only adapter. Old launchers remain non-actionable. */
public final class HgnUpdateTransactionBridge {
	private HgnUpdateTransactionBridge() {}

	public static HgnUpdateTransaction.Transport resolve(ClassLoader loader) {
		try {
			Class<?> helper = Class.forName("com.modrinth.theseus.rpc.HgnUpdateBridge", false, loader);
			Method offer = checked(helper.getMethod("queryOfferV2"));
			var methods = new EnumMap<HgnUpdateTransaction.Action, Method>(HgnUpdateTransaction.Action.class);
			for (var action : HgnUpdateTransaction.Action.values()) methods.put(action, checked(helper.getMethod(action.method(), String.class)));
			return new HgnUpdateTransaction.Transport() {
				public CompletionStage<String> offer() { return invoke(offer); }
				public CompletionStage<String> call(HgnUpdateTransaction.Action action, String id) { return invoke(methods.get(action), id); }
			};
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
	}

	private static Method checked(Method method) throws NoSuchMethodException {
		if (!Modifier.isStatic(method.getModifiers()) || !CompletionStage.class.isAssignableFrom(method.getReturnType())) throw new NoSuchMethodException();
		return method;
	}

	private static CompletionStage<String> invoke(Method method, Object... args) {
		try {
			if (method.invoke(null, args) instanceof CompletionStage<?> stage) return stage.thenApply(value -> value instanceof String text ? text : "");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
		return CompletableFuture.failedFuture(new IllegalStateException("Update bridge unavailable"));
	}
}
