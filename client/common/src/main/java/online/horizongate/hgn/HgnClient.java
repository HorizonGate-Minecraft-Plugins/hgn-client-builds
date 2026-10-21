package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The official HorizonGate Network Minecraft client.
 * Installed, updated, repaired and launched through the HorizonGate Network Launcher.
 */
public final class HgnClient {
	public static final Logger LOGGER = LoggerFactory.getLogger("hgn-client");

	/** Matches the launcher's version; both are stamped from the same source. */
	public static final String CLIENT_VERSION = HgnBuild.CLIENT_VERSION;

	/** Canonical human-visible release name; machine protocols keep using {@link #CLIENT_VERSION}. */
	public static final String RELEASE_NAME = HgnBuild.RELEASE_NAME;

	/** Development-only: opens one screen straight away for visual checks. Empty in production. */
	public static final String DEV_SCREEN = System.getProperty("hgn.devscreen", "").trim();

	public static final String HUB_NAME = "HorizonGate Network Hub";
	public static final String HUB_ADDRESS = "hub.horizongate.online";
	public static final String VINKSMP_NAME = "VinkSMP";
	public static final String VINKSMP_ADDRESS = "vinksmp.horizongate.online";

	public static final String DISCORD_URL = "https://discord.gg/ctj5TAbUsX";

	private HgnClient() {
	}

	/** Minecraft version this build was compiled against, filled in at build time. */
	public static String minecraftVersion() {
		return HgnBuild.MINECRAFT_VERSION;
	}

	/** Enables disposable, rendered Video Settings navigation only in the packaged CI client. */
	public static boolean videoRuntimeSmoke() {
		return DEV_SCREEN.equals("video-runtime-smoke") && Boolean.getBoolean("hgn.smokeVideoRuntime")
			&& "true".equals(System.getenv("GITHUB_ACTIONS"));
	}

	/** Enables disposable route-by-route screenshots only in the packaged CI client. */
	public static boolean visualCaptureSmoke() {
		return DEV_SCREEN.equals("visual-capture") && Boolean.getBoolean("hgn.smokeVisualCapture")
			&& "true".equals(System.getenv("GITHUB_ACTIONS"));
	}

	private static volatile boolean started;

	/** Called by each loader's entrypoint once {@link HgnPlatform} is installed. */
	public static void init() {
		HgnConfig.load();
		HgnCosmetics.initialize();
		LOGGER.info("HGN Minecraft Client {} (Minecraft {}, {}) initialized.",
			CLIENT_VERSION, minecraftVersion(), HgnPlatform.get().loaderName());
	}

	/**
	 * Runs the one-time client start-up work. Driven from a vanilla hook rather
	 * than a loader lifecycle event, because those events differ per loader and
	 * were reworked outright in Forge's EventBus 7.
	 */
	public static void ensureStarted() {
		if (started) {
			return;
		}
		synchronized (HgnClient.class) {
			if (started) {
				return;
			}
			started = true;
		}
		if (DEV_SCREEN.equals("container-smoke")) {
			try {
				online.horizongate.hgn.ui.container.HgnContainerSmokeCheck.run();
			} finally {
				Minecraft.getInstance().stop();
			}
			return;
		}
		HgnServers.ensureOfficialServers(Minecraft.getInstance());
		HgnWindowIcon.apply();
	}
}
