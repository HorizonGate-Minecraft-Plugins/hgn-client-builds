package online.horizongate.hgn.cosmetics;

/** Display-only notice for a verified session whose private model pack is unavailable. */
final class HgnMemberPackStatus {
	static final String STATUS_ENV = "HGN_CLIENT_MEMBER_PACK_STATUS";
	private static final String NO_COMPATIBLE_PACK = "no_compatible_pack";
	private static final String PACK_UNAVAILABLE = "pack_unavailable";

	private HgnMemberPackStatus() {
	}

	static boolean noCompatiblePack(String status, boolean sessionActive) {
		return sessionActive && NO_COMPATIBLE_PACK.equals(status);
	}

	static boolean modelsUnavailable(String status, boolean sessionActive) {
		return sessionActive && (NO_COMPATIBLE_PACK.equals(status) || PACK_UNAVAILABLE.equals(status));
	}

	/** Only an active member session with an installed private pack may repair it. */
	static boolean canRepairPrivatePack(String status, boolean sessionActive) {
		return sessionActive && (status == null || status.isBlank());
	}

	static String noticeFor(String status, String minecraftVersion,
		boolean sessionActive, boolean compact) {
		if (!modelsUnavailable(status, sessionActive)) return "";
		if (PACK_UNAVAILABLE.equals(status)) {
			return compact ? "Cosmetic models unavailable; retry next launch."
				: "Cosmetic model pack could not be installed. Your account and cosmetics are still connected; retry next launch.";
		}
		if (minecraftVersion == null || minecraftVersion.isBlank()) {
			return compact ? "No model pack for this Minecraft version."
				: "No cosmetic model pack available for this Minecraft version.";
		}
		return compact ? "No model pack for Minecraft " + minecraftVersion + "."
			: "No cosmetic model pack available for Minecraft " + minecraftVersion + ".";
	}
}
