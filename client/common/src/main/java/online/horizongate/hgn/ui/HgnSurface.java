package online.horizongate.hgn.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;

/** Scalable artwork for every visible HGN panel, control, focus, and meter. */
public final class HgnSurface {
	public static final String GATEWAY_SCENE = "textures/gui/hgn_gateway_reference_clean.png";
	private static final int SOURCE_SIZE = 256;
	private static final String CHROME = "textures/gui/chrome/";
	private static final String HUD_V3 = "textures/gui/hud_v3/";
	private static final String HUD_RACE_MODULES = HUD_V3 + "hgn_race_modules.png";
	private static final String HUD_PORTRAIT_SOCKET = CHROME + "hud_portrait_socket.png";
	private static final String HUD_CROSSHAIR = CHROME + "hud_crosshair.png";

	private HgnSurface() {
	}

	public static void screen(GuiGraphics graphics, int width, int height) {
		HgnDraw.rect(graphics, 0, 0, width, height, HgnPalette.VOID);
		float cover = Math.max(width / 1672.0f, height / 941.0f);
		int imageWidth = Math.round(1672 * cover);
		int imageHeight = Math.round(941 * cover);
		HgnDraw.textureStretch(graphics, GATEWAY_SCENE,
			(width - imageWidth) / 2, (height - imageHeight) / 2,
			imageWidth, imageHeight, 1672, 941);
		HgnDraw.gradient(graphics, 0, 0, width, height, 0x1804090C, 0x3804090C);
	}

	public static void veil(GuiGraphics graphics, int width, int height) {
		HgnDraw.gradient(graphics, 0, 0, width, height, 0x38000000, 0xA003070B);
	}

	public static void panel(GuiGraphics graphics, HgnRect bounds, boolean active) {
		panel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active);
	}

	public static void panel(GuiGraphics graphics, int x, int y,
		int width, int height, boolean active) {
		if (!valid(width, height)) return;
		frame(graphics, x, y, width, height, false, active);
	}

	public static void dashboardPanel(GuiGraphics graphics, HgnRect bounds, boolean active) {
		panel(graphics, bounds, active);
	}

	public static void dashboardInset(GuiGraphics graphics, HgnRect bounds, boolean active) {
		dashboardInset(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active);
	}

	public static void dashboardInset(GuiGraphics graphics, int x, int y,
		int width, int height, boolean active) {
		frame(graphics, x, y, width, height, true, active);
	}

	public static void inset(GuiGraphics graphics, HgnRect bounds, boolean active) {
		inset(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active);
	}

	public static void inset(GuiGraphics graphics, int x, int y,
		int width, int height, boolean active) {
		if (!valid(width, height)) return;
		frame(graphics, x, y, width, height, true, active);
	}

	public static void glass(GuiGraphics graphics, HgnRect bounds, boolean active) {
		glass(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active);
	}

	public static void glass(GuiGraphics graphics, int x, int y,
		int width, int height, boolean active) {
		if (!valid(width, height)) return;
		frame(graphics, x, y, width, height, false, active);
	}

	public static void control(GuiGraphics graphics, HgnRect bounds, float hover,
		boolean focused, boolean primary, boolean pressed, boolean disabled) {
		control(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hover,
			focused, primary, pressed, disabled);
	}

	public static void control(GuiGraphics graphics, int x, int y, int width, int height,
		float hover, boolean focused, boolean primary, boolean pressed, boolean disabled) {
		if (!valid(width, height)) return;
		boolean active = !disabled && (focused || primary || hover > 0.03f);
		frame(graphics, x, y, width, height, true, active);
		if (!disabled) {
			int cut = Math.max(2, Math.min(6, height / 6));
			HgnDraw.rect(graphics, x + width - 2, y + cut + 3,
				x + width - 1, y + height - cut, HgnPalette.CYAN_SOFT);
			for (int i = 0; i < cut; i++) {
				HgnDraw.rect(graphics, x + width - cut - 1 + i, y + height - 1 - i,
					x + width - cut + i, y + height - i, HgnPalette.CYAN_SOFT);
			}
		}
		if (disabled) {
			HgnDraw.rect(graphics, x, y, x + width, y + height, 0x8A020609);
			return;
		}
		if (pressed) HgnDraw.rect(graphics, x + 2, y + 2,
			x + width - 2, y + height - 2, 0x35000000);
		if (focused) focus(graphics, x - 2, y - 2, width + 4, height + 4);
	}

	public static void dashboardControl(GuiGraphics graphics, HgnRect bounds, float hover,
		boolean focused, boolean primary, boolean pressed, boolean disabled) {
		control(graphics, bounds, hover, focused, primary, pressed, disabled);
	}

	public static void focus(GuiGraphics graphics, HgnRect bounds) {
		focus(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void focus(GuiGraphics graphics, int x, int y, int width, int height) {
		if (!valid(width, height)) return;
		drawOutline(graphics, x, y, width, height, HgnPalette.CYAN_SOFT);
	}

	public static void separator(GuiGraphics graphics, int x, int y, int width, boolean active) {
		if (width <= 0) return;
		int edge = active ? HgnPalette.alpha(HgnPalette.CYAN, 210)
			: HgnPalette.alpha(HgnPalette.EDGE_STRONG, 180);
		HgnDraw.rect(graphics, x, y, x + width, y + 1, edge);
		if (active && width >= 8) {
			int center = x + width / 2;
			HgnDraw.rect(graphics, center - 2, y - 1, center + 2, y + 2,
				HgnPalette.CYAN_SOFT);
		}
	}

	public static void meter(GuiGraphics graphics, HgnRect bounds, float progress, int color) {
		meter(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), progress, color);
	}

	public static void meter(GuiGraphics graphics, int x, int y, int width, int height,
		float progress, int color) {
		if (!valid(width, height)) return;
		float safe = Math.max(0.0f, Math.min(1.0f, progress));
		drawChamfer(graphics, x, y, width, height, 0xFF071015, 0xFF39464D);
		int inset = height >= 5 ? 2 : 1;
		int available = Math.max(0, width - inset * 2);
		int innerWidth = Math.min(available, Math.max(0, Math.round(available * safe)));
		if (innerWidth <= 0) return;
		HgnDraw.rect(graphics, x + inset, y + inset,
			x + inset + innerWidth,
			Math.max(y + inset + 1, y + height - inset), color);
	}

	public static void hudPlayerHeader(GuiGraphics graphics, HgnRect bounds) {
		hudPlayerHeader(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudPlayerHeader(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, false, false);
	}

	public static void hudMetric(GuiGraphics graphics, HgnRect bounds) {
		hudMetric(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudMetric(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, true, false);
	}

	public static void hudTarget(GuiGraphics graphics, HgnRect bounds) {
		hudTarget(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudTarget(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, false, true);
	}

	public static void hudHotbar(GuiGraphics graphics, HgnRect bounds) {
		hudHotbar(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudHotbar(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, false, false);
	}

	public static void hudHotbarSelected(GuiGraphics graphics, HgnRect bounds) {
		hudHotbarSelected(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudHotbarSelected(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, true, true);
	}

	public static void hudHeldLabel(GuiGraphics graphics, HgnRect bounds) {
		hudHeldLabel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	public static void hudHeldLabel(GuiGraphics graphics, int x, int y, int width, int height) {
		hudFrame(graphics, x, y, width, height, true, false);
	}

	/** The always-visible HUD uses a fixed-cost bevel instead of the menu frame's row-by-row bevel. */
	private static void hudFrame(GuiGraphics graphics, int x, int y, int width, int height,
		boolean inset, boolean active) {
		if (!valid(width, height)) return;
		int cut = Math.min(2, Math.min(width, height) / 4);
		int top = inset ? 0xFC141E25 : 0xFC0D151B;
		int bottom = inset ? 0xFC0D151B : 0xFC080F14;
		int edge = HgnConfig.get().highContrast ? HgnPalette.CYAN_SOFT
			: active ? 0xFF748F98 : 0xFF465159;
		HgnDraw.gradient(graphics, x + cut, y, x + width - cut, y + height, top, bottom);
		if (cut > 0) {
			HgnDraw.gradient(graphics, x, y + cut, x + cut, y + height - cut, top, bottom);
			HgnDraw.gradient(graphics, x + width - cut, y + cut,
				x + width, y + height - cut, top, bottom);
		}
		drawOutline(graphics, x, y, width, height, edge);
		if (active) cornerAccent(graphics, x, y, width, height, true);
	}

	public static void hudPortraitSocket(GuiGraphics graphics, int x, int y, int width, int height) {
		drawTexture(graphics, HUD_PORTRAIT_SOCKET, x, y, width, height, 256, 256);
	}

	public static void hudRaceModule(GuiGraphics graphics, HgnRect bounds,
		int raceIndex, int moduleIndex) {
		hudRaceModule(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			raceIndex, moduleIndex);
	}

	public static void hudRaceModule(GuiGraphics graphics, int x, int y, int width, int height,
		int raceIndex, int moduleIndex) {
		int safeRace = Math.max(0, Math.min(5, raceIndex));
		int safeModule = Math.max(0, Math.min(4, moduleIndex));
		drawRegion(graphics, HUD_RACE_MODULES, x, y, width, height,
			safeModule * 229, safeRace * 229, 229, 229, 1145, 1374);
	}

	public static void hudCrosshair(GuiGraphics graphics, int centerX, int centerY, int size) {
		drawTexture(graphics, HUD_CROSSHAIR,
			centerX - size / 2, centerY - size / 2, size, size, SOURCE_SIZE, SOURCE_SIZE);
	}

	public static void cosmeticPreview(GuiGraphics graphics, HgnRect bounds) {
		inset(graphics, bounds, false);
	}

	public static void cosmeticCard(GuiGraphics graphics, HgnRect bounds, boolean selected) {
		inset(graphics, bounds, selected);
		cornerAccent(graphics, bounds, false);
	}

	public static void cosmeticWallet(GuiGraphics graphics, HgnRect bounds, boolean coins) {
		inset(graphics, bounds, false);
	}

	/** Crisp reference bevels stay the same thickness at every panel aspect ratio. */
	private static void frame(GuiGraphics graphics, int x, int y, int width, int height,
		boolean inset, boolean active) {
		if (!valid(width, height)) return;
		HgnDraw.beginScale(graphics, 0.5f);
		int left = x * 2;
		int top = y * 2;
		int right = (x + width) * 2;
		int bottom = (y + height) * 2;
		int cut = Math.max(2, Math.min(inset ? 8 : 14, Math.min(width, height) / 4));
		int fillTop = inset ? 0xFC141E25 : 0xFC0D151B;
		int fillBottom = inset ? 0xFC0D151B : 0xFC080F14;
		int edge = HgnConfig.get().highContrast ? HgnPalette.CYAN_SOFT
			: active ? HgnPalette.MENU_EDGE_ACTIVE : HgnPalette.MENU_EDGE;
		HgnDraw.gradient(graphics, left + cut, top, right - cut, bottom, fillTop, fillBottom);
		HgnDraw.gradient(graphics, left, top + cut, left + cut, bottom - cut, fillTop, fillBottom);
		HgnDraw.gradient(graphics, right - cut, top + cut, right, bottom - cut, fillTop, fillBottom);
		for (int row = 0; row < cut; row++) {
			int insetX = cut - row;
			HgnDraw.rect(graphics, left + insetX, top + row, left + cut, top + row + 1, fillTop);
			HgnDraw.rect(graphics, right - cut, top + row, right - insetX, top + row + 1, fillTop);
			HgnDraw.rect(graphics, left + insetX, bottom - row - 1, left + cut, bottom - row, fillBottom);
			HgnDraw.rect(graphics, right - cut, bottom - row - 1, right - insetX, bottom - row, fillBottom);
			HgnDraw.rect(graphics, left + insetX - 1, top + row, left + insetX, top + row + 1, edge);
			HgnDraw.rect(graphics, right - insetX, top + row, right - insetX + 1, top + row + 1, edge);
			HgnDraw.rect(graphics, left + insetX - 1, bottom - row - 1, left + insetX, bottom - row, edge);
			HgnDraw.rect(graphics, right - insetX, bottom - row - 1, right - insetX + 1, bottom - row, edge);
		}
		HgnDraw.rect(graphics, left + cut, top, right - cut, top + 1, edge);
		HgnDraw.rect(graphics, left + cut, bottom - 1, right - cut, bottom, edge);
		HgnDraw.rect(graphics, left, top + cut, left + 1, bottom - cut, edge);
		HgnDraw.rect(graphics, right - 1, top + cut, right, bottom - cut, edge);
		if (active && width >= 90 && height >= 45) {
			int bevel = HgnPalette.alpha(HgnPalette.CYAN_BRIGHT, 82);
			HgnDraw.rect(graphics, left + cut + 2, top + 2,
				right - cut - 2, top + 3, bevel);
			HgnDraw.rect(graphics, left + 2, top + cut + 2,
				left + 3, bottom - cut - 2, bevel);
		}
		HgnDraw.endScale(graphics);
		if (active) cornerAccent(graphics, x, y, width, height, true);
	}

	public static void cornerAccent(GuiGraphics graphics, HgnRect bounds, boolean both) {
		cornerAccent(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), both);
	}

	private static void cornerAccent(GuiGraphics graphics, int x, int y, int width, int height,
		boolean both) {
		int cut = Math.max(2, Math.min(5, Math.min(width, height) / 8));
		int color = HgnPalette.CYAN_SOFT;
		HgnDraw.rect(graphics, x + width - cut - 9, y, x + width - cut, y + 1, color);
		HgnDraw.rect(graphics, x + width - 1, y + cut, x + width, y + cut + 7, color);
		for (int i = 0; i < cut; i++) {
			HgnDraw.rect(graphics, x + width - cut + i, y + i,
				x + width - cut + i + 1, y + i + 1, color);
		}
		if (!both) return;
		HgnDraw.rect(graphics, x, y + height - cut - 7, x + 1, y + height - cut, color);
		HgnDraw.rect(graphics, x + cut, y + height - 1, x + cut + 9, y + height, color);
		for (int i = 0; i < cut; i++) {
			HgnDraw.rect(graphics, x + i, y + height - cut + i,
				x + i + 1, y + height - cut + i + 1, color);
		}
	}

	public static void wordmark(GuiGraphics graphics, int x, int y, int width, int height) {
		wordmark(graphics, x, y, width, height, true);
	}

	public static void wordmark(GuiGraphics graphics, int x, int y, int width, int height, boolean caption) {
		if (!valid(width, height)) return;
		HgnBrandMark.Geometry geometry = HgnBrandMark.layout(x, y, width, height, caption);
		HgnRect mark = geometry.mark();
		if (mark.width() >= 12 && mark.height() >= 12) {
			int halo = Math.max(1, Math.min(2, mark.width() / 24));
			for (HgnBrandMark.Stroke stroke : geometry.strokes()) {
				HgnRect bounds = stroke.bounds();
				HgnDraw.rect(graphics, Math.max(mark.x(), bounds.x() - halo),
					Math.max(mark.y(), bounds.y() - halo),
					Math.min(mark.right(), bounds.right() + halo),
					Math.min(mark.bottom(), bounds.bottom() + halo),
					HgnPalette.alpha(stroke.color(), 44));
			}
		}
		for (HgnBrandMark.Stroke stroke : geometry.strokes()) {
			HgnRect bounds = stroke.bounds();
			HgnDraw.rect(graphics, bounds.x(), bounds.y(), bounds.right(),
				bounds.bottom(), stroke.color());
		}
		if (!caption || geometry.captionTop() >= y + height) return;
		var font = Minecraft.getInstance().font;
		var label = HgnCompat.uiText("C  L  I  E  N  T");
		float scale = Math.min(width / (float) Math.max(1, font.width(label)),
			(height - (geometry.captionTop() - y)) / (float) Math.max(1, font.lineHeight));
		HgnDraw.scaledCentered(graphics, font, label, x + width / 2,
			geometry.captionTop(), scale, HgnPalette.TEXT);
	}

	private static void drawChamfer(GuiGraphics graphics, int x, int y,
		int width, int height, int fill, int edge) {
		if (!valid(width, height)) return;
		if (width < 5 || height < 5) {
			HgnDraw.rect(graphics, x, y, x + width, y + height, fill);
			drawOutline(graphics, x, y, width, height, edge);
			return;
		}
		HgnDraw.rect(graphics, x + 1, y, x + width - 1, y + height, fill);
		HgnDraw.rect(graphics, x, y + 1, x + width, y + height - 1, fill);
		drawOutline(graphics, x, y, width, height, edge);
	}

	private static void drawOutline(GuiGraphics graphics, int x, int y,
		int width, int height, int color) {
		if (!valid(width, height)) return;
		if (width < 5 || height < 5) {
			HgnDraw.rect(graphics, x, y, x + width, y + 1, color);
			HgnDraw.rect(graphics, x, y + height - 1, x + width, y + height, color);
			HgnDraw.rect(graphics, x, y, x + 1, y + height, color);
			HgnDraw.rect(graphics, x + width - 1, y, x + width, y + height, color);
			return;
		}
		HgnDraw.rect(graphics, x + 2, y, x + width - 2, y + 1, color);
		HgnDraw.rect(graphics, x + 2, y + height - 1,
			x + width - 2, y + height, color);
		HgnDraw.rect(graphics, x, y + 2, x + 1, y + height - 2, color);
		HgnDraw.rect(graphics, x + width - 1, y + 2,
			x + width, y + height - 2, color);
		HgnDraw.rect(graphics, x + 1, y + 1, x + 2, y + 2, color);
		HgnDraw.rect(graphics, x + width - 2, y + 1,
			x + width - 1, y + 2, color);
		HgnDraw.rect(graphics, x + 1, y + height - 2,
			x + 2, y + height - 1, color);
		HgnDraw.rect(graphics, x + width - 2, y + height - 2,
			x + width - 1, y + height - 1, color);
	}

	private static void drawTexture(GuiGraphics graphics, String asset, HgnRect bounds) {
		drawTexture(graphics, asset, bounds, SOURCE_SIZE, SOURCE_SIZE);
	}

	private static void drawTexture(GuiGraphics graphics, String asset, HgnRect bounds,
		int sourceWidth, int sourceHeight) {
		drawTexture(graphics, asset, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			sourceWidth, sourceHeight);
	}

	private static void drawTexture(GuiGraphics graphics, String asset,
		int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
		if (!valid(width, height)) return;
		HgnDraw.textureStretch(graphics, asset, x, y, width, height, sourceWidth, sourceHeight);
	}

	private static void drawRegion(GuiGraphics graphics, String asset, HgnRect bounds,
		int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		int textureWidth, int textureHeight) {
		drawRegion(graphics, asset, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight);
	}

	private static void drawRegion(GuiGraphics graphics, String asset,
		int x, int y, int width, int height, int sourceX, int sourceY,
		int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
		if (!valid(width, height)) return;
		HgnDraw.textureRegionStretch(graphics, asset, x, y,
			width, height, sourceX, sourceY, sourceWidth, sourceHeight,
			textureWidth, textureHeight);
	}

	private static boolean valid(int width, int height) {
		return width > 0 && height > 0;
	}
}
