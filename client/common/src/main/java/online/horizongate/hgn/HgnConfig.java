package online.horizongate.hgn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/** HGN Client settings, stored as JSON in the config folder. */
public final class HgnConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static HgnConfig instance;

	/** Master switch for the clean-room HGN Client interface. */
	public boolean interfaceEnabled = true;
	/** Keeps the saved setting name compatible with earlier Alpha clients. */
	public boolean experimentalContainerLayouts = false;
	public int containerLayoutRevision = 0;
	public boolean interfaceAnimations = true;
	public boolean reducedMotion = false;
	public boolean highContrast = false;
	public boolean sceneParticles = true;
	public SceneQuality sceneQuality = SceneQuality.BALANCED;
	public HudSize hudSize = HudSize.COMPACT;

	public boolean autoConnectAfterLaunch = false;
	public boolean confirmBeforeJoiningHgn = false;
	public boolean confirmBeforeQuitting = false;
	public boolean hudEnabled = true;
	public boolean hudPerformance = true;
	public boolean hudContext = true;
	public boolean hudCoordinates = true;
	public boolean hudFrames = true;
	public boolean experimentalHgnHotbar = false;
	/** Historical setting name retained; fresh installs reduce only distant XP visuals in heavy crowds. */
	public boolean crowdedItemRendering = true;

	public enum SceneQuality {
		LOW,
		BALANCED,
		HIGH
	}

	public enum HudSize {
		COMPACT(0.78f),
		STANDARD(0.90f),
		LARGE(1.0f);

		private final float multiplier;

		HudSize(float multiplier) {
			this.multiplier = multiplier;
		}

		public float multiplier() {
			return multiplier;
		}
	}

	public boolean containerLayoutsEnabled() {
		return false;
	}

	public static HgnConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static void load() {
		try {
			Path path = file();
			if (Files.exists(path)) {
				instance = GSON.fromJson(Files.readString(path), HgnConfig.class);
			}
		} catch (Exception e) {
			HgnClient.LOGGER.warn("Could not read HGN config, using defaults", e);
		}
		if (instance == null) {
			instance = new HgnConfig();
		}
		if (instance.sceneQuality == null) {
			instance.sceneQuality = SceneQuality.BALANCED;
		}
		if (instance.hudSize == null) {
			instance.hudSize = HudSize.COMPACT;
		}
		boolean migrated = false;
		if (!instance.interfaceEnabled) {
			instance.interfaceEnabled = true;
			migrated = true;
		}
		if (instance.containerLayoutRevision < 2 || instance.experimentalContainerLayouts) {
			instance.experimentalContainerLayouts = false;
			instance.containerLayoutRevision = 2;
			migrated = true;
		}
		if (migrated) {
			save();
		}
	}

	public static void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(get()));
		} catch (Exception e) {
			HgnClient.LOGGER.warn("Could not save HGN config", e);
		}
	}

	private static Path file() {
		return HgnPlatform.get().configDir().resolve("hgn-client.json");
	}
}
