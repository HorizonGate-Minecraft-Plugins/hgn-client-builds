package online.horizongate.hgn.ui;

/** Logical-pixel alert geometry; compact displays retain normal-height actions. */
public record HgnUpdateOfferLayout(HgnRect panel, HgnRect content, HgnRect check, HgnRect back, boolean compact) {
	public static HgnUpdateOfferLayout resolve(int width, int height) {
		boolean compact = height < 240;
		int w = Math.min(580, Math.max(1, width - 16));
		int h = Math.min(290, Math.max(1, height - 16));
		HgnRect panel = new HgnRect((width - w) / 2, (height - h) / 2, w, h);
		int lane = Math.max(1, w - 24);
		int buttonWidth = (lane - 8) / 2;
		int footerY = panel.bottom() - 34;
		return new HgnUpdateOfferLayout(panel,
			new HgnRect(panel.x() + 12, panel.y() + 34, lane, Math.max(1, footerY - panel.y() - 42)),
			new HgnRect(panel.x() + 12, footerY, buttonWidth, 24),
			new HgnRect(panel.x() + 20 + buttonWidth, footerY, buttonWidth, 24), compact);
	}
}
