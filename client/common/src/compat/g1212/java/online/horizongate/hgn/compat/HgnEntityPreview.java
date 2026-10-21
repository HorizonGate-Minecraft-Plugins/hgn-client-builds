package online.horizongate.hgn.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

/** Minecraft 1.21.2-1.21.5 live entity preview bridge. */
public final class HgnEntityPreview {
	private HgnEntityPreview() {
	}

	public static boolean render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int scale, LivingEntity entity) {
		try {
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left, top, right, bottom,
				scale, 0.0f, 0.0f, 0.0f, entity);
			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
