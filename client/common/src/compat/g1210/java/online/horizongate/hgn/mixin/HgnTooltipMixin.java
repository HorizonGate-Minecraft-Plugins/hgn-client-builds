package online.horizongate.hgn.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnTooltipArtwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HGN tooltip surface for the 1.21-1.21.1 renderer. */
@Mixin(TooltipRenderUtil.class)
public abstract class HgnTooltipMixin {
	@Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true)
	private static void hgn$renderTooltip(GuiGraphics graphics, int x, int y,
		int width, int height, int z, CallbackInfo callback) {
		if (!online.horizongate.hgn.ui.HgnMenuTheme.enabled()) return;
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(0.0f, 0.0f, z);
		HgnTooltipArtwork.render(graphics, x, y, width, height);
		pose.popPose();
		callback.cancel();
	}
}
