import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnKeyBindsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Pure geometry: includes 320px where native 340px rows cannot be used unchanged. */
public final class HgnKeyBindsLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {321, 180}, {427, 180}, {320, 239}, {320, 259}, {320, 260},
			{640, 360}, {819, 471}, {820, 471}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		for (int[] size : viewports) {
			HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
			HgnKeyBindsLayout layout = HgnKeyBindsLayout.resolve(size[0], size[1], 2);
			List<HgnRect> panels = List.of(layout.header(), layout.statusPanel(), layout.listPanel(), layout.footer());
			for (int index = 0; index < panels.size(); index++) {
				inside(viewport, panels.get(index));
				for (int next = index + 1; next < panels.size(); next++) disjoint(panels.get(index), panels.get(next));
			}
			inside(layout.header(), layout.title());
			inside(layout.statusPanel(), layout.status());
			inside(layout.statusPanel(), layout.hint());
			check(layout.list().x() == 0 && layout.list().width() == size[0], "Native categories must remain centered on the viewport");
			check(layout.list().y() > layout.listPanel().y() && layout.list().bottom() < layout.listPanel().bottom(), "List escaped frame vertically");
			check(layout.list().height() >= 44, "Compact viewport must retain more than one native row");
			List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.status(), layout.hint(), layout.list()));
			widgets.addAll(layout.actions());
			check(layout.actions().size() == 2, "Reset All and Done must both stay present");
			for (HgnRect action : layout.actions()) {
				inside(layout.footer(), action);
				check(action.width() >= 140 && action.height() == 20, "Native footer targets became too small");
			}
			for (int index = 0; index < widgets.size(); index++) {
				inside(viewport, widgets.get(index));
				for (int next = index + 1; next < widgets.size(); next++) disjoint(widgets.get(index), widgets.get(next));
			}
			// Native row layout: center + 2px inset; scrollbar starts 8px beyond row right.
			int rowLeft = size[0] / 2 - layout.rowWidth() / 2 + 2;
			int rowRight = rowLeft + layout.rowWidth();
			int scrollbar = rowRight + 8;
			int resetX = scrollbar - 50 - 10;
			int changeX = resetX - 5 - 75;
			int labelRight = HgnKeyBindsLayout.labelRight(rowLeft, changeX);
			check(rowLeft >= layout.listPanel().x() + 8 && scrollbar + 6 <= layout.listPanel().right(), "Native row or scrollbar exceeds frame");
			check(resetX + 50 <= rowRight && changeX >= rowLeft, "Native binding buttons exceed the row");
			check(labelRight - rowLeft >= 120, "Compact translated-label lane is too narrow");
			check(labelRight <= changeX - 8, "Visual labels cover native controls or conflict stripe");
			for (int translatedWidth : new int[]{0, 80, 280, 2000}) {
				check(Math.min(rowLeft + translatedWidth, labelRight) <= changeX - 8, "Long translated name escaped clipping lane");
			}
		}
		check(HgnKeyBindsLayout.resolve(320, 180, 2).rowWidth() < 320, "Native 340px width must not leak into compact layout");
		check(HgnKeyBindsLayout.labelRight(100, 90) == 100, "An empty label lane must not invert scissor bounds");
		check(HgnKeyBindsLayout.resolve(320, 259, 2).compact() && !HgnKeyBindsLayout.resolve(320, 260, 2).compact(), "Compact threshold drift");
		System.out.println("Key Binds layout: " + viewports.length + " viewport cases passed, including native button/scrollbar math and long-label clipping bounds.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Widget or panel outside viewport");
	}

	private static void disjoint(HgnRect first, HgnRect second) {
		check(first.right() <= second.x() || second.right() <= first.x() || first.bottom() <= second.y() || second.bottom() <= first.y(), "Page widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
