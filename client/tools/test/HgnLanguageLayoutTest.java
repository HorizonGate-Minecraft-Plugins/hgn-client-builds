import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnLanguageLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Runs without Minecraft; validates native row alignment and all language-page lanes. */
public final class HgnLanguageLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 240}, {427, 240}, {640, 260},
			{660, 320}, {836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}, {2560, 720}};
		for (int[] size : viewports) {
			for (boolean search : new boolean[]{false, true}) {
				HgnLanguageLayout layout = HgnLanguageLayout.resolve(size[0], size[1], search, 2);
				HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
				inside(viewport, layout.header());
				inside(viewport, layout.listPanel());
				inside(viewport, layout.footer());
				inside(layout.header(), layout.title());
				inside(layout.footer(), layout.warning());
				for (int textWidth : new int[]{1, 70, 300, 1000}) {
					HgnRect title = HgnLanguageLayout.labelBounds(layout.title(), textWidth, false);
					HgnRect warning = HgnLanguageLayout.labelBounds(layout.warning(), textWidth, true);
					inside(layout.title(), title);
					inside(layout.warning(), warning);
					check(title.x() == layout.title().x(), "Title must remain left aligned across versions");
					check(Math.abs(warning.centerX() - layout.warning().centerX()) <= 1,
						"Accuracy warning must remain centered across versions");
				}
				inside(viewport, layout.languageList());
				check(layout.languageList().x() == 0 && layout.languageList().width() == size[0],
					"Native language text must remain aligned to the viewport center");
				check(layout.languageList().height() >= 36, "Fewer than two native language rows fit");
				check(layout.listPanel().y() < layout.languageList().y()
					&& layout.languageList().bottom() < layout.listPanel().bottom(), "Native rows escape the panel vertically");
				check(layout.title().width() >= 100, "Language title has no readable lane");
				check(layout.actions().size() == 2, "Native language action dropped");
				List<HgnRect> controls = new ArrayList<>(layout.actions());
				for (HgnRect button : controls) {
					inside(layout.footer(), button);
					check(button.height() >= 20 && button.width() >= 140, "Language action is too small");
				}
				if (search) {
					inside(layout.header(), layout.search());
					check(layout.search().height() >= 22, "Native search is too short");
					controls.add(layout.search());
				} else check(layout.search() == null, "Old versions must not gain a fabricated search");
				controls.add(layout.title());
				controls.add(layout.warning());
				controls.add(layout.languageList());
				for (int index = 0; index < controls.size(); index++) {
					HgnRect bounds = controls.get(index);
					check(bounds.contains(bounds.centerX(), bounds.centerY()), "Control center misses its hit target");
					for (int next = index + 1; next < controls.size(); next++) {
						HgnRect other = controls.get(next);
						check(bounds.right() <= other.x() || other.right() <= bounds.x()
							|| bounds.bottom() <= other.y() || other.bottom() <= bounds.y(), "Language controls overlap");
					}
				}
			}
		}
		check(HgnLanguageLayout.resolve(320, 180, true, 2).compact(), "Short viewport needs compact spacing");
		check(!HgnLanguageLayout.resolve(836, 471, true, 2).compact(), "Normal viewport lost generous spacing");
		System.out.println("Language layout: 24 viewport/search combinations passed.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Language control or panel outside its parent");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
