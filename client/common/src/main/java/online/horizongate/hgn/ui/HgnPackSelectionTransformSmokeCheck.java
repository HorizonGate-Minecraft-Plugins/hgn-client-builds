package online.horizongate.hgn.ui;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.screen.HgnPackSelectionScreen;

/** Transformation only: constructing the native screen would acquire a directory watcher. */
public final class HgnPackSelectionTransformSmokeCheck {
	private HgnPackSelectionTransformSmokeCheck() {
	}

	public static void run() {
		if (!HgnPackSelectionScreen.Host.class.isAssignableFrom(PackSelectionScreen.class)) {
			throw new AssertionError("Pack Selection composition mixin was not applied");
		}
		HgnClient.LOGGER.info("HGN_PACK_SELECTION_TRANSFORM_SMOKE_OK: transformation only; no native screen, watcher, repository, UI, input, pack transfer, confirmation or commit exercised");
	}
}
