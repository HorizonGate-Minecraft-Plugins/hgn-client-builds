package online.horizongate.hgn.ui;

/** Three-rail Mods workspace that folds into a single pane at small GUI scales. */
public record HgnInstalledModsLayout(boolean compact, HgnRect header, HgnRect navigation,
	HgnRect back, HgnRect installed, HgnRect browse, HgnRect refresh,
	HgnRect workspace, HgnRect search, HgnRect list, HgnRect detail,
	HgnRect manage, HgnRect previous, HgnRect next,
	int rowHeight, int rowGap, int rowsPerPage) {
	public static HgnInstalledModsLayout resolve(int width, int height) {
		int margin = clamp(Math.min(width, height) / 80, 4, 10);
		int frameWidth = Math.min(Math.max(1, width - margin * 2), 1100);
		int frameHeight = Math.min(Math.max(1, height - margin * 2), 640);
		int left = (width - frameWidth) / 2;
		int top = (height - frameHeight) / 2;
		boolean compact = frameWidth < 600 || frameHeight < 300;
		int headerHeight = compact ? clamp(frameHeight / 8, 22, 34)
			: clamp(frameHeight / 6, 54, 76);
		HgnRect header = new HgnRect(left, top, frameWidth, headerHeight);
		int contentY = header.bottom() + (compact ? 3 : 5);
		int contentHeight = Math.max(1, top + frameHeight - contentY);
		if (compact) {
			HgnRect navigation = new HgnRect(left, contentY, frameWidth, 18);
			int navGap = 3;
			int navWidth = (navigation.width() - navGap * 3) / 4;
			HgnRect back = new HgnRect(left, contentY, navWidth, navigation.height());
			HgnRect installed = new HgnRect(back.right() + navGap, contentY,
				navWidth, navigation.height());
			HgnRect browse = new HgnRect(installed.right() + navGap, contentY,
				navWidth, navigation.height());
			HgnRect refresh = new HgnRect(browse.right() + navGap, contentY,
				left + frameWidth - browse.right() - navGap, navigation.height());
			HgnRect workspace = new HgnRect(left, navigation.bottom() + 3,
				frameWidth, Math.max(1, contentHeight - navigation.height() - 3));
			int inset = 5;
			HgnRect search = new HgnRect(workspace.x() + inset, workspace.y() + 18,
				workspace.width() - inset * 2, 15);
			int pagerY = workspace.bottom() - 18;
			HgnRect detail = new HgnRect(workspace.x() + inset, pagerY - 22,
				workspace.width() - inset * 2, 18);
			HgnRect list = new HgnRect(search.x(), search.bottom() + 4,
				search.width(), Math.max(1, detail.y() - search.bottom() - 8));
			int pagerWidth = 30;
			HgnRect next = new HgnRect(workspace.right() - inset - pagerWidth,
				pagerY, pagerWidth, 14);
			HgnRect previous = new HgnRect(next.x() - pagerWidth - 3,
				pagerY, pagerWidth, 14);
			int rowGap = 1;
			int rowHeight = Math.min(12, Math.max(1, list.height()));
			int rows = Math.max(1, (list.height() + rowGap) / (rowHeight + rowGap));
			return new HgnInstalledModsLayout(true, header, navigation, back,
				installed, browse, refresh, workspace, search, list, detail,
				browse, previous, next, rowHeight, rowGap, rows);
		}
		int gap = 6;
		int navigationWidth = clamp(frameWidth * 18 / 100, 108, 198);
		int detailWidth = clamp(frameWidth * 27 / 100, 170, 296);
		HgnRect navigation = new HgnRect(left, contentY, navigationWidth, contentHeight);
		HgnRect detail = new HgnRect(left + frameWidth - detailWidth, contentY,
			detailWidth, contentHeight);
		HgnRect workspace = new HgnRect(navigation.right() + gap, contentY,
			Math.max(1, detail.x() - navigation.right() - gap * 2), contentHeight);
		int navInset = 5;
		int navHeight = 23;
		HgnRect back = new HgnRect(navigation.x() + navInset, navigation.y() + 8,
			navigation.width() - navInset * 2, navHeight);
		HgnRect installed = new HgnRect(back.x(), back.bottom() + 11,
			back.width(), navHeight);
		HgnRect browse = new HgnRect(back.x(), installed.bottom() + 4,
			back.width(), navHeight);
		HgnRect refresh = new HgnRect(back.x(), browse.bottom() + 4,
			back.width(), navHeight);
		int inset = 7;
		HgnRect search = new HgnRect(workspace.x() + inset, workspace.y() + 39,
			workspace.width() - inset * 2, 19);
		int pagerY = workspace.bottom() - 24;
		HgnRect list = new HgnRect(search.x(), search.bottom() + 5,
			search.width(), Math.max(1, pagerY - search.bottom() - 9));
		int pagerWidth = 39;
		HgnRect next = new HgnRect(workspace.right() - inset - pagerWidth,
			pagerY, pagerWidth, 18);
		HgnRect previous = new HgnRect(next.x() - pagerWidth - 4,
			pagerY, pagerWidth, 18);
		HgnRect manage = new HgnRect(detail.x() + 7, detail.bottom() - 28,
			detail.width() - 14, 20);
		int rowGap = 2;
		int rowHeight = Math.min(21, Math.max(1, list.height()));
		int rows = Math.max(1, (list.height() + rowGap) / (rowHeight + rowGap));
		return new HgnInstalledModsLayout(false, header, navigation, back,
			installed, browse, refresh, workspace, search, list, detail,
			manage, previous, next, rowHeight, rowGap, rows);
	}

	public HgnRect row(int index) {
		return new HgnRect(list.x(), list.y() + index * (rowHeight + rowGap),
			list.width(), rowHeight);
	}

	public boolean showsEmptyDiscoveryHero() {
		return !compact && ((list.width() >= 420 && list.height() >= 150)
			|| (list.width() >= 300 && list.height() >= 175));
	}

	public boolean emptyDiscoveryStacked() {
		return list.width() < 420;
	}

	public HgnRect emptyDiscoveryHero() {
		return new HgnRect(list.x(), list.y(), list.width(), Math.min(190, list.height()));
	}

	public HgnRect emptyDiscoveryAction() {
		HgnRect hero = emptyDiscoveryHero();
		return new HgnRect(hero.x() + 12, hero.bottom() - 30,
			Math.max(1, emptyDiscoveryStacked() ? hero.width() - 24
				: hero.width() / 2 - 24), 21);
	}

	public int pages(int itemCount) {
		return Math.max(1, (Math.max(0, itemCount) + rowsPerPage - 1) / rowsPerPage);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
