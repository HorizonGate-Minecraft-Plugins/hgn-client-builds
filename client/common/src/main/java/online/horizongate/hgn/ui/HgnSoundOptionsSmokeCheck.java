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
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.sounds.SoundSource;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Visits detached sound groups without pressing a native option, changing audio or saving settings. */
public final class HgnSoundOptionsSmokeCheck {
	private HgnSoundOptionsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		boolean hasMusicControls = !Set.of("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5")
			.contains(HgnBuild.MINECRAFT_VERSION);
		int expectedCount = SoundSource.values().length + (hasMusicControls ? 5 : 3);
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {836, 471}, {1280, 720}}) {
			NativeSoundScreen original = new NativeSoundScreen(parent, minecraft.options);
			HgnSoundOptionsScreen screen = new HgnSoundOptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) {
				if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			}
			if (retained.size() != original.nativeControls().size() || retained.size() != expectedCount) {
				throw new AssertionError("Native sound controls were dropped, including version-specific music controls");
			}
			assertInitialFocus(original, screen);
			Set<AbstractWidget> visited = new HashSet<>();
			for (int group = 0; group < 2; group++) {
				screen.showGroup(group);
				int pages = screen.pageCount();
				for (int page = 0; page < pages; page++) {
					screen.showPage(page);
					assertParity(original, screen);
					assertCycleValues(cycleValues);
					assertBounds(screen);
					List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains).toList();
					if (visible.isEmpty()) throw new AssertionError("Sound pagination produced an empty native-control page");
					for (AbstractWidget widget : visible) {
						if (!visited.add(widget)) throw new AssertionError("Native sound control appears in multiple groups or pages");
						var tooltip = tooltip(widget);
						if (!widget.active || !widget.visible) continue;
						screen.setFocused(widget);
						HgnCompat.resizeScreen(screen, 836, 471);
						assertFocused(screen, widget, tooltip);
						assertCycleValues(cycleValues);
						HgnCompat.resizeScreen(screen, size[0], size[1]);
						assertFocused(screen, widget, tooltip);
						assertParity(original, screen);
						assertCycleValues(cycleValues);
						if (!screen.nativeControls().equals(retained)) throw new AssertionError("Sound resize replaced native widgets");
						assertUnchanged(screen, parent, originalValues);
					}
				}
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native sound controls are unreachable");
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_SOUND_OPTIONS_SMOKE_OK: native controls/values, music options, tooltips, group/page reachability, focus/resize bounds and parent preserved; no audio change, device reload, native option press, Done/save or rendering invoked");
	}

	private static void assertCycleValues(Map<CycleButton<?>, Object> values) {
		values.forEach((widget, value) -> {
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Sound paging changed a retained native cycle value");
		});
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (SoundSource source : SoundSource.values()) values.add(options.getSoundSourceOptionInstance(source).get());
		values.add(options.soundDevice().get());
		values.add(options.showSubtitles().get());
		values.add(options.directionalAudio().get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Sound inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Sound inspection changed an existing setting");
	}

	private static void assertParity(NativeSoundScreen original, HgnSoundOptionsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native sound title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) assertWidget(expected.get(index), actual.get(index));
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native sound footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (SoundSource source : SoundSource.values()) {
			OptionInstance<?> option = Minecraft.getInstance().options.getSoundSourceOptionInstance(source);
			assertWidget(original.nativeOption(option), screen.nativeOption(option));
		}
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Native sound list duplicates page input or rendering");
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
			throw new AssertionError("Native sound widget type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native sound cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial sound focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) {
			assertWidget(expectedWidget, actualWidget);
		}
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Sound focus or tooltip was lost when paging on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) {
				throw new AssertionError("Sound page widget outside viewport");
			}
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Sound page widgets overlap");
				}
			}
		}
	}

	private static final class NativeSoundScreen extends SoundOptionsScreen {
		private NativeSoundScreen(Screen parent, Options options) {
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
