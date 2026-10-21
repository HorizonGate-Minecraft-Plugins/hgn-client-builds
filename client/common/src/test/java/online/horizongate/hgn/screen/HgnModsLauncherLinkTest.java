package online.horizongate.hgn.screen;

public final class HgnModsLauncherLinkTest {
	private HgnModsLauncherLinkTest() {
	}

	public static void main(String[] args) {
		String processId = "da75f8c4-1654-45ac-b80d-b9ff03997cac";
		check(null, "hgn://mods");
		check("", "hgn://mods");
		check("not-a-uuid", "hgn://mods");
		check("DA75F8C4-1654-45AC-B80D-B9FF03997CAC", "hgn://mods");
		check(processId, "hgn://mods?processId=" + processId);
		checkSelection(processId, "A1b2C3d4", "E5f6G7h8",
			"hgn://mods/select?processId=" + processId + "&projectId=A1b2C3d4&versionId=E5f6G7h8");
		checkSelection(processId, "a".repeat(64), "Z", "hgn://mods/select?processId="
			+ processId + "&projectId=" + "a".repeat(64) + "&versionId=Z");
		for (String invalidProcess : new String[] { "", "not-a-uuid",
			"DA75F8C4-1654-45AC-B80D-B9FF03997CAC" }) {
			checkSelection(invalidProcess, "A1b2C3d4", "E5f6G7h8", null);
		}
		checkSelection(null, "A1b2C3d4", "E5f6G7h8", null);
		for (String unsafeId : new String[] { "", "sodium-mod", "../mods", "a%2Fb",
			"a b", "\u00E9", "a".repeat(65) }) {
			checkSelection(processId, unsafeId, "E5f6G7h8", null);
			checkSelection(processId, "A1b2C3d4", unsafeId, null);
		}
		checkSelection(processId, null, "E5f6G7h8", null);
		checkSelection(processId, "A1b2C3d4", null, null);
	}

	private static void check(String value, String expected) {
		String actual = HgnModsLauncherLink.fromProcessId(value);
		if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
	}

	private static void checkSelection(String processId, String projectId, String versionId,
		String expected) {
		String actual = HgnModsLauncherLink.fromProcessIdSelection(processId, projectId, versionId);
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(expected + " != " + actual);
		}
	}
}
