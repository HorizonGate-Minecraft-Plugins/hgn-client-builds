package online.horizongate.hgn.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.HgnServers;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Server-owned dungeon telemetry and authenticated quest display; the client grants nothing. */
public final class HgnDungeons {
	public record QuestState(int clearedZombieWave, boolean zombieUnlocked) {
	}

	public record MobInsight(String name, String role, double hp, double maxHp, String bestPower) {
	}

	private static final int MAX_BYTES = 24_000;
	private static final int MAX_DEPTH = 16;
	private static final long SNAPSHOT_TIMEOUT_NANOS = 5_000_000_000L;
	private static final long SUBSCRIBE_INTERVAL_NANOS = 20_000_000_000L;
	private static final long AUTH_RETRY_INTERVAL_NANOS = 15_000_000_000L;
	private static final long QUEST_TIMEOUT_NANOS = 60_000_000_000L;
	private static final Set<String> ROLES = Set.of(
		"normal", "runner", "miniboss", "titan", "boss", "pvink7");
	private static JsonObject capabilities;
	private static JsonObject catalog;
	private static JsonObject snapshot;
	private static QuestState quest;
	private static MobInsight insight;
	private static UUID runId;
	private static UUID serverSessionId;
	private static long sequence = -1;
	private static long receivedAtNanos;
	private static long lastSubscribeAtNanos;
	private static long lastAuthAtNanos;
	private static long lastQuestAtNanos;

	private HgnDungeons() {
	}

	public static void subscribe() {
		if (trusted() && HgnPlatform.get().canSendDungeonsHello()) {
			HgnPlatform.get().sendDungeonsHello();
			lastSubscribeAtNanos = System.nanoTime();
		}
	}

	/** Re-subscribe after a proxy backend switch, and forget all prior-server data on disconnect. */
	public static void tick() {
		HgnDungeonProof.token();
		if (!trusted()) {
			reset();
			return;
		}
		if (lastSubscribeAtNanos == 0
			|| System.nanoTime() - lastSubscribeAtNanos >= SUBSCRIBE_INTERVAL_NANOS) subscribe();
	}

	public static synchronized void reset() {
		capabilities = null;
		catalog = null;
		clearRun();
		quest = null;
		serverSessionId = null;
		sequence = -1;
		lastSubscribeAtNanos = 0;
		lastAuthAtNanos = 0;
		lastQuestAtNanos = 0;
	}

	public static synchronized JsonObject capabilities() {
		return capabilities == null ? null : capabilities.deepCopy();
	}

	public static synchronized JsonObject catalog() {
		return catalog == null ? null : catalog.deepCopy();
	}

	public static synchronized JsonObject snapshot() {
		if (!trusted() || snapshot == null) return null;
		if (!snapshotFresh(System.nanoTime(), receivedAtNanos)) {
			clearRun();
			return null;
		}
		return snapshot.deepCopy();
	}

	public static synchronized QuestState quest() {
		if (!trusted() || quest == null) return null;
		if (!questFresh(System.nanoTime(), lastQuestAtNanos)) {
			quest = null;
			return null;
		}
		return quest;
	}

	/** Only a fresh server snapshot can place the passive HUD inside an active run. */
	public static synchronized boolean inRun() {
		return trusted() && runId != null && snapshot != null
			&& snapshotFresh(System.nanoTime(), receivedAtNanos);
	}

	public static synchronized MobInsight insight() {
		return quest() != null && quest.zombieUnlocked() && snapshot != null
			&& snapshotFresh(System.nanoTime(), receivedAtNanos) ? insight : null;
	}

	static boolean questFresh(long now, long receivedAt) {
		return fresh(now, receivedAt, QUEST_TIMEOUT_NANOS);
	}

	static boolean snapshotFresh(long now, long receivedAt) {
		return fresh(now, receivedAt, SNAPSHOT_TIMEOUT_NANOS);
	}

	private static boolean fresh(long now, long receivedAt, long timeout) {
		long age = now - receivedAt;
		return receivedAt != 0 && age >= 0 && age < timeout;
	}

	public static synchronized void handleMeta(String json) {
		JsonObject object = parse(json, "capabilities");
		if (object == null || !integer(object, "affinityVersion", 1, 1)
			|| !integer(object, "powerCatalogVersion", 2, 2)
			|| !bool(object, "mobInsights")) return;
		UUID incomingSessionId = null;
		String sessionText = string(object, "sessionId");
		if (object.has("sessionId")) {
			if (sessionText == null) return;
			try {
				incomingSessionId = UUID.fromString(sessionText);
				if (!incomingSessionId.toString().equals(sessionText)) return;
			} catch (IllegalArgumentException exception) {
				return;
			}
		}
		// Repeated hello replies must not flicker the HUD. A different server-side
		// connection marker must discard old quest and mob state immediately.
		boolean questEnabled = bool(object, "godsEyeQuest")
			&& object.get("godsEyeQuest").getAsBoolean();
		boolean previousQuestEnabled = capabilities != null && bool(capabilities, "godsEyeQuest")
			&& capabilities.get("godsEyeQuest").getAsBoolean();
		if (incomingSessionId == null || !incomingSessionId.equals(serverSessionId)
			|| capabilities != null && questEnabled != previousQuestEnabled) {
			catalog = null;
			clearRun();
			quest = null;
			sequence = -1;
			lastQuestAtNanos = 0;
			lastAuthAtNanos = 0;
		}
		serverSessionId = incomingSessionId;
		capabilities = object.deepCopy();
		if (questEnabled) {
			sendProof();
		}
	}

	public static synchronized void handleQuest(String json) {
		JsonObject object = parse(json, "gods_eye_state");
		if (object == null || capabilities == null || !bool(capabilities, "godsEyeQuest")
			|| !capabilities.get("godsEyeQuest").getAsBoolean() || !bool(object, "verified")
			|| !object.get("verified").getAsBoolean()
			|| !integer(object, "highestZombieClear", 0, 25)) return;
		// Quest replies may finish after a proxy backend switch. Only the server
		// connection that advertised this capability may update the passive HUD.
		if (serverSessionId == null || !serverSessionId.toString().equals(string(object, "sessionId"))) return;
		long cleared = longValue(object, "highestZombieClear");
		String tier = string(object, "tier");
		JsonArray capes = array(object, "capes", 1);
		if (capes == null || (cleared == 25 && !"ZOMBIE_UNLOCKED".equals(tier))
			|| (cleared < 25 && !"LOCKED".equals(tier))) return;
		// The quest packet unlocks the passive HUD. Cape ownership is supplied only by
		// the account backend's cosmetics entitlement, never by this server packet.
		if (!capes.isEmpty()) return;
		quest = new QuestState((int) cleared, cleared == 25);
		lastQuestAtNanos = System.nanoTime();
	}

	private static void sendProof() {
		long now = System.nanoTime();
		if (lastAuthAtNanos != 0 && now - lastAuthAtNanos < AUTH_RETRY_INTERVAL_NANOS) return;
		String token = HgnDungeonProof.token();
		if (token == null || !HgnPlatform.get().canSendDungeonsAuth()) return;
		HgnPlatform.get().sendDungeonsAuth(token);
		lastAuthAtNanos = now;
	}

	public static synchronized void handleCatalog(String json) {
		JsonObject object = parse(json, "catalog");
		if (object == null || !integer(object, "affinityVersion", 1, 1)
			|| !integer(object, "powerCatalogVersion", 2, 2)
			|| capabilities == null) return;
		JsonArray powers = array(object, "powers", 256);
		JsonArray modifiers = array(object, "modifierDefinitions", 64);
		if (powers == null || modifiers == null) return;
		for (JsonElement element : powers) {
			if (!element.isJsonObject()) return;
			JsonObject power = element.getAsJsonObject();
			if (!identifier(power, "id", 64) || !text(power, "name", 96)
				|| !identifier(power, "element", 64)) return;
		}
		for (JsonElement element : modifiers) {
			if (!element.isJsonObject()) return;
			JsonObject modifier = element.getAsJsonObject();
			if (!identifier(modifier, "tag", 64)) return;
		}
		catalog = object.deepCopy();
	}

	public static synchronized void handleMobs(String json) {
		JsonObject object = parse(json, "mob_snapshot");
		if (object == null || capabilities == null || catalog == null) return;
		JsonArray mobs = array(object, "mobs", 32);
		if (mobs == null) return;
		JsonElement run = object.get("runId");
		if (run == null) return;
		Long nextSequence = longValue(object, "seq");
		if (nextSequence == null || nextSequence <= sequence) return;
		if (run.isJsonNull()) {
			if (!mobs.isEmpty()) return;
			clearRun();
			sequence = nextSequence;
			return;
		}
		if (!integer(object, "wave", 0, 1_000_000)
			|| !integer(object, "totalNearby", 0, 1_000_000)
			|| !bool(object, "truncated")) return;
		long total = longValue(object, "totalNearby");
		if (total < mobs.size() || object.get("truncated").getAsBoolean() != (total > mobs.size())) return;
		if (!text(object, "runId", 36)
			|| !identifier(object, "dungeonId", 64)
			|| !identifier(object, "difficulty", 64)) return;
		UUID nextRun;
		try {
			nextRun = UUID.fromString(run.getAsString());
			if (!nextRun.toString().equalsIgnoreCase(run.getAsString())) return;
		} catch (IllegalArgumentException exception) {
			return;
		}
		if (runId != null && !nextRun.equals(runId)) clearRun();
		for (JsonElement element : mobs) {
			if (!element.isJsonObject() || !validMob(element.getAsJsonObject())) return;
		}
		runId = nextRun;
		sequence = nextSequence;
		snapshot = object.deepCopy();
		if (mobs.isEmpty()) insight = null;
		else {
			JsonObject first = mobs.get(0).getAsJsonObject();
			insight = new MobInsight(string(first, "name"), string(first, "role"),
				first.get("hp").getAsDouble(), first.get("maxHp").getAsDouble(),
				bestPower(first));
		}
		receivedAtNanos = System.nanoTime();
	}

	private static String bestPower(JsonObject mob) {
		String bestId = null;
		double bestMultiplier = 1.2;
		for (String group : new String[] { "deadly", "effective" }) {
			for (var entry : mob.getAsJsonObject(group).entrySet()) {
				double multiplier = entry.getValue().getAsDouble();
				if (multiplier > bestMultiplier) {
					bestId = entry.getKey();
					bestMultiplier = multiplier;
				}
			}
		}
		if (bestId == null || catalog == null) return null;
		for (JsonElement element : catalog.getAsJsonArray("powers")) {
			JsonObject power = element.getAsJsonObject();
			if (bestId.equals(string(power, "id"))) {
				return string(power, "name") + " x" + String.format(
					java.util.Locale.ROOT, "%.1f", bestMultiplier);
			}
		}
		return null;
	}

	private static void clearRun() {
		runId = null;
		snapshot = null;
		insight = null;
		receivedAtNanos = 0;
	}

	private static JsonObject parse(String json, String kind) {
		if (!trusted() || json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
			|| !boundedDepth(json)) return null;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) return null;
			JsonObject object = parsed.getAsJsonObject();
			return integer(object, "schema", 1, 1) && kind.equals(string(object, "kind"))
				? object : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean boundedDepth(String json) {
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = 0; index < json.length(); index++) {
			char character = json.charAt(index);
			if (escaped) {
				escaped = false;
			} else if (quoted && character == '\\') {
				escaped = true;
			} else if (character == '"') {
				quoted = !quoted;
			} else if (!quoted && (character == '{' || character == '[')) {
				if (++depth > MAX_DEPTH) return false;
			} else if (!quoted && (character == '}' || character == ']')) {
				if (--depth < 0) return false;
			}
		}
		return !quoted && depth == 0;
	}

	private static boolean validMob(JsonObject mob) {
		String role = string(mob, "role");
		if (!text(mob, "id", 36) || !text(mob, "name", 96)
			|| !identifier(mob, "mobType", 64)
			|| role == null || !ROLES.contains(role)
			|| !integer(mob, "affinityVersion", 1, 1)
			|| !bool(mob, "boss")
			|| !number(mob, "hp", 0, 1_000_000_000)
			|| !number(mob, "maxHp", 0, 1_000_000_000)
			|| !number(mob, "damage", 0, 1_000_000_000)
			|| !number(mob, "x", -30_000_000, 30_000_000)
			|| !number(mob, "y", -30_000_000, 30_000_000)
			|| !number(mob, "z", -30_000_000, 30_000_000)) return false;
		try {
			String id = string(mob, "id");
			if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) return false;
		} catch (IllegalArgumentException exception) {
			return false;
		}
		JsonArray tags = array(mob, "modifiers", 16);
		if (tags == null) return false;
		for (JsonElement tag : tags) {
			if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()
				|| tag.getAsString().length() > 64) return false;
		}
		return multipliers(mob, "weak") && multipliers(mob, "effective")
			&& multipliers(mob, "deadly") && multipliers(mob, "eventAttributes");
	}

	private static boolean multipliers(JsonObject parent, String key) {
		JsonElement field = parent.get(key);
		if (field == null || !field.isJsonObject() || field.getAsJsonObject().size() > 64) return false;
		for (var entry : field.getAsJsonObject().entrySet()) {
			if (entry.getKey().length() > 64 || !entry.getKey().matches("[A-Za-z0-9_]{1,64}")
				|| !finite(entry.getValue(), 0, 100)) return false;
		}
		return true;
	}

	private static boolean text(JsonObject object, String key, int maximum) {
		String value = string(object, key);
		return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= maximum;
	}

	private static boolean identifier(JsonObject object, String key, int maximum) {
		String value = string(object, key);
		return value != null && value.length() <= maximum && value.matches("[A-Za-z0-9_.:-]+");
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : null;
	}

	private static boolean integer(JsonObject object, String key, int minimum, int maximum) {
		Long value = longValue(object, key);
		return value != null && value >= minimum && value <= maximum;
	}

	private static Long longValue(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
		String digits = value.getAsString();
		if (!digits.matches("(?:0|[1-9][0-9]{0,17})")) return null;
		try {
			return Long.parseLong(digits);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static boolean bool(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
	}

	private static boolean number(JsonObject object, String key, double minimum, double maximum) {
		return finite(object.get(key), minimum, maximum);
	}

	private static boolean finite(JsonElement value, double minimum, double maximum) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
		try {
			double parsed = value.getAsDouble();
			return Double.isFinite(parsed) && parsed >= minimum && parsed <= maximum;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static JsonArray array(JsonObject object, String key, int maximum) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonArray() && value.getAsJsonArray().size() <= maximum
			? value.getAsJsonArray() : null;
	}

	private static boolean trusted() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null && minecraft.getCurrentServer() != null
			&& HgnServers.isOfficialAddress(minecraft.getCurrentServer().ip);
	}
}
