package online.horizongate.hgn.ui;

import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** Shared tooltip surface used by the version-specific vanilla hooks. */
public final class HgnTooltipArtwork {
	private HgnTooltipArtwork() {
	}

	public static void render(GuiGraphics graphics, int x, int y, int width, int height) {
		// Tooltips must fully occlude already-rendered slot items. The themed glass
		// still supplies edge detail, but never acts as the only backdrop.
		HgnDraw.rect(graphics, x - 4, y - 4,
			x + Math.max(4, width + 4), y + Math.max(4, height + 4), 0xFC02070C);
		HgnSurface.glass(graphics, x - 4, y - 4,
			Math.max(8, width + 8), Math.max(8, height + 8), true);
	}
}
