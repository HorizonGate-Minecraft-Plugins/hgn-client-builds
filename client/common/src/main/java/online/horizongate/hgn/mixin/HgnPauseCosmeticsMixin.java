package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnCosmeticsScreen;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.widget.HgnButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the same Cosmetics editor to the unobstructed pause-menu corner. */
@Mixin(PauseScreen.class)
public abstract class HgnPauseCosmeticsMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$addCosmeticsEditor(CallbackInfo callback) {
		if (!HgnConfig.get().interfaceEnabled
			|| (Object) this instanceof online.horizongate.hgn.screen.HgnPauseScreen
			|| !((PauseScreen) (Object) this).showsPauseMenu()) return;
		Screen screen = (Screen) (Object) this;
		int buttonWidth = Math.max(72, Math.min(116, screen.width / 3));
		int buttonHeight = screen.height < 260 ? 18 : 22;
		int x = Math.max(4, screen.width - buttonWidth - 8);
		int y = 8;
		((HgnScreenAccessor) screen).hgn$addRenderableWidget(HgnButton.dashboardCommand(
			x, y, buttonWidth, buttonHeight, Component.literal("COSMETICS"),
			HgnIcon.COSMETICS, true,
			button -> Minecraft.getInstance().setScreen(new HgnCosmeticsScreen(screen))));
	}
}
