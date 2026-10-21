package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.HgnPackSelectionLayout;
import online.horizongate.hgn.ui.HgnPackSelectionLayout.Pane;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** In-place pack manager; the original screen alone owns its model, watcher and commit callback. */
public final class HgnPackSelectionScreen {
	public interface Host {
		TransferableSelectionList hgn$availablePacks();
		TransferableSelectionList hgn$selectedPacks();
		Button hgn$packDone();
		void hgn$packAdd(AbstractWidget widget);
		void hgn$packRemove(AbstractWidget widget);
		HgnPackSelectionScreen hgn$packComposition();
	}

	private final PackSelectionScreen screen;
	private final Host host;
	private final List<AbstractWidget> original;
	private final Map<AbstractWidget, HgnRect> nativeBounds = new IdentityHashMap<>();
	private final StringWidget title;
	private final StringWidget hint;
	private final EditBox search;
	private final AbstractWidget openFolder;
	private final HgnButton availableTab;
	private final HgnButton selectedTab;
	private final HgnPackSelectionLayout.State<TransferableSelectionList> state;
	private boolean arranging;

	/** Unknown children/layouts retain the whole native screen, never a partly hidden roster. */
	public static HgnPackSelectionScreen attach(PackSelectionScreen screen, Host host) {
		if (!HgnPackSelectionLayout.supported(screen.width, screen.height)) return null;
		List<AbstractWidget> original = new ArrayList<>();
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget widget)) return null;
			original.add(widget);
		}
		Set<AbstractWidget> identities = Collections.newSetFromMap(new IdentityHashMap<>());
		identities.addAll(original);
		if (identities.size() != original.size() || host.hgn$availablePacks() == host.hgn$selectedPacks()
			|| !identities.contains(host.hgn$availablePacks()) || !identities.contains(host.hgn$selectedPacks())
			|| !identities.contains(host.hgn$packDone())) return null;
		var titles = original.stream().filter(widget -> widget instanceof StringWidget
			&& messageIs(widget.getMessage(), screen.getTitle())).toList();
		var hints = original.stream().filter(widget -> widget instanceof StringWidget
			&& messageIs(widget.getMessage(), Component.translatable("pack.dropInfo"))).toList();
		var opens = original.stream().filter(widget -> widget instanceof Button
			&& messageIs(widget.getMessage(), Component.translatable("pack.openFolder"))).toList();
		var searches = original.stream().filter(widget -> widget instanceof EditBox).toList();
		if (titles.size() != 1 || hints.size() != 1 || opens.size() != 1 || searches.size() > 1) return null;
		Set<AbstractWidget> known = Collections.newSetFromMap(new IdentityHashMap<>());
		known.addAll(List.of(host.hgn$availablePacks(), host.hgn$selectedPacks(), host.hgn$packDone(),
			titles.getFirst(), hints.getFirst(), opens.getFirst()));
		known.addAll(searches);
		if (known.size() != original.size() || !known.containsAll(original)) return null;
		return new HgnPackSelectionScreen(screen, host, original, (StringWidget) titles.getFirst(),
			(StringWidget) hints.getFirst(), searches.isEmpty() ? null : (EditBox) searches.getFirst(), opens.getFirst());
	}

	private HgnPackSelectionScreen(PackSelectionScreen screen, Host host, List<AbstractWidget> original,
		StringWidget title, StringWidget hint, EditBox search, AbstractWidget openFolder) {
		this.screen = screen;
		this.host = host;
		this.original = List.copyOf(original);
		this.title = title;
		this.hint = hint;
		this.search = search;
		this.openFolder = openFolder;
		state = new HgnPackSelectionLayout.State<>(host.hgn$availablePacks(), host.hgn$selectedPacks());
		for (var widget : original) nativeBounds.put(widget, bounds(widget));
		availableTab = HgnButton.centered(0, 0, 1, 20, Component.translatable("pack.available.title"), false,
			button -> choose(Pane.AVAILABLE));
		selectedTab = HgnButton.centered(0, 0, 1, 20, Component.translatable("pack.selected.title"), false,
			button -> choose(Pane.SELECTED));
	}

	public boolean owns() { return !state.disposed() && host.hgn$packComposition() == this; }

	public void arrange() { arrange(true); }

	private void choose(Pane pane) {
		if (!owns()) return;
		state.choose(pane);
		arrange(false);
	}

	/** Native transfers may focus the other list; reveal that exact list immediately. */
	public void followNativeFocus(GuiEventListener focused) {
		if (!owns() || arranging || state.layout() == null || state.layout().dualPane()) return;
		if ((focused == host.hgn$availablePacks() && state.pane() != Pane.AVAILABLE)
			|| (focused == host.hgn$selectedPacks() && state.pane() != Pane.SELECTED)) arrange(true);
	}

	private void arrange(boolean followFocus) {
		if (!owns() || arranging || !HgnPackSelectionLayout.supported(screen.width, screen.height)) return;
		arranging = true;
		try {
			var focused = screen.getFocused();
			state.arrange(screen.width, screen.height, search != null, focused, followFocus);
			var layout = state.layout();
			place(title, layout.title());
			place(hint, layout.hint());
			if (search != null) place(search, layout.search());
			place(openFolder, layout.openFolder());
			place(host.hgn$packDone(), layout.done());
			placeList(host.hgn$availablePacks(), layout.availableList());
			placeList(host.hgn$selectedPacks(), layout.selectedList());
			for (var list : List.of(host.hgn$availablePacks(), host.hgn$selectedPacks())) {
				boolean visible = state.visible().contains(list);
				if (visible && !screen.children().contains(list)) host.hgn$packAdd(list);
				else if (!visible && screen.children().contains(list)) host.hgn$packRemove(list);
			}
			place(availableTab, layout.availableTab());
			place(selectedTab, layout.selectedTab());
			availableTab.selected(state.pane() == Pane.AVAILABLE);
			selectedTab.selected(state.pane() == Pane.SELECTED);
			for (var tab : List.of(availableTab, selectedTab)) {
				if (!layout.dualPane() && !screen.children().contains(tab)) host.hgn$packAdd(tab);
				else if (layout.dualPane() && screen.children().contains(tab)) host.hgn$packRemove(tab);
			}
			if (focused != null && !screen.children().contains(focused)
				&& (focused == availableTab || focused == selectedTab || focused == host.hgn$availablePacks() || focused == host.hgn$selectedPacks())) {
				if (layout.dualPane()) screen.setFocused(state.pane() == Pane.AVAILABLE ? host.hgn$availablePacks() : host.hgn$selectedPacks());
				else screen.setFocused(state.pane() == Pane.AVAILABLE ? availableTab : selectedTab);
			}
		} finally { arranging = false; }
	}

	/** Restore registrations/geometry only; never calls onClose, model.commit, reload or watcher cleanup. */
	public void dispose() { dispose(true); }

	/** Native rebuildWidgets has already cleared its registry; never resurrect that old roster. */
	public void disposeForReinit() { dispose(false); }

	private void dispose(boolean restoreRegistration) {
		if (state.disposed()) return;
		boolean owner = owns();
		state.dispose();
		if (!owner) return;
		var focused = screen.getFocused();
		host.hgn$packRemove(availableTab);
		host.hgn$packRemove(selectedTab);
		for (var widget : original) {
			if (widget instanceof TransferableSelectionList list) placeList(list, nativeBounds.get(widget));
			else place(widget, nativeBounds.get(widget));
			if (restoreRegistration && !screen.children().contains(widget)) host.hgn$packAdd(widget);
		}
		if (restoreRegistration && (focused == availableTab || focused == selectedTab))
			screen.setFocused(state.pane() == Pane.AVAILABLE ? host.hgn$availablePacks() : host.hgn$selectedPacks());
	}

	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!owns() || state.layout() == null) return;
		var layout = state.layout();
		HgnScreenBackdrop.render(screen, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.header(), true);
		HgnSurface.panel(graphics, layout.footer(), false);
		if (layout.dualPane() || state.pane() == Pane.AVAILABLE) HgnSurface.panel(graphics, layout.availablePanel(), true);
		if (layout.dualPane() || state.pane() == Pane.SELECTED) HgnSurface.panel(graphics, layout.selectedPanel(), true);
		if (!layout.compact()) HgnTypography.fitted(graphics, Minecraft.getInstance().font, "HGN / PACK MANAGER",
			layout.header().x() + 8, layout.header().y() + 9, layout.header().width() - 16, 0.85f, HgnPalette.CYAN);
	}

	private static boolean messageIs(Component first, Component second) { return first.getContents().equals(second.getContents()); }
	private static HgnRect bounds(AbstractWidget widget) { return new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()); }
	private static void place(AbstractWidget widget, HgnRect rect) {
		widget.setX(rect.x()); widget.setY(rect.y()); widget.setWidth(rect.width()); widget.setHeight(rect.height());
	}
	private static void placeList(TransferableSelectionList list, HgnRect rect) {
		list.setX(rect.x());
		list.updateSizeAndPosition(rect.width(), rect.height(), rect.y());
	}
}
