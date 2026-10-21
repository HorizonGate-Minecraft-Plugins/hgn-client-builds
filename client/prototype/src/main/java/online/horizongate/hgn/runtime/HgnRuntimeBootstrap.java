package online.horizongate.hgn.runtime;

import java.util.Objects;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;

/**
 * First-party lifecycle boundary for a future locally built HGN game version.
 * The game bootstrap must install HGN mixins, resources, and packet handlers
 * before calling this method. This class is not a mod entrypoint.
 */
public final class HgnRuntimeBootstrap {
	private static boolean initialized;

	private HgnRuntimeBootstrap() {
	}

	public static synchronized void initialize(HgnPlatform platform) {
		if (initialized) {
			throw new IllegalStateException("HGN runtime is already initialized");
		}
		HgnPlatform.install(Objects.requireNonNull(platform, "platform"));
		HgnClient.init();
		initialized = true;
	}

	public static synchronized void initializeFabric() {
		HgnRuntimeFabricPlatform platform = new HgnRuntimeFabricPlatform();
		initialize(platform);
		platform.registerPayloads();
	}
}
