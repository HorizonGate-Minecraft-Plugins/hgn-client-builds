package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Native display controls and ordered preferences inside responsive HGN video shells. */
public record HgnVideoSettingsLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect display, List<HgnRect> pinned, HgnRect content, HgnRect footer,
	HgnRect previous, HgnRect next, List<HgnRect> actions, List<Slot> entries,
	List<Integer> entryPages, int page, int pages, boolean compact, boolean sideBySide,
	boolean reference) {
	public record Slot(int index, HgnRect bounds) {
	}

	public HgnVideoSettingsLayout {
		pinned = List.copyOf(pinned);
		actions = List.copyOf(actions);
		entries = List.copyOf(entries);
		entryPages = List.copyOf(entryPages);
	}

	public static HgnVideoSettingsLayout resolve(int width, int height, List<Boolean> headings, int requestedPage, int actionCount) {
		HgnDashboardLayout canvas = HgnDashboardLayout.resolve(width, height);
		boolean reference = canvas.scale() >= 0.75f;
		boolean compact = !reference && height < 260;
		int gap = compact ? 4 : 8;
		int controlHeight = reference ? Math.max(20, canvas.size(22)) : compact ? 20 : 24;
		int frameWidth = Math.min(980, width - 16);
		boolean sideBySide = !reference && frameWidth >= 800 && height >= 320;
		int frameHeight = Math.min(520, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header;
		HgnRect title;
		HgnRect counter;
		HgnRect footer;
		HgnRect display;
		HgnRect content;
		List<HgnRect> pinned = new ArrayList<>();
		if (reference) {
			header = canvas.rect(208, 92, 480, 62);
			title = canvas.rect(246, 123, 340, 16);
			counter = canvas.rect(624, 108, 50, 16);
			display = canvas.rect(208, 158, 480, 41);
			content = canvas.rect(208, 203, 480, 207);
			footer = canvas.rect(208, 414, 480, 33);
			int inset = Math.max(6, canvas.size(8));
			int pinnedGap = Math.max(6, canvas.size(8));
			int pinnedWidth = (display.width() - inset * 2 - pinnedGap) / 2;
			for (int index = 0; index < 2; index++) pinned.add(new HgnRect(display.x() + inset + index * (pinnedWidth + pinnedGap),
				display.bottom() - controlHeight - Math.max(2, canvas.size(3)), pinnedWidth, controlHeight));
		} else {
			header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 20 : 50);
			title = new HgnRect(header.x() + 10, header.y() + (compact ? 3 : 28), header.width() - 76, 14);
			counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
			footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 24 : 36), frame.width(), compact ? 24 : 36);
			int bodyY = header.bottom() + gap;
			if (sideBySide) {
				display = new HgnRect(frame.x(), bodyY, 212, footer.y() - bodyY - gap);
				for (int index = 0; index < 2; index++) pinned.add(new HgnRect(display.x() + 10,
					display.y() + 30 + index * (controlHeight + 12), display.width() - 20, controlHeight));
				content = new HgnRect(display.right() + gap, bodyY, frame.width() - display.width() - gap, display.height());
			} else {
				display = new HgnRect(frame.x(), bodyY, frame.width(), compact ? 20 : 40);
				int pinnedWidth = (display.width() - 24) / 2;
				for (int index = 0; index < 2; index++) pinned.add(new HgnRect(display.x() + 8 + index * (pinnedWidth + 8),
					display.centerY() - controlHeight / 2, pinnedWidth, controlHeight));
				content = new HgnRect(frame.x(), display.bottom() + gap, frame.width(), footer.y() - display.bottom() - 2 * gap);
			}
		}
		int contentInset = reference ? Math.max(5, canvas.size(8)) : compact ? 3 : 10;
		HgnRect inner = reference
			? new HgnRect(content.x() + contentInset, content.y() + Math.max(15, canvas.size(18)),
				content.width() - contentInset * 2, content.height() - Math.max(15, canvas.size(18)) - contentInset)
			: content.inset(contentInset);
		int columns = inner.width() >= 560 ? 2 : 1;
		int rowGap = reference ? Math.max(4, canvas.size(5)) : compact ? 3 : 10;
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
			int entryHeight = heading ? compact ? 10 : 14 : controlHeight;
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
		int navigationWidth = Math.min(reference ? canvas.size(80) : 90, (footer.width() - 32) / 4);
		HgnRect previous = new HgnRect(footer.x() + 8, footer.centerY() - controlHeight / 2, navigationWidth, controlHeight);
		HgnRect next = new HgnRect(footer.right() - 8 - navigationWidth, previous.y(), navigationWidth, controlHeight);
		int actionLane = next.x() - previous.right() - 16;
		int actionWidth = actionCount == 0 ? 0 : (actionLane - 8 * (actionCount - 1)) / actionCount;
		List<HgnRect> actions = new ArrayList<>();
		for (int index = 0; index < actionCount; index++) actions.add(new HgnRect(previous.right() + 8 + index * (actionWidth + 8),
			previous.y(), actionWidth, controlHeight));
		return new HgnVideoSettingsLayout(header, title, counter, display, pinned, content, footer, previous, next,
			actions, allPages.get(page), entryPages, page, allPages.size(), compact, sideBySide, reference);
	}

	public int pageContaining(int index) {
		return entryPages.get(index);
	}

	public static int focusedControlIndex(List<?> controls, Object focused) {
		return focused == null ? -1 : controls.indexOf(focused);
	}
}
