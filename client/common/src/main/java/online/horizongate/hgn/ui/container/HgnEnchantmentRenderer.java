package online.horizongate.hgn.ui.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.EnchantmentMenu;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.compat.HgnEnchantCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;

/** Reference enchantment cards showing only server-disclosed clues and native costs. */
public final class HgnEnchantmentRenderer {
	private HgnEnchantmentRenderer() {
	}

	public static HgnRect option(HgnContainerLayout layout, int left, int top, int index) {
		float scale = layout.imageWidth() / 482.0f;
		return new HgnRect(left + Math.round(173 * scale), top + Math.round((59 + index * 45) * scale),
			Math.round(289 * scale), Math.round(40 * scale));
	}

	public static void render(GuiGraphics graphics, HgnContainerLayout layout, EnchantmentMenu menu,
		int left, int top, int mouseX, int mouseY) {
		var minecraft = Minecraft.getInstance();
		var font = minecraft.font;
		float scale = layout.imageWidth() / 482.0f;
		HgnDraw.beginScaleAt(graphics, scale, left, top);
		try {
			HgnSurface.panel(graphics, left, top, 482, 409, false);
			HgnSurface.glass(graphics, left + 11, top + 10, 460, 26, false);
			HgnContainerArt.module(graphics, HgnContainerLayout.Kind.ENCHANTMENT, left + 18, top + 13, 20);
			HgnTypography.text(graphics, font, "ENCHANTING TABLE", left + 45, top + 20, HgnPalette.TEXT);
			HgnSurface.glass(graphics, left + 20, top + 42, 130, 112, false);
			HgnContainerArt.module(graphics, HgnContainerLayout.Kind.ENCHANTMENT, left + 35, top + 48, 100);
			HgnTypography.text(graphics, font, "CHOOSE AN ENCHANTMENT", left + 173, top + 45, HgnPalette.TEXT);
			for (int index = 0; index < 3; index++) {
				int y = top + 59 + index * 45;
				int cost = menu.costs[index];
				boolean creative = minecraft.player != null && minecraft.player.getAbilities().instabuild;
				boolean available = cost > 0 && minecraft.player != null && (creative
					|| minecraft.player.experienceLevel >= cost && menu.getSlot(1).getItem().getCount() >= index + 1);
				boolean hovered = option(layout, left, top, index).contains(mouseX, mouseY);
				HgnSurface.glass(graphics, left + 173, y, 289, 40, hovered && available);
				HgnTypography.text(graphics, font, Integer.toString(index + 1), left + 184, y + 15,
					available ? HgnPalette.CYAN : HgnPalette.mutedText());
				String clue = cost > 0 && menu.enchantClue[index] >= 0 && menu.levelClue[index] > 0
					? HgnEnchantCompat.name(menu.enchantClue[index], menu.levelClue[index]) + " ..." : "UNAVAILABLE";
				HgnTypography.text(graphics, font, HgnTypography.trim(font, clue, 243), left + 205, y + 9,
					available ? HgnPalette.TEXT : HgnPalette.mutedText());
				String detail = cost > 0 ? "Requires level " + cost + " / costs " + (index + 1) + " levels + lapis"
					: "Insert an enchantable item and lapis";
				HgnTypography.text(graphics, font, HgnTypography.trim(font, detail, 243), left + 205, y + 25,
					HgnPalette.mutedText());
			}
			HgnSurface.glass(graphics, left + 20, top + 205, 442, 159, false);
			HgnTypography.text(graphics, font, "INVENTORY", left + 28, top + 212, HgnPalette.TEXT);
			HgnSurface.separator(graphics, left + 20, top + 373, 442, false);
			HgnTypography.centered(graphics, font, "HGN", left + 241, top + 388, HgnPalette.CYAN);
		} finally {
			HgnDraw.endScale(graphics);
		}
		for (var slot : layout.slots()) {
			HgnSurface.inset(graphics, left + slot.x() - 1, top + slot.y() - 1,
				layout.slotSize() + 2, layout.slotSize() + 2, false);
		}
	}
}
