package online.horizongate.hgn.ui;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnLanguageListAccessor;
import online.horizongate.hgn.mixin.HgnOptionsSubScreenAccessor;
import online.horizongate.hgn.screen.HgnLanguageSelectScreen;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Initialization-only checks; never presses Done, saves options or reloads resources. */
public final class HgnLanguageSmokeCheck {
	private HgnLanguageSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		String languageCode = minecraft.options.languageCode;
		String selectedLanguage = minecraft.getLanguageManager().getSelected();
		boolean unicodeFont = minecraft.options.forceUnicodeFont().get();
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			LanguageSelectScreen original = new LanguageSelectScreen(parent, minecraft.options, minecraft.getLanguageManager());
			HgnLanguageSelectScreen screen = new HgnLanguageSelectScreen(parent, minecraft.options, minecraft.getLanguageManager());
			HgnCompat.initScreen(original, size[0], size[1]);
			HgnCompat.initScreen(screen, size[0], size[1]);
			assertParent(screen, parent);
			assertParity(original, screen);
			assertInitialFocus(original, screen);
			assertBounds(screen, size[0], size[1]);
			ObjectSelectionList<?> languages = list(screen);
			int selected = languages.children().indexOf(languages.getSelected());
			HgnLanguageListAccessor rows = (HgnLanguageListAccessor) languages;
			if (selected >= 0 && (rows.hgn$languageRowTop(selected) < languages.getY()
				|| rows.hgn$languageRowBottom(selected) > languages.getY() + languages.getHeight())) {
				throw new AssertionError("Current language is not visible after initial layout");
			}
			int pending = languages.children().size() - 1;
			select(list(original), pending);
			select(languages, pending);
			Object pendingSelection = languages.getSelected();
			screen.setFocused(languages);
			HgnCompat.resizeScreen(screen, size[0] + 20, size[1] + 20);
			assertBounds(screen, size[0] + 20, size[1] + 20);
			if (list(screen) != languages || languages.getSelected() != pendingSelection || screen.getFocused() != languages) {
				throw new AssertionError("Pending language selection or list focus was lost during resize");
			}
			assertParity(original, screen);
			EditBox search = search(screen);
			if (search != null) {
				String query = "hgn-language-menu-smoke";
				search(original).setValue(query);
				search.setValue(query);
				screen.setFocused(search);
				HgnCompat.resizeScreen(screen, size[0], size[1]);
				if (search(screen) != search || !query.equals(search.getValue())
					|| screen.getFocused() != search || !search.isFocused()) {
					throw new AssertionError("Native language search query or focus was lost during resize");
				}
				assertParity(original, screen);
				assertBounds(screen, size[0], size[1]);
			}
			assertParent(screen, parent);
			if (!Objects.equals(languageCode, minecraft.options.languageCode)
				|| !Objects.equals(selectedLanguage, minecraft.getLanguageManager().getSelected())
				|| unicodeFont != minecraft.options.forceUnicodeFont().get()) {
				throw new AssertionError("Language initialization smoke changed active language or Unicode font settings");
			}
			// OptionsSubScreen.removed() saves options, so these detached inspection screens must not call it.
		}
		HgnClient.LOGGER.info("HGN_LANGUAGE_SMOKE_OK: native initialization, actions, selection/narration, optional search, resize bounds and parent preserved; no save, language apply, font action or row rendering invoked");
	}

	private static void assertParent(Screen screen, Screen parent) {
		if (Minecraft.getInstance().screen != parent || ((HgnOptionsSubScreenAccessor) screen).hgn$parent() != parent) {
			throw new AssertionError("Language page changed its native parent identity");
		}
	}

	private static ObjectSelectionList<?> list(Screen screen) {
		var lists = screen.children().stream()
			.filter(child -> child instanceof ObjectSelectionList<?>)
			.map(child -> (ObjectSelectionList<?>) child).toList();
		if (lists.size() != 1) throw new AssertionError("Native language list missing or duplicated");
		return lists.getFirst();
	}

	private static <E extends ObjectSelectionList.Entry<E>> void select(ObjectSelectionList<E> list, int index) {
		if (index < 0) throw new AssertionError("Native language list unexpectedly empty");
		list.setSelected(list.children().get(index));
	}

	private static EditBox search(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof EditBox)
			.map(child -> (EditBox) child).findFirst().orElse(null);
	}

	private static void assertInitialFocus(Screen original, Screen screen) {
		if ((original.getFocused() == null) != (screen.getFocused() == null)) {
			throw new AssertionError("Native initial language focus changed");
		}
		if (search(original) != null && ((original.getFocused() == search(original)) != (screen.getFocused() == search(screen))
			|| search(original).isFocused() != search(screen).isFocused())) {
			throw new AssertionError("Native initial language search focus changed");
		}
		if (original.getFocused() instanceof AbstractWidget expected && screen.getFocused() instanceof AbstractWidget actual
			&& (!expected.getClass().equals(actual.getClass()) || !expected.getMessage().equals(actual.getMessage()))) {
			throw new AssertionError("Native initial language focus target changed");
		}
	}

	private static List<AbstractWidget> actions(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget
			&& !(child instanceof AbstractSelectionList<?>) && !(child instanceof EditBox)
			&& !(child instanceof StringWidget) && !(child instanceof HgnSettingsHeader))
			.map(child -> (AbstractWidget) child).toList();
	}

	private static void assertParity(Screen original, Screen screen) {
		List<AbstractWidget> expected = actions(original);
		List<AbstractWidget> actual = actions(screen);
		if (actual.size() != expected.size() || expected.isEmpty()) throw new AssertionError("Native font/Done actions changed");
		for (int index = 0; index < expected.size(); index++) {
			if (!actual.get(index).getClass().equals(expected.get(index).getClass())
				|| !actual.get(index).getMessage().equals(expected.get(index).getMessage())
				|| actual.get(index).active != expected.get(index).active) {
				throw new AssertionError("Native language action label or availability changed");
			}
		}
		AbstractSelectionList<?> expectedList = list(original);
		AbstractSelectionList<?> actualList = list(screen);
		if (actualList.children().size() != expectedList.children().size()
			|| actualList.children().indexOf(actualList.getSelected()) != expectedList.children().indexOf(expectedList.getSelected())) {
			throw new AssertionError("Native language entries or selection changed");
		}
		if (actualList.getSelected() instanceof ObjectSelectionList.Entry<?> actualEntry
			&& expectedList.getSelected() instanceof ObjectSelectionList.Entry<?> expectedEntry
			&& !actualEntry.getNarration().equals(expectedEntry.getNarration())) {
			throw new AssertionError("Native selected-language narration changed");
		}
		if ((search(original) == null) != (search(screen) == null)) {
			throw new AssertionError("Native language search availability changed");
		}
		if (!warnings(original).equals(warnings(screen))) throw new AssertionError("Native language accuracy warning changed");
	}

	private static List<net.minecraft.network.chat.Component> warnings(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof StringWidget)
			.map(child -> ((StringWidget) child).getMessage()).filter(message -> !message.equals(screen.getTitle())).toList();
	}

	private static void assertBounds(Screen screen, int width, int height) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		AbstractSelectionList<?> languages = list(screen);
		if (languages.getX() != 0 || languages.getWidth() != width) throw new AssertionError("Native language row alignment changed");
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.right() > width || bounds.bottom() > height) {
				throw new AssertionError("Native language control outside viewport");
			}
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Native language controls overlap");
				}
			}
		}
	}
}
