package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSkinCustomizationLayout;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Frames the original model-layer and main-hand widgets, keeping their native callbacks and save owner. */
public final class HgnSkinCustomizationScreen extends SkinCustomizationScreen {
	private List<AbstractWidget> controls = List.of();
	private List<AbstractWidget> layers = List.of();
	private AbstractWidget mainHand;
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<AbstractWidget> visibleControls = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget pageCounter;
	private HgnButton previous;
	private HgnButton next;
	private HgnSkinCustomizationLayout pageLayout;
	private int page;
	private boolean collecting;

	public HgnSkinCustomizationScreen(Screen parent, Options options) {
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
		if (list == null) throw new IllegalStateException("Native skin options list was not initialized");
		controls = List.copyOf(HgnSoundOptionsScreen.optionWidgets(list));
		List<AbstractWidget> hands = controls.stream().filter(widget -> widget instanceof CycleButton<?> cycle
			&& cycle.getValue() instanceof HumanoidArm).toList();
		if (hands.size() != 1) throw new IllegalStateException("Native main-hand control could not be identified");
		mainHand = hands.getFirst();
		layers = controls.stream().filter(widget -> widget != mainHand).toList();
		if (layers.isEmpty()) throw new IllegalStateException("Native skin layer controls are missing");
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

	public List<AbstractWidget> nativeControls() {
		return controls;
	}

	public AbstractWidget mainHandControl() {
		return mainHand;
	}

	/** Changes only visibility; the original list continues to own every native control. */
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
		pageLayout = HgnSkinCustomizationLayout.resolve(width, height, layers.size(), page, actions.size());
		int focusedIndex = layers.indexOf(focused);
		if (retainFocusedPage && focusedIndex >= 0) {
			pageLayout = HgnSkinCustomizationLayout.resolve(width, height, layers.size(), pageLayout.pageContaining(focusedIndex), actions.size());
		}
		page = pageLayout.page();
		List<AbstractWidget> previouslyVisible = List.copyOf(visibleControls);
		visibleControls.clear();
		for (int index = 0; index < pageLayout.controls().size(); index++) {
			AbstractWidget control = layers.get(pageLayout.firstControl() + index);
			place(control, pageLayout.controls().get(index));
			visibleControls.add(control);
		}
		place(mainHand, pageLayout.mainHand());
		place(pageTitle, pageLayout.title());
		place(pageCounter, pageLayout.counter());
		pageCounter.setMessage(Component.literal((page + 1) + " / " + pageLayout.pages()));
		place(previous, pageLayout.previous());
		place(next, pageLayout.next());
		previous.active = page > 0;
		next.active = page + 1 < pageLayout.pages();
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
		if (!children().contains(pageTitle)) {
			// Keep the list for native apply/save ownership without duplicate input or rendering.
			clearWidgets();
			setFocused(null);
			for (AbstractWidget control : visibleControls) addRenderableWidget(control);
			addRenderableWidget(mainHand);
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
		HgnSurface.panel(graphics, pageLayout.layersPanel(), false);
		HgnSurface.panel(graphics, pageLayout.handPanel(), true);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) {
			HgnTypography.fitted(graphics, font, "HGN / PLAYER APPEARANCE", pageLayout.title().x(),
				pageLayout.header().y() + 11, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
			HgnTypography.fitted(graphics, font, "MODEL LAYERS", pageLayout.layersPanel().x() + 10,
				pageLayout.layersPanel().y() + 8, pageLayout.layersPanel().width() - 20, 0.85f, HgnPalette.CYAN);
			HgnTypography.fitted(graphics, font, Component.translatable("options.mainHand").getString(),
				pageLayout.handPanel().x() + 8, pageLayout.handPanel().y() + 7,
				pageLayout.handPanel().width() - 16, 0.85f, HgnPalette.CYAN);
		}
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		pageCounter.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : visibleControls) control.render(graphics, mouseX, mouseY, partialTick);
		mainHand.render(graphics, mouseX, mouseY, partialTick);
		previous.render(graphics, mouseX, mouseY, partialTick);
		next.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
