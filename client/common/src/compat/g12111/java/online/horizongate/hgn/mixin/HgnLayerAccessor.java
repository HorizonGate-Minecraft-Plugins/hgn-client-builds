package online.horizongate.hgn.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's layer registration only during PlayerRenderer construction. */
@Mixin(LivingEntityRenderer.class)
public interface HgnLayerAccessor {
	@Invoker("addLayer")
	boolean hgn$addLayer(RenderLayer<?, ?> layer);
}


