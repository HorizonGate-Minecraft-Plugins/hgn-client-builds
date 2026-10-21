package online.horizongate.hgn.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** Keeps custom UI typography physically readable across Minecraft GUI scales. */
public final class HgnTypography {
	private HgnTypography() {
	}

	public static float scale() {
		return 1.0f;
	}

	public static int width(Font font, String text) {
		return online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, text);
	}

	public static int lineHeight(Font font) {
		return Math.max(5, Math.round(font.lineHeight * scale()));
	}

	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		online.horizongate.hgn.ui.dashboard.HgnTypography.text(graphics, font, text, x, y, color);
	}

	public static void centered(GuiGraphics graphics, Font font, String text,
		int centerX, int y, int color) {
		online.horizongate.hgn.ui.dashboard.HgnTypography.centered(graphics, font, text, centerX, y, color);
	}

	public static void centered(GuiGraphics graphics, Font font, String text,
		int centerX, int y, float emphasis, int color) {
		online.horizongate.hgn.ui.dashboard.HgnTypography.centered(graphics, font, text, centerX, y, emphasis, color);
	}

	public static String trim(Font font, String text, int availableWidth) {
		return online.horizongate.hgn.ui.dashboard.HgnTypography.trim(font, text, availableWidth);
	}
}
