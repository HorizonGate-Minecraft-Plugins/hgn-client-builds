package online.horizongate.hgn.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.screen.HgnClientSettingsScreen;
import online.horizongate.hgn.screen.HgnCosmeticsScreen;
import online.horizongate.hgn.screen.HgnDashboardScreen;
import online.horizongate.hgn.screen.HgnInstalledModsScreen;
import online.horizongate.hgn.screen.HgnMultiplayerScreen;
import online.horizongate.hgn.screen.HgnSavedServersScreen;
import online.horizongate.hgn.screen.HgnSelectWorldScreen;
import online.horizongate.hgn.screen.HgnVideoSettingsScreen;

/** Routes an actual packaged Minecraft window for isolated CI screenshots. */
public final class HgnVisualCaptureSmoke {
	private static final String COMMAND_FILE = "visual-capture-command.txt";
	private static String activeRequest = "";
	private static String lastError = "";
	private static Screen expectedScreen;
	private static int stableTicks;

	private HgnVisualCaptureSmoke() {
	}

	public static void tick(Minecraft minecraft) {
		if (!HgnClient.visualCaptureSmoke() || minecraft.level != null
			|| minecraft.gameDirectory == null) return;
		Path commandFile = minecraft.gameDirectory.toPath().resolve(COMMAND_FILE);
		if (!Files.isRegularFile(commandFile)) return;
		String request;
		try {
			if (Files.size(commandFile) > 64L) return;
			request = Files.readString(commandFile, StandardCharsets.US_ASCII).trim();
		} catch (IOException exception) {
			if (!lastError.equals(exception.getClass().getSimpleName())) {
				lastError = exception.getClass().getSimpleName();
				HgnClient.LOGGER.warn("HGN_VISUAL_COMMAND_READ_FAILED={}", lastError);
			}
			return;
		}
		if (!request.equals(activeRequest)) {
			if (activeRequest.isEmpty() && !(minecraft.screen instanceof HgnDashboardScreen)) return;
			Screen destination = destination(request);
			activeRequest = request;
			if (destination == null) {
				HgnClient.LOGGER.warn("HGN_VISUAL_COMMAND_REJECTED={}", request);
				return;
			}
			expectedScreen = destination;
			stableTicks = 0;
			minecraft.setScreen(destination);
		}
		if (expectedScreen == null || minecraft.screen != expectedScreen) return;
		boolean wide = activeRequest.startsWith("wide/")
			|| activeRequest.startsWith("reference/");
		if (wide && (expectedScreen.width < 640 || expectedScreen.height < 360)) return;
		if (!wide && expectedScreen.width >= 640 && expectedScreen.height >= 360) return;
		if (++stableTicks < 20) return;
		HgnClient.LOGGER.info("HGN_VISUAL_READY={} SCREEN={} VIEWPORT={}x{}",
			activeRequest, expectedScreen.getClass().getSimpleName(),
			expectedScreen.width, expectedScreen.height);
		expectedScreen = null;
	}

	private static Screen destination(String request) {
		String[] parts = request.split("/", 2);
		if (parts.length != 2 || (!parts[0].equals("wide") && !parts[0].equals("reference")
			&& !parts[0].equals("compact"))) return null;
		Screen parent = new HgnDashboardScreen();
		return switch (parts[1]) {
			case "menu" -> parent;
			case "worlds" -> worldDestination(parent);
			case "servers" -> new HgnMultiplayerScreen(parent);
			case "saved-servers" -> new HgnSavedServersScreen(parent);
			case "mods" -> new HgnInstalledModsScreen(parent);
			case "cosmetics" -> new HgnCosmeticsScreen(parent);
			case "settings" -> new HgnClientSettingsScreen(parent);
			case "video" -> new HgnVideoSettingsScreen(parent, Minecraft.getInstance(),
				Minecraft.getInstance().options);
			default -> null;
		};
	}

	private static Screen worldDestination(Screen parent) {
		try {
			if (HgnWorldMenuSmokeFixture.prepare() == null) {
				throw new IllegalStateException("Packaged visual world route requires its disposable CI fixture");
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to prepare the packaged visual world-list fixture", exception);
		}
		return new HgnSelectWorldScreen(parent);
	}
}
