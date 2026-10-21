package online.horizongate.hgn.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Clips only drawn row names; the original component, key button narration and tooltips stay intact. */
public final class HgnKeyBindsLabels {
	private HgnKeyBindsLabels() {
	}

	public static void draw(GuiGraphics graphics, Font font, Component name, int x, int y, int color, int changeButtonX) {
		int right = HgnKeyBindsLayout.labelRight(x, changeButtonX);
		if (right <= x) return;
		graphics.enableScissor(x, y, right, y + font.lineHeight);
		try {
			graphics.drawString(font, name, x, y, color);
		} finally {
			graphics.disableScissor();
		}
	}
}
