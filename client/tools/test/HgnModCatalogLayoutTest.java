import online.horizongate.hgn.ui.HgnModCatalogLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Checks that search, release selection and launcher controls fit logical game viewports. */
public final class HgnModCatalogLayoutTest {
	public static void main(String[] args) {
		for (int[] size : new int[][]{{320, 180}, {320, 240}, {427, 240}, {500, 260},
			{600, 330}, {640, 360}, {836, 471}, {1280, 720}, {1920, 1080}}) {
			HgnModCatalogLayout layout = HgnModCatalogLayout.resolve(size[0], size[1]);
			HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
			for (HgnRect panel : new HgnRect[]{layout.header(), layout.navigation(),
				layout.projectsPanel(), layout.releasesPanel()}) inside(viewport, panel);
			for (HgnRect control : new HgnRect[]{layout.search(), layout.searchAction(),
				layout.projectList(), layout.releaseList(), layout.projectPrevious(),
				layout.projectNext(), layout.releasePrevious(), layout.releaseNext(),
				layout.review()}) inside(viewport, control);
			for (HgnRect control : new HgnRect[]{layout.back(), layout.installed(),
				layout.browse(), layout.openLauncher()}) inside(layout.navigation(), control);
			inside(layout.projectsPanel(), layout.projectList());
			inside(layout.projectsPanel(), layout.search());
			inside(layout.projectsPanel(), layout.searchAction());
			inside(layout.releasesPanel(), layout.releaseList());
			inside(layout.projectsPanel(), layout.projectPrevious());
			inside(layout.projectsPanel(), layout.projectNext());
			inside(layout.releasesPanel(), layout.releasePrevious());
			inside(layout.releasesPanel(), layout.releaseNext());
			check(layout.search().right() < layout.searchAction().x(),
				"Search field overlaps its action");
			check(layout.search().bottom() < layout.projectList().y(),
				"Search overlaps project results");
			check(layout.projectList().bottom() < layout.projectPrevious().y(),
				"Project list overlaps pager");
			check(layout.releaseList().bottom() < layout.releasePrevious().y(),
				"Release list overlaps pager");
			check(layout.releasePrevious().bottom() < layout.review().y(),
				"Release pager overlaps exact-version action");
			check(layout.projectRowsPerPage() >= 2 && layout.releaseRowsPerPage() >= 2,
				"At least two choices of each kind must be visible");
			for (int row = 0; row < layout.projectRowsPerPage(); row++)
				inside(layout.projectList(), layout.projectRow(row));
			for (int row = 0; row < layout.releaseRowsPerPage(); row++)
				inside(layout.releaseList(), layout.releaseRow(row));
			if (layout.compact()) {
				check(layout.projectsPanel().equals(layout.releasesPanel()),
					"Compact catalogue should swap results and releases in one pane");
			} else {
				check(layout.navigation().right() < layout.projectsPanel().x()
					&& layout.projectsPanel().right() < layout.releasesPanel().x(),
					"Mods navigation, projects and release inspector overlap");
			}
		}
		check(HgnModCatalogLayout.resolve(320, 180).compact(),
			"Small GUI-scale viewport needs one-pane navigation");
		check(!HgnModCatalogLayout.resolve(640, 360).compact(),
			"1280x720 at GUI scale 2 should show both catalogue columns");
		System.out.println("Mod catalogue layout: nine viewport sizes passed.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0
			&& outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1),
			"Catalogue control escaped its pane: " + inner + " outside " + outer);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
