package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.resources.language.LanguageManager;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnLanguageListAccessor;
import online.horizongate.hgn.ui.HgnLanguageLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Repositions the real language widgets; native selection, font options and reload own their flow. */
public final class HgnLanguageSelectScreen extends LanguageSelectScreen {
	private final List<AbstractWidget> retainedWidgets = new ArrayList<>();
	private final List<AbstractWidget> actions = new ArrayList<>();
	private final List<StringWidget> warnings = new ArrayList<>();
	private AbstractSelectionList<?> languages;
	private EditBox search;
	private StringWidget pageTitle;
	private HgnLanguageLayout pageLayout;
	private boolean collecting;

	public HgnLanguageSelectScreen(Screen parent, Options options, LanguageManager languageManager) {
		super(parent, options, languageManager);
	}

	@Override
	protected void init() {
		collecting = true;
		try {
			super.init();
		} finally {
			collecting = false;
		}
		retainedWidgets.clear();
		actions.clear();
		warnings.clear();
		languages = null;
		search = null;
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget) || widget instanceof HgnSettingsHeader) continue;
			if (widget instanceof StringWidget label && label.getMessage().equals(getTitle())) continue;
			retainedWidgets.add(widget);
			if (widget instanceof AbstractSelectionList<?> list) languages = list;
			else if (widget instanceof EditBox editBox) search = editBox;
			else if (widget instanceof StringWidget label) warnings.add(label);
			else actions.add(widget);
		}
		var focused = getFocused();
		clearWidgets();
		for (AbstractWidget widget : retainedWidgets) addRenderableWidget(widget);
		pageTitle = addRenderableWidget(new StringWidget(getTitle(), font));
		if (focused != null && retainedWidgets.contains(focused)) setFocused(focused);
		arrange();
		centerInitialSelection();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (!collecting && languages != null) arrange();
	}

	private void arrange() {
		pageLayout = HgnLanguageLayout.resolve(width, height, search != null, actions.size());
		placeLabel(pageTitle, pageLayout.title(), false);
		if (search != null) place(search, pageLayout.search());
		if (languages != null) {
			HgnRect bounds = pageLayout.languageList();
			languages.updateSizeAndPosition(bounds.width(), bounds.height(), bounds.y());
			languages.setX(0);
		}
		for (StringWidget warning : warnings) placeLabel(warning, pageLayout.warning(), true);
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
	}

	private void centerInitialSelection() {
		if (languages == null || languages.getSelected() == null) return;
		int selected = languages.children().indexOf(languages.getSelected());
		if (selected < 0) return;
		languages.setScrollAmount(0);
		HgnLanguageListAccessor rows = (HgnLanguageListAccessor) languages;
		int center = (rows.hgn$languageRowTop(selected) + rows.hgn$languageRowBottom(selected)) / 2;
		languages.setScrollAmount(center - languages.getY() - languages.getHeight() / 2.0);
	}

	private void placeLabel(StringWidget label, HgnRect bounds, boolean centered) {
		HgnCompat.leftAlignLanguageLabel(label);
		place(label, HgnLanguageLayout.labelBounds(bounds, font.width(label.getMessage()), centered));
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
		HgnSurface.panel(graphics, pageLayout.listPanel(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "LANGUAGE & TEXT",
			pageLayout.title().x(), pageLayout.header().y() + 38, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		pageTitle.render(graphics, mouseX, mouseY, partialTick);
		// Native renderWithTooltip surrounds this pass; language rows keep their native font and renderer.
		for (AbstractWidget widget : retainedWidgets) widget.render(graphics, mouseX, mouseY, partialTick);
	}
}
