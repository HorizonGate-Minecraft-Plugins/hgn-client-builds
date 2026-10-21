import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnMultiplayerLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnSavedServersLayout;
import online.horizongate.hgn.ui.HgnServerPopulation;

/** Checks the multiplayer page at the packaged screenshot size and smaller GUI scales. */
public final class HgnMultiplayerLayoutTest {
	public static void main(String[] args) {
		for (int[] viewport : new int[][] {{550, 352}, {550, 350}, {640, 360},
			{836, 471}}) {
			HgnDashboardLayout layout = HgnDashboardLayout.resolve(viewport[0], viewport[1]);
			HgnReferenceShellLayout shell = new HgnReferenceShellLayout(layout);
			HgnRect rail = shell.rail();
			HgnRect heading = layout.rect(422, 37, 273, 40);
			HgnRect hero = layout.rect(207, 108, 595, 195);
			HgnRect saved = layout.rect(207, 317, 277, 110);
			HgnRect network = layout.rect(496, 317, 306, 110);
			HgnRect search = layout.rect(HgnMultiplayerLayout.search());
			HgnRect details = layout.rect(HgnMultiplayerLayout.inspector());
			for (HgnRect panel : new HgnRect[] {rail, heading, hero, saved, network, search, details}) {
				check(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= viewport[0]
					&& panel.bottom() <= viewport[1], "Multiplayer panel outside viewport");
			}
			check(rail.right() < heading.x() && heading.bottom() < hero.y(),
				"Navigation or heading overlaps the content");
			HgnRect previousAction = null;
			for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
				check(inside(rail, shell.destination(index)),
					"A shared Dashboard destination left the official-server rail");
			}
			for (int index = 0; index < HgnMultiplayerLayout.WIDE_ACTION_COUNT; index++) {
				HgnRect action = layout.rect(HgnMultiplayerLayout.wideAction(index));
				check(action.x() > rail.right() && action.right() <= hero.right()
					&& action.y() > shell.wordmark().bottom()
					&& action.bottom() < hero.y() && action.height() >= 13,
					"Official-server action overlaps the shared shell or content");
				if (previousAction != null) check(previousAction.right() < action.x(),
					"Official-server actions overlap each other");
				previousAction = action;
			}
			check(hero.bottom() < saved.y() && saved.right() < network.x(),
				"Discovery cards overlap");
			HgnRect savedAction = layout.rect(227, 365, 233, 29);
			check(inside(saved, savedAction), "Saved server action outside its card");
			for (int index = 0; index < 2; index++) {
				HgnRect card = layout.rect(HgnMultiplayerLayout.serverCard(index));
				check(card.right() < details.x() && card.bottom() <= details.bottom()
					&& search.bottom() < card.y(),
					"Official server card overlaps the inspector");
			}
			check(inside(details, layout.rect(HgnMultiplayerLayout.join())),
				"Join action outside server inspector");
		}
		for (int[] viewport : new int[][] {{320, 180}, {427, 240}, {480, 270}, {500, 280}}) {
			int width = viewport[0];
			int height = viewport[1];
			check(HgnMultiplayerLayout.compact(width, height),
				"Small viewport did not activate compact Multiplayer");
			HgnRect header = HgnMultiplayerLayout.compactHeader(width);
			HgnRect refresh = HgnMultiplayerLayout.compactRefresh(width);
			HgnRect search = HgnMultiplayerLayout.compactSearch(width);
			HgnRect content = HgnMultiplayerLayout.compactContent(width, height);
			check(inside(header, refresh) && inside(header, search)
				&& search.right() < refresh.x() && search.width() >= 88
				&& refresh.height() >= 20,
				"Compact refresh is too small or outside its header");
			check(content.x() >= 0 && content.y() >= 0 && content.right() <= width
				&& content.bottom() <= height, "Compact content outside viewport");
			HgnRect previous = null;
			for (int index = 0; index < HgnMultiplayerLayout.COMPACT_NAVIGATION_COUNT; index++) {
				HgnRect action = HgnMultiplayerLayout.compactNavigation(width, index);
				check(action.width() >= 48 && action.height() >= 20,
					"Compact navigation hit target is too small");
				check(action.x() >= 0 && action.right() <= width && action.bottom() < content.y(),
					"Compact navigation outside its row");
				if (previous != null) check(previous.right() < action.x(),
					"Compact navigation buttons overlap");
				previous = action;
			}
			HgnRect first = HgnMultiplayerLayout.compactServerCard(width, height, 0);
			HgnRect second = HgnMultiplayerLayout.compactServerCard(width, height, 1);
			HgnRect join = HgnMultiplayerLayout.compactJoin(width, height);
			check(inside(content, first) && inside(content, second) && inside(content, join),
				"Compact server controls outside content");
			check(first.height() >= 32 && second.height() >= 32 && join.height() >= 20,
				"Compact server hit target is too small");
			check(first.bottom() < second.y() && second.bottom() < join.y(),
				"Compact server rows overlap the join action");
			check(inside(content, HgnMultiplayerLayout.compactExplore(width, height)),
				"Compact explore action outside content");
		}
		check(!HgnMultiplayerLayout.compact(550, 352),
			"Packaged-size Multiplayer should retain its full composition");
		check("Official".equals(HgnMultiplayerLayout.compactNavigationLabel(0))
			&& HgnMultiplayerLayout.compactShowsOfficialServers(0)
			&& HgnMultiplayerLayout.compactNavigationSelected(0, true)
			&& !HgnMultiplayerLayout.compactNavigationSelected(1, true),
			"Compact Official tab must open and highlight the official server list");
		check("Overview".equals(HgnMultiplayerLayout.compactNavigationLabel(1))
			&& !HgnMultiplayerLayout.compactShowsOfficialServers(1)
			&& HgnMultiplayerLayout.compactNavigationSelected(1, false)
			&& !HgnMultiplayerLayout.compactNavigationSelected(0, false),
			"Compact Overview tab must open and highlight the network overview");
		for (int index = 2; index < HgnMultiplayerLayout.COMPACT_NAVIGATION_COUNT; index++) {
			check(!HgnMultiplayerLayout.compactNavigationSelected(index, true)
				&& !HgnMultiplayerLayout.compactNavigationSelected(index, false),
				"A secondary compact destination must not look like the active network view");
		}

		HgnDashboardLayout packagedAtOne = HgnDashboardLayout.resolve(1100, 704);
		HgnDashboardLayout packagedAtTwo = HgnDashboardLayout.resolve(550, 352);
		for (int guiScale = 1; guiScale <= 2; guiScale++) {
			HgnDashboardLayout layout = guiScale == 1 ? packagedAtOne : packagedAtTwo;
			float scale = layout.scale();
			check(9 * HgnMultiplayerLayout.textScale(scale, 0.65f) * guiScale >= 14.5f,
				"Body text remains tiny at the packaged screenshot size");
			check(9 * HgnMultiplayerLayout.buttonTextScale(scale) * guiScale >= 15,
				"Action labels remain tiny at the packaged screenshot size");
			float titleHeight = 9 * scale * 1.38f;
			float bodyHeight = 9 * scale * 0.81f;
			check((titleHeight - bodyHeight) * guiScale >= 5,
				"Multiplayer heading no longer stands apart from body text");
			check(9 * scale * 1.38f < layout.y(61) - layout.y(37),
				"Reference header title overlaps its subtitle");
			check(9 * HgnMultiplayerLayout.buttonTextScale(scale)
				< layout.rect(HgnMultiplayerLayout.wideAction(5)).height(),
				"Refresh label exceeds its shared-shell toolbar control");
			check(9 * HgnMultiplayerLayout.textScale(scale, 0.68f)
				< layout.y(365) - layout.y(349), "Saved description overlaps its action");
		}
		check(Math.abs(packagedAtOne.scale() - 2 * packagedAtTwo.scale()) < 0.001f,
			"GUI scaling should preserve the page's physical proportions");
		check(HgnMultiplayerLayout.textScale(1, 1.7f)
			> HgnMultiplayerLayout.textScale(1, 0.65f), "Feature title lost its hierarchy");
		check(HgnMultiplayerLayout.matches("hub", "HorizonGate Hub", "hub.horizongate.online"),
			"Official search lost name/address matching");
		check(HgnMultiplayerLayout.matches("horizon hub", "HorizonGate Hub", "hub.horizongate.online"),
			"Official search lost multi-term matching");
		check(!HgnMultiplayerLayout.matches("other", "HorizonGate Hub", "hub.horizongate.online"),
			"Official search must reject unmatched servers");
		String sharedProxyPing = HgnServerPopulation.fromMinecraftPing(true, 4, 100);
		check("Scoped count unavailable".equals(sharedProxyPing)
			&& sharedProxyPing.equals(HgnServerPopulation.fromMinecraftPing(true, 13, 100)),
			"Shared proxy ping must not be attributed to Hub and VinkSMP separately");
		check("Scoped count unavailable".equals(HgnServerPopulation.fromMinecraftPing(true, -1, -1)),
			"Official population must remain unavailable when the proxy ping fails");
		check("4 / 100 players".equals(HgnServerPopulation.fromMinecraftPing(false, 4, 100))
			&& "Players unavailable".equals(HgnServerPopulation.fromMinecraftPing(false, -1, -1)),
			"Independent community server pings must keep real population and failure states");
		for (int[] viewport : new int[][] {{550, 352}, {640, 360}, {836, 471},
			{1280, 720}, {1672, 941}}) {
			int width = viewport[0];
			int height = viewport[1];
			HgnDashboardLayout layout = HgnDashboardLayout.resolve(width, height);
			HgnRect rail = layout.rect(HgnSavedServersLayout.wideRail());
			HgnRect header = layout.rect(HgnSavedServersLayout.wideHeader());
			HgnRect search = layout.rect(HgnSavedServersLayout.wideSearch());
			HgnRect savedMode = layout.rect(HgnSavedServersLayout.wideMode(false));
			HgnRect lanMode = layout.rect(HgnSavedServersLayout.wideMode(true));
			HgnRect inspector = layout.rect(HgnSavedServersLayout.wideInspector());
			check(rail.x() >= 0 && rail.y() >= 0 && rail.right() <= width
				&& rail.bottom() <= height && header.right() <= width
				&& header.bottom() < search.y() && rail.right() < header.x(),
				"Saved-server shell leaves the viewport or overlaps");
			check(search.right() < savedMode.x() && savedMode.right() < lanMode.x()
				&& lanMode.right() <= inspector.right() && search.bottom() < inspector.y(),
				"Saved-server search and mode tabs overlap content");
			for (int index = 0; index < HgnSavedServersLayout.WIDE_COMMANDS; index++) {
				HgnRect command = layout.rect(HgnSavedServersLayout.wideCommand(index));
				check(inside(rail, command) && command.height() >= 20,
					"Wide server navigation is outside its rail or too small");
				if (index > 0) check(layout.rect(HgnSavedServersLayout.wideCommand(index - 1))
					.bottom() < command.y(), "Wide server navigation overlaps");
			}
			for (int index = 0; index < HgnSavedServersLayout.WIDE_ROWS; index++) {
				HgnRect row = layout.rect(HgnSavedServersLayout.wideRow(index));
				check(row.x() > rail.right() && row.right() < inspector.x()
					&& row.height() >= 25 && row.bottom() <= height,
					"Wide saved-server row is cramped or overlaps its inspector");
				if (index > 0) check(layout.rect(HgnSavedServersLayout.wideRow(index - 1))
					.bottom() < row.y(), "Wide saved-server rows overlap");
			}
			HgnRect previous = layout.rect(HgnSavedServersLayout.widePrevious());
			HgnRect next = layout.rect(HgnSavedServersLayout.wideNext());
			check(layout.rect(HgnSavedServersLayout.wideRow(4)).bottom() < previous.y()
				&& previous.right() < next.x() && next.right() < inspector.x(),
				"Saved-server page controls overlap rows or inspector");
			HgnRect join = layout.rect(HgnSavedServersLayout.wideJoin());
			HgnRect edit = layout.rect(HgnSavedServersLayout.wideEdit());
			HgnRect delete = layout.rect(HgnSavedServersLayout.wideDelete());
			check(inside(inspector, join) && inside(inspector, edit)
				&& inside(inspector, delete) && join.bottom() < edit.y()
				&& edit.right() < delete.x() && join.height() >= 18,
				"Selected-server actions are outside or overlap their inspector");
		}
		for (int[] viewport : new int[][] {{320, 180}, {320, 240}, {427, 240}, {500, 280}}) {
			int width = viewport[0];
			int height = viewport[1];
			HgnRect header = HgnSavedServersLayout.header(width);
			HgnRect content = HgnSavedServersLayout.content(width, height);
			HgnRect back = HgnSavedServersLayout.back();
			HgnRect search = HgnSavedServersLayout.search(width);
			HgnRect refresh = HgnSavedServersLayout.refresh(width);
			check(inside(header, back) && inside(header, search) && inside(header, refresh)
				&& back.right() < search.x() && search.right() < refresh.x(),
				"Compact saved-server header controls overlap");
			for (int index = 0; index < 4; index++) {
				HgnRect tab = HgnSavedServersLayout.tab(width, index);
				check(tab.height() >= 20 && tab.x() >= 8 && tab.right() <= width - 8
					&& tab.bottom() < content.y(), "Compact saved-server tab is unusable");
			}
			HgnRect first = HgnSavedServersLayout.row(width, height, 0);
			HgnRect second = HgnSavedServersLayout.row(width, height, 1);
			HgnRect previous = HgnSavedServersLayout.previous(height);
			HgnRect next = HgnSavedServersLayout.next(height);
			HgnRect edit = HgnSavedServersLayout.edit(height);
			HgnRect delete = HgnSavedServersLayout.delete(height);
			HgnRect join = HgnSavedServersLayout.join(width, height);
			check(inside(content, first) && inside(content, second)
				&& inside(content, previous) && inside(content, next) && inside(content, edit)
				&& inside(content, delete) && inside(content, join),
				"Compact saved-server controls leave the viewport");
			check(first.height() >= 30 && second.height() >= 30
				&& first.bottom() < second.y() && second.bottom() + 11 < previous.y()
				&& previous.right() < next.x() && next.right() < edit.x()
				&& edit.right() < delete.x() && delete.right() < join.x(),
				"Compact saved-server rows or footer actions overlap");
			check(previous.width() >= 40 && next.width() >= 40 && edit.width() >= 40
				&& delete.width() >= 80 && join.width() >= 64
				&& previous.height() >= 20 && next.height() >= 20
				&& edit.height() >= 20 && delete.height() >= 20 && join.height() >= 20,
				"Compact saved-server footer targets are too small");
		}
		System.out.println("Multiplayer: reference, compact, saved/LAN, search and packaged-size geometry passed.");
	}

	private static boolean inside(HgnRect panel, HgnRect child) {
		return panel.contains(child.x(), child.y())
			&& panel.contains(child.right() - 1, child.bottom() - 1);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
