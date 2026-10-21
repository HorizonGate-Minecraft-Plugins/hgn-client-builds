package online.horizongate.hgn.ui;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnMouseSettingsScreen;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Inspects detached native mouse controls without input, option callbacks, rendering or saves. */
public final class HgnMouseSettingsSmokeCheck {
	private HgnMouseSettingsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		int baseCount = Set.of("1.21.9", "1.21.10", "1.21.11").contains(HgnBuild.MINECRAFT_VERSION) ? 7 : 5;
		int expectedCount = baseCount + (InputConstants.isRawMouseInputSupported() ? 1 : 0);
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {620, 260}, {836, 471}, {1280, 720}}) {
			NativeMouseScreen original = new NativeMouseScreen(parent, minecraft.options);
			HgnMouseSettingsScreen screen = new HgnMouseSettingsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			AbstractWidget sensitivity = screen.nativeOption(minecraft.options.sensitivity());
			if (retained.size() != original.nativeControls().size() || retained.size() != expectedCount) {
				throw new AssertionError("Native mouse roster changed, including optional raw input or generation-specific controls");
			}
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) {
				if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			}
			assertInitialFocus(original, screen);
			assertUnfocusedLayout(screen, size);
			Set<AbstractWidget> visited = new HashSet<>(List.of(sensitivity));
			for (int group = 0; group < 2; group++) {
				screen.showGroup(group);
				int pages = screen.pageCount();
				for (int page = 0; page < pages; page++) {
					screen.showPage(page);
					assertParity(original, screen);
					assertCycleValues(cycleValues);
					assertBounds(screen);
					if (!screen.children().contains(sensitivity)
						|| screen.nativeOption(minecraft.options.sensitivity()) != sensitivity) {
						throw new AssertionError("Sensitivity was replaced or paged away");
					}
					List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains)
						.filter(widget -> widget != sensitivity).toList();
					if (visible.isEmpty()) throw new AssertionError("Mouse pagination produced an empty preference page");
					for (AbstractWidget widget : visible) {
						if (!visited.add(widget)) throw new AssertionError("Native mouse option appears in multiple groups or pages");
						if (widget.active && widget.visible) assertResize(screen, widget, size, cycleValues);
						assertParity(original, screen);
						assertUnchanged(screen, parent, originalValues);
					}
					assertResize(screen, sensitivity, size, cycleValues);
				}
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native mouse controls are unreachable");
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Mouse resize replaced original widgets");
			assertParity(original, screen);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_MOUSE_SETTINGS_SMOKE_OK: native mouse roster, sensitivity, values, tooltips, narration, group/page reachability, focus/resize bounds and parent preserved; no input, option press, Done/save or rendering invoked");
	}

	private static void assertUnfocusedLayout(HgnMouseSettingsScreen screen, int[] size) {
		screen.setFocused(null);
		for (int group : new int[]{1, 0}) {
			screen.showGroup(group);
			screen.showPage(screen.pageCount() - 1);
			HgnCompat.resizeScreen(screen, 836, 471);
			HgnCompat.resizeScreen(screen, size[0], size[1]);
			screen.showPage(0);
			if (screen.getFocused() != null) throw new AssertionError("Unfocused mouse layout unexpectedly claimed focus");
			assertBounds(screen);
		}
	}

	private static void assertResize(HgnMouseSettingsScreen screen, AbstractWidget widget, int[] size, Map<CycleButton<?>, Object> values) {
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
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Mouse paging changed a native cycle value");
		});
	}

	private static List<OptionInstance<?>> stableOptions(Options options) {
		return List.of(options.sensitivity(), options.mouseWheelSensitivity(), options.discreteMouseScroll(),
			options.touchscreen(), options.rawMouseInput());
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (OptionInstance<?> option : stableOptions(options)) values.add(option.get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Mouse inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Mouse inspection changed an existing setting");
	}

	private static void assertParity(NativeMouseScreen original, HgnMouseSettingsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native mouse title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native mouse tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native mouse footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (OptionInstance<?> option : stableOptions(Minecraft.getInstance().options)) {
			AbstractWidget originalWidget = original.nativeOption(option);
			if (originalWidget != null) assertWidget(originalWidget, screen.nativeOption(option));
		}
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Native mouse list duplicates page input or rendering");
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
			throw new AssertionError("Native mouse control type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native mouse cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial mouse focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Mouse focus or tooltip was lost on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Mouse widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Mouse page widgets overlap");
				}
			}
		}
	}

	private static final class NativeMouseScreen extends MouseSettingsScreen {
		private NativeMouseScreen(Screen parent, Options options) {
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
