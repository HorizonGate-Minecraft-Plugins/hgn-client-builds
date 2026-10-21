package online.horizongate.hgn.update;

import java.nio.charset.StandardCharsets;

/** A game-thread observation, never evidence of consent, saved data or a stopped process. */
public final class HgnUpdateDestination {
	public static final long MAX_GENERATION = 9_007_199_254_740_991L;
	private static final HgnUpdateDestination NONE = new HgnUpdateDestination("none", null, "");
	private final String kind;
	private final Object owner;
	private final String value;

	private HgnUpdateDestination(String kind, Object owner, String value) {
		this.kind = kind;
		this.owner = owner;
		this.value = value;
	}

	public static HgnUpdateDestination none() {
		return NONE;
	}

	public static HgnUpdateDestination world(Object integratedServer, String folder) {
		return integratedServer != null && validText(folder, 255, false)
			? new HgnUpdateDestination("world", integratedServer, folder) : NONE;
	}

	public static HgnUpdateDestination server(Object connection, String address) {
		return connection != null && validText(address, 320, true)
			? new HgnUpdateDestination("server", connection, address) : NONE;
	}

	public boolean sameDestination(HgnUpdateDestination other) {
		return other != null && kind.equals(other.kind) && owner == other.owner && value.equals(other.value);
	}

	public String report(long generation) {
		if (generation < 1 || generation > MAX_GENERATION) throw new IllegalArgumentException("Invalid report generation");
		String json = "{\"protocol\":1,\"generation\":" + generation + ",\"kind\":\"" + kind + "\"";
		if (!kind.equals("none")) json += ",\"" + (kind.equals("world") ? "folder" : "address")
			+ "\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		return json + "}";
	}

	private static boolean validText(String text, int limit, boolean ascii) {
		if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > limit) return false;
		for (int index = 0; index < text.length(); index++) {
			char value = text.charAt(index);
			if (Character.isISOControl(value) || (ascii && value > 127)) return false;
			if (Character.isHighSurrogate(value)) {
				if (++index == text.length() || !Character.isLowSurrogate(text.charAt(index))) return false;
			} else if (Character.isLowSurrogate(value)) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "HgnUpdateDestination { private observation }";
	}
}
