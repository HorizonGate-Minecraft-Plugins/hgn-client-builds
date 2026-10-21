package online.horizongate.hgn.ui.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.mixin.HgnContainerAccessor;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;

/** Restyles complex workstations without moving their native recipes, patterns or input targets. */
public final class HgnNativeContainerSkin {
	private HgnNativeContainerSkin() {}

	public static boolean active() {
		if (!HgnConfig.get().containerLayoutsEnabled()
			|| !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return false;
		var layout = HgnContainers.layout(screen);
		return layout != null && switch (layout.kind()) {
			case ANVIL, STONECUTTER, LOOM, CARTOGRAPHY, MOUNT, BEACON, CRAFTER -> true;
			default -> false;
		};
	}

	public static boolean draw(GuiGraphics graphics, String texture, int x, int y,
		float u, float v, int width, int height) {
		if (u != 0 || v != 0 || !active() || !switch (texture) {
			case "minecraft:textures/gui/container/anvil.png",
				"minecraft:textures/gui/container/stonecutter.png",
				"minecraft:textures/gui/container/loom.png",
				"minecraft:textures/gui/container/horse.png",
				"minecraft:textures/gui/container/cartography_table.png",
				"minecraft:textures/gui/container/beacon.png",
				"minecraft:textures/gui/container/crafter.png" -> true;
			default -> false;
		}) return false;
		var screen = (AbstractContainerScreen<?>) Minecraft.getInstance().screen;
		var accessor = (HgnContainerAccessor) screen;
		if (width != accessor.hgn$getImageWidth() || height != accessor.hgn$getImageHeight()) return false;
		HgnSurface.panel(graphics, x, y, width, height, false);
		HgnSurface.separator(graphics, x + 7, y + 18, width - 14, false);
		for (var slot : screen.getMenu().slots) {
			if (slot.isActive()) HgnSurface.inset(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, false);
		}
		if (texture.endsWith("stonecutter.png")) HgnSurface.inset(graphics, x + 51, y + 13, 67, 56, false);
		if (texture.endsWith("loom.png")) HgnSurface.inset(graphics, x + 59, y + 12, 58, 56, false);
		return true;
	}

	public static int readableColor(int color) {
		return active() && (color & 0xFFFFFF) == 0x404040 ? HgnPalette.TEXT : color;
	}
}
