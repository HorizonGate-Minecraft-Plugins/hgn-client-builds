import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnFontOptionsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Checks native font-option hit targets and title/footer lanes without initializing Minecraft. */
public final class HgnFontOptionsLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 240}, {427, 240}, {640, 260},
			{660, 320}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}, {2560, 720}};
		for (int[] size : viewports) {
			for (int actionCount : new int[]{1, 2}) {
				HgnFontOptionsLayout layout = HgnFontOptionsLayout.resolve(size[0], size[1], actionCount);
				HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
				inside(viewport, layout.header());
				inside(viewport, layout.footer());
				inside(layout.header(), layout.title());
				check(layout.title().width() >= 280, "Native translated title lacks room");
				check(layout.controls().size() == 2 && layout.rows().size() == 2, "A native font option was dropped");
				List<HgnRect> controls = new ArrayList<>();
				for (int index = 0; index < 2; index++) {
					HgnRect row = layout.rows().get(index);
					HgnRect control = layout.controls().get(index);
					inside(viewport, row);
					inside(row, control);
					check(control.width() >= 250 && control.height() >= 20, "Native font control is too small");
					check(row.x() + 31 < control.x(), "Row index overlaps native control");
					controls.add(control);
				}
				check(layout.actions().size() == actionCount, "A native footer action was dropped");
				for (HgnRect action : layout.actions()) {
					inside(layout.footer(), action);
					check(action.width() >= 140 && action.height() >= 20, "Native footer action is too small");
					controls.add(action);
				}
				controls.add(layout.title());
				for (int index = 0; index < controls.size(); index++) {
					HgnRect bounds = controls.get(index);
					check(bounds.contains(bounds.centerX(), bounds.centerY()), "Control center misses native hit target");
					check(!bounds.contains(bounds.right(), bounds.centerY()), "Native right boundary must be exclusive");
					for (int next = index + 1; next < controls.size(); next++) {
						HgnRect other = controls.get(next);
						check(bounds.right() <= other.x() || other.right() <= bounds.x()
							|| bounds.bottom() <= other.y() || other.bottom() <= bounds.y(), "Native font controls overlap");
					}
				}
				check(layout.header().bottom() < layout.rows().getFirst().y(), "Header overlaps font rows");
				check(layout.rows().getLast().bottom() < layout.footer().y(), "Font rows overlap footer");
			}
		}
		check(HgnFontOptionsLayout.resolve(320, 180, 1).compact(), "Short viewport must use compact spacing");
		check(!HgnFontOptionsLayout.resolve(320, 240, 1).compact(), "Normal viewport should retain readable spacing");
		System.out.println("Font options layout: 24 viewport/footer combinations passed.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Font control or panel outside parent bounds");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
