package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.container.HgnContainerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents MerchantScreen from painting a second vanilla offer list over HGN. */
@Mixin(MerchantScreen.class)
public abstract class HgnMerchantScreenMixin {
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		shift = At.Shift.AFTER), cancellable = true)
	private void hgn$finishCustomMerchant(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (!HgnContainerRenderer.renderMerchantTooltip(screen, graphics, mouseX, mouseY)) {
			((HgnContainerAccessor) (Object) screen).hgn$renderTooltip(graphics, mouseX, mouseY);
		}
		callback.cancel();
	}
}
