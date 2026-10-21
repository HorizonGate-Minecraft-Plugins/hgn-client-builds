import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSoundOptionsLayout;

/** Exercises every retained control across compact pages and wide sound groups. */
public final class HgnSoundOptionsLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 240}, {427, 240}, {620, 240},
			{640, 260}, {660, 320}, {640, 480}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (int count : new int[]{10, 3, 5}) {
				boolean master = count == 10;
				boolean[] visited = new boolean[count];
				HgnSoundOptionsLayout first = HgnSoundOptionsLayout.resolve(size[0], size[1], count, master, 0, 1);
				for (int page = 0; page < first.pages(); page++) {
					HgnSoundOptionsLayout layout = HgnSoundOptionsLayout.resolve(size[0], size[1], count, master, page, 1);
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					inside(viewport, layout.header());
					inside(viewport, layout.content());
					inside(viewport, layout.footer());
					inside(layout.header(), layout.title());
					inside(layout.header(), layout.counter());
					List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.previous(), layout.next()));
					widgets.addAll(layout.tabs());
					widgets.addAll(layout.actions());
					widgets.addAll(layout.controls());
					for (int index = 0; index < layout.controls().size(); index++) {
						int nativeIndex = layout.firstControl() + index;
						check(!visited[nativeIndex], "Native control appears on multiple pages");
						visited[nativeIndex] = true;
						check(first.pageContaining(nativeIndex) == page, "Focus restoration chooses a wrong page");
						HgnRect control = layout.controls().get(index);
						inside(layout.content(), control);
						check(control.width() >= 250 && control.height() >= 20, "Native sound control became unreadably small");
						check(control.contains(control.centerX(), control.centerY()), "Control center is outside hit target");
						check(!control.contains(control.right(), control.centerY()), "Right hit boundary must be exclusive");
					}
					for (int index = 0; index < widgets.size(); index++) {
						HgnRect widget = widgets.get(index);
						inside(viewport, widget);
						for (int next = index + 1; next < widgets.size(); next++) disjoint(widget, widgets.get(next));
					}
					check(layout.header().bottom() < layout.tabs().getFirst().y(), "Title overlaps group tabs");
					check(layout.tabs().getFirst().bottom() < layout.content().y(), "Tabs overlap sound controls");
					check(layout.content().bottom() < layout.footer().y(), "Sound controls overlap footer");
					if (master && page == 0) check(layout.controls().getFirst().width()
						== layout.content().width() - (layout.compact() ? 8 : 16),
						"Master volume must retain its full-width lane");
				}
				for (boolean found : visited) check(found, "A native sound control is unreachable");
				check(HgnSoundOptionsLayout.resolve(size[0], size[1], count, master, -1, 1).page() == 0, "Negative page not clamped");
				check(HgnSoundOptionsLayout.resolve(size[0], size[1], count, master, 999, 1).page() == first.pages() - 1,
					"Final page not clamped after resize");
				combinations++;
			}
		}
		check(HgnSoundOptionsLayout.resolve(320, 180, 10, true, 0, 1).compact(), "Short viewport needs compact spacing");
		check(!HgnSoundOptionsLayout.resolve(320, 240, 10, true, 0, 1).compact(), "240px layout should retain normal spacing");
		check(HgnSoundOptionsLayout.resolve(320, 180, 10, true, 0, 1).controls().size() >= 3,
			"Compact sound layout must show at least three native controls");
		System.out.println("Sound options layout: " + combinations + " viewport/group combinations passed across every page.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Sound widget or panel outside bounds");
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(),
			"Sound widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
