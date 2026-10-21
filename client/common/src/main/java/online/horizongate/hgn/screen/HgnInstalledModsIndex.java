package online.horizongate.hgn.screen;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A read-only view of jar filenames in the active instance's mods directory. */
public final class HgnInstalledModsIndex {
	private HgnInstalledModsIndex() {
	}

	public static Snapshot read(Path gameDirectory) {
		Path mods = Objects.requireNonNull(gameDirectory, "gameDirectory").resolve("mods");
		if (Files.notExists(mods)) {
			return new Snapshot(mods, List.of(), "");
		}
		if (!Files.exists(mods)) {
			return new Snapshot(mods, List.of(), "Could not access this instance's mods folder.");
		}
		if (!Files.isDirectory(mods)) {
			return new Snapshot(mods, List.of(), "The mods path is not a folder.");
		}
		List<InstalledJar> jars = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(mods)) {
			for (Path path : entries) {
				if (!Files.isRegularFile(path)) continue;
				String name = path.getFileName().toString();
				if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) jars.add(new InstalledJar(name));
			}
		} catch (IOException | SecurityException error) {
			return new Snapshot(mods, List.of(), "Could not read this instance's mods folder.");
		}
		jars.sort(Comparator.comparing(InstalledJar::fileName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(InstalledJar::fileName));
		return new Snapshot(mods, jars, "");
	}

	/** Exact filename, including any version text present in that filename. */
	public record InstalledJar(String fileName) {
		public InstalledJar {
			Objects.requireNonNull(fileName, "fileName");
		}
	}

	public record Snapshot(Path directory, List<InstalledJar> jars, String problem) {
		public Snapshot {
			Objects.requireNonNull(directory, "directory");
			jars = List.copyOf(jars);
			Objects.requireNonNull(problem, "problem");
		}

		public List<InstalledJar> filter(String query) {
			String needle = Objects.requireNonNull(query, "query").trim().toLowerCase(Locale.ROOT);
			if (needle.isEmpty()) return jars;
			return jars.stream()
				.filter(jar -> jar.fileName().toLowerCase(Locale.ROOT).contains(needle))
				.toList();
		}
	}
}
