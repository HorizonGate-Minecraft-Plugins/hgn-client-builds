package online.horizongate.hgn.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.screen.HgnSelectWorldScreen;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Initializes and resizes native-backed world menus without invoking any world action. */
public final class HgnWorldSelectionSmokeCheck {
	private HgnWorldSelectionSmokeCheck() {
	}

	public static void run() {
		Screen parent = Minecraft.getInstance().screen;
		LevelSummary fixtureSummary;
		try {
			fixtureSummary = HgnWorldMenuSmokeFixture.prepare();
			if (Minecraft.getInstance().getLevelSource().findLevelCandidates().isEmpty()) {
				HgnClient.LOGGER.info("HGN_WORLD_SELECTION_SMOKE_SKIPPED: no existing world candidates; native init would open world creation");
				return;
			}
		} catch (LevelStorageException exception) {
			throw new AssertionError("Unable to inspect world candidates safely for menu smoke", exception);
		} catch (java.io.IOException exception) {
			throw new AssertionError("Unable to prepare the isolated CI world-list fixture", exception);
		}
		assertParentUnchanged(parent);
		assertMetadataFit();
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {427, 240}, {640, 360}, {836, 471},
			{1280, 720}, {1500, 840}}) {
			SelectWorldScreen original = new SelectWorldScreen(parent);
			HgnSelectWorldScreen screen = new HgnSelectWorldScreen(parent);
			try {
				HgnCompat.initScreen(original, size[0], size[1]);
				assertParentUnchanged(parent);
				HgnCompat.initScreen(screen, size[0], size[1]);
				assertParentUnchanged(parent);
				assertNativeActions(original, screen);
				assertNavigation(screen, size[0], size[1]);
				assertBounds(screen, size[0], size[1]);
				assertSearchFocus(original);
				assertSearchFocus(screen);
				search(screen).setValue("hgn-world-menu-smoke");
				HgnCompat.resizeScreen(screen, size[0] + 20, size[1] + 20);
				assertParentUnchanged(parent);
				assertNativeActions(original, screen);
				assertNavigation(screen, size[0] + 20, size[1] + 20);
				assertBounds(screen, size[0] + 20, size[1] + 20);
				assertSearchFocus(screen);
				if (!search(screen).getValue().equals("hgn-world-menu-smoke")) {
					throw new AssertionError("World search was lost during resize");
				}
				if (fixtureSummary != null) {
					original.updateButtonStatus(fixtureSummary);
					screen.updateButtonStatus(fixtureSummary);
					assertNativeActions(original, screen);
				}
				original.updateButtonStatus(null);
				for (Button button : buttons(screen)) button.active = true;
				screen.updateButtonStatus(null);
				assertNativeActions(original, screen);
			} finally {
				original.removed();
				screen.removed();
			}
			assertParentUnchanged(parent);
		}
		HgnClient.LOGGER.info("HGN_WORLD_SELECTION_SMOKE_OK: native initialization, action labels, disabled states, search query/focus, resize bounds and parent preserved; fixture-selected summary parity={}; no world action or row rendering invoked", fixtureSummary != null);
	}

	private static void assertMetadataFit() {
		var font = Minecraft.getInstance().font;
		String date = " (10/10/2026 20:31)";
		String longId = "hgn-world-menu-smoke-10158099311843464975";
		int width = font.width("hgn-world..." + date);
		String fitted = HgnWorldRowMetadata.fit(font, longId + date, width);
		if (!fitted.endsWith(date) || !fitted.contains("...") || font.width(fitted) > width) {
			throw new AssertionError("Native world metadata did not retain a readable date within its row");
		}
		String shortText = "world" + date;
		if (!HgnWorldRowMetadata.fit(font, shortText, font.width(shortText)).equals(shortText)) {
			throw new AssertionError("Short world metadata changed when it already fit");
		}
		if (!HgnWorldRowMetadata.fit(font, longId, 0).isEmpty()) {
			throw new AssertionError("World metadata escaped a zero-width row");
		}
	}

	private static void assertParentUnchanged(Screen parent) {
		if (Minecraft.getInstance().screen != parent) {
			throw new AssertionError("World initialization smoke changed the active parent screen");
		}
	}

	private static void assertSearchFocus(Screen screen) {
		EditBox search = search(screen);
		if (screen.getFocused() != search || !search.isFocused()) {
			throw new AssertionError("Native world search did not retain initial/resize focus");
		}
	}

	private static List<Button> buttons(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof Button
			&& !(child instanceof HgnButton))
			.map(child -> (Button) child).toList();
	}

	private static void assertNavigation(HgnSelectWorldScreen screen, int width, int height) {
		HgnWorldSelectionLayout layout = HgnWorldSelectionLayout.resolve(width, height, buttons(screen).size());
		List<HgnButton> navigation = screen.children().stream()
			.filter(child -> child instanceof HgnButton button && button.visible)
			.map(child -> (HgnButton) child).toList();
		if (!layout.sidebar()) {
			if (!navigation.isEmpty()) throw new AssertionError("Desktop world navigation leaked into compact view");
			return;
		}
		if (navigation.size() != 7) throw new AssertionError("World navigation lost a real route");
		for (int index : new int[] {0, 2, 3, 4, 5, 6, 7}) {
			String label = switch (index) {
				case 0 -> "PLAY";
				case 2 -> "MULTIPLAYER";
				case 3 -> "SERVERS";
				case 4 -> "COSMETICS";
				case 5 -> "MODS";
				case 6 -> "SETTINGS";
				default -> "BACK";
			};
			HgnButton button = navigation.stream()
				.filter(candidate -> candidate.getMessage().getString().equals(label))
				.findFirst().orElseThrow(() -> new AssertionError("Missing world navigation route: " + label));
			HgnRect expected = layout.navigation().get(index);
			if (!button.active || button.getX() != expected.x() || button.getY() != expected.y()
				|| button.getWidth() != expected.width() || button.getHeight() != expected.height()) {
				throw new AssertionError("World navigation target is misplaced: " + label);
			}
		}
	}

	private static EditBox search(Screen screen) {
		return screen.children().stream().filter(child -> child instanceof EditBox)
			.map(child -> (EditBox) child).findFirst()
			.orElseThrow(() -> new AssertionError("Native world search missing"));
	}

	private static void assertNativeActions(SelectWorldScreen original, HgnSelectWorldScreen screen) {
		List<Button> expected = buttons(original);
		List<Button> actual = buttons(screen);
		if (actual.size() != expected.size()) throw new AssertionError("Native world action count changed");
		for (int index = 0; index < expected.size(); index++) {
			if (!actual.get(index).getMessage().equals(expected.get(index).getMessage())
				|| actual.get(index).active != expected.get(index).active) {
				throw new AssertionError("Native world action or disabled state changed");
			}
		}
		if (screen.children().stream().filter(child -> child instanceof WorldSelectionList).count() != 1) {
			throw new AssertionError("Native world list missing or duplicated");
		}
	}

	private static void assertBounds(Screen screen, int width, int height) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			AbstractWidget widget = widgets.get(index);
			HgnRect bounds = new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
			if (bounds.x() < 0 || bounds.y() < 0 || bounds.right() > width || bounds.bottom() > height) {
				throw new AssertionError("Native world control outside viewport");
			}
			for (int next = index + 1; next < widgets.size(); next++) {
				AbstractWidget other = widgets.get(next);
				if (bounds.x() < other.getX() + other.getWidth() && bounds.right() > other.getX()
					&& bounds.y() < other.getY() + other.getHeight() && bounds.bottom() > other.getY()) {
					throw new AssertionError("Native world controls overlap");
				}
			}
		}
	}
}
