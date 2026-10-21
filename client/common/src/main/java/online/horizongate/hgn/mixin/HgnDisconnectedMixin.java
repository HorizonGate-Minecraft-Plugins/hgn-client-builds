package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnDisconnectedScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains native callback, narration, Escape and parent semantics on the exact original screen. */
@Mixin(DisconnectedScreen.class)
public abstract class HgnDisconnectedMixin extends Screen implements HgnDisconnectedScreen.Host {
	@Shadow @Final private Screen parent;
	@Shadow @Final private DisconnectionDetails details;
	@Unique private HgnDisconnectedScreen hgn$composition;
	@Unique private boolean hgn$initialized;

	protected HgnDisconnectedMixin(Component title) { super(title); }

	@Unique
	private boolean hgn$ownsDisconnected() {
		return ((Object) this).getClass() == DisconnectedScreen.class && HgnConfig.get().interfaceEnabled
			&& width >= 320 && height >= 180;
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void hgn$resetDisconnected(CallbackInfo callback) {
		hgn$initialized = false;
		if (hgn$composition != null) hgn$composition.disposeForReinit();
		hgn$composition = null;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$attachDisconnected(CallbackInfo callback) {
		hgn$initialized = true;
		hgn$arrangeDisconnected();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void hgn$resizeDisconnected(CallbackInfo callback) { hgn$arrangeDisconnected(); }

	@Unique
	private void hgn$arrangeDisconnected() {
		if (!hgn$initialized) return;
		if (!hgn$ownsDisconnected()) {
			if (hgn$composition != null) {
				hgn$composition.dispose();
				hgn$composition = null;
				repositionElements();
			}
			return;
		}
		if (hgn$composition == null) hgn$composition = HgnDisconnectedScreen.attach((DisconnectedScreen) (Object) this, this);
		if (hgn$composition != null) hgn$composition.arrange();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (hgn$composition != null && !hgn$ownsDisconnected()) hgn$arrangeDisconnected();
		if (hgn$composition != null && hgn$composition.owns()) hgn$composition.renderBackground(graphics, mouseX, mouseY);
		else super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}

	@Override public DisconnectionDetails hgn$disconnectDetails() { return details; }
	@Override public Screen hgn$disconnectParent() { return parent; }
	@Override public void hgn$disconnectAdd(AbstractWidget widget) { addRenderableWidget(widget); }
	@Override public void hgn$disconnectRemove(AbstractWidget widget) { removeWidget(widget); }
	@Override public HgnDisconnectedScreen hgn$disconnectComposition() { return hgn$composition; }
}
