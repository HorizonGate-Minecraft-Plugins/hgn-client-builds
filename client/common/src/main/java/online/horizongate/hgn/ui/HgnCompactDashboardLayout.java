package online.horizongate.hgn.ui;

/** Reflows small logical Minecraft viewports instead of scaling down every control. */
public record HgnCompactDashboardLayout(HgnRect panel, int rowHeight) {
	private static final int COLUMN_GAP = 6;
	private static final int ROW_GAP = 5;
	private static final int SIDE_INSET = 12;

	/** Desktop Auto scale may have a small logical viewport but still ample physical space. */
	public static boolean needed(int width, int height, int framebufferWidth, int framebufferHeight) {
		if (width >= 640 && height >= 360) return false;
		return framebufferWidth < 1280 || framebufferHeight < 720
			|| width < 400 || height < 240;
	}

	public static HgnCompactDashboardLayout resolve(int width, int height) {
		int panelWidth = Math.min(520, width - 24);
		int panelHeight = Math.min(224, height - 16);
		int headerHeight = headerHeight(panelHeight);
		return new HgnCompactDashboardLayout(new HgnRect((width - panelWidth) / 2,
			(height - panelHeight) / 2, panelWidth, panelHeight),
			Math.min(34, Math.max(20, (panelHeight - headerHeight - 35) / 4)));
	}

	public HgnRect header() {
		return new HgnRect(panel.x() + SIDE_INSET, panel.y() + 4,
			panel.width() - SIDE_INSET * 2, headerHeight(panel.height()) - 8);
	}

	public HgnRect footer() {
		return new HgnRect(panel.x() + SIDE_INSET, panel.bottom() - 16,
			panel.width() - SIDE_INSET * 2, 11);
	}

	public HgnRect button(int index) {
		int columnWidth = (panel.width() - SIDE_INSET * 2 - COLUMN_GAP) / 2;
		return new HgnRect(panel.x() + SIDE_INSET + (index % 2) * (columnWidth + COLUMN_GAP),
			panel.y() + headerHeight(panel.height()) + (index / 2) * (rowHeight + ROW_GAP),
			columnWidth, rowHeight);
	}

	private static int headerHeight(int panelHeight) {
		return Math.min(42, Math.max(32, panelHeight / 5));
	}
}
