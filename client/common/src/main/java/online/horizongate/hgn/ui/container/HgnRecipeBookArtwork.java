package online.horizongate.hgn.ui.container;

import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.ui.HgnSurface;

/** Shared clean-room artwork for Mojang-backed recipe-book components. */
public final class HgnRecipeBookArtwork {
	private HgnRecipeBookArtwork() {
	}

	public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, int xOffset) {
		int x = (screenWidth - 147) / 2 - xOffset;
		int y = (screenHeight - 166) / 2;
		HgnSurface.panel(graphics, x, y, 147, 166, true);
		HgnSurface.glass(graphics, x + 7, y + 7, 133, 20, true);
		HgnSurface.glass(graphics, x + 7, y + 32, 133, 127, false);
	}
}
