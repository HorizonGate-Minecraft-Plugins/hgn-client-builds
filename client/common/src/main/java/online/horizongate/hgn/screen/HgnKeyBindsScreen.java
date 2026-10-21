package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnKeyBindEntryAccessor;
import online.horizongate.hgn.mixin.HgnLanguageListAccessor;
import online.horizongate.hgn.ui.HgnKeyBindRow;
import online.horizongate.hgn.ui.HgnKeyBindsLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Keeps the complete native list, capture handlers and footer; only page composition changes. */
public final class HgnKeyBindsScreen extends KeyBindsScreen {
	private KeyBindsList bindings;
	private final List<AbstractWidget> actions = new ArrayList<>();
	private StringWidget pageTitle;
	private StringWidget status;
	private StringWidget hint;
	private HgnKeyBindsLayout pageLayout;
	private boolean collecting;

	public HgnKeyBindsScreen(Screen parent, Options options) {
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
		bindings = null;
		actions.clear();
		for (var child : List.copyOf(children())) {
			if (child instanceof KeyBindsList nativeList) bindings = nativeList;
			else if (child instanceof HgnSettingsHeader || child instanceof StringWidget) removeWidget(child);
			else if (child instanceof AbstractWidget widget) actions.add(widget);
		}
		if (bindings == null || actions.size() != 2) throw new IllegalStateException("Native key-binding list or footer missing");
		for (var entry : bindings.children()) {
			if (entry instanceof HgnKeyBindRow row) row.hgn$keyBindsOwner(this);
		}
		pageTitle = addRenderableWidget(new StringWidget(getTitle(), font));
		status = addRenderableWidget(new StringWidget(Component.empty(), font));
		hint = addRenderableWidget(new StringWidget(Component.empty(), font));
		HgnCompat.leftAlignLanguageLabel(pageTitle);
		HgnCompat.leftAlignLanguageLabel(status);
		HgnCompat.leftAlignLanguageLabel(hint);
		arrange();
		updateStatus(-1, -1);
	}

	public KeyBindsList nativeList() {
		return bindings;
	}

	@Override
	protected void repositionElements() {
		if (collecting || bindings == null) super.repositionElements();
		else arrange();
	}

	private void arrange() {
		pageLayout = HgnKeyBindsLayout.resolve(width, height, actions.size());
		place(pageTitle, pageLayout.title());
		place(status, pageLayout.status());
		place(hint, pageLayout.hint());
		HgnRect bounds = pageLayout.list();
		bindings.updateSizeAndPosition(bounds.width(), bounds.height(), bounds.y());
		bindings.setX(0);
		keepFocusedRowVisible();
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), pageLayout.actions().get(index));
	}

	private void keepFocusedRowVisible() {
		if (getFocused() != bindings || bindings.getFocused() == null) return;
		int index = bindings.children().indexOf(bindings.getFocused());
		if (index < 0) return;
		HgnLanguageListAccessor rows = (HgnLanguageListAccessor) bindings;
		int top = rows.hgn$languageRowTop(index);
		int bottom = rows.hgn$languageRowBottom(index);
		double scroll = HgnCompat.keyBindsScroll(bindings);
		if (top < bindings.getY() + 2) bindings.setScrollAmount(scroll + top - bindings.getY() - 2);
		else if (bottom > bindings.getY() + bindings.getHeight() - 2) {
			bindings.setScrollAmount(scroll + bottom - bindings.getY() - bindings.getHeight() + 2);
		}
	}

	private void updateStatus(int mouseX, int mouseY) {
		if (selectedKey != null) {
			status.setMessage(Component.literal("Binding: ").append(Component.translatable(selectedKey.getName())));
			hint.setMessage(Component.literal("Press a key or mouse button. Escape unbinds."));
			return;
		}
		var hovered = bindings.isMouseOver(mouseX, mouseY) ? bindings.getChildAt(mouseX, mouseY).orElse(null) : null;
		var focused = getFocused() == bindings ? bindings.getFocused() : null;
		HgnKeyBindEntryAccessor row = hovered instanceof HgnKeyBindEntryAccessor entry ? entry
			: focused instanceof HgnKeyBindEntryAccessor entry ? entry : null;
		if (row != null) {
			status.setMessage(row.hgn$name().copy().append(" / ").append(row.hgn$key().getTranslatedKeyMessage()));
		} else {
			int changed = 0;
			int unbound = 0;
			for (KeyMapping key : options.keyMappings) {
				if (!key.isDefault()) changed++;
				if (key.isUnbound()) unbound++;
			}
			status.setMessage(Component.literal(options.keyMappings.length + " bindings / " + changed + " customized / " + unbound + " unbound"));
		}
		hint.setMessage(Component.literal("Select a binding to change it. Reset restores its default."));
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
		updateStatus(mouseX, mouseY);
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, pageLayout.header(), true);
		HgnSurface.panel(graphics, pageLayout.statusPanel(), selectedKey != null);
		HgnSurface.panel(graphics, pageLayout.listPanel(), false);
		HgnSurface.panel(graphics, pageLayout.footer(), false);
		if (!pageLayout.compact()) HgnTypography.fitted(graphics, font, "HGN / KEY BINDINGS",
			pageLayout.title().x(), pageLayout.header().y() + 10, pageLayout.title().width(), 0.85f, HgnPalette.CYAN);
		// KeyBindsScreen.render owns Reset All's enabled state; native widgets render exactly once.
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
