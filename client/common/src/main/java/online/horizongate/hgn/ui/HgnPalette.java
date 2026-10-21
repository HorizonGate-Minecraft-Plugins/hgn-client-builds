package online.horizongate.hgn.ui;

import online.horizongate.hgn.HgnConfig;

/**
 * Color contract for the clean-room HGN Client interface.
 *
 * <p>Every interface surface consumes these tokens instead of owning colors.
 * Cosmetic rarity colors are deliberately absent: they belong to cosmetic
 * data, not the base client theme.</p>
 */
public final class HgnPalette {
	public static final int VOID = 0xFF03070B;
	public static final int OBSIDIAN = 0xFF050A0F;
	public static final int SURFACE = 0xBC08131C;
	public static final int SURFACE_RAISED = 0xC80B1923;
	public static final int SURFACE_HOVER = 0xD8132A36;
	public static final int SURFACE_PRESSED = 0xD0071118;
	public static final int EDGE = 0xFF203440;
	public static final int EDGE_STRONG = 0xFF3A5665;
	public static final int MENU_EDGE = 0xFF23718A;
	public static final int MENU_EDGE_ACTIVE = 0xFF40D7EF;
	public static final int CYAN = 0xFF57E2EF;
	public static final int CYAN_BRIGHT = 0xFF19E7FF;
	public static final int CYAN_SOFT = 0xFF6DEAF3;
	public static final int TEXT = 0xFFF3F7FA;
	public static final int TEXT_MUTED = 0xFFA5AEB5;
	public static final int DISABLED = 0xFF5B6870;
	public static final int SUCCESS = 0xFF58D67B;
	public static final int WARNING = 0xFFFFC552;
	public static final int DANGER = 0xFFFF5F70;
	public static final int SHADOW = 0x72000000;

	private HgnPalette() {
	}

	public static int edge() {
		return HgnConfig.get().highContrast ? CYAN_SOFT : EDGE;
	}

	public static int mutedText() {
		return HgnConfig.get().highContrast ? TEXT : TEXT_MUTED;
	}

	public static int alpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}

	public static int mix(int from, int to, float amount) {
		float t = Math.max(0.0f, Math.min(1.0f, amount));
		int a = Math.round(channel(from, 24) + (channel(to, 24) - channel(from, 24)) * t);
		int r = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * t);
		int g = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * t);
		int b = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int channel(int color, int shift) {
		return color >> shift & 0xFF;
	}
}
