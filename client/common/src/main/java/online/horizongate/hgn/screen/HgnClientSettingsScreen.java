package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.HgnEntityRenderBudget;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceSettingsLayout;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSettingsCategory;
import online.horizongate.hgn.ui.HgnSettingsLayout;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

import java.util.ArrayList;
import java.util.List;

/** Player-facing controls for HGN presentation and bounded render quality. */
public final class HgnClientSettingsScreen extends Screen {
	private final Screen parent;
	private HgnSettingsCategory section = HgnSettingsCategory.VIDEO;
	private int page;
	private HgnSettingsLayout layout;
	private HgnDashboardLayout canvas;
	private HgnReferenceShellLayout shell;
	private HgnReferenceSettingsLayout reference;
	private HgnButton accountButton;
	private final List<HgnButton> categoryButtons = new ArrayList<>();
	private final List<HgnButton> rowButtons = new ArrayList<>();
	private static final HgnIcon[] REFERENCE_TAB_ICONS = {HgnIcon.HUD, HgnIcon.EFFECTS,
		HgnIcon.PLAY, HgnIcon.HUD, HgnIcon.SETTINGS, HgnIcon.PERFORMANCE,
		HgnIcon.SERVERS, HgnIcon.COSMETICS, HgnIcon.ACCESSIBILITY, HgnIcon.SETTINGS};
	private static final String[] SETTING_TITLES = {"Show HGN HUD", "HUD size", "Interface motion",
		"Reduced motion", "Scene particles", "Effect quality", "High contrast", "Hotbar & boss frames",
		"Performance readout", "World information", "Show coordinates", "Crowded drops & XP",
		"Experimental HGN hotbar"};
	private static final String[] SETTING_DETAILS = {"Show HGN gameplay readouts and frames.",
		"Cycle through compact, standard and large.", "Allow motion in supported HGN effects.",
		"Limit motion even when animations are on.", "Allow decorative HGN scene particles.",
		"Cycle the detail budget for HGN effects.", "Brighten HGN borders and help text.",
		"Frame the hotbar and boss health bars.", "Show FPS, ping and clicks per second.",
		"Show game mode, client version and biome.", "Turn off to hide your position on stream.",
		"Reduce distant drop and XP drawing in crowds; entities stay loaded.",
		"Replace the hotbar frame while keeping live Minecraft items and indicators."};
	private static final String[] ACTION_TITLES = {"Minecraft video settings", "Minecraft sound settings",
		"Minecraft controls", "Multiplayer servers", "HGN cosmetics",
		"Accessibility & language", "Minecraft key bindings"};
	private static final String[] ACTION_DETAILS = {"Open the game's real graphics and display options.",
		"Open the game's real volume and sound controls.", "Open the game's real input controls.",
		"Browse saved servers and join multiplayer.", "Browse and equip account-owned cosmetics.",
		"Open the game's accessibility and language options.", "Edit the game's real key mappings."};

	public HgnClientSettingsScreen(Screen parent) {
		super(Component.literal("HGN Client Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rebuild();
	}

	private void rebuild() {
		rebuild(-1);
	}

	private void rebuild(int focusEntry) {
		clearWidgets();
		categoryButtons.clear();
		rowButtons.clear();
		if (HgnReferenceSettingsLayout.fits(width, height)) {
			canvas = HgnDashboardLayout.resolve(width, height);
			shell = new HgnReferenceShellLayout(canvas);
			reference = new HgnReferenceSettingsLayout(canvas);
			layout = reference.controls(section.entryCount());
		} else {
			canvas = null;
			shell = null;
			reference = null;
			layout = HgnSettingsLayout.resolveCategories(width, height, section.entryCount());
		}
		page = Math.max(0, Math.min(page, layout.pages(section.entryCount()) - 1));
		for (HgnSettingsCategory category : HgnSettingsCategory.values()) {
			boolean abbreviated = reference == null && layout.tabs().width() < 450;
			Runnable select = () -> { section = category; page = 0; rebuild();
				setFocused(children().get(category.ordinal())); };
			HgnButton tab;
			if (reference != null) {
				HgnRect bounds = reference.categoryTab(category.ordinal());
				tab = addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
					bounds.width(), bounds.height(), Component.literal(category.label()),
					ignored -> select.run()));
				categoryButtons.add(tab);
			} else {
				tab = button(layout.categoryTab(category.ordinal()),
					abbreviated ? category.compactLabel() : category.label(), section == category, select);
			}
			if (abbreviated) tab.setTooltip(Tooltip.create(Component.literal(category.label())));
		}
		int first = page * layout.rowsPerPage();
		for (int row = 0; row < layout.rowsPerPage() && first + row < section.entryCount(); row++) {
			int entry = section.entryAt(first + row);
			HgnRect bounds = layout.row(row);
			HgnButton control = addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(), Component.literal(entryTitle(entry) + ": "
					+ entryLabel(entry) + ". " + entryDetail(entry)), ignored -> activateEntry(entry)));
			rowButtons.add(control);
			if (entry == focusEntry) setFocused(control);
		}
		int pages = layout.pages(section.entryCount());
		if (pages > 1) {
			button(layout.categoryPageButton(false), "Previous", false,
				() -> { page--; rebuild(); focusFirstRow(); }).active = page > 0;
			button(layout.categoryPageButton(true), "Next", false,
				() -> { page++; rebuild(); focusFirstRow(); }).active = page + 1 < pages;
		}
		if (reference != null) {
			button(layout.footerButton(0, 3), "Video settings", false,
				() -> minecraft.setScreen(new HgnVideoSettingsScreen(this, minecraft, minecraft.options)));
			button(layout.footerButton(1, 3), "Minecraft settings", false,
				() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
			button(layout.footerButton(2, 3), "Done", true, this::onClose);
		} else {
			button(layout.footerButton(0, 2), "Minecraft settings", false,
				() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
			button(layout.footerButton(1, 2), "Done", true, this::onClose);
		}
		if (reference != null) rebuildReferenceWidgets();
	}

	private void rebuildReferenceWidgets() {
		String[] names = {"PLAY", "SINGLEPLAYER", "MULTIPLAYER", "SERVERS",
			"COSMETICS", "MODS", "SETTINGS", "EXIT"};
		HgnIcon[] icons = {HgnIcon.PLAY, HgnIcon.WORLD, HgnIcon.MULTIPLAYER,
			HgnIcon.SERVERS, HgnIcon.COSMETICS, HgnIcon.MODS, HgnIcon.SETTINGS, HgnIcon.EXIT};
		Runnable[] actions = {
			() -> minecraft.setScreen(new HgnMultiplayerScreen(this)),
			() -> minecraft.setScreen(HgnSelectWorldScreen.open(this)),
			() -> minecraft.setScreen(new HgnSavedServersScreen(this)),
			() -> minecraft.setScreen(new HgnMultiplayerScreen(this)),
			() -> minecraft.setScreen(new HgnCosmeticsScreen(this)),
			() -> minecraft.setScreen(new HgnInstalledModsScreen(this)),
			() -> { },
			() -> HgnCompat.quit(minecraft)
		};
		for (int index = 0; index < names.length; index++) {
			int choice = index;
			HgnRect bounds = shell.destination(index);
			addRenderableWidget(HgnButton.dashboardCommand(bounds.x(), bounds.y(), bounds.width(),
				bounds.height(), Component.literal(names[index]), icons[index], index == 6,
				ignored -> actions[choice].run()).referenceScale(canvas.scale()));
		}
		HgnRect account = reference.account();
		accountButton = addRenderableWidget(HgnButton.hitTarget(account.x(), account.y(),
			account.width(), account.height(), Component.literal("Open HGN Launcher account"),
			ignored -> HgnCompat.openUrl("hgn://account?playerId=" + minecraft.getUser().getProfileId())));
		HgnRect model = reference.playerPreviewModel();
		addRenderableWidget(HgnCompat.profileWidget(minecraft, model.x(), model.y(),
			model.width(), model.height()));
		HgnRect accessibility = canvas.rect(698, 410, 120, 27);
		addRenderableWidget(HgnButton.dashboardCentered(accessibility.x(), accessibility.y(),
			accessibility.width(), accessibility.height(), Component.literal("ACCESSIBILITY"),
			false, ignored -> minecraft.setScreen(new HgnAccessibilityScreen(this)))
			.referenceScale(canvas.scale()));
	}

	private void focusFirstRow() {
		if (!rowButtons.isEmpty()) setFocused(rowButtons.getFirst());
	}

	private HgnButton button(HgnRect bounds, String label, boolean selected, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), selected, ignored -> action.run())
			.referenceScale(canvas == null ? 1.0f : canvas.scale()));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (reference != null) {
			renderReference(graphics, mouseX, mouseY, partialTick);
			return;
		}
		HgnSurface.panel(graphics, layout.panel(), false);
		int left = layout.content().x();
		int lane = layout.content().width();
		HgnTypography.fittedHeading(graphics, font,
			layout.compact() ? "HGN SETTINGS / AUTO SAVE" : "HGN / CLIENT SETTINGS",
			left, layout.headerTitleY(), lane, 1.2f, HgnPalette.TEXT);
		if (!layout.compact()) text(graphics, "Saved automatically to this profile.", left,
			layout.panel().y() + 34, lane, 0.95f, HgnPalette.mutedText());
		int pages = layout.pages(section.entryCount());
		HgnTypography.fittedHeading(graphics, font, section.title(), left, layout.sectionTitleY(),
			lane - (pages > 1 ? 154 : 0), 1.05f, HgnPalette.CYAN);
		if (pages > 1) HgnTypography.centered(graphics, font, (page + 1) + " / " + pages,
			layout.content().right() - 66, layout.sectionTitleY(), HgnPalette.TEXT);
		for (int row = 0; row < rowButtons.size(); row++) {
			int entry = section.entryAt(page * layout.rowsPerPage() + row);
			HgnRect bounds = layout.row(row);
			HgnSurface.dashboardInset(graphics, bounds, rowButtons.get(row).isHoveredOrFocused());
			int textWidth = bounds.width() - 122;
			text(graphics, HgnTypography.trim(font, entryTitle(entry), textWidth),
				bounds.x() + 12, layout.rowTitleY(row), textWidth, 1.0f, HgnPalette.TEXT);
			HgnRect value = layout.settingValue(row);
			HgnSurface.control(graphics, value, 0, false, true, false, false);
			HgnTypography.centered(graphics, font, entryLabel(entry), value.centerX(),
				value.y() + (layout.compact() ? 6 : 8), HgnPalette.CYAN);
			if (layout.showsRowHelp()) text(graphics,
				HgnTypography.trim(font, entryDetail(entry), textWidth), bounds.x() + 12,
				layout.rowHelpY(row), textWidth, 0.95f, HgnPalette.mutedText());
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderReference(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnSurface.dashboardPanel(graphics, shell.rail(), false);
		HgnSurface.dashboardPanel(graphics, layout.panel(), false);
		HgnSurface.dashboardInset(graphics, reference.heading(), true);
		HgnSurface.dashboardInset(graphics, reference.preview(), false);
		HgnSurface.dashboardPanel(graphics, reference.profile(), false);
		HgnSurface.dashboardPanel(graphics, reference.systemInfo(), false);
		HgnSurface.dashboardPanel(graphics, reference.quickActions(), false);
		renderReferenceHeader(graphics);
		renderReferenceTabs(graphics);
		renderReferenceRows(graphics);
		renderReferencePreview(graphics);
		renderReferenceProfile(graphics);
		renderReferenceStatus(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		refCentered(graphics, "DRAG TO ROTATE", 758, 211, 0.7f, HgnPalette.TEXT_MUTED);
	}

	private void renderReferenceTabs(GuiGraphics graphics) {
		for (HgnSettingsCategory category : HgnSettingsCategory.values()) {
			int index = category.ordinal();
			HgnRect bounds = reference.categoryTab(index);
			HgnButton button = categoryButtons.get(index);
			boolean selected = category == section;
			HgnSurface.control(graphics, bounds, button.isHoveredOrFocused() ? 1 : 0,
				button.isFocused(), selected, false, !button.active);
			int iconSize = canvas.size(13);
			REFERENCE_TAB_ICONS[index].draw(graphics, bounds.centerX() - iconSize / 2,
				bounds.y() + canvas.size(3), iconSize,
				selected ? HgnPalette.CYAN_SOFT : HgnPalette.CYAN);
			String label = category.label();
			float fit = Math.min(canvas.scale() * 0.72f,
				Math.max(1, bounds.width() - canvas.size(3))
					/ (float) Math.max(1, HgnTypography.width(font, label)));
			HgnTypography.centered(graphics, font, label, bounds.centerX(),
				bounds.bottom() - canvas.size(10), fit,
				selected ? HgnPalette.TEXT : HgnPalette.TEXT_MUTED);
		}
	}

	private void renderReferenceHeader(GuiGraphics graphics) {
		HgnRect masthead = canvas.rect(20, 12, 384, 69);
		HgnDraw.gradient(graphics, masthead.x(), masthead.y(), masthead.right(), masthead.bottom(),
			0xC4071119, 0xA0071119);
		HgnRect mark = shell.wordmark();
		HgnSurface.wordmark(graphics, mark.x(), mark.y(), mark.width(), mark.height(), false);
		refHeading(graphics, "HORIZONGATE", 108, 27, 284, 1.62f, HgnPalette.TEXT);
		refHeading(graphics, "C L I E N T", 108, 47, 284, 1.16f, HgnPalette.CYAN);
		refFitted(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			108, 66, 260, 0.76f, HgnPalette.TEXT);
		HgnRect badge = shell.versionBadge();
		HgnSurface.dashboardInset(graphics, badge, false);
		HgnTypography.centered(graphics, font, "HGN " + HgnClient.minecraftVersion(),
			badge.centerX(), badge.y() + Math.max(4, (badge.height() - font.lineHeight) / 2),
			Math.max(0.75f, canvas.scale() * 0.86f), HgnPalette.CYAN);
		HgnRect performance = canvas.rect(622, 11, 88, 20);
		HgnSurface.dashboardInset(graphics, performance, false);
		HgnTypography.centered(graphics, font,
			minecraft.getFps() > 0 ? minecraft.getFps() + " FPS" : "MEASURING",
			performance.centerX(), performance.y() + Math.max(4, (performance.height() - font.lineHeight) / 2),
			Math.max(0.75f, canvas.scale() * 0.86f), HgnPalette.CYAN);
		HgnIcon.SETTINGS.draw(graphics, canvas.x(224), canvas.y(109), canvas.size(34), HgnPalette.CYAN);
		refHeading(graphics, "SETTINGS", 264, 105, 350, 1.5f, HgnPalette.TEXT);
		refHeading(graphics, "CUSTOMIZE YOUR EXPERIENCE", 264, 126, 350, 0.86f, HgnPalette.CYAN);
		refFitted(graphics, "HGN controls, presentation and performance. Saved automatically.",
			264, 139, 395, 0.7f, HgnPalette.TEXT_MUTED);
		refHeading(graphics, section.title().toUpperCase(), 217, 205, 210, 0.82f, HgnPalette.CYAN);
	}

	private void renderReferenceRows(GuiGraphics graphics) {
		for (int row = 0; row < rowButtons.size(); row++) {
			int entry = section.entryAt(page * layout.rowsPerPage() + row);
			HgnRect bounds = layout.row(row);
			HgnSurface.dashboardInset(graphics, bounds, rowButtons.get(row).isHoveredOrFocused());
			HgnRect value = reference.settingValue(bounds);
			HgnSurface.control(graphics, value, 0, false, true, false, false);
			HgnTypography.fitted(graphics, font, entryTitle(entry), bounds.x() + canvas.size(8),
				bounds.y() + canvas.size(5), Math.max(1, bounds.width() - canvas.size(115)),
				Math.max(0.7f, canvas.scale() * 0.82f), HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font, entryDetail(entry), bounds.x() + canvas.size(8),
				bounds.y() + canvas.size(20), Math.max(1, bounds.width() - canvas.size(115)),
				Math.max(0.64f, canvas.scale() * 0.68f), HgnPalette.TEXT_MUTED);
			HgnTypography.centered(graphics, font, entryLabel(entry), value.centerX(),
				value.y() + Math.max(3, (value.height() - font.lineHeight) / 2),
				canvas.scale() * 0.74f, HgnPalette.CYAN);
		}
	}

	private void renderReferencePreview(GuiGraphics graphics) {
		refHeading(graphics, "ILLUSTRATIVE GATEWAY ART", 454, 206, 222, 0.83f, HgnPalette.CYAN);
		HgnRect scene = reference.previewImage();
		HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
			scene.x(), scene.y(), scene.width(), scene.height(),
			160, 80, 1350, 800, 1672, 941);
		HgnSurface.cornerAccent(graphics, scene, true);
		refFitted(graphics, "CURRENT: HUD " + toggle(HgnConfig.get().hudEnabled)
			+ "  /  EFFECTS " + HgnConfig.get().sceneQuality.name(), 454, 384,
			220, 0.72f, HgnPalette.TEXT);
	}

	private void renderReferenceProfile(GuiGraphics graphics) {
		HgnSurface.dashboardInset(graphics, reference.account(),
			accountButton != null && accountButton.isHoveredOrFocused());
		HgnSurface.dashboardInset(graphics, reference.playerPreview(), false);
		HgnIcon.PROFILE.draw(graphics, canvas.x(701), canvas.y(56), canvas.size(20), HgnPalette.CYAN);
		refFitted(graphics, minecraft.getUser().getName(), 725, 56, 72, 0.94f, HgnPalette.TEXT);
		refFitted(graphics, HgnCosmetics.connectionState().compactLabel(),
			725, 70, 76, 0.72f, HgnPalette.TEXT_MUTED);
		HgnIcon.LINK.draw(graphics, canvas.x(803), canvas.y(59), canvas.size(13), HgnPalette.CYAN);
	}

	private void renderReferenceStatus(GuiGraphics graphics) {
		refHeading(graphics, "SYSTEM INFO", 697, 246, 121, 0.82f, HgnPalette.CYAN);
		HgnSurface.separator(graphics, canvas.x(697), canvas.y(263), canvas.size(121), false);
		refFitted(graphics, minecraft.getFps() > 0 ? minecraft.getFps() + " FPS" : "Measuring FPS",
			697, 272, 121, 0.78f, HgnPalette.TEXT);
		Runtime runtime = Runtime.getRuntime();
		long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
		long maximumMiB = runtime.maxMemory() / (1024L * 1024L);
		refFitted(graphics, "Memory " + usedMiB + " / " + maximumMiB + " MiB",
			697, 290, 121, 0.72f, HgnPalette.TEXT);
		refFitted(graphics, "Minecraft " + HgnClient.minecraftVersion(),
			697, 308, 121, 0.78f, HgnPalette.TEXT);
		refFitted(graphics, "HUD " + toggle(HgnConfig.get().hudEnabled)
			+ "  /  Effects " + HgnConfig.get().sceneQuality.name(),
			697, 326, 121, 0.71f, HgnPalette.TEXT);
		refFitted(graphics, HgnCosmetics.connectionState().compactLabel(),
			697, 344, 121, 0.72f, HgnPalette.CYAN);
		refHeading(graphics, "QUICK ACTIONS", 697, 386, 121, 0.82f, HgnPalette.CYAN);
		refFitted(graphics, "Changes save as you make them.",
			697, 399, 121, 0.69f, HgnPalette.TEXT_MUTED);
	}

	private void refText(GuiGraphics graphics, String value, int x, int y, float size, int color) {
		HgnTypography.text(graphics, font, value, canvas.x(x), canvas.y(y), canvas.scale() * size, color);
	}

	private void refCentered(GuiGraphics graphics, String value, int x, int y, float size, int color) {
		HgnTypography.centered(graphics, font, value, canvas.x(x), canvas.y(y), canvas.scale() * size, color);
	}

	private void refFitted(GuiGraphics graphics, String value, int x, int y,
		int width, float size, int color) {
		HgnTypography.fitted(graphics, font, value, canvas.x(x), canvas.y(y), canvas.size(width),
			canvas.scale() * size, color);
	}

	private void refHeading(GuiGraphics graphics, String value, int x, int y,
		int width, float size, int color) {
		HgnTypography.fittedHeading(graphics, font, value, canvas.x(x), canvas.y(y), canvas.size(width),
			canvas.scale() * size, color);
	}

	private void text(GuiGraphics graphics, String value, int x, int y, int width, float size, int color) {
		HgnTypography.fitted(graphics, font, value, x, y, width, size, color);
	}

	private String entryTitle(int entry) {
		return HgnSettingsCategory.isLocalSetting(entry)
			? SETTING_TITLES[entry] : ACTION_TITLES[entry - HgnSettingsCategory.LOCAL_SETTING_COUNT];
	}

	private String entryDetail(int entry) {
		return HgnSettingsCategory.isLocalSetting(entry)
			? settingDetail(entry) : ACTION_DETAILS[entry - HgnSettingsCategory.LOCAL_SETTING_COUNT];
	}

	private String entryLabel(int entry) {
		return HgnSettingsCategory.isLocalSetting(entry) ? settingLabel(entry) : "OPEN >";
	}

	private void activateEntry(int entry) {
		if (HgnSettingsCategory.isLocalSetting(entry)) {
			changeSetting(entry);
			return;
		}
		Screen destination = switch (entry) {
			case HgnSettingsCategory.VIDEO_OPTIONS -> new HgnVideoSettingsScreen(this, minecraft, minecraft.options);
			case HgnSettingsCategory.SOUND_OPTIONS -> new HgnSoundOptionsScreen(this, minecraft.options);
			case HgnSettingsCategory.CONTROLS_OPTIONS -> new HgnControlsScreen(this, minecraft.options);
			case HgnSettingsCategory.SERVER_BROWSER -> new HgnMultiplayerScreen(this);
			case HgnSettingsCategory.COSMETICS_SCREEN -> new HgnCosmeticsScreen(this);
			case HgnSettingsCategory.ACCESSIBILITY_OPTIONS -> new HgnAccessibilityScreen(this);
			case HgnSettingsCategory.KEY_BINDINGS -> new HgnKeyBindsScreen(this, minecraft.options);
			default -> throw new IllegalArgumentException("Unknown settings destination: " + entry);
		};
		minecraft.setScreen(destination);
	}

	private String settingLabel(int index) {
		HgnConfig config = HgnConfig.get();
		return switch (index) {
			case 0 -> toggle(config.hudEnabled);
			case 1 -> config.hudSize.name();
			case 2 -> toggle(config.interfaceAnimations);
			case 3 -> toggle(config.reducedMotion);
			case 4 -> toggle(config.sceneParticles);
			case 5 -> config.sceneQuality.name();
			case 6 -> toggle(config.highContrast);
			case 7 -> toggle(config.hudFrames);
			case 8 -> toggle(config.hudPerformance);
			case 9 -> toggle(config.hudContext);
			case 10 -> toggle(config.hudCoordinates);
			case 11 -> toggle(config.crowdedItemRendering);
			case 12 -> toggle(config.experimentalHgnHotbar);
			default -> "HGN";
		};
	}

	private String toggle(boolean enabled) {
		return enabled ? "ON" : "OFF";
	}

	private String settingDetail(int index) {
		if (index != 11 || !HgnConfig.get().crowdedItemRendering) return SETTING_DETAILS[index];
		long skipped = HgnEntityRenderBudget.skippedRenderChecks();
		return skipped > 0
			? "Recent: " + skipped + " distant draw checks skipped; entities stay loaded."
			: SETTING_DETAILS[index];
	}

	private void changeSetting(int index) {
		HgnConfig config = HgnConfig.get();
		switch (index) {
			case 0 -> {
				config.hudEnabled = !config.hudEnabled;
				HgnMessages.sendHudStatus(config.hudEnabled);
			}
			case 1 -> config.hudSize = next(config.hudSize);
			case 2 -> config.interfaceAnimations = !config.interfaceAnimations;
			case 3 -> config.reducedMotion = !config.reducedMotion;
			case 4 -> config.sceneParticles = !config.sceneParticles;
			case 5 -> config.sceneQuality = next(config.sceneQuality);
			case 6 -> config.highContrast = !config.highContrast;
			case 7 -> config.hudFrames = !config.hudFrames;
			case 8 -> config.hudPerformance = !config.hudPerformance;
			case 9 -> config.hudContext = !config.hudContext;
			case 10 -> config.hudCoordinates = !config.hudCoordinates;
			case 11 -> config.crowdedItemRendering = !config.crowdedItemRendering;
			case 12 -> config.experimentalHgnHotbar = !config.experimentalHgnHotbar;
			default -> { }
		}
		HgnConfig.save();
		rebuild(index);
	}

	private static HgnConfig.HudSize next(HgnConfig.HudSize value) {
		return switch (value) {
			case COMPACT -> HgnConfig.HudSize.STANDARD;
			case STANDARD -> HgnConfig.HudSize.LARGE;
			case LARGE -> HgnConfig.HudSize.COMPACT;
		};
	}

	private static HgnConfig.SceneQuality next(HgnConfig.SceneQuality value) {
		return switch (value) {
			case LOW -> HgnConfig.SceneQuality.BALANCED;
			case BALANCED -> HgnConfig.SceneQuality.HIGH;
			case HIGH -> HgnConfig.SceneQuality.LOW;
		};
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
