import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSkinCustomizationLayout;

/** Checks the native layers, persistent handedness and original footer at compact/wide boundaries. */
public final class HgnSkinCustomizationLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 259}, {320, 260}, {320, 300}, {476, 260},
			{739, 299}, {740, 299}, {739, 300}, {740, 300}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (int count : new int[]{1, 7, 8, 15}) {
				for (int actionCount : new int[]{1, 2}) {
					boolean[] visited = new boolean[count];
					HgnSkinCustomizationLayout first = HgnSkinCustomizationLayout.resolve(size[0], size[1], count, 0, actionCount);
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					for (int page = 0; page < first.pages(); page++) {
						HgnSkinCustomizationLayout layout = HgnSkinCustomizationLayout.resolve(size[0], size[1], count, page, actionCount);
						List<HgnRect> panels = List.of(layout.header(), layout.layersPanel(), layout.handPanel(), layout.footer());
						for (int index = 0; index < panels.size(); index++) {
							inside(viewport, panels.get(index));
							for (int other = index + 1; other < panels.size(); other++) disjoint(panels.get(index), panels.get(other));
						}
						inside(layout.header(), layout.title());
						inside(layout.header(), layout.counter());
						inside(layout.handPanel(), layout.mainHand());
						check(layout.mainHand().equals(first.mainHand()), "Main hand moved between pages");
						check(layout.mainHand().width() >= 220 && layout.mainHand().height() >= 20, "Main-hand target too small");
						List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.mainHand(), layout.previous(), layout.next()));
						widgets.addAll(layout.actions());
						widgets.addAll(layout.controls());
						inside(layout.footer(), layout.previous());
						inside(layout.footer(), layout.next());
						for (HgnRect action : layout.actions()) {
							inside(layout.footer(), action);
							check(action.width() >= 60 && action.height() >= 20, "Native footer target too small");
						}
						for (int index = 0; index < layout.controls().size(); index++) {
							int nativeIndex = layout.firstControl() + index;
							check(!visited[nativeIndex], "Native layer appears on multiple pages");
							visited[nativeIndex] = true;
							check(first.pageContaining(nativeIndex) == page, "Resize selects wrong page for focused layer");
							HgnRect control = layout.controls().get(index);
							inside(layout.layersPanel(), control);
							check(control.width() >= 215 && control.height() >= 20, "Native layer target too small");
							check(control.contains(control.centerX(), control.centerY()), "Control center outside hit target");
							check(!control.contains(control.right(), control.centerY()), "Right hit boundary must remain exclusive");
							if (!layout.compact()) check(control.y() >= layout.layersPanel().y() + 30, "Layer heading overlaps a control");
						}
						for (int index = 0; index < widgets.size(); index++) {
							inside(viewport, widgets.get(index));
							for (int other = index + 1; other < widgets.size(); other++) disjoint(widgets.get(index), widgets.get(other));
						}
					}
					for (boolean found : visited) check(found, "A native skin control is unreachable");
					check(HgnSkinCustomizationLayout.resolve(size[0], size[1], count, -1, actionCount).page() == 0, "Negative page not clamped");
					check(HgnSkinCustomizationLayout.resolve(size[0], size[1], count, 999, actionCount).page() == first.pages() - 1, "Last page not clamped");
					combinations++;
				}
			}
		}
		check(HgnSkinCustomizationLayout.resolve(320, 180, 7, 0, 1).pages() > 1, "Compact layout must page, not shrink controls");
		check(!HgnSkinCustomizationLayout.resolve(740, 299, 7, 0, 1).sideBySide(), "Short layout must not force side-by-side panels");
		check(HgnSkinCustomizationLayout.resolve(740, 300, 7, 0, 1).sideBySide(), "Wide layout needs separate layer/hand columns");
		check(HgnSkinCustomizationLayout.resolve(836, 471, 7, 0, 1).pages() == 1, "Wide layout should show all seven native layers");
		System.out.println("Skin customization layout: " + combinations + " viewport/roster/footer combinations passed across every page.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Skin widget or panel outside bounds");
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Skin widgets or panels overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
