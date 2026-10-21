package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.compat.HgnCompat;

/** Detached constructor/init/resize only: never starts or cancels a connection. */
public final class HgnConnectionSmokeCheck {
	private HgnConnectionSmokeCheck() { }

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		InputType previousInput = minecraft.getLastInputType();
		boolean enabled = HgnConfig.get().interfaceEnabled;
		try {
			minecraft.setLastInputType(InputType.KEYBOARD_TAB);
			for (int[] size : new int[][] {{320, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
				ConnectScreen screen = detached(parent);
				HgnConfig.get().interfaceEnabled = true;
				HgnCompat.initScreen(screen, size[0], size[1]);
				assertCancel(screen, size[0], size[1], true);
				HgnCompat.resizeScreen(screen, 320, 180);
				assertCancel(screen, 320, 180, true);
				HgnConfig.get().interfaceEnabled = false;
				HgnCompat.initScreen(screen, size[0], size[1]);
				assertCancel(screen, size[0], size[1], false);
				if (minecraft.screen != parent) throw new AssertionError("Detached connection changed the live screen");
			}
		} finally {
			minecraft.setLastInputType(previousInput);
			HgnConfig.get().interfaceEnabled = enabled;
		}
		HgnClient.LOGGER.info("HGN_CONNECTION_SMOKE_OK: detached native Cancel, keyboard initialization and resize retained; no connection, cancellation, rendering or network operation executed");
	}

	private static ConnectScreen detached(Screen parent) {
		try {
			var constructor = ConnectScreen.class.getDeclaredConstructor(Screen.class, Component.class);
			constructor.setAccessible(true);
			return constructor.newInstance(parent, CommonComponents.CONNECT_FAILED);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("Native connection constructor changed", error);
		}
	}

	private static void assertCancel(ConnectScreen screen, int width, int height, boolean themed) {
		if (screen.children().size() != 1 || !(screen.children().get(0) instanceof Button button)
			|| !button.getMessage().equals(CommonComponents.GUI_CANCEL) || !button.active || !button.visible) {
			throw new AssertionError("Original connection Cancel is missing");
		}
		HgnRect expected = themed ? HgnConnectionLayout.resolve(width, height).cancel()
			: new HgnRect(width / 2 - 100, height / 4 + 132, 200, 20);
		if (!expected.equals(new HgnRect(button.getX(), button.getY(), button.getWidth(), button.getHeight()))) {
			throw new AssertionError("Connection Cancel geometry differs from selected presentation");
		}
		if (screen.shouldCloseOnEsc()) throw new AssertionError("Native Escape behavior changed");
		if (screen.getFocused() != button) throw new AssertionError("Native keyboard initialization lost Cancel");
	}
}
