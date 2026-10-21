import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnPauseCompactLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Checks native-size pause actions and bounded paging at small GUI viewports. */
public final class HgnPauseCompactLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int[] viewport : new int[][] {{320, 180}, {427, 240}, {480, 270},
			{500, 280}, {320, 720}}) {
			int width = viewport[0];
			int height = viewport[1];
			check(HgnPauseCompactLayout.needed(width, height), "Small pause viewport did not reflow");
			HgnPauseCompactLayout layout = HgnPauseCompactLayout.resolve(width, height);
			HgnRect panel = layout.panel();
			HgnRect header = layout.header();
			HgnRect footer = layout.footer();
			check(inside(new HgnRect(0, 0, width, height), panel) && panel.height() <= 228,
				"Pause panel is clipped or overlong");
			check(inside(panel, header) && inside(panel, footer), "Pause header or footer is clipped");
			List<HgnRect> controls = new ArrayList<>();
			for (int index = 0; index < 6; index++) controls.add(layout.essential(index));
			for (int index = 0; index < layout.activitySlots(); index++) controls.add(layout.activity(index));
			controls.add(layout.previous());
			controls.add(layout.next());
			for (int index = 0; index < controls.size(); index++) {
				HgnRect control = controls.get(index);
				check(inside(panel, control) && control.height() >= 20 && control.width() >= 76,
					"Compact pause action is clipped or too small");
				check(control.y() > header.bottom(), "Compact pause action overlaps the header");
				for (int other = index + 1; other < controls.size(); other++) {
					check(!overlaps(control, controls.get(other)), "Compact pause actions overlap");
				}
			}
			check(inside(footer, layout.pageLabel()) && !overlaps(layout.pageLabel(), layout.previous())
				&& !overlaps(layout.pageLabel(), layout.next()), "Pause page label overlaps navigation");
			for (int activities = 0; activities <= 19; activities++) {
				int pages = layout.pages(activities);
				check(layout.clampPage(-1, activities) == 0
					&& layout.clampPage(99, activities) == pages - 1,
					"Compact pause page clamp changed");
				int reached = 0;
				for (int page = 0; page < pages; page++) {
					for (int slot = 0; slot < layout.activitySlots(); slot++) {
						if (page * layout.activitySlots() + slot < activities) reached++;
					}
				}
				check(reached == activities, "Compact pause page lost a secondary action");
			}
			cases++;
		}
		check(HgnPauseCompactLayout.resolve(320, 180).activitySlots() == 2,
			"Minimum viewport must show two secondary actions");
		check(HgnPauseCompactLayout.resolve(427, 240).activitySlots() == 6,
			"427x240 viewport must show six secondary actions");
		check(!HgnPauseCompactLayout.needed(550, 352)
			&& !HgnPauseCompactLayout.needed(836, 471),
			"Normal pause viewports must retain the wide composition");
		System.out.println("Compact pause: " + cases
			+ " viewports retain readable action targets and reachable pages.");
	}

	private static boolean inside(HgnRect parent, HgnRect child) {
		return child.width() > 0 && child.height() > 0
			&& child.x() >= parent.x() && child.y() >= parent.y()
			&& child.right() <= parent.right() && child.bottom() <= parent.bottom();
	}

	private static boolean overlaps(HgnRect first, HgnRect second) {
		return first.x() < second.right() && first.right() > second.x()
			&& first.y() < second.bottom() && first.bottom() > second.y();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
