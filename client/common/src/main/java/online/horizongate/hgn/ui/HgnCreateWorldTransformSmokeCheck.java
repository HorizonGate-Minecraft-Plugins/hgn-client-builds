package online.horizongate.hgn.ui;

import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.screen.HgnCreateWorldScreen;

/** Transformation check only: deliberately never constructs a creation context or native screen. */
public final class HgnCreateWorldTransformSmokeCheck {
	private HgnCreateWorldTransformSmokeCheck() {
	}

	public static void run() {
		if (!HgnCreateWorldScreen.Host.class.isAssignableFrom(CreateWorldScreen.class)
			|| !HgnCreateWorldScreen.TabHost.class.isAssignableFrom(TabManager.class)) {
			throw new AssertionError("Create World composition mixins were not applied");
		}
		HgnClient.LOGGER.info("HGN_CREATE_WORLD_TRANSFORM_SMOKE_OK: transformation only; no creation context, UI, input, callbacks, data-pack loading or world creation exercised");
	}
}
