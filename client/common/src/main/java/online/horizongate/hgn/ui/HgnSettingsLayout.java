package online.horizongate.hgn.ui;

/** Logical-pixel settings layout that pages rows before text or controls become cramped. */
public record HgnSettingsLayout(HgnRect panel, HgnRect tabs, HgnRect content,
	HgnRect footer, int rowHeight, int rowsPerPage, boolean sections, boolean compact) {
	private static final int GAP = 8;

	public static HgnSettingsLayout resolve(int width, int height, int items, boolean sections) {
		boolean compact = height < (sections ? 380 : 240);
		int panelWidth = Math.min(sections ? 520 : 600, Math.max(1, width - 16));
		int innerWidth = Math.max(1, panelWidth - 28);
		boolean narrow = innerWidth < 400;
		int rowGap = sections ? 4 : compact ? 6 : GAP;
		int rowHeight = sections ? compact ? narrow ? 22 : 26 : narrow ? 28 : 30
			: compact ? 34 : 56;
		int desiredHeight = sections
			? (compact ? 84 : 160) + items * rowHeight + Math.max(0, items - 1) * rowGap
			: 258;
		int maxHeight = sections ? compact ? 300 : 424 : 258;
		int panelHeight = Math.min(Math.min(maxHeight, Math.max(1, height - 16)), desiredHeight);
		HgnRect panel = new HgnRect((width - panelWidth) / 2, (height - panelHeight) / 2,
			panelWidth, panelHeight);
		HgnRect tabs = new HgnRect(panel.x() + 14, panel.y() + (sections && compact ? 23 : compact ? 34 : 52),
			innerWidth, sections ? compact ? 18 : 24 : 0);
		HgnRect footer = new HgnRect(panel.x() + 14, panel.bottom() - (sections && compact ? 26 : compact ? 30 : 38),
			innerWidth, compact ? 22 : 24);
		int contentTop = panel.y() + (sections ? compact ? 55 : 112 : compact ? 52 : 56);
		HgnRect content = new HgnRect(panel.x() + 14, contentTop, innerWidth,
			Math.max(1, footer.y() - (sections && compact ? 3 : compact ? 8 : 10) - contentTop));
		int rows = Math.max(1, Math.min(items, (content.height() + rowGap) / (rowHeight + rowGap)));
		if (sections && compact && items > rows) {
			content = new HgnRect(content.x(), content.y() + 14, content.width(),
				Math.max(1, content.height() - 14));
			rows = Math.max(1, Math.min(items, (content.height() + rowGap) / (rowHeight + rowGap)));
		}
		return new HgnSettingsLayout(panel, tabs, content, footer, rowHeight, rows, sections, compact);
	}

	/** Two five-item tab rows keep all reference categories readable at compact GUI scales. */
	public static HgnSettingsLayout resolveCategories(int width, int height, int items) {
		HgnSettingsLayout base = resolve(width, height, Math.max(items, 4), true);
		int categoryBottom = base.tabs.y() + 2 * base.tabs.height() + 3;
		int contentTop = categoryBottom + (base.compact ? 26 : 29);
		HgnRect content = new HgnRect(base.content.x(), contentTop, base.content.width(),
			Math.max(1, base.footer.y() - (base.compact ? 3 : 10) - contentTop));
		int rows = Math.max(1, Math.min(items, (content.height() + 4) / (base.rowHeight + 4)));
		return new HgnSettingsLayout(base.panel, base.tabs, content, base.footer,
			base.rowHeight, rows, true, base.compact);
	}

	public HgnRect row(int visibleIndex) {
		return new HgnRect(content.x(), content.y() + visibleIndex * (rowHeight + (sections ? 4 : compact ? 6 : GAP)),
			content.width(), rowHeight);
	}

	public int headerTitleY() {
		return panel.y() + (sections && compact ? 6 : compact ? 12 : 14);
	}

	public int sectionTitleY() {
		return content.y() - (compact ? 12 : 22);
	}

	public HgnRect pageButton(boolean next) {
		return new HgnRect(content.right() - (next ? 42 : 144), content.y() - (compact ? 26 : 29),
			next ? 42 : 54, compact ? 20 : 21);
	}

	public HgnRect categoryPageButton(boolean next) {
		HgnRect button = pageButton(next);
		return new HgnRect(button.x(), Math.max(button.y(), categoryTab(9).bottom() + 1),
			button.width(), button.height());
	}

	public int rowTitleY(int visibleIndex) {
		return row(visibleIndex).y() + (sections ? showsRowHelp() ? compact ? 2 : 3
			: (rowHeight - 10) / 2 : compact ? 7 : 12);
	}

	public int rowHelpY(int visibleIndex) {
		return row(visibleIndex).y() + (sections ? compact ? 15 : 17 : compact ? 22 : 35);
	}

	public HgnRect settingValue(int visibleIndex) {
		HgnRect row = row(visibleIndex);
		int valueHeight = compact && rowHeight < 32 ? 18 : 22;
		return new HgnRect(row.right() - 98, row.y() + (rowHeight - valueHeight) / 2,
			86, valueHeight);
	}

	public boolean showsRowHelp() {
		return !sections || content.width() >= 400 && rowHeight >= 26;
	}

	public HgnRect tab(int index, int count) {
		return split(tabs, index, count);
	}

	public HgnRect categoryTab(int index) {
		HgnRect row = new HgnRect(tabs.x(), tabs.y() + (index / 5) * (tabs.height() + 3),
			tabs.width(), tabs.height());
		return split(row, index % 5, 5);
	}

	public HgnRect footerButton(int index, int count) {
		return split(footer, index, count);
	}

	public int pages(int items) {
		return Math.max(1, (items + rowsPerPage - 1) / rowsPerPage);
	}

	private static HgnRect split(HgnRect lane, int index, int count) {
		int width = (lane.width() - GAP * (count - 1)) / count;
		return new HgnRect(lane.x() + index * (width + GAP), lane.y(), width, lane.height());
	}
}
