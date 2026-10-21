package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Keeps sensitivity within reach while paging the native pointer and wheel/touch controls. */
public record HgnMouseSettingsLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect sensitivityPanel, HgnRect sensitivity, List<HgnRect> tabs, HgnRect content,
	HgnRect footer, HgnRect previous, HgnRect next, List<HgnRect> actions,
	List<HgnRect> controls, int firstControl, int page, int pages, int capacity, boolean compact) {
	public HgnMouseSettingsLayout {
		tabs = List.copyOf(tabs);
		actions = List.copyOf(actions);
		controls = List.copyOf(controls);
	}

	public static HgnMouseSettingsLayout resolve(int width, int height, int count, int requestedPage, int actionCount) {
		boolean compact = height < 260;
		int gap = compact ? 4 : 8;
		int controlHeight = compact ? 20 : 24;
		int frameWidth = Math.min(760, width - 16);
		int frameHeight = Math.min(448, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 20 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 3 : 28), header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		HgnRect sensitivityPanel = new HgnRect(frame.x(), header.bottom() + gap, frame.width(), compact ? 24 : 56);
		HgnRect sensitivity;
		HgnRect pointerTab;
		HgnRect wheelTab;
		if (compact) {
			int tabWidth = Math.min(72, Math.max(44, (sensitivityPanel.width() - 20) / 4));
			int sensitivityWidth = sensitivityPanel.width() - 12 - tabWidth * 2 - gap * 2;
			sensitivity = new HgnRect(sensitivityPanel.x() + 6, sensitivityPanel.y() + 2,
				sensitivityWidth, controlHeight);
			pointerTab = new HgnRect(sensitivity.right() + gap, sensitivity.y(), tabWidth, controlHeight);
			wheelTab = new HgnRect(pointerTab.right() + gap, pointerTab.y(), tabWidth, controlHeight);
		} else {
			sensitivity = new HgnRect(sensitivityPanel.x() + 8,
				sensitivityPanel.centerY() - controlHeight / 2, sensitivityPanel.width() - 16, controlHeight);
			int tabWidth = (frame.width() - 8) / 2;
			pointerTab = new HgnRect(frame.x(), sensitivityPanel.bottom() + gap, tabWidth, controlHeight);
			wheelTab = new HgnRect(pointerTab.right() + 8, pointerTab.y(), tabWidth, controlHeight);
		}
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 24 : 36), frame.width(), compact ? 24 : 36);
		HgnRect content = new HgnRect(frame.x(), pointerTab.bottom() + gap, frame.width(), footer.y() - pointerTab.bottom() - 2 * gap);
		HgnRect inner = content.inset(compact ? 6 : 10);
		int columns = frame.width() >= 604 ? 2 : 1;
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
		return new HgnMouseSettingsLayout(header, title, counter, sensitivityPanel, sensitivity,
			List.of(pointerTab, wheelTab), content, footer, previous, next, actions, controls, first, page, pages, capacity, compact);
	}

	/** Initial or cleared focus has no page; immutable native-control lists reject indexOf(null). */
	public static int focusedControlIndex(List<?> controls, Object focused) {
		return focused == null ? -1 : controls.indexOf(focused);
	}

	public int pageContaining(int index) {
		return index / capacity;
	}
}
