import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnWorldSelectionLayout;

/** Checks the native world-list lane and every retained action across responsive layouts. */
public final class HgnWorldSelectionLayoutTest {
	public static void main(String[] args) {
		int[][] viewports = {{320, 180}, {427, 180}, {320, 240}, {427, 240}, {619, 360},
			{620, 360}, {640, 360}, {660, 320}, {836, 471}, {1064, 600}, {1065, 600},
			{1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}, {2560, 720}};
		for (int[] size : viewports) {
			for (int actionCount : new int[]{3, 6, 7}) {
				HgnWorldSelectionLayout layout = HgnWorldSelectionLayout.resolve(size[0], size[1], actionCount);
				HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
				inside(viewport, layout.header());
				inside(viewport, layout.listPanel());
				inside(viewport, layout.actionsPanel());
				inside(layout.header(), layout.search());
				inside(layout.listPanel(), layout.worldList());
				if (layout.sidebar()) {
					inside(viewport, layout.navigationPanel());
					inside(layout.listPanel(), layout.hero());
					if (layout.showsSelectedWorld()) {
						check(layout.selectedWorld().height() >= 68,
							"Selected-world inspector lacks room for real metadata");
						if (layout.selectedWorldInLibrary()) {
							inside(layout.listPanel(), layout.selectedWorld());
							check(layout.worldList().right() + 8 <= layout.selectedWorld().x(),
								"Wide selected-world inspector overlaps native rows");
							check(layout.selectedWorld().height() >= 210,
								"Wide selected-world inspector clips real metadata cards");
							check(layout.selectedWorld().width() >= 240,
								"Wide selected-world inspector makes real values unreadable");
						} else if (layout.selectedWorldInHero()) {
							check(layout.selectedWorld().equals(layout.hero()),
								"Small selected-world inspector must reuse the hero lane");
							check(layout.selectedWorld().bottom() + 12 <= layout.worldList().y(),
								"Hero inspector overlaps native saved-world rows");
						} else {
							inside(layout.actionsPanel(), layout.selectedWorld());
							check(layout.selectedWorld().bottom() + 6 <= layout.actions().get(0).y(),
								"Selected-world inspector overlaps native actions");
						}
					}
					check(layout.navigation().size() == 8, "Wide world navigation lost a destination");
					check(layout.navigationPanel().right() < layout.listPanel().x()
						&& layout.listPanel().right() < layout.actionsPanel().x(),
						"World navigation, library and action columns overlap");
					check(layout.hero().bottom() + 12 <= layout.worldList().y(),
						"World hero overlaps native saved-world rows");
					for (int index = 0; index < layout.navigation().size(); index++) {
						HgnRect destination = layout.navigation().get(index);
						inside(layout.navigationPanel(), destination);
						check(destination.width() >= 100 && destination.height() >= 20,
							"World navigation target became too small");
						if (index > 0) check(layout.navigation().get(index - 1).bottom() < destination.y(),
							"World navigation targets overlap");
					}
				} else {
					check(layout.navigation().isEmpty() && layout.hero().width() == 0
						&& !layout.showsSelectedWorld(),
						"Compact world layout retained desktop-only decoration");
				}
				check(layout.worldList().width() >= 280, "Native world row lacks horizontal room");
				check(layout.worldList().height() >= 36, "Native world row lacks vertical room");
				check(layout.search().height() >= 22, "Search field too short");
				check(layout.actions().size() == actionCount, "Native action dropped");
				List<HgnRect> controls = new ArrayList<>(layout.actions());
				for (HgnRect button : controls) {
					inside(layout.actionsPanel(), button);
					check(button.height() >= 20 && button.width() >= 48, "Native action too small");
				}
				if (layout.showsPlayerFooter() && !layout.actions().isEmpty()) {
					check(layout.actions().get(layout.actions().size() - 1).bottom() + 12
						<= layout.actionsPanel().bottom() - 45,
						"Player footer overlaps a native world action");
				}
				controls.add(layout.search());
				controls.add(layout.worldList());
				for (int index = 0; index < controls.size(); index++) {
					HgnRect bounds = controls.get(index);
					check(bounds.contains(bounds.centerX(), bounds.centerY()), "Control center missed native hit target");
					check(!bounds.contains(bounds.right(), bounds.centerY()), "Right edge must stay exclusive");
					for (int next = index + 1; next < controls.size(); next++) {
						HgnRect other = controls.get(next);
						check(bounds.right() <= other.x() || other.right() <= bounds.x()
							|| bounds.bottom() <= other.y() || other.bottom() <= bounds.y(), "Native controls overlap");
					}
				}
			}
		}
		check(!HgnWorldSelectionLayout.resolve(320, 180, 6).sidebar(), "Compact screen must use footer actions");
		check(!HgnWorldSelectionLayout.resolve(619, 360, 6).sidebar(),
			"World library narrower than 280 pixels must use footer actions");
		check(HgnWorldSelectionLayout.resolve(620, 360, 6).sidebar(),
			"Minimum safe world-library width should show full desktop layout");
		check(HgnWorldSelectionLayout.resolve(640, 360, 6).sidebar(),
			"1280x720 at GUI scale 2 should show full desktop layout");
		check(HgnWorldSelectionLayout.resolve(836, 471, 6).sidebar(), "Wide screen must use action sidebar");
		check(!HgnWorldSelectionLayout.resolve(660, 320, 6).showsPlayerFooter(),
			"Short action sidebar must not cover native buttons with profile details");
		check(HgnWorldSelectionLayout.resolve(836, 471, 6).showsPlayerFooter(),
			"Reference-sized action sidebar should show the real player name");
		check(HgnWorldSelectionLayout.resolve(836, 471, 6).showsSelectedWorld(),
			"Reference-sized world sidebar should show the selected-world inspector");
		check(!HgnWorldSelectionLayout.resolve(836, 471, 6).selectedWorldInLibrary(),
			"Reference-sized world list needs its full native row width");
		check(HgnWorldSelectionLayout.resolve(836, 471, 6).selectedWorld().height() >= 90,
			"Reference-sized selected-world metadata should be readable");
		check(HgnWorldSelectionLayout.resolve(1280, 720, 6).showsSelectedWorld(),
			"Desktop world sidebar should show the selected-world inspector");
		check(HgnWorldSelectionLayout.resolve(1280, 720, 6).selectedWorldInLibrary(),
			"Wide desktop world library should place selected details beside the real list");
		check(!HgnWorldSelectionLayout.resolve(1064, 600, 6).selectedWorldInLibrary()
			&& HgnWorldSelectionLayout.resolve(1065, 600, 6).selectedWorldInLibrary(),
			"Two-column world library changed before its native rows and inspector fit");
		check(HgnWorldSelectionLayout.resolve(640, 360, 6).showsSelectedWorld()
			&& !HgnWorldSelectionLayout.resolve(640, 360, 6).selectedWorldInHero(),
			"1280x720 at GUI scale 2 must preserve the banner beside real save details");
		check(HgnWorldSelectionLayout.resolve(640, 360, 6).selectedWorld().height() >= 68,
			"Six native actions left too little room for selected-save metadata");
		check(HgnWorldSelectionLayout.resolve(640, 360, 7).selectedWorldInHero(),
			"Seven native world actions must retain the selected-save hero inspector");
		check(HgnWorldSelectionLayout.resolve(640, 360, 6).worldList().width() >= 280,
			"Selected-save action-rail inspector must not narrow the native list");
		check(!HgnWorldSelectionLayout.resolve(660, 320, 6).showsSelectedWorld(),
			"A hero shorter than four metadata lines must not render an inspector");
		check(HgnWorldSelectionLayout.resolve(836, 471, 7).showsSelectedWorld(),
			"Reference-sized world sidebar should retain metadata with seven native actions");
		check(HgnWorldSelectionLayout.resolve(836, 471, 7).selectedWorld().height() >= 68,
			"Seven native actions should not squeeze selected metadata below readable height");
		System.out.println("World selection layout: " + viewports.length * 3
			+ " viewport/action-count combinations passed.");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(outer.contains(inner.x(), inner.y()) && outer.contains(inner.right() - 1, inner.bottom() - 1),
			"World selection control or panel outside its parent");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
