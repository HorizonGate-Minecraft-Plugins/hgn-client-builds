package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnLoadingArtwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints the HGN startup/reload presentation after vanilla updates its reload state. */
@Mixin(LoadingOverlay.class)
public abstract class HgnLoadingOverlayMixin {
	@Shadow private float currentProgress;
	@Shadow @Final private ReloadInstance reload;

	@Inject(method = "render", at = @At("TAIL"))
	private void hgn$renderLoading(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!HgnConfig.get().interfaceEnabled) return;
		if (!reload.isDone()) {
			HgnLoadingArtwork.renderBootstrap(graphics, currentProgress);
			return;
		}
		HgnLoadingArtwork.render(graphics, currentProgress, "HGN CLIENT",
			"Loading interface and game resources");
	}
}
