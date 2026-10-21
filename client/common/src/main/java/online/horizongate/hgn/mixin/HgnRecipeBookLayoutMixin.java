package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import online.horizongate.hgn.HgnConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Positions native recipe controls beside the actual, resized container. */
@Mixin(RecipeBookComponent.class)
public abstract class HgnRecipeBookLayoutMixin {
	@ModifyConstant(method = "initVisuals", constant = @Constant(intValue = 86))
	private int hgn$bookOffset(int original) {
		if (HgnConfig.get().containerLayoutsEnabled()
			&& Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) {
			return ((HgnContainerAccessor) screen).hgn$getImageWidth() / 2 + 2;
		}
		return original;
	}
}
