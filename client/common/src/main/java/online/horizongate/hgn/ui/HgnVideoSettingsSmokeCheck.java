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
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.screen.HgnVideoSettingsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Disposable CI inspection: native construction resets GPU warnings, but no native actions are exercised. */
public final class HgnVideoSettingsSmokeCheck {
	private HgnVideoSettingsSmokeCheck() {
	}

	public static void run() {
		if (!Boolean.getBoolean("hgn.smokeWorldFixture")) return;
		if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
			|| !"container-smoke".equals(System.getProperty("hgn.devscreen"))) {
			throw new IllegalStateException("Video inspection requires the explicitly disposable CI menu-smoke fixture");
		}
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		int expectedControls = switch (HgnBuild.MINECRAFT_VERSION) {
			case "1.21", "1.21.1" -> 25;
			case "1.21.2", "1.21.3", "1.21.4", "1.21.5" -> 26;
			case "1.21.11" -> 29;
			default -> 27;
		};
		int expectedHeadings = "1.21.11".equals(HgnBuild.MINECRAFT_VERSION) ? 3 : 0;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 260}, {836, 471}, {900, 320}, {1280, 720}}) {
			NativeVideoScreen original = new NativeVideoScreen(parent, minecraft, minecraft.options);
			HgnVideoSettingsScreen screen = new HgnVideoSettingsScreen(parent, minecraft, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			assertNavigation(screen, size);
			List<AbstractWidget> retained = screen.nativeControls();
			long headings = retained.stream().filter(widget -> widget instanceof StringWidget).count();
			if (retained.size() != original.nativeControls().size() || retained.size() - headings != expectedControls
				|| headings != expectedHeadings) throw new AssertionError("Native video controls or headings changed");
			List<AbstractWidget> pinned = List.of(screen.nativeOption(minecraft.options.guiScale()), screen.nativeOption(minecraft.options.fullscreen()));
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			assertInitialFocus(original, screen);
			screen.setFocused(null);
			screen.showPage(screen.pageCount() - 1);
			HgnCompat.resizeScreen(screen, 1280, 720);
			HgnCompat.resizeScreen(screen, size[0], size[1]);
			assertNavigation(screen, size);
			screen.showPage(0);
			if (screen.getFocused() != null) throw new AssertionError("Unfocused video pagination claimed focus");
			HashSet<AbstractWidget> visited = new HashSet<>(pinned);
			int pages = screen.pageCount();
			for (int page = 0; page < pages; page++) {
				screen.showPage(page);
				assertParity(original, screen);
				assertBounds(screen);
				for (AbstractWidget pin : pinned) if (!screen.children().contains(pin)) throw new AssertionError("Display control was paged away");
				List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains).filter(widget -> !pinned.contains(widget)).toList();
				if (visible.isEmpty()) throw new AssertionError("Video page contains no native entries");
				for (AbstractWidget widget : visible) {
					if (!visited.add(widget)) throw new AssertionError("Native video entry appears on multiple pages");
					if (!(widget instanceof StringWidget) && widget.active && widget.visible) assertResize(screen, widget, size);
					assertParity(original, screen);
					assertCycleValues(cycleValues);
					assertUnchanged(screen, parent, originalValues);
				}
				for (AbstractWidget pin : pinned) if (pin.active && pin.visible) assertResize(screen, pin, size);
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native video controls or headings are unreachable");
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Video resize replaced original widgets");
			assertParity(original, screen);
			assertCycleValues(cycleValues);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_VIDEO_SETTINGS_SMOKE_OK: disposable CI fixture only; native constructors reset GPU warnings; detached roster/headings, values, narration, focus/page/resize and parent checked; no input, rendering, callbacks, Done/save or world launch exercised");
	}

	private static void assertNavigation(Screen screen, int[] size) {
		boolean reference = HgnDashboardLayout.resolve(size[0], size[1]).scale() >= 0.75f;
		List<String> labels = screen.children().stream().filter(child -> child instanceof HgnButton)
			.map(child -> ((HgnButton) child).getMessage().getString()).toList();
		for (String label : List.of("PLAY", "SINGLEPLAYER", "MULTIPLAYER", "SERVERS",
			"COSMETICS", "MODS", "SETTINGS", "EXIT")) {
			if (labels.contains(label) != reference)
				throw new AssertionError("Reference Video navigation visibility changed: " + label);
		}
	}

	private static void assertResize(HgnVideoSettingsScreen screen, AbstractWidget widget, int[] size) {
		Tooltip tooltip = tooltip(widget);
		screen.setFocused(widget);
		HgnCompat.resizeScreen(screen, 1280, 720);
		assertFocused(screen, widget, tooltip);
		HgnCompat.resizeScreen(screen, size[0], size[1]);
		assertFocused(screen, widget, tooltip);
	}

	private static void assertCycleValues(Map<CycleButton<?>, Object> values) {
		values.forEach((widget, value) -> {
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Video paging changed a native cycle value");
		});
	}

	private static List<OptionInstance<?>> stableOptions(Options options) {
		return List.of(options.guiScale(), options.fullscreen(), options.framerateLimit(), options.enableVsync(),
			options.gamma(), options.renderDistance(), options.simulationDistance(), options.mipmapLevels(),
			options.particles(), options.entityShadows(), options.entityDistanceScaling(), options.biomeBlendRadius(),
			options.cloudStatus(), options.prioritizeChunkUpdates(), options.ambientOcclusion(), options.showAutosaveIndicator(),
			options.attackIndicator(), options.menuBackgroundBlurriness());
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (OptionInstance<?> option : stableOptions(options)) values.add(option.get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Video inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Video inspection changed an existing option value");
	}

	private static void assertParity(NativeVideoScreen original, HgnVideoSettingsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native video title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native video tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native video footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (OptionInstance<?> option : stableOptions(Minecraft.getInstance().options)) assertWidget(original.nativeOption(option), screen.nativeOption(option));
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) throw new AssertionError("Native video list duplicates page input or rendering");
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
			throw new AssertionError("Native video widget type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native video cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial video focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Video focus or tooltip lost on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Video widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) throw new AssertionError("Video widgets overlap");
			}
		}
	}

	private static final class NativeVideoScreen extends VideoSettingsScreen {
		private NativeVideoScreen(Screen parent, Minecraft minecraft, Options options) {
			super(parent, minecraft, options);
		}

		private List<AbstractWidget> nativeControls() {
			return HgnSoundOptionsScreen.optionWidgets(list);
		}

		private AbstractWidget nativeOption(OptionInstance<?> option) {
			return list.findOption(option);
		}
	}
}
