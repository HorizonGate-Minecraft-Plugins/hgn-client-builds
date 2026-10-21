package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import online.horizongate.hgn.screen.HgnSelectWorldScreen;
import online.horizongate.hgn.ui.HgnWorldRowMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Only bounds the native save-ID/date line; the native entry still owns selection and actions. */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class HgnWorldRowMetadataMixin {
	@ModifyArgs(method = "render", at = @At(value = "INVOKE", ordinal = 1,
		target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
		require = 0)
	private void hgn$fitLegacyMetadata(Args args) {
		if (Minecraft.getInstance().screen instanceof HgnSelectWorldScreen screen) {
			Font font = args.get(0);
			String metadata = args.get(1);
			int x = args.get(2);
			args.set(1, HgnWorldRowMetadata.fit(font, metadata, screen.worldMetadataRightEdge() - x));
		}
	}
}
