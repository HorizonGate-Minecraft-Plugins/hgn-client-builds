package online.horizongate.hgn.ui.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;

/** Reference furnace chrome; progress comes from the server-synchronized menu. */
public final class HgnFurnaceRenderer {
	private HgnFurnaceRenderer() {
	}

	public static void render(GuiGraphics graphics, HgnContainerLayout layout,
		AbstractFurnaceMenu menu, int left, int top) {
		float scale = layout.imageWidth() / 482.0f;
		HgnDraw.beginScaleAt(graphics, scale, left, top);
		HgnSurface.panel(graphics, left, top, 482, 409, false);
		HgnSurface.glass(graphics, left + 11, top + 10, 460, 67, false);
		HgnSurface.glass(graphics, left + 20, top + 78, 442, 118, false);
		HgnSurface.glass(graphics, left + 20, top + 205, 442, 159, false);
		HgnContainerArt.module(graphics, HgnContainerLayout.Kind.FURNACE, left + 27, top + 22, 44);
		var font = Minecraft.getInstance().font;
		HgnTypography.text(graphics, font, "HGN FURNACE", left + 83, top + 34, 1.3f, HgnPalette.TEXT);
		HgnTypography.text(graphics, font, "Smelt ores into refined materials.", left + 83, top + 53, HgnPalette.TEXT_MUTED);
		HgnTypography.centered(graphics, font, "INPUT", left + 116, top + 86, HgnPalette.TEXT_MUTED);
		HgnTypography.centered(graphics, font, "FUEL", left + 116, top + 137, HgnPalette.TEXT_MUTED);
		HgnTypography.centered(graphics, font, "OUTPUT", left + 293, top + 107, HgnPalette.TEXT_MUTED);
		HgnSurface.glass(graphics, left + 356, top + 85, 98, 104, false);
		HgnTypography.text(graphics, font, "FUEL", left + 365, top + 93, HgnPalette.TEXT_MUTED);
		HgnSurface.meter(graphics, left + 365, top + 108, 12, 70, menu.getLitProgress(), HgnPalette.CYAN);
		HgnTypography.text(graphics, font, "SMELTING", left + 386, top + 117, 0.72f, HgnPalette.TEXT_MUTED);
		HgnTypography.text(graphics, font, Math.round(menu.getBurnProgress() * 100) + "%",
			left + 386, top + 130, 1.1f, HgnPalette.CYAN);
		HgnTypography.text(graphics, font, menu.isLit() ? "BURNING" : "IDLE",
			left + 386, top + 158, 0.85f, HgnPalette.TEXT_MUTED);
		for (int segment = 0; segment < 7; segment++) {
			HgnDraw.rect(graphics, left + 181 + segment * 8, top + 132,
				left + 187 + segment * 8, top + 137,
				menu.getBurnProgress() > segment / 7.0f ? HgnPalette.CYAN : 0xFF17454C);
		}
		HgnContainerArt.icon(graphics, "icon_process_arrow", left + 231, top + 123, 22);
		HgnTypography.text(graphics, font, "INVENTORY", left + 28, top + 212, HgnPalette.TEXT);
		HgnSurface.separator(graphics, left + 20, top + 373, 442, false);
		HgnTypography.centered(graphics, font, "HGN", left + 241, top + 388, 1.25f, HgnPalette.CYAN);
		HgnDraw.endScale(graphics);
		for (HgnContainerLayout.SlotPosition slot : layout.slots()) {
			HgnSurface.inset(graphics, left + slot.x() - 1, top + slot.y() - 1,
				layout.slotSize() + 2, layout.slotSize() + 2, false);
		}
	}
}
