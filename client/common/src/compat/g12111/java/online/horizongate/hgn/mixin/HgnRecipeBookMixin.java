package online.horizongate.hgn.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.container.HgnRecipeBookArtwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean-room recipe-book panel for the 1.21.11 renderer. */
@Mixin(RecipeBookComponent.class)
public abstract class HgnRecipeBookMixin {
	@Shadow private int xOffset;
	@Shadow private int width;
	@Shadow private int height;

	@Inject(method = "render", at = @At("HEAD"))
	private void hgn$renderPanel(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled() && ((RecipeBookComponent) (Object) this).isVisible()) {
			HgnRecipeBookArtwork.render(graphics, width, height, xOffset);
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void hgn$removeVanillaPanel(GuiGraphics graphics, RenderPipeline pipeline,
		Identifier texture, int x, int y, float u, float v, int drawWidth,
		int drawHeight, int textureWidth, int textureHeight) {
		if (!HgnConfig.get().containerLayoutsEnabled()) {
			graphics.blit(pipeline, texture, x, y, u, v, drawWidth, drawHeight,
				textureWidth, textureHeight);
		}
	}
}
