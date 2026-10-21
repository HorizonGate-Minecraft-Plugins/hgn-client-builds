package online.horizongate.hgn.cosmetics;

/** Keeps a missing exact-version model pack distinct from sign-in or service failure. */
public final class HgnMemberPackStatusTest {
	public static void main(String[] args) {
		check(!HgnMemberPackStatus.noCompatiblePack(null, true),
			"An absent launcher status was treated as a missing pack");
		check(!HgnMemberPackStatus.noCompatiblePack("no_compatible_pack", false),
			"An unverified session displayed a pack-specific notice");
		check(HgnMemberPackStatus.noCompatiblePack("no_compatible_pack", true),
			"A verified session without this version's pack was not recognized");
		check(HgnMemberPackStatus.noticeFor("no_compatible_pack", "1.21.11", true, false)
			.equals("No cosmetic model pack available for Minecraft 1.21.11."),
			"The notice did not name the exact Minecraft version");
		String compact = HgnMemberPackStatus.noticeFor("no_compatible_pack", "1.21.11", true, true);
		check(compact.equals("No model pack for Minecraft 1.21.11.") && compact.length() < 40,
			"The compact notice was not concise enough for the catalogue header");
		check(HgnMemberPackStatus.noticeFor("no_compatible_pack", "1.21.4", false, true).isEmpty(),
			"The notice appeared without a verified session");
		check(HgnMemberPackStatus.noticeFor("unknown", "1.21.11", true, true).isEmpty(),
			"An unknown launcher status was treated as a missing pack");
		check(HgnMemberPackStatus.modelsUnavailable("pack_unavailable", true),
			"A failed optional pack install did not mark models unavailable");
		check(!HgnMemberPackStatus.modelsUnavailable("pack_unavailable", false),
			"A pack notice appeared without a verified session");
		check(!HgnMemberPackStatus.canRepairPrivatePack(null, false),
			"A private pack could be repaired without a member session");
		check(HgnMemberPackStatus.canRepairPrivatePack(null, true)
			&& HgnMemberPackStatus.canRepairPrivatePack("", true),
			"A verified session with an installed pack could not repair its models");
		for (String status : new String[] {"no_compatible_pack", "pack_unavailable", "unknown"}) {
			check(!HgnMemberPackStatus.canRepairPrivatePack(status, true),
				"A private pack could be repaired despite an unavailable pack status");
		}
		check(HgnMemberPackStatus.noticeFor("pack_unavailable", "1.21.4", true, false)
			.contains("account and cosmetics are still connected"),
			"The pack install failure was confused with a membership failure");
		check(HgnMemberPackStatus.noticeFor("pack_unavailable", "1.21.4", true, true)
			.length() < 50,
			"The compact pack-install notice was too wide");
		System.out.println("Exact-version cosmetic pack notices passed.");
	}

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
