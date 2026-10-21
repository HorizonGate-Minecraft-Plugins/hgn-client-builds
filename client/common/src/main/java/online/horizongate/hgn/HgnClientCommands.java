package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnBossEvents;

import java.util.Locale;

/** Lightweight client-only commands handled before they are sent to a server. */
public final class HgnClientCommands {
	private HgnClientCommands() {
	}

	/**
	 * Handles {@code /hgn hud on|off}. The command is deliberately consumed locally
	 * so the server never needs a matching Brigadier registration.
	 *
	 * @return true when the command was handled and should not be sent to the server
	 */
	public static boolean handle(String command) {
		String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals("hgn bossdemo skeleton") || normalized.equals("hgn bossdemo titan")
			|| normalized.equals("hgn bossdemo fx")) {
			HgnBossEvents.demo(normalized.substring("hgn bossdemo ".length()));
			return true;
		}
		boolean enabled;
		if ("hgn hud on".equals(normalized)) {
			enabled = true;
		} else if ("hgn hud off".equals(normalized)) {
			enabled = false;
		} else {
			return false;
		}

		HgnConfig.get().hudEnabled = enabled;
		HgnConfig.save();
		HgnMessages.sendHudStatus(enabled);

		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.displayClientMessage(
				Component.literal("HGN HUD " + (enabled ? "enabled" : "disabled")
					+ ". VinkSMP notified."), false);
		}
		return true;
	}
}
