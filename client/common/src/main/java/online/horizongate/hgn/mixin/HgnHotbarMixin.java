package online.horizongate.hgn.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.HgnHotbar;
import online.horizongate.hgn.ui.HgnHotbarCaptureSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the item hotbar after HGN has drawn every required live element. */
@Mixin(Gui.class)
public abstract class HgnHotbarMixin {
	@Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true, require = 0)
	private void hgn$renderHotbar(GuiGraphics graphics, DeltaTracker deltaTracker,
		CallbackInfo callback) {
		if (HgnHotbar.render(graphics)) {
			HgnHotbarCaptureSmoke.customRendered();
			callback.cancel();
		}
	}

	@Inject(method = "renderItemHotbar", at = @At("TAIL"), require = 0)
	private void hgn$nativeHotbarRendered(GuiGraphics graphics, DeltaTracker deltaTracker,
		CallbackInfo callback) {
		HgnHotbarCaptureSmoke.nativeRendered();
	}
}
