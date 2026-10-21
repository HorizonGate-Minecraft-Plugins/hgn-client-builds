package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnControlsLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Dedicated input navigation and preferences using the unchanged native widgets and lifecycle. */
public final class HgnControlsScreen extends ControlsScreen {
	private List<AbstractWidget> controls = List.of();
	private List<AbstractWidget> destinations = List.of();
	private List<AbstractWidget> preferences = List.of();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visiblePreferences = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton previous;
	private HgnButton next;
	private HgnControlsLayout pageLayout;
	private int page;
	private boolean collecting;

	public HgnControlsScreen(Screen parent, Options options) {
		super(parent, options);
	}

	@Override
	protected void init() {
		collecting = true;
		try {
			super.init();
		} finally {
			collecting = false;
		}
		if (list == null) throw new IllegalStateException("Native controls list was not initialized");
		controls = HgnSoundOptionsScreen.optionWidgets(list);
		destinations = List.of(nativeDestination("options.mouse_settings"), nativeDestination("controls.keybinds"));
		preferences = controls.stream().filter(widget -> !destinations.contains(widget)).toList();
		if (preferences.isEmpty()) throw new IllegalStateException("Native control preferences are missing");
		actions.clear();
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget != list
				&& !(widget instanceof StringWidget) && !(widget instanceof HgnSettingsHeader)) actions.add(widget);
		}
		pageTitle = new StringWidget(getTitle(), font);
		pageCounter = new StringWidget(Component.empty(), font);
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		HgnCompat.leftAlignLanguageLabel(pageCounter);
		previous = navigation("Previous", () -> showPage(page - 1));
		next = navigation("Next", () -> showPage(page + 1));
		for (int index = 0; index < controls.size(); index++) controls.get(index).setTabOrderGroup(index);
		previous.setTabOrderGroup(controls.size());
		next.setTabOrderGroup(controls.size() + 1);
		for (int index = 0; index < actions.size(); index++) actions.get(index).setTabOrderGroup(controls.size() + 2 + index);
		arrange(true);
	}

	private Button nativeDestination(String translation) {
		List<AbstractWidget> matches = controls.stream().filter(widget -> widget instanceof Button
			&& widget.getMessage().equals(Component.translatable(translation))).toList();
		if (matches.size() != 1) throw new IllegalStateException("Expected one native controls destination: " + translation);
		return (Button) matches.getFirst();
	}

	private static HgnButton navigation(String label, Runnable action) {
		return HgnButton.centered(0, 0, 1, 20, Component.literal(label), false, button -> action.run());
	}

	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	public List<AbstractWidget> nativeDestinations() {
		return destinations;
	}

	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native controls list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null || !preferences.contains(widget)) throw new IllegalStateException("Native control preference is missing: " + option);
		return widget;
	}

	/** Updates only the visible preference page, without pressing a native option or destination. */
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
		pageLayout = HgnControlsLayout.resolve(width, height, preferences.size(), page, actions.size());
		int focusedIndex = preferences.indexOf(focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnControlsLayout.resolve(width, height, preferences.size(), pageLayout.pageContaining(focusedIndex), actions.size());
		}
		page = pageLayout.page();
		List<AbstractWidget> previouslyVisible = List.copyOf(visiblePreferences);
		visiblePreferences.clear();
		for (int index = 0; index < pageLayout.controls().size(); index++) {
			AbstractWidget control = preferences.get(pageLayout.firstControl() + index);
			place(control, pageLayout.controls().get(index));
			visiblePreferences.add(control);
		}
		for (int index = 0; index < destinations.size(); index++) place(destinations.get(index), pageLayout.destinations().get(index));
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
		place(pageTitle, pageLayout.title());
		place(pageCounter, pageLayout.counter());
		pageCounter.setMessage(Component.literal((page + 1) + " / " + pageLayout.pages()));
		place(previous, pageLayout.previous());
		place(next, pageLayout.next());
		previous.active = page > 0;
		next.active = page + 1 < pageLayout.pages();
		if (!children().contains(pageTitle)) {
			// Retain the native list for pending/reset/save ownership, without duplicate input or rendering.
			clearWidgets();
			setFocused(null);
			for (AbstractWidget destination : destinations) addRenderableWidget(destination);
			for (AbstractWidget control : visiblePreferences) addRenderableWidget(control);
			addRenderableWidget(previous);
			addRenderableWidget(next);
			for (AbstractWidget action : actions) addRenderableWidget(action);
			addRenderableWidget(pageTitle);
			addRenderableWidget(pageCounter);
		} else {
			for (AbstractWidget control : previouslyVisible) if (!visiblePreferences.contains(control)) removeWidget(control);
			for (AbstractWidget control : visiblePreferences) if (!children().contains(control)) addRenderableWidget(control);
		}
		if (focused instanceof AbstractWidget widget && children().contains(widget) && widget.active && widget.visible) {
			// Keep an already-focused native slider in its keyboard-edit mode.
			if (getFocused() != widget || !widget.isFocused()) setFocused(widget);
		} else if (focused != null) {
			setFocused(null);
			visiblePreferences.stream().filter(widget -> widget.active && widget.visible).findFirst().ifPresent(this::setFocused);
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
		HgnSurface.panel(graphics, pageLayout.header(), true);
		HgnSurface.panel(graphics, pageLayout.navigation(), true);
		HgnSurface.panel(graphics, pageLayout.content(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / INPUT",
			pageLayout.title().x(), pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget destination : destinations) destination.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visiblePreferences) control.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
