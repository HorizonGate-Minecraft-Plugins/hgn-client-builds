package online.horizongate.hgn.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.screen.HgnUpdateOfferScreen;

/** Detached controls/resize check with canned read-only replies, never the real launcher bridge. */
public final class HgnUpdateOfferSmokeCheck {
	private HgnUpdateOfferSmokeCheck() {
	}

	public static void run() {
		var parent = Minecraft.getInstance().screen;
		assertKeyboardInitialization(parent);
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			CompletableFuture<String> reply = new CompletableFuture<>();
			var screen = new HgnUpdateOfferScreen(parent, () -> reply);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
			if (widgets.size() != 2 || widgets.get(0).active || !widgets.get(1).active) throw new AssertionError("Offer must retain Back while checking");
			screen.setFocused(widgets.get(1));
			HgnCompat.resizeScreen(screen, size[0], size[1]);
			if (screen.getFocused() != widgets.get(1) || !screen.children().containsAll(widgets)) throw new AssertionError("Offer resize changed controls/focus");
			for (AbstractWidget widget : widgets) {
				if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > size[0] || widget.getY() + widget.getHeight() > size[1]) throw new AssertionError("Offer action exceeds viewport");
			}
			reply.complete("{\"protocol\":1,\"status\":\"current\",\"can_update\":false,\"reason\":\"no_newer_release\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":null,\"release_name\":null}");
			screen.tick();
			if (!widgets.get(0).active || !screen.getNarrationMessage().getString().contains("No newer release found")) throw new AssertionError("Offer result is not accessible");
			screen.removed();
			if (Minecraft.getInstance().screen != parent) throw new AssertionError("Detached offer changed the current menu");
		}
		HgnClient.LOGGER.info("HGN_UPDATE_OFFER_SMOKE_OK: detached information controls, narration and resize retained; no real offer RPC, install, save, exit or restart executed");
	}

	private static void assertKeyboardInitialization(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		InputType previousInput = minecraft.getLastInputType();
		String current = "{\"protocol\":1,\"status\":\"current\",\"can_update\":false,\"reason\":\"no_newer_release\",\"current_version\":\"1.8.5-alpha.1\",\"candidate_version\":null,\"release_name\":null}";
		var screen = new HgnUpdateOfferScreen(parent, () -> CompletableFuture.completedFuture(current));
		try {
			minecraft.setLastInputType(InputType.KEYBOARD_TAB);
			HgnCompat.initScreen(screen, 320, 180);
			assertBackFocused(screen);
		} finally {
			screen.removed();
			minecraft.setLastInputType(previousInput);
		}
	}

	private static void assertBackFocused(HgnUpdateOfferScreen screen) {
		if (!(screen.getFocused() instanceof AbstractWidget widget) || !widget.getMessage().getString().equals("Back")) throw new AssertionError("Native keyboard initialization advanced past Back");
	}
}
