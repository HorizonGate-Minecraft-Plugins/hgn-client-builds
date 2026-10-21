package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Two readable native font controls, with compact spacing before logical pixels become cramped. */
public record HgnFontOptionsLayout(HgnRect header, HgnRect title, List<HgnRect> rows,
	List<HgnRect> controls, HgnRect footer, List<HgnRect> actions, boolean compact) {
	public HgnFontOptionsLayout {
		rows = List.copyOf(rows);
		controls = List.copyOf(controls);
		actions = List.copyOf(actions);
	}

	public static HgnFontOptionsLayout resolve(int width, int height, int actionCount) {
		boolean compact = height < 240;
		int panelWidth = Math.min(680, width - 16);
		int panelHeight = Math.min(318, height - 16);
		HgnRect frame = new HgnRect((width - panelWidth) / 2, (height - panelHeight) / 2,
			panelWidth, panelHeight);
		HgnRect header = new HgnRect(frame.x(), frame.y(), panelWidth, compact ? 36 : 62);
		HgnRect title = new HgnRect(header.x() + 12, header.y() + (compact ? 11 : 31),
			header.width() - 24, 14);
		HgnRect footer = new HgnRect(frame.x(), frame.bottom() - (compact ? 32 : 44),
			panelWidth, compact ? 32 : 44);
		int rowTop = header.bottom() + 8;
		int rowHeight = (footer.y() - rowTop - 16) / 2;
		int controlHeight = compact ? 20 : 28;
		List<HgnRect> rows = new ArrayList<>();
		List<HgnRect> controls = new ArrayList<>();
		for (int index = 0; index < 2; index++) {
			HgnRect row = new HgnRect(frame.x(), rowTop + index * (rowHeight + 8), panelWidth, rowHeight);
			rows.add(row);
			controls.add(new HgnRect(row.x() + 40, row.y() + (rowHeight - controlHeight) / 2,
				row.width() - 52, controlHeight));
		}
		List<HgnRect> actions = new ArrayList<>();
		int actionWidth = actionCount == 0 ? 0 : Math.min(300, (footer.width() - 16 - (actionCount - 1) * 8) / actionCount);
		int actionLeft = footer.centerX() - (actionCount * actionWidth + Math.max(0, actionCount - 1) * 8) / 2;
		for (int index = 0; index < actionCount; index++) {
			actions.add(new HgnRect(actionLeft + index * (actionWidth + 8),
				footer.y() + (footer.height() - controlHeight) / 2, actionWidth, controlHeight));
		}
		return new HgnFontOptionsLayout(header, title, rows, controls, footer, actions, compact);
	}
}
