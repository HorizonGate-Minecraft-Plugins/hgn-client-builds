package online.horizongate.hgn.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Dependency-free local protocol check: no Dungeons server or Eclipse account required. */
public final class HgnBossProtocolSmokeCheck {
	private HgnBossProtocolSmokeCheck() { }

	public static void main(String[] args) {
		String voice = """
			{"v":1,"event_id":"voice-0001","entity_id":"00000000-0000-0000-0000-000000000001",
			"voice_profile":"skeleton","text":"You cannot escape my arrows!","emotion":"threatening",
			"pitch":0.9,"speed":1.05,"volume":0.8,"origin":{"x":100.5,"y":65.0,"z":-42.0},
			"dimension":"minecraft:overworld","range":32,"ttl_ms":3000,"language":"en-US"}
			""";
		String fx = """
			{"v":1,"event_id":"fx-0001","entity_id":"00000000-0000-0000-0000-000000000001",
			"effect_id":"fire_aura_01","effect_type":"aura","particle_type":"minecraft:flame",
			"color":"#FF6600","shape":"helix","radius":2.5,"density":0.5,
			"rotation_speed":1.2,"duration_ms":2000,"origin":{"x":100.5,"y":65.0,"z":-42.0},
			"dimension":"minecraft:overworld"}
			""";
		check(HgnBossEvents.parseVoice(voice) != null, "brief voice example rejected");
		check(HgnBossEvents.validFxJson(fx), "brief FX example rejected");
		check(HgnBossEvents.parseVoice(edit(voice, "v", 2)) == null, "new protocol accepted");
		check(HgnBossEvents.parseVoice(edit(voice, "entity_id", "not-a-uuid")) == null, "invalid UUID accepted");
		check(HgnBossEvents.parseVoice(edit(voice, "ttl_ms", 0)) == null, "expired voice accepted");
		check(HgnBossEvents.parseVoice(edit(voice, "range", 1000)) == null, "oversized range accepted");
		check(HgnBossEvents.parseVoice(voice + " ".repeat(4096)) == null, "oversized voice accepted");
		check(!HgnBossEvents.validFxJson(edit(fx, "shape", "arbitrary")), "unknown shape accepted");
		check(!HgnBossEvents.validFxJson(edit(fx, "particle_type", "minecraft:explosion_emitter")),
			"expensive particle accepted");
		check(!HgnBossEvents.validFxJson(edit(fx, "radius", 500)), "oversized radius accepted");
		check(!HgnBossEvents.validFxJson(edit(fx, "duration_ms", -1)), "expired FX accepted");
	}

	private static String edit(String json, String field, Object value) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		if (value instanceof Number number) object.addProperty(field, number);
		else object.addProperty(field, value.toString());
		return object.toString();
	}

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
