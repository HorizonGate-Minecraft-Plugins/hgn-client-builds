package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.container.HgnContainerRenderer;
import online.horizongate.hgn.ui.container.HgnContainerLayout;
import online.horizongate.hgn.ui.container.HgnContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.inventory.Slot;
import online.horizongate.hgn.compat.HgnDraw;

/** Installs native slot coordinates for the 1.21.11 slot-rendering API. */
@Mixin(AbstractContainerScreen.class)
public abstract class HgnContainerScreenMixin {
	private boolean hgn$scaledSlot;
	private GuiGraphics hgn$slotGraphics;
	private boolean hgn$scaledHighlight;

	@Inject(method = {"renderSlotHighlightBack", "renderSlotHighlightFront"}, at = @At("HEAD"), require = 0)
	private void hgn$scaleHighlight(GuiGraphics graphics, CallbackInfo callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		var screen = (AbstractContainerScreen<?>) (Object) this;
		var layout = HgnContainers.layout(screen);
		var hovered = ((HgnContainerAccessor) this).hgn$getHoveredSlot();
		if (hovered == null || layout.slotSize() == 16) return;
		HgnDraw.beginScaleAt(graphics, layout.slotSize() / 16.0f, hovered.x, hovered.y);
		hgn$scaledHighlight = true;
	}

	@Inject(method = {"renderSlotHighlightBack", "renderSlotHighlightFront"}, at = @At("RETURN"), require = 0)
	private void hgn$restoreHighlight(GuiGraphics graphics, CallbackInfo callback) {
		if (!hgn$scaledHighlight) return;
		HgnDraw.endScale(graphics);
		hgn$scaledHighlight = false;
	}

	private void hgn$scaleSlot(GuiGraphics graphics, Slot slot, CallbackInfo callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		HgnContainerLayout layout = HgnContainers.layout((AbstractContainerScreen<?>) (Object) this);
		if (layout == null || layout.slotSize() == 16) return;
		HgnDraw.beginScaleAt(graphics, layout.slotSize() / 16.0f, slot.x, slot.y);
		hgn$slotGraphics = graphics;
		hgn$scaledSlot = true;
	}

	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void hgn$scaleModernSlot(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
		hgn$scaleSlot(graphics, slot, callback);
	}

	@Inject(method = "renderSlot", at = @At("RETURN"))
	private void hgn$restoreSlotScale(CallbackInfo callback) {
		if (!hgn$scaledSlot) return;
		HgnDraw.endScale(hgn$slotGraphics);
		hgn$scaledSlot = false;
		hgn$slotGraphics = null;
	}

	@Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
	private void hgn$slotHitArea(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		HgnContainerLayout layout = HgnContainers.layout(screen);
		if (layout == null || layout.slotSize() == 16) return;
		HgnContainerAccessor accessor = (HgnContainerAccessor) screen;
		int x = accessor.hgn$getLeftPos() + slot.x;
		int y = accessor.hgn$getTopPos() + slot.y;
		callback.setReturnValue(mouseX >= x - 1 && mouseX < x + layout.slotSize() + 1
			&& mouseY >= y - 1 && mouseY < y + layout.slotSize() + 1);
	}
	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$installLayout(CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) {
			HgnContainers.install((AbstractContainerScreen<?>) (Object) this);
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void hgn$syncDynamicSlots(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) {
			HgnContainers.install((AbstractContainerScreen<?>) (Object) this);
		}
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void hgn$renderContainerBackground(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		HgnContainerLayout layout = HgnContainers.layout(screen);
		if (layout == null || !layout.kind().replacementSafe()) return;
		HgnContainerRenderer.render(screen, graphics, mouseX, mouseY);
		callback.cancel();
	}
}
