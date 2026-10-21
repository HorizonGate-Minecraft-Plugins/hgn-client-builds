package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Remapped access to the merchant's current offer page and selection. */
@Mixin(MerchantScreen.class)
public interface HgnMerchantAccessor {
	@Accessor("scrollOff")
	int hgn$getScrollOffset();

	@Accessor("shopItem")
	int hgn$getSelectedOffer();
}
