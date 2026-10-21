package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnInputSmokeBridge;
import online.horizongate.hgn.screen.HgnAccessibilityScreen;
import online.horizongate.hgn.screen.HgnClientSettingsScreen;

/** Exercises settings controls in isolated dev smoke runs and restores the original profile. */
public final class HgnClientSettingsSmokeCheck {
	private HgnClientSettingsSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		HgnConfig config = HgnConfig.get();
		List<String> tabs = List.of("VIDEO", "AUDIO", "CONTROLS", "INTERFACE", "CLIENT",
			"PERFORMANCE", "NETWORK", "COSMETICS", "ACCESSIBILITY", "KEYBINDS");
		List<List<String>> settingsByTab = List.of(
			List.of("Scene particles", "Effect quality", "Hotbar & boss frames", "Minecraft video settings"),
			List.of("Minecraft sound settings"),
			List.of("Minecraft controls"),
			List.of("Interface motion", "World information", "Show coordinates"),
			List.of("Show HGN HUD", "HUD size", "Experimental HGN hotbar"),
			List.of("Performance readout", "Crowded drops & XP"),
			List.of("Multiplayer servers"),
			List.of("HGN cosmetics"),
			List.of("Reduced motion", "High contrast", "Accessibility & language"),
			List.of("Minecraft key bindings"));
		boolean[] original = {config.hudEnabled, config.interfaceAnimations, config.reducedMotion,
			config.sceneParticles, config.highContrast, config.hudFrames, config.hudPerformance,
			config.hudContext, config.hudCoordinates, config.crowdedItemRendering,
			config.experimentalHgnHotbar};
		HgnConfig.HudSize originalSize = config.hudSize;
		HgnConfig.SceneQuality originalQuality = config.sceneQuality;
		try {
			for (int[] size : new int[][] {{320, 180}, {320, 200}, {427, 240}, {480, 270},
				{550, 352}, {640, 360}, {640, 480}, {836, 471}, {1280, 720}}) {
				HgnClientSettingsScreen screen = new HgnClientSettingsScreen(parent);
				HgnCompat.initScreen(screen, size[0], size[1]);
				if (HgnReferenceSettingsLayout.fits(size[0], size[1])) {
					HgnDashboardLayout canvas = HgnDashboardLayout.resolve(size[0], size[1]);
					HgnReferenceShellLayout shell = new HgnReferenceShellLayout(canvas);
					HgnReferenceSettingsLayout reference = new HgnReferenceSettingsLayout(canvas);
					AbstractWidget settingsRoute = find(screen, "SETTINGS");
					HgnRect destination = shell.destination(6);
					if (settingsRoute.getX() != destination.x()
						|| settingsRoute.getY() != destination.y()
						|| settingsRoute.getWidth() != destination.width()
						|| settingsRoute.getHeight() != destination.height()) {
						throw new AssertionError("Reference Settings rail is misplaced");
					}
					for (HgnSettingsCategory category : HgnSettingsCategory.values()) {
						AbstractWidget tab = find(screen, category.label());
						HgnRect expected = reference.categoryTab(category.ordinal());
						if (tab.getX() != expected.x() || tab.getY() != expected.y()
							|| tab.getWidth() != expected.width() || tab.getHeight() != expected.height()) {
							throw new AssertionError("Reference category is not in the single-row strip: "
								+ category.label());
						}
					}
					find(screen, "Open HGN Launcher account");
					find(screen, "ACCESSIBILITY");
					find(screen, "Video settings");
					find(screen, "Minecraft settings");
				} else if (optional(screen, "SETTINGS") != null) {
					throw new AssertionError("Compact Settings drew the wide rail");
				} else if (optional(screen, "Video settings") != null) {
					throw new AssertionError("Compact Settings drew a third footer action");
				}
				Set<String> reached = new HashSet<>();
				for (int section = 0; section < tabs.size(); section++) {
					List<String> sectionSettings = settingsByTab.get(section);
					HgnSettingsLayout layout = HgnReferenceSettingsLayout.fits(size[0], size[1])
						? new HgnReferenceSettingsLayout(HgnDashboardLayout.resolve(size[0], size[1]))
							.controls(sectionSettings.size())
						: HgnSettingsLayout.resolveCategories(size[0], size[1], sectionSettings.size());
					String tabLabel = HgnReferenceSettingsLayout.fits(size[0], size[1])
						|| layout.tabs().width() >= 450 ? tabs.get(section)
						: HgnSettingsCategory.values()[section].compactLabel();
					click(find(screen, tabLabel));
					int remaining = 12;
					int pageIndex = 0;
					while (remaining-- > 0) {
						assertBounds(screen, size[0], size[1]);
						List<String> rows = new ArrayList<>();
						for (var child : screen.children()) {
							if (child instanceof AbstractWidget widget) {
								String label = widget.getMessage().getString();
								if (label.contains(": ")) rows.add(label.substring(0, label.indexOf(": ")));
							}
						}
						int expectedRows = Math.min(layout.rowsPerPage(),
							sectionSettings.size() - pageIndex * layout.rowsPerPage());
						if (rows.size() != expectedRows) throw new AssertionError("Settings row count is wrong at "
							+ size[0] + "x" + size[1] + ": " + rows);
						for (String row : rows) {
							if (!sectionSettings.contains(row)) {
								throw new AssertionError("Wrong setting shown in " + tabs.get(section) + ": " + row);
							}
							if (!reached.add(row)) throw new AssertionError("Setting repeated across pages: " + row);
							String before = find(screen, row + ": ").getMessage().getString();
							if (before.contains(": OPEN >")) continue;
							int cycle = row.equals("HUD size") || row.equals("Effect quality") ? 3 : 2;
							for (int index = 0; index < cycle; index++) {
								click(find(screen, row + ": "));
								AbstractWidget changed = find(screen, row + ": ");
								if (screen.getFocused() != changed) throw new AssertionError("Changed setting lost keyboard focus");
								if (index == 0 && changed.getMessage().getString().equals(before)) {
									throw new AssertionError("Setting click had no effect: " + row);
								}
							}
							if (!find(screen, row + ": ").getMessage().getString().equals(before)) {
								throw new AssertionError("Setting cycle did not restore its value: " + row);
							}
						}
						AbstractWidget next = optional(screen, "Next");
						if (next == null || !next.active) break;
						click(next);
						pageIndex++;
					}
					if (remaining < 0) throw new AssertionError("Settings pagination did not terminate");
				}
				if (reached.size() != 20 || !reached.contains("Experimental HGN hotbar")
					|| !reached.contains("Minecraft key bindings")) {
					throw new AssertionError("Not all HGN settings are reachable: " + reached);
				}
				HgnCompat.resizeScreen(screen, size[0], size[1]);
				assertBounds(screen, size[0], size[1]);
				HgnAccessibilityScreen accessibility = new HgnAccessibilityScreen(parent);
				HgnCompat.initScreen(accessibility, size[0], size[1]);
				assertBounds(accessibility, size[0], size[1]);
				find(accessibility, "Minecraft accessibility.");
				find(accessibility, "Language.");
			}
		} finally {
			config.hudEnabled = original[0];
			config.interfaceAnimations = original[1];
			config.reducedMotion = original[2];
			config.sceneParticles = original[3];
			config.highContrast = original[4];
			config.hudFrames = original[5];
			config.hudPerformance = original[6];
			config.hudContext = original[7];
			config.hudCoordinates = original[8];
			config.crowdedItemRendering = original[9];
			config.experimentalHgnHotbar = original[10];
			config.hudSize = originalSize;
			config.sceneQuality = originalQuality;
			HgnConfig.save();
		}
		HgnClient.LOGGER.info("HGN_SETTINGS_SMOKE_OK: ten categories expose 13 HGN settings and seven real destinations; local settings cycle and restore, compact pagination, focus, resize and accessibility targets checked");
	}

	private static AbstractWidget optional(Screen screen, String label) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
			&& (widget.getMessage().getString().equals(label) || widget.getMessage().getString().startsWith(label + " ")
				|| label.endsWith(": ") && widget.getMessage().getString().startsWith(label)))
			.map(child -> (AbstractWidget) child).findFirst().orElse(null);
	}

	private static AbstractWidget find(Screen screen, String label) {
		AbstractWidget widget = optional(screen, label);
		if (widget == null) throw new AssertionError("Missing settings control: " + label);
		return widget;
	}

	private static void click(AbstractWidget widget) {
		HgnInputSmokeBridge.click(widget, widget.getX() + widget.getWidth() / 2.0,
			widget.getY() + widget.getHeight() / 2.0);
	}

	private static void assertBounds(Screen screen, int width, int height) {
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget widget && (widget.getX() < 0 || widget.getY() < 0
				|| widget.getX() + widget.getWidth() > width || widget.getY() + widget.getHeight() > height)) {
				throw new AssertionError("Settings control outside viewport: " + widget.getMessage().getString());
			}
		}
	}
}
