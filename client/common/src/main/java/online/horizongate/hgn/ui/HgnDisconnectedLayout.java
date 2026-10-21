package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Geometry and read-only reason paging; no connection, return or reporting actions. */
public record HgnDisconnectedLayout(HgnRect header, HgnRect title, HgnRect reasonPanel,
	HgnRect reason, HgnRect previous, HgnRect pageLabel, HgnRect next, HgnRect footer,
	List<HgnRect> actions, boolean compact) {
	public static boolean supported(int width, int height, int actions) {
		return width >= 320 && height >= 180 && actions >= 1 && actions <= 3;
	}

	public static HgnDisconnectedLayout resolve(int width, int height, int actionCount, int lineHeight) {
		if (!supported(width, height, actionCount) || lineHeight < 1 || lineHeight > 12)
			throw new IllegalArgumentException("Unsupported disconnection layout");
		boolean compact = height < 320;
		int margin = compact ? 6 : 12, gap = compact ? 4 : 8;
		int frameWidth = Math.min(860, width - margin * 2), x = (width - frameWidth) / 2;
		boolean horizontal = width >= 620;
		int buttonHeight = compact ? 20 : 24;
		int rows = horizontal ? 1 : actionCount;
		int footerHeight = rows * buttonHeight + (rows - 1) * gap + 12;
		HgnRect header = new HgnRect(x, margin, frameWidth, compact ? 28 : 56);
		HgnRect title = new HgnRect(x + 8, header.y() + (compact ? 10 : 32), frameWidth - 16, 9);
		HgnRect footer = new HgnRect(x, height - margin - footerHeight, frameWidth, footerHeight);
		HgnRect panel = new HgnRect(x, header.bottom() + gap, frameWidth, footer.y() - header.bottom() - gap * 2);
		int inset = compact ? 4 : 8;
		int pagerY = panel.bottom() - inset - 20;
		HgnRect previous = new HgnRect(panel.x() + inset, pagerY, 70, 20);
		HgnRect next = new HgnRect(panel.right() - inset - 70, pagerY, 70, 20);
		HgnRect label = new HgnRect(previous.right() + gap, pagerY + 6, next.x() - previous.right() - gap * 2, 9);
		int textHeight = Math.max(lineHeight, (pagerY - gap - panel.y() - inset) / lineHeight * lineHeight);
		HgnRect reason = new HgnRect(panel.x() + inset, panel.y() + inset, panel.width() - inset * 2, textHeight);
		List<HgnRect> actions = new ArrayList<>();
		int buttonWidth = horizontal ? (frameWidth - 12 - gap * (actionCount - 1)) / actionCount : frameWidth - 12;
		for (int index = 0; index < actionCount; index++) actions.add(new HgnRect(x + 6 + (horizontal ? index * (buttonWidth + gap) : 0),
			footer.y() + 6 + (horizontal ? 0 : index * (buttonHeight + gap)), buttonWidth, buttonHeight));
		return new HgnDisconnectedLayout(header, title, panel, reason, previous, label, next, footer, List.copyOf(actions), compact);
	}

	/** Pure page ownership; resize retains the old first line rather than losing the reading position. */
	public static final class Pages {
		private int page;
		private int capacity = 1;
		private int lines;
		private boolean disposed;

		public void resize(int lineCount, int visibleLines) {
			if (disposed) return;
			if (lineCount < 0 || visibleLines < 1) throw new IllegalArgumentException("Invalid reason lines");
			long first = (long) page * capacity;
			lines = lineCount;
			capacity = visibleLines;
			page = (int) Math.min(first / capacity, count() - 1L);
		}

		public void choose(int requested) { if (!disposed) page = Math.max(0, Math.min(requested, count() - 1)); }
		public int page() { return page; }
		public int count() { return Math.max(1, (int) (((long) lines + capacity - 1) / capacity)); }
		public int firstLine() { return page * capacity; }
		public int endLine() { return Math.min(lines, (int) Math.min(Integer.MAX_VALUE, (long) firstLine() + capacity)); }
		public boolean disposed() { return disposed; }
		public void dispose() { disposed = true; }
	}
}
