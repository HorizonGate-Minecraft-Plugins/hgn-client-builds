import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Exercises the dashboard coordinate mapping and native mouse hit boundaries. */
public final class HgnDashboardLayoutTest {
	public static void main(String[] args) {
		int[][] windows = {{854, 480}, {1280, 720}, {1672, 941}, {1920, 1080},
			{3840, 2160}, {800, 600}, {2560, 1080}, {1080, 1920}};
		for (int[] window : windows) {
			for (int guiScale = 1; guiScale <= 4; guiScale++) {
				int width = (int) Math.ceil(window[0] / (double) guiScale);
				int height = (int) Math.ceil(window[1] / (double) guiScale);
				HgnDashboardLayout layout = HgnDashboardLayout.resolve(width, height);
				HgnRect rail = layout.rect(17, 84, 184, 332);
				HgnRect content = layout.rect(208, 115, 425, 333);
				HgnRect profile = layout.rect(636, 116, 178, 332);
				for (HgnRect panel : new HgnRect[]{rail, content, profile}) {
					check(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= width
						&& panel.bottom() <= height, "Panel outside viewport");
				}
				check(rail.right() < content.x() && content.right() < profile.x(), "Columns overlap");
				HgnRect preview = layout.rect(695, 94, 126, 136);
				HgnRect model = layout.profilePreviewModel();
				HgnRect label = layout.profilePreviewLabel();
				HgnRect gatewayPlayer = layout.gatewayForegroundModel();
				HgnRect gatewayRail = layout.rect(18, 88, 184, 351);
				HgnRect featuredNetwork = layout.rect(216, 351, 214, 96);
				check(gatewayPlayer.x() > gatewayRail.right()
					&& gatewayPlayer.bottom() <= featuredNetwork.y()
					&& gatewayPlayer.right() < layout.profilePreviewModel().x()
					&& gatewayPlayer.y() >= 0 && gatewayPlayer.bottom() <= height,
					"Gateway player overlaps another dashboard panel");
				check(preview.contains(model.x(), model.y())
					&& preview.contains(model.right() - 1, model.bottom() - 1),
					"Player model leaves its preview");
				check(preview.contains(label.x(), label.y())
					&& preview.contains(label.right() - 1, label.bottom() - 1),
					"Preview label leaves its panel");
				check(model.bottom() < label.y(), "Player model overlaps its preview label");
				for (int index = 0; index < 6; index++) {
					HgnRect button = layout.rect(28, 159 + 41 * index, 160, 34);
					check(rail.contains(button.x(), button.y())
						&& rail.contains(button.right() - 1, button.bottom() - 1), "Button outside rail");
					check(button.contains(layout.x(108), layout.y(176 + 41 * index)), "Paint/click mismatch");
					check(!button.contains(button.right(), button.centerY()), "Right edge is not exclusive");
					if (index < 5) {
						check(button.bottom() < layout.rect(28, 200 + 41 * index, 160, 34).y(),
							"Navigation targets overlap");
					}
				}
				for (int index = 0; index < 4; index++) {
					HgnRect feature = layout.rect(223 + 101 * index, 360, 92, 78);
					check(content.contains(feature.x(), feature.y())
						&& content.contains(feature.right() - 1, feature.bottom() - 1), "Feature clipped");
				}
			}
		}
		HgnDashboardLayout reference = HgnDashboardLayout.resolve(1672, 942);
		check(reference.rect(208, 115, 425, 333).equals(new HgnRect(416, 230, 850, 666)),
			"Reference composition changed");
		System.out.println("Dashboard layout: 32 viewport/GUI-scale combinations passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
