package online.horizongate.hgn.ui;

/** Settings destinations and locally persisted HGN controls, in reference-tab order. */
public enum HgnSettingsCategory {
	VIDEO("VIDEO", "VIDEO", "Video & effects", new int[] {4, 5, 7, 13}),
	AUDIO("AUDIO", "AUDIO", "Audio settings", new int[] {14}),
	CONTROLS("CONTROLS", "CTRL", "Game controls", new int[] {15}),
	INTERFACE("INTERFACE", "UI", "Interface presentation", new int[] {2, 9, 10}),
	CLIENT("CLIENT", "CLIENT", "HGN client", new int[] {0, 1, 12}),
	PERFORMANCE("PERFORMANCE", "PERF", "Performance", new int[] {8, 11}),
	NETWORK("NETWORK", "NET", "Servers & connections", new int[] {16}),
	COSMETICS("COSMETICS", "COSM", "HGN cosmetics", new int[] {17}),
	ACCESSIBILITY("ACCESSIBILITY", "ACCESS", "Accessibility", new int[] {3, 6, 18}),
	KEYBINDS("KEYBINDS", "KEYS", "Key bindings", new int[] {19});

	public static final int LOCAL_SETTING_COUNT = 13;
	public static final int VIDEO_OPTIONS = 13;
	public static final int SOUND_OPTIONS = 14;
	public static final int CONTROLS_OPTIONS = 15;
	public static final int SERVER_BROWSER = 16;
	public static final int COSMETICS_SCREEN = 17;
	public static final int ACCESSIBILITY_OPTIONS = 18;
	public static final int KEY_BINDINGS = 19;

	private final String label;
	private final String compactLabel;
	private final String title;
	private final int[] entries;

	HgnSettingsCategory(String label, String compactLabel, String title, int[] entries) {
		this.label = label;
		this.compactLabel = compactLabel;
		this.title = title;
		this.entries = entries;
	}

	public String label() {
		return label;
	}

	public String compactLabel() {
		return compactLabel;
	}

	public String title() {
		return title;
	}

	public int entryCount() {
		return entries.length;
	}

	public int entryAt(int index) {
		return entries[index];
	}

	public static boolean isLocalSetting(int entry) {
		return entry >= 0 && entry < LOCAL_SETTING_COUNT;
	}
}
