package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.compat.HgnScaledEditBox;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnInstalledModsLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Installed JARs stay read-only; discovery and exact release review have explicit handoffs. */
public final class HgnInstalledModsScreen extends Screen {
	private final Screen parent;
	private HgnInstalledModsIndex.Snapshot snapshot;
	private HgnInstalledModsLayout layout;
	private List<HgnInstalledModsIndex.InstalledJar> filtered = List.of();
	private final List<HgnButton> rowButtons = new ArrayList<>();
	private String search = "";
	private String selectedFile;
	private int page;
	private boolean filterDirty;
	private EditBox searchBox;
	private EditBox selectedBox;

	public HgnInstalledModsScreen(Screen parent) {
		super(Component.literal("HGN Installed Mods"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (snapshot == null) snapshot = HgnInstalledModsIndex.read(minecraft.gameDirectory.toPath());
		rebuild();
	}

	@Override
	public void tick() {
		if (!filterDirty) return;
		filterDirty = false;
		rebuild();
		if (searchBox != null) searchBox.setFocused(true);
	}

	private void rebuild() {
		clearWidgets();
		rowButtons.clear();
		layout = HgnInstalledModsLayout.resolve(width, height);
		filtered = snapshot.filter(search);
		page = Math.max(0, Math.min(page, layout.pages(filtered.size()) - 1));
		HgnRect searchBounds = layout.search();
		searchBox = addRenderableWidget(new HgnScaledEditBox(font, searchBounds.x() + 4,
			searchBounds.y() + 1, searchBounds.width() - 8, searchBounds.height() - 2,
			Component.literal("Search installed mod filenames"), layout.compact() ? 0.8f : 0.92f));
		searchBox.setMaxLength(255);
		searchBox.setBordered(false);
		searchBox.setHint(HgnCompat.uiText(layout.compact() ? "Search installed JARs..."
			: "Search this installation's mod files..."));
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			if (value.equals(search)) return;
			search = value;
			page = 0;
			filterDirty = true;
		});
		int first = page * layout.rowsPerPage();
		for (int row = 0; row < layout.rowsPerPage() && first + row < filtered.size(); row++) {
			String fileName = filtered.get(first + row).fileName();
			HgnRect bounds = layout.row(row);
			rowButtons.add(addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(), Component.literal("Installed JAR: " + fileName),
				button -> select(fileName))));
		}
		HgnRect detail = layout.detail();
		int fileX = detail.x() + (layout.compact() ? 3 : 8);
		int fileY = detail.y() + (layout.compact() ? 2 : 43);
		int fileHeight = layout.compact() ? 14 : 18;
		selectedBox = addRenderableWidget(new HgnScaledEditBox(font, fileX, fileY,
			detail.width() - (layout.compact() ? 6 : 16), fileHeight,
			Component.literal("Exact selected installed JAR filename"),
			layout.compact() ? 0.73f : 0.86f));
		selectedBox.setMaxLength(255);
		selectedBox.setBordered(false);
		selectedBox.setValue(selectedFile == null ? "No installed JAR selected" : selectedFile);
		selectedBox.setEditable(false);
		button(layout.back(), layout.compact() ? "BACK" : "MAIN MENU", HgnIcon.BACK,
			false, this::onClose);
		button(layout.installed(), "INSTALLED", HgnIcon.MODS, true, () -> {});
		button(layout.browse(), layout.compact() ? "BROWSE" : "DISCOVER", HgnIcon.SEARCH,
			false, () -> minecraft.setScreen(new HgnModCatalogScreen(this)));
		if (showsEmptyDiscoveryHero()) {
			HgnRect action = layout.emptyDiscoveryAction();
			addRenderableWidget(HgnButton.dashboardCentered(action.x(), action.y(),
				action.width(), action.height(), Component.literal("DISCOVER MODS"), true,
				ignored -> minecraft.setScreen(new HgnModCatalogScreen(this)))
				.referenceScale(0.82f));
		}
		button(layout.refresh(), layout.compact() ? "SCAN" : "REFRESH", HgnIcon.SETTINGS,
			false, this::rescan);
		if (!layout.compact()) button(layout.manage(), "OPEN LAUNCHER", HgnIcon.LINK,
			true, () -> HgnCompat.openUrl(HgnModsLauncherLink.fromEnvironment()));
		HgnButton previous = button(layout.previous(), "<", HgnIcon.NONE, false,
			() -> { page--; rebuild(); });
		previous.active = page > 0;
		HgnButton next = button(layout.next(), ">", HgnIcon.NONE, false,
			() -> { page++; rebuild(); });
		next.active = page + 1 < layout.pages(filtered.size());
	}

	private boolean showsEmptyDiscoveryHero() {
		return layout.showsEmptyDiscoveryHero() && snapshot.problem().isEmpty()
			&& snapshot.jars().isEmpty();
	}

	private void rescan() {
		snapshot = HgnInstalledModsIndex.read(minecraft.gameDirectory.toPath());
		if (selectedFile != null && snapshot.jars().stream()
			.noneMatch(jar -> jar.fileName().equals(selectedFile))) selectedFile = null;
		rebuild();
	}

	private HgnButton button(HgnRect bounds, String label, HgnIcon icon,
		boolean primary, Runnable action) {
		if (layout.compact()) return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(),
			bounds.y(), bounds.width(), bounds.height(), Component.literal(label), primary,
			ignored -> action.run()).referenceScale(0.82f));
		return addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), icon, primary,
			ignored -> action.run()).referenceScale(0.82f, 0.74f));
	}

	private void select(String fileName) {
		selectedFile = fileName;
		selectedBox.setValue(fileName);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		renderHeader(graphics);
		if (!layout.compact()) renderNavigation(graphics);
		HgnSurface.panel(graphics, layout.workspace(), false);
		HgnSurface.dashboardInset(graphics, layout.search(), false);
		HgnTypography.fitted(graphics, font, layout.compact() ? "INSTALLED MODS"
			: "MODS  /  INSTALLED", layout.workspace().x() + 7, layout.workspace().y() + 6,
			layout.workspace().width() - 14, layout.compact() ? 0.88f : 1.18f,
			HgnPalette.CYAN);
		if (!layout.compact()) HgnTypography.fitted(graphics, font,
			"Local JAR files in this installation", layout.workspace().x() + 7,
			layout.workspace().y() + 24, layout.workspace().width() - 14,
			0.72f, HgnPalette.mutedText());
		renderRows(graphics, mouseX, mouseY);
		HgnTypography.fitted(graphics, font,
			filtered.size() == snapshot.jars().size()
				? snapshot.jars().size() + " INSTALLED"
				: filtered.size() + " MATCHING / " + snapshot.jars().size() + " INSTALLED",
			layout.workspace().x() + 7, layout.previous().y() + (layout.compact() ? 2 : 4),
			layout.previous().x() - layout.workspace().x() - 10,
			layout.compact() ? 0.67f : 0.77f, HgnPalette.mutedText());
		if (!layout.compact()) renderDetail(graphics);
		else HgnSurface.dashboardInset(graphics, layout.detail(), selectedFile != null);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphics graphics) {
		HgnRect header = layout.header();
		if (layout.compact()) {
			HgnSurface.wordmark(graphics, header.x() + 3, header.y() + 1, 28, 18, false);
			HgnTypography.fittedHeading(graphics, font, "HORIZONGATE CLIENT", header.x() + 37,
				header.y() + 4, header.width() - 41, 1.05f, HgnPalette.TEXT);
			return;
		}
		HgnSurface.wordmark(graphics, header.x() + 5, header.y() + 2, 48, 37, false);
		HgnTypography.fittedHeading(graphics, font, "HORIZONGATE", header.x() + 63,
			header.y() + 3, header.width() - 178, 1.72f, HgnPalette.TEXT);
		HgnTypography.fittedHeading(graphics, font, "C L I E N T", header.x() + 67,
			header.y() + 25, header.width() - 182, 0.96f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			header.x() + 64, header.y() + 42, header.width() - 200,
			0.65f, HgnPalette.mutedText());
		String target = HgnPlatform.get().loaderName().toUpperCase(Locale.ROOT)
			+ "  /  " + HgnClient.minecraftVersion();
		HgnTypography.fitted(graphics, font, target, header.right() - 157,
			header.y() + 7, 150, 0.75f, HgnPalette.CYAN);
	}

	private void renderNavigation(GuiGraphics graphics) {
		HgnSurface.panel(graphics, layout.navigation(), false);
		HgnTypography.fitted(graphics, font, "HGN / LIBRARY", layout.navigation().x() + 8,
			layout.refresh().bottom() + 13, layout.navigation().width() - 16,
			0.78f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "Installed mods are read from the current",
			layout.navigation().x() + 8, layout.refresh().bottom() + 29,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "installation. Choose exact versions in",
			layout.navigation().x() + 8, layout.refresh().bottom() + 42,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "the Modrinth browser and launcher.",
			layout.navigation().x() + 8, layout.refresh().bottom() + 55,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
	}

	private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int row = 0; row < rowButtons.size(); row++) {
			String fileName = filtered.get(page * layout.rowsPerPage() + row).fileName();
			HgnRect bounds = layout.row(row);
			boolean highlighted = fileName.equals(selectedFile)
				|| rowButtons.get(row).isHoveredOrFocused();
			HgnSurface.dashboardInset(graphics, bounds, highlighted);
			int iconSize = layout.compact() ? 9 : 14;
			HgnIcon.MODS.draw(graphics, bounds.x() + 4,
				bounds.y() + (bounds.height() - iconSize) / 2, iconSize, HgnPalette.CYAN);
			int badge = layout.compact() ? 0 : 52;
			HgnTypography.fitted(graphics, font, fileName, bounds.x() + iconSize + 9,
				bounds.y() + (layout.compact() ? 1 : 5),
				bounds.width() - iconSize - badge - 15,
				layout.compact() ? 0.76f : 0.91f,
				highlighted ? HgnPalette.CYAN : HgnPalette.TEXT);
			if (!layout.compact()) HgnTypography.fitted(graphics, font, "INSTALLED",
				bounds.right() - badge, bounds.y() + 6, badge - 4,
				0.65f, HgnPalette.SUCCESS);
		}
		if (!filtered.isEmpty()) return;
		if (showsEmptyDiscoveryHero()) {
			renderEmptyDiscoveryHero(graphics);
			return;
		}
		HgnRect empty = new HgnRect(layout.list().x(), layout.list().y(),
			layout.list().width(), Math.min(layout.list().height(), layout.compact() ? 47 : 80));
		HgnSurface.dashboardInset(graphics, empty, false);
		String title = !snapshot.problem().isEmpty() ? "MOD FOLDER UNAVAILABLE"
			: snapshot.jars().isEmpty() ? "NO MOD JARS INSTALLED" : "NO MATCHING MODS";
		String detail = !snapshot.problem().isEmpty() ? snapshot.problem()
			: snapshot.jars().isEmpty() ? "Browse compatible mods to add one via HGN Launcher."
			: "Try another filename or clear the search.";
		HgnTypography.fitted(graphics, font, title, empty.x() + 8, empty.y() + 8,
			empty.width() - 16, layout.compact() ? 0.76f : 0.97f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, detail, empty.x() + 8,
			empty.y() + (layout.compact() ? 22 : 31), empty.width() - 16,
			layout.compact() ? 0.63f : 0.76f, HgnPalette.mutedText());
	}

	private void renderEmptyDiscoveryHero(GuiGraphics graphics) {
		HgnRect hero = layout.emptyDiscoveryHero();
		HgnSurface.dashboardInset(graphics, hero, true);
		if (layout.emptyDiscoveryStacked()) {
			int artHeight = 75;
			HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
				hero.x() + 2, hero.y() + 2, hero.width() - 4, artHeight,
				0, 170, 1672, 390, 1672, 941);
			HgnDraw.gradient(graphics, hero.x() + 2, hero.y() + 43,
				hero.right() - 2, hero.y() + artHeight + 2,
				0x00071722, 0xF0071722);
			HgnTypography.fittedHeading(graphics, font, "EXPLORE MODS",
				hero.x() + 12, hero.y() + 82, hero.width() - 24,
				1.18f, HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font,
				"No mod JARs here yet. Choose an exact compatible release.",
				hero.x() + 12, hero.y() + 108, hero.width() - 24,
				0.74f, HgnPalette.TEXT_MUTED);
			return;
		}
		HgnDraw.textureStretch(graphics, HgnSurface.GATEWAY_SCENE,
			hero.x() + hero.width() / 2, hero.y() + 2,
			hero.width() / 2 - 2, hero.height() - 4, 1672, 941);
		HgnDraw.rect(graphics, hero.x() + 2, hero.y() + 2,
			hero.x() + hero.width() / 2 + 2, hero.bottom() - 2, 0xDB071722);
		int textWidth = hero.width() / 2 - 24;
		HgnTypography.fittedHeading(graphics, font, "EXPLORE MODS", hero.x() + 12,
			hero.y() + 13, textWidth, 1.18f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "No mod JARs in this installation yet.",
			hero.x() + 12, hero.y() + 43, textWidth, 0.78f, HgnPalette.TEXT_MUTED);
		HgnTypography.fitted(graphics, font, "Find releases for your exact game version.",
			hero.x() + 12, hero.y() + 61, textWidth, 0.74f, HgnPalette.TEXT_MUTED);
	}

	private void renderDetail(GuiGraphics graphics) {
		HgnSurface.panel(graphics, layout.detail(), false);
		int x = layout.detail().x() + 8;
		int w = layout.detail().width() - 16;
		HgnTypography.fitted(graphics, font, "MOD DETAILS", x, layout.detail().y() + 9,
			w, 1.02f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "EXACT INSTALLED JAR", x,
			layout.detail().y() + 29, w, 0.68f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "TARGET", x, layout.detail().y() + 77,
			w, 0.69f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font,
			HgnPlatform.get().loaderName().toUpperCase(Locale.ROOT) + "  /  Minecraft "
				+ HgnClient.minecraftVersion(), x, layout.detail().y() + 91,
			w, 0.78f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "SOURCE", x, layout.detail().y() + 121,
			w, 0.69f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "This installation's mods folder", x,
			layout.detail().y() + 135, w, 0.74f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "Version and dependencies cannot be",
			x, layout.detail().y() + 159, w, 0.72f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "verified from a JAR filename alone.",
			x, layout.detail().y() + 172, w, 0.72f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "Use Discover to choose an exact",
			x, layout.manage().y() - 37, w, 0.72f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "compatible release to review.",
			x, layout.manage().y() - 24, w, 0.72f, HgnPalette.mutedText());
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
