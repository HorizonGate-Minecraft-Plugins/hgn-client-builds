package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.components.tabs.TabManager;
import online.horizongate.hgn.screen.HgnCreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inert unless explicitly bound to one exact native Create World owner. */
@Mixin(TabManager.class)
public abstract class HgnCreateWorldTabManagerMixin implements HgnCreateWorldScreen.TabHost {
	@Unique private HgnCreateWorldScreen hgn$creation;

	@Override
	public void hgn$bindCreation(HgnCreateWorldScreen composition) {
		hgn$creation = composition;
	}

	@Inject(method = {"setCurrentTab", "setTabArea"}, at = @At("TAIL"))
	private void hgn$afterNativeTabLayout(CallbackInfo callback) {
		if (hgn$creation != null && hgn$creation.owns((TabManager) (Object) this)) hgn$creation.arrange();
	}
}
