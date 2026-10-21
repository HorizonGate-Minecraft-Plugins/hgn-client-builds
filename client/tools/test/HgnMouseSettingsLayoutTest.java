import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnMouseSettingsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Checks pinned sensitivity and every native group/roster across compact and wide viewports. */
public final class HgnMouseSettingsLayoutTest {
	public static void main(String[] args) {
		assertFocusLookup();
		int[][] viewports = {{320, 180}, {427, 180}, {320, 239}, {320, 240}, {320, 259}, {320, 260},
			{619, 260}, {620, 260}, {640, 360}, {640, 480}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (int count : new int[]{1, 2, 3, 4}) {
				boolean[] visited = new boolean[count];
				HgnMouseSettingsLayout first = HgnMouseSettingsLayout.resolve(size[0], size[1], count, 0, 1);
				HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
				for (int page = 0; page < first.pages(); page++) {
					HgnMouseSettingsLayout layout = HgnMouseSettingsLayout.resolve(size[0], size[1], count, page, 1);
					for (HgnRect panel : List.of(layout.header(), layout.sensitivityPanel(), layout.content(), layout.footer())) inside(viewport, panel);
					inside(layout.header(), layout.title());
					inside(layout.header(), layout.counter());
					inside(layout.sensitivityPanel(), layout.sensitivity());
					check(layout.sensitivity().equals(first.sensitivity()), "Sensitivity moved between pages");
					check(layout.sensitivity().width() >= (layout.compact() ? 130 : 288),
						"Sensitivity loses precise pointer range");
					check(layout.tabs().size() == 2 && layout.tabs().equals(first.tabs()), "Group navigation moved or disappeared");
					List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.sensitivity(), layout.previous(), layout.next()));
					widgets.addAll(layout.tabs());
					widgets.addAll(layout.actions());
					widgets.addAll(layout.controls());
					inside(layout.footer(), layout.previous());
					inside(layout.footer(), layout.next());
					for (HgnRect action : layout.actions()) inside(layout.footer(), action);
					for (int index = 0; index < layout.controls().size(); index++) {
						int nativeIndex = layout.firstControl() + index;
						check(!visited[nativeIndex], "Native preference appears on multiple pages");
						visited[nativeIndex] = true;
						check(first.pageContaining(nativeIndex) == page, "Resize chooses the wrong page for focused control");
						HgnRect control = layout.controls().get(index);
						inside(layout.content(), control);
						check(control.width() >= 250 && control.height() >= 20, "Native preference target became too small");
						check(control.contains(control.centerX(), control.centerY()), "Control center outside hit target");
						check(!control.contains(control.right(), control.centerY()), "Right hit boundary must remain exclusive");
					}
					for (int index = 0; index < widgets.size(); index++) {
						inside(viewport, widgets.get(index));
						for (int next = index + 1; next < widgets.size(); next++) disjoint(widgets.get(index), widgets.get(next));
					}
					check(layout.header().bottom() < layout.sensitivityPanel().y(), "Header overlaps sensitivity panel");
					if (layout.compact()) {
						for (HgnRect tab : layout.tabs()) inside(layout.sensitivityPanel(), tab);
					} else {
						check(layout.sensitivityPanel().bottom() < layout.tabs().getFirst().y(),
							"Sensitivity panel overlaps tabs");
					}
					check(layout.tabs().getFirst().bottom() < layout.content().y(), "Tabs overlap preference panel");
					check(layout.content().bottom() < layout.footer().y(), "Preferences overlap footer");
				}
				for (boolean found : visited) check(found, "A native mouse preference is unreachable");
				check(HgnMouseSettingsLayout.resolve(size[0], size[1], count, -1, 1).page() == 0, "Negative page not clamped");
				check(HgnMouseSettingsLayout.resolve(size[0], size[1], count, 999, 1).page() == first.pages() - 1, "Final page not clamped");
				combinations++;
			}
		}
		check(HgnMouseSettingsLayout.resolve(320, 259, 4, 0, 1).compact(), "Short viewport needs compact spacing");
		check(!HgnMouseSettingsLayout.resolve(320, 260, 4, 0, 1).compact(), "260px viewport should retain normal spacing");
		check(HgnMouseSettingsLayout.resolve(320, 180, 3, 0, 1).controls().size() == 3,
			"Compact mouse layout must show three native preferences without shrinking targets");
		check(HgnMouseSettingsLayout.resolve(320, 180, 4, 0, 1).pages() == 2,
			"Compact mouse layout must still page excess native preferences");
		System.out.println("Mouse layout: " + combinations + " viewport/roster combinations passed across every page; immutable-list initial/cleared focus regression passed.");
	}

	private static void assertFocusLookup() {
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		List<Object> immutable = List.of(first, second, third);
		boolean rejectedNull = false;
		try {
			immutable.indexOf(null);
		} catch (NullPointerException expected) {
			rejectedNull = true;
		}
		check(rejectedNull, "Fixture must reproduce wheel/touch immutable-list null lookup rejection");
		check(HgnMouseSettingsLayout.focusedControlIndex(immutable, null) == -1, "Initial focus must not query an immutable list with null");
		check(HgnMouseSettingsLayout.focusedControlIndex(List.of(), null) == -1, "Cleared focus must not query an empty immutable list with null");
		check(HgnMouseSettingsLayout.focusedControlIndex(immutable, first) == 0, "First native control lost its page");
		check(HgnMouseSettingsLayout.focusedControlIndex(immutable, third) == 2, "Focused native control lost its index");
		check(HgnMouseSettingsLayout.focusedControlIndex(immutable, new Object()) == -1, "Footer or group focus must not select a preference page");
		check(HgnMouseSettingsLayout.focusedControlIndex(immutable, null) == -1, "Clearing retained focus must remain safe");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Mouse widget or panel outside bounds");
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Mouse widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
