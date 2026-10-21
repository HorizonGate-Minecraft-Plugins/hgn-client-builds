package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Separates native skin layers from a persistent main-hand control without shrinking compact targets. */
public record HgnSkinCustomizationLayout(HgnRect header, HgnRect title, HgnRect counter,
	HgnRect layersPanel, HgnRect handPanel, HgnRect mainHand, HgnRect footer,
	HgnRect previous, HgnRect next, List<HgnRect> actions, List<HgnRect> controls,
	int firstControl, int page, int pages, int capacity, boolean compact, boolean sideBySide) {
	public HgnSkinCustomizationLayout {
		actions = List.copyOf(actions);
		controls = List.copyOf(controls);
	}

	public static HgnSkinCustomizationLayout resolve(int width, int height, int count, int requestedPage, int actionCount) {
		boolean compact = height < 260;
		boolean sideBySide = width >= 740 && height >= 300;
		int gap = compact ? 4 : 8;
		int controlHeight = compact ? 20 : 24;
		int frameWidth = Math.min(800, width - 16);
		int frameHeight = Math.min(440, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 24 : 50);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 5 : 28), header.width() - 76, 14);
		HgnRect counter = new HgnRect(header.right() - 56, title.y(), 46, 14);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 28 : 36), frame.width(), compact ? 28 : 36);
		int bodyY = header.bottom() + gap;
		int bodyHeight = footer.y() - gap - bodyY;
		HgnRect handPanel = sideBySide
			? new HgnRect(frame.right() - 236, bodyY, 236, bodyHeight)
			: new HgnRect(frame.x(), bodyY, frame.width(), compact ? 28 : 54);
		HgnRect layersPanel = sideBySide
			? new HgnRect(frame.x(), bodyY, handPanel.x() - gap - frame.x(), bodyHeight)
			: new HgnRect(frame.x(), handPanel.bottom() + gap, frame.width(), footer.y() - handPanel.bottom() - 2 * gap);
		HgnRect mainHand = new HgnRect(handPanel.x() + 8, handPanel.y() + (compact ? 4 : 22), handPanel.width() - 16, controlHeight);
		int inset = compact ? 6 : 10;
		int headingHeight = compact ? 0 : 20;
		HgnRect inner = new HgnRect(layersPanel.x() + inset, layersPanel.y() + inset + headingHeight,
			layersPanel.width() - inset * 2, layersPanel.height() - inset * 2 - headingHeight);
		int columns = inner.width() >= 440 ? 2 : 1;
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
		return new HgnSkinCustomizationLayout(header, title, counter, layersPanel, handPanel, mainHand,
			footer, previous, next, actions, controls, first, page, pages, capacity, compact, sideBySide);
	}

	public int pageContaining(int index) {
		return index / capacity;
	}
}
