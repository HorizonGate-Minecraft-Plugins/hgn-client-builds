package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnOfficialServers;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnMultiplayerLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnServerPopulation;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Network discovery, individual server cards and a separate connection inspector. */
public final class HgnMultiplayerScreen extends Screen {
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
	private final Screen parent;
	private boolean networkOpen = true;
	private boolean compact;
	private int selected;
	private String searchQuery = "";
	private EditBox searchBox;
	private HgnDashboardLayout layout;
	private HgnReferenceShellLayout shell;
	private final HgnButton[] destinationButtons = new HgnButton[HgnReferenceShellLayout.DESTINATIONS];
	private final List<ServerData> statuses = new ArrayList<>();
	private ServerStatusPinger pinger;
	private ExecutorService executor;
	private long refreshStarted;
	private volatile long pingGeneration;
	private HgnButton joinButton;
	private final online.horizongate.hgn.ui.HgnDoubleClick serverClicks = new online.horizongate.hgn.ui.HgnDoubleClick();

	public HgnMultiplayerScreen(Screen parent) {
		super(Component.literal("HGN Multiplayer"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout = HgnDashboardLayout.resolve(width, height);
		shell = new HgnReferenceShellLayout(layout);
		compact = HgnMultiplayerLayout.compact(width, height);
		if (pinger == null) refreshStatus();
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		if (compact) {
			rebuildCompact();
			return;
		}
		rebuildDestinations();
		wideAction(0, "OFFICIAL", networkOpen,
			() -> { serverClicks.reset(); networkOpen = true; rebuild(); });
		wideAction(1, "OVERVIEW", !networkOpen,
			() -> { serverClicks.reset(); networkOpen = false; rebuild(); });
		wideAction(2, "SAVED", false, this::openSaved);
		wideAction(3, "LAN", false, this::openLan);
		wideAction(4, "DIRECT", false, this::directConnect);
		wideAction(5, "REFRESH", false, () -> {
			if (System.nanoTime() - refreshStarted > 2_000_000_000L) refreshStatus();
		});
		wideAction(6, "MENU", false, this::onClose);
		if (!networkOpen) {
			HgnRect hero = layout.rect(207, 108, 595, 195);
			addRenderableWidget(HgnButton.hitTarget(hero.x(), hero.y(), hero.width(), hero.height(),
				Component.literal("Browse all HorizonGate Network servers"), ignored -> {
					serverClicks.reset(); networkOpen = true; rebuild();
				}));
			button(232, 252, 226, 29, "Explore servers", HgnIcon.PLAY, true,
				() -> { serverClicks.reset(); networkOpen = true; rebuild(); });
			button(227, 365, 233, 29, "Open saved servers", HgnIcon.WORLD, false, this::openSaved);
			joinButton = null;
		} else {
			searchBox = addSearch(layout.rect(HgnMultiplayerLayout.search()));
			List<Integer> matches = matchedServers();
			for (int position = 0; position < matches.size(); position++) {
				int choice = matches.get(position);
				HgnRect card = layout.rect(HgnMultiplayerLayout.serverCard(position));
				addRenderableWidget(HgnButton.hitTarget(card.x(), card.y(), card.width(), card.height(),
					Component.literal("Select " + HgnOfficialServers.ALL.get(choice).name()), ignored -> {
						selectServer(choice);
					}));
			}
			HgnRect join = HgnMultiplayerLayout.join();
			joinButton = button(join.x(), join.y(), join.width(), join.height(),
				joinLabel(), HgnIcon.PLAY, true, this::join);
			joinButton.active = !matches.isEmpty();
		}
	}

	private void rebuildDestinations() {
		Runnable[] actions = {
			this::joinOfficialHub,
			() -> minecraft.setScreen(HgnSelectWorldScreen.open(this)),
			this::openSaved,
			() -> { serverClicks.reset(); networkOpen = true; rebuild(); },
			() -> minecraft.setScreen(new HgnCosmeticsScreen(this)),
			() -> minecraft.setScreen(new HgnInstalledModsScreen(this)),
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)),
			() -> HgnCompat.quit(minecraft)
		};
		for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
			int destination = index;
			HgnRect bounds = shell.destination(index);
			destinationButtons[index] = addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(),
				Component.literal(DESTINATION_LABELS[index] + ": " + DESTINATION_DETAILS[index]),
				ignored -> actions[destination].run()));
		}
	}

	private HgnButton wideAction(int index, String label, boolean selected, Runnable action) {
		HgnRect bounds = layout.rect(HgnMultiplayerLayout.wideAction(index));
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), selected,
			ignored -> action.run()).referenceScale(HgnMultiplayerLayout.buttonTextScale(layout.scale())));
	}

	private void rebuildCompact() {
		compactButton(HgnMultiplayerLayout.compactRefresh(width), "Refresh", false, () -> {
			if (System.nanoTime() - refreshStarted > 2_000_000_000L) refreshStatus();
		});
		for (int index = 0; index < HgnMultiplayerLayout.COMPACT_NAVIGATION_COUNT; index++) {
			int choice = index;
			compactButton(HgnMultiplayerLayout.compactNavigation(width, index),
				HgnMultiplayerLayout.compactNavigationLabel(index),
				HgnMultiplayerLayout.compactNavigationSelected(index, networkOpen), () -> {
					switch (choice) {
						case 0, 1 -> {
							serverClicks.reset();
							networkOpen = HgnMultiplayerLayout.compactShowsOfficialServers(choice);
							rebuild();
						}
						case 2 -> openSaved();
						case 3 -> openLan();
						default -> onClose();
					}
				});
		}
		if (!networkOpen) {
			compactButton(HgnMultiplayerLayout.compactExplore(width, height),
				"Explore official servers", true, () -> {
					serverClicks.reset(); networkOpen = true; rebuild();
				});
			joinButton = null;
			return;
		}
		searchBox = addSearch(HgnMultiplayerLayout.compactSearch(width));
		List<Integer> matches = matchedServers();
		for (int position = 0; position < matches.size(); position++) {
			int choice = matches.get(position);
			HgnRect card = HgnMultiplayerLayout.compactServerCard(width, height, position);
			addRenderableWidget(HgnButton.hitTarget(card.x(), card.y(), card.width(), card.height(),
				Component.literal("Select " + HgnOfficialServers.ALL.get(choice).name()),
				ignored -> selectServer(choice)));
		}
		joinButton = compactButton(HgnMultiplayerLayout.compactJoin(width, height),
			joinLabel(), true, this::join);
		joinButton.active = !matches.isEmpty();
	}

	private EditBox addSearch(HgnRect bounds) {
		EditBox box = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			Component.literal("Search official servers"));
		box.setMaxLength(100);
		box.setValue(searchQuery);
		box.setResponder(value -> {
			searchQuery = value;
			List<Integer> matches = matchedServers();
			if (!matches.isEmpty() && !matches.contains(selected)) selected = matches.get(0);
			serverClicks.reset();
			rebuild();
			if (searchBox != null) setInitialFocus(searchBox);
		});
		return addRenderableWidget(box);
	}

	private List<Integer> matchedServers() {
		List<Integer> matches = new ArrayList<>();
		for (int index = 0; index < HgnOfficialServers.ALL.size(); index++) {
			var server = HgnOfficialServers.ALL.get(index);
			if (HgnMultiplayerLayout.matches(searchQuery, server.name(), server.id(),
				server.address(), server.blurb())) matches.add(index);
		}
		return matches;
	}

	private HgnButton compactButton(HgnRect bounds, String label, boolean primary, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), primary,
			ignored -> action.run()).referenceScale(1.0f));
	}

	private void openSaved() { minecraft.setScreen(new HgnSavedServersScreen(this)); }

	private void openLan() { minecraft.setScreen(new HgnSavedServersScreen(this, true)); }

	private void directConnect() {
		minecraft.setScreen(new HgnServerEditorScreen(this, null, null, true));
	}

	private void selectServer(int choice) {
		boolean doubleClick = serverClicks.activate(choice, System.nanoTime());
		selected = choice;
		if (doubleClick) join();
	}

	private String joinLabel() {
		if (!matchedServers().contains(selected)) return "Select a server";
		return HgnOfficialServers.ALL.get(selected).playableNow() ? "Join server" : "Switch in launcher";
	}

	private void join() {
		if (!matchedServers().contains(selected)) return;
		var server = HgnOfficialServers.ALL.get(selected);
		if (server.playableNow()) HgnCompat.connect(this, minecraft, server.name(), server.address());
		else HgnCompat.openUrl("hgn://play/" + server.id() + "/" + server.requiredVersion());
	}

	private void joinOfficialHub() {
		var hub = HgnOfficialServers.HUB;
		if (hub.playableNow()) HgnCompat.connect(this, minecraft, hub.name(), hub.address());
		else HgnCompat.openUrl("hgn://play/" + hub.id() + "/" + hub.requiredVersion());
	}

	private void refreshStatus() {
		serverClicks.reset();
		stopPings();
		statuses.clear();
		pinger = new ServerStatusPinger();
		ServerStatusPinger activePinger = pinger;
		long generation = pingGeneration;
		executor = Executors.newFixedThreadPool(2, task -> {
			Thread thread = new Thread(task, "HGN server status");
			thread.setDaemon(true);
			return thread;
		});
		refreshStarted = System.nanoTime();
		for (var server : HgnOfficialServers.ALL) {
			ServerData data = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
			data.ping = -2L;
			statuses.add(data);
			executor.execute(() -> {
				try {
					if (generation == pingGeneration) HgnCompat.pingServer(activePinger, data, () -> {}, () -> {});
				} finally {
					if (generation != pingGeneration) activePinger.removeAll();
				}
			});
		}
	}

	@Override
	public void tick() {
		if (pinger != null) pinger.tick();
		if (joinButton != null) joinButton.setMessage(Component.literal(joinLabel()));
	}

	private void stopPings() {
		pingGeneration++;
		if (executor != null) { executor.shutdownNow(); executor = null; }
		if (pinger != null) { pinger.removeAll(); pinger = null; }
	}

	@Override
	public void removed() { serverClicks.reset(); stopPings(); }

	@Override
	public void onClose() { minecraft.setScreen(parent); }

	private HgnButton button(int x, int y, int w, int h, String label, HgnIcon icon,
		boolean primary, Runnable action) {
		HgnRect bounds = layout.rect(x, y, w, h);
		return addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), icon, primary,
			ignored -> action.run()).referenceScale(
				HgnMultiplayerLayout.buttonTextScale(layout.scale()),
				HgnMultiplayerLayout.buttonIconScale(layout.scale())));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (compact) {
			renderCompact(graphics, mouseX, mouseY);
			super.render(graphics, mouseX, mouseY, partialTick);
			renderSearchPrompt(graphics);
			return;
		}
		renderReferenceHeader(graphics);
		renderReferenceNavigation(graphics);
		if (networkOpen) renderServers(graphics, mouseX, mouseY);
		else renderDiscovery(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderSearchPrompt(graphics);
	}

	private void renderReferenceHeader(GuiGraphics graphics) {
		HgnRect masthead = layout.rect(20, 12, 384, 69);
		HgnDraw.gradient(graphics, masthead.x(), masthead.y(), masthead.right(),
			masthead.bottom(), 0xC4071119, 0xA0071119);
		HgnRect mark = shell.wordmark();
		HgnSurface.wordmark(graphics, mark.x(), mark.y(), mark.width(), mark.height(), false);
		referenceHeading(graphics, "HORIZONGATE", 108, 27, 284, 1.62f, HgnPalette.TEXT);
		referenceHeading(graphics, "C L I E N T", 108, 47, 284, 1.16f, HgnPalette.CYAN);
		referenceFit(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			108, 66, 260, 0.76f, HgnPalette.TEXT);
		HgnRect badge = shell.versionBadge();
		HgnSurface.dashboardInset(graphics, badge, false);
		HgnTypography.centered(graphics, font, "HGN " + HgnClient.minecraftVersion(),
			badge.centerX(), badge.y() + Math.max(4, (badge.height() - font.lineHeight) / 2),
			Math.max(0.75f, layout.scale() * 0.86f), HgnPalette.CYAN);
		referenceFit(graphics, networkOpen ? "SERVERS" : "HORIZONGATE NETWORK",
			422, 37, 273, 1.38f, HgnPalette.TEXT);
		referenceFit(graphics, networkOpen ? "Official destinations" : "Explore the network",
			422, 61, 273, 0.81f, HgnPalette.CYAN_SOFT);
	}

	private void renderReferenceNavigation(GuiGraphics graphics) {
		HgnSurface.dashboardPanel(graphics, shell.rail(), false);
		for (int index = 0; index < HgnReferenceShellLayout.DESTINATIONS; index++) {
			HgnButton button = destinationButtons[index];
			boolean hovered = button != null && button.isHoveredOrFocused();
			boolean highlighted = index == 3 || hovered;
			HgnRect bounds = shell.destination(index);
			HgnSurface.control(graphics, bounds, hovered ? 1.0f : 0.0f,
				button != null && button.isFocused(), index == 3, false, false);
			HgnRect icon = shell.destinationIcon(index);
			DESTINATION_ICONS[index].draw(graphics, icon.x(), icon.y(),
				Math.min(icon.width(), icon.height()), HgnPalette.CYAN_SOFT);
			HgnRect title = shell.destinationTitle(index);
			HgnTypography.fitted(graphics, font, DESTINATION_LABELS[index], title.x(),
				title.y(), title.width(), layout.scale() * 0.96f, HgnPalette.TEXT);
			HgnRect detail = shell.destinationDetail(index);
			HgnTypography.fitted(graphics, font, DESTINATION_DETAILS[index], detail.x(),
				detail.y(), detail.width(), layout.scale() * 0.72f,
				highlighted ? HgnPalette.CYAN_SOFT : HgnPalette.TEXT_MUTED);
		}
	}

	private void referenceFit(GuiGraphics graphics, String value, int x, int y,
		int width, float emphasis, int color) {
		HgnTypography.fitted(graphics, font, value, layout.x(x), layout.y(y),
			layout.size(width), layout.scale() * emphasis, color);
	}

	private void referenceHeading(GuiGraphics graphics, String value, int x, int y,
		int width, float emphasis, int color) {
		HgnTypography.fittedHeading(graphics, font, value, layout.x(x), layout.y(y),
			layout.size(width), layout.scale() * emphasis, color);
	}

	private void renderSearchPrompt(GuiGraphics graphics) {
		if (!networkOpen || searchBox == null || !searchBox.getValue().isEmpty()) return;
		HgnTypography.fitted(graphics, font, "Search official servers...",
			searchBox.getX() + 12,
			searchBox.getY() + Math.max(5, (searchBox.getHeight() - font.lineHeight) / 2),
			searchBox.getWidth() - 20, 0.82f, HgnPalette.TEXT_MUTED);
	}

	private void renderCompact(GuiGraphics graphics, int mouseX, int mouseY) {
		HgnSurface.panel(graphics, HgnMultiplayerLayout.compactHeader(width), false);
		compactText(graphics, "HGN SERVERS", 16, 12, 1.05f, HgnPalette.TEXT);
		HgnSurface.panel(graphics, HgnMultiplayerLayout.compactContent(width, height), false);
		if (!networkOpen) {
			compactText(graphics, "HORIZONGATE NETWORK", 16, 75, 1.1f, HgnPalette.TEXT);
			compactText(graphics, HgnOfficialServers.ALL.size() + " official destinations", 16, 94,
				1.0f, HgnPalette.CYAN);
			compactFit(graphics, "Choose a server to inspect its connection and version.",
				16, 112, width - 32, 0.95f, HgnPalette.TEXT_MUTED);
			return;
		}
		List<Integer> matches = matchedServers();
		for (int position = 0; position < matches.size(); position++) {
			int index = matches.get(position);
			var server = HgnOfficialServers.ALL.get(index);
			HgnRect card = HgnMultiplayerLayout.compactServerCard(width, height, position);
			HgnSurface.panel(graphics, card, selected == index || card.contains(mouseX, mouseY));
			int imageSize = Math.min(36, card.height() - 6);
			int sourceSize = serverImageSize(server.id());
			HgnDraw.textureStretch(graphics, "textures/gui/servers/server-" + server.id() + ".png",
				card.x() + 5, card.y() + (card.height() - imageSize) / 2,
				imageSize, imageSize, sourceSize, sourceSize);
			int textY = card.y() + Math.max(5, (card.height() - 28) / 2);
			compactFit(graphics, server.name(), card.x() + 47, textY, card.width() - 55,
				0.98f, HgnPalette.TEXT);
			compactFit(graphics, status(index) + " / " + server.versionLabel(), card.x() + 47,
				textY + 15, card.width() - 55, 0.95f, HgnPalette.TEXT_MUTED);
		}
		if (matches.isEmpty()) compactFit(graphics, "No matching official servers", 16, 78,
			width - 32, 0.95f, HgnPalette.TEXT_MUTED);
		HgnRect join = HgnMultiplayerLayout.compactJoin(width, height);
		if (matches.isEmpty()) return;
		HgnRect lastCard = HgnMultiplayerLayout.compactServerCard(width, height, matches.size() - 1);
		int detailRoom = join.y() - lastCard.bottom();
		var selectedServer = HgnOfficialServers.ALL.get(selected);
		if (detailRoom >= 18) {
			compactFit(graphics, selectedServer.address(), 16, lastCard.bottom() + 7,
				width - 32, 0.95f, HgnPalette.TEXT_MUTED);
		}
		if (detailRoom >= 32) {
			compactFit(graphics, selectedServer.blurb(), 16, lastCard.bottom() + 21,
				width - 32, 0.95f, HgnPalette.TEXT_MUTED);
		}
		compactFit(graphics, status(selected), 16, join.y() + 7, join.x() - 24,
			0.95f, HgnPalette.CYAN);
	}

	private void compactText(GuiGraphics graphics, String value, int x, int y,
		float size, int color) {
		HgnTypography.text(graphics, font, value, x, y, size, color);
	}

	private void compactFit(GuiGraphics graphics, String value, int x, int y,
		int available, float size, int color) {
		compactText(graphics, HgnTypography.trim(font, value,
			Math.max(1, (int) (available / size))), x, y, size, color);
	}

	private void renderDiscovery(GuiGraphics graphics, int mouseX, int mouseY) {
		panel(graphics, 207, 108, 595, 195, layout.rect(207, 108, 595, 195).contains(mouseX, mouseY));
		serverBanner(graphics, "hub", 210, 111, 589, 189, 0.25f, 0.42f);
		HgnDraw.rect(graphics, layout.x(211), layout.y(112), layout.x(486), layout.y(298),
			0xD8040A10);
		text(graphics, "FEATURED NETWORK", 232, 131, 0.75f, HgnPalette.CYAN);
		text(graphics, "HORIZONGATE", 232, 156, 1.7f, HgnPalette.TEXT);
		text(graphics, "NETWORK", 232, 178, 1.7f, HgnPalette.TEXT);
		text(graphics, "One community. Choose your adventure.", 232, 213, 0.91f, HgnPalette.TEXT_MUTED);
		text(graphics, HgnOfficialServers.ALL.size() + " official destinations", 232, 232, 0.78f, HgnPalette.CYAN);
		HgnSurface.inset(graphics, layout.rect(613, 261, 174, 26), false);
		fit(graphics, status(0), 622, 267, 156, 0.69f, HgnPalette.CYAN);
		panel(graphics, 207, 317, 277, 110, false);
		panel(graphics, 496, 317, 306, 110, false);
		text(graphics, "YOUR SAVED SERVERS", 227, 334, 0.94f, HgnPalette.TEXT);
		text(graphics, "Saved servers and LAN", 227, 349, 0.68f, HgnPalette.TEXT_MUTED);
		text(graphics, "BUILT FOR YOUR NEXT SESSION", 514, 337, 0.89f, HgnPalette.CYAN);
		text(graphics, "Choose a server to inspect its connection", 514, 361, 0.77f, HgnPalette.TEXT_MUTED);
		text(graphics, "and Minecraft version before joining.", 514, 377, 0.77f, HgnPalette.TEXT_MUTED);
		text(graphics, "Saved in Minecraft's server list.", 514, 401, 0.69f, HgnPalette.TEXT_MUTED);
	}

	private void renderServers(GuiGraphics graphics, int mouseX, int mouseY) {
		List<Integer> matches = matchedServers();
		fit(graphics, matches.size() + " official", 584, 113, 99, 0.76f, HgnPalette.CYAN);
		for (int position = 0; position < matches.size(); position++) {
			int index = matches.get(position);
			var server = HgnOfficialServers.ALL.get(index);
			HgnRect card = HgnMultiplayerLayout.serverCard(position);
			int y = card.y();
			panel(graphics, card.x(), y, card.width(), card.height(), selected == index
				|| layout.rect(card).contains(mouseX, mouseY));
			serverBanner(graphics, server.id(), 218, y + 13, 116, 88, 0.16f, 0.76f);
			fit(graphics, server.name(), 346, y + 17, 211, 0.98f, HgnPalette.TEXT);
			fit(graphics, server.blurb(), 346, y + 44, 211, 0.70f, HgnPalette.TEXT_MUTED);
			fit(graphics, status(index), 346, y + 70, 211, 0.72f, HgnPalette.CYAN);
			fit(graphics, players(index), 346, y + 90, 211, 0.69f, HgnPalette.TEXT_MUTED);
			fit(graphics, "Minecraft " + server.versionLabel(), 218, y + 108, 230,
				0.68f, HgnPalette.TEXT_MUTED);
			text(graphics, "OFFICIAL", 496, y + 108, 0.65f, HgnPalette.CYAN);
		}
		if (matches.isEmpty()) {
			panel(graphics, 207, 145, 365, 128, false);
			text(graphics, "NO MATCHING SERVERS", 224, 169, 1.0f, HgnPalette.TEXT);
			text(graphics, "Try another name or server address.", 224, 196, 0.75f, HgnPalette.TEXT_MUTED);
		}
		HgnRect inspector = HgnMultiplayerLayout.inspector();
		panel(graphics, inspector.x(), inspector.y(), inspector.width(), inspector.height(), false);
		text(graphics, "CONNECTION DETAILS", 599, 161, 0.77f, HgnPalette.CYAN);
		if (matches.isEmpty()) {
			fit(graphics, "Select an official server", 599, 200, 188, 0.9f, HgnPalette.TEXT_MUTED);
			return;
		}
		var server = HgnOfficialServers.ALL.get(selected);
		serverBanner(graphics, server.id(), 599, 181, 188, 92, 0.25f, 0.49f);
		fit(graphics, server.name(), 599, 281, 188, 0.95f, HgnPalette.TEXT);
		fit(graphics, server.address(), 599, 302, 188, 0.72f, HgnPalette.TEXT_MUTED);
		fit(graphics, status(selected), 599, 322, 188, 0.75f, HgnPalette.CYAN);
		fit(graphics, players(selected), 599, 341, 188, 0.69f, HgnPalette.TEXT_MUTED);
		fit(graphics, server.playableNow() ? "MC " + server.versionLabel() + " / ready"
			: "Requires Minecraft " + server.requiredVersion(), 599, 361, 188, 0.69f,
			server.playableNow() ? HgnPalette.TEXT_MUTED : HgnPalette.WARNING);
	}

	private void serverBanner(GuiGraphics graphics, String id, int x, int y,
		int width, int height, float topFraction, float heightFraction) {
		int sourceSize = serverImageSize(id);
		int sourceY = Math.round(sourceSize * topFraction);
		int sourceHeight = Math.min(sourceSize - sourceY,
			Math.max(1, Math.round(sourceSize * heightFraction)));
		HgnDraw.textureRegionStretch(graphics, "textures/gui/servers/server-" + id + ".png",
			layout.x(x), layout.y(y), layout.size(width), layout.size(height),
			0, sourceY, sourceSize, sourceHeight, sourceSize, sourceSize);
	}

	private String players(int index) {
		ServerData data = index < statuses.size() ? statuses.get(index) : null;
		return HgnServerPopulation.fromMinecraftPing(true,
			data == null || data.players == null ? -1 : data.players.online(),
			data == null || data.players == null ? -1 : data.players.max());
	}

	private String status(int index) {
		if (index >= statuses.size()) return "Status unavailable";
		ServerData data = statuses.get(index);
		if (data.ping >= 0L) return "Online  /  " + data.ping + " ms";
		return System.nanoTime() - refreshStarted < 10_000_000_000L ? "Checking connection..." : "Unreachable / retry available";
	}

	private void panel(GuiGraphics graphics, int x, int y, int w, int h, boolean active) {
		HgnSurface.panel(graphics, layout.rect(x, y, w, h), active);
	}

	private int serverImageSize(String serverId) {
		return serverId.equals("hub") ? 1254 : 256;
	}

	private void text(GuiGraphics graphics, String value, int x, int y, float size, int color) {
		HgnTypography.text(graphics, font, value, layout.x(x), layout.y(y),
			HgnMultiplayerLayout.textScale(layout.scale(), size), color);
	}

	private void fit(GuiGraphics graphics, String value, int x, int y, int width, float size, int color) {
		float textScale = HgnMultiplayerLayout.textScale(layout.scale(), size);
		int available = Math.max(1, layout.x(x + width) - layout.x(x));
		text(graphics, HgnTypography.trim(font, value, (int) (available / textScale)), x, y, size, color);
	}
}
