package online.horizongate.hgn.cosmetics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import online.horizongate.hgn.HgnPerformanceGuard;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.HgnServers;

/** Immutable client view of the launcher-membership cosmetics protocol v2. */
public final class HgnCosmetics {
	public static final int PROTOCOL_VERSION = 2;
	private static final Gson GSON = new Gson();
	private static final long MAX_MINUTE_RETRY_SECONDS = 60L;
	private static final long SNAPSHOT_REFRESH_SECONDS = 30L;
	private static final long MAX_SNAPSHOT_RETRY_SECONDS = 60L;
	private static final long MAX_SNAPSHOT_STALENESS_SECONDS = 120L;
	private static final long MAX_REMOTE_LOADOUT_STALENESS_SECONDS = 30L;
	private static final long EQUIPMENT_SYNC_SECONDS = 15L;
	private static final int MAX_VISIBLE_EQUIPMENT_PLAYERS = 32;
	private static final double MAX_EQUIPMENT_DISTANCE_SQUARED = 64.0D * 64.0D;
	private static volatile Snapshot snapshot = Snapshot.unlinked();
	private static volatile String snapshotPlayerId = "";
	private static volatile Map<String, ItemDefinition> catalogueById = Map.of();
	private static volatile Result lastResult = Result.none();
	private static volatile Map<String, Map<String, String>> remoteLoadouts = Map.of();
	private static final AtomicLong renderRevision = new AtomicLong();
	private static final ConcurrentHashMap<String, CachedRenderPlan> renderPlans =
		new ConcurrentHashMap<>();
	private static volatile String previewItemId = "";
	private static volatile boolean snapshotRefreshInFlight;
	private static volatile String pendingSnapshotRequestId = "";
	private static volatile int snapshotRefreshFailureCount;
	private static volatile long nextSnapshotRefreshNanos;
	private static volatile long lastSnapshotAcceptedNanos;
	private static volatile long lastRemoteLoadoutAcceptedNanos;
	private static volatile String activeServer = "";
	private static volatile String playSessionId = UUID.randomUUID().toString();
	private static volatile long minuteSequence;
	private static volatile long lastTickNanos;
	private static volatile long lastActivityNanos;
	private static volatile long activeNanos;
	private static volatile boolean minuteInFlight;
	private static volatile String pendingMinuteRequestId = "";
	private static volatile int minuteFailureCount;
	private static volatile long nextMinuteAttemptNanos;
	private static volatile long nextEquipmentSyncNanos;
	private static volatile boolean equipmentSyncInFlight;
	private static volatile boolean officialServerActive;
	private static double lastX;
	private static double lastY;
	private static double lastZ;
	private static float lastYaw;
	private static float lastPitch;
	private static boolean sampledPlayer;

	private HgnCosmetics() {
	}

	public static void initialize() {
		HgnMembershipClient.initialize();
		if (HgnMembershipClient.available()) refresh();
	}

	public static boolean handle(JsonObject message) {
		if (intValue(message, "v", 0) != PROTOCOL_VERSION) return false;
		return switch (value(message, "type", "")) {
			case "cosmetics_snapshot" -> acceptSnapshot(message);
			case "cosmetics_result" -> {
				Result acceptedResult;
				try {
					acceptedResult = Result.from(message);
				} catch (RuntimeException exception) {
					yield false;
				}
				if (message.has("snapshot") && message.get("snapshot").isJsonObject()) {
					JsonObject nested = message.getAsJsonObject("snapshot");
					nested.addProperty("v", PROTOCOL_VERSION);
					nested.addProperty("type", "cosmetics_snapshot");
					if (!acceptSnapshot(nested)) yield false;
				}
				lastResult = acceptedResult;
				yield true;
			}
			case "cosmetics_equipment" -> {
				String playerId = value(message, "playerId", "");
				if (!playerId.isBlank()) {
					HashMap<String, Map<String, String>> next = new HashMap<>(remoteLoadouts);
					next.put(playerId.toLowerCase(Locale.ROOT),
						stringMap(message.getAsJsonObject("equipped")));
					remoteLoadouts = Map.copyOf(next);
					lastRemoteLoadoutAcceptedNanos = System.nanoTime();
					invalidateRenderPlans();
				}
				yield true;
			}
			case "cosmetics_equipment_batch" -> {
				HashMap<String, Map<String, String>> next = new HashMap<>();
				JsonObject loadouts = object(message, "loadouts");
				for (Map.Entry<String, JsonElement> entry : loadouts.entrySet()) {
					if (entry.getValue().isJsonObject()) {
						next.put(entry.getKey().toLowerCase(Locale.ROOT),
							stringMap(entry.getValue().getAsJsonObject()));
					}
				}
				remoteLoadouts = Map.copyOf(next);
				lastRemoteLoadoutAcceptedNanos = System.nanoTime();
				invalidateRenderPlans();
				yield true;
			}
			default -> false;
		};
	}

	private static synchronized boolean acceptSnapshot(JsonObject message) {
		try {
			Snapshot accepted = Snapshot.from(message);
			String playerId = accepted.profile().playerId();
			UUID parsedPlayerId = UUID.fromString(playerId);
			UUID sessionPlayerId = HgnMembershipClient.playerId();
			if (!parsedPlayerId.toString().equalsIgnoreCase(playerId)
				|| (sessionPlayerId != null && !sessionPlayerId.equals(parsedPlayerId))
				|| (!snapshotPlayerId.isEmpty() && !snapshotPlayerId.equalsIgnoreCase(playerId))) {
				return false;
			}
			HashMap<String, ItemDefinition> definitions = new HashMap<>();
			for (ItemDefinition definition : accepted.catalogue()) {
				definitions.put(definition.id(), definition);
			}
			snapshot = accepted;
			snapshotPlayerId = playerId;
			catalogueById = Map.copyOf(definitions);
			lastSnapshotAcceptedNanos = System.nanoTime();
			invalidateRenderPlans();
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	static boolean acceptLaunchSnapshot(JsonObject message, UUID expectedPlayerId) {
		if (message == null || expectedPlayerId == null
			|| intValue(message, "v", 0) != PROTOCOL_VERSION
			|| !"cosmetics_snapshot".equals(value(message, "type", ""))) return false;
		try {
			Snapshot accepted = Snapshot.from(message);
			if (!expectedPlayerId.toString().equalsIgnoreCase(accepted.profile().playerId())) {
				return false;
			}
			if (!acceptSnapshot(message)) return false;
			lastResult = new Result("", "launch_snapshot", true, "connected",
				"Cosmetics connected.", System.currentTimeMillis());
			nextSnapshotRefreshNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static Snapshot snapshot() {
		expireStaleDisplayState(System.nanoTime());
		return snapshot;
	}

	public static Result lastResult() {
		return lastResult;
	}

	public static boolean noCompatibleMemberPack() {
		return HgnMemberPackStatus.noCompatiblePack(
			System.getenv(HgnMemberPackStatus.STATUS_ENV), HgnMembershipClient.available());
	}

	public static String memberPackNotice(String minecraftVersion, boolean compact) {
		return HgnMemberPackStatus.noticeFor(System.getenv(HgnMemberPackStatus.STATUS_ENV),
			minecraftVersion, HgnMembershipClient.available(), compact);
	}

	public static ConnectionState connectionState() {
		if (HgnMembershipClient.initializing()) return ConnectionState.CONNECTING;
		if (!HgnMembershipClient.available()) {
			if (lastResult.code().contains("expired")) return ConnectionState.EXPIRED;
			if ("membership_launch_unavailable".equals(lastResult.code())) {
				return ConnectionState.MEMBERSHIP_UNAVAILABLE;
			}
			return ConnectionState.LAUNCHER_REQUIRED;
		}
		if (lastSnapshotAcceptedNanos == 0L) {
			return snapshotRefreshFailureCount > 0
				? ConnectionState.OFFLINE : ConnectionState.CONNECTING;
		}
		if (snapshotRefreshFailureCount > 0) {
			return ConnectionState.OFFLINE_CACHED;
		}
		if (!snapshot.profile().launcherLinked()) return ConnectionState.UNLINKED;
		return HgnMemberPackStatus.modelsUnavailable(
			System.getenv(HgnMemberPackStatus.STATUS_ENV), HgnMembershipClient.available())
			? ConnectionState.MODELS_UNAVAILABLE : ConnectionState.CONNECTED;
	}

	public static Map<String, String> equipmentFor(String playerId) {
		expireStaleDisplayState(System.nanoTime());
		Snapshot current = snapshot;
		if (current.profile().playerId().equalsIgnoreCase(playerId)) {
			return current.profile().equipped();
		}
		return remoteLoadouts.getOrDefault(playerId.toLowerCase(Locale.ROOT), Map.of());
	}

	public static ItemDefinition definition(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		expireStaleDisplayState(System.nanoTime());
		return catalogueById.get(itemId);
	}

	/** Immutable resolved equipment for the renderer; rebuilt only after state changes. */
	public static List<EquippedDefinition> renderPlanFor(String playerId) {
		if (!officialServerActive || HgnPerformanceGuard.chunksWarming()
			|| playerId == null || playerId.isBlank()) return List.of();
		String cacheKey = playerId.toLowerCase(Locale.ROOT);
		long revision = renderRevision.get();
		Snapshot current = snapshot;
		Map<String, ItemDefinition> definitions = catalogueById;
		Map<String, String> equipped = current.profile().playerId().equalsIgnoreCase(playerId)
			? current.profile().equipped()
			: remoteLoadouts.getOrDefault(cacheKey, Map.of());
		ItemDefinition preview = current.profile().playerId().equalsIgnoreCase(playerId)
			? definitions.get(previewItemId) : null;
		return renderPlan(cacheKey, revision, equipped, preview, definitions);
	}

	/** Cached local loadout used by menu previews even before an official server join. */
	public static List<EquippedDefinition> previewRenderPlan() {
		expireStaleDisplayState(System.nanoTime());
		long revision = renderRevision.get();
		Snapshot current = snapshot;
		Map<String, ItemDefinition> definitions = catalogueById;
		return renderPlan("@local-preview", revision, current.profile().equipped(),
			definitions.get(previewItemId), definitions);
	}

	/** The gateway avatar never displays a merely selected shop preview. */
	public static List<EquippedDefinition> equippedPreviewRenderPlan() {
		expireStaleDisplayState(System.nanoTime());
		long revision = renderRevision.get();
		Snapshot current = snapshot;
		return renderPlan("@local-equipped", revision, current.profile().equipped(), null,
			catalogueById);
	}

	private static List<EquippedDefinition> renderPlan(String cacheKey, long revision,
		Map<String, String> equipped, ItemDefinition preview,
		Map<String, ItemDefinition> definitions) {
		CachedRenderPlan cached = renderPlans.get(cacheKey);
		if (cached != null && cached.revision() == revision) return cached.entries();
		String previewSlot = preview == null ? "" : preview.equipmentSlot();
		ArrayList<EquippedDefinition> resolved = new ArrayList<>(
			equipped.size() + (preview == null ? 0 : 1));
		for (Map.Entry<String, String> entry : equipped.entrySet()) {
			if (!previewSlot.isEmpty() && previewSlot.equalsIgnoreCase(entry.getKey())) continue;
			ItemDefinition definition = definitions.get(entry.getValue());
			if (definition != null && definition.active()) {
				resolved.add(new EquippedDefinition(entry.getKey(), definition));
			}
		}
		if (preview != null && preview.active()) {
			resolved.add(new EquippedDefinition(preview.equipmentSlot(), preview));
		}
		List<EquippedDefinition> entries = List.copyOf(resolved);
		if (revision == renderRevision.get()) {
			renderPlans.put(cacheKey, new CachedRenderPlan(revision, entries));
		}
		return entries;
	}

	public static void preview(String itemId) {
		String next = itemId == null ? "" : itemId;
		if (next.equals(previewItemId)) return;
		previewItemId = next;
		invalidateRenderPlans();
	}

	public static String previewItemId() {
		return previewItemId;
	}

	public static void reset() {
		snapshot = Snapshot.unlinked();
		snapshotPlayerId = "";
		catalogueById = Map.of();
		lastResult = Result.none();
		remoteLoadouts = Map.of();
		previewItemId = "";
		snapshotRefreshInFlight = false;
		pendingSnapshotRequestId = "";
		snapshotRefreshFailureCount = 0;
		nextSnapshotRefreshNanos = 0L;
		lastSnapshotAcceptedNanos = 0L;
		lastRemoteLoadoutAcceptedNanos = 0L;
		equipmentSyncInFlight = false;
		officialServerActive = false;
		nextEquipmentSyncNanos = 0L;
		invalidateRenderPlans();
	}

	static void membershipSessionExpired(String code, String message) {
		reset();
		activeServer = "";
		resetPlayClock();
		lastResult = new Result("", "membership_session", false, code, message,
			System.currentTimeMillis());
	}

	static void membershipLaunchUnavailable() {
		reset();
		lastResult = new Result("", "membership_session", false,
			"membership_launch_unavailable",
			"Check Account or service in HGN Launcher, then relaunch.",
			System.currentTimeMillis());
	}

	public static void onMinecraftJoin(Minecraft minecraft) {
		String server = currentServer(minecraft);
		setOfficialServerActive(HgnServers.isOfficialAddress(server));
		if (!HgnServers.isVillagerHatRewardAddress(server)) {
			activeServer = "";
			resetPlayClock();
			return;
		}
		activeServer = server;
		resetPlayClock();
	}

	/**
	 * Counts only active time while this member client is connected to an
	 * allowlisted HGN address. It sends a capped one-minute heartbeat to the
	 * launcher backend and never changes or contacts the Minecraft server.
	 */
	public static void tick(Minecraft minecraft) {
		HgnMembershipClient.initialize();
		if (!HgnMembershipClient.available()) return;
		long now = System.nanoTime();
		if (lastTickNanos != 0L && now - lastTickNanos < TimeUnit.MILLISECONDS.toNanos(200)) return;
		expireStaleDisplayState(now);
		boolean chunksWarming = HgnPerformanceGuard.chunksWarming();
		if (!chunksWarming && now >= nextSnapshotRefreshNanos) refresh();
		long elapsed = lastTickNanos == 0L ? 0L
			: Math.max(0L, Math.min(TimeUnit.SECONDS.toNanos(2), now - lastTickNanos));
		lastTickNanos = now;

		String server = currentServer(minecraft);
		boolean officialServer = HgnServers.isOfficialAddress(server);
		setOfficialServerActive(officialServer);
		if (officialServer && chunksWarming) {
			nextEquipmentSyncNanos = Math.max(nextEquipmentSyncNanos,
				now + TimeUnit.SECONDS.toNanos(2L));
		} else if (officialServer && !equipmentSyncInFlight
			&& now >= nextEquipmentSyncNanos) {
			syncVisibleEquipment(minecraft);
		}
		if (!HgnServers.isVillagerHatRewardAddress(server)) {
			if (!activeServer.isEmpty()) resetPlayClock();
			activeServer = "";
			return;
		}
		if (!server.equals(activeServer)) {
			activeServer = server;
			resetPlayClock();
			lastTickNanos = now;
		}

		if (minecraft.player != null) {
			double x = minecraft.player.getX();
			double y = minecraft.player.getY();
			double z = minecraft.player.getZ();
			float yaw = minecraft.player.getYRot();
			float pitch = minecraft.player.getXRot();
			if (!sampledPlayer || squared(x - lastX, y - lastY, z - lastZ) >= 0.0025D
				|| Math.abs(yaw - lastYaw) >= 2.0F || Math.abs(pitch - lastPitch) >= 2.0F) {
				lastActivityNanos = now;
			}
			lastX = x;
			lastY = y;
			lastZ = z;
			lastYaw = yaw;
			lastPitch = pitch;
			sampledPlayer = true;
		}

		if (now - lastActivityNanos <= TimeUnit.SECONDS.toNanos(60)) {
			activeNanos = Math.min(TimeUnit.HOURS.toNanos(24), activeNanos + elapsed);
		}
		if (!chunksWarming && activeNanos >= TimeUnit.MINUTES.toNanos(1) && !minuteInFlight
			&& now >= nextMinuteAttemptNanos) {
			sendActiveMinute();
		}
	}

	public static String refresh() {
		return refresh(false);
	}

	public static String refreshOnPageOpen() {
		return refresh(true);
	}

	private static String refresh(boolean pageOpen) {
		if (!HgnMembershipClient.available()) return "";
		long now = System.nanoTime();
		String requestId;
		synchronized (HgnCosmetics.class) {
			if (snapshotRefreshInFlight || now < nextSnapshotRefreshNanos
				&& (!pageOpen || !HgnCosmeticsPageRefresh.canBypassScheduledRefresh(
					now, lastSnapshotAcceptedNanos, snapshotRefreshFailureCount))) return "";
			requestId = UUID.randomUUID().toString();
			snapshotRefreshInFlight = true;
			pendingSnapshotRequestId = requestId;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("requestId", requestId);
		HgnMembershipClient.MemberRequest request = HgnMembershipClient.post(
			"/v2/cosmetics/snapshot", payload);
		request.whenComplete((response, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				if (!request.current()) return;
				finishSnapshotRefresh(requestId, null, failure);
				return;
			}
			minecraft.execute(() -> {
				if (!request.current()) return;
				finishSnapshotRefresh(requestId, response, failure);
			});
		});
		return requestId;
	}

	public static String equip(String itemId) {
		return request("equip", Map.of("itemId", itemId));
	}

	public static String unequip(String slot) {
		return request("unequip", Map.of("slot", slot));
	}

	private static String request(String operation, Map<String, ?> fields) {
		if (!HgnMembershipClient.available()) return "";
		String requestId = UUID.randomUUID().toString();
		JsonObject payload = new JsonObject();
		payload.addProperty("requestId", requestId);
		for (Map.Entry<String, ?> field : fields.entrySet()) {
			payload.add(field.getKey(), GSON.toJsonTree(field.getValue()));
		}
		String route = switch (operation) {
			case "equip" -> "/v2/cosmetics/equip";
			case "unequip" -> "/v2/cosmetics/unequip";
			default -> "";
		};
		if (route.isEmpty()) return "";
		HgnMembershipClient.deliver(HgnMembershipClient.post(route, payload), requestId, operation);
		return requestId;
	}

	private static void finishSnapshotRefresh(String requestId, JsonObject response, Throwable failure) {
		synchronized (HgnCosmetics.class) {
			if (!requestId.equals(pendingSnapshotRequestId)) return;
			pendingSnapshotRequestId = "";
			snapshotRefreshInFlight = false;
		}
		if (failure != null || response == null) {
			scheduleSnapshotRetry(requestId, HgnMembershipClient.failure(failure));
			return;
		}
		boolean accepted;
		try {
			accepted = "cosmetics_snapshot".equals(value(response, "type", "")) && handle(response);
		} catch (RuntimeException exception) {
			accepted = false;
		}
		if (!accepted) {
			scheduleSnapshotRetry(requestId, new HgnMembershipClient.Failure(0,
				"invalid_cosmetics_snapshot", "The cosmetics service returned an invalid snapshot."));
			return;
		}
		snapshotRefreshFailureCount = 0;
		nextSnapshotRefreshNanos = System.nanoTime()
			+ TimeUnit.SECONDS.toNanos(SNAPSHOT_REFRESH_SECONDS);
	}

	private static void scheduleSnapshotRetry(String requestId, HgnMembershipClient.Failure failure) {
		long baseSeconds = switch (failure.status()) {
			case 401 -> MAX_SNAPSHOT_RETRY_SECONDS;
			case 429 -> 15L;
			case 503 -> 5L;
			default -> 2L;
		};
		int exponent = Math.min(snapshotRefreshFailureCount, 5);
		long delaySeconds = Math.min(MAX_SNAPSHOT_RETRY_SECONDS, baseSeconds * (1L << exponent));
		snapshotRefreshFailureCount = Math.min(snapshotRefreshFailureCount + 1, 10);
		nextSnapshotRefreshNanos = failure.status() == 401
			? Long.MAX_VALUE : System.nanoTime() + TimeUnit.SECONDS.toNanos(delaySeconds);
		lastResult = new Result(requestId, "profile_refresh", false, failure.code(),
			failure.message(), System.currentTimeMillis());
	}

	private static void expireStaleDisplayState(long now) {
		if (lastSnapshotAcceptedNanos != 0L
			&& now - lastSnapshotAcceptedNanos
				> TimeUnit.SECONDS.toNanos(MAX_SNAPSHOT_STALENESS_SECONDS)) {
			snapshot = Snapshot.unlinked();
			previewItemId = "";
			lastSnapshotAcceptedNanos = 0L;
			catalogueById = Map.of();
			invalidateRenderPlans();
		}
		if (lastRemoteLoadoutAcceptedNanos != 0L
			&& now - lastRemoteLoadoutAcceptedNanos
				> TimeUnit.SECONDS.toNanos(MAX_REMOTE_LOADOUT_STALENESS_SECONDS)) {
			remoteLoadouts = Map.of();
			lastRemoteLoadoutAcceptedNanos = 0L;
			invalidateRenderPlans();
		}
	}

	static void transportFailure(String requestId, String operation, String message) {
		lastResult = new Result(requestId, operation, false, "membership_backend_unavailable",
			message, System.currentTimeMillis());
	}

	private static void sendActiveMinute() {
		String requestId = UUID.randomUUID().toString();
		String submittedPlaySessionId = playSessionId;
		long submittedMinuteSequence = minuteSequence;
		minuteInFlight = true;
		pendingMinuteRequestId = requestId;
		JsonObject body = new JsonObject();
		body.addProperty("requestId", requestId);
		body.addProperty("sessionId", submittedPlaySessionId);
		body.addProperty("minuteSequence", submittedMinuteSequence);
		body.addProperty("activeSeconds", 60);
		body.addProperty("afk", false);
		HgnMembershipClient.MemberRequest request = HgnMembershipClient.post(
			"/v2/rewards/active-minute", body);
		request.whenComplete((response, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				if (!request.current()) return;
				if (requestId.equals(pendingMinuteRequestId)) {
					pendingMinuteRequestId = "";
					minuteInFlight = false;
				}
				return;
			}
			minecraft.execute(() -> {
				if (!request.current()) return;
				if (!requestId.equals(pendingMinuteRequestId)
					|| !submittedPlaySessionId.equals(playSessionId)
					|| submittedMinuteSequence != minuteSequence) return;
				pendingMinuteRequestId = "";
				minuteInFlight = false;
				if (failure != null || response == null) {
					scheduleMinuteRetry(HgnMembershipClient.failure(failure));
					return;
				}
				if (!boolValue(response, "minuteRecorded", false)
					&& !boolValue(response, "repeated", false)) {
					scheduleMinuteRetry(new HgnMembershipClient.Failure(0,
						"invalid_active_minute_response", "Verified playtime response was incomplete."));
					return;
				}
				minuteFailureCount = 0;
				nextMinuteAttemptNanos = 0L;
				minuteSequence++;
				activeNanos = Math.max(0L, activeNanos - TimeUnit.MINUTES.toNanos(1));
				if (response.has("snapshot") && response.get("snapshot").isJsonObject()) {
					JsonObject next = response.getAsJsonObject("snapshot");
					next.addProperty("v", PROTOCOL_VERSION);
					next.addProperty("type", "cosmetics_snapshot");
					handle(next);
				}
			});
		});
	}

	private static void scheduleMinuteRetry(HgnMembershipClient.Failure failure) {
		if (failure.status() == 401) {
			nextMinuteAttemptNanos = Long.MAX_VALUE;
			return;
		}
		long baseSeconds = switch (failure.status()) {
			case 429 -> 15L;
			case 503 -> 5L;
			default -> 2L;
		};
		int exponent = Math.min(minuteFailureCount, 5);
		long delaySeconds = Math.min(MAX_MINUTE_RETRY_SECONDS, baseSeconds * (1L << exponent));
		minuteFailureCount = Math.min(minuteFailureCount + 1, 10);
		nextMinuteAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(delaySeconds);
	}

	private static void syncVisibleEquipment(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) return;
		ArrayList<AbstractClientPlayer> visible = new ArrayList<>();
		for (AbstractClientPlayer player : minecraft.level.players()) {
			if (player == minecraft.player || player.isRemoved()
				|| minecraft.player.distanceToSqr(player) > MAX_EQUIPMENT_DISTANCE_SQUARED) continue;
			visible.add(player);
		}
		visible.sort(Comparator.comparingDouble(minecraft.player::distanceToSqr));
		if (visible.isEmpty()) {
			if (!remoteLoadouts.isEmpty()) {
				remoteLoadouts = Map.of();
				lastRemoteLoadoutAcceptedNanos = System.nanoTime();
				invalidateRenderPlans();
			}
			nextEquipmentSyncNanos = System.nanoTime()
				+ TimeUnit.SECONDS.toNanos(EQUIPMENT_SYNC_SECONDS);
			return;
		}
		JsonArray players = new JsonArray();
		for (AbstractClientPlayer player : visible) {
			players.add(player.getUUID().toString());
			if (players.size() >= MAX_VISIBLE_EQUIPMENT_PLAYERS) break;
		}
		JsonObject body = new JsonObject();
		body.add("playerIds", players);
		equipmentSyncInFlight = true;
		HgnMembershipClient.MemberRequest request = HgnMembershipClient.post(
			"/v2/cosmetics/equipment", body);
		request.whenComplete((response, failure) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				equipmentSyncInFlight = false;
				return;
			}
			client.execute(() -> {
				equipmentSyncInFlight = false;
				nextEquipmentSyncNanos = System.nanoTime()
					+ TimeUnit.SECONDS.toNanos(EQUIPMENT_SYNC_SECONDS);
				if (!request.current()) return;
				if (failure != null || response == null) {
					transportFailure("", "equipment_sync",
						HgnMembershipClient.failure(failure).message());
					return;
				}
				handle(response);
			});
		});
	}

	private static void resetPlayClock() {
		playSessionId = UUID.randomUUID().toString();
		minuteSequence = 0L;
		lastTickNanos = 0L;
		lastActivityNanos = System.nanoTime();
		activeNanos = 0L;
		minuteInFlight = false;
		pendingMinuteRequestId = "";
		minuteFailureCount = 0;
		nextMinuteAttemptNanos = 0L;
		nextEquipmentSyncNanos = 0L;
		equipmentSyncInFlight = false;
		sampledPlayer = false;
	}

	private static void invalidateRenderPlans() {
		renderRevision.incrementAndGet();
		renderPlans.clear();
	}

	private static void setOfficialServerActive(boolean active) {
		if (officialServerActive == active) return;
		officialServerActive = active;
		if (!active) {
			remoteLoadouts = Map.of();
			lastRemoteLoadoutAcceptedNanos = 0L;
			nextEquipmentSyncNanos = 0L;
			equipmentSyncInFlight = false;
		}
		invalidateRenderPlans();
	}

	public record EquippedDefinition(String slot, ItemDefinition definition) {
	}

	public enum ConnectionState {
		CONNECTING("CONNECTING", "CONNECTING"),
		CONNECTED("CONNECTED", "CONNECTED"),
		UNLINKED("PROFILE NOT LINKED", "NOT LINKED"),
		OFFLINE_CACHED("OFFLINE - CACHED", "CACHED"),
		OFFLINE("SERVICE OFFLINE", "OFFLINE"),
		EXPIRED("SESSION EXPIRED", "EXPIRED"),
		MODELS_UNAVAILABLE("MODELS UNAVAILABLE", "NO MODELS"),
		MEMBERSHIP_UNAVAILABLE("MEMBERSHIP UNAVAILABLE", "UNAVAILABLE"),
		LAUNCHER_REQUIRED("LAUNCH FROM HGN", "LAUNCHER");

		private final String label;
		private final String compactLabel;

		ConnectionState(String label, String compactLabel) {
			this.label = label;
			this.compactLabel = compactLabel;
		}

		public String label() {
			return label;
		}

		public String compactLabel() {
			return compactLabel;
		}

		public String description() {
			return switch (this) {
				case CONNECTING -> "Loading verified profile";
				case CONNECTED -> "HGN Client member";
				case UNLINKED -> "Link in HGN Launcher";
				case OFFLINE_CACHED -> "Last verified data";
				case OFFLINE -> "Service unavailable";
				case EXPIRED -> "Relaunch to reconnect";
				case MODELS_UNAVAILABLE -> "No model pack for this Minecraft version";
				case MEMBERSHIP_UNAVAILABLE -> "Check Account in HGN Launcher";
				case LAUNCHER_REQUIRED -> "Launch from HGN Launcher";
			};
		}

		public String dashboardDescription() {
			return switch (this) {
				case CONNECTING -> "Loading profile";
				case UNLINKED -> "Link HGN account";
				case MODELS_UNAVAILABLE -> "No models for MC";
				case MEMBERSHIP_UNAVAILABLE -> "Check HGN account";
				case LAUNCHER_REQUIRED -> "Use HGN Launcher";
				default -> description();
			};
		}
	}

	private record CachedRenderPlan(long revision, List<EquippedDefinition> entries) {
	}

	private static String currentServer(Minecraft minecraft) {
		if (minecraft == null || minecraft.hasSingleplayerServer()) return "";
		ServerData data = minecraft.getCurrentServer();
		if (data == null || data.ip == null) return "";
		String value = data.ip.trim().toLowerCase(java.util.Locale.ROOT);
		if (value.startsWith("[")) {
			int end = value.indexOf(']');
			return end > 0 ? value.substring(1, end) : value;
		}
		int colon = value.lastIndexOf(':');
		return colon > 0 && value.indexOf(':') == colon ? value.substring(0, colon) : value;
	}

	private static double squared(double x, double y, double z) {
		return x * x + y * y + z * z;
	}

	public record Snapshot(Profile profile, List<ItemDefinition> catalogue) {
		public Snapshot {
			// Do not invent placeholder cosmetics. Only publisher-approved model
			// packs supplied through the authoritative catalogue may appear here.
			catalogue = List.copyOf(catalogue);
		}

		public static Snapshot unlinked() {
			return new Snapshot(Profile.unlinked(), List.of());
		}

		private static Snapshot from(JsonObject message) {
			JsonObject profileObject = requiredObject(message, "profile");
			Profile profile = Profile.from(profileObject);
			ArrayList<ItemDefinition> catalogue = new ArrayList<>();
			JsonArray items = requiredArray(message, "catalogue");
			for (JsonElement element : items) {
				if (!element.isJsonObject()) continue;
				ItemDefinition definition = ItemDefinition.from(element.getAsJsonObject());
				if (definition.currency().legacy() != profile.legacyCurrency()) {
					throw new IllegalArgumentException("Cosmetics currency does not match the profile schema.");
				}
				catalogue.add(definition);
			}
			return new Snapshot(profile, catalogue);
		}
	}

	public record Profile(String playerId, String hgnGoldenTokens, long hgnTokens,
		boolean launcherLinked, long catalogueRevision,
		Set<String> ownedIds, Map<String, String> equipped,
		boolean legacyCurrency, String legacyCosmeticCoins, String legacyAccountCredits) {
		public Profile {
			ownedIds = Set.copyOf(ownedIds);
			equipped = Map.copyOf(equipped);
		}

		public static Profile unlinked() {
			return new Profile("", "NOT LINKED", -1L, false, 0L, Set.of(), Map.of(), false, "", "");
		}

		private static Profile from(JsonObject object) {
			HashSet<String> owned = new HashSet<>();
			for (JsonElement element : array(object, "ownedIds")) {
				if (element.isJsonPrimitive()) owned.add(element.getAsString());
			}
			boolean linked = boolValue(object, "launcherLinked", false);
			boolean legacy = object.has("cosmeticCoins") || object.has("accountCredits");
			if (legacy && (!object.has("cosmeticCoins") || !object.has("accountCredits")
				|| object.has("hgnTokens") || object.has("hgnGoldenTokens"))) {
				throw new IllegalArgumentException("Cosmetics profile mixes currency schemas.");
			}
			String legacyCoins = legacy ? requiredValue(object, "cosmeticCoins") : "";
			String legacyCredits = legacy ? requiredValue(object, "accountCredits") : "";
			if (legacy && (!legacyCoins.matches("(0|[1-9][0-9]{0,19})")
				|| !(linked ? legacyCredits.matches("(0|[1-9][0-9]{0,16})\\.[0-9]{2}")
					: legacyCredits.equals("NOT LINKED")))) {
				throw new IllegalArgumentException("Cosmetics legacy balance is invalid.");
			}
			String goldenTokens = legacy ? "UNAVAILABLE" : requiredValue(object, "hgnGoldenTokens");
			JsonElement tokenElement = object.get("hgnTokens");
			String tokenValue = !legacy && tokenElement != null && tokenElement.isJsonPrimitive()
				? tokenElement.getAsString() : "";
			if (!legacy && (!isCanonicalWholeUnits(tokenValue)
				|| linked && !isCanonicalWholeUnits(goldenTokens))) {
				throw new IllegalArgumentException("Cosmetics profile currency balance is invalid.");
			}
			return new Profile(requiredValue(object, "playerId"),
				goldenTokens,
				legacy ? -1L : Long.parseLong(tokenValue),
				linked,
				longValue(object, "catalogueRevision", 0L), owned,
				stringMap(object.getAsJsonObject("equipped")), legacy, legacyCoins, legacyCredits);
		}
	}

	public enum Currency {
		HGN_TOKENS("HGN_TOKENS", "HGN Tokens", false),
		HGN_GOLDEN_TOKENS("HGN_GOLDEN_TOKENS", "HGN Golden Tokens", false),
		COSMETIC_COINS("COSMETIC_COINS", "Cosmetic Coins", true),
		ACCOUNT_CREDITS("ACCOUNT_CREDITS", "Account Credits", true);

		private final String wireName;
		private final String displayName;
		private final boolean legacy;

		Currency(String wireName, String displayName, boolean legacy) {
			this.wireName = wireName;
			this.displayName = displayName;
			this.legacy = legacy;
		}

		public String wireName() {
			return wireName;
		}

		public String displayName() {
			return displayName;
		}

		public boolean legacy() {
			return legacy;
		}

		private static Currency from(String value) {
			for (Currency currency : values()) {
				if (currency.wireName.equals(value)) return currency;
			}
			throw new IllegalArgumentException("Unsupported cosmetic currency.");
		}
	}

	public record ItemDefinition(String id, String assetId, long assetRevision, String name,
		String category, String rarity, long price, Currency currency, String equipmentSlot,
		boolean launcherExclusive, boolean active, Set<String> tags, List<String> bundleContents) {
		public ItemDefinition {
			tags = Set.copyOf(tags);
			bundleContents = List.copyOf(bundleContents);
		}

		private static ItemDefinition from(JsonObject object) {
			HashSet<String> tags = new HashSet<>();
			for (JsonElement element : array(object, "tags")) {
				if (element.isJsonPrimitive()) tags.add(element.getAsString());
			}
			ArrayList<String> contents = new ArrayList<>();
			for (JsonElement element : array(object, "bundleContents")) {
				if (element.isJsonPrimitive()) contents.add(element.getAsString());
			}
			return new ItemDefinition(value(object, "id", ""), value(object, "assetId", ""),
				longValue(object, "assetRevision", 0L), value(object, "name", "UNNAMED"),
				value(object, "category", "ALL"), value(object, "rarity", "COMMON"),
				Math.max(0L, longValue(object, "price", 0L)),
				Currency.from(requiredValue(object, "currency")),
				value(object, "equipmentSlot", "NONE"),
				boolValue(object, "launcherExclusive", false),
				boolValue(object, "active", false), tags, contents);
		}
	}

	public record Result(String requestId, String operation, boolean success,
		String code, String message, long receivedAt) {
		public static Result none() {
			return new Result("", "", false, "", "", 0L);
		}

		private static Result from(JsonObject object) {
			return new Result(value(object, "requestId", ""), value(object, "operation", ""),
				boolValue(object, "success", false), value(object, "code", "unknown"),
				value(object, "message", ""), System.currentTimeMillis());
		}
	}

	private static JsonObject object(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
	}

	private static JsonObject requiredObject(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonObject()) {
			throw new IllegalArgumentException("Missing required cosmetics field: " + key);
		}
		return object.getAsJsonObject(key);
	}

	private static JsonArray array(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
	}

	private static JsonArray requiredArray(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonArray()) {
			throw new IllegalArgumentException("Missing required cosmetics field: " + key);
		}
		return object.getAsJsonArray(key);
	}

	private static Map<String, String> stringMap(JsonObject object) {
		if (object == null) return Map.of();
		HashMap<String, String> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
		}
		return Map.copyOf(values);
	}

	private static String value(JsonObject object, String key, String fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static String requiredValue(JsonObject object, String key) {
		if (!object.has(key) || object.get(key).isJsonNull()
			|| !object.get(key).isJsonPrimitive()
			|| !object.getAsJsonPrimitive(key).isString()) {
			throw new IllegalArgumentException("Missing required cosmetics field: " + key);
		}
		return object.get(key).getAsString();
	}

	private static boolean isCanonicalWholeUnits(String value) {
		if (!value.matches("(0|[1-9][0-9]{0,19})")) return false;
		try {
			Long.parseUnsignedLong(value);
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private static int intValue(JsonObject object, String key, int fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
	}

	private static long longValue(JsonObject object, String key, long fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
	}

	private static boolean boolValue(JsonObject object, String key, boolean fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
	}
}
