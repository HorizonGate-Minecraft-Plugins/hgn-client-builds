package online.horizongate.hgn.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;

/** CI-only world-list metadata; never starts a world or writes into an existing save. */
public final class HgnWorldMenuSmokeFixture {
	private static Prepared prepared;

	public record Prepared(Path directory, LevelSummary summary) {
	}

	private HgnWorldMenuSmokeFixture() {
	}

	/** Reuses only this process's already-created, recognized CI fixture; never searches user saves. */
	public static Prepared requirePrepared() throws IOException {
		if (!ciFixtureEnabled() || prepared == null) {
			throw new IllegalStateException("World menu smoke requires the existing isolated CI fixture");
		}
		Path base = Minecraft.getInstance().getLevelSource().getBaseDir().toAbsolutePath().normalize();
		Path directory = prepared.directory();
		if (!directory.getParent().equals(base) || Files.isSymbolicLink(directory)
			|| !directory.toRealPath().equals(base.toRealPath().resolve(directory.getFileName()))
			|| !directory.getFileName().toString().equals(prepared.summary().getLevelId())
			|| !"HGN menu smoke fixture".equals(prepared.summary().getLevelName())
			|| !Files.isRegularFile(directory.resolve("level.dat"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Prepared fixture identity changed");
		}
		return prepared;
	}

	/** Returns the recognized fixture summary, or null when this CI fixture was not requested. */
	public static LevelSummary prepare() throws IOException, LevelStorageException {
		if (!Boolean.getBoolean("hgn.smokeWorldFixture")) return null;
		if (!ciFixtureEnabled()) {
			throw new IllegalStateException("World-list fixture requires an explicit CI menu-smoke or visual-capture run");
		}
		if (prepared != null) return requirePrepared().summary();
		var storage = Minecraft.getInstance().getLevelSource();
		Path base = storage.getBaseDir().toAbsolutePath().normalize();
		Files.createDirectories(base);
		Path directory = Files.createTempDirectory(base, "hgn-menu-smoke-");
		if (!directory.getParent().equals(base)) throw new IOException("Fixture escaped the test saves directory");
		CompoundTag version = new CompoundTag();
		version.putInt("Id", SharedConstants.WORLD_VERSION);
		version.putString("Name", HgnBuild.MINECRAFT_VERSION);
		version.putString("Series", "main");
		version.putBoolean("Snapshot", false);
		CompoundTag data = new CompoundTag();
		data.putInt("DataVersion", SharedConstants.WORLD_VERSION);
		data.putInt("version", 19133);
		data.putString("LevelName", "HGN menu smoke fixture");
		data.putInt("GameType", 0);
		data.putLong("LastPlayed", System.currentTimeMillis());
		data.putBoolean("hardcore", false);
		data.putBoolean("allowCommands", false);
		data.put("Version", version);
		CompoundTag root = new CompoundTag();
		root.put("Data", data);
		NbtIo.writeCompressed(root, directory.resolve("level.dat"));
		String id = directory.getFileName().toString();
		LevelSummary summary = storage.loadLevelSummaries(storage.findLevelCandidates()).join().stream()
			.filter(candidate -> id.equals(candidate.getLevelId())
				&& "HGN menu smoke fixture".equals(candidate.getLevelName()))
			.findFirst().orElseThrow(() -> new AssertionError("Native world storage did not recognize the menu fixture"));
		prepared = new Prepared(directory, summary);
		HgnClient.LOGGER.info("HGN_WORLD_FIXTURE_OK: isolated CI metadata recognized; no world launched");
		return summary;
	}

	private static boolean ciFixtureEnabled() {
		return Boolean.getBoolean("hgn.smokeWorldFixture") && "true".equals(System.getenv("GITHUB_ACTIONS"))
			&& ("container-smoke".equals(System.getProperty("hgn.devscreen"))
				|| (HgnClient.visualCaptureSmoke() && isolatedVisualRun()));
	}

	private static boolean isolatedVisualRun() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gameDirectory == null) return false;
		Path run = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
		Path saves = minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize();
		if (run.getFileName() == null || !run.getFileName().toString().equals("run-packaged-visual")
			|| !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS)
			|| !saves.equals(run.resolve("saves"))) return false;
		try {
			return !Files.exists(saves, LinkOption.NOFOLLOW_LINKS)
				|| (Files.isDirectory(saves, LinkOption.NOFOLLOW_LINKS)
					&& saves.toRealPath().equals(run.toRealPath().resolve("saves")));
		} catch (IOException exception) {
			return false;
		}
	}
}
