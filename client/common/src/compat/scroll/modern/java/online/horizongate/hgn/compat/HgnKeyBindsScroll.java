package online.horizongate.hgn.compat;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

/** Native key-binding list scroll API for Minecraft 1.21.4 and 1.21.5. */
public final class HgnKeyBindsScroll {
	private HgnKeyBindsScroll() {
	}

	public static double amount(KeyBindsList list) {
		return list.scrollAmount();
	}
}
