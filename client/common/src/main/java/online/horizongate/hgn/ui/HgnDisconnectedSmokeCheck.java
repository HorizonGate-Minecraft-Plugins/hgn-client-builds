package online.horizongate.hgn.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnDisconnectedTextAccessor;
import online.horizongate.hgn.screen.HgnDisconnectedScreen;

/** Detached construction/roster/geometry only; native constructors retain data without opening paths or links. */
public final class HgnDisconnectedSmokeCheck {
	private HgnDisconnectedSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		var parent = minecraft.screen;
		Component title = Component.translatable("connect.failed");
		Component reason = Component.literal("A long diagnostic reason with preserved formatting. ".repeat(80)).withStyle(ChatFormatting.AQUA)
			.append(Component.literal("\nFinal diagnostic line.").withStyle(ChatFormatting.BOLD));
		for (int options = 0; options < 4; options++) {
			Optional<Path> report = (options & 1) == 0 ? Optional.empty() : Optional.of(Path.of("hgn-detached-not-opened", "report.txt"));
			Optional<URI> link = (options & 2) == 0 ? Optional.empty() : Optional.of(URI.create("https://example.invalid/hgn-detached-not-opened"));
			var details = new DisconnectionDetails(reason, report, link);
			for (int[] size : new int[][] {{320, 180}, {427, 240}, {620, 320}, {1280, 720}}) {
				var screen = new DisconnectedScreen(parent, title, details, Component.literal("Original parent action"));
				HgnCompat.initScreen(screen, size[0], size[1]);
				var host = (HgnDisconnectedScreen.Host) screen;
				var controller = host.hgn$disconnectComposition();
				if (controller == null || !controller.owns()) throw new AssertionError("Native disconnection composition did not attach");
				if (host.hgn$disconnectDetails() != details || host.hgn$disconnectParent() != parent || screen.shouldCloseOnEsc())
					throw new AssertionError("Native disconnect details, parent or Escape semantics changed");
				if (controller.nativeReason().getMessage() != reason || !screen.getNarrationMessage().getString().contains(reason.getString()))
					throw new AssertionError("Styled reason or full native narration lost");
				var actions = controller.nativeActions();
				if (actions.size() != 1 + report.stream().count() + link.stream().count()) throw new AssertionError("Native diagnostic action lost");
				var last = actions.getLast();
				Component expectedReturn = minecraft.allowsMultiplayer() ? Component.literal("Original parent action") : Component.translatable("gui.toTitle");
				if (!last.getMessage().equals(expectedReturn)) throw new AssertionError("Restricted/native return label changed");
				screen.setFocused(last);
				HgnCompat.resizeScreen(screen, 320, 180);
				if (screen.getFocused() != last || !screen.children().containsAll(actions) || controller.pageCount() <= 1)
					throw new AssertionError("Compact resize lost focus/actions or long reason paging");
				for (var child : screen.children()) {
					if (child instanceof AbstractWidget widget && (widget.getX() < 0 || widget.getY() < 0
						|| widget.getX() + widget.getWidth() > 320 || widget.getY() + widget.getHeight() > 180))
						throw new AssertionError("Disconnection control escaped compact viewport");
				}
				var retainedReason = controller.nativeReason();
				controller.dispose();
				controller.dispose();
				if (controller.owns() || !screen.children().contains(retainedReason)
					|| !((HgnDisconnectedTextAccessor) retainedReason).hgn$disconnectMaxWidth().equals(OptionalInt.of(size[0] - 50)))
					throw new AssertionError("Original reason wrapping/registration not restored");
				int count = screen.children().size();
				controller.arrange();
				if (screen.children().size() != count) throw new AssertionError("Disposed controller changed native roster");
				host.hgn$disconnectAdd(Button.builder(Component.literal("Unknown extra"), ignored -> { throw new AssertionError("No callback may be invoked by smoke"); }).build());
				if (HgnDisconnectedScreen.attach(screen, host) != null) throw new AssertionError("Unknown native roster did not fall back");
				if (minecraft.screen != parent) throw new AssertionError("Detached disconnection changed active menu");
			}
		}
		var replacement = new DisconnectedScreen(parent, title, reason) { };
		HgnCompat.initScreen(replacement, 320, 180);
		if (((HgnDisconnectedScreen.Host) replacement).hgn$disconnectComposition() != null)
			throw new AssertionError("Third-party disconnection subclass was composed");
		HgnClient.LOGGER.info("HGN_DISCONNECTED_SMOKE_OK: detached native details, actions, styled reason, narration, resize, focus and fallback retained; no rendering, input, reporting, link/folder opening or return callbacks exercised");
	}
}
