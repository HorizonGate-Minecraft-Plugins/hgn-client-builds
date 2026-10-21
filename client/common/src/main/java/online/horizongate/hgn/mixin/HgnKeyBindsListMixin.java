package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import online.horizongate.hgn.screen.HgnKeyBindsScreen;
import online.horizongate.hgn.ui.HgnKeyBindsLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native row/hit/scrollbar geometry follows the dedicated page, including logical widths below 340. */
@Mixin(KeyBindsList.class)
public abstract class HgnKeyBindsListMixin {
	@Shadow @Final private KeyBindsScreen keyBindsScreen;

	@Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
	private void hgn$keyBindsRowWidth(CallbackInfoReturnable<Integer> callback) {
		if (keyBindsScreen instanceof HgnKeyBindsScreen) {
			callback.setReturnValue(HgnKeyBindsLayout.rowWidth(keyBindsScreen.width));
		}
	}
}
