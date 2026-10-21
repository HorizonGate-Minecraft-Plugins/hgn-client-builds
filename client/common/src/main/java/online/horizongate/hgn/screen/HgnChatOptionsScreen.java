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
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnChatOptionsLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Native chat preferences grouped around a persistent visibility control, with unchanged save ownership. */
public final class HgnChatOptionsScreen extends ChatOptionsScreen {
	private List<AbstractWidget> controls = List.of();
	private List<AbstractWidget> display = List.of();
	private List<AbstractWidget> conversation = List.of();
	private AbstractWidget visibility;
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visibleControls = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton displayTab;
	private HgnButton conversationTab;
	private HgnButton previous;
	private HgnButton next;
	private HgnChatOptionsLayout pageLayout;
	private int group;
	private int page;
	private boolean collecting;

	public HgnChatOptionsScreen(Screen parent, Options options) {
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
		if (list == null) throw new IllegalStateException("Native chat options list was not initialized");
		controls = HgnSoundOptionsScreen.optionWidgets(list);
		visibility = nativeOption(options.chatVisibility());
		display = List.of(nativeOption(options.chatColors()), nativeOption(options.chatOpacity()),
			nativeOption(options.textBackgroundOpacity()), nativeOption(options.chatScale()),
			nativeOption(options.chatLineSpacing()), nativeOption(options.chatWidth()),
			nativeOption(options.chatHeightFocused()), nativeOption(options.chatHeightUnfocused()));
		conversation = controls.stream().filter(widget -> widget != visibility && !display.contains(widget)).toList();
		if (conversation.isEmpty()) throw new IllegalStateException("Native conversation controls are missing");
		actions.clear();
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget != list
				&& !(widget instanceof StringWidget) && !(widget instanceof HgnSettingsHeader)) actions.add(widget);
		}
		pageTitle = new StringWidget(getTitle(), font);
		pageCounter = new StringWidget(Component.empty(), font);
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		HgnCompat.leftAlignLanguageLabel(pageCounter);
		displayTab = navigation("Display", () -> showGroup(0));
		conversationTab = navigation("Conversation", () -> showGroup(1));
		previous = navigation("Previous", () -> showPage(page - 1));
		next = navigation("Next", () -> showPage(page + 1));
		for (int index = 0; index < controls.size(); index++) controls.get(index).setTabOrderGroup(index);
		displayTab.setTabOrderGroup(controls.size());
		conversationTab.setTabOrderGroup(controls.size() + 1);
		previous.setTabOrderGroup(controls.size() + 2);
		next.setTabOrderGroup(controls.size() + 3);
		for (int index = 0; index < actions.size(); index++) actions.get(index).setTabOrderGroup(controls.size() + 4 + index);
		arrange(true);
	}

	private static HgnButton navigation(String label, Runnable action) {
		return HgnButton.centered(0, 0, 1, 20, Component.literal(label), false, button -> action.run());
	}

	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	/** The original reference also used by the inherited narrator-shortcut refresh. */
	public AbstractWidget nativeNarratorControl() {
		return narratorButton;
	}

	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native chat options list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null || !controls.contains(widget)) throw new IllegalStateException("Native chat option is missing: " + option);
		return widget;
	}

	/** Changes only the displayed group; every original widget remains owned by the native list. */
	public void showGroup(int selected) {
		if (selected < 0 || selected > 1) throw new IllegalArgumentException("Unknown chat options group");
		if (selected == group) return;
		group = selected;
		page = 0;
		if (pageTitle != null) arrange(false);
	}

	/** Changes only pagination, without applying pending sliders or invoking native callbacks. */
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
		List<AbstractWidget> selected = group == 0 ? display : conversation;
		pageLayout = HgnChatOptionsLayout.resolve(width, height, selected.size(), page, actions.size());
		int focusedIndex = HgnChatOptionsLayout.focusedControlIndex(selected, focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnChatOptionsLayout.resolve(width, height, selected.size(), pageLayout.pageContaining(focusedIndex), actions.size());
		}
		page = pageLayout.page();
		List<AbstractWidget> previouslyVisible = List.copyOf(visibleControls);
		visibleControls.clear();
		for (int index = 0; index < pageLayout.controls().size(); index++) {
			AbstractWidget control = selected.get(pageLayout.firstControl() + index);
			place(control, pageLayout.controls().get(index));
			visibleControls.add(control);
		}
		place(visibility, pageLayout.visibility());
		place(pageTitle, pageLayout.title());
		place(pageCounter, pageLayout.counter());
		pageCounter.setMessage(Component.literal((page + 1) + " / " + pageLayout.pages()));
		place(displayTab.selected(group == 0), pageLayout.tabs().get(0));
		place(conversationTab.selected(group == 1), pageLayout.tabs().get(1));
		place(previous, pageLayout.previous());
		place(next, pageLayout.next());
		previous.active = page > 0;
		next.active = page + 1 < pageLayout.pages();
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
		if (!children().contains(pageTitle)) {
			// Retain native pending values and narrator ownership without duplicate input or rendering.
			clearWidgets();
			setFocused(null);
			addRenderableWidget(visibility);
			for (AbstractWidget control : visibleControls) addRenderableWidget(control);
			addRenderableWidget(displayTab);
			addRenderableWidget(conversationTab);
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
			// Refocusing an unchanged native slider would exit its keyboard-edit mode.
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
		HgnSurface.panel(graphics, pageLayout.navigation(), true);
		HgnSurface.panel(graphics, pageLayout.content(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / CHAT PREFERENCES",
			pageLayout.title().x(), pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		if (pageLayout.sideBySide()) HgnTypography.fitted(graphics, font, "CHAT MODE",
			pageLayout.visibility().x(), pageLayout.navigation().y() + 12, pageLayout.visibility().width(), 0.85f, HgnPalette.CYAN);
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		visibility.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visibleControls) control.render(graphics, mouseX, mouseY, partialTick);
		displayTab.render(graphics, mouseX, mouseY, partialTick);
		conversationTab.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
