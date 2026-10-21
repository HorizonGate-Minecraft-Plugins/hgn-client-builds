package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import online.horizongate.hgn.HgnConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes list wallpaper while keeping entries, selection, scrolling, and narration intact. */
@Mixin(AbstractSelectionList.class)
public abstract class HgnSelectionListMixin {
	@Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
	private void hgn$renderListSurface(GuiGraphics graphics, CallbackInfo callback) {
		if (!online.horizongate.hgn.ui.HgnMenuTheme.enabled()) return;
		// The owning screen already paints one continuous HGN backdrop. A second
		// list-sized sheet exposes implementation-specific list widths as a false
		// split panel (notably on the fullscreen Language screen).
		callback.cancel();
	}
}
