package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces every vanilla text-field frame while preserving its editor and narration logic. */
@Mixin(EditBox.class)
public abstract class HgnEditBoxMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"))
	private void hgn$renderFrame(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!online.horizongate.hgn.ui.HgnMenuTheme.enabled()) return;
		EditBox field = (EditBox) (Object) this;
		field.setBordered(false);
		HgnSurface.control(graphics,
			field.getX(), field.getY(), field.getWidth(), field.getHeight(),
			field.isHovered() ? 1.0f : 0.0f, field.isFocused(), false, false, !field.active);
	}
}
