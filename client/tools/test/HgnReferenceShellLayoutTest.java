import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;

/** Guards the reference-style header, eight destinations and featured action. */
public final class HgnReferenceShellLayoutTest {
	public static void main(String[] args) {
		verifyFrameColors();
		int cases = 0;
		for (int[] viewport : new int[][] {{640, 360}, {836, 471}, {1100, 704},
			{1280, 720}, {1672, 941}}) {
			int width = viewport[0];
			int height = viewport[1];
			HgnReferenceShellLayout shell = new HgnReferenceShellLayout(
				HgnDashboardLayout.resolve(width, height));
			HgnRect foregroundPlayer = shell.canvas().gatewayForegroundModel();
			HgnRect rail = shell.rail();
			HgnRect hero = shell.featuredNetwork();
			within(rail, width, height);
			within(hero, width, height);
			within(foregroundPlayer, width, height);
			if (rail.right() >= foregroundPlayer.x()
				|| foregroundPlayer.right() >= shell.profileColumn().x()
				|| foregroundPlayer.bottom() > hero.y()
				|| foregroundPlayer.width() < 80 || foregroundPlayer.height() < 80) {
				throw new AssertionError("Gateway player overlaps chrome or is too small");
			}
			within(shell.wordmark(), width, height);
			within(shell.versionBadge(), width, height);
			if (rail.right() >= hero.x() || shell.wordmark().bottom() >= rail.y()
				|| shell.versionBadge().bottom() >= hero.y()) {
				throw new AssertionError("Header, rail and hero overlap");
			}
			HgnRect action = shell.featuredAction();
			within(action, width, height);
			if (action.x() < hero.x() || action.right() > hero.right()
				|| action.y() < hero.y() || action.bottom() > hero.bottom()) {
				throw new AssertionError("Featured action left its hero");
			}
			HgnRect profile = shell.profileColumn();
			HgnRect identity = shell.profileIdentity();
			HgnRect preview = shell.profilePreview();
			HgnRect profileAction = shell.profileAction();
			HgnRect wallet = shell.profileWallet();
			within(profile, width, height);
			inside(identity, profile);
			inside(preview, profile);
			inside(profileAction, profile);
			inside(wallet, profile);
			if (identity.bottom() >= preview.y() || preview.bottom() >= profileAction.y()
				|| profileAction.bottom() >= wallet.y()) {
				throw new AssertionError("Profile identity, player, action, and wallet overlap");
			}
			HgnRect status = shell.quickStatus();
			within(status, width, height);
			HgnRect[] cells = new HgnRect[4];
			for (int index = 0; index < cells.length; index++) {
				cells[index] = shell.quickStatusCell(index);
				inside(cells[index], status);
				if (cells[index].width() < 50 || cells[index].height() < 20) {
					throw new AssertionError("Quick-status cell is too small");
				}
			}
			if (cells[0].right() >= cells[1].x() || cells[2].right() >= cells[3].x()
				|| cells[0].bottom() >= cells[2].y() || cells[1].bottom() >= cells[3].y()) {
				throw new AssertionError("Quick-status cells overlap");
			}
			for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
				HgnRect destination = shell.destination(index);
				within(destination, width, height);
				if (destination.width() < 100 || destination.height() < 20
					|| destination.x() < rail.x() || destination.right() > rail.right()
					|| destination.y() < rail.y() || destination.bottom() > rail.bottom()) {
					throw new AssertionError("Navigation target is cramped or outside the rail");
				}
				if (index > 0 && shell.destination(index - 1).bottom() >= destination.y()) {
					throw new AssertionError("Navigation targets overlap");
				}
				HgnRect icon = shell.destinationIcon(index);
				HgnRect title = shell.destinationTitle(index);
				HgnRect detail = shell.destinationDetail(index);
				inside(icon, destination);
				inside(title, destination);
				inside(detail, destination);
				if (icon.right() >= title.x() || title.bottom() >= detail.y()) {
					throw new AssertionError("Two-line navigation overlaps its icon or text");
				}
			}
			cases++;
		}
		System.out.println("Reference shell: " + cases + " wide viewports keep all eight actions usable.");
	}

	private static void verifyFrameColors() {
		int inactive = HgnPalette.MENU_EDGE;
		int active = HgnPalette.MENU_EDGE_ACTIVE;
		if ((inactive >>> 24) != 0xFF || (active >>> 24) != 0xFF
			|| channel(inactive, 8) <= channel(inactive, 16) + 50
			|| channel(inactive, 0) <= channel(inactive, 16) + 75
			|| brightness(active) <= brightness(inactive) * 3 / 2
			|| brightness(HgnPalette.CYAN_SOFT) <= brightness(active)) {
			throw new AssertionError("Reference frame edges lost cyan contrast or active emphasis");
		}
	}

	private static int brightness(int color) {
		return channel(color, 16) * 2126 + channel(color, 8) * 7152
			+ channel(color, 0) * 722;
	}

	private static int channel(int color, int shift) {
		return color >>> shift & 0xFF;
	}

	private static void within(HgnRect rect, int width, int height) {
		if (rect.x() < 0 || rect.y() < 0 || rect.right() > width || rect.bottom() > height) {
			throw new AssertionError("Reference shell element is outside the viewport: " + rect);
		}
	}

	private static void inside(HgnRect inner, HgnRect outer) {
		if (inner.x() < outer.x() || inner.y() < outer.y()
			|| inner.right() > outer.right() || inner.bottom() > outer.bottom()) {
			throw new AssertionError("Navigation content is outside its target: " + inner);
		}
	}
}
