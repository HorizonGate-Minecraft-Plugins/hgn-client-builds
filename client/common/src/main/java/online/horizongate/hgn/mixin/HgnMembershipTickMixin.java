package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnPerformanceGuard;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.net.HgnBossEvents;
import online.horizongate.hgn.update.HgnUpdateDestinations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives client-only member heartbeats without involving a Minecraft server plugin. */
@Mixin(Minecraft.class)
public abstract class HgnMembershipTickMixin {
	@Inject(method = {"setLevel", "clearClientLevel"}, at = @At("HEAD"))
	private void hgn$updateLevelChanging(CallbackInfo callback) {
		HgnUpdateDestinations.changingLevel();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void hgn$membershipTick(CallbackInfo callback) {
		Minecraft minecraft = (Minecraft) (Object) this;
		HgnPerformanceGuard.tick(minecraft);
		online.horizongate.hgn.HgnEntityRenderBudget.tick(minecraft);
		HgnCosmetics.tick(minecraft);
		HgnDungeons.tick();
		HgnBossEvents.tick(minecraft);
		HgnUpdateDestinations.tick(minecraft);
		online.horizongate.hgn.update.HgnUpdateRuntime.tick(minecraft);
		online.horizongate.hgn.ui.HgnVisualCaptureSmoke.tick(minecraft);
		online.horizongate.hgn.ui.HgnHotbarCaptureSmoke.tick(minecraft);
	}
}
