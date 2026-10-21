package online.horizongate.hgn;

import java.util.List;

/** The HorizonGate Network servers pinned in the client. */
public final class HgnOfficialServers {

	/**
	 * @param requiredVersion the Minecraft version this server accepts, or null
	 *                        when it takes whatever the player is running
	 */
	public record Server(
		String id,
		String name,
		String address,
		String blurb,
		String requiredVersion) {

		/** Whether the running client can join without switching version. */
		public boolean playableNow() {
			return requiredVersion == null || requiredVersion.equals(HgnClient.minecraftVersion());
		}

		/** "1.21.x – 1.21.4" for a server that takes a range, or the one it requires. */
		public String versionLabel() {
			return requiredVersion == null ? "1.21.x – 1.21.11" : requiredVersion;
		}
	}

	public static final Server HUB = new Server(
		"hub",
		HgnClient.HUB_NAME,
		HgnClient.HUB_ADDRESS,
		"The official Minecraft network powered by HGN.",
		null);
	public static final Server VINKSMP = new Server(
		"vinksmp",
		HgnClient.VINKSMP_NAME,
		HgnClient.VINKSMP_ADDRESS,
		"Survival. Community. Adventure.",
		"1.21.4");
	public static final List<Server> ALL = List.of(HUB, VINKSMP);

	private HgnOfficialServers() {
	}
}
