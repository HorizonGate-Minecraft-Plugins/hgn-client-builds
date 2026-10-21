package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSoundOptionsLayout;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** HGN sound groups retaining native option widgets and their apply/save/return ownership. */
public final class HgnSoundOptionsScreen extends SoundOptionsScreen {
	private List<AbstractWidget> controls = List.of();
	private final List<AbstractWidget> volumes = new ArrayList<>();
	private final List<AbstractWidget> output = new ArrayList<>();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visibleControls = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton volumeTab;
	private HgnButton outputTab;
	private HgnButton previous;
	private HgnButton next;
	private HgnSoundOptionsLayout pageLayout;
	private int group;
	private int page;
	private boolean collecting;

	public HgnSoundOptionsScreen(Screen parent, Options options) {
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
		if (list == null) throw new IllegalStateException("Native sound options list was not initialized");
		controls = optionWidgets(list);
		volumes.clear();
		output.clear();
		actions.clear();
		for (SoundSource source : SoundSource.values()) volumes.add(nativeOption(options.getSoundSourceOptionInstance(source)));
		for (AbstractWidget control : controls) if (!volumes.contains(control)) output.add(control);
		if (output.isEmpty()) throw new IllegalStateException("Native sound output controls are missing");
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget != list
				&& !(widget instanceof StringWidget) && !(widget instanceof HgnSettingsHeader)) actions.add(widget);
		}
		pageTitle = new StringWidget(getTitle(), font);
		pageCounter = new StringWidget(Component.empty(), font);
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		HgnCompat.leftAlignLanguageLabel(pageCounter);
		volumeTab = navigation("Volume", () -> showGroup(0));
		outputTab = navigation("Output & cues", () -> showGroup(1));
		previous = navigation("Previous", () -> showPage(page - 1));
		next = navigation("Next", () -> showPage(page + 1));
		for (int index = 0; index < controls.size(); index++) controls.get(index).setTabOrderGroup(index);
		volumeTab.setTabOrderGroup(controls.size());
		outputTab.setTabOrderGroup(controls.size() + 1);
		previous.setTabOrderGroup(controls.size() + 2);
		next.setTabOrderGroup(controls.size() + 3);
		for (int index = 0; index < actions.size(); index++) actions.get(index).setTabOrderGroup(controls.size() + 4 + index);
		arrange(true);
	}

	private static HgnButton navigation(String label, Runnable action) {
		return HgnButton.centered(0, 0, 1, 20, Component.literal(label), false, button -> action.run());
	}

	/** Public event-tree traversal avoids version-specific native option-entry classes. */
	public static List<AbstractWidget> optionWidgets(ContainerEventHandler owner) {
		List<AbstractWidget> widgets = new ArrayList<>();
		for (GuiEventListener child : owner.children()) {
			if (child instanceof ContainerEventHandler container) widgets.addAll(optionWidgets(container));
			else if (child instanceof AbstractWidget widget) widgets.add(widget);
		}
		return List.copyOf(widgets);
	}

	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native sound options list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null) throw new IllegalStateException("Native sound option widget is missing: " + option);
		if (!controls.contains(widget)) throw new IllegalStateException("Native sound option is absent from its event tree: " + option);
		return widget;
	}

	/** Changes only the displayed group, without invoking an option callback. */
	public void showGroup(int selected) {
		if (selected < 0 || selected > 1) throw new IllegalArgumentException("Unknown sound options group");
		if (selected == group) return;
		group = selected;
		page = 0;
		if (pageTitle != null) arrange(false);
	}

	/** Changes only pagination, retaining all native controls in their original list. */
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
		List<AbstractWidget> selected = group == 0 ? volumes : output;
		pageLayout = HgnSoundOptionsLayout.resolve(width, height, selected.size(), group == 0, page, actions.size());
		int focusedIndex = selected.indexOf(focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnSoundOptionsLayout.resolve(width, height, selected.size(), group == 0,
				pageLayout.pageContaining(focusedIndex), actions.size());
		}
		page = pageLayout.page();
		List<AbstractWidget> previouslyVisible = List.copyOf(visibleControls);
		visibleControls.clear();
		for (int index = 0; index < pageLayout.controls().size(); index++) {
			AbstractWidget control = selected.get(pageLayout.firstControl() + index);
			place(control, pageLayout.controls().get(index));
			visibleControls.add(control);
		}
		place(pageTitle, pageLayout.title());
		place(pageCounter, pageLayout.counter());
		pageCounter.setMessage(Component.literal((page + 1) + " / " + pageLayout.pages()));
		place(volumeTab.selected(group == 0), pageLayout.tabs().get(0));
		place(outputTab.selected(group == 1), pageLayout.tabs().get(1));
		place(previous, pageLayout.previous());
		place(next, pageLayout.next());
		previous.active = page > 0;
		next.active = page + 1 < pageLayout.pages();
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
		if (!children().contains(pageTitle)) {
			// Keep the native list alive for pending values and reset/save ownership, without registering it twice.
			clearWidgets();
			setFocused(null);
			for (AbstractWidget control : visibleControls) addRenderableWidget(control);
			addRenderableWidget(volumeTab);
			addRenderableWidget(outputTab);
			addRenderableWidget(previous);
			addRenderableWidget(next);
			for (AbstractWidget action : actions) addRenderableWidget(action);
			addRenderableWidget(pageTitle);
			addRenderableWidget(pageCounter);
		} else {
			for (AbstractWidget control : previouslyVisible) if (!visibleControls.contains(control)) removeWidget(control);
			for (AbstractWidget control : visibleControls) if (!children().contains(control)) addRenderableWidget(control);
		}
		if (focused instanceof AbstractWidget widget && children().contains(widget) && widget.active && widget.visible) {
			// Refocusing a retained slider exits its native keyboard-edit mode.
			if (getFocused() != widget || !widget.isFocused()) setFocused(widget);
		} else if (focused != null) {
			setFocused(null);
			visibleControls.stream().filter(widget -> widget.active && widget.visible).findFirst().ifPresent(this::setFocused);
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
		HgnSurface.panel(graphics, pageLayout.content(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / SOUND",
			pageLayout.title().x(), pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visibleControls) control.render(graphics, mouseX, mouseY, partialTick);
		volumeTab.render(graphics, mouseX, mouseY, partialTick);
		outputTab.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
