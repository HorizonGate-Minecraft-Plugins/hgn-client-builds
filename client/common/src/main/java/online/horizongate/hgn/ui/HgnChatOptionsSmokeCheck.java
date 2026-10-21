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
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import online.horizongate.hgn.HgnBuild;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnChatOptionsScreen;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Detached roster/focus/geometry checks; never sends chat, presses options, renders or saves. */
public final class HgnChatOptionsSmokeCheck {
	private HgnChatOptionsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		int minimumCount = Set.of("1.21.9", "1.21.10", "1.21.11").contains(HgnBuild.MINECRAFT_VERSION) ? 18 : 17;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 260}, {820, 300}, {836, 471}, {1280, 720}}) {
			NativeChatScreen original = new NativeChatScreen(parent, minecraft.options);
			HgnChatOptionsScreen screen = new HgnChatOptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			AbstractWidget visibility = screen.nativeOption(minecraft.options.chatVisibility());
			if (retained.size() != original.nativeControls().size() || retained.size() < minimumCount) {
				throw new AssertionError("Native chat roster changed, including generation-specific draft control");
			}
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) {
				if (widget instanceof CycleButton<?> cycle) cycleValues.put(cycle, cycle.getValue());
			}
			assertInitialFocus(original, screen);
			assertUnfocusedLayout(screen, size);
			Set<AbstractWidget> visited = new HashSet<>(List.of(visibility));
			for (int group = 0; group < 2; group++) {
				screen.showGroup(group);
				int pages = screen.pageCount();
				for (int page = 0; page < pages; page++) {
					screen.showPage(page);
					assertParity(original, screen);
					assertCycleValues(cycleValues);
					assertBounds(screen);
					if (!screen.children().contains(visibility)
						|| screen.nativeOption(minecraft.options.chatVisibility()) != visibility) {
						throw new AssertionError("Chat visibility was replaced or paged away");
					}
					List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains)
						.filter(widget -> widget != visibility).toList();
					if (visible.isEmpty()) throw new AssertionError("Chat pagination produced an empty preference page");
					for (AbstractWidget widget : visible) {
						if (!visited.add(widget)) throw new AssertionError("Native chat option appears in multiple groups or pages");
						if (widget.active && widget.visible) assertResize(screen, widget, size, cycleValues);
						assertParity(original, screen);
						assertUnchanged(screen, parent, originalValues);
					}
					assertResize(screen, visibility, size, cycleValues);
				}
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native chat controls are unreachable");
			assertGroupFocus(screen);
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Chat resize replaced original widgets");
			assertParity(original, screen);
			assertCycleValues(cycleValues);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_CHAT_OPTIONS_SMOKE_OK: native chat roster, narrator reference/state, values, tooltips, narration, group/page reachability, focus/resize bounds and parent preserved; no input, option press, Done/save or rendering invoked");
	}

	private static void assertResize(HgnChatOptionsScreen screen, AbstractWidget widget, int[] size, Map<CycleButton<?>, Object> values) {
		Tooltip tooltip = tooltip(widget);
		screen.setFocused(widget);
		HgnCompat.resizeScreen(screen, 836, 471);
		assertFocused(screen, widget, tooltip);
		assertCycleValues(values);
		HgnCompat.resizeScreen(screen, size[0], size[1]);
		assertFocused(screen, widget, tooltip);
		assertCycleValues(values);
	}

	private static void assertUnfocusedLayout(HgnChatOptionsScreen screen, int[] size) {
		screen.setFocused(null);
		for (int group : new int[]{1, 0}) {
			screen.showGroup(group);
			screen.showPage(screen.pageCount() - 1);
			HgnCompat.resizeScreen(screen, 836, 471);
			HgnCompat.resizeScreen(screen, size[0], size[1]);
			screen.showPage(0);
			if (screen.getFocused() != null) throw new AssertionError("Unfocused chat layout unexpectedly claimed focus");
			assertBounds(screen);
		}
	}

	private static void assertGroupFocus(HgnChatOptionsScreen screen) {
		screen.showGroup(0);
		screen.showPage(0);
		AbstractWidget display = screen.nativeOption(Minecraft.getInstance().options.chatColors());
		screen.setFocused(display);
		screen.showGroup(1);
		if (screen.children().contains(display) || display.isFocused() || screen.getFocused() == display
			|| !screen.children().contains(screen.getFocused())) throw new AssertionError("Group change retained hidden chat focus");
		assertBounds(screen);
	}

	private static void assertCycleValues(Map<CycleButton<?>, Object> values) {
		values.forEach((widget, value) -> {
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Chat paging changed a native cycle value");
		});
	}

	private static List<OptionInstance<?>> stableOptions(Options options) {
		return List.of(options.chatVisibility(), options.chatColors(), options.chatLinks(), options.chatLinksPrompt(),
			options.chatOpacity(), options.textBackgroundOpacity(), options.chatScale(), options.chatLineSpacing(),
			options.chatDelay(), options.chatWidth(), options.chatHeightFocused(), options.chatHeightUnfocused(),
			options.narrator(), options.autoSuggestions(), options.hideMatchedNames(), options.reducedDebugInfo(), options.onlyShowSecureChat());
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (OptionInstance<?> option : stableOptions(options)) values.add(option.get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Chat inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Chat inspection changed an existing setting");
	}

	private static void assertParity(NativeChatScreen original, HgnChatOptionsScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native chat title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native chat tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native chat footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		for (OptionInstance<?> option : stableOptions(Minecraft.getInstance().options)) {
			assertWidget(original.nativeOption(option), screen.nativeOption(option));
		}
		if (screen.nativeNarratorControl() != screen.nativeOption(Minecraft.getInstance().options.narrator())
			|| original.nativeNarratorControl() != original.nativeOption(Minecraft.getInstance().options.narrator())) {
			throw new AssertionError("Native narrator shortcut reference was replaced or detached from its option");
		}
		assertWidget(original.nativeNarratorControl(), screen.nativeNarratorControl());
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Native chat list duplicates page input or rendering");
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
			throw new AssertionError("Native chat control type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native chat cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial chat focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Chat focus or tooltip was lost on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Chat widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Chat page widgets overlap");
				}
			}
		}
	}

	private static final class NativeChatScreen extends ChatOptionsScreen {
		private NativeChatScreen(Screen parent, Options options) {
			super(parent, options);
		}

		private List<AbstractWidget> nativeControls() {
			return HgnSoundOptionsScreen.optionWidgets(list);
		}

		private AbstractWidget nativeOption(OptionInstance<?> option) {
			return list.findOption(option);
		}

		private AbstractWidget nativeNarratorControl() {
			return narratorButton;
		}
	}
}
