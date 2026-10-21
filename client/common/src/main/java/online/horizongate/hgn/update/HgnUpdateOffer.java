package online.horizongate.hgn.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;

/** Sanitized informational display data, never update or restart authority. */
public record HgnUpdateOffer(String status, String reason, String currentVersion,
	String candidateVersion, String releaseName) {
	public static HgnUpdateOffer checking() {
		return new HgnUpdateOffer("checking", "", null, null, null);
	}

	public static HgnUpdateOffer unavailable() {
		return new HgnUpdateOffer("unavailable", "bridge_unavailable", null, null, null);
	}

	public static HgnUpdateOffer parse(String json) {
		if (json == null || json.length() > 2048) return unavailable();
		try {
			JsonObject value = readObject(json, 1);
			if (value.size() != 7 || !value.has("current_version") || !value.has("candidate_version") || !value.has("release_name")) return unavailable();
			JsonElement protocol = value.get("protocol"), allowed = value.get("can_update");
			if (protocol == null || !protocol.isJsonPrimitive() || !protocol.getAsJsonPrimitive().isNumber() || !"1".equals(protocol.getAsString())
				|| allowed == null || !allowed.isJsonPrimitive() || !allowed.getAsJsonPrimitive().isBoolean() || allowed.getAsBoolean()) return unavailable();
			String status = text(value.get("status")), reason = text(value.get("reason"));
			String current = text(value.get("current_version")), candidate = text(value.get("candidate_version")), release = text(value.get("release_name"));
			boolean empty = value.get("current_version").isJsonNull() && value.get("candidate_version").isJsonNull() && value.get("release_name").isJsonNull();
			boolean valid = ("not_ready".equals(status) && "session_not_running".equals(reason) && empty)
				|| ("unavailable".equals(status) && ("baseline_unknown".equals(reason) || "check_failed".equals(reason) || "bridge_unavailable".equals(reason)) && empty)
				|| ("current".equals(status) && "no_newer_release".equals(reason) && version(current) && value.get("candidate_version").isJsonNull() && value.get("release_name").isJsonNull())
				|| ("available".equals(status) && "restart_unavailable".equals(reason) && version(current) && version(candidate) && display(release));
			return valid ? new HgnUpdateOffer(status, reason, current, candidate, release) : unavailable();
		} catch (IOException | RuntimeException malformed) {
			return unavailable();
		}
	}

	static JsonObject readObject(String json, int protocol) throws IOException {
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			reader.setLenient(false);
			reader.beginObject();
			JsonObject value = new JsonObject();
			while (reader.hasNext()) {
				String key = reader.nextName();
				if (value.has(key)) throw new IOException("Duplicate offer field");
				switch (reader.peek()) {
					case STRING -> value.addProperty(key, reader.nextString());
					case NUMBER -> {
						if (!Integer.toString(protocol).equals(reader.nextString())) throw new IOException("Invalid offer number");
						value.addProperty(key, protocol);
					}
					case BOOLEAN -> value.addProperty(key, reader.nextBoolean());
					case NULL -> { reader.nextNull(); value.add(key, null); }
					default -> throw new IOException("Invalid offer field");
				}
			}
			reader.endObject();
			if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing offer data");
			return value;
		}
	}

	static String text(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	static boolean display(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}");
	}

	static boolean version(String value) {
		if (!display(value) || !value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?(\\+[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?")) return false;
		String core = value.split("\\+", 2)[0];
		int dash = core.indexOf('-');
		if (dash >= 0) for (String part : core.substring(dash + 1).split("\\."))
			if (part.matches("[0-9]+") && part.length() > 1 && part.charAt(0) == '0') return false;
		return true;
	}

	public String title() {
		return switch (status) {
			case "checking" -> "Checking client releases";
			case "current" -> "No newer release found";
			case "available" -> "A newer client release is available";
			case "not_ready" -> "Launcher session is not ready";
			default -> "Release check unavailable";
		};
	}

	public String detail() {
		return switch (status) {
			case "checking" -> "Waiting for this session's launcher check.";
			case "current" -> "No newer compatible release in the checked feed.";
			case "available" -> "Information only. Updating and restarting are not enabled.";
			case "not_ready" -> "The launcher has not confirmed a running session.";
			default -> "baseline_unknown".equals(reason)
				? "The launcher could not verify the installed client baseline."
				: "A verified release result could not be obtained. Try again.";
		};
	}

	public String narration() {
		return title() + ". " + detail() + (currentVersion == null ? "" : " Installed baseline " + currentVersion + ".")
			+ (candidateVersion == null ? "" : " Candidate " + candidateVersion + ", " + releaseName + ".")
			+ " This check does not change files or restart Minecraft.";
	}
}
