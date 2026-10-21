package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import online.horizongate.hgn.HgnConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** These screens draw fixed vanilla labels independently of the movable title fields. */
@Mixin({CreativeModeInventoryScreen.class, MerchantScreen.class})
public abstract class HgnContainerLabelsMixin {
	@Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
	private void hgn$replaceLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) callback.cancel();
	}
}
