package online.horizongate.hgn.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/** Safe fallback until the 1.21.11 render-state bridge is available. */
public final class HgnEntityPreview {
	private HgnEntityPreview() {
	}

	public static boolean render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int scale, LivingEntity entity) {
		return false;
	}
}
