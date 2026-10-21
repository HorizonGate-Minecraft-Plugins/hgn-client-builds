package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.screen.HgnSelectWorldScreen;
import online.horizongate.hgn.ui.HgnWorldRowMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only bounds the native save-ID/date line; the native entry still owns selection and actions. */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class HgnWorldRowMetadataMixin {
	@Unique private Component hgn$originalMetadata;

	@Redirect(method = "renderContent", at = @At(value = "INVOKE", ordinal = 1,
		target = "Lnet/minecraft/client/gui/components/StringWidget;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
		require = 0)
	private void hgn$fitModernMetadata(StringWidget widget, GuiGraphics graphics,
		int mouseX, int mouseY, float partialTick) {
		if (hgn$originalMetadata == null) hgn$originalMetadata = widget.getMessage();
		if (Minecraft.getInstance().screen instanceof HgnSelectWorldScreen screen) {
			Font font = Minecraft.getInstance().font;
			String fitted = HgnWorldRowMetadata.fit(font, hgn$originalMetadata.getString(),
				screen.worldMetadataRightEdge() - widget.getX());
			if (!widget.getMessage().getString().equals(fitted)) {
				widget.setMessage(Component.literal(fitted).withStyle(hgn$originalMetadata.getStyle()));
			}
		} else if (widget.getMessage() != hgn$originalMetadata) {
			widget.setMessage(hgn$originalMetadata);
		}
		widget.render(graphics, mouseX, mouseY, partialTick);
	}
}
