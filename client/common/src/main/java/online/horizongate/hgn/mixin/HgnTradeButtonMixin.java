package online.horizongate.hgn.mixin;

import online.horizongate.hgn.ui.container.HgnTradeButtonState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Retains vanilla trade selection callbacks while exposing their row index. */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton")
public abstract class HgnTradeButtonMixin implements HgnTradeButtonState {
	@Shadow @Final private int index;

	@Override
	public int hgn$offerRow() {
		return index;
	}
}
