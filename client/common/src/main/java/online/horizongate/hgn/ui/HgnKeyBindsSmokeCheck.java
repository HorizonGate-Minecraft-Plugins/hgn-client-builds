package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.mixin.HgnKeyBindEntryAccessor;
import online.horizongate.hgn.mixin.HgnLanguageListAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnKeyBindsScreen;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Detached roster, focus and geometry inspection: no key capture, resets, rendering or saves. */
public final class HgnKeyBindsSmokeCheck {
	private HgnKeyBindsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		List<KeyMapping> mappings = List.copyOf(Arrays.asList(minecraft.options.keyMappings));
		List<String> values = mappings.stream().map(KeyMapping::saveString).toList();
		List<Boolean> down = mappings.stream().map(KeyMapping::isDown).toList();
		for (int[] size : new int[][] {{320, 180}, {320, 259}, {320, 260}, {427, 240}, {836, 471}, {1280, 720}}) {
			KeyBindsScreen original = new KeyBindsScreen(parent, minecraft.options);
			HgnKeyBindsScreen screen = new HgnKeyBindsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			KeyBindsList retained = screen.nativeList();
			List<?> entries = List.copyOf(retained.children());
			List<AbstractWidget> footer = actions(screen);
			Map<AbstractWidget, Tooltip> tooltips = new IdentityHashMap<>();
			for (var entry : retained.children()) {
				for (var child : entry.children()) if (child instanceof AbstractWidget widget) tooltips.put(widget, tooltip(widget));
			}
			assertParity(original, screen, mappings);
			assertBounds(screen);
			if ((original.getFocused() == null) != (screen.getFocused() == null)) throw new AssertionError("Initial native key-binding focus changed");
			for (int index : new int[]{0, entries.size() / 2, entries.size() - 1}) {
				var entry = retained.children().get(index);
				for (var child : entry.children()) {
					if (!(child instanceof AbstractWidget widget) || !widget.active || !widget.visible) continue;
					screen.setFocused(retained);
					retained.setFocused(entry);
					entry.setFocused(widget);
					HgnCompat.resizeScreen(screen, 836, 471);
					assertFocus(screen, retained, entry, widget, index);
					HgnCompat.resizeScreen(screen, size[0], size[1]);
					assertFocus(screen, retained, entry, widget, index);
				}
			}
			for (AbstractWidget action : footer) {
				screen.setFocused(action);
				HgnCompat.resizeScreen(screen, size[0] + 20, size[1] + 20);
				if (screen.getFocused() != action || !action.isFocused()) throw new AssertionError("Native footer focus lost on resize");
			}
			if (screen.nativeList() != retained || !retained.children().equals(entries) || !actions(screen).equals(footer)) {
				throw new AssertionError("Key-binding resize replaced the native list, entries or actions");
			}
			tooltips.forEach((widget, tooltip) -> {
				if (tooltip(widget) != tooltip) throw new AssertionError("Key-binding resize replaced a native conflict tooltip");
			});
			assertParity(original, screen, mappings);
			assertBounds(screen);
			if (screen.selectedKey != null || original.selectedKey != null || screen.lastKeySelection != original.lastKeySelection) {
				throw new AssertionError("Detached key-binding inspection entered capture mode");
			}
			if (minecraft.screen != parent || ((HgnOptionsSubScreenAccessor) (Object) screen).hgn$parent() != parent
				|| !Arrays.asList(minecraft.options.keyMappings).equals(mappings)
				|| !mappings.stream().map(KeyMapping::saveString).toList().equals(values)
				|| !mappings.stream().map(KeyMapping::isDown).toList().equals(down)) {
				throw new AssertionError("Key-binding inspection changed the parent or existing mappings");
			}
		}
		HgnClient.LOGGER.info("HGN_KEY_BINDS_SMOKE_OK: full native roster/categories, button narration/tooltips, original list/footer, focus/resize and parent preserved; no capture, reset, mapping lookup refresh, Done/save or rendering invoked; rendered row geometry and interactive capture remain unverified");
	}

	private static void assertFocus(HgnKeyBindsScreen screen, KeyBindsList list, KeyBindsList.Entry entry,
		AbstractWidget widget, int index) {
		if (screen.getFocused() != list || list.getFocused() != entry || entry.getFocused() != widget || !widget.isFocused()) {
			throw new AssertionError("Native key-binding focus chain lost on resize");
		}
		HgnLanguageListAccessor rows = (HgnLanguageListAccessor) list;
		if (rows.hgn$languageRowTop(index) < list.getY() || rows.hgn$languageRowBottom(index) > list.getY() + list.getHeight()) {
			throw new AssertionError("Focused binding row was left outside the resized scroll viewport");
		}
		assertBounds(screen);
	}

	private static KeyBindsList list(Screen screen) {
		var lists = screen.children().stream().filter(child -> child instanceof KeyBindsList).map(child -> (KeyBindsList) child).toList();
		if (lists.size() != 1) throw new AssertionError("Native key-binding list missing or registered twice");
		return lists.getFirst();
	}

	private static List<AbstractWidget> actions(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget
			&& !(child instanceof KeyBindsList) && !(child instanceof StringWidget) && !(child instanceof HgnSettingsHeader))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertParity(KeyBindsScreen original, HgnKeyBindsScreen screen, List<KeyMapping> mappings) {
		var expected = list(original).children();
		var actual = list(screen).children();
		if (!original.getTitle().equals(screen.getTitle()) || expected.size() != actual.size()) throw new AssertionError("Native title or complete entry roster changed");
		List<KeyMapping> roster = new ArrayList<>();
		for (int index = 0; index < expected.size(); index++) {
			var first = expected.get(index);
			var second = actual.get(index);
			if (first.getClass() != second.getClass() || first.children().size() != second.children().size()) {
				throw new AssertionError("Native key-binding category or row order changed");
			}
			if (first instanceof HgnKeyBindEntryAccessor firstKey && second instanceof HgnKeyBindEntryAccessor secondKey) {
				if (firstKey.hgn$key() != secondKey.hgn$key() || !firstKey.hgn$name().equals(secondKey.hgn$name())
					|| ((HgnKeyBindRow) second).hgn$keyBindsOwner() != screen || ((HgnKeyBindRow) first).hgn$keyBindsOwner() != null) {
					throw new AssertionError("Native mapping identity, full translated label or scoped geometry ownership changed");
				}
				roster.add(secondKey.hgn$key());
			}
			for (int child = 0; child < first.children().size(); child++) {
				if (first.children().get(child) instanceof AbstractWidget firstWidget && second.children().get(child) instanceof AbstractWidget secondWidget) {
					assertWidget(firstWidget, secondWidget);
				}
			}
		}
		if (roster.size() != mappings.size() || !roster.containsAll(mappings)) throw new AssertionError("A vanilla or mod-added key mapping is missing");
		List<AbstractWidget> expectedActions = actions(original);
		List<AbstractWidget> actualActions = actions(screen);
		if (expectedActions.size() != 2 || actualActions.size() != 2) throw new AssertionError("Native Reset All or Done missing");
		for (int index = 0; index < 2; index++) assertWidget(expectedActions.get(index), actualActions.get(index));
	}

	private static void assertWidget(AbstractWidget expected, AbstractWidget actual) {
		if (expected.getClass() != actual.getClass() || !expected.getMessage().equals(actual.getMessage())
			|| expected.active != actual.active || expected.visible != actual.visible
			|| (tooltip(expected) == null) != (tooltip(actual) == null) || !titleNarration(expected).equals(titleNarration(actual))) {
			throw new AssertionError("Native key-binding control, availability, tooltip or full title narration changed");
		}
	}

	private static List<String> titleNarration(AbstractWidget widget) {
		List<String> text = new ArrayList<>();
		widget.updateNarration(new NarrationElementOutput() {
			@Override
			public void add(NarratedElementType type, NarrationThunk<?> value) {
				if (type == NarratedElementType.TITLE) value.getText(text::add);
			}
			@Override
			public NarrationElementOutput nest() {
				return this;
			}
		});
		return text;
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertBounds(HgnKeyBindsScreen screen) {
		KeyBindsList bindings = screen.nativeList();
		HgnKeyBindsLayout layout = HgnKeyBindsLayout.resolve(screen.width, screen.height, 2);
		if (bindings.getX() != 0 || bindings.getWidth() != screen.width || bindings.getRowWidth() != layout.rowWidth()
			|| bindings.getRowLeft() < layout.listPanel().x() || bindings.getRowRight() > layout.listPanel().right() - 12) {
			throw new AssertionError("Key-binding native row or category alignment escaped its panel");
		}
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget).map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.width() <= 0 || bounds.height() <= 0
				|| bounds.right() > screen.width || bounds.bottom() > screen.height) throw new AssertionError("Key-binding page widget outside viewport");
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Key-binding page widgets overlap");
				}
			}
		}
	}
}
