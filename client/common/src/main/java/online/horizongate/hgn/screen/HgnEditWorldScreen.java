package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnEditWorldLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Composition attached to the original native editor, never a replacement storage/callback owner. */
public final class HgnEditWorldScreen {
	public interface Host {
		List<AbstractWidget> hgn$editWorldWidgets();
		EditBox hgn$editWorldName();
		void hgn$editWorldAdd(AbstractWidget widget);
		void hgn$editWorldRemove(AbstractWidget widget);
		HgnEditWorldScreen hgn$editWorldComposition();
	}

	private final EditWorldScreen screen;
	private final Host host;
	private final List<AbstractWidget> original;
	private final List<AbstractWidget> actions;
	private final AbstractWidget nameLabel;
	private final AbstractWidget save;
	private final AbstractWidget cancel;
	private final StringWidget title;
	private final HgnButton previous;
	private final HgnButton next;
	private HgnEditWorldLayout layout;
	private boolean disposed;

	public HgnEditWorldScreen(EditWorldScreen screen, Host host) {
		this.screen = screen;
		this.host = host;
		List<AbstractWidget> widgets = new ArrayList<>(host.hgn$editWorldWidgets());
		for (var child : screen.children()) if (child instanceof AbstractWidget widget && !widgets.contains(widget)) widgets.add(widget);
		original = List.copyOf(widgets);
		nameLabel = unique(Component.translatable("selectWorld.enterName"), true);
		save = unique(Component.translatable("selectWorld.edit.save"), false);
		cancel = unique(CommonComponents.GUI_CANCEL, false);
		actions = original.stream().filter(widget -> widget != nameLabel && widget != host.hgn$editWorldName()
			&& widget != save && widget != cancel).toList();
		title = new StringWidget(screen.getTitle(), Minecraft.getInstance().font);
		HgnCompat.leftAlignLanguageLabel(title);
		previous = HgnButton.centered(0, 0, 1, 20, Component.literal("Previous"), false, button -> showPage(layout.page() - 1));
		next = HgnButton.centered(0, 0, 1, 20, Component.literal("Next"), false, button -> showPage(layout.page() + 1));
		host.hgn$editWorldAdd(title);
	}

	private AbstractWidget unique(Component label, boolean text) {
		List<AbstractWidget> matches = original.stream().filter(widget -> (widget instanceof StringWidget) == text
			&& widget.getMessage().getContents().equals(label.getContents())).toList();
		if (matches.size() != 1) throw new IllegalStateException("Native Edit World control missing or ambiguous: " + label);
		return matches.getFirst();
	}

	public List<AbstractWidget> nativeWidgets() {
		return original;
	}

	public int pageCount() {
		return layout == null ? 1 : layout.pages();
	}

	public boolean disposed() {
		return disposed;
	}

	public void arrange() {
		arrange(layout == null ? 0 : layout.page(), true);
	}

	public void showPage(int page) {
		arrange(page, false);
	}

	private void arrange(int page, boolean keepFocusedPage) {
		if (disposed) return;
		var focused = screen.getFocused();
		layout = HgnEditWorldLayout.resolve(screen.width, screen.height, actions.size(), page);
		int focusedIndex = HgnEditWorldLayout.focusedIndex(actions, focused);
		if (keepFocusedPage && focusedIndex >= 0) layout = HgnEditWorldLayout.resolve(screen.width, screen.height,
			actions.size(), focusedIndex / layout.capacity());
		place(title, layout.title());
		place(nameLabel, layout.nameLabel());
		place(host.hgn$editWorldName(), layout.name());
		place(save, layout.save());
		place(cancel, layout.cancel());
		List<AbstractWidget> visible = new ArrayList<>();
		for (var slot : layout.actions()) {
			AbstractWidget widget = actions.get(slot.index());
			place(widget, slot.bounds());
			visible.add(widget);
		}
		for (AbstractWidget widget : original) {
			boolean show = !actions.contains(widget) || visible.contains(widget);
			if (show && !screen.children().contains(widget)) host.hgn$editWorldAdd(widget);
			else if (!show && screen.children().contains(widget)) host.hgn$editWorldRemove(widget);
		}
		place(previous, layout.previous());
		place(next, layout.next());
		previous.active = layout.page() > 0;
		next.active = layout.page() + 1 < layout.pages();
		for (HgnButton navigation : List.of(previous, next)) {
			if (layout.pages() > 1 && !screen.children().contains(navigation)) host.hgn$editWorldAdd(navigation);
			else if (layout.pages() == 1 && screen.children().contains(navigation)) host.hgn$editWorldRemove(navigation);
		}
		if (focused != null && !screen.children().contains(focused)) screen.setFocused(host.hgn$editWorldName());
	}

	/** Reinitialization removes only HGN chrome and restores original registrations, never closes storage. */
	public void dispose() {
		if (disposed) return;
		disposed = true;
		var focused = screen.getFocused();
		host.hgn$editWorldRemove(title);
		host.hgn$editWorldRemove(previous);
		host.hgn$editWorldRemove(next);
		for (AbstractWidget widget : original) if (!screen.children().contains(widget)) host.hgn$editWorldAdd(widget);
		if (focused == previous || focused == next || focused == title) screen.setFocused(host.hgn$editWorldName());
	}

	public void renderChrome(GuiGraphics graphics, int mouseX, int mouseY) {
		if (disposed || layout == null) return;
		HgnScreenBackdrop.render(screen, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.header(), true);
		HgnSurface.panel(graphics, layout.identity(), true);
		HgnSurface.panel(graphics, layout.content(), false);
		HgnSurface.panel(graphics, layout.footer(), false);
		var font = Minecraft.getInstance().font;
		if (!layout.compact()) HgnTypography.fitted(graphics, font, "HGN / WORLD MANAGEMENT", layout.title().x(),
			layout.header().y() + 10, layout.title().width(), 0.85f, HgnPalette.CYAN);
		if (layout.sideBySide()) HgnTypography.fitted(graphics, font, "WORLD IDENTITY", layout.identity().x() + 12,
			layout.identity().y() + 13, layout.identity().width() - 24, 0.85f, HgnPalette.CYAN);
	}

	private static void place(AbstractWidget widget, HgnRect bounds) {
		widget.setX(bounds.x());
		widget.setY(bounds.y());
		widget.setWidth(bounds.width());
		widget.setHeight(bounds.height());
	}
}
