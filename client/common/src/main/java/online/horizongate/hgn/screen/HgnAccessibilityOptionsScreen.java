package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnAccessibilityOptionsLayout;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Keeps speech and captions within reach while preserving native accessibility controls and callbacks. */
public final class HgnAccessibilityOptionsScreen extends AccessibilityOptionsScreen {
	private List<AbstractWidget> controls = List.of();
	private List<AbstractWidget> pinned = List.of();
	private List<AbstractWidget> preferences = List.of();
	private List<Boolean> headings = List.of();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visibleControls = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton previous;
	private HgnButton next;
	private HgnAccessibilityOptionsLayout pageLayout;
	private int page;
	private boolean collecting;

	public HgnAccessibilityOptionsScreen(Screen parent, Options options) {
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
		if (list == null) throw new IllegalStateException("Native accessibility options list was not initialized");
		controls = HgnSoundOptionsScreen.optionWidgets(list);
		pinned = List.of(narratorControl(), nativeOption(options.showSubtitles()));
		preferences = controls.stream().filter(widget -> !pinned.contains(widget)).toList();
		headings = preferences.stream().map(widget -> widget instanceof StringWidget).toList();
		actions.clear();
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
		for (int index = 0; index < controls.size(); index++) controls.get(index).setTabOrderGroup(index);
		previous.setTabOrderGroup(controls.size());
		next.setTabOrderGroup(controls.size() + 1);
		for (int index = 0; index < actions.size(); index++) actions.get(index).setTabOrderGroup(controls.size() + 2 + index);
		arrange(true);
	}

	/** Includes native section headings and any extra controls contributed to the original list. */
	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	/** Some native generations do not map the narrator option into findOption. */
	public AbstractWidget narratorControl() {
		List<AbstractWidget> narrators = controls.stream().filter(widget -> widget instanceof CycleButton<?> cycle
			&& cycle.getValue() instanceof NarratorStatus).toList();
		if (narrators.size() != 1) throw new IllegalStateException("Native accessibility narrator is missing or ambiguous");
		return narrators.getFirst();
	}

	/** Retains the original nullable reference used by the native narrator shortcut. */
	public AbstractWidget nativeNarratorReference() {
		return narratorButton;
	}

	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native accessibility options list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null || !controls.contains(widget)) throw new IllegalStateException("Native accessibility option is missing: " + option);
		return widget;
	}

	/** Pagination does not apply pending native options or invoke accessibility callbacks. */
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
		pageLayout = HgnAccessibilityOptionsLayout.resolve(width, height, headings, page, actions.size());
		int focusedIndex = HgnAccessibilityOptionsLayout.focusedControlIndex(preferences, focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnAccessibilityOptionsLayout.resolve(width, height, headings, pageLayout.pageContaining(focusedIndex), actions.size());
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
		if (!children().contains(pageTitle)) {
			// Native list remains intact for findOption, pending values, narrator ownership and inherited callbacks.
			clearWidgets();
			setFocused(null);
			for (AbstractWidget control : pinned) addRenderableWidget(control);
			for (AbstractWidget control : visibleControls) addRenderableWidget(control);
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
		HgnSurface.panel(graphics, pageLayout.header(), true);
		HgnSurface.panel(graphics, pageLayout.assistance(), true);
		HgnSurface.panel(graphics, pageLayout.content(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / ACCESSIBILITY",
			pageLayout.title().x(), pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		if (pageLayout.sideBySide()) HgnTypography.fitted(graphics, font, "SPEECH & CAPTIONS",
			pageLayout.assistance().x() + 10, pageLayout.assistance().y() + 12, pageLayout.assistance().width() - 20, 0.85f, HgnPalette.CYAN);
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : pinned) control.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visibleControls) control.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
