package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.server.LanServerDetection;
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
import online.horizongate.hgn.ui.HgnSavedServersLayout;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnServerPopulation;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** HGN saved-server and LAN browser backed by Minecraft's own server file and discovery. */
public final class HgnSavedServersScreen extends Screen {
	private final Screen parent;
	private final List<ServerData> entries = new ArrayList<>();
	private final java.util.Map<ServerData, Long> queried = new java.util.IdentityHashMap<>();
	private final java.util.Map<ServerData, String> officialIds = new java.util.IdentityHashMap<>();
	private ServerList saved;
	private ServerStatusPinger pinger;
	private ExecutorService executor;
	private LanServerDetection.LanServerList lanList;
	private LanServerDetection.LanServerDetector detector;
	private HgnDashboardLayout frame;
	private boolean lan;
	private boolean compact;
	private int page;
	private String searchQuery = "";
	private EditBox searchBox;
	private ServerData selected;
	private final online.horizongate.hgn.ui.HgnDoubleClick serverClicks = new online.horizongate.hgn.ui.HgnDoubleClick();
	private long refreshStarted;
	private volatile long generation;
	private boolean confirmDelete;
	private String notice = "";

	public HgnSavedServersScreen(Screen parent) {
		this(parent, false);
	}

	public HgnSavedServersScreen(Screen parent, boolean lan) {
		super(Component.literal("HGN Servers"));
		this.parent = parent;
		this.lan = lan;
	}

	@Override
	protected void init() {
		frame = HgnDashboardLayout.resolve(width, height);
		compact = HgnMultiplayerLayout.compact(width, height);
		if (pinger == null) refresh();
		else rebuild();
	}

	private void refresh() {
		stop();
		entries.clear();
		queried.clear();
		officialIds.clear();
		selected = null;
		serverClicks.reset();
		confirmDelete = false;
		notice = "";
		refreshStarted = System.nanoTime();
		pinger = new ServerStatusPinger();
		executor = Executors.newFixedThreadPool(3, task -> {
			Thread thread = new Thread(task, "HGN saved server status");
			thread.setDaemon(true);
			return thread;
		});
		if (lan) {
			lanList = new LanServerDetection.LanServerList();
			try {
				detector = new LanServerDetection.LanServerDetector(lanList);
				detector.start();
			} catch (java.io.IOException failure) {
				notice = "LAN discovery unavailable. Use Direct connect.";
			}
		} else {
			saved = new ServerList(minecraft);
			saved.load();
			for (int index = 0; index < saved.size(); index++) entries.add(saved.get(index));
		}
		page = Math.min(page, Math.max(0, (filteredEntries().size() - 1) / pageSize()));
		pingPage();
		rebuild();
	}

	private int pageSize() { return compact ? 2 : HgnSavedServersLayout.WIDE_ROWS; }

	private List<ServerData> filteredEntries() {
		List<ServerData> matches = new ArrayList<>();
		for (ServerData data : entries) {
			if (HgnMultiplayerLayout.matches(searchQuery, data.name, data.ip,
				data.motd == null ? null : data.motd.getString())) matches.add(data);
		}
		return matches;
	}

	private void pingPage() {
		long activeGeneration = generation;
		ServerStatusPinger activePinger = pinger;
		List<ServerData> visible = filteredEntries();
		for (int index = page * pageSize(); index < Math.min(visible.size(), (page + 1) * pageSize()); index++) {
			ServerData data = visible.get(index);
			if (queried.containsKey(data)) continue;
			queried.put(data, System.nanoTime());
			data.ping = -2;
			executor.execute(() -> {
				try {
					if (generation == activeGeneration) HgnCompat.pingServer(activePinger, data, () -> {}, () -> {});
				} finally {
					if (generation != activeGeneration) activePinger.removeAll();
				}
			});
		}
	}

	private void rebuild() {
		clearWidgets();
		if (compact) {
			rebuildCompact();
			return;
		}
		wideCommand(0, "OFFICIAL SERVERS", HgnIcon.SERVERS, false,
			this::openOfficial);
		wideCommand(1, "SAVED SERVERS", HgnIcon.WORLD, !lan, () -> switchMode(false));
		wideCommand(2, "LAN GAMES", HgnIcon.MULTIPLAYER, lan, () -> switchMode(true));
		wideCommand(3, "ADD SERVER", HgnIcon.PLAY, false, () -> edit(null));
		wideCommand(4, "DIRECT CONNECT", HgnIcon.LINK, false,
			() -> minecraft.setScreen(new HgnServerEditorScreen(this, null, null, true)));
		wideCommand(5, "REFRESH", HgnIcon.SERVERS, false, this::refreshIfReady);
		wideCommand(6, "MAIN MENU", HgnIcon.BACK, false,
			() -> minecraft.setScreen(new HgnDashboardScreen()));
		wideCommand(7, "BACK", HgnIcon.BACK, false, this::onClose);
		searchBox = addSearch(frame.rect(HgnSavedServersLayout.wideSearch()));
		wideButton(HgnSavedServersLayout.wideMode(false), "Saved", !lan,
			() -> switchMode(false));
		wideButton(HgnSavedServersLayout.wideMode(true), "LAN", lan,
			() -> switchMode(true));
		List<ServerData> visible = filteredEntries();
		for (int index = page * pageSize(); index < Math.min(visible.size(), (page + 1) * pageSize()); index++) {
			ServerData data = visible.get(index);
			var rect = frame.rect(HgnSavedServersLayout.wideRow(index % pageSize()));
			addRenderableWidget(HgnButton.hitTarget(rect.x(), rect.y(), rect.width(), rect.height(),
				Component.literal("Select " + data.name), ignored -> select(data)));
		}
		wideButton(HgnSavedServersLayout.wideJoin(), "JOIN SERVER", true,
			this::join).active = selected != null;
		wideButton(HgnSavedServersLayout.wideEdit(), "Edit", false,
			() -> edit(selected)).active = selected != null && !lan;
		wideButton(HgnSavedServersLayout.wideDelete(), confirmDelete ? "Confirm delete" : "Delete", false,
			this::deleteSelected).active = selected != null && !lan;
		wideButton(HgnSavedServersLayout.widePrevious(), "Previous", false,
			() -> changePage(-1)).active = page > 0;
		wideButton(HgnSavedServersLayout.wideNext(), "Next", false,
			() -> changePage(1)).active = (page + 1) * pageSize() < visible.size();
	}

	private void rebuildCompact() {
		compactButton(HgnSavedServersLayout.back(), "Back", false, this::onClose);
		searchBox = addSearch(HgnSavedServersLayout.search(width));
		compactButton(HgnSavedServersLayout.refresh(width), "Refresh", false, this::refreshIfReady);
		compactButton(HgnSavedServersLayout.tab(width, 0), "Saved", !lan, () -> switchMode(false));
		compactButton(HgnSavedServersLayout.tab(width, 1), "LAN", lan, () -> switchMode(true));
		compactButton(HgnSavedServersLayout.tab(width, 2), "Add", false, () -> edit(null));
		compactButton(HgnSavedServersLayout.tab(width, 3), "Direct", false,
			() -> minecraft.setScreen(new HgnServerEditorScreen(this, null, null, true)));
		List<ServerData> visible = filteredEntries();
		for (int index = page * 2; index < Math.min(visible.size(), page * 2 + 2); index++) {
			ServerData data = visible.get(index);
			HgnRect row = HgnSavedServersLayout.row(width, height, index % 2);
			addRenderableWidget(HgnButton.hitTarget(row.x(), row.y(), row.width(), row.height(),
				Component.literal("Select " + data.name), ignored -> select(data)));
		}
		compactButton(HgnSavedServersLayout.previous(height), "Prev", false,
			() -> changePage(-1)).active = page > 0;
		compactButton(HgnSavedServersLayout.next(height), "Next", false,
			() -> changePage(1)).active = (page + 1) * 2 < visible.size();
		compactButton(HgnSavedServersLayout.edit(height), "Edit", false,
			() -> edit(selected)).active = selected != null && !lan;
		compactButton(HgnSavedServersLayout.delete(height),
			confirmDelete ? "Confirm delete" : "Delete", false,
			this::deleteSelected).active = selected != null && !lan;
		compactButton(HgnSavedServersLayout.join(width, height), "Join server", true,
			this::join).active = selected != null;
	}

	private EditBox addSearch(HgnRect bounds) {
		EditBox box = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			Component.literal("Search saved or LAN servers"));
		box.setMaxLength(100);
		box.setValue(searchQuery);
		box.setResponder(value -> {
			searchQuery = value;
			page = 0;
			int selectedIndex = filteredEntries().indexOf(selected);
			if (selectedIndex < 0 || selectedIndex >= pageSize()) selected = null;
			confirmDelete = false;
			serverClicks.reset();
			pingPage();
			rebuild();
			if (searchBox != null) setInitialFocus(searchBox);
		});
		return addRenderableWidget(box);
	}

	private HgnButton wideCommand(int index, String label, HgnIcon icon,
		boolean selected, Runnable action) {
		HgnRect bounds = frame.rect(HgnSavedServersLayout.wideCommand(index));
		return addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), icon, selected,
			ignored -> action.run()).referenceScale(frame.scale()));
	}

	private HgnButton wideButton(HgnRect reference, String label,
		boolean primary, Runnable action) {
		HgnRect bounds = frame.rect(reference);
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), primary,
			ignored -> action.run()).referenceScale(frame.scale() * 0.84f));
	}

	private HgnButton compactButton(HgnRect bounds, String label, boolean primary, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(), bounds.width(),
			bounds.height(), Component.literal(label), primary, ignored -> action.run()).referenceScale(1f));
	}

	private void switchMode(boolean value) {
		if (lan == value) return;
		lan = value;
		page = 0;
		refresh();
	}

	private void refreshIfReady() {
		if (System.nanoTime() - refreshStarted > 2_000_000_000L) refresh();
	}

	private void openOfficial() {
		minecraft.setScreen(parent instanceof HgnMultiplayerScreen
			? parent : new HgnMultiplayerScreen(parent));
	}

	private void changePage(int delta) {
		page += delta;
		selected = null;
		serverClicks.reset();
		confirmDelete = false;
		pingPage();
		rebuild();
	}

	private void edit(ServerData data) {
		minecraft.setScreen(new HgnServerEditorScreen(this, saved, data, false));
	}

	private void deleteSelected() {
		if (selected == null || lan || saved == null) return;
		if (!confirmDelete) { confirmDelete = true; rebuild(); return; }
		saved.remove(selected);
		saved.save();
		refresh();
	}

	private void select(ServerData data) {
		boolean doubleClick = serverClicks.activate(data, System.nanoTime());
		selected = data;
		confirmDelete = false;
		if (doubleClick) join();
		else rebuild();
	}

	private void join() {
		if (selected != null) HgnCompat.connect(this, minecraft, selected);
	}

	@Override
	public void tick() {
		if (pinger != null) pinger.tick();
		if (lanList != null) {
			var discovered = lanList.takeDirtyServers();
			if (discovered != null) {
				for (var server : discovered) {
					if (entries.stream().noneMatch(data -> data.ip.equals(server.getAddress()))) {
						entries.add(new ServerData(server.getMotd(), server.getAddress(), ServerData.Type.LAN));
					}
				}
				pingPage();
				rebuild();
			}
		}
	}

	private void stop() {
		generation++;
		if (detector != null) { detector.interrupt(); detector = null; }
		lanList = null;
		if (executor != null) { executor.shutdownNow(); executor = null; }
		if (pinger != null) { pinger.removeAll(); pinger = null; }
	}

	@Override
	public void removed() { serverClicks.reset(); stop(); }

	@Override
	public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (compact) {
			renderCompact(graphics, mouseX, mouseY);
			super.render(graphics, mouseX, mouseY, partialTick);
			renderSearchPrompt(graphics);
			return;
		}
		renderWideHeader(graphics);
		HgnSurface.panel(graphics, frame.rect(HgnSavedServersLayout.wideRail()), false);
		HgnSurface.panel(graphics, frame.rect(HgnSavedServersLayout.wideHeader()), false);
		text(graphics, "MULTIPLAYER", 226, 97, 1.4f, HgnPalette.TEXT);
		fit(graphics, lan ? "Live LAN discovery from this network"
			: "Your Minecraft saved destinations", 227, 116, 525, 0.83f, HgnPalette.TEXT_MUTED);
		text(graphics, "HGN / CONNECT", 35, 82, 0.72f, HgnPalette.CYAN);
		renderWideList(graphics, mouseX, mouseY);
		renderWideInspector(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderSearchPrompt(graphics);
	}

	private void renderWideHeader(GuiGraphics graphics) {
		HgnRect masthead = frame.rect(20, 12, 384, 69);
		HgnDraw.gradient(graphics, masthead.x(), masthead.y(), masthead.right(),
			masthead.bottom(), 0xC4071119, 0xA0071119);
		HgnSurface.wordmark(graphics, frame.x(28), frame.y(12),
			frame.size(73), frame.size(69), false);
		heading(graphics, "HORIZONGATE", 108, 27, 284, 1.62f, HgnPalette.TEXT);
		heading(graphics, "C L I E N T", 108, 47, 284, 1.16f, HgnPalette.CYAN);
		fit(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			108, 66, 260, 0.76f, HgnPalette.TEXT);
		HgnRect badge = frame.rect(716, 11, 98, 20);
		HgnSurface.inset(graphics, badge, false);
		fit(graphics, "HGN " + HgnClient.minecraftVersion(), 728, 16, 80,
			0.8f, HgnPalette.CYAN);
	}

	private void renderWideList(GuiGraphics graphics, int mouseX, int mouseY) {
		List<ServerData> visible = filteredEntries();
		for (int index = page * pageSize(); index < Math.min(visible.size(), (page + 1) * pageSize()); index++) {
			ServerData data = visible.get(index);
			HgnRect row = frame.rect(HgnSavedServersLayout.wideRow(index % pageSize()));
			HgnSurface.panel(graphics, row, data == selected || row.contains(mouseX, mouseY));
			int y = HgnSavedServersLayout.wideRow(index % pageSize()).y();
			serverArtwork(graphics, data, 217, y + 5, 55, 32);
			fit(graphics, data.name, 282, y + 7, 205, 0.86f, HgnPalette.TEXT);
			fit(graphics, subtitle(data),
				282, y + 23, 205, 0.77f, HgnPalette.TEXT_MUTED);
			fit(graphics, status(data), 495, y + 8, 74, 0.75f, statusColor(data));
			fit(graphics, rowPopulation(data), 495, y + 24, 74, 0.75f, HgnPalette.TEXT_MUTED);
		}
		if (visible.isEmpty()) {
			HgnSurface.panel(graphics, frame.rect(211, 172, 365, 226), false);
			text(graphics, !searchQuery.isBlank() ? "NO MATCHING SERVERS"
				: lan ? "SEARCHING FOR LAN GAMES" : "NO SAVED SERVERS",
				226, 201, 1f, HgnPalette.TEXT);
			fit(graphics, !searchQuery.isBlank() ? "Try another server name or address."
				: lan ? "Local games appear when discovered."
				: "Add a server or connect directly to one.",
				226, 228, 330, 0.83f, HgnPalette.TEXT_MUTED);
		}
		fit(graphics, (page + 1) + " / " + Math.max(1,
			(visible.size() + pageSize() - 1) / pageSize()) + "  •  " + visible.size()
			+ (lan ? " LAN games" : " saved servers"), 302, 414, 188,
			0.78f, HgnPalette.TEXT_MUTED);
	}

	private void renderWideInspector(GuiGraphics graphics) {
		HgnSurface.panel(graphics, frame.rect(HgnSavedServersLayout.wideInspector()), false);
		text(graphics, "SERVER DETAILS", 598, 180, 0.79f, HgnPalette.CYAN);
		if (selected == null) {
			HgnSurface.inset(graphics, frame.rect(597, 200, 219, 94), false);
			HgnIcon.SERVERS.draw(graphics, frame.x(681), frame.y(219),
				frame.size(42), HgnPalette.CYAN);
			fit(graphics, "Select a server to inspect it", 600, 306, 214,
				0.79f, HgnPalette.TEXT);
			fit(graphics, lan ? "LAN discovery is local to this network."
				: "Double-click a row to join quickly.", 600, 326, 214,
				0.76f, HgnPalette.TEXT_MUTED);
		} else {
			serverArtwork(graphics, selected, 597, 200, 219, 94);
			fit(graphics, selected.name, 600, 304, 214, 0.96f, HgnPalette.TEXT);
			fit(graphics, selected.ip, 600, 321, 214, 0.72f, HgnPalette.TEXT_MUTED);
			fit(graphics, status(selected) + "  /  " + players(selected),
				600, 339, 214, 0.77f, statusColor(selected));
			fit(graphics, selected.version == null ? "Server version unavailable"
				: "Minecraft " + selected.version.getString(), 600, 353, 214,
				0.75f, HgnPalette.TEXT_MUTED);
		}
		if (!notice.isEmpty()) fit(graphics, notice, 211, 444, 616,
			0.62f, HgnPalette.WARNING);
	}

	private void serverArtwork(GuiGraphics graphics, ServerData data,
		int x, int y, int width, int height) {
		HgnRect bounds = frame.rect(x, y, width, height);
		HgnSurface.inset(graphics, bounds, false);
		String serverId = officialId(data);
		if (serverId == null) {
			int icon = Math.min(height - 8, width - 8);
			HgnIcon.SERVERS.draw(graphics, bounds.centerX() - frame.size(icon) / 2,
				bounds.centerY() - frame.size(icon) / 2, frame.size(icon), HgnPalette.CYAN);
			return;
		}
		int sourceSize = serverId.equals("hub") ? 1254 : 256;
		int sourceY = Math.round(sourceSize * 0.2f);
		int sourceHeight = Math.round(sourceSize * 0.58f);
		HgnDraw.textureRegionStretch(graphics,
			"textures/gui/servers/server-" + serverId + ".png",
			bounds.x() + 1, bounds.y() + 1,
			Math.max(1, bounds.width() - 2), Math.max(1, bounds.height() - 2),
			0, sourceY, sourceSize, sourceHeight, sourceSize, sourceSize);
		HgnSurface.cornerAccent(graphics, bounds, false);
	}

	private String officialId(ServerData data) {
		String id = officialIds.computeIfAbsent(data, this::lookupOfficialId);
		return id.isEmpty() ? null : id;
	}

	private String lookupOfficialId(ServerData data) {
		String host = net.minecraft.client.multiplayer.resolver.ServerAddress
			.parseString(data.ip).getHost();
		for (var server : HgnOfficialServers.ALL) {
			String officialHost = net.minecraft.client.multiplayer.resolver.ServerAddress
				.parseString(server.address()).getHost();
			if (officialHost.equalsIgnoreCase(host)) return server.id();
		}
		return "";
	}

	private void renderSearchPrompt(GuiGraphics graphics) {
		if (searchBox == null || !searchBox.getValue().isEmpty()) return;
		HgnTypography.fitted(graphics, font,
			lan ? "Search LAN games..." : "Search saved servers...",
			searchBox.getX() + 10,
			searchBox.getY() + Math.max(5, (searchBox.getHeight() - font.lineHeight) / 2),
			searchBox.getWidth() - 17, 0.81f, HgnPalette.TEXT_MUTED);
	}

	private void renderCompact(GuiGraphics graphics, int mouseX, int mouseY) {
		HgnSurface.panel(graphics, HgnSavedServersLayout.header(width), false);
		HgnSurface.panel(graphics, HgnSavedServersLayout.content(width, height), false);
		List<ServerData> visible = filteredEntries();
		for (int index = page * 2; index < Math.min(visible.size(), page * 2 + 2); index++) {
			ServerData data = visible.get(index);
			HgnRect row = HgnSavedServersLayout.row(width, height, index % 2);
			HgnSurface.panel(graphics, row, data == selected || row.contains(mouseX, mouseY));
			compactFit(graphics, data.name, row.x() + 8, row.y() + 5, row.width() - 98,
				1.0f, HgnPalette.TEXT);
			compactFit(graphics, data.ip, row.x() + 8, row.y() + 17, row.width() - 98,
				0.9f, HgnPalette.TEXT_MUTED);
			compactFit(graphics, status(data), row.right() - 84, row.y() + 7, 77,
				0.85f, HgnPalette.CYAN);
		}
		int summaryY = HgnSavedServersLayout.row(width, height, 1).bottom() + 2;
		if (visible.isEmpty()) compactFit(graphics, !searchQuery.isBlank()
			? "No matching servers" : lan ? "Searching for LAN games..." : "No saved servers",
			16, 77, width - 32, 0.95f, HgnPalette.TEXT_MUTED);
		else compactFit(graphics, selected == null
			? "Select a server / " + (page + 1) + " of " + Math.max(1, (visible.size() + 1) / 2)
			: selected.name + " / " + players(selected), 16, summaryY, width - 32,
			0.9f, HgnPalette.TEXT_MUTED);
		if (!notice.isEmpty() && summaryY + 20 < HgnSavedServersLayout.join(width, height).y()) {
			compactFit(graphics, notice, 16, summaryY + 14, width - 32, 0.85f,
				HgnPalette.WARNING);
		}
	}

	private String status(ServerData data) {
		if (data.ping >= 0) return data.ping + " ms";
		return System.nanoTime() - queried.getOrDefault(data, refreshStarted) < 10_000_000_000L ? "Checking..." : "Unreachable";
	}

	private int statusColor(ServerData data) {
		if (data.ping >= 0) return HgnPalette.SUCCESS;
		return System.nanoTime() - queried.getOrDefault(data, refreshStarted) < 10_000_000_000L
			? HgnPalette.CYAN : HgnPalette.WARNING;
	}

	private String subtitle(ServerData data) {
		String value = data.motd == null ? data.ip : data.motd.getString();
		return value.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private String players(ServerData data) {
		return HgnServerPopulation.fromMinecraftPing(officialId(data) != null,
			data.players == null ? -1 : data.players.online(),
			data.players == null ? -1 : data.players.max());
	}

	private String rowPopulation(ServerData data) {
		if (officialId(data) != null || data.players == null) return "Count n/a";
		return players(data);
	}

	private void text(GuiGraphics graphics, String value, int x, int y, float size, int color) {
		HgnTypography.text(graphics, font, value, frame.x(x), frame.y(y), frame.scale() * size, color);
	}

	private void fit(GuiGraphics graphics, String value, int x, int y, int w, float size, int color) {
		text(graphics, HgnTypography.trim(font, value, (int) (w / size)), x, y, size, color);
	}

	private void heading(GuiGraphics graphics, String value, int x, int y,
		int width, float size, int color) {
		HgnTypography.fittedHeading(graphics, font, value, frame.x(x), frame.y(y),
			frame.size(width), frame.scale() * size, color);
	}

	private void compactFit(GuiGraphics graphics, String value, int x, int y, int width,
		float size, int color) {
		HgnTypography.text(graphics, font, HgnTypography.trim(font, value,
			Math.max(1, (int) (width / size))), x, y, size, color);
	}
}
