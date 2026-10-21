package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnControlRenderer;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.widget.HgnButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the clean-room control skin without replacing vanilla button behavior. */
@Mixin(AbstractButton.class)
public abstract class HgnButtonMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void hgn$renderControl(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
		CallbackInfo callback) {
		AbstractButton button = (AbstractButton) (Object) this;
		if (!online.horizongate.hgn.ui.HgnMenuTheme.enabled() || button instanceof HgnButton) return;
		HgnControlRenderer.render(button, graphics, HgnIcon.NONE, false, false, false);
		callback.cancel();
	}
}
