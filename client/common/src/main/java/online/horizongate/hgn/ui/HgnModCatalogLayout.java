package online.horizongate.hgn.ui;

/** Modrinth search and exact-release inspector in the shared HGN Mods shell. */
public record HgnModCatalogLayout(boolean compact, HgnRect header, HgnRect navigation,
	HgnRect back, HgnRect installed, HgnRect browse, HgnRect openLauncher,
	HgnRect projectsPanel, HgnRect releasesPanel, HgnRect search,
	HgnRect searchAction, HgnRect projectList, HgnRect releaseList,
	HgnRect projectPrevious, HgnRect projectNext,
	HgnRect releasePrevious, HgnRect releaseNext, HgnRect review,
	int projectRowHeight, int releaseRowHeight, int projectRowsPerPage,
	int releaseRowsPerPage) {
	public static HgnModCatalogLayout resolve(int width, int height) {
		HgnInstalledModsLayout shell = HgnInstalledModsLayout.resolve(width, height);
		HgnRect projects = shell.workspace();
		HgnRect releases = shell.compact() ? projects : shell.detail();
		int inset = shell.compact() ? 5 : 7;
		int searchY = projects.y() + (shell.compact() ? 19 : 39);
		int searchHeight = shell.compact() ? 15 : 19;
		int searchButtonWidth = shell.compact() ? 50 : 61;
		HgnRect searchAction = new HgnRect(projects.right() - inset - searchButtonWidth,
			searchY, searchButtonWidth, searchHeight);
		HgnRect search = new HgnRect(projects.x() + inset, searchY,
			Math.max(1, searchAction.x() - projects.x() - inset - 4), searchHeight);
		int pagerHeight = shell.compact() ? 14 : 18;
		int pagerWidth = shell.compact() ? 30 : 39;
		int projectPagerY = projects.bottom() - (shell.compact() ? 18 : 24);
		HgnRect projectNext = new HgnRect(projects.right() - inset - pagerWidth,
			projectPagerY, pagerWidth, pagerHeight);
		HgnRect projectPrevious = new HgnRect(projectNext.x() - pagerWidth - 3,
			projectPagerY, pagerWidth, pagerHeight);
		HgnRect projectList = new HgnRect(projects.x() + inset, searchY + searchHeight + 5,
			projects.width() - inset * 2,
			Math.max(1, projectPagerY - searchY - searchHeight - 10));
		int reviewHeight = shell.compact() ? 16 : 21;
		int reviewY = releases.bottom() - reviewHeight - (shell.compact() ? 4 : 7);
		HgnRect review = new HgnRect(releases.x() + inset, reviewY,
			releases.width() - inset * 2, reviewHeight);
		int releasePagerY = review.y() - pagerHeight - 4;
		HgnRect releaseNext = new HgnRect(releases.right() - inset - pagerWidth,
			releasePagerY, pagerWidth, pagerHeight);
		HgnRect releasePrevious = new HgnRect(releaseNext.x() - pagerWidth - 3,
			releasePagerY, pagerWidth, pagerHeight);
		int releaseListY = releases.y() + (shell.compact() ? 43 : 94);
		HgnRect releaseList = new HgnRect(releases.x() + inset, releaseListY,
			releases.width() - inset * 2,
			Math.max(1, releasePagerY - releaseListY - 5));
		int projectRowHeight = Math.min(shell.compact() ? 13 : 22, projectList.height());
		int releaseRowHeight = Math.min(shell.compact() ? 14 : 23, releaseList.height());
		int gap = shell.compact() ? 1 : 2;
		int projectRows = Math.max(1,
			(projectList.height() + gap) / (projectRowHeight + gap));
		int releaseRows = Math.max(1,
			(releaseList.height() + gap) / (releaseRowHeight + gap));
		return new HgnModCatalogLayout(shell.compact(), shell.header(), shell.navigation(),
			shell.back(), shell.installed(), shell.browse(), shell.refresh(),
			projects, releases, search, searchAction, projectList, releaseList,
			projectPrevious, projectNext, releasePrevious, releaseNext, review,
			projectRowHeight, releaseRowHeight, projectRows, releaseRows);
	}

	public HgnRect projectRow(int index) {
		return new HgnRect(projectList.x(),
			projectList.y() + index * (projectRowHeight + (compact ? 1 : 2)),
			projectList.width(), projectRowHeight);
	}

	public HgnRect releaseRow(int index) {
		return new HgnRect(releaseList.x(),
			releaseList.y() + index * (releaseRowHeight + (compact ? 1 : 2)),
			releaseList.width(), releaseRowHeight);
	}
}
