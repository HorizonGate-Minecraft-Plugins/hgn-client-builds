package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.HgnOfficialServers;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnHeroPreviewPolicy;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Gateway-first HGN main menu with real client data and native widget inputs. */
public final class HgnDashboardScreen extends Screen {
	private static final String[] DESTINATION_LABELS = {
		"PLAY", "SINGLEPLAYER", "MULTIPLAYER", "SERVERS",
		"COSMETICS", "MODS", "SETTINGS", "EXIT"
	};
	private static final String[] DESTINATION_DETAILS = {
		"Enter HorizonGate Network", "Play in your own world", "Join a saved server",
		"Browse featured servers", "Skins, capes and more", "Manage installed mods",
		"Client preferences", "Close the game"
	};
	private static final HgnIcon[] DESTINATION_ICONS = {
		HgnIcon.PLAY, HgnIcon.WORLD, HgnIcon.MULTIPLAYER, HgnIcon.SERVERS,
		HgnIcon.COSMETICS, HgnIcon.MODS, HgnIcon.SETTINGS, HgnIcon.EXIT
	};
	private static final String[] COMPACT_LABELS = {
		"Singleplayer", "Multiplayer", "Servers", "Mods",
		"Cosmetics", "Settings", "Account", "Exit"
	};
	private static final String[] COMPACT_DETAILS = {
		"Your worlds", "Saved servers", "Official network", "Installed mods",
		"Your equipped look", "HGN and Minecraft options", "Open launcher account", "Close Minecraft"
	};
	private static final HgnIcon[] COMPACT_ICONS = {
		HgnIcon.WORLD, HgnIcon.MULTIPLAYER, HgnIcon.SERVERS, HgnIcon.MODS,
		HgnIcon.COSMETICS, HgnIcon.SETTINGS, HgnIcon.PROFILE, HgnIcon.EXIT
	};
	private HgnDashboardLayout layout;
	private HgnReferenceShellLayout shell;
	private online.horizongate.hgn.ui.HgnCompactDashboardLayout compactLayout;
	private HgnDashboardData.Snapshot snapshot = HgnDashboardData.Snapshot.empty();
	private boolean dataReady;
	private boolean dataLoading;
	private final HgnHeroPreviewPolicy heroPreviewPolicy = new HgnHeroPreviewPolicy();
	private AbstractWidget heroPreviewWidget;
	private HgnRect rail, content, profile, welcome, accountCard, preview, credits, quickStatus;
	private boolean videoRuntimeSmokeOpened;
	private final HgnRect[] worlds = new HgnRect[3];
	private final HgnRect[] servers = new HgnRect[3];
	private final HgnButton[] compactButtons = new HgnButton[8];
	private final HgnButton[] destinationButtons = new HgnButton[HgnReferenceShellLayout.DESTINATIONS];
	private final HgnButton[] worldButtons = new HgnButton[3];
	private final HgnButton[] serverButtons = new HgnButton[3];
	private HgnButton browseWorldsButton;
	private HgnButton browseServersButton;
	private HgnButton accountButton;
	private HgnButton profileButton;

	public HgnDashboardScreen() {
		super(Component.literal("HGN Client"));
	}

	@Override
	protected void init() {
		HgnClient.ensureStarted();
		layout = HgnDashboardLayout.resolve(width, height);
		shell = new HgnReferenceShellLayout(layout);
		compactLayout = online.horizongate.hgn.ui.HgnCompactDashboardLayout.needed(width, height,
			minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight())
			? online.horizongate.hgn.ui.HgnCompactDashboardLayout.resolve(width, height) : null;
		rebuildDashboardWidgets();
		if (compactLayout == null) loadDashboardData();
	}

	private void loadDashboardData() {
		if (dataReady || dataLoading) return;
		dataLoading = true;
		HgnDashboardData.load(minecraft).whenComplete((loaded, failure) -> minecraft.execute(() -> {
			dataLoading = false;
			snapshot = failure == null && loaded != null
				? loaded : HgnDashboardData.Snapshot.empty();
			dataReady = minecraft.screen == this;
			if (minecraft.screen == this) rebuildDashboardWidgets();
		}));
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft.screen == this) refreshHeroPreview();
		if (!videoRuntimeSmokeOpened && HgnClient.videoRuntimeSmoke() && minecraft.screen == this) {
			videoRuntimeSmokeOpened = true;
			minecraft.setScreen(new OptionsScreen(this, minecraft.options));
		}
	}

	private void rebuildDashboardWidgets() {
		clearWidgets();
		heroPreviewWidget = null;
		if (compactLayout != null) {
			compactCommand(0, () -> minecraft.setScreen(HgnSelectWorldScreen.open(this)));
			compactCommand(1, () -> minecraft.setScreen(new HgnSavedServersScreen(this)));
			compactCommand(2, () -> minecraft.setScreen(new HgnMultiplayerScreen(this)));
			compactCommand(3, () -> minecraft.setScreen(new HgnInstalledModsScreen(this)));
			compactCommand(4, () -> minecraft.setScreen(new HgnCosmeticsScreen(this)));
			compactCommand(5, () -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
			compactCommand(6, () -> HgnCompat.openUrl("hgn://account?playerId=" + minecraft.getUser().getProfileId()));
			compactCommand(7, () -> HgnCompat.quit(minecraft));
			return;
		}
		rail = shell.rail();
		content = layout.rect(437, 351, 227, 96);
		profile = shell.profileColumn();
		welcome = shell.featuredNetwork();
		accountCard = shell.profileIdentity();
		preview = shell.profilePreview();
		credits = shell.profileWallet();
		quickStatus = shell.quickStatus();
		for (int index = 0; index < 3; index++) {
			worlds[index] = layout.rect(443, 372 + index * 23, 104, 20);
			servers[index] = layout.rect(552, 372 + index * 23, 105, 20);
			worldButtons[index] = null;
			serverButtons[index] = null;
		}
		command(0, this::joinOfficialHub);
		command(1,
			() -> minecraft.setScreen(HgnSelectWorldScreen.open(this)));
		command(2,
			() -> minecraft.setScreen(new HgnSavedServersScreen(this)));
		command(3,
			() -> minecraft.setScreen(new HgnMultiplayerScreen(this)));
		command(4,
			() -> minecraft.setScreen(new HgnCosmeticsScreen(this)));
		command(5,
			() -> minecraft.setScreen(new HgnInstalledModsScreen(this)));
		command(6,
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		command(7, () -> HgnCompat.quit(minecraft));
		addRenderableWidget(HgnButton.dashboardCentered(
			shell.featuredAction().x(), shell.featuredAction().y(),
			shell.featuredAction().width(), shell.featuredAction().height(),
			Component.literal("JOIN HORIZONGATE NETWORK"), true,
			ignored -> joinOfficialHub())
			.referenceScale(layout.scale()));
		for (int index = 0; index < Math.min(3, snapshot.worlds().size()); index++) {
			HgnDashboardData.RecentWorld world = snapshot.worlds().get(index);
			worldButtons[index] = target(worlds[index], "Open " + world.name(),
				() -> minecraft.createWorldOpenFlows().openWorld(world.id(),
					() -> minecraft.setScreen(this)));
		}
		browseWorldsButton = null;
		browseServersButton = null;
		if (snapshot.worlds().isEmpty()) {
			browseWorldsButton = target(worlds[0], "Open singleplayer worlds",
				() -> minecraft.setScreen(HgnSelectWorldScreen.open(this)));
		}
		for (int index = 0; index < 3; index++) {
			if (index < snapshot.servers().size()) {
				HgnDashboardData.SavedServer server = snapshot.servers().get(index);
				serverButtons[index] = target(servers[index], "Join " + server.name(),
					() -> HgnCompat.connect(this, minecraft, server.name(), server.address()));
			} else if (index == 0) {
				browseServersButton = target(servers[index], "Browse servers",
					() -> minecraft.setScreen(new HgnMultiplayerScreen(this)));
			}
		}
		accountButton = target(accountCard, "Open HGN Launcher account",
			() -> HgnCompat.openUrl("hgn://account?playerId=" + minecraft.getUser().getProfileId()));
		profileButton = target(shell.profileAction(), "View HGN Launcher profile",
			() -> HgnCompat.openUrl("hgn://account?playerId=" + minecraft.getUser().getProfileId()));
		HgnRect model = layout.profilePreviewModel();
		addRenderableWidget(HgnCompat.profileWidget(minecraft,
			model.x(), model.y(), model.width(), model.height()));
	}

	private void refreshHeroPreview() {
		boolean lowQuality = HgnConfig.get().sceneQuality == HgnConfig.SceneQuality.LOW;
		boolean visible = heroPreviewPolicy.shouldRender(compactLayout == null,
			lowQuality, minecraft.getFps());
		if (!visible) {
			if (heroPreviewWidget != null) heroPreviewWidget.visible = false;
			return;
		}
		if (heroPreviewWidget == null) {
			HgnRect bounds = layout.gatewayForegroundModel();
			heroPreviewWidget = addRenderableWidget(HgnCompat.heroWidget(minecraft,
				bounds.x(), bounds.y(), bounds.width(), bounds.height()));
		}
		heroPreviewWidget.visible = true;
	}

	private void joinOfficialHub() {
		var hub = HgnOfficialServers.HUB;
		if (hub.playableNow()) HgnCompat.connect(this, minecraft, hub.name(), hub.address());
		else HgnCompat.openUrl("hgn://play/" + hub.id() + "/" + hub.requiredVersion());
	}

	private void compactCommand(int index, Runnable action) {
		HgnRect bounds = compactLayout.button(index);
		compactButtons[index] = target(bounds, COMPACT_LABELS[index], action);
	}

	private void command(int index, Runnable action) {
		HgnRect bounds = shell.destination(index);
		destinationButtons[index] = target(bounds,
			DESTINATION_LABELS[index] + ": " + DESTINATION_DETAILS[index], action);
	}

	private HgnButton target(HgnRect bounds, String label, Runnable action) {
		return addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(), bounds.width(),
			bounds.height(), Component.literal(label), button -> action.run()));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (compactLayout != null) {
			renderCompact(graphics);
			super.render(graphics, mouseX, mouseY, partialTick);
			return;
		}
		HgnSurface.dashboardPanel(graphics, rail, false);
		HgnSurface.dashboardPanel(graphics, content, false);
		HgnSurface.dashboardPanel(graphics, profile, false);
		HgnSurface.dashboardPanel(graphics, quickStatus, false);
		renderHeader(graphics);
		renderNavigation(graphics);
		renderWelcome(graphics);
		renderActivity(graphics);
		renderProfile(graphics);
		renderStatus(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		HgnRect previewLabel = layout.profilePreviewLabel();
		HgnTypography.centered(graphics, font, "DRAG TO ROTATE", previewLabel.centerX(),
			previewLabel.y() + layout.size(2), layout.scale() * 0.7f, HgnPalette.TEXT_MUTED);
	}

	private void renderNavigation(GuiGraphics graphics) {
		for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
			HgnButton button = destinationButtons[index];
			HgnRect bounds = shell.destination(index);
			boolean highlighted = index == 0 || hot(button);
			HgnSurface.control(graphics, bounds, hot(button) ? 1.0f : 0.0f,
				button != null && button.isFocused(), index == 0, false, false);
			HgnRect iconBounds = shell.destinationIcon(index);
			DESTINATION_ICONS[index].draw(graphics, iconBounds.x(), iconBounds.y(),
				Math.min(iconBounds.width(), iconBounds.height()), HgnPalette.CYAN_SOFT);
			HgnRect title = shell.destinationTitle(index);
			HgnTypography.fitted(graphics, font, DESTINATION_LABELS[index],
				title.x(), title.y(), title.width(), layout.scale() * 0.96f, HgnPalette.TEXT);
			HgnRect detail = shell.destinationDetail(index);
			HgnTypography.fitted(graphics, font, DESTINATION_DETAILS[index],
				detail.x(), detail.y(), detail.width(), layout.scale() * 0.72f,
				highlighted ? HgnPalette.CYAN_SOFT : HgnPalette.TEXT_MUTED);
		}
	}

	private void renderHeader(GuiGraphics graphics) {
		HgnRect masthead = layout.rect(20, 12, 384, 69);
		HgnDraw.gradient(graphics, masthead.x(), masthead.y(), masthead.right(), masthead.bottom(),
			0xA4071119, 0x20071119);
		HgnRect mark = shell.wordmark();
		HgnSurface.wordmark(graphics, mark.x(), mark.y(), mark.width(), mark.height(), false);
		HgnTypography.fittedHeading(graphics, font, "HORIZONGATE",
			layout.x(108), layout.y(21), layout.size(284), layout.scale() * 2.28f,
			HgnPalette.TEXT);
		HgnTypography.heading(graphics, font, "C L I E N T",
			layout.x(108), layout.y(48), layout.scale() * 1.28f, HgnPalette.CYAN);
		fitted(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			108, 67, 260, 0.79f, HgnPalette.TEXT);
		HgnRect badge = shell.versionBadge();
		HgnSurface.dashboardInset(graphics, badge, false);
		HgnTypography.centered(graphics, font, "HGN " + HgnClient.minecraftVersion(),
			badge.centerX(), badge.y() + Math.max(4, (badge.height() - font.lineHeight) / 2),
			Math.max(0.75f, layout.scale() * 0.86f), HgnPalette.CYAN);
	}

	private void renderCompact(GuiGraphics graphics) {
		HgnRect panel = compactLayout.panel();
		int x = panel.x();
		int y = panel.y();
		int right = panel.right();
		int bottom = panel.bottom();
		HgnDraw.gradient(graphics, x + 6, y, right - 6, bottom,
			0xEE0B1922, 0xF2071119);
		HgnDraw.gradient(graphics, x, y + 6, x + 6, bottom - 6,
			0xEE0B1922, 0xF2071119);
		HgnDraw.gradient(graphics, right - 6, y + 6, right, bottom - 6,
			0xEE0B1922, 0xF2071119);
		HgnDraw.rect(graphics, x + 6, y, right - 6, y + 1, HgnPalette.EDGE_STRONG);
		HgnDraw.rect(graphics, x + 6, bottom - 1, right - 6, bottom, HgnPalette.EDGE_STRONG);
		HgnDraw.rect(graphics, x, y + 6, x + 1, bottom - 6, HgnPalette.EDGE_STRONG);
		HgnDraw.rect(graphics, right - 1, y + 6, right, bottom - 6, HgnPalette.EDGE_STRONG);
		HgnSurface.cornerAccent(graphics, panel, true);

		HgnRect header = compactLayout.header();
		HgnSurface.wordmark(graphics, header.x() + 2, header.y() + 2, 38, 20, false);
		HgnDraw.rect(graphics, header.x() + 48, header.y() + 2,
			header.x() + 49, header.y() + 23, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "HorizonGate Network",
			header.x() + 57, header.y() + 1, header.width() - 59, 1.05f, HgnPalette.CYAN);
		HgnTypography.text(graphics, font, "PLAY BEYOND",
			header.x() + 57, header.y() + 16, 0.72f, HgnPalette.mutedText());
		HgnSurface.separator(graphics, header.x(), header.bottom() + 1, header.width(), false);

		for (int index = 0; index < compactButtons.length; index++) {
			HgnRect card = compactLayout.button(index);
			boolean highlighted = index < 2 || hot(compactButtons[index]);
			HgnSurface.dashboardInset(graphics, card, highlighted);
			if (index < 2) HgnDraw.rect(graphics, card.x() + 1, card.y() + 5,
				card.x() + 2, card.bottom() - 5, HgnPalette.CYAN);
			int iconSize = Math.min(18, card.height() - 8);
			COMPACT_ICONS[index].draw(graphics, card.x() + 7,
				card.centerY() - iconSize / 2, iconSize, HgnPalette.CYAN_SOFT);
			int textX = card.x() + 31;
			int textWidth = card.width() - 38;
			int titleY = card.y() + Math.max(3, (card.height() - 19) / 2);
			HgnTypography.fitted(graphics, font, COMPACT_LABELS[index],
				textX, titleY, textWidth, 0.98f, HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font, COMPACT_DETAILS[index],
				textX, titleY + 11, textWidth, 0.72f, HgnPalette.mutedText());
		}
		HgnRect footer = compactLayout.footer();
		HgnTypography.text(graphics, font, "HGN CLIENT", footer.x(), footer.y(),
			0.72f, HgnPalette.mutedText());
		HgnTypography.text(graphics, font, "PLAY BEYOND", footer.right() - 73,
			footer.y(), 0.72f, HgnPalette.CYAN);
	}

	private void renderWelcome(GuiGraphics graphics) {
		HgnSurface.dashboardInset(graphics, welcome, true);
		HgnRect scenery = layout.rect(217, 352, 212, 60);
		HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
			scenery.x(), scenery.y(), scenery.width(), scenery.height(),
			0, 100, 1672, 377, 1672, 941);
		HgnDraw.gradient(graphics, scenery.x(), scenery.y(), scenery.right(), scenery.bottom(),
			0xB007141C, 0xE007141C);
		HgnSurface.cornerAccent(graphics, welcome, true);
		text(graphics, "FEATURED SERVER", 224, 358, 0.72f, HgnPalette.CYAN);
		HgnTypography.fittedHeading(graphics, font, "HORIZONGATE NETWORK",
			layout.x(224), layout.y(377), layout.size(195), layout.scale() * 1.05f,
			HgnPalette.TEXT);
		fitted(graphics, "Explore, build and belong.", 224, 395, 195, 0.75f,
			HgnPalette.TEXT_MUTED);
	}

	private void renderActivity(GuiGraphics graphics) {
		text(graphics, "RECENT WORLDS", 445, 358, 0.72f, HgnPalette.CYAN);
		text(graphics, "SAVED SERVERS", 554, 358, 0.72f, HgnPalette.CYAN);
		for (int index = 0; index < 3; index++) {
			if (index >= snapshot.worlds().size() && index > 0) continue;
			HgnSurface.dashboardInset(graphics, worlds[index],
				hot(index == 0 && snapshot.worlds().isEmpty() ? browseWorldsButton : worldButtons[index]));
			int y = 372 + index * 23;
			if (index < snapshot.worlds().size()) {
				HgnDashboardData.RecentWorld world = snapshot.worlds().get(index);
				icon(graphics, HgnIcon.WORLD, 448, y + 3, 13);
				fitted(graphics, world.name(), 464, y + 6, 79, 0.76f, HgnPalette.TEXT);
			} else {
				fitted(graphics, dataReady ? "No recent worlds" : "Loading worlds…",
					448, y + 6, 95, 0.74f, HgnPalette.TEXT_MUTED);
			}
		}
		for (int index = 0; index < 3; index++) {
			if (index >= snapshot.servers().size() && index > 0) continue;
			HgnSurface.dashboardInset(graphics, servers[index],
				hot(index == 0 && snapshot.servers().isEmpty() ? browseServersButton : serverButtons[index]));
			int y = 372 + index * 23;
			if (index < snapshot.servers().size()) {
				HgnDashboardData.SavedServer server = snapshot.servers().get(index);
				icon(graphics, HgnIcon.SERVERS, 557, y + 3, 13);
				fitted(graphics, server.name(), 573, y + 6, 79, 0.76f, HgnPalette.TEXT);
			} else {
				fitted(graphics, dataReady ? "Browse servers" : "Loading servers…",
					557, y + 6, 94, 0.74f, HgnPalette.TEXT_MUTED);
			}
		}
	}

	private void renderProfile(GuiGraphics graphics) {
		HgnSurface.dashboardInset(graphics, accountCard, hot(accountButton));
		HgnSurface.dashboardInset(graphics, preview, false);
		HgnRect action = shell.profileAction();
		HgnSurface.control(graphics, action, hot(profileButton) ? 1.0f : 0.0f,
			profileButton != null && profileButton.isFocused(), false, false, false);
		HgnSurface.dashboardInset(graphics, credits, false);
		texture(graphics, "profile_platform.png", 710, 198, 96, 30, 256, 96);
		HgnCosmetics.Profile account = HgnMessages.cosmetics().profile();
		HgnCosmetics.ConnectionState connection = HgnCosmetics.connectionState();
		String name = minecraft.getUser().getName();
		icon(graphics, HgnIcon.PROFILE, 701, 56, 20);
		fitted(graphics, name, 725, 56, 72, 0.94f, HgnPalette.TEXT);
		fitted(graphics, connection.compactLabel(), 725, 70, 76, 0.72f,
			HgnPalette.TEXT_MUTED);
		icon(graphics, HgnIcon.LINK, 803, 59, 13);
		icon(graphics, HgnIcon.PROFILE, 702, 239, 13);
		fitted(graphics, "VIEW PROFILE", 720, 241, 82, 0.81f, HgnPalette.TEXT);
		text(graphics, ">", 808, 240, 1.0f, HgnPalette.CYAN_SOFT);
		icon(graphics, HgnIcon.HGN_GOLDEN_TOKENS, 702, 270, 14);
		text(graphics, account.legacyCurrency() ? "ACCOUNT BALANCES" : "ACCOUNT TOKENS",
			720, 273, 0.76f, HgnPalette.CYAN);
		if (account.launcherLinked()) {
			if (account.legacyCurrency()) {
				fitted(graphics, account.legacyAccountCredits(), 702, 290, 113, 0.95f, HgnPalette.TEXT);
				text(graphics, "Account Credits", 702, 303, 0.68f, HgnPalette.TEXT_MUTED);
				fitted(graphics, account.legacyCosmeticCoins() + " Cosmetic Coins",
					702, 320, 113, 0.69f, HgnPalette.TEXT_MUTED);
			} else {
				fitted(graphics, account.hgnGoldenTokens(), 702, 290, 113, 0.95f, HgnPalette.TEXT);
				text(graphics, "HGN Golden Tokens", 702, 303, 0.68f, HgnPalette.TEXT_MUTED);
				fitted(graphics, account.hgnTokens() + " HGN Tokens", 702, 320, 113,
					0.69f, HgnPalette.TEXT_MUTED);
			}
		} else {
			fitted(graphics, connection.compactLabel(), 702, 292, 112, 0.86f, HgnPalette.TEXT);
			fitted(graphics, connection.dashboardDescription(), 702, 312, 112,
				0.72f, HgnPalette.TEXT_MUTED);
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		text(graphics, "QUICK STATUS", 679, 358, 0.76f, HgnPalette.CYAN);
		HgnSurface.separator(graphics, layout.x(679), layout.y(373), layout.size(140), false);
		renderStatusCell(graphics, 0, HgnIcon.GATEWAY, "MINECRAFT",
			HgnClient.minecraftVersion(), HgnPalette.TEXT);
		renderStatusCell(graphics, 1, HgnIcon.PERFORMANCE, "FRAMERATE",
			minecraft.getFps() > 0 ? minecraft.getFps() + " FPS" : "MEASURING",
			HgnPalette.TEXT);
		renderStatusCell(graphics, 2, HgnIcon.SERVERS, "SAVED SERVERS",
			dataReady ? Integer.toString(snapshot.servers().size()) : "...", HgnPalette.TEXT);
		renderStatusCell(graphics, 3, HgnIcon.PROFILE, "HGN ACCOUNT",
			HgnCosmetics.connectionState().compactLabel(), HgnPalette.CYAN_SOFT);
	}

	private void renderStatusCell(GuiGraphics graphics, int index, HgnIcon icon,
		String label, String value, int valueColor) {
		HgnRect cell = shell.quickStatusCell(index);
		HgnSurface.dashboardInset(graphics, cell, false);
		icon.draw(graphics, cell.x() + layout.size(5), cell.y() + layout.size(7),
			layout.size(14), HgnPalette.CYAN_SOFT);
		int valueX = cell.x() + layout.size(23);
		int textWidth = Math.max(1, cell.right() - valueX - layout.size(3));
		HgnTypography.fitted(graphics, font, value, valueX, cell.y() + layout.size(4),
			textWidth, layout.scale() * 0.89f, valueColor);
		HgnTypography.fitted(graphics, font, label, valueX, cell.y() + layout.size(17),
			textWidth, layout.scale() * 0.58f, HgnPalette.TEXT_MUTED);
	}

	private void text(GuiGraphics graphics, String text, int x, int y, float emphasis, int color) {
		HgnTypography.text(graphics, font, text, layout.x(x), layout.y(y), layout.scale() * emphasis, color);
	}

	private void centered(GuiGraphics graphics, String text, int x, int y, float emphasis, int color) {
		HgnTypography.centered(graphics, font, text, layout.x(x), layout.y(y), layout.scale() * emphasis, color);
	}

	private void fitted(GuiGraphics graphics, String text, int x, int y, int width, float emphasis, int color) {
		String visible = HgnTypography.trim(font, text, Math.round(width / emphasis));
		text(graphics, visible, x, y, emphasis, color);
	}

	private void icon(GuiGraphics graphics, HgnIcon icon, int x, int y, int size) {
		icon.draw(graphics, layout.x(x), layout.y(y), layout.size(size), HgnPalette.CYAN_SOFT);
	}

	private void texture(GuiGraphics graphics, String path, int x, int y, int width, int height,
		int sourceWidth, int sourceHeight) {
		HgnDraw.textureStretch(graphics, "textures/gui/" + path, layout.x(x), layout.y(y),
			layout.size(width), layout.size(height), sourceWidth, sourceHeight);
	}

	private void dashed(GuiGraphics graphics, int x, int y, int width, int height) {
		int color = 0xA8788790;
		for (int offset = 0; offset < width; offset += 4) {
			HgnDraw.rect(graphics, layout.x(x + offset), layout.y(y),
				layout.x(x + Math.min(offset + 2, width)), layout.y(y) + 1, color);
			HgnDraw.rect(graphics, layout.x(x + offset), layout.y(y + height),
				layout.x(x + Math.min(offset + 2, width)), layout.y(y + height) + 1, color);
		}
		for (int offset = 0; offset < height; offset += 4) {
			HgnDraw.rect(graphics, layout.x(x), layout.y(y + offset), layout.x(x) + 1,
				layout.y(y + Math.min(offset + 2, height)), color);
			HgnDraw.rect(graphics, layout.x(x + width), layout.y(y + offset), layout.x(x + width) + 1,
				layout.y(y + Math.min(offset + 2, height)), color);
		}
	}

	private static boolean hot(AbstractWidget widget) {
		return widget != null && widget.isHoveredOrFocused();
	}

	@Override
	public void removed() {
		dataReady = false;
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
	}
}
