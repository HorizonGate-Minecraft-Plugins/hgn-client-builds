package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Pages native sound controls before reducing their readable width or hit targets. */
public record HgnSoundOptionsLayout(HgnRect header, HgnRect title, HgnRect counter,
	List<HgnRect> tabs, HgnRect content, HgnRect footer, HgnRect previous, HgnRect next,
	List<HgnRect> actions, List<HgnRect> controls, int firstControl, int page, int pages,
	int firstPageCapacity, int capacity, boolean compact) {
	public HgnSoundOptionsLayout {
		tabs = List.copyOf(tabs);
		actions = List.copyOf(actions);
		controls = List.copyOf(controls);
	}

	public static HgnSoundOptionsLayout resolve(int width, int height, int count,
		boolean masterFirst, int requestedPage, int actionCount) {
		boolean compact = height < 240;
		int compactGap = compact ? 4 : 8;
		int frameWidth = Math.min(760, width - 16);
		int frameHeight = Math.min(418, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2,
			frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 20 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 3 : 28),
			header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		int tabWidth = (frame.width() - 8) / 2;
		HgnRect firstTab = new HgnRect(frame.x(), header.bottom() + (compact ? 4 : 6), tabWidth, compact ? 20 : 24);
		HgnRect secondTab = new HgnRect(firstTab.right() + 8, firstTab.y(), tabWidth, firstTab.height());
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 24 : 36),
			frame.width(), compact ? 24 : 36);
		HgnRect content = new HgnRect(frame.x(), firstTab.bottom() + compactGap, frame.width(),
			footer.y() - firstTab.bottom() - 2 * compactGap);
		int controlHeight = compact ? 20 : 24;
		int gap = compact ? 4 : 10;
		int columns = frame.width() >= 604 ? 2 : 1;
		HgnRect inner = content.inset(compact ? 4 : 8);
		int rows = Math.max(1, (inner.height() + gap) / (controlHeight + gap));
		int capacity = columns * rows;
		int firstCapacity = capacity - (masterFirst ? columns - 1 : 0);
		int pages = count <= firstCapacity ? 1 : 1 + (count - firstCapacity + capacity - 1) / capacity;
		int page = Math.max(0, Math.min(requestedPage, pages - 1));
		int first = page == 0 ? 0 : firstCapacity + (page - 1) * capacity;
		int visible = Math.min(count - first, page == 0 ? firstCapacity : capacity);
		int controlWidth = (inner.width() - gap * (columns - 1)) / columns;
		List<HgnRect> controls = new ArrayList<>();
		for (int index = 0; index < visible; index++) {
			boolean master = masterFirst && page == 0 && index == 0;
			int slot = index + (masterFirst && page == 0 && index > 0 ? columns - 1 : 0);
			controls.add(new HgnRect(inner.x() + (slot % columns) * (controlWidth + gap),
				inner.y() + (slot / columns) * (controlHeight + gap),
				master ? inner.width() : controlWidth, controlHeight));
		}
		int navigationWidth = Math.min(90, (footer.width() - 32) / 4);
		HgnRect previous = new HgnRect(footer.x() + 8, footer.centerY() - controlHeight / 2,
			navigationWidth, controlHeight);
		HgnRect next = new HgnRect(footer.right() - 8 - navigationWidth, previous.y(),
			navigationWidth, controlHeight);
		int actionLane = next.x() - 8 - previous.right() - 8;
		int actionWidth = actionCount == 0 ? 0 : (actionLane - 8 * (actionCount - 1)) / actionCount;
		List<HgnRect> actions = new ArrayList<>();
		for (int index = 0; index < actionCount; index++) {
			actions.add(new HgnRect(previous.right() + 8 + index * (actionWidth + 8), previous.y(),
				actionWidth, controlHeight));
		}
		return new HgnSoundOptionsLayout(header, title, counter, List.of(firstTab, secondTab),
			content, footer, previous, next, actions, controls, first, page, pages,
			firstCapacity, capacity, compact);
	}

	public int pageContaining(int index) {
		return index < firstPageCapacity ? 0 : 1 + (index - firstPageCapacity) / capacity;
	}
}
