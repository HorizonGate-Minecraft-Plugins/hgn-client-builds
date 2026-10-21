package online.horizongate.hgn.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Stable no-scene shell shared by every client screen. */
public final class HgnScreenBackdrop {
	private HgnScreenBackdrop() {
	}

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
		HgnSurface.screen(graphics, screen.width, screen.height);
	}

	public static void renderMenuBackground(GuiGraphics graphics, int width, int height) {
		HgnSurface.screen(graphics, width, height);
	}
}
