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
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnCreateWorldLayout;
import online.horizongate.hgn.ui.HgnCreateWorldPageState;
import online.horizongate.hgn.ui.HgnCreateWorldPageState.Row;
import online.horizongate.hgn.ui.HgnCreateWorldPageState.RowKind;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** In-place native-tab composition; the original screen alone owns creation and resource cleanup. */
public final class HgnCreateWorldScreen {
	public interface Host {
		TabManager hgn$creationTabs();
		TabNavigationBar hgn$creationNavigation();
		int hgn$creationFooterHeight();
		void hgn$creationAdd(AbstractWidget widget);
		void hgn$creationRemove(AbstractWidget widget);
		HgnCreateWorldScreen hgn$creationComposition();
	}

	public interface TabHost {
		void hgn$bindCreation(HgnCreateWorldScreen composition);
	}

	private final CreateWorldScreen screen;
	private final Host host;
	private final HgnCreateWorldPageState<AbstractWidget> pages;
	private final Map<Tab, HgnCreateWorldPageState<AbstractWidget>> tabPages = new IdentityHashMap<>();
	private final Map<Tab, StringWidget> tabTitles = new IdentityHashMap<>();
	private final Set<Tab> nativeTabs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<AbstractWidget, HgnRect> nativeBounds = new IdentityHashMap<>();
	private final HgnButton previous;
	private final HgnButton next;

	/** Unknown or ambiguous native layouts retain vanilla arrangement rather than rebuilding controls. */
	public static HgnCreateWorldScreen attach(CreateWorldScreen screen, Host host) {
		Tab game = host.hgn$creationTabs().getCurrentTab();
		if (game == null || !game.getTabTitle().getContents().equals(Component.translatable("createWorld.tab.game.title").getContents())) return null;
		List<AbstractWidget> original = new ArrayList<>();
		game.visitChildren(original::add);
		if (!uniqueRoster(original)) return null;
		var label = Component.translatable("selectWorld.enterName").getContents();
		List<AbstractWidget> labels = original.stream().filter(widget -> widget instanceof StringWidget && widget.getMessage().getContents().equals(label)).toList();
		List<AbstractWidget> names = original.stream().filter(widget -> widget instanceof EditBox && widget.getMessage().getContents().equals(label)).toList();
		if (labels.size() != 1 || names.size() != 1) return null;
		return new HgnCreateWorldScreen(screen, host, game, original, labels.getFirst(), (EditBox) names.getFirst());
	}

	private HgnCreateWorldScreen(CreateWorldScreen screen, Host host, Tab game,
		List<AbstractWidget> original, AbstractWidget nameLabel, EditBox name) {
		this.screen = screen;
		this.host = host;
		pages = new HgnCreateWorldPageState<>(original, nameLabel, name);
		tabPages.put(game, pages);
		for (var widget : original) nativeBounds.put(widget, new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
		var title = new StringWidget(screen.getTitle(), Minecraft.getInstance().font);
		HgnCompat.leftAlignLanguageLabel(title);
		tabTitles.put(game, title);
		previous = HgnButton.centered(0, 0, 1, 20, Component.translatable("spectatorMenu.previous_page"), false,
			button -> navigate(-1));
		next = HgnButton.centered(0, 0, 1, 20, Component.translatable("spectatorMenu.next_page"), false,
			button -> navigate(1));
	}

	public boolean owns(TabManager manager) {
		return !pages.disposed() && host.hgn$creationComposition() == this && host.hgn$creationTabs() == manager;
	}

	public void arrange() {
		var active = tabPages.get(host.hgn$creationTabs().getCurrentTab());
		arrange(active == null || active.layout() == null ? 0 : active.layout().page(), true);
	}

	public void showPage(int page) {
		arrange(page, false);
	}

	public boolean isComposedTab() {
		return owns(host.hgn$creationTabs()) && tabPages.containsKey(host.hgn$creationTabs().getCurrentTab());
	}

	private void navigate(int delta) {
		if (!owns(host.hgn$creationTabs())) return;
		var active = tabPages.get(host.hgn$creationTabs().getCurrentTab());
		if (active != null && active.layout() != null) showPage(active.layout().page() + delta);
	}

	/** Capture only after the native TabManager has laid out and registered this exact tab. */
	private HgnCreateWorldPageState<AbstractWidget> capture(Tab tab) {
		if (tab == null || nativeTabs.contains(tab)) return null;
		if (tabPages.containsKey(tab)) return tabPages.get(tab);
		List<AbstractWidget> original = new ArrayList<>();
		tab.visitChildren(original::add);
		HgnCreateWorldPageState<AbstractWidget> captured = null;
		if (uniqueRoster(original) && original.stream().allMatch(widget -> widget.getHeight() <= 24)) {
			if (messageIs(tab.getTabTitle(), "createWorld.tab.more.title")) {
				captured = new HgnCreateWorldPageState<>(HgnCreateWorldLayout.Kind.MORE, original, List.of(),
					original.stream().map(widget -> new Row<>(RowKind.SINGLE, List.of(widget))).toList());
			} else if (messageIs(tab.getTabTitle(), "createWorld.tab.world.title")) captured = captureWorld(original);
		}
		if (captured == null) {
			nativeTabs.add(tab);
			return null;
		}
		tabPages.put(tab, captured);
		for (var widget : original) nativeBounds.put(widget, new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
		var heading = new StringWidget(tab.getTabTitle(), Minecraft.getInstance().font);
		HgnCompat.leftAlignLanguageLabel(heading);
		tabTitles.put(tab, heading);
		return captured;
	}

	private static boolean uniqueRoster(List<AbstractWidget> original) {
		Set<AbstractWidget> identities = Collections.newSetFromMap(new IdentityHashMap<>());
		identities.addAll(original);
		return !original.isEmpty() && identities.size() == original.size();
	}

	private static HgnCreateWorldPageState<AbstractWidget> captureWorld(List<AbstractWidget> original) {
		var seedLabels = original.stream().filter(widget -> widget instanceof StringWidget && messageIs(widget.getMessage(), "selectWorld.enterSeed")).toList();
		var seeds = original.stream().filter(widget -> widget instanceof EditBox && messageIs(widget.getMessage(), "selectWorld.enterSeed")).toList();
		var presets = original.stream().filter(widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof WorldCreationUiState.WorldTypeEntry).toList();
		var customize = original.stream().filter(widget -> widget instanceof Button && messageIs(widget.getMessage(), "selectWorld.customizeType")).toList();
		if (seedLabels.size() != 1 || seeds.size() != 1 || presets.size() != 1 || customize.size() != 1) return null;
		List<AbstractWidget> pinned = List.of(seedLabels.getFirst(), seeds.getFirst());
		List<Row<AbstractWidget>> rows = new ArrayList<>();
		rows.add(new Row<>(RowKind.PAIR, List.of(presets.getFirst(), customize.getFirst())));
		Set<AbstractWidget> used = Collections.newSetFromMap(new IdentityHashMap<>());
		used.addAll(pinned);
		used.addAll(rows.getFirst().widgets());
		for (String key : List.of("selectWorld.mapFeatures", "selectWorld.bonusItems")) {
			var labels = original.stream().filter(widget -> widget instanceof StringWidget && messageIs(widget.getMessage(), key)).toList();
			if (labels.size() != 1) return null;
			var label = labels.getFirst();
			var switches = original.stream().filter(widget -> widget instanceof CycleButton<?> cycle
				&& cycle.getValue() instanceof Boolean && !used.contains(widget)).toList();
			int paired = HgnCreateWorldPageState.pairedSwitchIndex(
				new HgnRect(label.getX(), label.getY(), label.getWidth(), label.getHeight()),
				switches.stream().map(widget -> new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())).toList());
			if (paired < 0) return null;
			rows.add(new Row<>(RowKind.LABELED, List.of(label, switches.get(paired))));
			used.addAll(rows.getLast().widgets());
		}
		for (var widget : original) if (!used.contains(widget)) rows.add(new Row<>(RowKind.SINGLE, List.of(widget)));
		return new HgnCreateWorldPageState<>(HgnCreateWorldLayout.Kind.WORLD, original, pinned, rows);
	}

	private static boolean messageIs(Component message, String key) {
		return message.getContents().equals(Component.translatable(key).getContents());
	}

	private void arrange(int page, boolean keepFocusedPage) {
		if (!owns(host.hgn$creationTabs())) return;
		var focused = screen.getFocused();
		Tab tab = host.hgn$creationTabs().getCurrentTab();
		var active = capture(tab);
		if (active == null) {
			removeChrome();
			if (focused == previous || focused == next || tabTitles.containsValue(focused)) screen.setFocused(host.hgn$creationNavigation());
			return;
		}
		active.arrange(screen.width, screen.height, host.hgn$creationNavigation().getRectangle().bottom(),
			host.hgn$creationFooterHeight(), page, keepFocusedPage,
			focused instanceof AbstractWidget widget ? widget : null);
		var layout = active.layout();
		var heading = tabTitles.get(tab);
		for (var other : tabTitles.values()) if (other != heading) host.hgn$creationRemove(other);
		place(heading, layout.title());
		if (!active.pinned().isEmpty()) {
			place(active.pinned().get(0), layout.nameLabel());
			place(active.pinned().get(1), layout.name());
		}
		for (var slot : layout.actions()) {
			var row = active.rows().get(slot.index());
			var bounds = row.bounds(slot.bounds());
			for (int index = 0; index < row.widgets().size(); index++) place(row.widgets().get(index), bounds.get(index));
		}
		for (var widget : active.original()) {
			if (active.visible().contains(widget) && !screen.children().contains(widget)) host.hgn$creationAdd(widget);
			else if (!active.visible().contains(widget) && screen.children().contains(widget)) host.hgn$creationRemove(widget);
		}
		if (!screen.children().contains(heading)) host.hgn$creationAdd(heading);
		place(previous, layout.previous());
		place(next, layout.next());
		previous.active = layout.page() > 0;
		next.active = layout.page() + 1 < layout.pages();
		for (var navigation : List.of(previous, next)) {
			if (layout.pages() > 1 && !screen.children().contains(navigation)) host.hgn$creationAdd(navigation);
			else if (layout.pages() == 1 && screen.children().contains(navigation)) host.hgn$creationRemove(navigation);
		}
		if (focused != null && !screen.children().contains(focused)
			&& (focused == previous || focused == next || tabTitles.containsValue(focused) || active.original().contains(focused)))
			screen.setFocused(active.pinned().isEmpty() ? host.hgn$creationNavigation() : active.pinned().getLast());
	}

	/** Only registrations and geometry are restored; no close/cleanup or creation callback is invoked. */
	public void dispose() {
		if (pages.disposed()) return;
		boolean owner = owns(host.hgn$creationTabs());
		for (var state : tabPages.values()) state.dispose();
		if (owner) ((TabHost) host.hgn$creationTabs()).hgn$bindCreation(null);
		var focused = screen.getFocused();
		removeChrome();
		for (var widget : nativeBounds.keySet()) {
			widget.setWidth(nativeBounds.get(widget).width());
			widget.setHeight(nativeBounds.get(widget).height());
		}
		Tab tab = host.hgn$creationTabs().getCurrentTab();
		if (owner && tabPages.containsKey(tab)) {
			for (var widget : tabPages.get(tab).original()) if (!screen.children().contains(widget)) host.hgn$creationAdd(widget);
			int top = host.hgn$creationNavigation().getRectangle().bottom();
			tab.doLayout(new ScreenRectangle(0, top, screen.width, screen.height - top - host.hgn$creationFooterHeight()));
		}
		if (owner && (focused == previous || focused == next || tabTitles.containsValue(focused))) screen.setFocused(host.hgn$creationNavigation());
	}

	private void removeChrome() {
		for (var heading : tabTitles.values()) host.hgn$creationRemove(heading);
		host.hgn$creationRemove(previous);
		host.hgn$creationRemove(next);
	}

	public void renderChrome(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!owns(host.hgn$creationTabs())) return;
		HgnScreenBackdrop.render(screen, graphics, mouseX, mouseY);
		var active = tabPages.get(host.hgn$creationTabs().getCurrentTab());
		if (active == null || active.layout() == null) return;
		var layout = active.layout();
		HgnSurface.panel(graphics, layout.header(), true);
		if (active.kind() != HgnCreateWorldLayout.Kind.MORE) HgnSurface.panel(graphics, layout.identity(), true);
		HgnSurface.panel(graphics, layout.content(), false);
		HgnSurface.panel(graphics, layout.footer(), false);
		var font = Minecraft.getInstance().font;
		if (!layout.compact()) HgnTypography.fitted(graphics, font, "HGN / CREATE WORLD", layout.title().x(),
			layout.header().y() + 10, layout.title().width(), 0.85f, HgnPalette.CYAN);
		if (layout.sideBySide()) HgnTypography.fitted(graphics, font,
			active.kind() == HgnCreateWorldLayout.Kind.WORLD ? "WORLD SEED" : "WORLD IDENTITY", layout.identity().x() + 12,
			layout.identity().y() + 12, layout.identity().width() - 24, 0.85f, HgnPalette.CYAN);
		if (active.kind() == HgnCreateWorldLayout.Kind.MORE) for (var slot : layout.actions()) {
			HgnSurface.panel(graphics, slot.frame(), true);
			if (!layout.compact()) HgnTypography.fitted(graphics, font, "NATIVE WORLD OPTIONS / " + (slot.index() + 1),
				slot.frame().x() + 8, slot.frame().y() + 12, slot.frame().width() - 16, 0.8f, HgnPalette.CYAN);
		}
	}

	private static void place(AbstractWidget widget, HgnRect bounds) {
		widget.setX(bounds.x());
		widget.setY(bounds.y());
		widget.setWidth(bounds.width());
		widget.setHeight(bounds.height());
	}
}
