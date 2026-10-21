package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnVideoSettingsLayout;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Composes the original video widgets without replacing native warning, display or resource lifecycles. */
public final class HgnVideoSettingsScreen extends VideoSettingsScreen {
	private List<AbstractWidget> controls = List.of();
	private List<AbstractWidget> pinned = List.of();
	private List<AbstractWidget> preferences = List.of();
	private List<Boolean> headings = List.of();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visibleControls = new ArrayList<>();
	private final List<HgnButton> navigation = new ArrayList<>();
	private final Screen returnScreen;
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton previous;
	private HgnButton next;
	private HgnVideoSettingsLayout pageLayout;
	private int page;
	private boolean collecting;

	public HgnVideoSettingsScreen(Screen parent, Minecraft minecraft, Options options) {
		super(parent, minecraft, options);
		returnScreen = parent;
	}

	@Override
	protected void init() {
		collecting = true;
		try {
			super.init();
		} finally {
			collecting = false;
		}
		if (list == null) throw new IllegalStateException("Native video options list was not initialized");
		controls = HgnSoundOptionsScreen.optionWidgets(list);
		pinned = List.of(nativeOption(options.guiScale()), nativeOption(options.fullscreen()));
		preferences = controls.stream().filter(widget -> !pinned.contains(widget)).toList();
		headings = preferences.stream().map(widget -> widget instanceof StringWidget).toList();
		actions.clear();
		navigation.clear();
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget != list
				&& !(widget instanceof StringWidget) && !(widget instanceof HgnSettingsHeader)) actions.add(widget);
		}
		pageTitle = new StringWidget(getTitle(), font);
		pageCounter = new StringWidget(Component.empty(), font);
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		HgnCompat.leftAlignLanguageLabel(pageCounter);
		previous = HgnButton.centered(0, 0, 1, 20, Component.literal("Previous"), false, button -> showPage(page - 1));
		next = HgnButton.centered(0, 0, 1, 20, Component.literal("Next"), false, button -> showPage(page + 1));
		String[] names = {"PLAY", "SINGLEPLAYER", "MULTIPLAYER", "SERVERS",
			"COSMETICS", "MODS", "SETTINGS", "EXIT"};
		HgnIcon[] icons = {HgnIcon.PLAY, HgnIcon.WORLD, HgnIcon.MULTIPLAYER, HgnIcon.SERVERS,
			HgnIcon.COSMETICS, HgnIcon.MODS, HgnIcon.SETTINGS, HgnIcon.EXIT};
		for (int index = 0; index < names.length; index++) {
			int destination = index;
			navigation.add(HgnButton.dashboardCommand(0, 0, 1, 20,
				Component.literal(names[index]), icons[index], index == 6,
				button -> navigate(destination)));
		}
		for (int index = 0; index < controls.size(); index++) controls.get(index).setTabOrderGroup(index);
		previous.setTabOrderGroup(controls.size());
		next.setTabOrderGroup(controls.size() + 1);
		for (int index = 0; index < actions.size(); index++) actions.get(index).setTabOrderGroup(controls.size() + 2 + index);
		for (int index = 0; index < navigation.size(); index++)
			navigation.get(index).setTabOrderGroup(controls.size() + 2 + actions.size() + index);
		arrange(true);
	}

	/** Includes native section headings and any extra controls contributed to the original list. */
	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native video options list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null || !controls.contains(widget)) throw new IllegalStateException("Native video option is missing: " + option);
		return widget;
	}

	/** Pagination does not apply pending native options or invoke display/resource callbacks. */
	public void showPage(int selected) {
		page = selected;
		if (pageTitle != null) arrange(false);
	}

	public int pageCount() {
		return pageLayout == null ? 1 : pageLayout.pages();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (!collecting && pageTitle != null) arrange(true);
	}

	private void arrange(boolean retainFocusedPage) {
		GuiEventListener focused = getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		pageLayout = HgnVideoSettingsLayout.resolve(width, height, headings, page, actions.size());
		int focusedIndex = HgnVideoSettingsLayout.focusedControlIndex(preferences, focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnVideoSettingsLayout.resolve(width, height, headings, pageLayout.pageContaining(focusedIndex), actions.size());
		}
		page = pageLayout.page();
		List<AbstractWidget> previouslyVisible = List.copyOf(visibleControls);
		visibleControls.clear();
		for (var slot : pageLayout.entries()) {
			AbstractWidget control = preferences.get(slot.index());
			place(control, slot.bounds());
			visibleControls.add(control);
		}
		for (int index = 0; index < pinned.size(); index++) place(pinned.get(index), pageLayout.pinned().get(index));
		place(pageTitle, pageLayout.title());
		place(pageCounter, pageLayout.counter());
		pageCounter.setMessage(Component.literal((page + 1) + " / " + pageLayout.pages()));
		place(previous, pageLayout.previous());
		place(next, pageLayout.next());
		previous.active = page > 0;
		next.active = page + 1 < pageLayout.pages();
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
		if (pageLayout.reference()) {
			HgnDashboardLayout canvas = HgnDashboardLayout.resolve(width, height);
			HgnReferenceShellLayout shell = new HgnReferenceShellLayout(canvas);
			for (int index = 0; index < navigation.size(); index++) {
				place(navigation.get(index), shell.destination(index));
				navigation.get(index).referenceScale(canvas.scale());
			}
		}
		if (!children().contains(pageTitle)) {
			// Native list remains intact for findOption, pending values, presets and inherited callbacks.
			clearWidgets();
			setFocused(null);
			for (AbstractWidget control : pinned) addRenderableWidget(control);
			for (AbstractWidget control : visibleControls) addRenderableWidget(control);
			addRenderableWidget(previous);
			addRenderableWidget(next);
			for (AbstractWidget action : actions) addRenderableWidget(action);
			if (pageLayout.reference()) for (HgnButton button : navigation) addRenderableWidget(button);
			addRenderableWidget(pageTitle);
			addRenderableWidget(pageCounter);
		} else {
			for (AbstractWidget control : previouslyVisible) if (!visibleControls.contains(control)) removeWidget(control);
			for (AbstractWidget control : visibleControls) if (!children().contains(control)) addRenderableWidget(control);
			for (HgnButton button : navigation) {
				if (pageLayout.reference() && !children().contains(button)) addRenderableWidget(button);
				if (!pageLayout.reference() && children().contains(button)) removeWidget(button);
			}
		}
		if (focused instanceof AbstractWidget widget && children().contains(widget) && widget.active && widget.visible) {
			if (getFocused() != widget || !widget.isFocused()) setFocused(widget);
		} else if (focused != null) {
			setFocused(null);
			visibleControls.stream().filter(widget -> !(widget instanceof StringWidget) && widget.active && widget.visible)
				.findFirst().ifPresent(this::setFocused);
		}
	}

	private static void place(AbstractWidget widget, HgnRect bounds) {
		widget.setX(bounds.x());
		widget.setY(bounds.y());
		widget.setWidth(bounds.width());
		widget.setHeight(bounds.height());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (pageLayout == null) return;
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (pageLayout.reference()) {
			renderReferenceChrome(graphics);
		} else {
			HgnSurface.panel(graphics, pageLayout.header(), true);
			HgnSurface.panel(graphics, pageLayout.display(), true);
			HgnSurface.panel(graphics, pageLayout.content(), false);
			HgnSurface.panel(graphics, pageLayout.footer(), false);
			if (!pageLayout.compact()) HgnTypography.fittedHeading(graphics, font, "HGN / VIDEO",
				pageLayout.title().x(), pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
			if (pageLayout.sideBySide()) HgnTypography.fittedHeading(graphics, font, "DISPLAY",
				pageLayout.display().x() + 10, pageLayout.display().y() + 12, pageLayout.display().width() - 20, 0.85f, HgnPalette.CYAN);
		}
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : pinned) control.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visibleControls) control.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
		if (pageLayout.reference()) for (HgnButton button : navigation)
			button.render(graphics, mouseX, mouseY, partialTick);
	}

	private void navigate(int destination) {
		onClose();
		Screen next = switch (destination) {
			case 0, 3 -> new HgnMultiplayerScreen(returnScreen);
			case 1 -> HgnSelectWorldScreen.open(returnScreen);
			case 2 -> new HgnSavedServersScreen(returnScreen);
			case 4 -> new HgnCosmeticsScreen(returnScreen);
			case 5 -> new HgnInstalledModsScreen(returnScreen);
			case 6, 7 -> null;
			default -> throw new IllegalArgumentException("Unknown navigation destination: " + destination);
		};
		if (destination == 7) HgnCompat.quit(minecraft);
		else if (next != null) minecraft.setScreen(next);
	}

	private void renderReferenceChrome(GuiGraphics graphics) {
		HgnDashboardLayout canvas = HgnDashboardLayout.resolve(width, height);
		HgnReferenceShellLayout shell = new HgnReferenceShellLayout(canvas);
		HgnSurface.dashboardPanel(graphics, shell.rail(), false);
		HgnSurface.dashboardPanel(graphics, pageLayout.header(), true);
		HgnSurface.dashboardInset(graphics, pageLayout.display(), false);
		HgnSurface.dashboardPanel(graphics, pageLayout.content(), false);
		HgnSurface.dashboardPanel(graphics, pageLayout.footer(), false);
		HgnSurface.dashboardPanel(graphics, canvas.rect(689, 40, 138, 190), false);
		HgnSurface.dashboardPanel(graphics, canvas.rect(689, 237, 138, 132), false);
		HgnSurface.dashboardPanel(graphics, canvas.rect(689, 377, 138, 70), false);
		HgnSurface.wordmark(graphics, shell.wordmark().x(), shell.wordmark().y(),
			shell.wordmark().width(), shell.wordmark().height(), false);
		referenceHeading(graphics, canvas, "HORIZONGATE", 108, 27, 284, 1.62f, HgnPalette.TEXT);
		referenceHeading(graphics, canvas, "C L I E N T", 108, 47, 230, 1.16f, HgnPalette.CYAN);
		referenceText(graphics, canvas, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG", 108, 66, 260, 0.76f, HgnPalette.TEXT);
		HgnSurface.dashboardInset(graphics, shell.versionBadge(), false);
		HgnTypography.centered(graphics, font, "HGN " + HgnClient.minecraftVersion(),
			shell.versionBadge().centerX(), shell.versionBadge().y() + canvas.size(5),
			canvas.scale() * 0.8f, HgnPalette.CYAN);
		HgnIcon.SETTINGS.draw(graphics, canvas.x(216), canvas.y(106), canvas.size(31), HgnPalette.CYAN);
		referenceHeading(graphics, canvas, "SETTINGS", 252, 104, 344, 1.45f, HgnPalette.TEXT);
		referenceHeading(graphics, canvas, "VIDEO / MINECRAFT", 218, 142, 380, 0.75f, HgnPalette.CYAN);
		referenceHeading(graphics, canvas, "DISPLAY", 218, 161, 300, 0.69f, HgnPalette.CYAN);
		referenceHeading(graphics, canvas, "GRAPHICS OPTIONS", 218, 206, 300, 0.69f, HgnPalette.CYAN);
		HgnRect account = canvas.rect(695, 47, 126, 40);
		HgnSurface.dashboardInset(graphics, account, false);
		referenceText(graphics, canvas, minecraft.getUser().getName(), 703, 54, 110, 0.86f, HgnPalette.TEXT);
		referenceText(graphics, canvas, "JAVA PROFILE", 703, 71, 110, 0.68f, HgnPalette.CYAN);
		HgnRect artwork = canvas.rect(695, 94, 126, 103);
		HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
			artwork.x(), artwork.y(), artwork.width(), artwork.height(), 180, 95, 1290, 730, 1672, 941);
		HgnSurface.cornerAccent(graphics, artwork, true);
		referenceText(graphics, canvas, "STATIC HGN BACKDROP", 697, 208, 122, 0.66f, HgnPalette.TEXT_MUTED);
		referenceHeading(graphics, canvas, "VIDEO OPTIONS", 697, 247, 122, 0.78f, HgnPalette.CYAN);
		referenceText(graphics, canvas, "Minecraft owns these", 697, 270, 122, 0.7f, HgnPalette.TEXT);
		referenceText(graphics, canvas, "graphics settings.", 697, 285, 122, 0.7f, HgnPalette.TEXT);
		referenceText(graphics, canvas, "Some changes may reset", 697, 312, 122, 0.7f, HgnPalette.TEXT_MUTED);
		referenceText(graphics, canvas, "the display or resources.", 697, 327, 122, 0.7f, HgnPalette.TEXT_MUTED);
		referenceHeading(graphics, canvas, "NATIVE SAVE", 697, 387, 122, 0.78f, HgnPalette.CYAN);
		referenceText(graphics, canvas, "Done uses Minecraft's", 697, 410, 122, 0.7f, HgnPalette.TEXT);
		referenceText(graphics, canvas, "own return path.", 697, 425, 122, 0.7f, HgnPalette.TEXT);
	}

	private void referenceText(GuiGraphics graphics, HgnDashboardLayout canvas,
		String text, int x, int y, int width, float size, int color) {
		HgnTypography.fitted(graphics, font, text, canvas.x(x), canvas.y(y),
			canvas.size(width), canvas.scale() * size, color);
	}

	private void referenceHeading(GuiGraphics graphics, HgnDashboardLayout canvas,
		String text, int x, int y, int width, float size, int color) {
		HgnTypography.fittedHeading(graphics, font, text, canvas.x(x), canvas.y(y),
			canvas.size(width), canvas.scale() * size, color);
	}
}
