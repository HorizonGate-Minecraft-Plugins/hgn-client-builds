package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import online.horizongate.hgn.HgnConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Uses the resized container width for native recipe-book overlay mode. */
@Mixin(net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen.class)
public abstract class HgnRecipeBookScreenMixin {
	@ModifyConstant(method = "init", constant = @Constant(intValue = 379))
	private int hgn$narrowThreshold(int original) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return original;
		return ((HgnContainerAccessor) (AbstractContainerScreen<?>) (Object) this).hgn$getImageWidth() + 190;
	}
}
