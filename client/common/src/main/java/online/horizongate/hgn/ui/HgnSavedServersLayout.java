package online.horizongate.hgn.ui;

/** Saved/LAN browser geometry for the reference shell and minimum-size viewports. */
public final class HgnSavedServersLayout {
	public static final int WIDE_ROWS = 5;
	public static final int WIDE_COMMANDS = 8;

	private HgnSavedServersLayout() {
	}

	public static HgnRect wideRail() {
		return new HgnRect(18, 88, 184, 351);
	}

	public static HgnRect wideCommand(int index) {
		if (index < 0 || index >= WIDE_COMMANDS) {
			throw new IllegalArgumentException("Invalid saved-server command");
		}
		return new HgnRect(28, 98 + index * 41, 164, 34);
	}

	public static HgnRect wideHeader() {
		return new HgnRect(211, 88, 616, 42);
	}

	public static HgnRect wideSearch() {
		return new HgnRect(211, 137, 365, 27);
	}

	public static HgnRect wideMode(boolean lan) {
		return new HgnRect(lan ? 705 : 586, 137, lan ? 122 : 111, 27);
	}

	public static HgnRect wideRow(int index) {
		if (index < 0 || index >= WIDE_ROWS) {
			throw new IllegalArgumentException("Invalid saved-server row");
		}
		return new HgnRect(211, 172 + index * 46, 365, 42);
	}

	public static HgnRect wideInspector() {
		return new HgnRect(586, 172, 241, 263);
	}

	public static HgnRect wideJoin() {
		return new HgnRect(596, 367, 221, 29);
	}

	public static HgnRect wideEdit() {
		return new HgnRect(596, 402, 105, 24);
	}

	public static HgnRect wideDelete() {
		return new HgnRect(708, 402, 109, 24);
	}

	public static HgnRect widePrevious() {
		return new HgnRect(211, 407, 76, 27);
	}

	public static HgnRect wideNext() {
		return new HgnRect(500, 407, 76, 27);
	}

	public static HgnRect header(int width) {
		return new HgnRect(8, 5, width - 16, 28);
	}

	public static HgnRect back() {
		return new HgnRect(12, 8, 52, 20);
	}

	public static HgnRect search(int width) {
		return new HgnRect(70, 8, width - 150, 20);
	}

	public static HgnRect refresh(int width) {
		return new HgnRect(width - 76, 8, 68, 20);
	}

	public static HgnRect tab(int width, int index) {
		if (index < 0 || index > 3) throw new IllegalArgumentException("Invalid server tab");
		int cellWidth = (width - 28) / 4;
		int x = 8 + index * (cellWidth + 4);
		return new HgnRect(x, 37, index == 3 ? width - 8 - x : cellWidth, 23);
	}

	public static HgnRect content(int width, int height) {
		return new HgnRect(8, 65, width - 16, height - 72);
	}

	public static HgnRect row(int width, int height, int index) {
		if (index < 0 || index > 1) throw new IllegalArgumentException("Invalid saved-server row");
		int rowHeight = Math.min(46, Math.max(30, (height - 118) / 2));
		return new HgnRect(12, 69 + index * (rowHeight + 4), width - 24, rowHeight);
	}

	public static HgnRect previous(int height) {
		return new HgnRect(12, height - 31, 42, 24);
	}

	public static HgnRect next(int height) {
		return new HgnRect(58, height - 31, 42, 24);
	}

	public static HgnRect edit(int height) {
		return new HgnRect(104, height - 31, 46, 24);
	}

	public static HgnRect delete(int height) {
		return new HgnRect(154, height - 31, 84, 24);
	}

	public static HgnRect join(int width, int height) {
		return new HgnRect(242, height - 31, width - 254, 24);
	}
}
