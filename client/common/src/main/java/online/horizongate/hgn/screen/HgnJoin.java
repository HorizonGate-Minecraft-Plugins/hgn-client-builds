package online.horizongate.hgn.screen;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Remembers which network backdrop the connection in progress should wear.
 *
 * <p>The actual connection remains vanilla-owned, while the HGN client presents
 * the target and connection state through its own loading view.
 */
public final class HgnJoin {
	private static volatile String pendingBackground;
	private static volatile String pendingName = "HorizonGate Network";
	private static volatile String pendingAddress = "";
	private static volatile boolean reconnecting;
	private static volatile long startedAt;

	private HgnJoin() {
	}

	/** Sets (or clears, with null) the backdrop for the next connection. */
	public static void expect(String backgroundPath) {
		pendingBackground = backgroundPath;
	}

	/** Starts a themed connection status session before vanilla opens its screen. */
	public static void connecting(String name, String address, boolean isReconnect) {
		pendingName = name == null || name.isBlank() ? "HorizonGate Network" : name;
		pendingAddress = address == null ? "" : address;
		reconnecting = isReconnect;
		startedAt = System.currentTimeMillis();
	}

	/** The backdrop to paint for {@code screen}, or null to leave it vanilla. */
	public static String backgroundFor(Screen screen) {
		return screen instanceof ConnectScreen ? pendingBackground : null;
	}

	public static boolean owns(Screen screen) {
		if (startedAt <= 0L || screen == null) return false;
		if (screen instanceof ConnectScreen) return true;
		String simpleName = screen.getClass().getSimpleName();
		return "ReceivingLevelScreen".equals(simpleName) || "LevelLoadingScreen".equals(simpleName);
	}

	public static String background() {
		return pendingBackground;
	}

	public static String targetName() {
		return pendingName;
	}

	public static String targetAddress() {
		return pendingAddress;
	}

	public static boolean reconnecting() {
		return reconnecting;
	}

	public static long elapsedMillis() {
		return Math.max(0L, System.currentTimeMillis() - startedAt);
	}

	/** Clears the temporary connection presentation once vanilla leaves its connection flow. */
	public static void observe(Screen screen) {
		if (screen instanceof ConnectScreen) {
			return;
		}
		String simpleName = screen == null ? "" : screen.getClass().getSimpleName();
		if ("ReceivingLevelScreen".equals(simpleName) || "LevelLoadingScreen".equals(simpleName)) {
			return;
		}
		startedAt = 0L;
		reconnecting = false;
	}
}
