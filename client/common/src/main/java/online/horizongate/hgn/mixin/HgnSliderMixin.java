package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnTypography;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces slider artwork while retaining Mojang's value, input, and narration behavior. */
@Mixin(AbstractSliderButton.class)
public abstract class HgnSliderMixin {
	@Shadow protected double value;

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void hgn$renderSlider(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!online.horizongate.hgn.ui.HgnMenuTheme.enabled()) return;
		AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
		int x = slider.getX();
		int y = slider.getY();
		int width = slider.getWidth();
		int height = slider.getHeight();
		HgnSurface.control(graphics, x, y, width, height, slider.isHovered() ? 1.0f : 0.0f,
			slider.isFocused(), false, false, !slider.active);
		int trackX = x + 9;
		int trackY = y + height - 7;
		int trackWidth = Math.max(1, width - 18);
		HgnSurface.meter(graphics, trackX, trackY, trackWidth, 4,
			(float) value, HgnPalette.CYAN);
		int handleX = trackX + Math.round((trackWidth - 4) * (float) value);
		HgnDraw.rect(graphics, handleX, trackY - 2, handleX + 4, trackY + 6,
			slider.active ? HgnPalette.CYAN_BRIGHT : HgnPalette.DISABLED);
		var font = Minecraft.getInstance().font;
		String label = slider.getMessage().getString();
		float fit = Math.min(Math.min(1f, Math.max(1, height - 10) / (float) font.lineHeight),
			Math.max(1, width - 12) / (float) Math.max(1, HgnTypography.width(font, label)));
		HgnTypography.centered(graphics, font, label, x + width / 2, y + 2, fit,
			slider.active ? HgnPalette.TEXT : HgnPalette.DISABLED);
		callback.cancel();
	}
}
