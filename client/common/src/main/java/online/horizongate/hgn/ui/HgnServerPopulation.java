package online.horizongate.hgn.ui;

/** Keeps shared-proxy status pings separate from per-server population. */
public final class HgnServerPopulation {
	private HgnServerPopulation() {
	}

	public static String fromMinecraftPing(boolean official, int online, int maximum) {
		if (official) return "Scoped count unavailable";
		if (online < 0 || maximum < 0) return "Players unavailable";
		return online + " / " + maximum + " players";
	}
}
