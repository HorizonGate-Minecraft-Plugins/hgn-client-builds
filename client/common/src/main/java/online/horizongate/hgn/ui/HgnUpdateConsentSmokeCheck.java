package online.horizongate.hgn.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.components.AbstractWidget;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.screen.HgnUpdateConsentScreen;
import online.horizongate.hgn.update.HgnUpdateTransaction;

/** Detached discovery only. Any preparation, disconnect, or exit is a test failure. */
public final class HgnUpdateConsentSmokeCheck {
	private HgnUpdateConsentSmokeCheck() {}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		var parent = minecraft.screen;
		var input = minecraft.getLastInputType();
		try {
			minecraft.setLastInputType(InputType.KEYBOARD_TAB);
			for (int[] size : new int[][] {{320, 180}, {427, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
				var reply = new CompletableFuture<String>();
				var transaction = new HgnUpdateTransaction(new HgnUpdateTransaction.Transport() {
					public CompletionStage<String> offer() { return reply; }
					public CompletionStage<String> call(HgnUpdateTransaction.Action action, String id) { throw new AssertionError("Smoke must never prepare an update"); }
				}, new HgnUpdateTransaction.Effects() {
					public long revision() { return 1; }
					public long epoch() { return 1; }
					public boolean disconnectAndVerify() { throw new AssertionError("Smoke must never disconnect"); }
					public void stop() { throw new AssertionError("Smoke must never exit"); }
				}, minecraft::execute);
				var screen = new HgnUpdateConsentScreen(parent, transaction);
				HgnCompat.initScreen(screen, size[0], size[1]);
				List<AbstractWidget> widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
				if (widgets.size() != 3 || widgets.get(0).active || !widgets.get(1).active || widgets.get(2).active || screen.getFocused() != widgets.get(1)) throw new AssertionError("Checking must retain focused Back without consent");
				reply.complete("{\"protocol\":2,\"status\":\"available\",\"can_update\":true,\"reason\":\"ready\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":\"1.8.6-alpha.1\",\"release_name\":\"ALPHA-V1.8.6\",\"offer_id\":\"ea287614-54db-43ca-8bbd-ce38ed5712b5\"}");
				screen.tick();
				if (!widgets.get(0).active || !screen.getNarrationMessage().getString().contains("Restart can fail")) throw new AssertionError("Consent consequences must be accessible");
				HgnCompat.resizeScreen(screen, size[0], size[1]);
				if (!screen.children().containsAll(widgets) || screen.getFocused() != widgets.get(1)) throw new AssertionError("Resize replaced review/focus");
				for (var widget : widgets) if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > size[0] || widget.getY() + widget.getHeight() > size[1]) throw new AssertionError("Consent action outside viewport");
				screen.removed();
				if (minecraft.screen != parent || !transaction.closed()) throw new AssertionError("Detached removal changed native menu or left live consent");
			}
		} finally { minecraft.setLastInputType(input); }
		HgnClient.LOGGER.info("HGN_UPDATE_CONSENT_SMOKE_OK: detached explicit review, compact controls and focus only; no real preparation, save, disconnect, exit or restart executed");
	}
}
