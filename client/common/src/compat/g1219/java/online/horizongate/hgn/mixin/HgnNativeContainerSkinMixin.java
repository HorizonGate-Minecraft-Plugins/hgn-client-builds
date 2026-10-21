package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import online.horizongate.hgn.ui.container.HgnNativeContainerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class HgnNativeContainerSkinMixin {
	@Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V",
		at = @At("HEAD"), cancellable = true)
	private void hgn$workstationBackground(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, ResourceLocation texture,
		int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight,
		CallbackInfo callback) {
		if (HgnNativeContainerSkin.draw((GuiGraphics) (Object) this, texture.toString(),
			x, y, u, v, width, height)) callback.cancel();
	}

	@ModifyVariable(method = "drawString", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int hgn$readableWorkstationLabel(int color) {
		return HgnNativeContainerSkin.readableColor(color);
	}
}
