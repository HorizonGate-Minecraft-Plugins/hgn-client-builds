package online.horizongate.hgn.ui.dashboard;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;

import java.util.LinkedHashMap;
import java.util.Map;

/** Smooth, licensed typography used only by dashboard-style client menus. */
public final class HgnTypography {
	private static final int COMPONENT_CACHE_LIMIT = 256;
	private static final Map<String, Component> COMPONENT_CACHE =
		new LinkedHashMap<>(COMPONENT_CACHE_LIMIT, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
				return size() > COMPONENT_CACHE_LIMIT;
			}
		};

	private HgnTypography() {
	}

	public static int width(Font font, String text) {
		return Math.round(font.width(component(text)) * scale());
	}

	public static int lineHeight(Font font) {
		return Math.max(5, Math.round(font.lineHeight * scale()));
	}

	public static void text(GuiGraphics graphics, Font font, String text,
		int x, int y, int color) {
		draw(graphics, font, text, x, y, scale(), color, false);
	}

	public static void text(GuiGraphics graphics, Font font, String text,
		int x, int y, float emphasis, int color) {
		draw(graphics, font, text, x, y, scale() * emphasis, color, false);
	}

	/** The Rajdhani face is reserved for HGN Client mastheads and section titles. */
	public static void heading(GuiGraphics graphics, Font font, String text,
		int x, int y, float emphasis, int color) {
		draw(graphics, font, HgnCompat.headingText(text), x, y,
			scale() * emphasis, color, false);
	}

	public static void fittedHeading(GuiGraphics graphics, Font font, String text,
		int x, int y, int availableWidth, float emphasis, int color) {
		if (availableWidth <= 0 || emphasis <= 0) return;
		Component label = HgnCompat.headingText(text);
		float fitted = Math.min(scale() * emphasis,
			availableWidth / (float) Math.max(1, font.width(label)));
		draw(graphics, font, label, x, y, fitted, color, false);
	}

	public static void centered(GuiGraphics graphics, Font font, String text,
		int centerX, int y, int color) {
		draw(graphics, font, text, centerX, y, scale(), color, true);
	}

	/** Fits the actual themed glyph metrics inside a finite logical-pixel lane. */
	public static void fitted(GuiGraphics graphics, Font font, String text,
		int x, int y, int availableWidth, float emphasis, int color) {
		if (availableWidth <= 0 || emphasis <= 0) return;
		float fitted = Math.min(emphasis, availableWidth / (float) Math.max(1, width(font, text)));
		draw(graphics, font, text, x, y, scale() * fitted, color, false);
	}

	public static void centered(GuiGraphics graphics, Font font, String text,
		int centerX, int y, float emphasis, int color) {
		draw(graphics, font, text, centerX, y, scale() * emphasis, color, true);
	}

	private static void draw(GuiGraphics graphics, Font font, String text, int x, int y,
		float scale, int color, boolean centered) {
		draw(graphics, font, component(text), x, y, scale, color, centered);
	}

	private static void draw(GuiGraphics graphics, Font font, Component label, int x, int y,
		float scale, int color, boolean centered) {
		if (scale <= 0) return;
		HgnDraw.beginScale(graphics, scale);
		int left = Math.round(x / scale) - (centered ? font.width(label) / 2 : 0);
		graphics.drawString(font, label, left, Math.round(y / scale), color, false);
		HgnDraw.endScale(graphics);
	}

	public static String trim(Font font, String text, int availableWidth) {
		if (availableWidth <= 0) return "";
		if (width(font, text) <= availableWidth) return text;
		String suffix = "...";
		if (width(font, suffix) > availableWidth) return "";
		int low = 0;
		int high = text.length();
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			if (width(font, text.substring(0, middle) + suffix) <= availableWidth) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}
		return text.substring(0, low) + suffix;
	}

	private static float scale() {
		return online.horizongate.hgn.ui.HgnTypography.scale();
	}

	private static Component component(String text) {
		// Components retain only text and a font identifier. Glyphs and metrics are
		// still resolved from the current Font after every resource reload.
		synchronized (COMPONENT_CACHE) {
			Component cached = COMPONENT_CACHE.get(text);
			if (cached != null) return cached;
			Component created = HgnCompat.uiText(text);
			COMPONENT_CACHE.put(text, created);
			return created;
		}
	}
}
