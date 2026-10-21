import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnControlsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Covers every native preference and the fixed Mouse/Keybinds lane across compact and wide layouts. */
public final class HgnControlsLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 239}, {320, 240}, {427, 240}, {620, 240},
			{640, 260}, {660, 320}, {640, 480}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (int count : new int[]{4, 7}) {
				boolean[] visited = new boolean[count];
				HgnControlsLayout first = HgnControlsLayout.resolve(size[0], size[1], count, 0, 1);
				for (int page = 0; page < first.pages(); page++) {
					HgnControlsLayout layout = HgnControlsLayout.resolve(size[0], size[1], count, page, 1);
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					for (HgnRect panel : List.of(layout.header(), layout.navigation(), layout.content(), layout.footer())) inside(viewport, panel);
					inside(layout.header(), layout.title());
					inside(layout.header(), layout.counter());
					check(layout.destinations().equals(first.destinations()), "Native destinations moved between pages");
					check(layout.destinations().size() == 2, "Both native destinations must remain visible");
					for (HgnRect destination : layout.destinations()) {
						inside(layout.navigation(), destination);
						check(destination.width() >= 140 && destination.height() >= 20, "Native navigation target became unreadably small");
					}
					List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.previous(), layout.next()));
					widgets.addAll(layout.destinations());
					widgets.addAll(layout.actions());
					widgets.addAll(layout.controls());
					for (int index = 0; index < layout.controls().size(); index++) {
						int nativeIndex = layout.firstControl() + index;
						check(!visited[nativeIndex], "Native preference appears on multiple pages");
						visited[nativeIndex] = true;
						check(first.pageContaining(nativeIndex) == page, "Resize focus restoration chooses a wrong page");
						HgnRect control = layout.controls().get(index);
						inside(layout.content(), control);
						check(control.width() >= 250 && control.height() >= 20, "Native preference became unreadably small");
						check(control.contains(control.centerX(), control.centerY()), "Control center is outside hit target");
						check(!control.contains(control.right(), control.centerY()), "Right hit boundary must be exclusive");
					}
					for (int index = 0; index < widgets.size(); index++) {
						HgnRect widget = widgets.get(index);
						inside(viewport, widget);
						for (int next = index + 1; next < widgets.size(); next++) disjoint(widget, widgets.get(next));
					}
					check(layout.header().bottom() < layout.navigation().y(), "Title overlaps navigation");
					check(layout.navigation().bottom() < layout.content().y(), "Navigation overlaps preferences");
					check(layout.content().bottom() < layout.footer().y(), "Preferences overlap footer");
				}
				for (boolean found : visited) check(found, "A native control preference is unreachable");
				check(HgnControlsLayout.resolve(size[0], size[1], count, -1, 1).page() == 0, "Negative page not clamped");
				check(HgnControlsLayout.resolve(size[0], size[1], count, 999, 1).page() == first.pages() - 1, "Final page not clamped");
				combinations++;
			}
		}
		check(HgnControlsLayout.resolve(320, 239, 4, 0, 1).compact(), "Short viewport needs compact spacing");
		check(!HgnControlsLayout.resolve(320, 240, 4, 0, 1).compact(), "240px layout should retain normal spacing");
		check(HgnControlsLayout.resolve(320, 180, 7, 0, 1).controls().size() >= 3,
			"Compact controls layout must show at least three native preferences");
		System.out.println("Controls layout: " + combinations + " viewport/roster combinations passed across every page.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Controls widget or panel outside bounds");
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Controls widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
