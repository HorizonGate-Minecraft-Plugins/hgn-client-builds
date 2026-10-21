package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnControlsScreen;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Inspects detached native-backed controls without navigating, rebinding or changing preferences. */
public final class HgnControlsSmokeCheck {
	private HgnControlsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		int expectedCount = Set.of("1.21.9", "1.21.10", "1.21.11").contains(HgnBuild.MINECRAFT_VERSION) ? 9 : 6;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {836, 471}, {1280, 720}}) {
			NativeControlsScreen original = new NativeControlsScreen(parent, minecraft.options);
			HgnControlsScreen screen = new HgnControlsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			List<AbstractWidget> destinations = screen.nativeDestinations();
			if (retained.size() != original.nativeControls().size() || retained.size() != expectedCount || destinations.size() != 2) {
				throw new AssertionError("Native controls roster changed, including generation-specific preferences or destinations");
			}
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) {
				if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			}
			assertInitialFocus(original, screen);
			Set<AbstractWidget> visited = new HashSet<>(destinations);
			int pages = screen.pageCount();
			for (int page = 0; page < pages; page++) {
				screen.showPage(page);
				assertParity(original, screen);
				assertCycleValues(cycleValues);
				assertBounds(screen);
				if (!screen.children().containsAll(destinations) || !screen.nativeDestinations().equals(destinations)) {
					throw new AssertionError("Native Mouse/Keybinds destinations were replaced or paged away");
				}
				List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains)
					.filter(widget -> !destinations.contains(widget)).toList();
				if (visible.isEmpty()) throw new AssertionError("Controls pagination produced an empty preference page");
				for (AbstractWidget widget : visible) {
					if (!visited.add(widget)) throw new AssertionError("Native preference appears on multiple pages");
					if (!widget.active || !widget.visible) continue;
					assertResize(screen, widget, size, cycleValues);
					assertParity(original, screen);
					assertUnchanged(screen, parent, originalValues);
				}
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native control preferences are unreachable");
			for (AbstractWidget destination : destinations) {
				if (destination.active && destination.visible) assertResize(screen, destination, size, cycleValues);
			}
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Controls resize replaced native widgets");
			assertParity(original, screen);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_CONTROLS_SMOKE_OK: native destinations/preferences, values, tooltips, narration, page reachability, focus/resize bounds and parent preserved; no option press, navigation, key rebind, Done/save or rendering invoked");
	}

	private static void assertResize(HgnControlsScreen screen, AbstractWidget widget, int[] size, Map<CycleButton<?>, Object> values) {
		Tooltip tooltip = tooltip(widget);
		screen.setFocused(widget);
		HgnCompat.resizeScreen(screen, 836, 471);
		assertFocused(screen, widget, tooltip);
		assertCycleValues(values);
		HgnCompat.resizeScreen(screen, size[0], size[1]);
		assertFocused(screen, widget, tooltip);
		assertCycleValues(values);
	}

	private static void assertCycleValues(Map<CycleButton<?>, Object> values) {
		values.forEach((widget, value) -> {
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Controls paging changed a retained native cycle value");
		});
	}

	private static List<OptionInstance<?>> stableOptions(Options options) {
		return List.of(options.toggleCrouch(), options.toggleSprint(), options.autoJump(), options.operatorItemsTab());
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (OptionInstance<?> option : stableOptions(options)) values.add(option.get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Controls inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Controls inspection changed an existing setting");
	}

	private static void assertParity(NativeControlsScreen original, HgnControlsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native controls title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native control tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native controls footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (OptionInstance<?> option : stableOptions(Minecraft.getInstance().options)) {
			assertWidget(original.nativeOption(option), screen.nativeOption(option));
		}
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Native controls list duplicates page input or rendering");
		}
	}

	private static List<AbstractWidget> actions(Screen screen, List<AbstractWidget> controls) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget
			&& !(child instanceof OptionsList) && !(child instanceof StringWidget)
			&& !(child instanceof HgnSettingsHeader) && !(child instanceof HgnButton) && !controls.contains(child))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertWidget(AbstractWidget expected, AbstractWidget actual) {
		if (expected == null || actual == null || expected.getClass() != actual.getClass()
			|| !expected.getMessage().equals(actual.getMessage()) || expected.active != actual.active
			|| expected.visible != actual.visible || (tooltip(expected) == null) != (tooltip(actual) == null)) {
			throw new AssertionError("Native control type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native control cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial controls focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Control focus or tooltip was lost when paging on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Controls widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Controls page widgets overlap");
				}
			}
		}
	}

	private static final class NativeControlsScreen extends ControlsScreen {
		private NativeControlsScreen(Screen parent, Options options) {
			super(parent, options);
		}

		private List<AbstractWidget> nativeControls() {
			return HgnSoundOptionsScreen.optionWidgets(list);
		}

		private AbstractWidget nativeOption(OptionInstance<?> option) {
			return list.findOption(option);
		}
	}
}
