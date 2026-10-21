package online.horizongate.hgn.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import online.horizongate.hgn.cosmetics.HgnCosmeticLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the supplied-model cosmetic layer to both normal and slim players. */
@Mixin(PlayerRenderer.class)
public abstract class HgnPlayerRendererMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void hgn$addCosmetics(EntityRendererProvider.Context context, boolean slim,
		CallbackInfo callback) {
		PlayerRenderer renderer = (PlayerRenderer) (Object) this;
		((HgnLayerAccessor) this).hgn$addLayer(new HgnCosmeticLayer(renderer));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;"
		+ "Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
	private void hgn$applyCapePolicy(AbstractClientPlayer player, PlayerRenderState state,
		float partialTick, CallbackInfo callback) {
		if (HgnCosmeticLayer.hasRenderableCape(player)) {
			state.showCape = false;
		}
	}
}
