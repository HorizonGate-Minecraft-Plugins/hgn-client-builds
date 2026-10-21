package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import online.horizongate.hgn.HgnConfig;

/** Menu styling never changes the presentation or controls of an open inventory. */
public final class HgnMenuTheme {
	private HgnMenuTheme() {
	}

	public static boolean enabled() {
		if (!HgnConfig.get().interfaceEnabled) return false;
		Screen screen = Minecraft.getInstance().screen;
		if (screen == null || screen instanceof AbstractContainerScreen<?>) return false;
		String className = screen.getClass().getName();
		return className.startsWith("net.minecraft.")
			|| className.startsWith("online.horizongate.hgn.screen.");
	}
}
