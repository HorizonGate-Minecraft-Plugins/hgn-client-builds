package online.horizongate.hgn.ui;

/** Reference-sized header and navigation geometry shared by wide HGN Client menus. */
public record HgnReferenceShellLayout(HgnDashboardLayout canvas) {
	public static final int DESTINATIONS = 8;

	public HgnRect rail() {
		return canvas.rect(18, 88, 184, 351);
	}

	public HgnRect destination(int index) {
		if (index < 0 || index >= DESTINATIONS) throw new IndexOutOfBoundsException(index);
		return canvas.rect(28, 98 + index * 41, 164, 34);
	}

	public HgnRect destinationIcon(int index) {
		destination(index);
		return canvas.rect(35, 104 + index * 41, 22, 22);
	}

	public HgnRect destinationTitle(int index) {
		destination(index);
		return canvas.rect(64, 102 + index * 41, 112, 12);
	}

	public HgnRect destinationDetail(int index) {
		destination(index);
		return canvas.rect(64, 117 + index * 41, 112, 10);
	}

	public HgnRect wordmark() {
		return canvas.rect(28, 12, 73, 69);
	}

	public HgnRect versionBadge() {
		return canvas.rect(716, 11, 98, 20);
	}

	public HgnRect featuredNetwork() {
		return canvas.rect(216, 351, 214, 96);
	}

	public HgnRect featuredAction() {
		return canvas.rect(224, 412, 198, 26);
	}

	public HgnRect profileColumn() {
		return canvas.rect(689, 40, 138, 306);
	}

	public HgnRect profileIdentity() {
		return canvas.rect(695, 47, 126, 40);
	}

	public HgnRect profilePreview() {
		return canvas.rect(695, 94, 126, 136);
	}

	public HgnRect profileAction() {
		return canvas.rect(695, 233, 126, 24);
	}

	public HgnRect profileWallet() {
		return canvas.rect(695, 263, 126, 75);
	}

	public HgnRect quickStatus() {
		return canvas.rect(671, 351, 156, 96);
	}

	public HgnRect quickStatusCell(int index) {
		if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
		return canvas.rect(677 + index % 2 * 74, 379 + index / 2 * 32, 70, 28);
	}
}
