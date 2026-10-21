import online.horizongate.hgn.ui.HgnCompactDashboardLayout;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;

public final class HgnCompactDashboardLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int width : new int[] {320, 427, 560, 639, 640, 836, 1280}) {
			for (int height : new int[] {180, 240, 300, 359, 360, 471, 720}) {
				var layout = HgnCompactDashboardLayout.resolve(width, height);
				HgnRect panel = layout.panel();
				HgnRect header = layout.header();
				HgnRect footer = layout.footer();
				if (panel.x() < 0 || panel.y() < 0 || panel.right() > width
					|| panel.bottom() > height || panel.height() > 224) {
					throw new AssertionError("Compact panel is clipped or overlong");
				}
				if (header.x() < panel.x() || header.right() > panel.right()
					|| footer.x() < panel.x() || footer.right() > panel.right()
					|| header.bottom() >= footer.y()) {
					throw new AssertionError("Compact header or footer is clipped");
				}
				for (int index = 0; index < 8; index++) {
					HgnRect button = layout.button(index);
					if (button.height() < 20 || button.width() < 100 || button.x() < 0 || button.y() < 0
						|| button.right() > width || button.bottom() > height) throw new AssertionError("Clipped compact command");
					if (button.y() <= header.bottom() || button.bottom() >= footer.y()) {
						throw new AssertionError("Compact command overlaps heading or footer");
					}
					for (int other = 0; other < index; other++) {
						HgnRect prior = layout.button(other);
						if (button.x() < prior.right() && button.right() > prior.x()
							&& button.y() < prior.bottom() && button.bottom() > prior.y()) throw new AssertionError("Overlapping commands");
					}
				}
				cases++;
			}
		}
		checkCompact(427, 240, 640, 480, true);
		checkCompact(427, 240, 1280, 720, false);
		checkCompact(640, 360, 1280, 720, false);
		checkCompact(320, 180, 1280, 720, true);
		checkCompact(399, 240, 1280, 720, true);
		checkCompact(427, 240, 1280, 719, true);
		checkCompact(480, 270, 1920, 1080, false);
		checkCompact(836, 471, 640, 480, false);
		HgnReferenceShellLayout autoShell = new HgnReferenceShellLayout(
			HgnDashboardLayout.resolve(427, 240));
		for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
			HgnRect action = autoShell.destination(index);
			if (action.x() < 0 || action.y() < 0 || action.right() > 427 || action.bottom() > 240
				|| action.width() * 3 < 240 || action.height() * 3 < 48) {
				throw new AssertionError("Auto-scale reference action is clipped or too small");
			}
		}
		var desktop = HgnCompactDashboardLayout.resolve(550, 350);
		if (desktop.panel().height() != 224 || desktop.panel().y() != 63) {
			throw new AssertionError("Compact panel should not leave a tall empty lower half");
		}
		System.out.println("Compact dashboard: " + cases + " viewport cases retain eight non-overlapping normal-sized controls.");
	}

	private static void checkCompact(int width, int height, int framebufferWidth,
		int framebufferHeight, boolean expected) {
		if (HgnCompactDashboardLayout.needed(width, height, framebufferWidth, framebufferHeight) != expected) {
			throw new AssertionError("Wrong Dashboard reflow for " + width + "x" + height
				+ " in " + framebufferWidth + "x" + framebufferHeight);
		}
	}
}
