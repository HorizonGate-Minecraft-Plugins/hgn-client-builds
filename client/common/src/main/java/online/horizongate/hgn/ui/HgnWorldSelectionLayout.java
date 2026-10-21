package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;

/** Responsive reference shell around the native search, saved worlds and actions. */
public record HgnWorldSelectionLayout(HgnRect header, HgnRect search, HgnRect navigationPanel,
	List<HgnRect> navigation, HgnRect listPanel, HgnRect hero, HgnRect worldList,
	HgnRect actionsPanel, HgnRect selectedWorld, List<HgnRect> actions,
	boolean sidebar, boolean compact) {
	public HgnWorldSelectionLayout {
		navigation = List.copyOf(navigation);
		actions = List.copyOf(actions);
	}

	public boolean showsPlayerFooter() {
		return sidebar && (actions.isEmpty()
			|| actions.get(actions.size() - 1).bottom() + 12 <= actionsPanel.bottom() - 45);
	}

	public boolean showsSelectedWorld() {
		return sidebar && selectedWorld.width() > 0 && selectedWorld.height() > 0;
	}

	public boolean selectedWorldInLibrary() {
		return showsSelectedWorld() && selectedWorld.x() >= worldList.x()
			&& selectedWorld.right() <= listPanel.right() && selectedWorld.y() == worldList.y();
	}

	public boolean selectedWorldInHero() {
		return showsSelectedWorld() && selectedWorld.equals(hero);
	}

	public static HgnWorldSelectionLayout resolve(int width, int height, int actionCount) {
		int panelWidth = Math.min(1100, width - 16);
		int panelHeight = Math.min(640, height - 16);
		HgnRect frame = new HgnRect((width - panelWidth) / 2, (height - panelHeight) / 2,
			panelWidth, panelHeight);
		boolean compact = height < 260;
		HgnRect header = new HgnRect(frame.x(), frame.y(), panelWidth, compact ? 42 : 62);
		int searchWidth = Math.min(300, Math.max(130, panelWidth / 3));
		HgnRect search = new HgnRect(header.right() - searchWidth - 12,
			header.y() + (compact ? 10 : 20), searchWidth, compact ? 22 : 24);
		int contentTop = header.bottom() + 8;
		int contentHeight = frame.bottom() - contentTop;
		boolean sidebar = width >= 620 && contentHeight >= 70 + actionCount * 20 + Math.max(0, actionCount - 1) * 6;
		HgnRect navigationPanel = new HgnRect(0, 0, 0, 0);
		List<HgnRect> navigation = new ArrayList<>();
		HgnRect listPanel;
		HgnRect hero = new HgnRect(0, 0, 0, 0);
		HgnRect worldList;
		HgnRect actionsPanel;
		HgnRect selectedWorld = new HgnRect(0, 0, 0, 0);
		List<HgnRect> actions = new ArrayList<>();
		if (sidebar) {
			int navigationWidth = Math.min(174, Math.max(136, panelWidth / 5));
			int actionWidth = Math.min(220, Math.max(160, panelWidth / 5));
			navigationPanel = new HgnRect(frame.x(), contentTop, navigationWidth, contentHeight);
			actionsPanel = new HgnRect(frame.right() - actionWidth, contentTop, actionWidth, contentHeight);
			listPanel = new HgnRect(navigationPanel.right() + 8, contentTop,
				actionsPanel.x() - navigationPanel.right() - 16, contentHeight);
			int navigationHeight = Math.min(30, Math.max(20, (contentHeight - 72) / 8));
			for (int index = 0; index < 8; index++) {
				navigation.add(new HgnRect(navigationPanel.x() + 8,
					navigationPanel.y() + 30 + index * (navigationHeight + 4),
					navigationPanel.width() - 16, navigationHeight));
			}
			int heroHeight = Math.min(90, Math.max(52, contentHeight / 4));
			hero = new HgnRect(listPanel.x() + 6, listPanel.y() + 6,
				listPanel.width() - 12, heroHeight);
			HgnRect library = new HgnRect(listPanel.x() + 6, hero.bottom() + 22,
				listPanel.width() - 12, listPanel.bottom() - hero.bottom() - 28);
			worldList = library;
			if (listPanel.width() >= 650 && library.height() >= 210) {
				int worldListWidth = Math.max(280, library.width() * 55 / 100);
				worldList = new HgnRect(library.x(), library.y(), worldListWidth, library.height());
				selectedWorld = new HgnRect(worldList.right() + 8, library.y(),
					library.right() - worldList.right() - 8, library.height());
			}
			boolean constrainedActions = contentHeight < 330;
			int buttonGap = constrainedActions ? 2 : 4;
			int maxButtonHeight = constrainedActions ? 20 : 24;
			int buttonHeight = actionCount == 0 ? maxButtonHeight : Math.min(maxButtonHeight,
				(contentHeight - 70 - Math.max(0, actionCount - 1) * buttonGap) / actionCount);
			int baseActionBottom = actionsPanel.y() + 56
				+ Math.max(0, actionCount - 1) * (buttonHeight + buttonGap) + buttonHeight;
			boolean footerFits = !constrainedActions
				&& baseActionBottom + 12 <= actionsPanel.bottom() - 45;
			int actionLimit = actionsPanel.bottom() - (footerFits ? 57 : 12);
			int availableDetailSpace = actionLimit - baseActionBottom;
			int detailsHeight = selectedWorld.width() == 0 && availableDetailSpace >= 74
				? Math.min(96, availableDetailSpace - 6) : 0;
			int detailsShift = detailsHeight == 0 ? 0 : detailsHeight + 6;
			if (detailsHeight > 0) {
				selectedWorld = new HgnRect(actionsPanel.x() + 12, actionsPanel.y() + 52,
					actionsPanel.width() - 24, detailsHeight);
			} else if (selectedWorld.width() == 0 && hero.height() >= 64) {
				selectedWorld = hero;
			}
			for (int index = 0; index < actionCount; index++) {
				actions.add(new HgnRect(actionsPanel.x() + 12,
					actionsPanel.y() + 56 + detailsShift + index * (buttonHeight + buttonGap),
					actionsPanel.width() - 24, buttonHeight));
			}
		} else {
			int buttonHeight = compact ? 20 : 24;
			int rows = actionCount > 2 ? 2 : 1;
			int actionHeight = rows * buttonHeight + (rows - 1) * 6 + 12;
			actionsPanel = new HgnRect(frame.x(), frame.bottom() - actionHeight, panelWidth, actionHeight);
			listPanel = new HgnRect(frame.x(), contentTop, panelWidth, actionsPanel.y() - contentTop - 8);
			worldList = listPanel.inset(compact ? 4 : 6);
			for (int index = 0; index < actionCount; index++) {
				int row = index < 2 ? 0 : 1;
				int column = row == 0 ? index : index - 2;
				int columns = row == 0 ? Math.min(2, actionCount) : actionCount - 2;
				int buttonWidth = (panelWidth - 12 - (columns - 1) * 6) / columns;
				actions.add(new HgnRect(actionsPanel.x() + 6 + column * (buttonWidth + 6),
					actionsPanel.y() + 6 + row * (buttonHeight + 6), buttonWidth, buttonHeight));
			}
		}
		return new HgnWorldSelectionLayout(header, search, navigationPanel, navigation,
			listPanel, hero, worldList, actionsPanel, selectedWorld, actions, sidebar, compact);
	}
}
