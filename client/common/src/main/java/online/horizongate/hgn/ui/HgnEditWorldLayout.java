package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Geometry only: the native world editor remains the sole owner of every action. */
public record HgnEditWorldLayout(HgnRect header, HgnRect title, HgnRect identity,
	HgnRect nameLabel, HgnRect name, HgnRect content, HgnRect footer, HgnRect save,
	HgnRect cancel, HgnRect previous, HgnRect next, List<Slot> actions,
	int page, int pages, int capacity, boolean compact, boolean sideBySide) {
	public record Slot(int index, HgnRect bounds) {
	}

	public static HgnEditWorldLayout resolve(int width, int height, int count, int requested) {
		boolean compact = height < 300;
		boolean sideBySide = width >= 800 && height >= 360;
		int gap = compact ? 3 : 8;
		int frameWidth = Math.min(920, width - 16);
		int frameHeight = Math.min(500, height - 16);
		int x = (width - frameWidth) / 2;
		int y = (height - frameHeight) / 2;
		int headerHeight = compact ? 20 : 48;
		int footerHeight = compact ? 24 : 40;
		HgnRect header = new HgnRect(x, y, frameWidth, headerHeight);
		HgnRect footer = new HgnRect(x, y + frameHeight - footerHeight, frameWidth, footerHeight);
		int bodyY = header.bottom() + gap;
		int bodyHeight = footer.y() - gap - bodyY;
		int identityWidth = sideBySide ? Math.min(290, frameWidth / 3) : frameWidth;
		int identityHeight = sideBySide ? bodyHeight : compact ? 34 : 64;
		HgnRect identity = new HgnRect(x, bodyY, identityWidth, identityHeight);
		HgnRect content = sideBySide
			? new HgnRect(identity.right() + gap, bodyY, frameWidth - identityWidth - gap, bodyHeight)
			: new HgnRect(x, identity.bottom() + gap, frameWidth, footer.y() - gap - identity.bottom() - gap);
		int inset = compact ? 4 : 12;
		int labelY = identity.y() + (compact ? 2 : sideBySide ? 38 : 10);
		HgnRect label = new HgnRect(identity.x() + inset, labelY, identity.width() - inset * 2, 9);
		HgnRect name = new HgnRect(label.x(), label.bottom() + (compact ? 1 : 7), label.width(), compact ? 20 : 24);
		int controlHeight = compact ? 20 : 24;
		int rowGap = compact ? 3 : 10;
		HgnRect inner = content.inset(inset);
		int columnGap = compact ? 6 : 12;
		int columns = inner.width() >= 280 + columnGap ? 2 : 1;
		int rows = Math.max(1, (inner.height() + rowGap) / (controlHeight + rowGap));
		int capacity = rows * columns;
		int pages = Math.max(1, (count + capacity - 1) / capacity);
		int page = Math.max(0, Math.min(pages - 1, requested));
		int controlWidth = (inner.width() - columnGap * (columns - 1)) / columns;
		List<Slot> actions = new ArrayList<>();
		for (int index = page * capacity; index < Math.min(count, (page + 1) * capacity); index++) {
			int slot = index - page * capacity;
			actions.add(new Slot(index, new HgnRect(inner.x() + slot % columns * (controlWidth + columnGap),
				inner.y() + slot / columns * (controlHeight + rowGap), controlWidth, controlHeight)));
		}
		int footerInset = compact ? 2 : 8;
		int footerGap = compact ? 6 : 12;
		int buttonWidth = (footer.width() - footerInset * 2 - footerGap) / 2;
		HgnRect save = new HgnRect(footer.x() + footerInset, footer.y() + footerInset, buttonWidth, controlHeight);
		HgnRect cancel = new HgnRect(save.right() + footerGap, save.y(), buttonWidth, controlHeight);
		int navigationWidth = compact ? 48 : 70;
		HgnRect next = new HgnRect(header.right() - 4 - navigationWidth, header.y() + (header.height() - 20) / 2, navigationWidth, 20);
		HgnRect previous = new HgnRect(next.x() - 4 - navigationWidth, next.y(), navigationWidth, 20);
		HgnRect title = new HgnRect(header.x() + 6, header.y() + (compact ? 5 : 25),
			(pages > 1 ? previous.x() - 4 : header.right() - 6) - header.x() - 6, 9);
		return new HgnEditWorldLayout(header, title, identity, label, name, content, footer, save, cancel,
			previous, next, List.copyOf(actions), page, pages, capacity, compact, sideBySide);
	}

	public static int focusedIndex(List<?> controls, Object focused) {
		return focused == null ? -1 : controls.indexOf(focused);
	}
}
