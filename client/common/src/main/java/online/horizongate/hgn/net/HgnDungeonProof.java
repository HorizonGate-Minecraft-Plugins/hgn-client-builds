package online.horizongate.hgn.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Launcher-provided, account-bound proof. Only the dungeon server can validate it. */
final class HgnDungeonProof {
	private static final int MAX_HANDOFF_BYTES = 1024;
	private static boolean loaded;
	private static String token;
	private static long expiresAt;

	private HgnDungeonProof() {
	}

	static synchronized String token() {
		if (!loaded) load();
		if (expiresAt <= System.currentTimeMillis()) {
			token = null;
			return null;
		}
		return token;
	}

	private static void load() {
		String configured = System.getenv("HGN_DUNGEON_SESSION_FILE");
		if (configured == null || configured.isBlank()) {
			loaded = true;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getUser() == null
			|| minecraft.getUser().getProfileId() == null || minecraft.gameDirectory == null) return;
		loaded = true;
		Path owned = null;
		try {
			owned = ownedFile(configured, minecraft.gameDirectory.toPath());
			long size = Files.size(owned);
			if (size < 1 || size > MAX_HANDOFF_BYTES) throw new IOException("Invalid proof size");
			JsonObject proof = JsonParser.parseString(
				Files.readString(owned, StandardCharsets.UTF_8)).getAsJsonObject();
			UUID playerId = UUID.fromString(proof.get("playerId").getAsString());
			long expiry = proof.get("expiresAt").getAsLong();
			String accessToken = proof.get("accessToken").getAsString();
			if (proof.get("v").getAsInt() != 2
				|| !"dungeon_session".equals(proof.get("type").getAsString())
				|| !playerId.equals(minecraft.getUser().getProfileId())
				|| expiry <= System.currentTimeMillis()
				|| expiry > System.currentTimeMillis() + 12 * 60 * 60 * 1000L
				|| accessToken.length() < 80 || accessToken.length() > 512
				|| !accessToken.matches("[A-Za-z0-9_.-]+")) {
				throw new IOException("Invalid dungeon proof");
			}
			token = accessToken;
			expiresAt = expiry;
		} catch (Exception ignored) {
			token = null;
		} finally {
			if (owned != null) {
				try {
					Files.deleteIfExists(owned);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static Path ownedFile(String configured, Path gameDirectory) throws IOException {
		Path supplied = Path.of(configured);
		if (!supplied.isAbsolute()) throw new IOException("Proof path must be absolute");
		Path handoffDirectory = gameDirectory.resolve(".hgn-client").toRealPath();
		Path file = supplied.normalize();
		Path parent = file.getParent();
		String name = file.getFileName() == null ? "" : file.getFileName().toString();
		if (parent == null || !parent.toRealPath().equals(handoffDirectory)
			|| !name.matches("session-[A-Za-z0-9]{6,64}\\.json") || Files.isSymbolicLink(file)) {
			throw new IOException("Proof file is outside the launcher handoff directory");
		}
		BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
			LinkOption.NOFOLLOW_LINKS);
		if (!attributes.isRegularFile()) throw new IOException("Proof file must be regular");
		return file;
	}
}
