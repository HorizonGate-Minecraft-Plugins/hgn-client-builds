package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** A full native scrolling list, separated from capture guidance and the original footer actions. */
public record HgnKeyBindsLayout(HgnRect header, HgnRect title, HgnRect statusPanel,
	HgnRect status, HgnRect hint, HgnRect listPanel, HgnRect list, HgnRect footer,
	List<HgnRect> actions, int rowWidth, boolean compact) {
	public HgnKeyBindsLayout {
		actions = List.copyOf(actions);
	}

	public static HgnKeyBindsLayout resolve(int width, int height, int actionCount) {
		boolean compact = height < 260;
		int gap = compact ? 4 : 8;
		int frameWidth = Math.min(820, width - 16);
		int frameHeight = Math.min(640, height - 16);
		HgnRect frame = new HgnRect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 28 : 52);
		HgnRect title = new HgnRect(header.x() + 10, header.y() + (compact ? 7 : 28), header.width() - 20, 14);
		HgnRect statusPanel = new HgnRect(frame.x(), header.bottom() + gap, frame.width(), compact ? 30 : 38);
		HgnRect status = new HgnRect(frame.x() + 10, statusPanel.y() + (compact ? 3 : 6), frame.width() - 20, 12);
		HgnRect hint = new HgnRect(status.x(), status.bottom() + 1, status.width(), 11);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 28 : 36), frame.width(), compact ? 28 : 36);
		HgnRect listPanel = new HgnRect(frame.x(), statusPanel.bottom() + gap, frame.width(), footer.y() - statusPanel.bottom() - gap * 2);
		// Native category rows center on the list width, not its x coordinate.
		HgnRect list = new HgnRect(0, listPanel.y() + 4, width, listPanel.height() - 8);
		int actionWidth = actionCount == 0 ? 0 : Math.min(200, (footer.width() - 16 - 8 * (actionCount - 1)) / actionCount);
		int actionLeft = footer.centerX() - (actionCount * actionWidth + Math.max(0, actionCount - 1) * 8) / 2;
		List<HgnRect> actions = new ArrayList<>();
		for (int index = 0; index < actionCount; index++) {
			actions.add(new HgnRect(actionLeft + index * (actionWidth + 8), footer.centerY() - 10, actionWidth, 20));
		}
		return new HgnKeyBindsLayout(header, title, statusPanel, status, hint, listPanel, list, footer,
			actions, rowWidth(width), compact);
	}

	public static int rowWidth(int viewportWidth) {
		return Math.max(1, Math.min(820, viewportWidth - 16) - 36);
	}

	/** Leaves the native collision stripe and both native buttons outside the visual label lane. */
	public static int labelRight(int labelLeft, int changeButtonX) {
		return Math.max(labelLeft, changeButtonX - 8);
	}
}
