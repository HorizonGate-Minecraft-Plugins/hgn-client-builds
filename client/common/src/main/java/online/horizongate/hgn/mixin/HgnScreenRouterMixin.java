package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnDashboardScreen;
import online.horizongate.hgn.screen.HgnOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes title, options, language and multiplayer destinations into their HGN screens. */
@Mixin(value = Minecraft.class, priority = 2000)
public abstract class HgnScreenRouterMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void hgn$brandWindowTitle(CallbackInfoReturnable<String> callback) {
		if (HgnConfig.get().interfaceEnabled) {
			callback.setReturnValue("HorizonGate Network Client " + HgnClient.minecraftVersion());
		}
	}

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void hgn$routeTitle(Screen screen, CallbackInfo callback) {
		if (!HgnConfig.get().interfaceEnabled) return;
		Minecraft minecraft = (Minecraft) (Object) this;
		if (HgnClient.videoRuntimeSmoke() && minecraft.screen instanceof HgnOptionsScreen && screen != null) {
			HgnClient.LOGGER.info("HGN_SODIUM_VIDEO_DESTINATION={}", screen.getClass().getName());
		}
		if (screen instanceof net.minecraft.client.gui.screens.PauseScreen pause && pause.showsPauseMenu()
			&& screen.getClass() == net.minecraft.client.gui.screens.PauseScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnPauseScreen());
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnSavedServersScreen(
				((HgnMultiplayerScreenAccessor) screen).hgn$parent()));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.OptionsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnOptionsScreen(
				((HgnOptionsScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.LanguageSelectScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnLanguageSelectScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options, minecraft.getLanguageManager()));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.FontOptionsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnFontOptionsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.SoundOptionsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnSoundOptionsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.controls.ControlsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnControlsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.MouseSettingsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnMouseSettingsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.controls.KeyBindsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnKeyBindsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.SkinCustomizationScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnSkinCustomizationScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.ChatOptionsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnChatOptionsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.VideoSettingsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnVideoSettingsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft, minecraft.options));
			callback.cancel();
			return;
		}
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen.class) {
			minecraft.setScreen(new online.horizongate.hgn.screen.HgnAccessibilityOptionsScreen(
				((HgnOptionsSubScreenAccessor) screen).hgn$parent(), minecraft.options));
			callback.cancel();
			return;
		}
		if (!(screen instanceof TitleScreen)) return;
		if (minecraft.screen instanceof HgnDashboardScreen) {
			callback.cancel();
			return;
		}
		minecraft.setScreen(new HgnDashboardScreen());
		callback.cancel();
	}
}
