package online.horizongate.hgn.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import online.horizongate.hgn.HgnEntityRenderBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class HgnEntityRenderBudgetMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void hgn$budgetDistantItems(Entity entity, Frustum frustum,
		double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> callback) {
		if (HgnEntityRenderBudget.skip(entity, cameraX, cameraY, cameraZ)) callback.setReturnValue(false);
	}
}
