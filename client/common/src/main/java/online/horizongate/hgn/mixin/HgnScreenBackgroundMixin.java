package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnJoin;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes panorama, dirt, blur, and default menu backdrops from non-container screens. */
@Mixin(Screen.class)
public abstract class HgnScreenBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void hgn$renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!HgnConfig.get().interfaceEnabled) return;
		Screen screen = (Screen) (Object) this;
		if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) return;
		HgnJoin.observe(screen);
		// HGN's dashboard and store own their scene and individually separated
		// panels. A second page-sized glass sheet hid the city in both screens.
		if (screen.getClass().getName().startsWith("online.horizongate.hgn.screen.Hgn")) {
			callback.cancel();
			return;
		}
		if (!screen.getClass().getName().startsWith("net.minecraft.")) return;
		// Keep the paused world visible. Controls receive their own image-backed
		// surfaces, so a second page-sized layer only obscures gameplay.
		if (screen instanceof PauseScreen) {
			HgnSurface.veil(graphics, screen.width, screen.height);
			int panelWidth = Math.min(244, screen.width - 16);
			int panelHeight = Math.min(222, screen.height - 18);
			HgnSurface.panel(graphics, (screen.width - panelWidth) / 2,
				Math.max(9, screen.height / 4 - 29), panelWidth, panelHeight, false);
			callback.cancel();
			return;
		}
		HgnScreenBackdrop.render(screen, graphics, mouseX, mouseY);
		int margin = Math.max(8, Math.min(18, screen.width / 60));
		int panelWidth = screen instanceof OptionsScreen
			? Math.min(340, screen.width - margin * 2) : screen.width - margin * 2;
		HgnSurface.glass(graphics, (screen.width - panelWidth) / 2, margin,
			Math.max(1, panelWidth), Math.max(1, screen.height - margin * 2), false);
		callback.cancel();
	}
}
