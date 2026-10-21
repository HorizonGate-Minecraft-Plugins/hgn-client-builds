package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.screen.HgnAccessibilityOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Detached native accessibility inspection; no input, link confirmation, narrator changes or saves. */
public final class HgnAccessibilityOptionsSmokeCheck {
	private HgnAccessibilityOptionsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		boolean modern = java.util.Set.of("1.21.9", "1.21.10", "1.21.11").contains(HgnBuild.MINECRAFT_VERSION);
		int expectedControls = modern || java.util.Set.of("1.21", "1.21.1").contains(HgnBuild.MINECRAFT_VERSION) ? 25 : 27;
		int expectedHeadings = 0;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 260}, {836, 471}, {900, 360}, {1280, 720}}) {
			NativeAccessibilityScreen original = new NativeAccessibilityScreen(parent, minecraft.options);
			HgnAccessibilityOptionsScreen screen = new HgnAccessibilityOptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			long headings = retained.stream().filter(widget -> widget instanceof StringWidget).count();
			if (retained.size() != original.nativeControls().size() || retained.size() - headings != expectedControls
				|| headings != expectedHeadings) throw new AssertionError("Native accessibility controls or headings changed");
			long destinations = retained.stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.Button).count();
			if (destinations != (modern ? 1 : 0)) throw new AssertionError("Native Controls destination changed");
			List<AbstractWidget> pinned = List.of(screen.narratorControl(), screen.nativeOption(minecraft.options.showSubtitles()));
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			assertInitialFocus(original, screen);
			screen.setFocused(null);
			screen.showPage(screen.pageCount() - 1);
			HgnCompat.resizeScreen(screen, 1280, 720);
			HgnCompat.resizeScreen(screen, size[0], size[1]);
			screen.showPage(0);
			if (screen.getFocused() != null) throw new AssertionError("Unfocused accessibility pagination claimed focus");
			HashSet<AbstractWidget> visited = new HashSet<>(pinned);
			int pages = screen.pageCount();
			for (int page = 0; page < pages; page++) {
				screen.showPage(page);
				assertParity(original, screen);
				assertBounds(screen);
				for (AbstractWidget pin : pinned) if (!screen.children().contains(pin)) throw new AssertionError("Narrator or subtitles were paged away");
				List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains).filter(widget -> !pinned.contains(widget)).toList();
				if (visible.isEmpty()) throw new AssertionError("Accessibility page contains no native entries");
				for (AbstractWidget widget : visible) {
					if (!visited.add(widget)) throw new AssertionError("Native accessibility entry appears on multiple pages");
					if (!(widget instanceof StringWidget) && widget.active && widget.visible) assertResize(screen, widget, size);
					assertParity(original, screen);
					assertCycleValues(cycleValues);
					assertUnchanged(screen, parent, originalValues);
				}
				for (AbstractWidget pin : pinned) if (pin.active && pin.visible) assertResize(screen, pin, size);
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native accessibility controls or headings are unreachable");
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Accessibility resize replaced original widgets");
			assertParity(original, screen);
			assertCycleValues(cycleValues);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_ACCESSIBILITY_OPTIONS_SMOKE_OK: detached native roster, narrator reference/state, Controls destination, Help/Done footer, values, tooltips, narration, focus/page/resize and parent preserved; no input, rendering, callbacks, external link, Done/save or world launch exercised");
	}

	private static void assertResize(HgnAccessibilityOptionsScreen screen, AbstractWidget widget, int[] size) {
		Tooltip tooltip = tooltip(widget);
		screen.setFocused(widget);
		HgnCompat.resizeScreen(screen, 1280, 720);
		assertFocused(screen, widget, tooltip);
		HgnCompat.resizeScreen(screen, size[0], size[1]);
		assertFocused(screen, widget, tooltip);
	}

	private static void assertCycleValues(Map<CycleButton<?>, Object> values) {
		values.forEach((widget, value) -> {
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Accessibility paging changed a native cycle value");
		});
	}

	private static List<OptionInstance<?>> stableOptions(Options options) {
		return List.of(options.narrator(), options.showSubtitles(), options.highContrast(), options.menuBackgroundBlurriness(),
			options.textBackgroundOpacity(), options.backgroundForChatOnly(), options.chatOpacity(), options.chatLineSpacing(),
			options.chatDelay(), options.notificationDisplayTime(), options.bobView(), options.screenEffectScale(),
			options.fovEffectScale(), options.darknessEffectScale(), options.damageTiltStrength(), options.glintSpeed(),
			options.glintStrength(), options.hideLightningFlash(), options.darkMojangStudiosBackground(), options.panoramaSpeed(),
			options.hideSplashTexts(), options.narratorHotkey());
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (OptionInstance<?> option : stableOptions(options)) values.add(option.get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Accessibility inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Accessibility inspection changed an existing option value");
	}

	private static void assertParity(NativeAccessibilityScreen original, HgnAccessibilityOptionsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native accessibility title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native accessibility tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.size() != 2 || expectedActions.size() != actualActions.size()) throw new AssertionError("Native accessibility footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (OptionInstance<?> option : stableOptions(Minecraft.getInstance().options)) {
			if (option == Minecraft.getInstance().options.narrator()) assertWidget(original.narratorControl(), screen.narratorControl());
			else assertWidget(original.nativeOption(option), screen.nativeOption(option));
		}
		if ((original.nativeNarratorReference() == null) != (screen.nativeNarratorReference() == null)
			|| (screen.nativeNarratorReference() != null && screen.nativeNarratorReference() != screen.narratorControl())) {
			throw new AssertionError("Original nullable narrator ownership changed");
		}
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) throw new AssertionError("Native accessibility list duplicates page input or rendering");
	}

	private static List<AbstractWidget> actions(Screen screen, List<AbstractWidget> controls) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
			&& !(widget instanceof OptionsList) && !(widget instanceof StringWidget)
			&& !(widget instanceof HgnSettingsHeader) && !(widget instanceof HgnButton) && !controls.contains(widget))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertWidget(AbstractWidget expected, AbstractWidget actual) {
		if (expected == null || actual == null || expected.getClass() != actual.getClass()
			|| !expected.getMessage().equals(actual.getMessage()) || expected.active != actual.active
			|| expected.visible != actual.visible || (tooltip(expected) == null) != (tooltip(actual) == null)) {
			throw new AssertionError("Native accessibility widget type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native accessibility cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial accessibility focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Accessibility focus or tooltip lost on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Accessibility widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) throw new AssertionError("Accessibility widgets overlap");
			}
		}
	}

	private static final class NativeAccessibilityScreen extends AccessibilityOptionsScreen {
		private NativeAccessibilityScreen(Screen parent, Options options) {
			super(parent, options);
		}

		private List<AbstractWidget> nativeControls() {
			return HgnSoundOptionsScreen.optionWidgets(list);
		}

		private AbstractWidget narratorControl() {
			List<AbstractWidget> narrators = nativeControls().stream().filter(widget -> widget instanceof CycleButton<?> cycle
				&& cycle.getValue() instanceof net.minecraft.client.NarratorStatus).toList();
			if (narrators.size() != 1) throw new AssertionError("Native narrator roster is ambiguous");
			return narrators.getFirst();
		}

		private AbstractWidget nativeNarratorReference() {
			return narratorButton;
		}

		private AbstractWidget nativeOption(OptionInstance<?> option) {
			return list.findOption(option);
		}
	}
}
