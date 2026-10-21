import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSettingsLayout;

/** Checks compact five-section settings geometry and pagination across logical viewports. */
public final class HgnSettingsLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 200}, {320, 239}, {320, 240},
			{427, 240}, {443, 352}, {444, 352}, {480, 270}, {550, 311}, {550, 312},
			{550, 352}, {640, 360}, {320, 379}, {320, 380}, {854, 480}, {1280, 720},
			{1920, 1080}, {3840, 2160}, {360, 640}, {2560, 720}};
		for (int[] viewport : viewports) {
			for (boolean sections : new boolean[]{false, true}) {
				int items = sections ? 6 : 2;
				HgnSettingsLayout layout = HgnSettingsLayout.resolve(viewport[0], viewport[1], items, sections);
				HgnRect screen = new HgnRect(0, 0, viewport[0], viewport[1]);
				inside(screen, layout.panel(), "Panel outside viewport");
				inside(layout.panel(), layout.content(), "Content outside panel");
				inside(layout.panel(), layout.footer(), "Footer outside panel");
				check(layout.footer().height() >= 22, "Footer control became too small");
				check(layout.rowsPerPage() > 0 && layout.rowsPerPage() <= items, "Invalid page capacity");
				int pages = layout.pages(items);
				check((pages - 1) * layout.rowsPerPage() < items
					&& pages * layout.rowsPerPage() >= items, "Pagination lost a setting");
				for (int index = 0; index < layout.rowsPerPage(); index++) {
					HgnRect row = layout.row(index);
					inside(layout.content(), row, "Setting row outside content");
					check(row.height() >= (sections ? 22 : 34) && row.width() >= 276,
						"Setting row became too small");
					HgnRect title = new HgnRect(row.x() + 12, layout.rowTitleY(index),
						sections ? row.width() - 122 : 120, 10);
					inside(row, title, "Setting title clipped");
					if (layout.showsRowHelp()) {
						HgnRect help = new HgnRect(row.x() + 12, layout.rowHelpY(index),
							sections ? row.width() - 122 : row.width() - 24, 10);
						inside(row, help, "Help text clipped");
						check(title.bottom() < help.y(), "Title overlaps help text");
						if (sections) disjoint(help, layout.settingValue(index), "Help overlaps setting value");
					}
					if (sections) {
						HgnRect value = layout.settingValue(index);
						inside(row, value, "Setting value clipped");
						check(value.width() >= 86 && value.height() >= 18, "Setting value became too small");
						disjoint(title, value, "Title overlaps setting value");
						check(value.x() - title.right() >= 8, "Title and value need a readable gap");
					}
					if (index > 0) disjoint(layout.row(index - 1), row, "Setting rows overlap");
					disjoint(row, layout.footer(), "Setting overlaps footer");
				}
				for (int index = 0; index < 2; index++) {
					HgnRect button = layout.footerButton(index, 2);
					inside(layout.footer(), button, "Footer button clipped");
					check(button.width() >= 100 && button.height() >= 22, "Footer button became too small");
				}
				disjoint(layout.footerButton(0, 2), layout.footerButton(1, 2), "Footer buttons overlap");
				if (sections) {
					inside(layout.panel(), layout.tabs(), "Tabs outside panel");
					for (int index = 0; index < 5; index++) {
						HgnRect tab = layout.tab(index, 5);
						inside(layout.tabs(), tab, "Category tab clipped");
						check(tab.width() >= 48 && tab.height() >= 18, "Category tab became too small");
						for (int row = 0; row < layout.rowsPerPage(); row++) {
							disjoint(tab, layout.row(row), "Category tab overlaps setting row");
						}
						if (pages > 1) for (boolean next : new boolean[]{false, true}) {
							disjoint(tab, layout.pageButton(next), "Category tab overlaps pager");
						}
					}
				for (int index = 1; index < 5; index++) {
					disjoint(layout.tab(index - 1, 5), layout.tab(index, 5), "Category tabs overlap");
				}
					check(layout.headerTitleY() + (layout.compact() ? 10 : 14) < layout.tabs().y(),
						"Header overlaps tabs");
					check(layout.tabs().bottom() < layout.sectionTitleY(), "Tabs overlap section title");
					check(layout.sectionTitleY() + 10 < layout.content().y(),
						"Section title overlaps settings");
					check(layout.showsRowHelp() == (layout.content().width() >= 400),
						"Narrow settings should hide help text");
					if (pages > 1) {
						HgnRect previous = layout.pageButton(false);
						HgnRect next = layout.pageButton(true);
						HgnRect sectionTitle = new HgnRect(layout.content().x(), layout.sectionTitleY(),
							layout.content().width() - 154, 10);
						inside(layout.panel(), previous, "Previous button outside panel");
						inside(layout.panel(), next, "Next button outside panel");
						check(previous.height() >= 20 && next.height() >= 20,
							"Page buttons became too small");
						disjoint(previous, next, "Page buttons overlap");
						disjoint(sectionTitle, previous, "Section title overlaps previous button");
						disjoint(sectionTitle, next, "Section title overlaps next button");
						for (int row = 0; row < layout.rowsPerPage(); row++) {
							disjoint(previous, layout.row(row), "Previous button overlaps setting row");
							disjoint(next, layout.row(row), "Next button overlaps setting row");
						}
					}
				} else check(layout.rowsPerPage() == 2, "Native accessibility destinations need two rows");
			}
		}
		HgnSettingsLayout reference = HgnSettingsLayout.resolve(550, 352, 6, true);
		check(reference.compact() && reference.rowsPerPage() == 6 && reference.pages(6) == 1
			&& reference.rowHeight() == 26 && reference.showsRowHelp(),
			"550x352 settings must show all six rows and help without paging");
		check(reference.panel().height() == 260 && reference.row(5).bottom() < reference.footer().y(),
			"Reference settings panel lost its compact six-row geometry");
		HgnSettingsLayout smallest = HgnSettingsLayout.resolve(320, 180, 6, true);
		check(smallest.rowHeight() == 22 && smallest.rowsPerPage() == 2 && smallest.pages(6) == 3,
			"Smallest settings viewport should show two rows on each of three pages");
		HgnSettingsLayout actualSmallest = HgnSettingsLayout.resolve(320, 180, 3, true);
		check(actualSmallest.rowsPerPage() == 3 && actualSmallest.pages(3) == 1,
			"Each three-control section must fit the smallest settings viewport without paging");
		check(actualSmallest.panel().height() == 158 && actualSmallest.rowHeight() == 22,
			"Smallest three-control section retained unused panel space");
		HgnSettingsLayout compact = HgnSettingsLayout.resolve(427, 240, 6, true);
		check(compact.rowsPerPage() == 5 && compact.pages(6) == 2,
			"Compact settings viewport should page after five rows");
		check(HgnSettingsLayout.resolve(854, 480, 6, true).pages(6) == 1,
			"Large settings viewport should show all six rows");
		check(HgnSettingsLayout.resolve(320, 480, 6, true).rowHeight() == 28
			&& HgnSettingsLayout.resolve(854, 480, 6, true).rowHeight() == 30,
			"Normal settings rows should be 28px narrow and 30px wide");
		HgnSettingsLayout wideHud = HgnSettingsLayout.resolve(640, 360, 3, true);
		check(wideHud.panel().width() == 520 && wideHud.panel().height() == 170
			&& wideHud.rowsPerPage() == 3 && wideHud.rowHeight() == 26,
			"Wide HUD settings should fit all real controls in a compact panel");
		HgnSettingsLayout smallHud = HgnSettingsLayout.resolve(320, 240, 3, true);
		check(smallHud.panel().height() == 158 && smallHud.rowsPerPage() == 3
			&& smallHud.rowHeight() == 22,
			"640x480 high-GUI-scale HUD settings should keep every control reachable");
		HgnSettingsLayout normalHud = HgnSettingsLayout.resolve(640, 480, 3, true);
		check(normalHud.panel().height() == 258 && normalHud.rowsPerPage() == 3
			&& normalHud.rowHeight() == 30,
			"640x480 normal-GUI-scale HUD settings should stay readable and dense");
		check(!HgnSettingsLayout.resolve(443, 352, 6, true).showsRowHelp()
			&& HgnSettingsLayout.resolve(444, 352, 6, true).showsRowHelp(),
			"Help visibility width threshold drifted");
		check(HgnSettingsLayout.resolve(550, 275, 6, true).rowsPerPage() == 5
			&& HgnSettingsLayout.resolve(550, 276, 6, true).rowsPerPage() == 6,
			"Six-row height threshold drifted");
		System.out.println("Settings layouts: " + viewports.length * 2
			+ " viewport/screen combinations passed.");
	}

	private static void inside(HgnRect outer, HgnRect inner, String message) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), message);
	}

	private static void disjoint(HgnRect left, HgnRect right, String message) {
		check(left.right() <= right.x() || right.right() <= left.x()
			|| left.bottom() <= right.y() || right.bottom() <= left.y(), message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
