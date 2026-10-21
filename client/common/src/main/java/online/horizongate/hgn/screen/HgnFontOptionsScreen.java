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
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnFontOptionsLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Font preferences using the original controls and the native apply/save/return lifecycle. */
public final class HgnFontOptionsScreen extends FontOptionsScreen {
	private final List<AbstractWidget> controls = new ArrayList<>();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private StringWidget pageTitle;
	private HgnFontOptionsLayout pageLayout;
	private boolean collecting;

	public HgnFontOptionsScreen(Screen parent, Options options) {
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
		controls.clear();
		actions.clear();
		controls.add(nativeOption(options.forceUnicodeFont()));
		controls.add(nativeOption(options.japaneseGlyphVariants()));
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget != list
				&& !(widget instanceof StringWidget) && !(widget instanceof HgnSettingsHeader)) actions.add(widget);
		}
		GuiEventListener focused = getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
			focused = container.getFocused();
		}
		// Keep list and its entries intact: native onClose applies pending values through that ownership.
		clearWidgets();
		setFocused(null);
		for (AbstractWidget control : controls) addRenderableWidget(control);
		for (AbstractWidget action : actions) addRenderableWidget(action);
		pageTitle = addRenderableWidget(new StringWidget(getTitle(), font));
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		arrange();
		if (controls.contains(focused) || actions.contains(focused)) setFocused(focused);
	}

	/** Reads the native option mapping without replacing its widget or callback. */
	public AbstractWidget nativeOption(OptionInstance<?> option) {
		if (list == null) throw new IllegalStateException("Native font options list was not initialized");
		AbstractWidget widget = list.findOption(option);
		if (widget == null) throw new IllegalStateException("Native font option widget is missing: " + option);
		return widget;
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (!collecting && pageTitle != null) arrange();
	}

	private void arrange() {
		pageLayout = HgnFontOptionsLayout.resolve(width, height, actions.size());
		place(pageTitle, pageLayout.title());
		for (int index = 0; index < controls.size(); index++) place(controls.get(index), pageLayout.controls().get(index));
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
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
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / SETTINGS",
			pageLayout.title().x(), pageLayout.header().y() + 13, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		for (int index = 0; index < controls.size(); index++) {
			HgnRect row = pageLayout.rows().get(index);
			HgnSurface.inset(graphics, row, controls.get(index).isHoveredOrFocused());
			HgnTypography.centered(graphics, font, "0" + (index + 1), row.x() + 21,
				row.centerY() - 4, 0.9f, HgnPalette.CYAN);
		}
		// Render retained controls once; Screen.render also paints a background on older versions.
		// The inherited outer render pass continues to handle the native widget tooltips.
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget control : controls) control.render(graphics, mouseX, mouseY, partialTick);
		for (AbstractWidget action : actions) action.render(graphics, mouseX, mouseY, partialTick);
	}
}
