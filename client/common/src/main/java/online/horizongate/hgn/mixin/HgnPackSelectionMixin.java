package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnPackSelectionScreen;
import online.horizongate.hgn.ui.HgnPackSelectionLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native model, watcher, confirmation, keyboard and commit methods are not replaced. */
@Mixin(PackSelectionScreen.class)
public abstract class HgnPackSelectionMixin extends Screen implements HgnPackSelectionScreen.Host {
	@Shadow private TransferableSelectionList availablePackList;
	@Shadow private TransferableSelectionList selectedPackList;
	@Shadow private Button doneButton;
	@Unique private HgnPackSelectionScreen hgn$composition;
	@Unique private boolean hgn$initialized;

	protected HgnPackSelectionMixin(Component title) { super(title); }

	@Unique
	private boolean hgn$ownsPackScreen() {
		return ((Object) this).getClass() == PackSelectionScreen.class && HgnConfig.get().interfaceEnabled
			&& HgnPackSelectionLayout.supported(width, height);
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void hgn$resetPacks(CallbackInfo callback) {
		hgn$initialized = false;
		if (hgn$composition != null) hgn$composition.disposeForReinit();
		hgn$composition = null;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$attachPacks(CallbackInfo callback) {
		hgn$initialized = true;
		hgn$arrangePacks();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void hgn$resizePacks(CallbackInfo callback) { hgn$arrangePacks(); }

	@Unique
	private void hgn$arrangePacks() {
		if (!hgn$initialized) return;
		if (!hgn$ownsPackScreen()) {
			if (hgn$composition != null) {
				hgn$composition.dispose();
				hgn$composition = null;
				repositionElements();
			}
			return;
		}
		if (hgn$composition == null) hgn$composition = HgnPackSelectionScreen.attach((PackSelectionScreen) (Object) this, this);
		if (hgn$composition != null) hgn$composition.arrange();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (hgn$composition != null && !hgn$ownsPackScreen()) hgn$arrangePacks();
		if (hgn$composition != null && hgn$composition.owns()) hgn$composition.renderBackground(graphics, mouseX, mouseY);
		else super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void setFocused(GuiEventListener focused) {
		super.setFocused(focused);
		if (hgn$composition != null && hgn$ownsPackScreen()) hgn$composition.followNativeFocus(focused);
	}

	@Override public TransferableSelectionList hgn$availablePacks() { return availablePackList; }
	@Override public TransferableSelectionList hgn$selectedPacks() { return selectedPackList; }
	@Override public Button hgn$packDone() { return doneButton; }
	@Override public void hgn$packAdd(AbstractWidget widget) { addRenderableWidget(widget); }
	@Override public void hgn$packRemove(AbstractWidget widget) { removeWidget(widget); }
	@Override public HgnPackSelectionScreen hgn$packComposition() { return hgn$composition; }
}
