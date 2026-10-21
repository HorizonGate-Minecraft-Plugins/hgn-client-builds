package online.horizongate.hgn.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.HgnPerformanceGuard;
import online.horizongate.hgn.HgnServers;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Optional, client-only Smart Mob speech and effects; never grants gameplay authority. */
public final class HgnBossEvents {
	private static final int MAX_VOICE_BYTES = 4096;
	private static final int MAX_FX_BYTES = 2048;
	private static final int MAX_EVENTS = 128;
	private static final int MAX_EFFECTS = 24;
	private static final Set<String> SHAPES = Set.of("ring", "spiral", "helix", "trail", "wave", "burst", "orbit", "beam");
	private static final Set<String> PARTICLES = Set.of("minecraft:flame", "minecraft:soul_fire_flame",
		"minecraft:smoke", "minecraft:cloud", "minecraft:end_rod", "minecraft:crit",
		"minecraft:portal", "minecraft:dragon_breath");
	private static final LinkedHashMap<String, Long> SEEN = new LinkedHashMap<>();
	private static final LinkedHashMap<String, Effect> EFFECTS = new LinkedHashMap<>();
	private static ClientLevel currentLevel;
	private static long frame;
	private static long localDemoUntil;

	private HgnBossEvents() {
	}

	public static void handleVoice(String json) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!trusted(minecraft)) return;
		adoptLevel(minecraft.level);
		Voice voice = parseVoice(json);
		if (voice == null || !matchingEntity(minecraft, voice.entityId(), voice.origin())
			|| !voice.dimension().equals(dimension(minecraft))) return;
		long now = System.nanoTime();
		if (!freshEvent("voice:" + voice.eventId(), now, voice.ttlMs())) return;
		if (minecraft.player.distanceToSqr(voice.origin().x(), voice.origin().y(), voice.origin().z())
			> voice.range() * voice.range()) return;
		minecraft.player.displayClientMessage(Component.literal("[" + voice.profile() + "] " + voice.text()), true);
		HgnLocalSpeech.speak(voice, minecraft);
	}

	public static void handleFx(String json) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!trusted(minecraft)) return;
		adoptLevel(minecraft.level);
		Fx fx = parseFx(json);
		if (fx == null || !matchingEntity(minecraft, fx.entityId(), fx.origin())
			|| !fx.dimension().equals(dimension(minecraft))) return;
		long now = System.nanoTime();
		if (!freshEvent("fx:" + fx.eventId(), now, fx.durationMs())) return;
		String key = fx.entityId() + ":" + fx.effectId();
		if (EFFECTS.size() >= MAX_EFFECTS && !EFFECTS.containsKey(key)) {
			EFFECTS.remove(EFFECTS.keySet().iterator().next());
		}
		Effect previous = EFFECTS.get(key);
		EFFECTS.put(key, new Effect(fx, previous == null ? fx : previous.target,
			now, now + fx.durationMs() * 1_000_000L));
	}

	/** Called on the Minecraft client tick; rendering is capped even for hostile packets. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level != currentLevel || !trusted(minecraft) && System.nanoTime() > localDemoUntil) {
			reset();
			currentLevel = minecraft.level;
			return;
		}
		if (!HgnConfig.get().sceneParticles || HgnPerformanceGuard.chunksWarming()) return;
		long now = System.nanoTime();
		int budget = switch (HgnConfig.get().sceneQuality) {
			case LOW -> 12;
			case BALANCED -> 32;
			case HIGH -> 64;
		};
		if (HgnConfig.get().reducedMotion) budget = Math.min(budget, 12);
		frame++;
		Iterator<Effect> iterator = EFFECTS.values().iterator();
		while (iterator.hasNext()) {
			Effect effect = iterator.next();
			if (now >= effect.until || !dimension(minecraft).equals(effect.target.dimension())) {
				iterator.remove();
				continue;
			}
			if (budget <= 0 || minecraft.player.distanceToSqr(effect.target.origin().x(),
				effect.target.origin().y(), effect.target.origin().z()) > 80 * 80) continue;
			if (effect.target.density() == 0) continue;
			int count = Math.min(budget, Math.max(1, (int) Math.ceil(effect.target.density() * 4)));
			for (int i = 0; i < count; i++) emit(minecraft.level, effect, now, i, count);
			budget -= count;
		}
	}

	public static void reset() {
		SEEN.clear();
		EFFECTS.clear();
		localDemoUntil = 0;
		HgnLocalSpeech.reset();
	}

	private static void adoptLevel(ClientLevel level) {
		if (currentLevel != level) {
			reset();
			currentLevel = level;
		}
	}

	/** Local test tool, intentionally independent of Dungeons/Eclipse and never sent to a server. */
	public static void demo(String kind) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) return;
		adoptLevel(minecraft.level);
		localDemoUntil = System.nanoTime() + 3_000_000_000L;
		String id = UUID.randomUUID().toString();
		JsonObject origin = new JsonObject();
		origin.addProperty("x", minecraft.player.getX());
		origin.addProperty("y", minecraft.player.getY());
		origin.addProperty("z", minecraft.player.getZ());
		JsonObject message = new JsonObject();
		message.addProperty("v", 1);
		message.addProperty("event_id", id);
		message.addProperty("entity_id", minecraft.player.getUUID().toString());
		message.add("origin", origin);
		message.addProperty("dimension", dimension(minecraft));
		if ("fx".equals(kind)) {
			message.addProperty("effect_id", "local-demo");
			message.addProperty("effect_type", "aura");
			message.addProperty("particle_type", "minecraft:end_rod");
			message.addProperty("color", "#00CCFF");
			message.addProperty("shape", "helix");
			message.addProperty("radius", 2.5);
			message.addProperty("density", 0.5);
			message.addProperty("rotation_speed", 1.2);
			message.addProperty("duration_ms", 2000);
			Fx fx = parseFx(message.toString());
			if (fx != null) EFFECTS.put(fx.entityId() + ":" + fx.effectId(), new Effect(fx, fx, System.nanoTime(),
				System.nanoTime() + 2_000_000_000L));
		} else {
			message.addProperty("voice_profile", kind);
			message.addProperty("text", "skeleton".equals(kind) ? "You cannot escape my arrows!" : "The dungeon trembles before me!");
			message.addProperty("emotion", "threatening");
			message.addProperty("pitch", "skeleton".equals(kind) ? 1.2 : 0.7);
			message.addProperty("speed", "skeleton".equals(kind) ? 1.1 : 0.8);
			message.addProperty("volume", 0.8);
			message.addProperty("range", 32);
			message.addProperty("ttl_ms", 3000);
			message.addProperty("language", "en-US");
			Voice voice = parseVoice(message.toString());
			if (voice != null) {
				minecraft.player.displayClientMessage(Component.literal("[" + voice.profile() + "] " + voice.text()), true);
				HgnLocalSpeech.speak(voice, minecraft);
			}
		}
	}

	static Voice parseVoice(String json) {
		JsonObject o = object(json, MAX_VOICE_BYTES);
		if (o == null || !Integer.valueOf(1).equals(integer(o, "v", 1, 1))) return null;
		String event = string(o, "event_id", 96);
		UUID entity = uuid(o);
		String profile = string(o, "voice_profile", 48);
		String text = string(o, "text", 240);
		String emotion = string(o, "emotion", 32);
		String dimension = string(o, "dimension", 96);
		String language = string(o, "language", 24);
		Point origin = point(o);
		Double pitch = number(o, "pitch", 0.5, 2.0);
		Double speed = number(o, "speed", 0.5, 2.0);
		Double volume = number(o, "volume", 0, 1);
		Double range = number(o, "range", 1, 64);
		Integer ttl = integer(o, "ttl_ms", 1, 10_000);
		if (event == null || entity == null || profile == null || text == null || emotion == null
			|| dimension == null || language == null || origin == null || pitch == null || speed == null
			|| volume == null || range == null || ttl == null || !language.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?")) return null;
		return new Voice(event, entity, profile.toLowerCase(Locale.ROOT), text, emotion, pitch,
			speed, volume, origin, dimension, range, ttl, language);
	}

	static Fx parseFx(String json) {
		JsonObject o = object(json, MAX_FX_BYTES);
		if (o == null || !Integer.valueOf(1).equals(integer(o, "v", 1, 1))) return null;
		String event = string(o, "event_id", 96);
		UUID entity = uuid(o);
		String effect = string(o, "effect_id", 96);
		String type = string(o, "effect_type", 48);
		String particleName = string(o, "particle_type", 64);
		String color = string(o, "color", 7);
		String shape = string(o, "shape", 24);
		String dimension = string(o, "dimension", 96);
		Point origin = point(o);
		Double radius = number(o, "radius", 0.1, 8);
		Double density = number(o, "density", 0, 1);
		Double rotation = number(o, "rotation_speed", -6, 6);
		Integer duration = integer(o, "duration_ms", 1, 10_000);
		if (event == null || entity == null || effect == null || type == null || particleName == null
			|| color == null || !color.matches("#[0-9a-fA-F]{6}") || shape == null
			|| !SHAPES.contains(shape) || !PARTICLES.contains(particleName) || dimension == null
			|| origin == null || radius == null || density == null || rotation == null || duration == null) return null;
		return new Fx(event, entity, effect, type, particleName, color, shape,
			radius, density, rotation, duration, origin, dimension);
	}

	static boolean validFxJson(String json) {
		return parseFx(json) != null;
	}

	private static void emit(ClientLevel level, Effect effect, long now, int i, int count) {
		Fx fx = effect.target;
		double blend = Math.min(1, (now - effect.started) / 250_000_000.0);
		double radius = effect.previous.radius() + (fx.radius() - effect.previous.radius()) * blend;
		double progress = Math.min(1, (now - effect.started) / (double) (effect.until - effect.started));
		double angle = frame * fx.rotationSpeed() * 0.10 + i * (Math.PI * 2 / count);
		double x = effect.previous.origin().x() + (fx.origin().x() - effect.previous.origin().x()) * blend;
		double y = effect.previous.origin().y() + (fx.origin().y() - effect.previous.origin().y()) * blend;
		double z = effect.previous.origin().z() + (fx.origin().z() - effect.previous.origin().z()) * blend;
		switch (fx.shape()) {
			case "ring", "orbit" -> { x += Math.cos(angle) * radius; z += Math.sin(angle) * radius; }
			case "spiral", "helix" -> { x += Math.cos(angle) * radius; z += Math.sin(angle) * radius; y += (frame % 30) / 30.0 * 2; }
			case "wave" -> { x += Math.cos(angle) * radius * progress; z += Math.sin(angle) * radius * progress; y += Math.sin(angle * 2) * 0.4; }
			case "burst" -> { x += Math.cos(angle) * radius * progress; z += Math.sin(angle) * radius * progress; y += progress; }
			case "trail" -> { x += Math.cos(angle) * radius * 0.3; z += Math.sin(angle) * radius * 0.3; y += i * 0.15; }
			case "beam" -> y += i * radius / count;
			default -> { return; }
		}
		ParticleOptions particle = i % 3 == 0
			? new DustParticleOptions(Integer.parseInt(fx.color().substring(1), 16), 1.0f)
			: particle(fx.particleType());
		level.addParticle(particle, x, y, z, 0, 0.01, 0);
	}

	private static ParticleOptions particle(String name) {
		return switch (name) {
			case "minecraft:flame" -> ParticleTypes.FLAME;
			case "minecraft:soul_fire_flame" -> ParticleTypes.SOUL_FIRE_FLAME;
			case "minecraft:smoke" -> ParticleTypes.SMOKE;
			case "minecraft:cloud" -> ParticleTypes.CLOUD;
			case "minecraft:end_rod" -> ParticleTypes.END_ROD;
			case "minecraft:crit" -> ParticleTypes.CRIT;
			case "minecraft:portal" -> ParticleTypes.PORTAL;
			case "minecraft:dragon_breath" -> ParticleTypes.DRAGON_BREATH;
			default -> ParticleTypes.CLOUD; // Parse-time allowlist makes this unreachable.
		};
	}

	private static boolean trusted(Minecraft minecraft) {
		return minecraft != null && minecraft.level != null && minecraft.player != null
			&& minecraft.getCurrentServer() != null
			&& HgnServers.isOfficialAddress(minecraft.getCurrentServer().ip);
	}

	private static boolean matchingEntity(Minecraft minecraft, UUID entity, Point origin) {
		return !minecraft.level.getEntitiesOfClass(Entity.class,
			new AABB(origin.x() - 8, origin.y() - 8, origin.z() - 8,
				origin.x() + 8, origin.y() + 8, origin.z() + 8),
			candidate -> candidate.getUUID().equals(entity)).isEmpty();
	}

	private static String dimension(Minecraft minecraft) {
		return minecraft.level.dimension().location().toString();
	}

	private static boolean freshEvent(String id, long now, int ttlMs) {
		SEEN.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (SEEN.containsKey(id)) return false;
		while (SEEN.size() >= MAX_EVENTS) SEEN.remove(SEEN.keySet().iterator().next());
		SEEN.put(id, now + ttlMs * 1_000_000L);
		return true;
	}

	private static JsonObject object(String json, int maxBytes) {
		if (json == null || json.getBytes(StandardCharsets.UTF_8).length > maxBytes) return null;
		try {
			JsonElement element = JsonParser.parseString(json);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static String string(JsonObject object, String field, int maximum) {
		try {
			JsonElement value = object.get(field);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
			String text = value.getAsString().trim();
			return !text.isEmpty() && text.length() <= maximum && text.chars().noneMatch(Character::isISOControl)
				? text : null;
		} catch (RuntimeException exception) { return null; }
	}

	private static Integer integer(JsonObject object, String field, int minimum, int maximum) {
		try {
			JsonElement value = object.get(field);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
			String raw = value.getAsString();
			if (!raw.matches("-?[0-9]+")) return null;
			int result = Integer.parseInt(raw);
			return result >= minimum && result <= maximum ? result : null;
		} catch (RuntimeException exception) { return null; }
	}

	private static Double number(JsonObject object, String field, double minimum, double maximum) {
		try {
			JsonElement value = object.get(field);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
			double result = value.getAsDouble();
			return Double.isFinite(result) && result >= minimum && result <= maximum ? result : null;
		} catch (RuntimeException exception) { return null; }
	}

	private static UUID uuid(JsonObject object) {
		String value = string(object, "entity_id", 36);
		try {
			if (value == null) return null;
			UUID parsed = UUID.fromString(value);
			return parsed.toString().equals(value) ? parsed : null;
		}
		catch (RuntimeException exception) { return null; }
	}

	private static Point point(JsonObject object) {
		JsonElement raw = object.get("origin");
		if (raw == null || !raw.isJsonObject()) return null;
		JsonObject origin = raw.getAsJsonObject();
		Double x = number(origin, "x", -30_000_000, 30_000_000);
		Double y = number(origin, "y", -2048, 2048);
		Double z = number(origin, "z", -30_000_000, 30_000_000);
		return x == null || y == null || z == null ? null : new Point(x, y, z);
	}

	public record Point(double x, double y, double z) { }
	public record Voice(String eventId, UUID entityId, String profile, String text, String emotion,
		double pitch, double speed, double volume, Point origin, String dimension, double range,
		int ttlMs, String language) { }
	private record Fx(String eventId, UUID entityId, String effectId, String effectType,
		String particleType, String color, String shape, double radius, double density,
		double rotationSpeed, int durationMs, Point origin, String dimension) { }
	private record Effect(Fx target, Fx previous, long started, long until) { }
}
