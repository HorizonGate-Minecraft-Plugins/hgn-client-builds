package online.horizongate.hgn.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import online.horizongate.hgn.mixin.HgnUpdateWorldStorageAccessor;
import online.horizongate.hgn.HgnClient;
import net.minecraft.server.MinecraftServer;

/** Observes the actual current world/connection, not the destination used at initial launch. */
public final class HgnUpdateDestinations {
	private static boolean resolved;
	private static HgnUpdateDestinationReporter reporter;
	private static HgnUpdateDestination observed = HgnUpdateDestination.none();
	private static long revision;

	/** Invalidate before native level changes, including A-to-B-to-A within one tick. */
	public static void changingLevel() {
		revision = revision == Long.MAX_VALUE ? -1 : revision < 0 ? -1 : revision + 1;
	}

	public static long epoch(Minecraft minecraft) {
		revision(minecraft);
		return revision;
	}

	/** Local transitions are independent from the launcher's periodic report generation. */
	public static long revision(Minecraft minecraft) {
		var current = capture(minecraft);
		if (!current.sameDestination(observed)) {
			observed = current;
			revision = revision == Long.MAX_VALUE ? -1 : revision < 0 ? -1 : revision + 1;
		}
		return current.sameDestination(HgnUpdateDestination.none()) ? -1 : revision;
	}

	private HgnUpdateDestinations() {
	}

	public static void tick(Minecraft minecraft) {
		revision(minecraft);
		if (!resolved) {
			resolved = true;
			var bridge = HgnUpdateDestinationBridge.resolve(ClassLoader.getSystemClassLoader());
			if (bridge != null) reporter = new HgnUpdateDestinationReporter(bridge);
		}
		if (reporter == null) return;
		reporter.observe(capture(minecraft));
		reporter.tick(System.nanoTime());
	}

	private static HgnUpdateDestination capture(Minecraft minecraft) {
		if (minecraft == null || minecraft.getConnection() == null) return HgnUpdateDestination.none();
		var integrated = minecraft.getSingleplayerServer();
		if (integrated != null) {
			if (integrated.isPublished()) return HgnUpdateDestination.none();
			var storage = ((HgnUpdateWorldStorageAccessor) integrated).hgn$updateStorage();
			return HgnUpdateDestination.world(integrated, storage.getLevelId());
		}
		if (minecraft.level == null) return HgnUpdateDestination.none();
		ServerData server = minecraft.getCurrentServer();
		if (server == null || server.isLan() || server.isRealm()) return HgnUpdateDestination.none();
		return HgnUpdateDestination.server(minecraft.getConnection(), server.ip);
	}

	/** Verifies transformed storage access and the menu observation without opening a world or bridge. */
	public static void verifyMenuSmoke() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null || minecraft.getSingleplayerServer() != null || minecraft.getConnection() != null) {
			throw new AssertionError("Destination menu smoke requires no active game");
		}
		if (!HgnUpdateWorldStorageAccessor.class.isAssignableFrom(MinecraftServer.class)) {
			throw new AssertionError("World storage accessor was not transformed");
		}
		if (!capture(minecraft).sameDestination(HgnUpdateDestination.none())) {
			throw new AssertionError("Menu observation did not clear the update destination");
		}
		HgnClient.LOGGER.info("HGN_UPDATE_DESTINATION_SMOKE_OK: world storage accessor transformed; menu observation has no destination; no game actions executed");
	}
}
