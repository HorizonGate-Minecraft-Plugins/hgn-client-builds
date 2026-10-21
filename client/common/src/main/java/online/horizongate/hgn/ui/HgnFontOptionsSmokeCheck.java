package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnFontOptionsScreen;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Initializes detached font menus without toggling an option, reloading fonts or saving options. */
public final class HgnFontOptionsSmokeCheck {
	private HgnFontOptionsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		boolean unicode = minecraft.options.forceUnicodeFont().get();
		boolean japanese = minecraft.options.japaneseGlyphVariants().get();
		List<OptionInstance<?>> options = List.of(minecraft.options.forceUnicodeFont(), minecraft.options.japaneseGlyphVariants());
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {836, 471}, {1280, 720}}) {
			NativeFontScreen original = new NativeFontScreen(parent, minecraft.options);
			HgnFontOptionsScreen screen = new HgnFontOptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			assertUnchanged(screen, parent, unicode, japanese);
			assertParity(original, screen, options);
			assertInitialFocus(original, screen);
			assertBounds(screen, size[0], size[1]);
			List<AbstractWidget> retained = new ArrayList<>();
			for (OptionInstance<?> option : options) retained.add(screen.nativeOption(option));
			for (AbstractWidget action : actions(screen)) if (!retained.contains(action)) retained.add(action);
			for (AbstractWidget widget : retained) {
				var tooltip = tooltip(widget);
				screen.setFocused(widget);
				HgnCompat.resizeScreen(screen, size[0] + 20, size[1] + 20);
				assertBounds(screen, size[0] + 20, size[1] + 20);
				assertParity(original, screen, options);
				if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)
					|| tooltip(widget) != tooltip) {
					throw new AssertionError("Native font widget focus, ownership or tooltip changed during resize");
				}
				HgnCompat.resizeScreen(screen, size[0], size[1]);
				assertBounds(screen, size[0], size[1]);
				if (screen.getFocused() != widget || !widget.isFocused()) {
					throw new AssertionError("Native font widget focus was lost returning to the compact viewport");
				}
				for (int index = 0; index < options.size(); index++) {
					if (screen.nativeOption(options.get(index)) != retained.get(index)) {
						throw new AssertionError("Resize replaced a native font option or its retained list mapping");
					}
				}
				assertUnchanged(screen, parent, unicode, japanese);
			}
			// Native removed()/onClose() save or apply settings; detached inspection must not invoke them.
		}
		HgnClient.LOGGER.info("HGN_FONT_OPTIONS_SMOKE_OK: native initialization, title/actions/options, retained list mappings, tooltip/focus/resize bounds and parent preserved; no font toggle, reload, save, Done action or rendering invoked");
	}

	private static void assertUnchanged(Screen screen, Screen parent, boolean unicode, boolean japanese) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Font initialization changed the native parent screen");
		}
		if (minecraft.options.forceUnicodeFont().get() != unicode || minecraft.options.japaneseGlyphVariants().get() != japanese) {
			throw new AssertionError("Font initialization changed an existing option value");
		}
	}

	private static void assertParity(NativeFontScreen original, HgnFontOptionsScreen screen, List<OptionInstance<?>> options) {
		if (!original.getTitle().equals(screen.getTitle())) throw new AssertionError("Native font title changed");
		List<AbstractWidget> expected = actions(original);
		List<AbstractWidget> actual = actions(screen);
		for (OptionInstance<?> option : options) actual = without(actual, screen.nativeOption(option));
		if (actual.size() != expected.size() || expected.isEmpty()) throw new AssertionError("Native font footer actions changed");
		for (int index = 0; index < expected.size(); index++) assertWidget(expected.get(index), actual.get(index));
		for (OptionInstance<?> option : options) {
			AbstractWidget widget = screen.nativeOption(option);
			assertWidget(original.nativeOption(option), widget);
			if (screen.children().stream().filter(child -> child == widget).count() != 1) {
				throw new AssertionError("Native font control missing or registered twice");
			}
		}
		if (screen.children().stream().anyMatch(child -> child instanceof OptionsList)) {
			throw new AssertionError("Retained native options list must not duplicate direct control input/rendering");
		}
		long titleCount = screen.children().stream().filter(child -> child instanceof StringWidget label
			&& label.getMessage().equals(original.getTitle())).count();
		if (titleCount != 1) throw new AssertionError("Native font page title missing or duplicated");
	}

	private static List<AbstractWidget> without(List<AbstractWidget> widgets, AbstractWidget excluded) {
		return widgets.stream().filter(widget -> widget != excluded).toList();
	}

	private static void assertWidget(AbstractWidget expected, AbstractWidget actual) {
		if (expected == null || actual == null || !expected.getClass().equals(actual.getClass())
			|| !expected.getMessage().equals(actual.getMessage()) || expected.active != actual.active
			|| expected.visible != actual.visible || (tooltip(expected) == null) != (tooltip(actual) == null)) {
			throw new AssertionError("Native font control type, translation, state or tooltip changed");
		}
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static List<AbstractWidget> actions(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget
			&& !(child instanceof OptionsList) && !(child instanceof StringWidget) && !(child instanceof HgnSettingsHeader))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		GuiEventListener expected = focusedLeaf(original);
		GuiEventListener actual = focusedLeaf(screen);
		if ((expected == null) != (actual == null)) throw new AssertionError("Native initial font focus changed");
		if (expected instanceof AbstractWidget expectedWidget && actual instanceof AbstractWidget actualWidget) {
			assertWidget(expectedWidget, actualWidget);
			if (expectedWidget.isFocused() != actualWidget.isFocused()) throw new AssertionError("Native font focus state changed");
		}
	}

	private static GuiEventListener focusedLeaf(ContainerEventHandler screen) {
		GuiEventListener focused = screen.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) focused = container.getFocused();
		return focused;
	}

	private static void assertBounds(Screen screen, int width, int height) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.width() <= 0 || bounds.height() <= 0 || bounds.x() < 0 || bounds.y() < 0
				|| bounds.right() > width || bounds.bottom() > height) {
				throw new AssertionError("Native font control outside viewport");
			}
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Native font controls overlap");
				}
			}
		}
	}

	private static final class NativeFontScreen extends FontOptionsScreen {
		private NativeFontScreen(Screen parent, Options options) {
			super(parent, options);
		}

		private AbstractWidget nativeOption(OptionInstance<?> option) {
			return list.findOption(option);
		}
	}
}
