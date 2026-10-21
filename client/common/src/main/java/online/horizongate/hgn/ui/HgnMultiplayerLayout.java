package online.horizongate.hgn.ui;

import java.util.Locale;

/** Readable type proportions for the reference-sized multiplayer composition. */
public final class HgnMultiplayerLayout {
	public static final int COMPACT_NAVIGATION_COUNT = 5;
	public static final int WIDE_ACTION_COUNT = 7;
	private static final float MIN_TEXT_EMPHASIS = 1.25f;
	private static final float BUTTON_TEXT_EMPHASIS = 1.30f;
	private static final float BUTTON_ICON_EMPHASIS = 0.82f;

	private HgnMultiplayerLayout() {
	}

	public static float textScale(float layoutScale, float emphasis) {
		return layoutScale * Math.max(MIN_TEXT_EMPHASIS, emphasis);
	}

	public static float buttonTextScale(float layoutScale) {
		return layoutScale * BUTTON_TEXT_EMPHASIS;
	}

	public static float buttonIconScale(float layoutScale) {
		return layoutScale * BUTTON_ICON_EMPHASIS;
	}

	public static HgnRect search() {
		return new HgnRect(207, 112, 361, 26);
	}

	public static HgnRect wideAction(int index) {
		if (index < 0 || index >= WIDE_ACTION_COUNT) {
			throw new IllegalArgumentException("Invalid wide server action");
		}
		return new HgnRect(207 + index * 85, 84, 78, 21);
	}

	public static HgnRect serverCard(int index) {
		if (index < 0 || index > 1) throw new IllegalArgumentException("Invalid server index");
		return new HgnRect(207, 145 + index * 137, 365, 128);
	}

	public static HgnRect inspector() {
		return new HgnRect(584, 145, 218, 282);
	}

	public static HgnRect join() {
		return new HgnRect(599, 382, 188, 30);
	}

	public static boolean matches(String query, String... fields) {
		if (query == null || query.isBlank()) return true;
		StringBuilder searchable = new StringBuilder();
		for (String field : fields) {
			if (field != null) searchable.append(field).append(' ');
		}
		String haystack = searchable.toString().toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!haystack.contains(word)) return false;
		}
		return true;
	}

	/** Reflow before reference scaling makes labels and targets too small to use. */
	public static boolean compact(int width, int height) {
		return HgnDashboardLayout.resolve(width, height).scale() < 0.62f;
	}

	public static HgnRect compactHeader(int width) {
		return new HgnRect(8, 5, width - 16, 28);
	}

	public static HgnRect compactRefresh(int width) {
		return new HgnRect(width - 80, 8, 72, 20);
	}

	public static HgnRect compactSearch(int width) {
		return new HgnRect(115, 8, width - 203, 20);
	}

	public static HgnRect compactNavigation(int width, int index) {
		if (index < 0 || index >= COMPACT_NAVIGATION_COUNT) {
			throw new IllegalArgumentException("Invalid compact navigation index");
		}
		int cellWidth = (width - 16 - (COMPACT_NAVIGATION_COUNT - 1) * 4)
			/ COMPACT_NAVIGATION_COUNT;
		int x = 8 + index * (cellWidth + 4);
		int lastWidth = width - 8 - x;
		return new HgnRect(x, 37, index == COMPACT_NAVIGATION_COUNT - 1
			? lastWidth : cellWidth, 23);
	}

	public static String compactNavigationLabel(int index) {
		return switch (index) {
			case 0 -> "Official";
			case 1 -> "Overview";
			case 2 -> "Saved";
			case 3 -> "LAN";
			case 4 -> "Back";
			default -> throw new IllegalArgumentException("Invalid compact navigation index");
		};
	}

	public static boolean compactShowsOfficialServers(int index) {
		if (index < 0 || index > 1) {
			throw new IllegalArgumentException("Not a compact network view index");
		}
		return index == 0;
	}

	public static boolean compactNavigationSelected(int index, boolean showingOfficialServers) {
		if (index < 0 || index >= COMPACT_NAVIGATION_COUNT) {
			throw new IllegalArgumentException("Invalid compact navigation index");
		}
		return index < 2 && compactShowsOfficialServers(index) == showingOfficialServers;
	}

	public static HgnRect compactContent(int width, int height) {
		return new HgnRect(8, 65, width - 16, height - 72);
	}

	public static HgnRect compactServerCard(int width, int height, int index) {
		if (index < 0 || index > 1) throw new IllegalArgumentException("Invalid server index");
		int cardHeight = Math.min(58, Math.max(32, (height - 111) / 2));
		return new HgnRect(12, 69 + index * (cardHeight + 4), width - 24, cardHeight);
	}

	public static HgnRect compactJoin(int width, int height) {
		int buttonWidth = Math.min(140, (width - 24) / 2);
		return new HgnRect(width - 12 - buttonWidth, height - 31, buttonWidth, 24);
	}

	public static HgnRect compactExplore(int width, int height) {
		return new HgnRect(16, height - 38, width - 32, 26);
	}
}
