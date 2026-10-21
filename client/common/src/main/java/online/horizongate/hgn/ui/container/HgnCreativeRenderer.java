package online.horizongate.hgn.ui.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;

/** Native nine-by-five picker with an icon category deck and separate search/hotbar lanes. */
public final class HgnCreativeRenderer {
	private HgnCreativeRenderer() {
	}

	public static HgnRect tab(HgnContainerLayout layout, CreativeModeTab tab) {
		int index = CreativeModeTabs.tabs().indexOf(tab);
		return tabBounds(layout, index);
	}

	public static HgnRect tabBounds(HgnContainerLayout layout, int index) {
		float scale = layout.imageWidth() / 586.0f;
		return new HgnRect(Math.round((10 + Math.max(0, index) % 7 * 81) * scale),
			Math.round((4 + Math.max(0, index) / 7 * 38) * scale), Math.round(78 * scale), Math.round(34 * scale));
	}

	public static HgnRect searchBounds(HgnContainerLayout layout) {
		float scale = layout.imageWidth() / 586.0f;
		return new HgnRect(Math.round(155 * scale), Math.round(80 * scale),
			Math.round(350 * scale), Math.max(16, Math.round(18 * scale)));
	}

	public static HgnRect scrollbar(HgnContainerLayout layout) {
		float scale = layout.imageWidth() / 586.0f;
		return new HgnRect(Math.round(495 * scale), Math.round(116 * scale),
			Math.max(4, Math.round(6 * scale)), Math.round(172 * scale));
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics,
		HgnContainerLayout layout, int left, int top) {
		var minecraft = Minecraft.getInstance();
		var font = minecraft.font;
		var state = (HgnCreativeState) screen;
		boolean inventory = layout.slots().size() != 54;
		float scale = layout.imageWidth() / 586.0f;
		HgnDraw.beginScaleAt(graphics, scale, left, top);
		try {
			HgnSurface.panel(graphics, left, top, 586, 374, false);
			HgnSurface.glass(graphics, left + 85, top + 78, 421, 22, false);
			boolean searching = state.hgn$searchBox() != null && state.hgn$searchBox().visible;
			HgnTypography.fitted(graphics, font, searching ? "SEARCH" : "CATEGORY", left + 94, top + 85, 52, 0.55f, HgnPalette.CYAN);
			if (!searching && state.hgn$selectedTab() != null) {
				HgnTypography.fitted(graphics, font, state.hgn$selectedTab().getDisplayName().getString(),
					left + 155, top + 85, 340, 0.55f, HgnPalette.TEXT);
			}
			if (inventory) {
				HgnSurface.glass(graphics, left + 10, top + 101, 135, 248, false);
				HgnTypography.text(graphics, font, "EQUIPMENT", left + 18, top + 109, HgnPalette.TEXT);
			}
			int gridX = inventory ? 155 : 85;
			HgnSurface.glass(graphics, left + gridX, top + 101, 421, 190, false);
			HgnSurface.glass(graphics, left + gridX, top + 301, 421, 48, false);
			HgnTypography.text(graphics, font, "Use Minecraft's load/save hotbar keys with 1-9", left + 16, top + 359, HgnPalette.mutedText());
			HgnTypography.text(graphics, font, "ESC CLOSE", left + 510, top + 359, HgnPalette.TEXT);
		} finally {
			HgnDraw.endScale(graphics);
		}
		for (var tab : CreativeModeTabs.tabs()) {
			var rect = tab(layout, tab);
			boolean selected = state.hgn$selectedTab() == tab;
			HgnSurface.control(graphics, left + rect.x(), top + rect.y(), rect.width(), rect.height(),
				selected ? 1 : 0, selected, false, false, false);
			String label = tab.getDisplayName().getString();
			HgnDraw.item(graphics, tab.getIconItem(), left + rect.centerX() - Math.round(7 * scale),
				top + rect.y() + Math.round(3 * scale), 0.85f * scale, 0);
			float textScale = Math.min(scale * 0.54f, (rect.width() - 6) /
				(float) Math.max(1, HgnTypography.width(font, label)));
			HgnTypography.centered(graphics, font, label, left + rect.centerX(),
				top + rect.y() + Math.round(23 * scale), textScale,
				selected ? HgnPalette.CYAN : HgnPalette.TEXT);
		}
		for (var slot : layout.slots()) HgnSurface.inset(graphics, left + slot.x() - 1, top + slot.y() - 1,
			layout.slotSize() + 2, layout.slotSize() + 2, false);
		if (inventory) return;
		var track = scrollbar(layout);
		HgnSurface.inset(graphics, left + track.x(), top + track.y(), track.width(), track.height(), false);
		int thumb = Math.max(8, Math.round(15 * scale));
		int y = top + track.y() + Math.round(state.hgn$scrollOffset() * (track.height() - thumb));
		HgnSurface.focus(graphics, left + track.x(), y, track.width(), thumb);
	}
}
