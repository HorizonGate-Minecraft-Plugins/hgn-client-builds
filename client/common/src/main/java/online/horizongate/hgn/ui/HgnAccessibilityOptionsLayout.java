package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Speech/caption controls and a full-width native Help/Done row surrounding paged preferences. */
public record HgnAccessibilityOptionsLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect assistance, List<HgnRect> pinned, HgnRect content, HgnRect footer,
	HgnRect previous, HgnRect next, List<HgnRect> actions, List<Slot> entries,
	List<Integer> entryPages, int page, int pages, boolean compact, boolean sideBySide) {
	public record Slot(int index, HgnRect bounds) {
	}

	public HgnAccessibilityOptionsLayout {
		pinned = List.copyOf(pinned);
		actions = List.copyOf(actions);
		entries = List.copyOf(entries);
		entryPages = List.copyOf(entryPages);
	}

	public static HgnAccessibilityOptionsLayout resolve(int width, int height, List<Boolean> headings, int requestedPage, int actionCount) {
		boolean compact = height < 300;
		boolean sideBySide = width >= 900 && height >= 360;
		int gap = compact ? 4 : 8;
		int controlHeight = compact ? 20 : 24;
		int frameWidth = Math.min(980, width - 16);
		int frameHeight = Math.min(520, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 24 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 5 : 28), header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 54 : 72), frame.width(), compact ? 54 : 72);
		int bodyY = header.bottom() + gap;
		HgnRect assistance;
		HgnRect content;
		List<HgnRect> pinned = new ArrayList<>();
		if (sideBySide) {
			assistance = new HgnRect(frame.x(), bodyY, 212, footer.y() - bodyY - gap);
			for (int index = 0; index < 2; index++) pinned.add(new HgnRect(assistance.x() + 10,
				assistance.y() + 30 + index * (controlHeight + 12), assistance.width() - 20, controlHeight));
			content = new HgnRect(assistance.right() + gap, bodyY, frame.width() - assistance.width() - gap, assistance.height());
		} else {
			assistance = new HgnRect(frame.x(), bodyY, frame.width(), compact ? 24 : 40);
			int pinnedWidth = (assistance.width() - 24) / 2;
			for (int index = 0; index < 2; index++) pinned.add(new HgnRect(assistance.x() + 8 + index * (pinnedWidth + 8),
				assistance.centerY() - controlHeight / 2, pinnedWidth, controlHeight));
			content = new HgnRect(frame.x(), assistance.bottom() + gap, frame.width(), footer.y() - assistance.bottom() - 2 * gap);
		}
		HgnRect inner = content.inset(compact ? 4 : 10);
		int columns = inner.width() >= 560 ? 2 : 1;
		int rowGap = compact ? 6 : 10;
		int controlWidth = (inner.width() - rowGap * (columns - 1)) / columns;
		List<List<Slot>> allPages = new ArrayList<>();
		List<Integer> entryPages = new ArrayList<>();
		List<Slot> slots = new ArrayList<>();
		allPages.add(slots);
		int rowY = inner.y();
		int column = 0;
		for (int index = 0; index < headings.size(); index++) {
			boolean heading = headings.get(index);
			if (heading && column > 0) {
				rowY += controlHeight + rowGap;
				column = 0;
			}
			int entryHeight = heading ? 14 : controlHeight;
			int required = entryHeight + (heading && index + 1 < headings.size() && !headings.get(index + 1) ? rowGap + controlHeight : 0);
			if (rowY + required > inner.bottom() && !slots.isEmpty()) {
				slots = new ArrayList<>();
				allPages.add(slots);
				rowY = inner.y();
				column = 0;
			}
			slots.add(new Slot(index, new HgnRect(inner.x() + column * (controlWidth + rowGap), rowY,
				heading ? inner.width() : controlWidth, entryHeight)));
			entryPages.add(allPages.size() - 1);
			if (heading || ++column == columns) {
				rowY += entryHeight + rowGap;
				column = 0;
			}
		}
		int page = Math.max(0, Math.min(requestedPage, allPages.size() - 1));
		int navigationWidth = Math.min(90, (footer.width() - 24) / 2);
		HgnRect previous = new HgnRect(footer.x() + 8, footer.y() + (compact ? 4 : 8), navigationWidth, controlHeight);
		HgnRect next = new HgnRect(footer.right() - 8 - navigationWidth, previous.y(), navigationWidth, controlHeight);
		int actionWidth = actionCount == 0 ? 0 : (footer.width() - 16 - 8 * (actionCount - 1)) / actionCount;
		List<HgnRect> actions = new ArrayList<>();
		for (int index = 0; index < actionCount; index++) actions.add(new HgnRect(footer.x() + 8 + index * (actionWidth + 8),
			footer.bottom() - (compact ? 4 : 8) - controlHeight, actionWidth, controlHeight));
		return new HgnAccessibilityOptionsLayout(header, title, counter, assistance, pinned, content, footer, previous, next,
			actions, allPages.get(page), entryPages, page, allPages.size(), compact, sideBySide);
	}

	public int pageContaining(int index) {
		return entryPages.get(index);
	}

	public static int focusedControlIndex(List<?> controls, Object focused) {
		return focused == null ? -1 : controls.indexOf(focused);
	}
}
