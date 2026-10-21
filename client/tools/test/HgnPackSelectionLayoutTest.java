import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnPackSelectionLayout;
import online.horizongate.hgn.ui.HgnPackSelectionLayout.Pane;
import online.horizongate.hgn.ui.HgnRect;

/** Pure layout/identity checks; no native screen, watcher, pack operation or input is exercised. */
public final class HgnPackSelectionLayoutTest {
	private static final class NativeList {
		final List<String> entries;
		final Object selection;
		final Object callback = new Object();
		final String translatedTitle;
		final String search = "unchanged filter";
		final int scroll = 37;

		NativeList(String title, String... entries) {
			this.translatedTitle = title;
			this.entries = List.of(entries);
			selection = this.entries.isEmpty() ? null : this.entries.getLast();
		}
	}

	public static void main(String[] args) {
		int cases = 0;
		for (int[] size : new int[][] {{320, 180}, {320, 181}, {320, 240}, {320, 319}, {320, 320},
			{427, 240}, {559, 180}, {560, 180}, {559, 320}, {560, 320}, {820, 300}, {836, 471},
			{1152, 676}, {1200, 700}, {1920, 1080}}) {
			for (boolean search : new boolean[] {false, true}) {
				verifyGeometry(size[0], size[1], search);
				cases++;
			}
		}
		for (int width = 320; width <= 620; width += 10) {
			for (int height = 180; height <= 340; height += 10) {
				verifyGeometry(width, height, true);
				cases++;
			}
		}
		verifyIdentityAndFocus();
		verifyInvalidState();
		System.out.println("Pack Selection: " + cases + " geometry cases plus native-list identity, null focus, pane switches, resize and disposal checks passed; no native screen/watchers/input/pack commits exercised.");
	}

	private static void verifyGeometry(int width, int height, boolean search) {
		var layout = HgnPackSelectionLayout.resolve(width, height, search);
		List<HgnRect> controls = new ArrayList<>(List.of(layout.title(), layout.hint(), layout.openFolder(), layout.done()));
		if (search) controls.add(layout.search());
		if (layout.dualPane()) controls.addAll(List.of(layout.availableList(), layout.selectedList()));
		else controls.addAll(List.of(layout.availableTab(), layout.selectedTab(), layout.availableList()));
		for (int index = 0; index < controls.size(); index++) {
			var rect = controls.get(index);
			check(rect.x() >= 0 && rect.y() >= 0 && rect.width() > 0 && rect.height() > 0
				&& rect.right() <= width && rect.bottom() <= height, "Control outside viewport " + width + "x" + height);
			for (int next = index + 1; next < controls.size(); next++) check(!overlap(rect, controls.get(next)), "Overlapping native controls");
		}
		for (var list : List.of(layout.availableList(), layout.selectedList())) {
			check(list.width() >= 200, "Native pack move/reorder affordance squeezed");
			check(list.height() >= 49, "Native list cannot show its header and one complete pack row");
		}
		check(contains(layout.header(), layout.title()), "Native title escaped header");
		if (search) check(contains(layout.header(), layout.search()), "Native search escaped header");
		check(contains(layout.footer(), layout.hint()) && contains(layout.footer(), layout.openFolder())
			&& contains(layout.footer(), layout.done()), "Native footer control escaped frame");
		check(layout.openFolder().height() >= 20 && layout.done().height() >= 20, "Native footer controls too short");
		check(contains(layout.availablePanel(), layout.availableList()) && contains(layout.selectedPanel(), layout.selectedList()), "List escaped frame");
		check(!overlap(layout.header(), layout.availablePanel()) && !overlap(layout.footer(), layout.availablePanel()), "Panel overlaps native chrome");
		if (layout.dualPane()) check(!overlap(layout.availablePanel(), layout.selectedPanel()), "Wide native lists overlap");
		else check(layout.availableList().equals(layout.selectedList()), "Compact panes must receive identical usable space");
	}

	private static void verifyIdentityAndFocus() {
		for (String title : List.of("Available", "Verfuegbar", "\u53ef\u7528", "A very long translated available pack title")) {
			var available = new NativeList(title, "unknown mod pack", "vanilla");
			var selected = new NativeList("Selected", "highest priority", "lowest priority");
			var originalEntries = selected.entries;
			var originalSelection = selected.selection;
			var originalCallback = selected.callback;
			var state = new HgnPackSelectionLayout.State<>(available, selected);
			state.arrange(320, 180, true, null, true);
			check(state.pane() == Pane.AVAILABLE && state.visible().equals(List.of(available)), "Null focus changed initial pane");
			state.choose(Pane.SELECTED);
			state.arrange(320, 180, true, available, false);
			check(state.visible().equals(List.of(selected)), "Explicit tab choice followed obsolete list focus");
			state.arrange(1200, 700, true, selected, true);
			check(state.visible().equals(List.of(available, selected)), "Wide view lost a native list");
			state.arrange(320, 180, true, selected, true);
			check(state.visible().equals(List.of(selected)), "Compact resize lost focused selected list");
			state.arrange(320, 180, true, available, true);
			check(state.visible().equals(List.of(available)), "Native transfer focus did not reveal available list");
			state.arrange(320, 180, true, new Object(), true);
			check(state.pane() == Pane.AVAILABLE, "Search/footer focus changed pane");
			state.choose(Pane.SELECTED);
			state.arrange(560, 320, false, null, true);
			state.arrange(559, 319, false, null, true);
			check(state.visible().equals(List.of(selected)), "Unfocused resize discarded explicit pane");
			state.choose(null);
			check(state.pane() == Pane.SELECTED, "Null pane choice mutated selection");
			check(selected.entries == originalEntries && selected.selection == originalSelection
				&& selected.callback == originalCallback && selected.search.equals("unchanged filter") && selected.scroll == 37,
				"Pure arrangement mutated pack order, selection, search, scroll or callback identity");
			check(available.translatedTitle.equals(title), "Translated native title changed");
			var before = state.layout();
			state.dispose();
			state.dispose();
			state.choose(Pane.AVAILABLE);
			state.arrange(1200, 700, true, available, true);
			check(state.disposed() && state.layout() == before && state.pane() == Pane.SELECTED, "Disposed pane accepted stale navigation/resize");
		}
	}

	private static void verifyInvalidState() {
		check(!HgnPackSelectionLayout.supported(319, 180) && !HgnPackSelectionLayout.supported(320, 179), "Unsupported native fallback lost");
		for (int[] size : new int[][] {{319, 180}, {320, 179}, {0, 0}}) {
			try {
				HgnPackSelectionLayout.resolve(size[0], size[1], true);
				throw new AssertionError("Unsupported viewport accepted");
			} catch (IllegalArgumentException expected) { }
		}
		Object list = new Object();
		for (Object[] lists : new Object[][] {{null, list}, {list, null}, {list, list}}) {
			try {
				new HgnPackSelectionLayout.State<>(lists[0], lists[1]);
				throw new AssertionError("Invalid native list roster accepted");
			} catch (IllegalArgumentException expected) { }
		}
	}

	private static boolean contains(HgnRect outer, HgnRect inner) {
		return outer.x() <= inner.x() && outer.y() <= inner.y() && outer.right() >= inner.right() && outer.bottom() >= inner.bottom();
	}

	private static boolean overlap(HgnRect first, HgnRect second) {
		return first.x() < second.right() && second.x() < first.right() && first.y() < second.bottom() && second.y() < first.bottom();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
