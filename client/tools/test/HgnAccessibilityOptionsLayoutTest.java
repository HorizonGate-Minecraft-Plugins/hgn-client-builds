import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnAccessibilityOptionsLayout;

/** Exercises native rosters, additional entries and section boundaries without Minecraft or display actions. */
public final class HgnAccessibilityOptionsLayoutTest {
	public static void main(String[] args) {
		List<List<Boolean>> rosters = new ArrayList<>();
		for (int count : new int[]{25, 27, 40}) rosters.add(Collections.nCopies(count - 2, false));
		List<Boolean> modern = new ArrayList<>();
		for (int count : new int[]{5, 18, 4}) {
			modern.add(true);
			modern.addAll(Collections.nCopies(count, false));
		}
		rosters.add(modern);
		rosters.add(List.of(true, true, false, true, false, true));
		rosters.add(List.of());
		int[][] viewports = {{320, 180}, {427, 180}, {320, 239}, {320, 259}, {320, 260}, {620, 260},
			{320, 299}, {320, 300}, {899, 359}, {900, 359}, {899, 360}, {900, 360}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (List<Boolean> headings : rosters) {
				for (int actionCount : new int[]{1, 2}) {
					HgnAccessibilityOptionsLayout first = HgnAccessibilityOptionsLayout.resolve(size[0], size[1], headings, 0, actionCount);
					boolean[] visited = new boolean[headings.size()];
					int nextIndex = 0;
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					for (int page = 0; page < first.pages(); page++) {
						HgnAccessibilityOptionsLayout layout = HgnAccessibilityOptionsLayout.resolve(size[0], size[1], headings, page, actionCount);
						for (HgnRect panel : List.of(layout.header(), layout.assistance(), layout.content(), layout.footer())) inside(viewport, panel);
						inside(layout.header(), layout.title());
						inside(layout.header(), layout.counter());
						check(layout.pinned().equals(first.pinned()), "Speech/caption controls moved between pages");
						List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.previous(), layout.next()));
						widgets.addAll(layout.pinned());
						widgets.addAll(layout.actions());
						for (HgnRect pinned : layout.pinned()) {
							inside(layout.assistance(), pinned);
							check(pinned.width() >= 140 && pinned.height() >= 20, "Native speech/caption control became too small");
						}
						inside(layout.footer(), layout.previous());
						inside(layout.footer(), layout.next());
						check(layout.actions().size() == actionCount, "Native footer action disappeared");
						for (HgnRect action : layout.actions()) {
							inside(layout.footer(), action);
							check(action.width() >= 140 && action.height() >= 20, "Native footer action became too small");
						}
						for (var slot : layout.entries()) {
							check(slot.index() == nextIndex++, "Native entry order changed");
							check(!visited[slot.index()], "Native entry appears more than once");
							visited[slot.index()] = true;
							check(layout.pageContaining(slot.index()) == page, "Resize chooses wrong focused page");
							inside(layout.content(), slot.bounds());
							check(slot.bounds().width() >= 250, "Native preference is too narrow");
							check(slot.bounds().height() >= (headings.get(slot.index()) ? 14 : 20), "Native entry is too short");
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
					check(HgnAccessibilityOptionsLayout.resolve(size[0], size[1], headings, -1, actionCount).page() == 0, "Negative page not clamped");
					check(HgnAccessibilityOptionsLayout.resolve(size[0], size[1], headings, 999, actionCount).page() == first.pages() - 1, "Last page not clamped");
					combinations++;
				}
			}
		}
		check(HgnAccessibilityOptionsLayout.focusedControlIndex(List.of(new Object()), null) == -1, "Unfocused immutable list lookup must be safe");
		Object focused = new Object();
		check(HgnAccessibilityOptionsLayout.focusedControlIndex(List.of(new Object(), focused), focused) == 1, "Retained focused index changed");
		check(!HgnAccessibilityOptionsLayout.resolve(900, 359, modern, 0, 1).sideBySide(), "Short viewport needs stacked speech/caption controls");
		check(HgnAccessibilityOptionsLayout.resolve(900, 360, modern, 0, 1).sideBySide(), "Wide viewport needs assistance lane");
		System.out.println("Accessibility layout: " + combinations + " viewport/roster/footer cases passed with ordered headings, focus and every page.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Accessibility bounds invalid: " + inner + " in " + outer);
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Accessibility widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
