package online.horizongate.hgn.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.compat.HgnInputSmokeBridge;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnSlotAccessor;
import online.horizongate.hgn.screen.HgnOptionsScreen;
import online.horizongate.hgn.screen.HgnServerEditorScreen;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;

/** Exercises real initialized menus without screenshots, network joins or changing options. */
public final class HgnMenuSmokeCheck {
	private HgnMenuSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		online.horizongate.hgn.update.HgnUpdateDestinations.verifyMenuSmoke();
		HgnPauseMenuSmokeCheck.run();
		HgnUpdateOfferSmokeCheck.run();
		HgnUpdateConsentSmokeCheck.run();
		HgnPackSelectionTransformSmokeCheck.run();
		HgnDisconnectedSmokeCheck.run();
		HgnConnectionSmokeCheck.run();
		HgnClientSettingsSmokeCheck.run();
		HgnWorldSelectionSmokeCheck.run();
		HgnEditWorldSmokeCheck.run();
		HgnCreateWorldTransformSmokeCheck.run();
		HgnLanguageSmokeCheck.run();
		HgnFontOptionsSmokeCheck.run();
		HgnSoundOptionsSmokeCheck.run();
		HgnControlsSmokeCheck.run();
		HgnMouseSettingsSmokeCheck.run();
		HgnKeyBindsSmokeCheck.run();
		HgnSkinCustomizationSmokeCheck.run();
		HgnChatOptionsSmokeCheck.run();
		HgnVideoSettingsSmokeCheck.run();
		HgnAccessibilityOptionsSmokeCheck.run();
		for (int[] size : new int[][] {{320, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			if (HgnCompactDashboardLayout.needed(size[0], size[1],
				minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight())) {
				var dashboard = new online.horizongate.hgn.screen.HgnDashboardScreen();
				HgnCompat.initScreen(dashboard, size[0], size[1]);
				Set<String> commands = new HashSet<>();
				for (var child : dashboard.children()) {
					if (child instanceof AbstractWidget widget) {
						commands.add(widget.getMessage().getString());
						if (widget.getHeight() < 20 || widget.getWidth() < 100
							|| widget.getX() < 0 || widget.getY() < 0
							|| widget.getX() + widget.getWidth() > size[0]
							|| widget.getY() + widget.getHeight() > size[1]) {
							throw new AssertionError("Compact dashboard control clipped or shrunk");
						}
					}
				}
				if (!commands.equals(Set.of("Singleplayer", "Multiplayer", "Servers", "Mods",
					"Cosmetics", "Settings", "Account", "Exit"))) {
					throw new AssertionError("Compact dashboard lost a destination: " + commands);
				}
			} else {
				assertWideDashboard(size[0], size[1]);
			}
			OptionsScreen original = new OptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(original, size[0], size[1]);
			Set<String> expected = new HashSet<>();
			for (var child : original.children()) {
				if (child instanceof AbstractWidget widget && !(widget instanceof StringWidget)
					&& !widget.getMessage().equals(Component.translatable("gui.done"))) {
					expected.add(widget.getMessage().getString());
				}
			}
			HgnOptionsScreen custom = new HgnOptionsScreen(parent, minecraft.options);
			HgnCompat.initScreen(custom, size[0], size[1]);
			assertAllOptions(custom, expected, size[0], size[1]);
			HgnCompat.resizeScreen(custom, size[0], size[1]);
			assertAllOptions(custom, expected, size[0], size[1]);
			SoundOptionsScreen sound = new SoundOptionsScreen(custom, minecraft.options);
			HgnCompat.initScreen(sound, size[0], size[1]);
			if (sound.children().stream().noneMatch(child -> child instanceof HgnSettingsHeader)) {
				throw new AssertionError("Sound settings did not receive the custom settings header");
			}
			assertServerEditor(parent, size[0], size[1]);
		}
		Inventory inventory = HgnCompat.smokeInventory();
		ChestMenu menu = ChestMenu.threeRows(0, inventory);
		int[][] positions = menu.slots.stream().map(slot -> {
			HgnSlotAccessor accessor = (HgnSlotAccessor) (Object) slot;
			return new int[] {accessor.hgn$getX(), accessor.hgn$getY()};
		}).toArray(int[][]::new);
		ContainerScreen container = new ContainerScreen(menu, inventory, Component.literal("Inventory smoke"));
		HgnCompat.initScreen(container, 427, 240);
		for (int index = 0; index < menu.slots.size(); index++) {
			HgnSlotAccessor slot = (HgnSlotAccessor) (Object) menu.slots.get(index);
			if (slot.hgn$getX() != positions[index][0] || slot.hgn$getY() != positions[index][1]) {
				throw new AssertionError("Inventory initialization moved a vanilla slot");
			}
		}
		boolean previous = HgnConfig.get().experimentalContainerLayouts;
		try {
			HgnConfig.get().experimentalContainerLayouts = true;
			if (HgnConfig.get().containerLayoutsEnabled()) throw new AssertionError("Legacy config re-enabled inventory replacement");
		} finally {
			HgnConfig.get().experimentalContainerLayouts = previous;
		}
		assertTitleRouting(minecraft);
		HgnClient.LOGGER.info("HGN_MENU_SMOKE_OK: settings reachable; server editor validation and resize passed; native container slots preserved");
	}

	private static void assertWideDashboard(int width, int height) {
		var dashboard = new online.horizongate.hgn.screen.HgnDashboardScreen();
		HgnCompat.initScreen(dashboard, width, height);
		HgnReferenceShellLayout shell = new HgnReferenceShellLayout(HgnDashboardLayout.resolve(width, height));
		String[] routes = {"PLAY", "SINGLEPLAYER", "MULTIPLAYER", "SERVERS",
			"COSMETICS", "MODS", "SETTINGS", "EXIT"};
		for (int index = 0; index < routes.length; index++) {
			AbstractWidget widget = destinationControl(dashboard, routes[index]);
			HgnRect bounds = shell.destination(index);
			if (!widget.active || widget.getX() != bounds.x() || widget.getY() != bounds.y()
				|| widget.getWidth() != bounds.width() || widget.getHeight() != bounds.height()) {
				throw new AssertionError("Wide dashboard route is missing or misplaced: " + routes[index]);
			}
		}
		AbstractWidget featured = control(dashboard, "JOIN HORIZONGATE NETWORK");
		if (!featured.active || featured.getX() != shell.featuredAction().x()
			|| featured.getY() != shell.featuredAction().y()) {
			throw new AssertionError("Featured network action is missing or misplaced");
		}
		AbstractWidget profile = control(dashboard, "View HGN Launcher profile");
		if (!profile.active || profile.getX() != shell.profileAction().x()
			|| profile.getY() != shell.profileAction().y()
			|| profile.getWidth() != shell.profileAction().width()
			|| profile.getHeight() != shell.profileAction().height()) {
			throw new AssertionError("Dashboard profile action is missing or misplaced");
		}
	}

	private static void assertTitleRouting(Minecraft minecraft) {
		Screen previous = minecraft.screen;
		if (!HgnConfig.get().interfaceEnabled) {
			throw new AssertionError("The HGN interface was disabled by a saved config");
		}
		try {
			Screen modScreen = new Screen(Component.literal("External mod settings")) { };
			minecraft.setScreen(modScreen);
			if (minecraft.screen != modScreen) {
				throw new AssertionError("HGN main-menu routing blocked an unrelated mod screen");
			}
			minecraft.setScreen(new TitleScreen() { });
			if (!(minecraft.screen instanceof online.horizongate.hgn.screen.HgnDashboardScreen)
				|| minecraft.screen == previous) {
				throw new AssertionError("A modded TitleScreen replaced the HGN main menu");
			}
			minecraft.setScreen(modScreen);
			if (minecraft.screen != modScreen) {
				throw new AssertionError("HGN main-menu routing blocked an unrelated mod screen");
			}
		} finally {
			minecraft.setScreen(previous);
		}
		HgnClient.LOGGER.info("HGN_TITLE_ROUTING_SMOKE_OK: modded title reclaimed; unrelated mod screen preserved");
	}

	private static void assertServerEditor(Screen parent, int width, int height) {
		ServerData original = new ServerData("Original name", "example.invalid:25565", ServerData.Type.OTHER);
		original.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
		for (boolean direct : new boolean[] {false, true}) {
			HgnServerEditorScreen editor = new HgnServerEditorScreen(parent, null, original, direct);
			HgnCompat.initScreen(editor, width, height);
			EditBox name = field(editor, "Server name");
			EditBox address = field(editor, "Server address");
			AbstractWidget submit = control(editor, direct ? "Join server" : "Save server");
			if (!submit.active || name.active == direct) throw new AssertionError("Invalid editor initial state");
			address.setValue("");
			if (submit.active) throw new AssertionError("Empty server address accepted");
			address.setValue("new.example.invalid:25566");
			if (!submit.active) throw new AssertionError("Valid server address rejected");
			if (!direct) {
				name.setValue("   ");
				if (submit.active) throw new AssertionError("Blank server name accepted");
				name.setValue("Updated name");
				if (!submit.active) throw new AssertionError("Valid server name rejected");
			}
			HgnCompat.resizeScreen(editor, width, height);
			if (!field(editor, "Server address").getValue().equals("new.example.invalid:25566")
				|| !field(editor, "Server name").getValue().equals(direct ? "Original name" : "Updated name")) {
				throw new AssertionError("Resizing discarded server editor input");
			}
			for (var child : editor.children()) {
				if (child instanceof AbstractWidget widget) assertBounds(widget, width, height);
			}
			if (!original.name.equals("Original name") || !original.ip.equals("example.invalid:25565")
				|| original.getResourcePackStatus() != ServerData.ServerPackStatus.DISABLED) {
				throw new AssertionError("Editing changed a saved server before confirmation");
			}
		}
	}

	private static EditBox field(Screen screen, String label) {
		return (EditBox) control(screen, label);
	}

	private static AbstractWidget control(Screen screen, String label) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
			&& widget.getMessage().getString().equals(label)).map(child -> (AbstractWidget) child)
			.findFirst().orElseThrow(() -> new AssertionError("Missing control: " + label));
	}

	private static AbstractWidget destinationControl(Screen screen, String route) {
		return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
			&& widget.getMessage().getString().startsWith(route + ": "))
			.map(child -> (AbstractWidget) child)
			.findFirst().orElseThrow(() -> new AssertionError("Missing destination: " + route));
	}

	private static void assertBounds(AbstractWidget widget, int width, int height) {
		if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width
			|| widget.getY() + widget.getHeight() > height) throw new AssertionError("Control outside viewport");
	}

	private static void assertAllOptions(HgnOptionsScreen screen, Set<String> expected, int width, int height) {
		Set<String> found = new HashSet<>();
		List<AbstractWidget> pages = screen.children().stream()
			.filter(child -> child instanceof HgnButton button && button.getMessage().getString().startsWith("Game options "))
			.map(child -> (AbstractWidget) child).toList();
		for (AbstractWidget page : pages) {
			String selectedPage = page.getMessage().getString();
			HgnInputSmokeBridge.click(page, page.getX() + 1, page.getY() + 1);
			if (!(screen.getFocused() instanceof AbstractWidget focused)
				|| !focused.getMessage().getString().equals(selectedPage)) {
				throw new AssertionError("Options page lost keyboard focus: " + selectedPage);
			}
			int visibleOptions = 0;
			for (var child : screen.children()) {
				if (!(child instanceof AbstractWidget widget)) continue;
				assertBounds(widget, width, height);
				if (!(widget instanceof HgnButton)) {
					found.add(widget.getMessage().getString());
					visibleOptions++;
					if ((width < 680 || height < 380) && widget.getHeight() < 20) {
						throw new AssertionError("Compact option control became too small: "
							+ widget.getMessage().getString());
					}
				}
			}
			if (selectedPage.equals("Game options 1") && visibleOptions < Math.min(4, expected.size())) {
				throw new AssertionError("Compact options must show at least four settings");
			}
		}
		if (!found.equals(expected)) throw new AssertionError("Settings changed after rebuild: expected " + expected + ", found " + found);
	}
}
