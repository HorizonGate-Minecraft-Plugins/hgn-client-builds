import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnVideoSettingsLayout;

/** Exercises native rosters, additional entries and section boundaries without Minecraft or display actions. */
public final class HgnVideoSettingsLayoutTest {
	public static void main(String[] args) {
		List<List<Boolean>> rosters = new ArrayList<>();
		for (int count : new int[]{25, 26, 27, 29, 40}) rosters.add(Collections.nCopies(count - 2, false));
		List<Boolean> modern = new ArrayList<>();
		for (int count : new int[]{5, 18, 4}) {
			modern.add(true);
			modern.addAll(Collections.nCopies(count, false));
		}
		rosters.add(modern);
		rosters.add(List.of(true, true, false, true, false, true));
		rosters.add(List.of());
		int[][] viewports = {{320, 180}, {427, 180}, {320, 239}, {320, 259}, {320, 260}, {620, 260},
			{815, 319}, {816, 319}, {815, 320}, {816, 320}, {836, 471},
			{899, 319}, {900, 319}, {899, 320}, {900, 320}, {626, 354}, {627, 354},
			{640, 352}, {640, 360}, {640, 480},
			{1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (List<Boolean> headings : rosters) {
				for (int actionCount : new int[]{1, 2}) {
					HgnVideoSettingsLayout first = HgnVideoSettingsLayout.resolve(size[0], size[1], headings, 0, actionCount);
					boolean[] visited = new boolean[headings.size()];
					int nextIndex = 0;
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					for (int page = 0; page < first.pages(); page++) {
						HgnVideoSettingsLayout layout = HgnVideoSettingsLayout.resolve(size[0], size[1], headings, page, actionCount);
						for (HgnRect panel : List.of(layout.header(), layout.display(), layout.content(), layout.footer())) inside(viewport, panel);
						if (layout.reference()) {
							HgnDashboardLayout canvas = HgnDashboardLayout.resolve(size[0], size[1]);
							HgnReferenceShellLayout shell = new HgnReferenceShellLayout(canvas);
							HgnRect profile = canvas.rect(689, 40, 138, 190);
							inside(viewport, shell.rail());
							inside(shell.rail(), shell.destination(0));
							inside(viewport, profile);
							for (HgnRect panel : List.of(layout.header(), layout.display(), layout.content(), layout.footer())) {
								disjoint(panel, shell.rail());
								disjoint(panel, profile);
							}
						}
						inside(layout.header(), layout.title());
						inside(layout.header(), layout.counter());
						check(layout.pinned().equals(first.pinned()), "Display controls moved between pages");
						List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.previous(), layout.next()));
						widgets.addAll(layout.pinned());
						widgets.addAll(layout.actions());
						for (HgnRect pinned : layout.pinned()) {
							inside(layout.display(), pinned);
							check(pinned.width() >= 140 && pinned.height() >= 20, "Native display control became too small");
						}
						inside(layout.footer(), layout.previous());
						inside(layout.footer(), layout.next());
						check(layout.actions().size() == actionCount, "Native footer action disappeared");
						for (HgnRect action : layout.actions()) {
							inside(layout.footer(), action);
							check(action.width() >= 60 && action.height() >= 20, "Native footer action became too small");
						}
						for (var slot : layout.entries()) {
							check(slot.index() == nextIndex++, "Native entry order changed");
							check(!visited[slot.index()], "Native entry appears more than once");
							visited[slot.index()] = true;
							check(layout.pageContaining(slot.index()) == page, "Resize chooses wrong focused page");
							inside(layout.content(), slot.bounds());
							check(slot.bounds().width() >= 250, "Native preference is too narrow");
							check(slot.bounds().height() >= (headings.get(slot.index()) ? layout.compact() ? 10 : 14 : 20),
								"Native entry is too short");
							if (headings.get(slot.index()) && slot.index() + 1 < headings.size() && !headings.get(slot.index() + 1)) {
								check(layout.pageContaining(slot.index() + 1) == page, "Section heading orphaned from its first control");
							}
							widgets.add(slot.bounds());
						}
						for (int index = 0; index < widgets.size(); index++) {
							inside(viewport, widgets.get(index));
							for (int next = index + 1; next < widgets.size(); next++) disjoint(widgets.get(index), widgets.get(next));
						}
					}
					for (boolean found : visited) check(found, "A native preference or heading is unreachable");
					check(HgnVideoSettingsLayout.resolve(size[0], size[1], headings, -1, actionCount).page() == 0, "Negative page not clamped");
					check(HgnVideoSettingsLayout.resolve(size[0], size[1], headings, 999, actionCount).page() == first.pages() - 1, "Last page not clamped");
					combinations++;
				}
			}
		}
		check(HgnVideoSettingsLayout.focusedControlIndex(List.of(new Object()), null) == -1, "Unfocused immutable list lookup must be safe");
		Object focused = new Object();
		check(HgnVideoSettingsLayout.focusedControlIndex(List.of(new Object(), focused), focused) == 1, "Retained focused index changed");
		check(!HgnVideoSettingsLayout.resolve(900, 319, modern, 0, 1).sideBySide(), "Short viewport needs stacked display controls");
		check(HgnVideoSettingsLayout.resolve(900, 320, modern, 0, 1).sideBySide(), "Wide viewport needs display lane");
		check(HgnVideoSettingsLayout.resolve(320, 180, List.of(false, false, false, false), 0, 1).entries().size() >= 3,
			"Compact video layout must show at least three native preferences");
		check(HgnVideoSettingsLayout.resolve(320, 180, List.of(true, false, false, false), 0, 1).entries().size() >= 4,
			"Compact video section must show its heading and three preferences together");
		check(!HgnVideoSettingsLayout.resolve(816, 319, modern, 0, 1).sideBySide(), "Short viewport needs stacked display controls");
		check(!HgnVideoSettingsLayout.resolve(815, 320, modern, 0, 1).sideBySide(), "Narrow viewport needs stacked display controls");
		check(!HgnVideoSettingsLayout.resolve(626, 354, modern, 0, 1).reference(),
			"Reference shell must not crowd narrower native controls");
		check(HgnVideoSettingsLayout.resolve(627, 354, modern, 0, 1).reference(),
			"Reference shell should fit at the minimum safe scale");
		check(!HgnVideoSettingsLayout.resolve(640, 352, modern, 0, 1).reference(),
			"Reference shell must not crowd shorter windows");
		check(HgnVideoSettingsLayout.resolve(640, 360, modern, 0, 1).reference(),
			"Reference shell should include common GUI-scale-2 viewports");
		check(HgnVideoSettingsLayout.resolve(836, 471, modern, 0, 1).entries().size() >= 6,
			"Reference-sized video settings should expose several native options per page");
		System.out.println("Video layout: " + combinations + " viewport/roster/footer cases passed with ordered headings, focus and every page.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Video bounds invalid: " + inner + " in " + outer);
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Video widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
