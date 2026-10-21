package online.horizongate.hgn.screen;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnScaledEditBox;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnModCatalogLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Read-only Modrinth browser for the Minecraft version and loader of this client. */
public final class HgnModCatalogScreen extends Screen {
	private static final int SEARCH_BATCH_SIZE = 20;
	private final Screen parent;
	private final HgnModCatalog catalog = new HgnModCatalog();
	private final String gameVersion = HgnClient.minecraftVersion();
	private final String loader = HgnPlatform.get().loaderName().toLowerCase(Locale.ROOT);
	private HgnModCatalogLayout layout;
	private EditBox searchBox;
	private String searchText = "";
	private List<HgnModCatalog.Project> projects = List.of();
	private List<HgnModCatalog.Release> releases = List.of();
	private HgnModCatalog.Project selectedProject;
	private HgnModCatalog.Release selectedRelease;
	private int totalHits;
	private int projectOffset;
	private int projectPage;
	private int releasePage;
	private int searchTicket;
	private int releaseTicket;
	private int searchDelay;
	private boolean initialized;
	private boolean searchLoading;
	private boolean releaseLoading;
	private String searchError = "";
	private String releaseError = "";

	public HgnModCatalogScreen(Screen parent) {
		super(Component.literal("HGN Mod Catalogue"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rebuild();
		if (!initialized) {
			initialized = true;
			search(0);
		}
	}

	@Override
	public void tick() {
		if (searchDelay > 0 && --searchDelay == 0) search(0);
	}

	private void rebuild() {
		boolean focusSearch = searchBox != null && searchBox.isFocused();
		clearWidgets();
		layout = HgnModCatalogLayout.resolve(width, height);
		projectPage = Math.min(projectPage,
			Math.max(0, (projects.size() - 1) / layout.projectRowsPerPage()));
		releasePage = Math.min(releasePage,
			Math.max(0, (releases.size() - 1) / layout.releaseRowsPerPage()));
		searchBox = null;
		if (!layout.compact() || selectedProject == null) {
			HgnRect searchBounds = layout.search();
			searchBox = addRenderableWidget(new HgnScaledEditBox(font, searchBounds.x() + 4,
				searchBounds.y() + 1, searchBounds.width() - 8, searchBounds.height() - 2,
				Component.literal("Search compatible Modrinth mods"),
				layout.compact() ? 0.8f : 0.92f));
			searchBox.setMaxLength(80);
			searchBox.setBordered(false);
			searchBox.setHint(HgnCompat.uiText(layout.compact() ? "Search compatible mods..."
				: "Search compatible Modrinth mods..."));
			searchBox.setValue(searchText);
			searchBox.setResponder(value -> {
				if (value.equals(searchText)) return;
				searchText = value;
				searchDelay = 12;
			});
			if (focusSearch) searchBox.setFocused(true);
			button(layout.searchAction(), "SEARCH", () -> search(0));
		}
		if (!layout.compact() || selectedProject == null) addProjectControls();
		if (!layout.compact() || selectedProject != null) addReleaseControls();
		navButton(layout.back(), layout.compact() && selectedProject != null
			? "RESULTS" : "BACK", HgnIcon.BACK, false, () -> {
			if (layout.compact() && selectedProject != null) {
				selectedProject = null;
				selectedRelease = null;
				releaseTicket++;
				rebuild();
			} else onClose();
		});
		navButton(layout.installed(), "INSTALLED", HgnIcon.MODS, false, this::onClose);
		navButton(layout.browse(), "DISCOVER", HgnIcon.SEARCH, true, () -> {
			if (layout.compact() && selectedProject != null) {
				selectedProject = null;
				selectedRelease = null;
				releaseTicket++;
				rebuild();
			}
		});
		navButton(layout.openLauncher(), "LAUNCHER", HgnIcon.LINK, false,
			() -> HgnCompat.openUrl(HgnModsLauncherLink.fromEnvironment()));
		String reviewLink = selectionLink();
		String reviewLabel = selectedRelease == null ? "CHOOSE VERSION"
			: reviewLink == null ? layout.compact() ? "START IN LAUNCHER"
				: "START FROM LAUNCHER TO REVIEW" : "REVIEW IN LAUNCHER";
		HgnButton review = button(layout.review(), reviewLabel, this::reviewSelection);
		review.active = reviewLink != null && !releaseLoading;
	}

	private void addProjectControls() {
		List<HgnModCatalog.Project> visible = visibleProjects();
		for (int row = 0; row < visible.size(); row++) {
			HgnModCatalog.Project project = visible.get(row);
			HgnRect bounds = layout.projectRow(row);
			addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(), Component.literal("Show versions for " + project.title()),
				ignored -> selectProject(project)));
		}
		HgnButton previous = button(layout.projectPrevious(), "<", () -> {
			if (projectPage > 0) {
				projectPage--;
				rebuild();
			} else search(Math.max(0, projectOffset - SEARCH_BATCH_SIZE), true);
		});
		previous.active = !searchLoading && (projectPage > 0 || projectOffset > 0);
		HgnButton next = button(layout.projectNext(), ">", () -> {
			if ((projectPage + 1) * layout.projectRowsPerPage() < projects.size()) {
				projectPage++;
				rebuild();
			} else search(projectOffset + SEARCH_BATCH_SIZE);
		});
		next.active = !searchLoading && ((projectPage + 1) * layout.projectRowsPerPage()
			< projects.size() || projectOffset + SEARCH_BATCH_SIZE < totalHits
				&& projectOffset + SEARCH_BATCH_SIZE <= 5_000);
	}

	private void addReleaseControls() {
		int first = releasePage * layout.releaseRowsPerPage();
		int last = Math.min(releases.size(), first + layout.releaseRowsPerPage());
		for (int index = first; index < last; index++) {
			HgnModCatalog.Release release = releases.get(index);
			HgnRect bounds = layout.releaseRow(index - first);
			addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(), Component.literal("Select " + release.label()),
				ignored -> {
					selectedRelease = release;
					rebuild();
				}));
		}
		if (selectedProject != null && !releaseError.isEmpty()) {
			button(layout.releaseRow(0), "RETRY RELEASES", () -> selectProject(selectedProject));
		}
		HgnButton previous = button(layout.releasePrevious(), "<", () -> {
			releasePage--;
			rebuild();
		});
		previous.active = releasePage > 0;
		HgnButton next = button(layout.releaseNext(), ">", () -> {
			releasePage++;
			rebuild();
		});
		next.active = (releasePage + 1) * layout.releaseRowsPerPage() < releases.size();
	}

	private HgnButton button(HgnRect bounds, String label, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), false,
			ignored -> action.run()).referenceScale(layout.compact() ? 0.82f : 0.9f));
	}

	private HgnButton navButton(HgnRect bounds, String label, HgnIcon icon,
		boolean primary, Runnable action) {
		if (layout.compact()) return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(),
			bounds.y(), bounds.width(), bounds.height(), Component.literal(label), primary,
			ignored -> action.run()).referenceScale(0.82f));
		return addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), icon, primary,
			ignored -> action.run()).referenceScale(0.82f, 0.74f));
	}

	private void search(int offset) {
		search(offset, false);
	}

	private void search(int offset, boolean lastPage) {
		searchDelay = 0;
		projectOffset = offset;
		projectPage = 0;
		searchLoading = true;
		searchError = "";
		projects = List.of();
		totalHits = 0;
		selectedProject = null;
		selectedRelease = null;
		releases = List.of();
		releaseLoading = false;
		releaseError = "";
		releasePage = 0;
		releaseTicket++;
		int ticket = ++searchTicket;
		String query = searchText;
		Minecraft client = minecraft;
		rebuild();
		CompletableFuture.supplyAsync(() -> {
			try {
				return new SearchOutcome(catalog.search(query, gameVersion, loader, offset), "");
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				return new SearchOutcome(null, "Catalogue request interrupted. Try again.");
			} catch (IOException error) {
				return new SearchOutcome(null, failureMessage(error));
			} catch (RuntimeException error) {
				return new SearchOutcome(null, "Catalogue search failed. Try again.");
			}
		}).thenAccept(outcome -> client.execute(() -> {
			if (client.screen != this || ticket != searchTicket) return;
			searchLoading = false;
			searchError = outcome.error();
			if (outcome.result() != null) {
				projects = outcome.result().projects();
				totalHits = outcome.result().totalHits();
				if (lastPage) projectPage = Math.max(0,
					(projects.size() - 1) / layout.projectRowsPerPage());
			}
			rebuild();
		}));
	}

	private void selectProject(HgnModCatalog.Project project) {
		selectedProject = project;
		selectedRelease = null;
		releases = List.of();
		releasePage = 0;
		releaseError = "";
		releaseLoading = true;
		int ticket = ++releaseTicket;
		Minecraft client = minecraft;
		rebuild();
		CompletableFuture.supplyAsync(() -> {
			try {
				return new ReleaseOutcome(catalog.releases(project.id(), gameVersion, loader), "");
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				return new ReleaseOutcome(null, "Release request interrupted. Try again.");
			} catch (IOException error) {
				return new ReleaseOutcome(null, failureMessage(error));
			} catch (RuntimeException error) {
				return new ReleaseOutcome(null, "Could not load releases. Try again.");
			}
		}).thenAccept(outcome -> client.execute(() -> {
			if (client.screen != this || ticket != releaseTicket || selectedProject == null
				|| !selectedProject.id().equals(project.id())) return;
			releaseLoading = false;
			releaseError = outcome.error();
			if (outcome.releases() != null) {
				releases = outcome.releases();
				selectedRelease = null;
			}
			rebuild();
		}));
	}

	private void reviewSelection() {
		String link = selectionLink();
		if (link != null) HgnCompat.openUrl(link);
	}

	private String selectionLink() {
		return selectedProject == null || selectedRelease == null ? null
			: HgnModsLauncherLink.fromEnvironmentSelection(selectedProject.id(), selectedRelease.id());
	}

	private List<HgnModCatalog.Project> visibleProjects() {
		int first = projectPage * layout.projectRowsPerPage();
		return projects.subList(first,
			Math.min(projects.size(), first + layout.projectRowsPerPage()));
	}

	private static String failureMessage(IOException error) {
		String message = error.getMessage();
		return message == null || message.isBlank()
			? "Could not reach Modrinth. Check the connection and retry."
			: message;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		renderHeader(graphics);
		if (!layout.compact()) renderNavigation(graphics);
		if (layout.compact()) {
			if (selectedProject == null) renderProjects(graphics, mouseX, mouseY);
			else renderReleases(graphics, mouseX, mouseY);
		} else {
			renderProjects(graphics, mouseX, mouseY);
			renderReleases(graphics, mouseX, mouseY);
		}
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
		HgnTypography.fitted(graphics, font,
			loader.toUpperCase(Locale.ROOT) + "  /  " + gameVersion,
			header.right() - 157, header.y() + 7, 150, 0.75f, HgnPalette.CYAN);
	}

	private void renderNavigation(GuiGraphics graphics) {
		HgnSurface.panel(graphics, layout.navigation(), false);
		HgnTypography.fitted(graphics, font, "HGN / DISCOVER",
			layout.navigation().x() + 8, layout.openLauncher().bottom() + 13,
			layout.navigation().width() - 16, 0.78f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, "Modrinth results are filtered to",
			layout.navigation().x() + 8, layout.openLauncher().bottom() + 29,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "your active Minecraft version",
			layout.navigation().x() + 8, layout.openLauncher().bottom() + 42,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
		HgnTypography.fitted(graphics, font, "and mod loader.", layout.navigation().x() + 8,
			layout.openLauncher().bottom() + 55,
			layout.navigation().width() - 16, 0.67f, HgnPalette.mutedText());
	}

	private void renderProjects(GuiGraphics graphics, int mouseX, int mouseY) {
		HgnRect panel = layout.projectsPanel();
		HgnSurface.panel(graphics, panel, false);
		HgnTypography.fitted(graphics, font, layout.compact() ? "DISCOVER MODS"
			: "MODS  /  DISCOVER", panel.x() + 7, panel.y() + 6,
			panel.width() - 14, layout.compact() ? 0.88f : 1.18f,
			HgnPalette.CYAN);
		if (!layout.compact()) HgnTypography.fitted(graphics, font,
			"Modrinth  /  " + loader.toUpperCase(Locale.ROOT) + "  /  Minecraft "
				+ gameVersion, panel.x() + 7, panel.y() + 24,
			panel.width() - 14, 0.72f, HgnPalette.mutedText());
		HgnSurface.dashboardInset(graphics, layout.search(), false);
		List<HgnModCatalog.Project> visible = visibleProjects();
		for (int row = 0; row < visible.size(); row++) {
			HgnModCatalog.Project project = visible.get(row);
			HgnRect bounds = layout.projectRow(row);
			boolean selected = selectedProject != null && selectedProject.id().equals(project.id());
			HgnSurface.dashboardInset(graphics, bounds, selected || bounds.contains(mouseX, mouseY));
			HgnIcon.MODS.draw(graphics, bounds.x() + 4,
				bounds.y() + (layout.compact() ? 2 : 4),
				layout.compact() ? 9 : 14, HgnPalette.CYAN);
			int textX = bounds.x() + (layout.compact() ? 17 : 23);
			HgnTypography.fitted(graphics, font, project.title(), textX,
				bounds.y() + (layout.compact() ? 1 : 2), bounds.right() - textX - 5,
				layout.compact() ? 0.76f : 0.9f,
				selected ? HgnPalette.CYAN : HgnPalette.TEXT);
			if (!layout.compact() && !project.author().isEmpty()) HgnTypography.fitted(
				graphics, font, "by " + project.author(), textX,
				bounds.y() + 12, bounds.right() - textX - 5,
				0.68f, HgnPalette.mutedText());
		}
		if (visible.isEmpty()) {
			HgnRect empty = new HgnRect(layout.projectList().x(), layout.projectList().y(),
				layout.projectList().width(), Math.min(layout.projectList().height(),
					layout.compact() ? 40 : 70));
			HgnSurface.dashboardInset(graphics, empty, false);
			HgnTypography.fitted(graphics, font,
				searchLoading ? "SEARCHING MODRINTH"
					: !searchError.isEmpty() ? "CATALOGUE UNAVAILABLE"
					: totalHits == 0 ? "NO COMPATIBLE MODS"
					: "NO READABLE RESULTS", empty.x() + 7,
				empty.y() + 7, empty.width() - 14,
				layout.compact() ? 0.76f : 0.96f,
				searchError.isEmpty() ? HgnPalette.CYAN : HgnPalette.DANGER);
			HgnTypography.fitted(graphics, font,
				!searchError.isEmpty() ? searchError
					: searchLoading ? "Checking compatible listings..."
					: "Try a different search term.", empty.x() + 7,
				empty.y() + (layout.compact() ? 21 : 30), empty.width() - 14,
				layout.compact() ? 0.63f : 0.76f, HgnPalette.mutedText());
		}
		HgnTypography.fitted(graphics, font,
			searchLoading ? "LOADING" : totalHits + " RESULTS",
			panel.x() + 7, layout.projectPrevious().y() + (layout.compact() ? 2 : 4),
			layout.projectPrevious().x() - panel.x() - 10,
			layout.compact() ? 0.68f : 0.77f, HgnPalette.mutedText());
	}

	private void renderReleases(GuiGraphics graphics, int mouseX, int mouseY) {
		HgnRect panel = layout.releasesPanel();
		HgnSurface.panel(graphics, panel, false);
		int textX = panel.x() + (layout.compact() ? 5 : 8);
		int textWidth = panel.width() - (layout.compact() ? 10 : 16);
		HgnTypography.fitted(graphics, font, layout.compact() ? "CHOOSE MOD VERSION"
			: "MOD DETAILS", textX, panel.y() + (layout.compact() ? 5 : 9),
			textWidth, layout.compact() ? 0.86f : 1.02f, HgnPalette.CYAN);
		if (selectedProject != null) {
			HgnTypography.fitted(graphics, font, selectedProject.title(), textX,
				panel.y() + (layout.compact() ? 20 : 28), textWidth,
				layout.compact() ? 0.82f : 1.0f, HgnPalette.TEXT);
			if (!layout.compact()) {
				HgnTypography.fitted(graphics, font,
					selectedProject.author().isEmpty() ? selectedProject.description()
						: "by " + selectedProject.author(), textX, panel.y() + 44,
					textWidth, 0.73f, HgnPalette.mutedText());
				HgnTypography.fitted(graphics, font,
					loader.toUpperCase(Locale.ROOT) + " / Minecraft " + gameVersion,
					textX, panel.y() + 59, textWidth, 0.72f, HgnPalette.CYAN);
				HgnTypography.fitted(graphics, font, selectedRelease == null
					? "Select an exact version below"
					: "SELECTED  " + selectedRelease.versionNumber(),
					textX, panel.y() + 76, textWidth, 0.7f,
					selectedRelease == null ? HgnPalette.mutedText() : HgnPalette.SUCCESS);
			} else HgnTypography.fitted(graphics, font,
				loader.toUpperCase(Locale.ROOT) + " / Minecraft " + gameVersion,
				textX, panel.y() + 32, textWidth, 0.66f, HgnPalette.mutedText());
		} else {
			HgnTypography.fitted(graphics, font, "SELECT A MOD", textX,
				panel.y() + 28, textWidth, 0.9f, HgnPalette.TEXT);
			if (!layout.compact()) HgnTypography.fitted(graphics, font,
				"Choose a result to load releases for the current game and loader.",
				textX, panel.y() + 47, textWidth, 0.73f, HgnPalette.mutedText());
		}
		int first = releasePage * layout.releaseRowsPerPage();
		int last = Math.min(releases.size(), first + layout.releaseRowsPerPage());
		for (int index = first; index < last; index++) {
			HgnModCatalog.Release release = releases.get(index);
			HgnRect bounds = layout.releaseRow(index - first);
			boolean selected = selectedRelease != null && selectedRelease.id().equals(release.id());
			HgnSurface.dashboardInset(graphics, bounds, selected || bounds.contains(mouseX, mouseY));
			HgnTypography.fitted(graphics, font, release.versionNumber(), bounds.x() + 5,
				bounds.y() + (layout.compact() ? 2 : 2),
				bounds.width() - (layout.compact() ? 62 : 10),
				layout.compact() ? 0.75f : 0.86f,
				selected ? HgnPalette.CYAN : HgnPalette.TEXT);
			String channel = release.channel().toUpperCase(Locale.ROOT);
			if (layout.compact()) HgnTypography.fitted(graphics, font, channel,
				bounds.right() - 51, bounds.y() + 2, 47, 0.63f,
				selected ? HgnPalette.SUCCESS : HgnPalette.mutedText());
			else HgnTypography.fitted(graphics, font, channel
				+ (release.publishedAt().isEmpty() ? "" : "  /  "
					+ release.publishedAt().substring(0,
						Math.min(10, release.publishedAt().length()))),
				bounds.x() + 5, bounds.y() + 13, bounds.width() - 10,
				0.66f, selected ? HgnPalette.SUCCESS : HgnPalette.mutedText());
		}
		if (first == last) {
			HgnRect empty = new HgnRect(layout.releaseList().x(), layout.releaseList().y(),
				layout.releaseList().width(),
				Math.min(layout.releaseList().height(), layout.compact() ? 34 : 57));
			HgnSurface.dashboardInset(graphics, empty, false);
			HgnTypography.fitted(graphics, font,
				selectedProject == null ? "Choose a search result."
					: releaseLoading ? "Loading compatible versions..."
					: !releaseError.isEmpty() ? releaseError
					: "No listed compatible versions.",
				empty.x() + 5, empty.y() + 7, empty.width() - 10,
				layout.compact() ? 0.68f : 0.78f,
				releaseError.isEmpty() ? HgnPalette.mutedText() : HgnPalette.DANGER);
		}
		HgnTypography.fitted(graphics, font,
			releaseLoading ? "LOADING" : releases.size() + " RELEASES",
			panel.x() + (layout.compact() ? 5 : 8),
			layout.releasePrevious().y() + (layout.compact() ? 2 : 4),
			layout.releasePrevious().x() - panel.x() - 10,
			layout.compact() ? 0.68f : 0.75f, HgnPalette.mutedText());
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private record SearchOutcome(HgnModCatalog.SearchResult result, String error) {
	}

	private record ReleaseOutcome(List<HgnModCatalog.Release> releases, String error) {
	}
}
