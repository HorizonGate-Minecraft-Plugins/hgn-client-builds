package online.horizongate.hgn.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.HgnDragonEye;
import online.horizongate.hgn.HgnHud;
import online.horizongate.hgn.HgnGameplayHud;
import online.horizongate.hgn.HgnGodsEyeHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets an active, server-authorized Dragon Eye sense temporarily own the view. */
@Mixin(Gui.class)
public abstract class HgnDragonEyeMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hgn$dragonEye(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
		if (!HgnDragonEye.owningScreen()) return;
		HgnDragonEye.render(graphics);
		callback.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void hgn$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
		HgnGameplayHud.render(graphics);
		HgnHud.render(graphics);
		HgnGodsEyeHud.render(graphics);
	}
}
