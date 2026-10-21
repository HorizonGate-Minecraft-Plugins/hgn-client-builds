package online.horizongate.hgn.cosmetics;

/**
 * Fault boundary between optional HGN cosmetics and Minecraft's player model.
 *
 * <p>A broken supplied model or an exhausted low-end GPU must never abort the
 * parent player render. After one render failure cosmetics stay disabled for
 * this client session, leaving the vanilla skin, head, armor, and cape intact.
 */
public final class HgnCosmeticRenderGuard {
	private static volatile boolean disabled;

	private HgnCosmeticRenderGuard() {
	}

	public static boolean available() {
		return !disabled;
	}

	public static void failed() {
		disabled = true;
	}
}
