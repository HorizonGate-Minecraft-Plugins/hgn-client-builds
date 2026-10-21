package online.horizongate.hgn.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.screen.HgnUpdateConsentScreen;

/** Owns committed handoff beyond the review screen's removal by native disconnect. */
public final class HgnUpdateRuntime {
	private static HgnUpdateTransaction active;
	private static HgnUpdateConsentScreen owner;
	private static boolean disconnectStarted;
	private static boolean showingFailure;

	private HgnUpdateRuntime() {}

	public static HgnUpdateTransaction attach(HgnUpdateConsentScreen screen, Minecraft minecraft) {
		if (active != null && active.busy()) return null;
		if (active != null) active.close();
		owner = screen;
		disconnectStarted = false;
		showingFailure = false;
		var server = minecraft.getSingleplayerServer();
		var connection = minecraft.getConnection();
		var level = minecraft.level;
		active = new HgnUpdateTransaction(HgnUpdateTransactionBridge.resolve(ClassLoader.getSystemClassLoader()), new HgnUpdateTransaction.Effects() {
			private long disconnectedEpoch = -1;
			public long epoch() { return HgnUpdateDestinations.epoch(minecraft); }
			public String unsupportedReason() {
				return server != null ? "singleplayer_unavailable"
					: minecraft.getReportingContext().hasDraftReport() ? "report_draft_pending" : "context_unsupported";
			}
			public long revision() {
				long revision = HgnUpdateDestinations.revision(minecraft);
				return server != null || minecraft.getReportingContext().hasDraftReport() || server != minecraft.getSingleplayerServer()
					|| connection != minecraft.getConnection() || level != minecraft.level ? -1 : revision;
			}
			public boolean disconnectAndVerify() {
				if (server != null || minecraft.getSingleplayerServer() != null || revision() < 0 || connection == null) return false;
				disconnectStarted = true;
				HgnCompat.disconnectForUpdate(minecraft);
				boolean detached = !connection.getConnection().isConnected()
					&& minecraft.getSingleplayerServer() == null && minecraft.getConnection() == null
					&& minecraft.level == null && minecraft.player == null;
				if (detached) disconnectedEpoch = HgnUpdateDestinations.epoch(minecraft);
				return detached && disconnectedEpoch >= 0;
			}
			public void stop() {
				if (server != null || !disconnectStarted || disconnectedEpoch < 0 || disconnectedEpoch != HgnUpdateDestinations.epoch(minecraft)
					|| minecraft.level != null || minecraft.getConnection() != null || minecraft.player != null
					|| minecraft.getSingleplayerServer() != null) throw new IllegalStateException("Disconnect unconfirmed");
				minecraft.stop();
			}
		}, minecraft::execute);
		return active;
	}

	public static void tick(Minecraft minecraft) {
		if (active == null) return;
		active.tick(System.nanoTime());
		if (disconnectStarted && active.phase() == HgnUpdateTransaction.Phase.FAILED && !showingFailure) {
			showingFailure = true;
			minecraft.setScreen(owner);
		}
	}

	public static void removed(Screen screen) {
		if (screen == owner && active != null) active.close();
	}
}
