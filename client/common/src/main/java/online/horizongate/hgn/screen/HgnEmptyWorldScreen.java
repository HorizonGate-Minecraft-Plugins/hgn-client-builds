package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

/** Real zero-save HGN library; never creates a placeholder world to display the page. */
public final class HgnEmptyWorldScreen extends Screen {
	private static final String[] NAVIGATION_LABELS = {"PLAY", "SINGLEPLAYER", "MULTIPLAYER",
		"SERVERS", "COSMETICS", "MODS", "SETTINGS", "BACK"};
	private static final HgnIcon[] NAVIGATION_ICONS = {HgnIcon.PLAY, HgnIcon.WORLD,
		HgnIcon.MULTIPLAYER, HgnIcon.SERVERS, HgnIcon.COSMETICS, HgnIcon.MODS,
		HgnIcon.SETTINGS, HgnIcon.BACK};
	private final Screen parent;
	private HgnWorldSelectionLayout layout;

	public HgnEmptyWorldScreen(Screen parent) {
		super(Component.literal("HGN Worlds"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout = HgnWorldSelectionLayout.resolve(width, height, 3);
		clearWidgets();
		if (layout.sidebar()) {
			for (int index = 0; index < NAVIGATION_LABELS.length; index++) {
				if (index == 1) continue;
				int destination = index;
				HgnRect bounds = layout.navigation().get(index);
				addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
					bounds.width(), bounds.height(), Component.literal(NAVIGATION_LABELS[index]),
					NAVIGATION_ICONS[index], false, ignored -> openDestination(destination)));
			}
		}
		button(0, "CREATE NEW WORLD", () -> HgnCompat.openFreshWorld(minecraft, this));
		button(1, "REFRESH WORLDS", this::refresh);
		button(2, "BACK", this::onClose);
	}

	private void button(int index, String label, Runnable action) {
		HgnRect bounds = layout.actions().get(index);
		addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), index == 0,
			ignored -> action.run()));
	}

	private void refresh() {
		minecraft.setScreen(HgnSelectWorldScreen.open(parent));
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

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null) return;
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.header(), true);
		HgnSurface.panel(graphics, layout.listPanel(), false);
		HgnSurface.panel(graphics, layout.actionsPanel(), false);
		if (layout.sidebar()) HgnSurface.panel(graphics, layout.navigationPanel(), false);
		renderHeader(graphics);
		if (layout.sidebar()) {
			renderNavigation(graphics);
			renderHero(graphics);
		}
		renderLibrary(graphics);
		renderActions(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphics graphics) {
		HgnRect header = layout.header();
		HgnRect count = layout.search();
		HgnSurface.dashboardInset(graphics, count, false);
		HgnTypography.centered(graphics, font, "WORLDS  0", count.centerX(),
			count.y() + Math.max(4, (count.height() - font.lineHeight) / 2), HgnPalette.CYAN);
		if (layout.compact()) {
			HgnTypography.fittedHeading(graphics, font, "SINGLEPLAYER", header.x() + 12,
				header.y() + 12, count.x() - header.x() - 18, 1.05f, HgnPalette.TEXT);
			return;
		}
		HgnSurface.wordmark(graphics, header.x() + 12, header.y() + 8, 55, 38, false);
		HgnTypography.fittedHeading(graphics, font, "HORIZONGATE CLIENT", header.x() + 77,
			header.y() + 11, count.x() - header.x() - 89, 1.05f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "SINGLEPLAYER  /  YOUR WORLDS. YOUR STORIES.",
			header.x() + 77, header.y() + 34, count.x() - header.x() - 89,
			0.8f, HgnPalette.CYAN);
	}

	private void renderNavigation(GuiGraphics graphics) {
		HgnRect panel = layout.navigationPanel();
		HgnTypography.fitted(graphics, font, "HGN CLIENT", panel.x() + 9,
			panel.y() + 11, panel.width() - 18, 0.82f, HgnPalette.CYAN);
		HgnRect selected = layout.navigation().get(1);
		HgnSurface.dashboardInset(graphics, selected, true);
		HgnIcon.WORLD.draw(graphics, selected.x() + 7,
			selected.centerY() - 9, 18, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "SINGLEPLAYER", selected.x() + 33,
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
		HgnTypography.fittedHeading(graphics, font, "SINGLEPLAYER", hero.x() + 12,
			hero.y() + 12, hero.width() / 2 - 16, 1.25f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "Create, explore and manage your worlds.",
			hero.x() + 12, hero.y() + 34, hero.width() / 2 - 16,
			0.8f, HgnPalette.TEXT_MUTED);
	}

	private void renderLibrary(GuiGraphics graphics) {
		HgnRect library = layout.worldList();
		HgnSurface.dashboardInset(graphics, library, false);
		HgnTypography.fittedHeading(graphics, font, "NO SAVED WORLDS", library.x() + 12,
			library.y() + 10, library.width() - 24, 1.06f, HgnPalette.CYAN);
		if (library.height() >= 52) HgnTypography.fitted(graphics, font,
			"This Minecraft profile has no saved worlds.", library.x() + 12,
			library.y() + 28, library.width() - 24, 0.86f, HgnPalette.TEXT_MUTED);
		if (layout.showsSelectedWorld() && !layout.selectedWorldInHero()) {
			HgnRect details = layout.selectedWorld();
			HgnSurface.dashboardInset(graphics, details, false);
			HgnTypography.fittedHeading(graphics, font, "NO WORLD SELECTED",
				details.x() + 10, details.y() + 12, details.width() - 20,
				0.9f, HgnPalette.TEXT);
			if (details.height() >= 58) HgnTypography.fitted(graphics, font,
				"Create a world to begin.", details.x() + 10,
				details.y() + 32, details.width() - 20, 0.78f, HgnPalette.TEXT_MUTED);
		}
	}

	private void renderActions(GuiGraphics graphics) {
		HgnRect panel = layout.actionsPanel();
		HgnTypography.fittedHeading(graphics, font, "WORLD ACTIONS", panel.x() + 12,
			panel.y() + 14, panel.width() - 24, 1.05f, HgnPalette.CYAN);
		if (layout.showsPlayerFooter()) {
			HgnSurface.separator(graphics, panel.x() + 12, panel.bottom() - 45,
				panel.width() - 24, false);
			HgnTypography.fitted(graphics, font, "PLAYER", panel.x() + 12,
				panel.bottom() - 36, panel.width() - 24, 0.75f, HgnPalette.CYAN);
			HgnTypography.fitted(graphics, font, minecraft.getUser().getName(),
				panel.x() + 12, panel.bottom() - 22, panel.width() - 24,
				0.94f, HgnPalette.TEXT);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
