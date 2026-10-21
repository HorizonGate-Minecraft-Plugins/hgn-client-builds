package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keeps the official HorizonGate Network servers pinned at the top of the server list. */
public final class HgnServers {
	private HgnServers() {
	}

	/** An official-server entry that auto-accepts the HGN server resource pack (no prompt). */
	private static ServerData official(String name, String address) {
		ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
		data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
		return data;
	}

	public static boolean isOfficialAddress(String address) {
		String a = normalizeServerAddress(address);
		return a.equals(HgnClient.HUB_ADDRESS) || a.equals(HgnClient.VINKSMP_ADDRESS)
			|| a.equals("parsrng.horizongate.online");
	}

	/** Servers where active non-AFK time counts toward free Villager Hats. */
	public static boolean isVillagerHatRewardAddress(String address) {
		String normalized = normalizeServerAddress(address);
		return normalized.equals(HgnClient.VINKSMP_ADDRESS)
			|| normalized.equals("parsrng.horizongate.online");
	}

	/**
	 * Returns a canonical DNS host for a Minecraft server entry, or an empty
	 * string for malformed input. The omitted/default Minecraft port 25565 and
	 * one DNS root dot are canonicalized away. A non-default valid port remains
	 * in the result so it cannot be mistaken for an official origin. Schemes,
	 * credentials, paths, queries, fragments, IPv6 literals, whitespace, and
	 * ambiguous/multiple ports are rejected.
	 */
	public static String normalizeServerAddress(String address) {
		if (address == null) return "";
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.length() > 260
			|| normalized.contains("/") || normalized.contains("\\")
			|| normalized.contains("?") || normalized.contains("#")
			|| normalized.contains("@") || normalized.contains("[")
			|| normalized.contains("]")) return "";
		for (int index = 0; index < normalized.length(); index++) {
			if (Character.isWhitespace(normalized.charAt(index))) return "";
		}

		int colon = normalized.indexOf(':');
		String host = normalized;
		int port = 25_565;
		if (colon >= 0) {
			if (colon != normalized.lastIndexOf(':')) return "";
			host = normalized.substring(0, colon);
			String portText = normalized.substring(colon + 1);
			if (portText.isEmpty() || portText.length() > 5) return "";
			for (int index = 0; index < portText.length(); index++) {
				if (!Character.isDigit(portText.charAt(index))) return "";
			}
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException ignored) {
				return "";
			}
			if (port < 1 || port > 65_535) return "";
		}
		if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		if (host.isEmpty() || host.length() > 253 || host.startsWith(".")
			|| host.contains("..")) return "";
		for (int index = 0; index < host.length(); index++) {
			char character = host.charAt(index);
			if (!(character >= 'a' && character <= 'z')
				&& !(character >= '0' && character <= '9')
				&& character != '.' && character != '-') return "";
		}
		return port == 25_565 ? host : host + ":" + port;
	}

	/** Rebuild servers.dat so the official entries are always first (and correct). */
	public static void ensureOfficialServers(Minecraft client) {
		try {
			ServerList list = new ServerList(client);
			list.load();

			List<ServerData> personal = new ArrayList<>();
			List<ServerData> all = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				ServerData s = list.get(i);
				all.add(s);
				if (!isOfficialAddress(s.ip)) {
					personal.add(s);
				}
			}
			for (ServerData s : all) {
				list.remove(s);
			}

			list.add(official(HgnClient.HUB_NAME, HgnClient.HUB_ADDRESS), false);
			list.add(official(HgnClient.VINKSMP_NAME, HgnClient.VINKSMP_ADDRESS), false);
			for (ServerData s : personal) {
				list.add(s, false);
			}

			list.save();
			HgnClient.LOGGER.info("Official HorizonGate Network servers pinned ({} personal server(s) kept).", personal.size());
		} catch (Exception e) {
			HgnClient.LOGGER.warn("Could not pin official servers", e);
		}
	}
}
