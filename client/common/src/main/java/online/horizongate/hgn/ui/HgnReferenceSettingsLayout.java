package online.horizongate.hgn.ui;

/** Wide in-game settings geometry, paired with the gateway Dashboard shell. */
public record HgnReferenceSettingsLayout(HgnDashboardLayout canvas) {
	public static boolean fits(int width, int height) {
		return HgnDashboardLayout.resolve(width, height).scale() >= 0.62f;
	}

	public HgnSettingsLayout controls(int items) {
		return new HgnSettingsLayout(canvas.rect(208, 92, 480, 355),
			canvas.rect(212, 158, 472, 30), canvas.rect(212, 226, 228, 164),
			canvas.rect(212, 414, 472, 28), canvas.size(36),
			Math.min(4, Math.max(1, items)), true, false);
	}

	/** All ten reference categories share one horizontal strip; compact layouts still paginate. */
	public HgnRect categoryTab(int index) {
		if (index < 0 || index >= HgnSettingsCategory.values().length)
			throw new IndexOutOfBoundsException(index);
		return canvas.rect(212 + index * 47, 158, 45, 30);
	}

	public HgnRect heading() { return canvas.rect(212, 98, 472, 54); }
	public HgnRect preview() { return canvas.rect(446, 198, 238, 205); }
	public HgnRect previewImage() { return canvas.rect(452, 222, 226, 154); }
	public HgnRect settingValue(HgnRect row) {
		return new HgnRect(row.right() - canvas.size(94), row.centerY() - canvas.size(11),
			canvas.size(86), canvas.size(22));
	}
	public HgnRect profile() { return canvas.rect(689, 40, 138, 190); }
	public HgnRect account() { return canvas.rect(695, 47, 126, 40); }
	public HgnRect playerPreview() { return canvas.rect(695, 94, 126, 129); }
	public HgnRect playerPreviewModel() { return canvas.rect(695, 94, 126, 107); }
	public HgnRect systemInfo() { return canvas.rect(689, 237, 138, 132); }
	public HgnRect quickActions() { return canvas.rect(689, 377, 138, 70); }
}
