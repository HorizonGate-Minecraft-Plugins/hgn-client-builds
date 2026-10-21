package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Keeps native input destinations visible while paging the retained control preferences. */
public record HgnControlsLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect navigation, List<HgnRect> destinations, HgnRect content, HgnRect footer,
	HgnRect previous, HgnRect next, List<HgnRect> actions, List<HgnRect> controls,
	int firstControl, int page, int pages, int capacity, boolean compact) {
	public HgnControlsLayout {
		destinations = List.copyOf(destinations);
		actions = List.copyOf(actions);
		controls = List.copyOf(controls);
	}

	public static HgnControlsLayout resolve(int width, int height, int count, int requestedPage, int actionCount) {
		boolean compact = height < 240;
		int frameWidth = Math.min(760, width - 16);
		int frameHeight = Math.min(418, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 20 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 3 : 28), header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		int controlHeight = compact ? 20 : 24;
		HgnRect navigation = new HgnRect(frame.x(), header.bottom() + (compact ? 4 : 6),
			frame.width(), controlHeight + (compact ? 4 : 12));
		int destinationWidth = (navigation.width() - 24) / 2;
		HgnRect mouse = new HgnRect(navigation.x() + 8, navigation.y() + (compact ? 2 : 6), destinationWidth, controlHeight);
		HgnRect keys = new HgnRect(mouse.right() + 8, mouse.y(), destinationWidth, controlHeight);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 24 : 36), frame.width(), compact ? 24 : 36);
		HgnRect content = new HgnRect(frame.x(), navigation.bottom() + (compact ? 4 : 6), frame.width(),
			footer.y() - navigation.bottom() - (compact ? 8 : 12));
		int gap = compact ? 4 : 10;
		int columns = frame.width() >= 604 ? 2 : 1;
		HgnRect inner = content.inset(compact ? 4 : 8);
		int rows = Math.max(1, (inner.height() + gap) / (controlHeight + gap));
		int capacity = columns * rows;
		int pages = Math.max(1, (count + capacity - 1) / capacity);
		int page = Math.max(0, Math.min(requestedPage, pages - 1));
		int first = page * capacity;
		int controlWidth = (inner.width() - gap * (columns - 1)) / columns;
		List<HgnRect> controls = new ArrayList<>();
		for (int index = 0; index < Math.min(count - first, capacity); index++) {
			controls.add(new HgnRect(inner.x() + (index % columns) * (controlWidth + gap),
				inner.y() + (index / columns) * (controlHeight + gap), controlWidth, controlHeight));
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
		return new HgnControlsLayout(header, title, counter, navigation, List.of(mouse, keys), content,
			footer, previous, next, actions, controls, first, page, pages, capacity, compact);
	}

	public int pageContaining(int index) {
		return index / capacity;
	}
}
