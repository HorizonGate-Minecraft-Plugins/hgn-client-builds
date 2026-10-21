package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** A persistent chat-mode lane beside wide preferences, stacked above paged compact controls. */
public record HgnChatOptionsLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect navigation, HgnRect visibility, List<HgnRect> tabs, HgnRect content,
	HgnRect footer, HgnRect previous, HgnRect next, List<HgnRect> actions,
	List<HgnRect> controls, int firstControl, int page, int pages, int capacity,
	boolean compact, boolean sideBySide) {
	public HgnChatOptionsLayout {
		tabs = List.copyOf(tabs);
		actions = List.copyOf(actions);
		controls = List.copyOf(controls);
	}

	public static HgnChatOptionsLayout resolve(int width, int height, int count, int requestedPage, int actionCount) {
		boolean compact = height < 260;
		boolean sideBySide = width >= 820 && height >= 300;
		int gap = compact ? 4 : 8;
		int controlHeight = compact ? 20 : 24;
		int frameWidth = Math.min(900, width - 16);
		int frameHeight = Math.min(480, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 24 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 5 : 28), header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 28 : 36), frame.width(), compact ? 28 : 36);
		int bodyY = header.bottom() + gap;
		HgnRect navigation;
		HgnRect visibility;
		HgnRect displayTab;
		HgnRect conversationTab;
		HgnRect content;
		if (sideBySide) {
			navigation = new HgnRect(frame.x(), bodyY, 212, footer.y() - bodyY - gap);
			visibility = new HgnRect(navigation.x() + 10, navigation.y() + 30, navigation.width() - 20, controlHeight);
			displayTab = new HgnRect(visibility.x(), visibility.bottom() + 24, visibility.width(), controlHeight);
			conversationTab = new HgnRect(visibility.x(), displayTab.bottom() + gap, visibility.width(), controlHeight);
			content = new HgnRect(navigation.right() + gap, bodyY, frame.width() - navigation.width() - gap, navigation.height());
		} else {
			navigation = new HgnRect(frame.x(), bodyY, frame.width(), compact ? 28 : 40);
			visibility = new HgnRect(navigation.x() + 8, navigation.centerY() - controlHeight / 2, navigation.width() - 16, controlHeight);
			int tabWidth = (frame.width() - 8) / 2;
			displayTab = new HgnRect(frame.x(), navigation.bottom() + gap, tabWidth, controlHeight);
			conversationTab = new HgnRect(displayTab.right() + 8, displayTab.y(), tabWidth, controlHeight);
			content = new HgnRect(frame.x(), displayTab.bottom() + gap, frame.width(), footer.y() - displayTab.bottom() - 2 * gap);
		}
		HgnRect inner = content.inset(compact ? 6 : 10);
		int columns = inner.width() >= 560 ? 2 : 1;
		int rowGap = compact ? 6 : 10;
		int rows = Math.max(1, (inner.height() + rowGap) / (controlHeight + rowGap));
		int capacity = columns * rows;
		int pages = Math.max(1, (count + capacity - 1) / capacity);
		int page = Math.max(0, Math.min(requestedPage, pages - 1));
		int first = page * capacity;
		int controlWidth = (inner.width() - rowGap * (columns - 1)) / columns;
		List<HgnRect> controls = new ArrayList<>();
		for (int index = 0; index < Math.min(count - first, capacity); index++) {
			controls.add(new HgnRect(inner.x() + (index % columns) * (controlWidth + rowGap),
				inner.y() + (index / columns) * (controlHeight + rowGap), controlWidth, controlHeight));
		}
		int navigationWidth = Math.min(90, (footer.width() - 32) / 4);
		HgnRect previous = new HgnRect(footer.x() + 8, footer.centerY() - controlHeight / 2, navigationWidth, controlHeight);
		HgnRect next = new HgnRect(footer.right() - 8 - navigationWidth, previous.y(), navigationWidth, controlHeight);
		int actionLane = next.x() - previous.right() - 16;
		int actionWidth = actionCount == 0 ? 0 : (actionLane - 8 * (actionCount - 1)) / actionCount;
		List<HgnRect> actions = new ArrayList<>();
		for (int index = 0; index < actionCount; index++) {
			actions.add(new HgnRect(previous.right() + 8 + index * (actionWidth + 8), previous.y(), actionWidth, controlHeight));
		}
		return new HgnChatOptionsLayout(header, title, counter, navigation, visibility,
			List.of(displayTab, conversationTab), content, footer, previous, next, actions,
			controls, first, page, pages, capacity, compact, sideBySide);
	}

	public int pageContaining(int index) {
		return index / capacity;
	}

	/** Initial or cleared focus has no page; immutable native-control lists reject indexOf(null). */
	public static int focusedControlIndex(List<?> controls, Object focused) {
		return focused == null ? -1 : controls.indexOf(focused);
	}
}
