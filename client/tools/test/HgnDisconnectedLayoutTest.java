import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnDisconnectedLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Isolated geometry/pagination only; never initializes or invokes a native diagnostic action. */
public final class HgnDisconnectedLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int[] size : new int[][] {{320, 180}, {320, 240}, {320, 319}, {320, 320}, {427, 180},
			{619, 180}, {620, 180}, {619, 320}, {620, 320}, {836, 471}, {1152, 676}, {1920, 1080}}) {
			for (int actions = 1; actions <= 3; actions++) {
				for (int lineHeight : new int[] {9, 10, 12}) {
					geometry(size[0], size[1], actions, lineHeight);
					cases++;
				}
			}
		}
		for (int width = 320; width <= 640; width += 10) {
			for (int height = 180; height <= 340; height += 10) {
				geometry(width, height, 3, 9);
				cases++;
			}
		}
		pages();
		for (int[] invalid : new int[][] {{319, 180, 1}, {320, 179, 1}, {320, 180, 0}, {320, 180, 4}})
			check(!HgnDisconnectedLayout.supported(invalid[0], invalid[1], invalid[2]), "Unsupported roster/viewport accepted");
		System.out.println("Disconnection: " + cases + " geometry cases plus complete reason paging, boundary, reading position, null-action and disposal checks passed; no native callbacks/input/paths opened.");
	}

	private static void geometry(int width, int height, int count, int lineHeight) {
		var layout = HgnDisconnectedLayout.resolve(width, height, count, lineHeight);
		List<HgnRect> controls = new ArrayList<>(List.of(layout.title(), layout.reason(), layout.previous(), layout.pageLabel(), layout.next()));
		controls.addAll(layout.actions());
		for (int index = 0; index < controls.size(); index++) {
			var rect = controls.get(index);
			check(rect.x() >= 0 && rect.y() >= 0 && rect.width() > 0 && rect.height() > 0
				&& rect.right() <= width && rect.bottom() <= height, "Outside viewport " + width + "x" + height);
			for (int next = index + 1; next < controls.size(); next++) check(!overlap(rect, controls.get(next)), "Controls overlap");
		}
		check(layout.actions().size() == count, "Native report/return action lost");
		for (var action : layout.actions()) check(action.height() >= 20 && action.width() >= 140
			&& contains(layout.footer(), action), "Native action unusable");
		check(layout.reason().height() >= lineHeight && layout.reason().height() % lineHeight == 0, "Reason clips a partial line");
		check(contains(layout.reasonPanel(), layout.reason()) && contains(layout.reasonPanel(), layout.previous())
			&& contains(layout.reasonPanel(), layout.next()), "Reason or navigation escaped frame");
		check(!overlap(layout.header(), layout.reasonPanel()) && !overlap(layout.reasonPanel(), layout.footer()), "Frames overlap");
	}

	private static void pages() {
		for (int lines : new int[] {0, 1, 2, 3, 7, 80, 10000}) {
			for (int capacity : new int[] {1, 2, 7, 25, 100}) {
				var pages = new HgnDisconnectedLayout.Pages();
				pages.resize(lines, capacity);
				int end = 0;
				for (int page = 0; page < pages.count(); page++) {
					pages.choose(page);
					check(pages.firstLine() == end, "Reason line omitted or duplicated");
					check(pages.endLine() >= pages.firstLine() && pages.endLine() <= lines, "Reason bounds invalid");
					end = pages.endLine();
				}
				check(end == lines, "Last diagnostic line unreachable");
				pages.choose(-1);
				check(pages.page() == 0, "Negative reason page");
				pages.choose(Integer.MAX_VALUE);
				check(pages.page() == pages.count() - 1, "Page not clamped");
				int first = pages.firstLine();
				pages.resize(lines, capacity + 3);
				check(lines == 0 || (pages.firstLine() <= first && pages.endLine() > first), "Resize lost reading position");
				int oldPage = pages.page(), oldCount = pages.count();
				pages.dispose();
				pages.dispose();
				pages.choose(0);
				pages.resize(1, 1);
				check(pages.disposed() && pages.page() == oldPage && pages.count() == oldCount, "Disposed controller accepted stale paging");
			}
		}
		var huge = new HgnDisconnectedLayout.Pages();
		huge.resize(Integer.MAX_VALUE, 2);
		huge.choose(Integer.MAX_VALUE);
		check(huge.endLine() == Integer.MAX_VALUE && huge.firstLine() >= 0, "Reason arithmetic overflow");
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
