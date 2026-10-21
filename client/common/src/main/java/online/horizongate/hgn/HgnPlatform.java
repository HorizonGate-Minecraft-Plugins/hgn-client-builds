package online.horizongate.hgn;

import java.nio.file.Path;

/**
 * The only things the HGN Client needs from the mod loader underneath it.
 * Fabric, Forge and NeoForge each install their own implementation during
 * start-up; everything else in the client is loader-agnostic.
 */
public interface HgnPlatform {
	Path configDir();

	/** Whether the server has advertised the {@code hgn:core} channel. */
	boolean canSendCore();

	void sendCore(String json);

	/** Whether the backend advertised the read-only dungeon subscription. */
	boolean canSendDungeonsHello();

	void sendDungeonsHello();

	boolean canSendDungeonsAuth();

	void sendDungeonsAuth(String token);

	String loaderName();

	static HgnPlatform get() {
		if (Holder.current == null) {
			throw new IllegalStateException("HGN platform accessed before the loader installed it");
		}
		return Holder.current;
	}

	static void install(HgnPlatform platform) {
		Holder.current = platform;
	}

	final class Holder {
		private static HgnPlatform current;

		private Holder() {
		}
	}
}
