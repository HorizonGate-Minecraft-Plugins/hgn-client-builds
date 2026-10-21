package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Responsive language page; native row text requires a viewport-wide list at x=0. */
public record HgnLanguageLayout(HgnRect header, HgnRect title, HgnRect search, HgnRect listPanel,
	HgnRect languageList, HgnRect footer, HgnRect warning, List<HgnRect> actions, boolean compact) {
	public HgnLanguageLayout {
		actions = List.copyOf(actions);
	}

	public static HgnRect labelBounds(HgnRect lane, int textWidth, boolean centered) {
		int width = Math.min(lane.width(), Math.max(1, textWidth));
		return new HgnRect(lane.x() + (centered ? (lane.width() - width) / 2 : 0), lane.y(), width, lane.height());
	}

	public static HgnLanguageLayout resolve(int width, int height, boolean hasSearch, int actionCount) {
		int panelWidth = Math.min(720, width - 16);
		int panelHeight = Math.min(640, height - 16);
		HgnRect frame = new HgnRect((width - panelWidth) / 2, (height - panelHeight) / 2,
			panelWidth, panelHeight);
		boolean compact = height < 260;
		HgnRect header = new HgnRect(frame.x(), frame.y(), frame.width(), compact ? 42 : 62);
		int searchWidth = Math.min(260, Math.max(130, panelWidth / 3));
		HgnRect search = hasSearch ? new HgnRect(header.right() - searchWidth - 12,
			header.y() + (compact ? 10 : 20), searchWidth, compact ? 22 : 24) : null;
		HgnRect title = new HgnRect(header.x() + 12, header.y() + (compact ? 14 : 16),
			(hasSearch ? search.x() - 12 : header.right() - 12) - header.x() - 12, 12);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 48 : 66),
			frame.width(), compact ? 48 : 66);
		HgnRect warning = new HgnRect(footer.x() + 8, footer.y() + (compact ? 6 : 9),
			footer.width() - 16, 10);
		int listWidth = Math.min(540, panelWidth);
		HgnRect listPanel = new HgnRect((width - listWidth) / 2, header.bottom() + 8,
			listWidth, footer.y() - header.bottom() - 16);
		HgnRect languageList = new HgnRect(0, listPanel.y() + 4, width, listPanel.height() - 8);
		List<HgnRect> actions = new ArrayList<>();
		int gap = 8;
		int buttonWidth = actionCount == 0 ? 0 : (footer.width() - 16 - gap * (actionCount - 1)) / actionCount;
		int buttonHeight = compact ? 20 : 28;
		for (int index = 0; index < actionCount; index++) {
			actions.add(new HgnRect(footer.x() + 8 + index * (buttonWidth + gap),
				footer.bottom() - buttonHeight - (compact ? 6 : 9), buttonWidth, buttonHeight));
		}
		return new HgnLanguageLayout(header, title, search, listPanel, languageList, footer,
			warning, actions, compact);
	}
}
