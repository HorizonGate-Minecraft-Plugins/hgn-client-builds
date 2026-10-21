package online.horizongate.hgn.ui;

import java.util.List;

/** Pack manager geometry only; native entry widths, order and actions stay native. */
public record HgnPackSelectionLayout(HgnRect header, HgnRect title, HgnRect search,
	HgnRect availablePanel, HgnRect selectedPanel, HgnRect availableList, HgnRect selectedList,
	HgnRect availableTab, HgnRect selectedTab, HgnRect footer, HgnRect hint,
	HgnRect openFolder, HgnRect done, boolean dualPane, boolean compact) {
	public enum Pane { AVAILABLE, SELECTED }

	public static boolean supported(int width, int height) {
		return width >= 320 && height >= 180;
	}

	public static HgnPackSelectionLayout resolve(int width, int height, boolean hasSearch) {
		if (!supported(width, height)) throw new IllegalArgumentException("Unsupported pack viewport");
		boolean compact = height < 320;
		boolean dual = width >= 560;
		int margin = compact ? 6 : 12;
		int gap = compact ? 3 : 8;
		int frameWidth = Math.min(1120, width - margin * 2);
		int x = (width - frameWidth) / 2;
		int headerHeight = compact ? hasSearch ? 40 : 22 : hasSearch ? 78 : 52;
		HgnRect header = new HgnRect(x, margin, frameWidth, headerHeight);
		HgnRect title = new HgnRect(x + 8, header.y() + (compact ? 5 : 26), frameWidth - 16, 9);
		HgnRect search = new HgnRect(x + 8, title.bottom() + (compact ? 3 : 9), frameWidth - 16, compact ? 20 : 24);
		HgnRect footer = new HgnRect(x, height - margin - (compact ? 38 : 52), frameWidth, compact ? 38 : 52);
		HgnRect hint = new HgnRect(x + 6, footer.y() + (compact ? 3 : 7), frameWidth - 12, 9);
		int buttonWidth = (frameWidth - 18) / 2;
		HgnRect open = new HgnRect(x + 6, hint.bottom() + (compact ? 2 : 8), buttonWidth, compact ? 20 : 22);
		HgnRect done = new HgnRect(footer.right() - 6 - buttonWidth, open.y(), buttonWidth, open.height());
		int tabsY = header.bottom() + gap;
		int tabHeight = compact ? 20 : 24;
		HgnRect availableTab = new HgnRect(x, tabsY, (frameWidth - gap) / 2, tabHeight);
		HgnRect selectedTab = new HgnRect(availableTab.right() + gap, tabsY,
			frameWidth - availableTab.width() - gap, tabHeight);
		int contentY = dual ? tabsY : tabsY + tabHeight + gap;
		int contentHeight = footer.y() - gap - contentY;
		int columnWidth = dual ? (frameWidth - gap) / 2 : frameWidth;
		HgnRect available = new HgnRect(x, contentY, columnWidth, contentHeight);
		HgnRect selected = new HgnRect(dual ? available.right() + gap : x, contentY,
			dual ? frameWidth - columnWidth - gap : frameWidth, contentHeight);
		int inset = compact ? 3 : 8;
		return new HgnPackSelectionLayout(header, title, search, available, selected,
			available.inset(inset), selected.inset(inset), availableTab, selectedTab,
			footer, hint, open, done, dual, compact);
	}

	/** Pure identity plan: switching panes never reconstructs native rows or runs callbacks. */
	public static final class State<T> {
		private final T available;
		private final T selected;
		private Pane pane = Pane.AVAILABLE;
		private HgnPackSelectionLayout layout;
		private boolean disposed;

		public State(T available, T selected) {
			if (available == null || selected == null || available == selected) throw new IllegalArgumentException("Invalid native pack lists");
			this.available = available;
			this.selected = selected;
		}

		public void arrange(int width, int height, boolean hasSearch, Object focused, boolean followFocus) {
			if (disposed) return;
			layout = resolve(width, height, hasSearch);
			if (followFocus && focused == available) pane = Pane.AVAILABLE;
			else if (followFocus && focused == selected) pane = Pane.SELECTED;
		}

		public void choose(Pane choice) { if (!disposed && choice != null) pane = choice; }
		public Pane pane() { return pane; }
		public HgnPackSelectionLayout layout() { return layout; }
		public List<T> visible() {
			return layout != null && layout.dualPane() ? List.of(available, selected)
				: List.of(pane == Pane.AVAILABLE ? available : selected);
		}
		public boolean disposed() { return disposed; }
		public void dispose() { disposed = true; }
	}
}
