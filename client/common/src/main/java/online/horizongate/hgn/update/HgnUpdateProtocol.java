package online.horizongate.hgn.update;

import com.google.gson.JsonObject;
import java.io.IOException;

/** Strict v2 wire data. An offer is not consent; only the explicit controller action prepares it. */
public final class HgnUpdateProtocol {
	private HgnUpdateProtocol() {}

	public record Offer(String status, boolean canUpdate, String reason, String currentVersion,
		String candidateVersion, String releaseName, String id) {
		public static Offer unavailable() { return new Offer("unavailable", false, "bridge_unavailable", null, null, null, null); }
	}

	public record Reply(String id, String status, String reason) {}

	public static Offer offer(String json) {
		try {
			JsonObject value = read(json);
			if (value.size() != 8 || !value.has("current_version") || !value.has("candidate_version")
				|| !value.has("release_name") || !value.has("offer_id")) return Offer.unavailable();
			var allowed = value.get("can_update");
			if (allowed == null || !allowed.isJsonPrimitive() || !allowed.getAsJsonPrimitive().isBoolean()) return Offer.unavailable();
			boolean actionable = allowed.getAsBoolean();
			String status = text(value, "status"), reason = text(value, "reason"), current = text(value, "current_version"),
				candidate = text(value, "candidate_version"), release = text(value, "release_name"), id = text(value, "offer_id");
			boolean empty = nil(value, "current_version") && nil(value, "candidate_version") && nil(value, "release_name");
			boolean valid = (("not_ready".equals(status) || "unavailable".equals(status)) && !actionable && empty && nil(value, "offer_id"))
				|| ("current".equals(status) && !actionable && HgnUpdateOffer.version(current) && nil(value, "candidate_version") && nil(value, "release_name") && nil(value, "offer_id"))
				|| ("available".equals(status) && HgnUpdateOffer.version(current) && HgnUpdateOffer.version(candidate)
					&& HgnUpdateOffer.display(release) && (actionable ? uuid(id) : nil(value, "offer_id")));
			return valid && reason(reason) ? new Offer(status, actionable, reason, current, candidate, release, id) : Offer.unavailable();
		} catch (IOException | RuntimeException malformed) { return Offer.unavailable(); }
	}

	public static Reply reply(String json, String expected) {
		try {
			JsonObject value = read(json);
			String id = text(value, "transaction_id"), status = text(value, "status"), reason = text(value, "reason");
			if (value.size() == 4 && uuid(id) && id.equals(expected) && reason(reason) && status != null
				&& status.matches("preparing|ready|armed|exit_authorized|cancelled|failed")) return new Reply(id, status, reason);
		} catch (IOException | RuntimeException malformed) { }
		return new Reply(expected, "failed", "bridge_unavailable");
	}

	private static JsonObject read(String json) throws IOException {
		if (json == null || json.length() > 2048) throw new IOException("Invalid update reply");
		JsonObject value = HgnUpdateOffer.readObject(json, 2);
		var protocol = value.get("protocol");
		if (protocol == null || !protocol.isJsonPrimitive() || !protocol.getAsJsonPrimitive().isNumber() || !"2".equals(protocol.getAsString())) throw new IOException("Invalid update protocol");
		return value;
	}

	private static String text(JsonObject value, String key) { return HgnUpdateOffer.text(value.get(key)); }
	private static boolean nil(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonNull(); }
	private static boolean reason(String value) { return value != null && value.matches("[a-z][a-z0-9_]{0,63}"); }
	public static boolean uuid(String value) { return value != null && !"00000000-0000-0000-0000-000000000000".equals(value)
		&& value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
}
