package online.horizongate.hgn.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import online.horizongate.hgn.cosmetics.HgnCosmeticLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the supplied-model cosmetic layer to both normal and slim players. */
@Mixin(AvatarRenderer.class)
public abstract class HgnPlayerRendererMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	@SuppressWarnings("unchecked")
	private void hgn$addCosmetics(EntityRendererProvider.Context context, boolean slim,
		CallbackInfo callback) {
		AvatarRenderer<AbstractClientPlayer> renderer =
			(AvatarRenderer<AbstractClientPlayer>) (Object) this;
		((HgnLayerAccessor) this).hgn$addLayer(new HgnCosmeticLayer(renderer));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
		+ "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void hgn$applyCapePolicy(Avatar avatar, AvatarRenderState state,
		float partialTick, CallbackInfo callback) {
		if (avatar instanceof AbstractClientPlayer player
			&& HgnCosmeticLayer.hasRenderableCape(player)) {
			state.showCape = false;
		}
	}
}
