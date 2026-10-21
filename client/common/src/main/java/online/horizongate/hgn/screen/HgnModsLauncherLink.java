package online.horizongate.hgn.screen;

import java.util.UUID;
import java.util.regex.Pattern;

/** Builds launcher mod browse and review links without granting install authority. */
public final class HgnModsLauncherLink {
	private static final String BARE_LINK = "hgn://mods";
	private static final String PROCESS_ENV = "HGN_LAUNCH_PROCESS_UUID";
	private static final Pattern MODRINTH_ID = Pattern.compile("[A-Za-z0-9]{1,64}");

	private HgnModsLauncherLink() {
	}

	public static String fromEnvironment() {
		try {
			return fromProcessId(System.getenv(PROCESS_ENV));
		} catch (SecurityException ignored) {
			return BARE_LINK;
		}
	}

	public static String fromProcessId(String value) {
		String processId = canonicalProcessId(value);
		return processId == null ? BARE_LINK : BARE_LINK + "?processId=" + processId;
	}

	/** A selection is only a review request; the launcher decides whether installation is allowed. */
	public static String fromEnvironmentSelection(String projectId, String versionId) {
		try {
			return fromProcessIdSelection(System.getenv(PROCESS_ENV), projectId, versionId);
		} catch (SecurityException ignored) {
			return null;
		}
	}

	static String fromProcessIdSelection(String value, String projectId, String versionId) {
		String processId = canonicalProcessId(value);
		if (processId == null || !safeModrinthId(projectId) || !safeModrinthId(versionId)) return null;
		return BARE_LINK + "/select?processId=" + processId
			+ "&projectId=" + projectId + "&versionId=" + versionId;
	}

	private static boolean safeModrinthId(String value) {
		return value != null && MODRINTH_ID.matcher(value).matches();
	}

	private static String canonicalProcessId(String value) {
		if (value == null) return null;
		try {
			UUID processId = UUID.fromString(value);
			return processId.toString().equals(value) ? value : null;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
