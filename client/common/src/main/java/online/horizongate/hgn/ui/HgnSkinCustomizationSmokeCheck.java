package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.world.entity.player.PlayerModelPart;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnSkinCustomizationScreen;
import online.horizongate.hgn.screen.HgnSoundOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Inspects detached skin controls without input, model-part callbacks, rendering or saves. */
public final class HgnSkinCustomizationSmokeCheck {
	private HgnSkinCustomizationSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<Object> originalValues = optionValues(minecraft.options);
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 260}, {740, 300}, {836, 471}, {1280, 720}}) {
			NativeSkinScreen original = new NativeSkinScreen(parent, minecraft.options);
			HgnSkinCustomizationScreen screen = new HgnSkinCustomizationScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			List<AbstractWidget> retained = screen.nativeControls();
			AbstractWidget hand = screen.mainHandControl();
			if (retained.size() != original.nativeControls().size() || retained.size() < PlayerModelPart.values().length + 1) {
				throw new AssertionError("Native skin layers or additional controls were lost");
			}
			if (!(hand instanceof CycleButton<?> cycle) || !Objects.equals(cycle.getValue(), minecraft.options.mainHand().get())) {
				throw new AssertionError("Native main-hand value was not retained");
			}
			Map<CycleButton<?>, Object> cycleValues = new IdentityHashMap<>();
			for (AbstractWidget widget : retained) {
				if (widget instanceof CycleButton<?> value) cycleValues.put(value, value.getValue());
			}
			assertInitialFocus(original, screen);
			Set<AbstractWidget> visited = new HashSet<>(List.of(hand));
			int pages = screen.pageCount();
			for (int page = 0; page < pages; page++) {
				screen.showPage(page);
				assertParity(original, screen);
				assertBounds(screen);
				if (!screen.children().contains(hand) || screen.mainHandControl() != hand) {
					throw new AssertionError("Main hand was replaced or paged away");
				}
				List<AbstractWidget> visible = retained.stream().filter(screen.children()::contains)
					.filter(widget -> widget != hand).toList();
				if (visible.isEmpty()) throw new AssertionError("Skin pagination produced an empty layer page");
				for (AbstractWidget widget : visible) {
					if (!visited.add(widget)) throw new AssertionError("Native skin control appears on multiple pages");
					if (widget.active && widget.visible) assertResize(screen, widget, size, cycleValues);
					assertParity(original, screen);
					assertUnchanged(screen, parent, originalValues);
				}
			}
			if (!visited.equals(new HashSet<>(retained))) throw new AssertionError("Some native skin controls are unreachable");
			assertResize(screen, hand, size, cycleValues);
			if (screen.pageCount() > 1) {
				screen.showPage(0);
				AbstractWidget first = retained.stream().filter(screen.children()::contains).filter(widget -> widget != hand).findFirst().orElseThrow();
				screen.setFocused(first);
				screen.showPage(1);
				if (screen.getFocused() == first || first.isFocused() || !screen.children().contains(screen.getFocused())) {
					throw new AssertionError("Skin paging retained focus on an unavailable control");
				}
			}
			if (!screen.nativeControls().equals(retained)) throw new AssertionError("Skin resize replaced original controls");
			assertCycleValues(cycleValues);
			assertParity(original, screen);
			assertUnchanged(screen, parent, originalValues);
		}
		HgnClient.LOGGER.info("HGN_SKIN_CUSTOMIZATION_SMOKE_OK: native layer/main-hand roster, values, translations, tooltips, narration, page reachability, focus/resize bounds and parent preserved; no input, option press, Done/save or rendering invoked");
	}

	private static void assertResize(HgnSkinCustomizationScreen screen, AbstractWidget widget, int[] size, Map<CycleButton<?>, Object> values) {
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
			if (!Objects.equals(widget.getValue(), value)) throw new AssertionError("Skin paging changed a native cycle value");
		});
	}

	private static List<Object> optionValues(Options options) {
		List<Object> values = new ArrayList<>();
		for (PlayerModelPart part : PlayerModelPart.values()) values.add(options.isModelPartEnabled(part));
		values.add(options.mainHand().get());
		return values;
	}

	private static void assertUnchanged(Screen screen, Screen parent, List<Object> values) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Skin inspection changed the native parent");
		}
		if (!optionValues(minecraft.options).equals(values)) throw new AssertionError("Skin inspection changed an existing preference");
	}

	private static void assertParity(NativeSkinScreen original, HgnSkinCustomizationScreen screen) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native skin title changed");
		List<AbstractWidget> expected = original.nativeControls();
		List<AbstractWidget> actual = screen.nativeControls();
		for (int index = 0; index < expected.size(); index++) {
			assertWidget(expected.get(index), actual.get(index));
			if (actual.get(index).getTabOrderGroup() != index) throw new AssertionError("Native skin tab order changed on resize");
		}
		List<AbstractWidget> expectedActions = actions(original, expected);
		List<AbstractWidget> actualActions = actions(screen, actual);
		if (expectedActions.isEmpty() || expectedActions.size() != actualActions.size()) throw new AssertionError("Native skin footer changed");
		for (int index = 0; index < expectedActions.size(); index++) assertWidget(expectedActions.get(index), actualActions.get(index));
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Native skin list duplicates page input or rendering");
		}
	}

	private static List<AbstractWidget> actions(Screen screen, List<AbstractWidget> controls) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget
			&& !(child instanceof OptionsList) && !(child instanceof StringWidget)
			&& !(child instanceof HgnSettingsHeader) && !(child instanceof HgnButton) && !controls.contains(child))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertWidget(AbstractWidget expected, AbstractWidget actual) {
		if (expected.getClass() != actual.getClass() || !expected.getMessage().equals(actual.getMessage())
			|| expected.active != actual.active || expected.visible != actual.visible
			|| (tooltip(expected) == null) != (tooltip(actual) == null)) {
			throw new AssertionError("Native skin control type, translation, state or tooltip changed");
		}
		if (expected instanceof CycleButton<?> expectedCycle && actual instanceof CycleButton<?> actualCycle
			&& (!Objects.equals(expectedCycle.getValue(), actualCycle.getValue())
				|| !expectedCycle.createDefaultNarrationMessage().equals(actualCycle.createDefaultNarrationMessage()))) {
			throw new AssertionError("Native skin cycle value or narration changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial skin focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) assertWidget(expectedWidget, actualWidget);
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler owner) {
		GuiEventListener focused = owner.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertFocused(Screen screen, AbstractWidget widget, Tooltip tooltip) {
		if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
			|| tooltip(widget) != tooltip) throw new AssertionError("Skin focus or tooltip was lost on resize");
		assertBounds(screen);
	}

	private static void assertBounds(Screen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Skin widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Skin page widgets overlap");
				}
			}
		}
	}

	private static final class NativeSkinScreen extends SkinCustomizationScreen {
		private NativeSkinScreen(Screen parent, Options options) {
			super(parent, options);
		}

		private List<AbstractWidget> nativeControls() {
			return HgnSoundOptionsScreen.optionWidgets(list);
		}
	}
}
