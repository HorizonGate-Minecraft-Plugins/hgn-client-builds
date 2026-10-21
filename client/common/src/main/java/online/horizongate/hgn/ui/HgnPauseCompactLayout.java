package online.horizongate.hgn.ui;

/** Native-size pause controls for the smallest supported menu viewports. */
public record HgnPauseCompactLayout(HgnRect panel, HgnRect header, HgnRect footer,
	int activityRows) {
	private static final int CONTROL_HEIGHT = 22;
	private static final int GAP = 4;
	private static final int ESSENTIAL_TOP = 33;
	private static final int ACTIVITY_TOP = 114;

	public static boolean needed(int width, int height) {
		return HgnDashboardLayout.resolve(width, height).scale() < 0.62f;
	}

	public static HgnPauseCompactLayout resolve(int width, int height) {
		int panelHeight = Math.min(228, height - 12);
		HgnRect panel = new HgnRect(8, (height - panelHeight) / 2, width - 16, panelHeight);
		HgnRect header = new HgnRect(12, panel.y() + 3, width - 24, 25);
		HgnRect footer = new HgnRect(12, panel.bottom() - 25, width - 24, CONTROL_HEIGHT);
		int available = footer.y() - GAP - (panel.y() + ACTIVITY_TOP);
		int rows = Math.max(1, Math.min(3, (available + GAP) / (CONTROL_HEIGHT + GAP)));
		return new HgnPauseCompactLayout(panel, header, footer, rows);
	}

	public HgnRect essential(int index) {
		if (index < 0 || index >= 6) throw new IllegalArgumentException("Invalid pause action index");
		return cell(index / 3, index % 3, panel.y() + ESSENTIAL_TOP);
	}

	public HgnRect activity(int slot) {
		if (slot < 0 || slot >= activitySlots()) throw new IllegalArgumentException("Invalid pause activity slot");
		return cell(slot % 2, slot / 2, panel.y() + ACTIVITY_TOP);
	}

	public int activitySlots() {
		return activityRows * 2;
	}

	public int pages(int activities) {
		return Math.max(1, (activities + activitySlots() - 1) / activitySlots());
	}

	public int clampPage(int page, int activities) {
		return Math.max(0, Math.min(page, pages(activities) - 1));
	}

	public HgnRect previous() {
		return new HgnRect(footer.x(), footer.y(), 76, footer.height());
	}

	public HgnRect next() {
		return new HgnRect(footer.right() - 76, footer.y(), 76, footer.height());
	}

	public HgnRect pageLabel() {
		return new HgnRect(previous().right() + GAP, footer.y(),
			next().x() - previous().right() - GAP * 2, footer.height());
	}

	private HgnRect cell(int column, int row, int top) {
		int width = (header.width() - GAP) / 2;
		return new HgnRect(header.x() + column * (width + GAP),
			top + row * (CONTROL_HEIGHT + GAP), width, CONTROL_HEIGHT);
	}
}
