package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** An indeterminate connection surface; no timer is presented as network progress. */
public final class HgnConnectionArtwork {
	private HgnConnectionArtwork() { }

	public static void render(GuiGraphics graphics, HgnConnectionLayout layout) {
		HgnSurface.screen(graphics, graphics.guiWidth(), graphics.guiHeight());
		HgnSurface.glass(graphics, layout.frame(), true);
		var font = Minecraft.getInstance().font;
		HgnTypography.centered(graphics, font, "HGN // SERVER CONNECTION",
			layout.heading().centerX(), layout.heading().y(), HgnPalette.CYAN_SOFT);
		HgnRect lane = layout.activity();
		HgnSurface.separator(graphics, lane.x(), lane.centerY(), lane.width(), false);
		for (int offset = -10; offset <= 10; offset += 10) {
			HgnDraw.rect(graphics, lane.centerX() + offset - 2, lane.y(),
				lane.centerX() + offset + 2, lane.bottom(), HgnPalette.CYAN);
		}
		HgnTypography.centered(graphics, font,
			HgnTypography.trim(font, "Waiting for Minecraft", layout.hint().width()),
			layout.hint().centerX(), layout.hint().y(), HgnPalette.mutedText());
	}
}
