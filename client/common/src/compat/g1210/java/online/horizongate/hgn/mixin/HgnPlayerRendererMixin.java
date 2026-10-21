package online.horizongate.hgn.mixin;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import online.horizongate.hgn.cosmetics.HgnCosmeticLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds supplied-model cosmetics and gives an equipped HGN cape the cloak silhouette. */
@Mixin(PlayerRenderer.class)
public abstract class HgnPlayerRendererMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void hgn$addCosmetics(EntityRendererProvider.Context context, boolean slim,
		CallbackInfo callback) {
		PlayerRenderer renderer = (PlayerRenderer) (Object) this;
		((HgnLayerAccessor) this).hgn$addLayer(new HgnCosmeticLayer(renderer));
	}
}
