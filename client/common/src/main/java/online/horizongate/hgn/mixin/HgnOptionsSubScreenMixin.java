package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.widget.HgnSettingsHeader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives option categories the HGN shell while retaining their real settings and inputs. */
@Mixin(OptionsSubScreen.class)
public abstract class HgnOptionsSubScreenMixin {
	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void hgn$resizeHeader(CallbackInfo callback) {
		OptionsSubScreen screen = (OptionsSubScreen) (Object) this;
		for (var child : screen.children()) {
			if (child instanceof HgnSettingsHeader header) header.setWidth(Math.max(1, screen.width - 16));
		}
	}

	@Inject(method = "addTitle", at = @At("HEAD"), cancellable = true)
	private void hgn$header(CallbackInfo callback) {
		if (!HgnConfig.get().interfaceEnabled) return;
		OptionsSubScreen screen = (OptionsSubScreen) (Object) this;
		String className = screen.getClass().getName();
		if (!className.startsWith("net.minecraft.")
			&& !className.startsWith("online.horizongate.hgn.screen.")) return;
		screen.layout.setHeaderHeight(48);
		screen.layout.setFooterHeight(36);
		((HgnScreenAccessor) screen).hgn$addRenderableWidget(new HgnSettingsHeader(screen.width, screen.getTitle()));
		callback.cancel();
	}
}
