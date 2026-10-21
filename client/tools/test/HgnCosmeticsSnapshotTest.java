import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import online.horizongate.hgn.cosmetics.HgnCosmetics;

/** Checks both deployed and current authenticated Cosmetics snapshot wire shapes. */
public final class HgnCosmeticsSnapshotTest {
	public static void main(String[] args) {
		check("Use HGN Launcher".equals(
			HgnCosmetics.ConnectionState.LAUNCHER_REQUIRED.dashboardDescription()),
			"Dashboard launcher action must fit its narrow status lane");
		check("Launch from HGN Launcher".equals(
			HgnCosmetics.ConnectionState.LAUNCHER_REQUIRED.description()),
			"Full Cosmetics guidance must stay explicit");
		HgnCosmetics.reset();
		JsonObject legacy = snapshot(true);
		check(HgnCosmetics.handle(legacy), "Legacy snapshot was rejected");
		HgnCosmetics.Snapshot accepted = HgnCosmetics.snapshot();
		check(accepted.profile().legacyCurrency(), "Legacy profile was not distinguished");
		check("250".equals(accepted.profile().legacyCosmeticCoins()), "Legacy coins changed");
		check("12.50".equals(accepted.profile().legacyAccountCredits()), "Fractional credits changed");
		check(accepted.profile().hgnTokens() == -1L, "Legacy coins became HGN Tokens");
		check("UNAVAILABLE".equals(accepted.profile().hgnGoldenTokens()),
			"Legacy credits became HGN Golden Tokens");
		check(accepted.catalogue().get(0).currency() == HgnCosmetics.Currency.ACCOUNT_CREDITS,
			"Legacy Account Credits were relabeled");
		check(accepted.catalogue().get(1).currency() == HgnCosmetics.Currency.COSMETIC_COINS,
			"Legacy Cosmetic Coins were relabeled");

		JsonObject unknown = snapshot(true);
		unknown.getAsJsonArray("catalogue").get(0).getAsJsonObject()
			.addProperty("currency", "UNRECOGNIZED");
		check(!HgnCosmetics.handle(unknown), "Unknown currency was accepted");
		check(HgnCosmetics.snapshot().equals(accepted), "Rejected response replaced the snapshot");

		JsonObject mixed = snapshot(true);
		mixed.getAsJsonArray("catalogue").get(0).getAsJsonObject()
			.addProperty("currency", "HGN_GOLDEN_TOKENS");
		check(!HgnCosmetics.handle(mixed), "Mixed currency schemas were accepted");
		JsonObject malformed = snapshot(true);
		malformed.getAsJsonObject("profile").addProperty("accountCredits", "12.5");
		check(!HgnCosmetics.handle(malformed), "Malformed legacy credits were accepted");
		JsonObject mixedProfile = snapshot(true);
		mixedProfile.getAsJsonObject("profile").addProperty("hgnGoldenTokens", "12");
		check(!HgnCosmetics.handle(mixedProfile), "Mixed profile schemas were accepted");
		JsonObject unlinked = snapshot(true);
		unlinked.getAsJsonObject("profile").addProperty("launcherLinked", false);
		unlinked.getAsJsonObject("profile").addProperty("accountCredits", "NOT LINKED");
		check(HgnCosmetics.handle(unlinked), "Legacy unlinked snapshot was rejected");
		check("NOT LINKED".equals(HgnCosmetics.snapshot().profile().legacyAccountCredits()),
			"Legacy unlinked credits changed");

		JsonObject current = snapshot(false);
		check(HgnCosmetics.handle(current), "Current snapshot was rejected");
		HgnCosmetics.Snapshot canonical = HgnCosmetics.snapshot();
		check(!canonical.profile().legacyCurrency(), "Current profile became legacy");
		check("12".equals(canonical.profile().hgnGoldenTokens()), "Current Golden Tokens changed");
		check(canonical.profile().hgnTokens() == 250L, "Current HGN Tokens changed");
		check(canonical.catalogue().get(0).currency() == HgnCosmetics.Currency.HGN_GOLDEN_TOKENS,
			"Current Golden Token item changed");
		check(canonical.catalogue().get(1).currency() == HgnCosmetics.Currency.HGN_TOKENS,
			"Current HGN Token item changed");
		JsonObject numericTokens = snapshot(false);
		numericTokens.getAsJsonObject("profile").addProperty("hgnTokens", 250);
		check(HgnCosmetics.handle(numericTokens), "Canonical numeric HGN Tokens were rejected");
		check(HgnCosmetics.snapshot().equals(canonical), "Numeric HGN Tokens changed the balance");
		JsonObject fractionalNumericTokens = snapshot(false);
		fractionalNumericTokens.getAsJsonObject("profile").addProperty("hgnTokens", 250.9);
		check(!HgnCosmetics.handle(fractionalNumericTokens),
			"Fractional numeric HGN Tokens were rounded into spendable units");
		for (String value : new String[] {"-1", "12.5", "012", "18446744073709551616"}) {
			JsonObject invalidGoldenTokens = snapshot(false);
			invalidGoldenTokens.getAsJsonObject("profile").addProperty("hgnGoldenTokens", value);
			check(!HgnCosmetics.handle(invalidGoldenTokens),
				"Invalid Golden Tokens were accepted: " + value);
			check(HgnCosmetics.snapshot().equals(canonical),
				"Invalid Golden Tokens replaced the verified snapshot");
		}
		for (String value : new String[] {"-1", "250.9", "0250", "9223372036854775808"}) {
			JsonObject invalidTokens = snapshot(false);
			invalidTokens.getAsJsonObject("profile").addProperty("hgnTokens", value);
			check(!HgnCosmetics.handle(invalidTokens), "Invalid HGN Tokens were accepted: " + value);
			check(HgnCosmetics.snapshot().equals(canonical),
				"Invalid HGN Tokens replaced the verified snapshot");
		}

		JsonObject missingProfile = snapshot(false);
		missingProfile.remove("profile");
		check(!HgnCosmetics.handle(missingProfile), "Snapshot without a profile was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Missing profile cleared verified Cosmetics");
		JsonObject missingCatalogue = snapshot(false);
		missingCatalogue.remove("catalogue");
		check(!HgnCosmetics.handle(missingCatalogue), "Snapshot without a catalogue was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Missing catalogue cleared verified Cosmetics");
		JsonObject missingGoldenTokens = snapshot(false);
		missingGoldenTokens.getAsJsonObject("profile").remove("hgnGoldenTokens");
		check(!HgnCosmetics.handle(missingGoldenTokens), "Snapshot without premium balance was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Missing premium balance cleared verified Cosmetics");
		JsonObject missingTokens = snapshot(false);
		missingTokens.getAsJsonObject("profile").remove("hgnTokens");
		check(!HgnCosmetics.handle(missingTokens), "Snapshot without earned balance was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Missing earned balance cleared verified Cosmetics");
		JsonObject nullTokens = snapshot(false);
		nullTokens.getAsJsonObject("profile").add("hgnTokens", JsonNull.INSTANCE);
		check(!HgnCosmetics.handle(nullTokens), "Snapshot with null earned balance was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Null earned balance cleared verified Cosmetics");
		JsonObject differentPlayer = snapshot(false);
		differentPlayer.getAsJsonObject("profile")
			.addProperty("playerId", "da75f8c4-1654-45ac-b80d-b9ff03997cac");
		check(!HgnCosmetics.handle(differentPlayer), "Another player's snapshot was accepted");
		check(HgnCosmetics.snapshot().equals(canonical), "Another player's balance replaced verified Cosmetics");
		JsonObject emptyCatalogue = snapshot(false);
		emptyCatalogue.add("catalogue", new JsonArray());
		check(HgnCosmetics.handle(emptyCatalogue), "Valid empty catalogue was rejected");
		check(HgnCosmetics.snapshot().catalogue().isEmpty(), "Valid empty catalogue gained placeholder items");
		HgnCosmetics.reset();
		check(HgnCosmetics.handle(differentPlayer), "A new session could not accept its own profile");
		System.out.println("Legacy and current Cosmetics snapshots passed.");
	}

	private static JsonObject snapshot(boolean legacy) {
		JsonObject result = new JsonObject();
		result.addProperty("v", 2);
		result.addProperty("type", "cosmetics_snapshot");
		JsonObject profile = new JsonObject();
		profile.addProperty("playerId", "b8d36cfa-3de8-4f61-970d-fbc0d43f7740");
		profile.addProperty("launcherLinked", true);
		profile.addProperty("catalogueRevision", "1");
		profile.add("ownedIds", new JsonArray());
		profile.add("equipped", new JsonObject());
		if (legacy) {
			profile.addProperty("cosmeticCoins", "250");
			profile.addProperty("accountCredits", "12.50");
		} else {
			profile.addProperty("hgnTokens", "250");
			profile.addProperty("hgnGoldenTokens", "12");
		}
		result.add("profile", profile);
		JsonArray catalogue = new JsonArray();
		catalogue.add(item("one", legacy ? "ACCOUNT_CREDITS" : "HGN_GOLDEN_TOKENS"));
		catalogue.add(item("two", legacy ? "COSMETIC_COINS" : "HGN_TOKENS"));
		result.add("catalogue", catalogue);
		return result;
	}

	private static JsonObject item(String id, String currency) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.addProperty("name", id);
		item.addProperty("price", "50");
		item.addProperty("currency", currency);
		item.addProperty("active", true);
		return item;
	}

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
