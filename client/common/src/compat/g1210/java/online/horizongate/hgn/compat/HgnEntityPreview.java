package online.horizongate.hgn.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/** Safe fallback for the earliest 1.21 renderer generation. */
public final class HgnEntityPreview {
	private HgnEntityPreview() {
	}

	public static boolean render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int scale, LivingEntity entity) {
		return false;
	}
}
