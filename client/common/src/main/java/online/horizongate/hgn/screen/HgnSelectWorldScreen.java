package online.horizongate.hgn.screen;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnOfficialServers;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnWorldSelectionLayout;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** HGN world library retaining native loading, selection, actions and resource cleanup. */
public final class HgnSelectWorldScreen extends SelectWorldScreen {
	private final List<AbstractWidget> retainedWidgets = new ArrayList<>();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final HgnButton[] navigationButtons = new HgnButton[8];
	private static final String[] NAVIGATION_LABELS = {"PLAY", "SINGLEPLAYER", "MULTIPLAYER",
		"SERVERS", "COSMETICS", "MODS", "SETTINGS", "BACK"};
	private static final HgnIcon[] NAVIGATION_ICONS = {HgnIcon.PLAY, HgnIcon.WORLD,
		HgnIcon.MULTIPLAYER, HgnIcon.SERVERS, HgnIcon.COSMETICS, HgnIcon.MODS,
		HgnIcon.SETTINGS, HgnIcon.BACK};
	private record SelectedWorld(String name, String id, String mode, String version,
		String lastPlayed) {
		private static SelectedWorld from(LevelSummary summary) {
			String mode = summary.isDisabled() ? "Unavailable"
				: summary.isHardcore() ? "Hardcore" : summary.getGameMode().getName();
			String version = summary.isDisabled() ? "Unavailable"
				: summary.getWorldVersionName().getString();
			long lastPlayed = summary.getLastPlayed();
			String date = lastPlayed <= 0 ? "Unknown"
				: DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
					.format(new Date(lastPlayed));
			return new SelectedWorld(worldName(summary), summary.getLevelId(), mode, version, date);
		}
	}
	private WorldSelectionList worlds;
	private SelectedWorld selectedWorld;
	private HgnWorldSelectionLayout layout;
	private boolean collecting;

	public HgnSelectWorldScreen(Screen parent) {
		super(parent);
	}

	/** Keeps vanilla's automatic create-world redirect from hiding an empty HGN library. */
	public static Screen open(Screen parent) {
		try {
			if (Minecraft.getInstance().getLevelSource().findLevelCandidates().isEmpty()) {
				return new HgnEmptyWorldScreen(parent);
			}
		} catch (LevelStorageException exception) {
			HgnClient.LOGGER.warn("Could not inspect saved worlds; opening the native world list", exception);
		}
		return new HgnSelectWorldScreen(parent);
	}

	@Override
	protected void init() {
		selectedWorld = null;
		collecting = true;
		try {
			super.init();
		} finally {
			collecting = false;
		}
		// Render one prompt ourselves so it stays visible while native search starts focused.
		searchBox.setHint(Component.empty());
		retainedWidgets.clear();
		actions.clear();
		worlds = null;
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget) || widget instanceof StringWidget
				|| widget instanceof HgnButton) continue;
			retainedWidgets.add(widget);
			if (widget instanceof WorldSelectionList list) worlds = list;
			else if (widget != searchBox) actions.add(widget);
		}
		var focused = getFocused();
		String focusedRoute = focused instanceof HgnButton button ? button.getMessage().getString() : null;
		clearWidgets();
		for (int index = 0; index < navigationButtons.length; index++) navigationButtons[index] = null;
		for (AbstractWidget widget : retainedWidgets) addRenderableWidget(widget);
		arrange();
		if (focused != null && retainedWidgets.contains(focused)) setFocused(focused);
		else if (focusedRoute != null) {
			for (HgnButton button : navigationButtons) {
				if (button != null && button.visible
					&& button.getMessage().getString().equals(focusedRoute)) setFocused(button);
			}
		}
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (!collecting && worlds != null) arrange();
	}

	private void arrange() {
		layout = HgnWorldSelectionLayout.resolve(width, height, actions.size());
		place(searchBox, layout.search());
		if (worlds != null) {
			HgnRect bounds = layout.worldList();
			worlds.updateSizeAndPosition(bounds.width(), bounds.height(), bounds.y());
			worlds.setX(bounds.x());
		}
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), layout.actions().get(index));
		for (int index = 0; index < navigationButtons.length; index++) {
			if (index == 1) continue;
			HgnButton button = navigationButtons[index];
			if (layout.sidebar()) {
				if (button == null) {
					int destination = index;
					HgnRect bounds = layout.navigation().get(index);
					button = addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
						bounds.width(), bounds.height(), Component.literal(NAVIGATION_LABELS[index]),
						NAVIGATION_ICONS[index], false, ignored -> openDestination(destination)));
					navigationButtons[index] = button;
				}
				place(button, layout.navigation().get(index));
				button.visible = true;
				button.active = true;
			} else if (button != null) {
				button.visible = false;
				button.active = false;
				place(button, new HgnRect(0, 0, 0, 0));
				if (getFocused() == button) setFocused(searchBox);
			}
		}
	}

	private void openDestination(int index) {
		switch (index) {
			case 0 -> {
				var hub = HgnOfficialServers.HUB;
				if (hub.playableNow()) HgnCompat.connect(this, minecraft, hub.name(), hub.address());
				else HgnCompat.openUrl("hgn://play/" + hub.id() + "/" + hub.requiredVersion());
			}
			case 2 -> minecraft.setScreen(new HgnMultiplayerScreen(this));
			case 3 -> minecraft.setScreen(new HgnSavedServersScreen(this));
			case 4 -> minecraft.setScreen(new HgnCosmeticsScreen(this));
			case 5 -> minecraft.setScreen(new HgnInstalledModsScreen(this));
			case 6 -> minecraft.setScreen(new HgnClientSettingsScreen(this));
			case 7 -> onClose();
			default -> { }
		}
	}

	private static void place(AbstractWidget widget, HgnRect bounds) {
		widget.setX(bounds.x());
		widget.setY(bounds.y());
		widget.setWidth(bounds.width());
		widget.setHeight(bounds.height());
	}

	public int worldMetadataRightEdge() {
		// Minecraft centers its fixed-width rows inside wider list viewports.
		return worlds == null ? width : worlds.getRowRight() - 8;
	}

	@Override
	public void updateButtonStatus(LevelSummary summary) {
		super.updateButtonStatus(summary);
		selectedWorld = summary == null ? null : SelectedWorld.from(summary);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null) return;
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.header(), true);
		if (layout.sidebar()) HgnSurface.panel(graphics, layout.navigationPanel(), false);
		HgnSurface.panel(graphics, layout.listPanel(), false);
		HgnSurface.panel(graphics, layout.actionsPanel(), false);
		renderHeader(graphics);
		if (layout.sidebar()) {
			SelectedWorld selected = worlds != null && worlds.getSelectedOpt().isPresent()
				? selectedWorld : null;
			renderNavigation(graphics);
			if (layout.selectedWorldInHero() && selected != null) renderSelectedWorld(graphics, selected);
			else renderHero(graphics);
			HgnRect hero = layout.hero();
			HgnTypography.fitted(graphics, font, "YOUR WORLDS", hero.x() + 4,
				hero.bottom() + 8, hero.width() - 8, 0.94f, HgnPalette.CYAN);
			HgnRect panel = layout.actionsPanel();
			HgnTypography.fitted(graphics, font, "WORLD ACTIONS", panel.x() + 12, panel.y() + 14,
				panel.width() - 24, 1.05f, HgnPalette.CYAN);
			String hint = selected == null ? "Select a world to continue"
				: layout.showsSelectedWorld() ? "Your selected world" : selected.name();
			HgnTypography.fitted(graphics, font, hint, panel.x() + 12, panel.y() + 34,
				panel.width() - 24, 0.9f, HgnPalette.mutedText());
			if (layout.showsSelectedWorld() && !layout.selectedWorldInHero()) {
				renderSelectedWorld(graphics, selected);
			}
			if (layout.showsPlayerFooter()) {
				HgnSurface.separator(graphics, panel.x() + 12, panel.bottom() - 45,
					panel.width() - 24, false);
				HgnTypography.fitted(graphics, font, "PLAYER", panel.x() + 12,
					panel.bottom() - 36, panel.width() - 24, 0.75f, HgnPalette.CYAN);
				HgnTypography.fitted(graphics, font, minecraft.getUser().getName(), panel.x() + 12,
					panel.bottom() - 22, panel.width() - 24, 0.94f, HgnPalette.TEXT);
			}
		}
		// Native renderWithTooltip still surrounds this pass; skip only vanilla's fixed title/search pass.
		for (AbstractWidget widget : retainedWidgets) widget.render(graphics, mouseX, mouseY, partialTick);
		if (searchBox.getValue().isEmpty()) {
			HgnRect search = layout.search();
			HgnTypography.fitted(graphics, font, "Search worlds...", search.x() + 14,
				search.y() + Math.max(5, (search.height() - font.lineHeight) / 2),
				search.width() - 22, 0.82f, HgnPalette.mutedText());
		}
		for (HgnButton button : navigationButtons) {
			if (button != null && button.visible) button.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	private void renderSelectedWorld(GuiGraphics graphics, SelectedWorld selected) {
		HgnRect details = layout.selectedWorld();
		HgnSurface.dashboardInset(graphics, details, selected != null);
		if (layout.selectedWorldInHero()) {
			renderHeroWorldDetails(graphics, details, selected);
			return;
		}
		if (layout.selectedWorldInLibrary()) {
			renderLibraryWorldDetails(graphics, details, selected);
			return;
		}
		if (selected == null) {
			HgnTypography.fitted(graphics, font, "No world selected", details.x() + 8,
				details.y() + 22, details.width() - 16, 0.85f, HgnPalette.mutedText());
			return;
		}
		HgnIcon.WORLD.draw(graphics, details.x() + 8, details.y() + 7, 16, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, selected.name(), details.width() - 37), details.x() + 29,
			details.y() + 9, details.width() - 37, 0.94f, HgnPalette.TEXT);
		int firstRow = details.y() + 25;
		if (details.height() >= 90) {
			renderCompactWorldFact(graphics, details, "SAVE  " + selected.id(), firstRow);
			firstRow += 17;
		}
		renderCompactWorldFact(graphics, details, "MODE  " + selected.mode(), firstRow);
		renderCompactWorldFact(graphics, details, "VERSION  " + selected.version(), firstRow + 13);
		renderCompactWorldFact(graphics, details, "LAST PLAYED  " + selected.lastPlayed(),
			firstRow + 26);
	}

	private void renderHeroWorldDetails(GuiGraphics graphics, HgnRect details,
		SelectedWorld selected) {
		if (selected == null) return;
		HgnTypography.fitted(graphics, font, "SELECTED WORLD", details.x() + 10,
			details.y() + 7, details.width() - 20, 0.72f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, selected.name(), details.width() - 20),
			details.x() + 10, details.y() + 20, details.width() - 20, 1.08f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, selected.mode() + "  /  " + selected.version(),
				details.width() - 20), details.x() + 10, details.y() + 38,
			details.width() - 20, 0.8f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, "Last played: " + selected.lastPlayed(), details.width() - 20),
			details.x() + 10, details.y() + 52, details.width() - 20,
			0.75f, HgnPalette.mutedText());
	}

	private void renderCompactWorldFact(GuiGraphics graphics, HgnRect details,
		String fact, int y) {
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, fact, details.width() - 16), details.x() + 8,
			y, details.width() - 16, 0.77f, HgnPalette.mutedText());
	}

	private void renderLibraryWorldDetails(GuiGraphics graphics, HgnRect details,
		SelectedWorld selected) {
		HgnTypography.fitted(graphics, font, "SELECTED WORLD", details.x() + 12,
			details.y() + 12, details.width() - 24, 0.85f, HgnPalette.CYAN);
		if (selected == null) {
			HgnTypography.fitted(graphics, font, "Select a saved world", details.x() + 12,
				details.y() + 43, details.width() - 24, 1.05f, HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font, "Its real details will appear here.",
				details.x() + 12, details.y() + 64, details.width() - 24,
				0.8f, HgnPalette.mutedText());
			return;
		}
		HgnIcon.WORLD.draw(graphics, details.x() + 12, details.y() + 34, 28, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, selected.name(), details.width() - 62),
			details.x() + 50, details.y() + 35, details.width() - 62, 1.18f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, selected.id(), details.width() - 62),
			details.x() + 50, details.y() + 54, details.width() - 62,
			0.74f, HgnPalette.mutedText());
		HgnSurface.separator(graphics, details.x() + 12, details.y() + 73,
			details.width() - 24, true);
		renderLibraryWorldFact(graphics, details, 80, "GAME MODE", selected.mode());
		renderLibraryWorldFact(graphics, details, 124, "VERSION", selected.version());
		renderLibraryWorldFact(graphics, details, 168, "LAST PLAYED", selected.lastPlayed());
	}

	private void renderLibraryWorldFact(GuiGraphics graphics, HgnRect details,
		int offsetY, String label, String value) {
		HgnRect row = new HgnRect(details.x() + 10, details.y() + offsetY,
			details.width() - 20, 38);
		HgnSurface.dashboardInset(graphics, row, false);
		HgnTypography.fitted(graphics, font, label, row.x() + 8, row.y() + 6,
			row.width() - 16, 0.71f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font,
			HgnTypography.trim(font, value, row.width() - 16), row.x() + 8,
			row.y() + 20, row.width() - 16, 0.94f, HgnPalette.TEXT);
	}

	private static String worldName(LevelSummary summary) {
		String name = summary.getLevelName();
		return name == null || name.isBlank() ? summary.getLevelId() : name;
	}

	private void renderHeader(GuiGraphics graphics) {
		int textX = layout.header().x() + 12;
		int textWidth = layout.search().x() - textX - 12;
		if (layout.sidebar()) {
			HgnSurface.wordmark(graphics, textX, layout.header().y() + 8, 55, 38, false);
			HgnTypography.fittedHeading(graphics, font, "HORIZONGATE NETWORK CLIENT", textX + 65,
				layout.header().y() + 11, textWidth - 65, 1.05f, HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font, "SINGLEPLAYER  /  YOUR WORLDS. YOUR STORIES.",
				textX + 65, layout.header().y() + 34, textWidth - 65, 0.8f, HgnPalette.CYAN);
		} else {
			HgnTypography.fittedHeading(graphics, font, "SINGLEPLAYER", textX,
				layout.header().y() + 14, textWidth, 1.1f, HgnPalette.TEXT);
		}
	}

	private void renderNavigation(GuiGraphics graphics) {
		HgnRect panel = layout.navigationPanel();
		HgnTypography.fitted(graphics, font, "HGN CLIENT", panel.x() + 9,
			panel.y() + 11, panel.width() - 18, 0.82f, HgnPalette.CYAN);
		HgnRect selected = layout.navigation().get(1);
		HgnSurface.dashboardInset(graphics, selected, true);
		int iconSize = Math.min(18, selected.height() - 6);
		HgnIcon.WORLD.draw(graphics, selected.x() + 7,
			selected.centerY() - iconSize / 2, iconSize, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, NAVIGATION_LABELS[1], selected.x() + 33,
			selected.y() + Math.max(5, (selected.height() - font.lineHeight) / 2),
			selected.width() - 40, 0.9f, HgnPalette.TEXT);
	}

	private void renderHero(GuiGraphics graphics) {
		HgnRect hero = layout.hero();
		HgnSurface.dashboardInset(graphics, hero, true);
		HgnDraw.textureStretch(graphics, "textures/gui/dashboard_welcome_city.png",
			hero.x() + hero.width() / 2, hero.y() + 2, hero.width() / 2 - 2,
			hero.height() - 4, 1983, 793);
		HgnDraw.rect(graphics, hero.x() + 2, hero.y() + 2,
			hero.x() + hero.width() / 2 + 2, hero.bottom() - 2, 0xD9081720);
		HgnTypography.fitted(graphics, font, "SINGLEPLAYER", hero.x() + 12,
			hero.y() + 12, hero.width() / 2 - 16, 1.25f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "Create, explore and manage your worlds.",
			hero.x() + 12, hero.y() + 34, hero.width() / 2 - 16,
			0.8f, HgnPalette.mutedText());
	}
}
