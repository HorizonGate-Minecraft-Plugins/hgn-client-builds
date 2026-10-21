import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import online.horizongate.hgn.screen.HgnInstalledModsIndex;
import online.horizongate.hgn.ui.HgnInstalledModsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Pure-JDK checks for honest installed-jar discovery and compact menu geometry. */
public final class HgnInstalledModsTest {
	public static void main(String[] args) throws Exception {
		checkLayout();
		checkIndex();
		System.out.println("HgnInstalledModsTest passed");
	}

	private static void checkLayout() {
		for (int[] size : new int[][]{{320, 180}, {320, 240}, {550, 350}, {640, 360}, {836, 471},
			{1280, 720}, {1920, 1080}}) {
			HgnInstalledModsLayout layout = HgnInstalledModsLayout.resolve(size[0], size[1]);
			HgnRect screen = new HgnRect(0, 0, size[0], size[1]);
			for (HgnRect panel : new HgnRect[]{layout.header(), layout.navigation(),
				layout.workspace(), layout.detail()}) inside(screen, panel);
			for (HgnRect control : new HgnRect[]{layout.back(), layout.installed(),
				layout.browse(), layout.refresh()}) inside(layout.navigation(), control);
			for (HgnRect control : new HgnRect[]{layout.search(), layout.list(),
				layout.previous(), layout.next()}) inside(layout.workspace(), control);
			inside(layout.compact() ? layout.navigation() : layout.detail(), layout.manage());
			check(layout.header().bottom() < layout.navigation().y(), "Header overlaps navigation");
			if (layout.compact()) {
				inside(layout.workspace(), layout.detail());
				check(layout.back().right() < layout.installed().x()
					&& layout.installed().right() < layout.browse().x()
					&& layout.browse().right() < layout.refresh().x(),
					"Compact navigation overlaps");
			} else {
				check(layout.navigation().right() < layout.workspace().x()
					&& layout.workspace().right() < layout.detail().x(),
					"Wide Mods rails overlap");
			}
			check(layout.search().bottom() < layout.list().y(), "Search overlaps list");
			check(layout.list().bottom() < layout.previous().y(), "List overlaps pager");
			check(layout.previous().right() < layout.next().x(), "Pager controls overlap");
			if (layout.showsEmptyDiscoveryHero()) {
				HgnRect hero = layout.emptyDiscoveryHero();
				HgnRect action = layout.emptyDiscoveryAction();
				inside(layout.list(), hero);
				inside(hero, action);
				if (layout.emptyDiscoveryStacked()) {
					check(action.width() == hero.width() - 24,
						"Stacked Discover action should span the narrow hero");
					check(action.y() >= hero.y() + 140,
						"Stacked Discover action overlaps the hero copy");
				} else {
					check(action.right() <= hero.x() + hero.width() / 2,
						"Discover action overlaps gateway artwork");
				}
			}
			check(layout.rowsPerPage() >= 3, "Installed jars need several visible rows");
			for (int row = 0; row < layout.rowsPerPage(); row++) {
				inside(layout.list(), layout.row(row));
				if (row > 0) check(layout.row(row - 1).bottom() < layout.row(row).y(),
					"Installed jar rows overlap");
			}
			check(layout.pages(0) == 1, "Empty view needs one page");
			check(layout.pages(layout.rowsPerPage() + 1) == 2, "Paging dropped a jar");
		}
		check(HgnInstalledModsLayout.resolve(320, 180).compact(), "320x180 needs compact mode");
		check(HgnInstalledModsLayout.resolve(550, 350).compact(), "Narrow panes need compact mode");
		check(!HgnInstalledModsLayout.resolve(640, 360).compact(),
			"1280x720 at GUI scale 2 should show three rails");
		check(HgnInstalledModsLayout.resolve(640, 360).showsEmptyDiscoveryHero()
			&& HgnInstalledModsLayout.resolve(640, 360).emptyDiscoveryStacked(),
			"1280x720 Mods page should show the stacked empty discovery hero");
		check(HgnInstalledModsLayout.resolve(836, 471).showsEmptyDiscoveryHero(),
			"Wide Mods page omitted the empty discovery hero");
	}

	private static void checkIndex() throws Exception {
		Path instance = Files.createTempDirectory("hgn-installed-mods-test-");
		Path mods = instance.resolve("mods");
		Path sodium = mods.resolve("sodium-fabric-0.6.13+mc1.21.4.jar");
		Path iris = mods.resolve("Iris-1.8.5+mc1.21.4.JAR");
		Path disabled = mods.resolve("disabled.jar.disabled");
		Path readme = mods.resolve("readme.txt");
		Path nested = mods.resolve("nested");
		Path nestedJar = nested.resolve("not-in-root.jar");
		try {
			HgnInstalledModsIndex.Snapshot missing = HgnInstalledModsIndex.read(instance);
			check(missing.jars().isEmpty() && missing.problem().isEmpty(),
				"New installation without a mods folder is empty, not broken");
			Files.createDirectories(nested);
			Files.writeString(sodium, "sodium bytes");
			Files.writeString(iris, "iris bytes");
			Files.writeString(disabled, "disabled bytes");
			Files.writeString(readme, "not a jar");
			Files.writeString(nestedJar, "nested bytes");
			HgnInstalledModsIndex.Snapshot found = HgnInstalledModsIndex.read(instance);
			check(found.problem().isEmpty(), "Readable folder reported an error");
			check(found.jars().stream().map(HgnInstalledModsIndex.InstalledJar::fileName)
				.toList().equals(List.of("Iris-1.8.5+mc1.21.4.JAR",
					"sodium-fabric-0.6.13+mc1.21.4.jar")),
				"Only exact root JAR filenames should appear, in sorted order");
			check(found.filter("MC1.21.4").size() == 2, "Search should be case insensitive");
			check(found.filter("sodium").getFirst().fileName().equals(sodium.getFileName().toString()),
				"Search must retain the exact installed filename and version text");
			check(found.filter("missing").isEmpty(), "Search invented a result");
			check(Files.readString(sodium).equals("sodium bytes"), "Index changed installed file");
			boolean immutable = false;
			try {
				found.jars().clear();
			} catch (UnsupportedOperationException expected) {
				immutable = true;
			}
			check(immutable, "Snapshot must not expose mutable jar data");
		} finally {
			Files.deleteIfExists(nestedJar);
			Files.deleteIfExists(nested);
			Files.deleteIfExists(sodium);
			Files.deleteIfExists(iris);
			Files.deleteIfExists(disabled);
			Files.deleteIfExists(readme);
			Files.deleteIfExists(mods);
			Files.deleteIfExists(instance);
		}
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && inner.x() >= outer.x()
			&& inner.y() >= outer.y() && inner.right() <= outer.right()
			&& inner.bottom() <= outer.bottom(), "Control escaped its panel: " + inner);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
