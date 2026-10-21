package online.horizongate.hgn.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnDragonEye;
import online.horizongate.hgn.HgnHud;
import online.horizongate.hgn.HgnOfficialServers;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.HgnServers;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import online.horizongate.hgn.screen.HgnJoin;
import online.horizongate.hgn.screen.HgnJumpscareScreen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Loader-neutral handling for the HGN plugin-message channels
 * (see docs/PLUGIN-DEV.md). Payloads are raw UTF-8 JSON, exactly as Bukkit's
 * sendPluginMessage sends them. Each loader decodes bytes to a String and
 * hands them here.
 */
public final class HgnMessages {
	private static final Gson GSON = new Gson();
	private static final int MAX_PAYLOAD_CHARS = 65_536;
	private static final int MAX_JSON_DEPTH = 16;
	private static final int MAX_BEAST_ENTITIES = 256;
	private static final int MAX_IDENTIFIER_CHARS = 64;
	private static final int MAX_DISPLAY_TEXT_CODE_POINTS = 96;
	private static final int MAX_BEAST_NUMBER = 1_000_000;
	private static final long MAX_HUD_NUMBER = 9_000_000_000_000_000L;
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:/-]{1," + MAX_IDENTIFIER_CHARS + "}");
	private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
	private static final Pattern COMPACT_NUMBER = Pattern.compile("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,3})?");
	private static final Set<String> DRAGON_EYE_FIELDS = Set.of("v", "type", "active", "rank", "range");
	private static final Set<String> BEAST_SNAPSHOT_FIELDS = Set.of("v", "type", "entities");
	private static final Set<String> BEAST_FIELDS = Set.of(
		"uuid", "kind", "species", "tier", "tierLevel", "rank", "color", "emperor",
		"displayName", "model", "shape", "rankName", "rankColor",
		"hp", "hpMax", "hp_max", "currentHp", "currentHP", "health", "currentHealth",
		"maxHp", "maxHP", "healthMax", "maxHealth"
	);
	private static final Set<String> HUD_FIELDS = Set.of(
		"v", "type", "race", "player", "playerName", "name",
		"title", "class", "rank", "subrace", "nextRank", "next", "nextTier",
		"level", "maxLevel", "levelMax", "max_level", "levelCap", "level_cap",
		"evolveLevel", "evolutionLevel", "evolveAt", "evolveAtLevel",
		"evolved", "isEvolved", "evolutionComplete", "evolution_complete",
		"xp", "xpMax", "xp_max",
		"hp", "hpMax", "hp_max", "health", "healthMax", "health_max",
		"currentHp", "currentHP", "maxHp", "maxHP", "currentHealth", "maxHealth",
		"realHp", "realHpMax", "real_hp", "real_hp_max",
		"blood", "bloodMax", "blood_max", "maxBlood", "bloodHunger", "bloodHungerMax",
		"bloodLevel", "bloodLevelMax", "bloodBank", "bloodBankMax", "blood_bank", "blood_bank_max",
		"mc", "mcMax", "qi", "qiMax", "qi_max", "dp", "dpMax", "miasma",
		"auraColor", "aura_color", "raceTheme", "race_theme"
	);
	private static final Set<String> HUD_TEXT_FIELDS = Set.of(
		"player", "playerName", "name", "title", "class", "rank", "subrace",
		"nextRank", "next", "nextTier"
	);
	private static final Set<String> HUD_BOOLEAN_FIELDS = Set.of(
		"evolved", "isEvolved", "evolutionComplete", "evolution_complete"
	);
	private static final Set<String> HUD_COLOR_FIELDS = Set.of(
		"auraColor", "aura_color", "raceTheme", "race_theme"
	);

	public static volatile boolean dragonEyeActive;
	public static volatile int dragonEyeRank;
	public static volatile int dragonEyeRange;
	public static volatile JsonObject lastBeasts;
	/** The player's own read-only HUD snapshot (race, XP, Qi, powers …), ~1/s. */
	public static volatile JsonObject lastHud;
	/** When the last `hud` message arrived, so the HUD hides when the channel goes quiet (off VinkSMP). */
	public static volatile long lastHudAt;
	private HgnMessages() {
	}

	/** Announce ourselves so plugins know this player runs the HGN client. */
	public static void sendHello() {
		if (!trustedCoreConnection() || !HgnPlatform.get().canSendCore()) {
			return;
		}
		String hello = "{\"v\":1,\"type\":\"hello\",\"client\":\"hgn-client\",\"version\":\""
			+ HgnClient.CLIENT_VERSION + "\",\"mc\":\"" + HgnClient.minecraftVersion()
			+ "\",\"loader\":\"" + HgnPlatform.get().loaderName() + "\"}";
		HgnPlatform.get().sendCore(hello);
		HgnClient.LOGGER.info("[hgn:core] hello sent");
		sendHudStatus(online.horizongate.hgn.HgnConfig.get().hudEnabled);
	}

	/**
	 * Reports the player's HUD preference to HGN server plugins. This is sent
	 * after the hello handshake and whenever the setting is changed in-game.
	 */
	public static void sendHudStatus(boolean enabled) {
		if (!trustedCoreConnection() || !HgnPlatform.get().canSendCore()) {
			return;
		}
		String status = "{\"v\":1,\"type\":\"client_status\",\"client\":\"hgn-client\",\"hudEnabled\":"
			+ enabled + "}";
		HgnPlatform.get().sendCore(status);
		HgnClient.LOGGER.debug("[hgn:core] HUD enabled: {}", enabled);
	}

	public static void handleCore(String json) {
		if (!trustedCoreConnection()) {
			HgnClient.LOGGER.debug("[hgn:core] ignored a control payload from a non-HGN server");
			return;
		}
		if (!withinInputBounds(json)) {
			HgnClient.LOGGER.warn("[hgn:core] rejected payload outside input bounds");
			return;
		}
		try {
			JsonObject obj = GSON.fromJson(json, JsonObject.class);
			if (!protocolOne(obj)) return;
			String type = type(obj);
			HgnClient.LOGGER.debug("[hgn:core] received type: {}", type);
			switch (type) {
				case "switchserver" -> {
					if (!hasOnly(obj, "v", "type", "server")
						|| !stringField(obj, "server")) return;
					switchServer(obj.get("server").getAsString());
				}
				case "jumpscare" -> {
					if (hasOnly(obj, "v", "type")) HgnJumpscareScreen.show();
				}
				default -> {
				}
			}
		} catch (Exception ignored) {
			HgnClient.LOGGER.warn("[hgn:core] rejected malformed payload");
		}
	}

	public static void handleVink(String json) {
		if (!trustedVinkConnection()) {
			HgnClient.LOGGER.debug("[hgn:vinksmp] ignored a control payload from a non-VinkSMP server");
			return;
		}
		if (!withinInputBounds(json)) {
			HgnClient.LOGGER.warn("[hgn:vinksmp] rejected payload outside input bounds");
			return;
		}
		try {
			JsonObject obj = GSON.fromJson(json, JsonObject.class);
			if (!protocolOne(obj)) return;
			String type = type(obj);
			HgnClient.LOGGER.debug("[hgn:vinksmp] received type: {}", type);
			switch (type) {
				case "dragoneye" -> {
					if (!hasOnlyKnown(obj, DRAGON_EYE_FIELDS)) return;
					// VinkSMP may send a small rank/range refresh without an `active`
					// field. Treat it as a partial update: resetting the flag to false
					// briefly blacked the whole screen between otherwise valid pulses.
					Boolean active = optionalBoolean(obj, "active");
					Integer rank = optionalInteger(obj, "rank", 1, 3);
					Integer range = optionalInteger(obj, "range", 1, 512);
					if (active == null && rank == null && range == null) return;

					// Parse and validate every supplied field before changing any state.
					// A malformed rank/range must never leave a valid `active` update behind.
					boolean activeChanged = active != null && active != dragonEyeActive;
					if (active != null) dragonEyeActive = active;
					if (rank != null) dragonEyeRank = rank;
					if (range != null) dragonEyeRange = range;
					if (activeChanged) {
						HgnClient.LOGGER.info("[hgn:vinksmp] dragon eye {} (rank {}, range {})",
							dragonEyeActive ? "ACTIVE" : "off", dragonEyeRank, dragonEyeRange);
					}
				}
				case "beasts" -> {
					JsonObject snapshot = sanitizeBeasts(obj);
					if (snapshot != null) lastBeasts = snapshot;
				}
				case "hud" -> {
					JsonObject snapshot = sanitizeHud(obj);
					if (snapshot != null) {
						long receivedAt = System.currentTimeMillis();
						lastHud = snapshot;
						lastHudAt = receivedAt;
					}
				}
				default -> {
				}
			}
		} catch (Exception ignored) {
			HgnClient.LOGGER.warn("[hgn:vinksmp] rejected malformed payload");
		}
	}

	/**
	 * Moves the player to another network server, driven by the plugin's
	 * {@code /hgn-client server <id>} command. If the target runs the same
	 * Minecraft version it is joined directly; otherwise the launcher is asked to
	 * restart on the right version. Runs on the client thread, as the message
	 * arrives on the network thread.
	 */
	private static void switchServer(String id) {
		Minecraft mc = Minecraft.getInstance();
		HgnOfficialServers.ALL.stream()
			.filter(server -> server.id().equalsIgnoreCase(id))
			.findFirst()
			.ifPresent(server -> mc.execute(() -> {
				if (server.playableNow()) {
					HgnJoin.expect(null);
					HgnCompat.connect(mc.screen != null ? mc.screen : new TitleScreen(),
						mc, server.name(), server.address());
				} else {
					HgnCompat.openUrl("hgn://play/" + server.id() + "/" + server.requiredVersion());
					HgnClient.LOGGER.info("Switching to {} on {} via the launcher",
						server.id(), server.requiredVersion());
					HgnCompat.quit(mc);
				}
			}));
	}

	/**
	 * Clears all HGN server state when leaving a server, so a stale Dragon Eye, HUD or
	 * beast list never carries over to the next one.
	 */
	public static void reset() {
		HgnDungeons.reset();
		HgnBossEvents.reset();
		dragonEyeActive = false;
		dragonEyeRank = 0;
		dragonEyeRange = 0;
		lastBeasts = null;
		lastHud = null;
		lastHudAt = 0L;
		HgnDragonEye.reset();
		HgnHud.reset();
	}

	private static String type(JsonObject obj) {
		if (!stringField(obj, "type")) return "";
		String value = obj.get("type").getAsString();
		return IDENTIFIER.matcher(value).matches() ? value : "";
	}

	private static boolean protocolOne(JsonObject object) {
		return object != null && object.has("v") && object.get("v").isJsonPrimitive()
			&& object.getAsJsonPrimitive("v").isNumber()
			&& "1".equals(object.getAsJsonPrimitive("v").getAsString());
	}

	private static boolean stringField(JsonObject object, String name) {
		return object != null && object.has(name) && object.get(name).isJsonPrimitive()
			&& object.getAsJsonPrimitive(name).isString();
	}

	private static boolean hasOnly(JsonObject object, String... fields) {
		return object != null && object.keySet().equals(Set.of(fields));
	}

	private static boolean hasOnlyKnown(JsonObject object, Set<String> fields) {
		return object != null && fields.containsAll(object.keySet());
	}

	private static boolean withinInputBounds(String json) {
		if (json == null || json.isEmpty() || json.length() > MAX_PAYLOAD_CHARS) return false;
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int index = 0; index < json.length(); index++) {
			char character = json.charAt(index);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (character == '\\') {
					escaped = true;
				} else if (character == '"') {
					inString = false;
				}
				continue;
			}
			if (character == '"') {
				inString = true;
			} else if (character == '{' || character == '[') {
				if (++depth > MAX_JSON_DEPTH) return false;
			} else if (character == '}' || character == ']') {
				if (--depth < 0) return false;
			}
		}
		return !inString && depth == 0;
	}

	private static JsonObject sanitizeBeasts(JsonObject object) {
		if (!hasOnlyKnown(object, BEAST_SNAPSHOT_FIELDS) || !object.has("entities")
			|| !object.get("entities").isJsonArray()) return null;
		JsonArray source = object.getAsJsonArray("entities");
		if (source.size() > MAX_BEAST_ENTITIES) return null;

		JsonArray entities = new JsonArray();
		Set<UUID> seen = new HashSet<>();
		for (JsonElement element : source) {
			if (!element.isJsonObject()) return null;
			JsonObject entity = sanitizeBeast(element.getAsJsonObject(), seen);
			if (entity == null) return null;
			entities.add(entity);
		}
		JsonObject snapshot = snapshot("beasts");
		snapshot.add("entities", entities);
		return snapshot;
	}

	private static JsonObject sanitizeBeast(JsonObject object, Set<UUID> seen) {
		if (!hasOnlyKnown(object, BEAST_FIELDS) || !stringField(object, "uuid")) return null;
		String uuidText = object.get("uuid").getAsString();
		UUID uuid;
		try {
			uuid = UUID.fromString(uuidText);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
		if (!uuid.toString().equalsIgnoreCase(uuidText) || !seen.add(uuid)) return null;

		JsonObject sanitized = new JsonObject();
		sanitized.addProperty("uuid", uuid.toString());
		if (!copyOptionalIdentifier(object, sanitized, "kind", Set.of("mob", "animal", "beast"))) return null;
		if (!copyOptionalIdentifier(object, sanitized, "species", null)) return null;
		if (!copyOptionalIdentifier(object, sanitized, "tier", null)) return null;
		if (!copyOptionalInteger(object, sanitized, "tierLevel", 0, MAX_BEAST_NUMBER)) return null;
		if (!copyOptionalInteger(object, sanitized, "rank", 0, MAX_BEAST_NUMBER)) return null;
		if (!copyOptionalColor(object, sanitized, "color")) return null;
		if (!copyOptionalBoolean(object, sanitized, "emperor")) return null;
		if (!copyOptionalText(object, sanitized, "displayName")) return null;
		if (!copyOptionalIdentifier(object, sanitized, "model", null)) return null;
		if (!copyOptionalIdentifier(object, sanitized, "shape", null)) return null;
		if (!copyOptionalText(object, sanitized, "rankName")) return null;
		if (!copyOptionalColor(object, sanitized, "rankColor")) return null;
		for (String field : Set.of("hp", "hpMax", "hp_max", "currentHp", "currentHP",
			"health", "currentHealth", "maxHp", "maxHP", "healthMax", "maxHealth")) {
			if (!copyOptionalHudNumber(object, sanitized, field, MAX_HUD_NUMBER)) return null;
		}
		return sanitized;
	}

	private static JsonObject sanitizeHud(JsonObject object) {
		if (!hasOnlyKnown(object, HUD_FIELDS) || object.keySet().size() <= 2) return null;
		JsonObject sanitized = snapshot("hud");
		for (String field : object.keySet()) {
			if ("v".equals(field) || "type".equals(field)) continue;
			if ("race".equals(field)) {
				if (!copyOptionalIdentifier(object, sanitized, field, null)) return null;
			} else if (HUD_TEXT_FIELDS.contains(field)) {
				if (!copyOptionalText(object, sanitized, field)) return null;
			} else if (HUD_BOOLEAN_FIELDS.contains(field)) {
				if (!copyOptionalBoolean(object, sanitized, field)) return null;
			} else if (HUD_COLOR_FIELDS.contains(field)) {
				if (!copyOptionalColor(object, sanitized, field)) return null;
			} else {
				Long value = hudNumber(object.get(field), "level".equals(field) ? MAX_BEAST_NUMBER : MAX_HUD_NUMBER);
				if (value == null) return null;
				sanitized.addProperty(field, value);
			}
		}
		if (!normalizeAlias(sanitized, "qi_max", "qiMax")
			|| !normalizeAlias(sanitized, "aura_color", "auraColor")
			|| !normalizeAlias(sanitized, "race_theme", "raceTheme")) return null;
		return sanitized;
	}

	private static JsonObject snapshot(String type) {
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("v", 1);
		snapshot.addProperty("type", type);
		return snapshot;
	}

	private static boolean copyOptionalIdentifier(JsonObject source, JsonObject target, String name,
		Set<String> allowed) {
		if (!source.has(name)) return true;
		if (!stringField(source, name)) return false;
		String value = source.get(name).getAsString();
		if (!IDENTIFIER.matcher(value).matches() || allowed != null && !allowed.contains(value)) return false;
		target.addProperty(name, value);
		return true;
	}

	private static boolean copyOptionalText(JsonObject source, JsonObject target, String name) {
		if (!source.has(name)) return true;
		if (!stringField(source, name)) return false;
		String value = source.get(name).getAsString();
		if (value.isBlank() || value.codePointCount(0, value.length()) > MAX_DISPLAY_TEXT_CODE_POINTS) return false;
		for (int offset = 0; offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT
				|| codePoint == '\u00A7') return false;
			offset += Character.charCount(codePoint);
		}
		target.addProperty(name, value);
		return true;
	}

	private static boolean copyOptionalInteger(JsonObject source, JsonObject target, String name,
		int minimum, int maximum) {
		if (!source.has(name)) return true;
		Integer value = optionalInteger(source, name, minimum, maximum);
		if (value == null) return false;
		target.addProperty(name, value);
		return true;
	}

	private static boolean copyOptionalBoolean(JsonObject source, JsonObject target, String name) {
		if (!source.has(name)) return true;
		Boolean value = optionalBoolean(source, name);
		if (value == null) return false;
		target.addProperty(name, value);
		return true;
	}

	private static boolean copyOptionalColor(JsonObject source, JsonObject target, String name) {
		if (!source.has(name)) return true;
		if (!stringField(source, name)) return false;
		String value = source.get(name).getAsString();
		if (!COLOR.matcher(value).matches()) return false;
		target.addProperty(name, value.toUpperCase(Locale.ROOT));
		return true;
	}

	private static boolean copyOptionalHudNumber(JsonObject source, JsonObject target,
		String name, long maximum) {
		if (!source.has(name)) return true;
		Long value = hudNumber(source.get(name), maximum);
		if (value == null) return false;
		target.addProperty(name, value);
		return true;
	}

	private static Long hudNumber(JsonElement element, long maximum) {
		if (element == null || !element.isJsonPrimitive()) return null;
		String value;
		BigDecimal multiplier = BigDecimal.ONE;
		if (element.getAsJsonPrimitive().isNumber()) {
			value = element.getAsString();
			if (!value.matches("(?:0|[1-9][0-9]*)")) return null;
		} else if (element.getAsJsonPrimitive().isString()) {
			String original = element.getAsString().trim();
			if (original.isEmpty() || original.length() > 32) return null;
			value = original.replace(",", "").replace("_", "").replace(" ", "");
			if (value.isEmpty()) return null;
			char suffix = Character.toUpperCase(value.charAt(value.length() - 1));
			multiplier = switch (suffix) {
				case 'K' -> BigDecimal.valueOf(1_000L);
				case 'M' -> BigDecimal.valueOf(1_000_000L);
				case 'B' -> BigDecimal.valueOf(1_000_000_000L);
				case 'T' -> BigDecimal.valueOf(1_000_000_000_000L);
				case 'Q' -> BigDecimal.valueOf(1_000_000_000_000_000L);
				default -> BigDecimal.ONE;
			};
			if (!BigDecimal.ONE.equals(multiplier)) value = value.substring(0, value.length() - 1);
			if (!COMPACT_NUMBER.matcher(value).matches()) return null;
		} else {
			return null;
		}
		try {
			long parsed = new BigDecimal(value).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
			return parsed <= maximum ? parsed : null;
		} catch (ArithmeticException ignored) {
			return null;
		}
	}

	private static boolean normalizeAlias(JsonObject object, String alias, String canonical) {
		if (!object.has(alias)) return true;
		if (object.has(canonical) && !object.get(canonical).equals(object.get(alias))) return false;
		object.add(canonical, object.get(alias));
		return true;
	}

	private static Boolean optionalBoolean(JsonObject object, String name) {
		if (!object.has(name)) return null;
		if (object.get(name).isJsonNull() || !object.get(name).isJsonPrimitive()
			|| !object.getAsJsonPrimitive(name).isBoolean()) {
			throw new IllegalArgumentException("invalid " + name);
		}
		return object.getAsJsonPrimitive(name).getAsBoolean();
	}

	private static Integer optionalInteger(JsonObject object, String name, int minimum, int maximum) {
		if (!object.has(name)) return null;
		if (object.get(name).isJsonNull() || !object.get(name).isJsonPrimitive()
			|| !object.getAsJsonPrimitive(name).isNumber()) {
			throw new IllegalArgumentException("invalid " + name);
		}
		String value = object.getAsJsonPrimitive(name).getAsString();
		if (!value.matches("-?(?:0|[1-9][0-9]*)")) {
			throw new IllegalArgumentException("invalid " + name);
		}
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException("invalid " + name);
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("invalid " + name, exception);
		}
	}

	private static boolean trustedCoreConnection() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null && minecraft.getCurrentServer() != null
			&& HgnServers.isOfficialAddress(minecraft.getCurrentServer().ip);
	}

	private static boolean trustedVinkConnection() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null && minecraft.getCurrentServer() != null
			&& HgnClient.VINKSMP_ADDRESS.equals(
				HgnServers.normalizeServerAddress(minecraft.getCurrentServer().ip));
	}

	public static String hgnGoldenTokens() {
		return HgnCosmetics.snapshot().profile().hgnGoldenTokens();
	}

	public static long hgnTokens() {
		return HgnCosmetics.snapshot().profile().hgnTokens();
	}

	public static HgnCosmetics.Snapshot cosmetics() {
		return HgnCosmetics.snapshot();
	}

	/** Requests a rate-limited launcher-membership snapshot while the Cosmetics page is open. */
	public static String requestCosmetics() {
		return HgnCosmetics.refreshOnPageOpen();
	}

	/**
	 * An equip request is only a request. The HGN membership backend validates ownership,
	 * category conflicts and launcher eligibility, then returns a new
	 * {@code profile} snapshot. Nothing can be granted or equipped locally.
	 */
	public static String equipCosmetic(String id) {
		return id == null || id.isBlank() ? "" : HgnCosmetics.equip(id);
	}

	public static String unequipCosmetic(String slot) {
		return slot == null || slot.isBlank() ? "" : HgnCosmetics.unequip(slot);
	}

	public static boolean launcherLinked() {
		return HgnCosmetics.snapshot().profile().launcherLinked();
	}
}
