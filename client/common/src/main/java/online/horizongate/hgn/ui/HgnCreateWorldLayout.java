package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Native-tab geometry only; the native tab bar and footer retain their original bounds. */
public record HgnCreateWorldLayout(HgnRect header, HgnRect title, HgnRect identity,
	HgnRect nameLabel, HgnRect name, HgnRect content, HgnRect footer,
	HgnRect previous, HgnRect next, List<Slot> actions,
	int page, int pages, int capacity, boolean compact, boolean sideBySide) {
	public enum Kind { GAME, WORLD, MORE }

	public record Slot(int index, HgnRect bounds, HgnRect frame) {
	}

	public static HgnCreateWorldLayout resolve(int width, int height, int tabBottom,
		int footerHeight, int count, int requested) {
		return resolve(Kind.GAME, width, height, tabBottom, footerHeight, count, requested);
	}

	public static HgnCreateWorldLayout resolve(Kind kind, int width, int height, int tabBottom,
		int footerHeight, int count, int requested) {
		boolean compact = height < 320;
		boolean sideBySide = kind != Kind.MORE && width >= 760 && height >= 360;
		int gap = compact ? 3 : 8;
		int frameWidth = Math.min(920, width - 16);
		int x = (width - frameWidth) / 2;
		HgnRect header = new HgnRect(x, tabBottom + gap, frameWidth, compact ? 20 : 48);
		HgnRect footer = new HgnRect(x, height - footerHeight, frameWidth, footerHeight);
		int bodyY = header.bottom() + gap;
		int bodyHeight = footer.y() - gap - bodyY;
		int identityWidth = sideBySide ? 280 : frameWidth;
		int identityHeight = kind == Kind.MORE ? 0 : sideBySide ? bodyHeight : compact ? 34 : 62;
		HgnRect identity = new HgnRect(x, bodyY, identityWidth, identityHeight);
		HgnRect content = kind == Kind.MORE ? new HgnRect(x, bodyY, frameWidth, bodyHeight) : sideBySide
			? new HgnRect(identity.right() + gap, bodyY, frameWidth - identityWidth - gap, bodyHeight)
			: new HgnRect(x, identity.bottom() + gap, frameWidth, footer.y() - gap - identity.bottom() - gap);
		int inset = compact ? 4 : 12;
		int labelY = identity.y() + (compact ? 2 : sideBySide ? 36 : 8);
		HgnRect label = new HgnRect(identity.x() + inset, labelY, identity.width() - inset * 2, 9);
		int controlHeight = compact ? 20 : 24;
		HgnRect name = new HgnRect(label.x(), label.bottom() + (compact ? 1 : 7), label.width(), controlHeight);
		HgnRect inner = content.inset(inset);
		int columnGap = compact ? 6 : 12;
		int rowGap = compact ? 3 : 10;
		int minimumWidth = kind == Kind.WORLD ? 286 : kind == Kind.MORE ? 180 : 140;
		int columns = inner.width() >= minimumWidth * 2 + columnGap ? 2 : 1;
		int rowHeight = kind == Kind.MORE ? compact ? 24 : 76 : controlHeight;
		int rows = Math.max(1, (inner.height() + rowGap) / (rowHeight + rowGap));
		int capacity = rows * columns;
		int pages = Math.max(1, (count + capacity - 1) / capacity);
		int page = Math.max(0, Math.min(pages - 1, requested));
		int controlWidth = (inner.width() - columnGap * (columns - 1)) / columns;
		List<Slot> actions = new ArrayList<>();
		for (int index = page * capacity; index < Math.min(count, (page + 1) * capacity); index++) {
			int slot = index - page * capacity;
			HgnRect frame = new HgnRect(inner.x() + slot % columns * (controlWidth + columnGap),
				inner.y() + slot / columns * (rowHeight + rowGap), controlWidth, rowHeight);
			HgnRect bounds = kind == Kind.MORE
				? new HgnRect(frame.x() + (compact ? 2 : 8), frame.bottom() - controlHeight - (compact ? 2 : 8),
					frame.width() - (compact ? 4 : 16), controlHeight) : frame;
			actions.add(new Slot(index, bounds, frame));
		}
		int navigationWidth = compact ? 48 : 70;
		int navigationHeight = 20;
		HgnRect next = new HgnRect(header.right() - navigationWidth - 4,
			header.y() + (header.height() - navigationHeight) / 2, navigationWidth, navigationHeight);
		HgnRect previous = new HgnRect(next.x() - navigationWidth - 4, next.y(), navigationWidth, navigationHeight);
		HgnRect title = new HgnRect(header.x() + 6, header.y() + (compact ? 4 : 26),
			(pages > 1 ? previous.x() - 4 : header.right() - 6) - header.x() - 6, 9);
		return new HgnCreateWorldLayout(header, title, identity, label, name, content, footer,
			previous, next, List.copyOf(actions), page, pages, capacity, compact, sideBySide);
	}
}
