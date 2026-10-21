package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.container.HgnContainerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps enchantment clicks and tooltips aligned with the expanded HGN cards. */
@Mixin(EnchantmentScreen.class)
public abstract class HgnEnchantmentScreenMixin {
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		shift = At.Shift.AFTER), cancellable = true)
	private void hgn$finishCustomEnchanting(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) {
			((HgnContainerAccessor) this).hgn$renderTooltip(graphics, mouseX, mouseY);
			callback.cancel();
		}
	}

	private void hgn$clickCustomEnchantment(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled() || button != 0) return;
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		HgnContainerAccessor accessor = (HgnContainerAccessor) (Object) screen;
		AbstractContainerMenu menu = accessor.hgn$getMenu();
		Minecraft minecraft = Minecraft.getInstance();
		for (int index = 0; index < 3; index++) {
			HgnRect option = HgnContainerRenderer.enchantmentOption(screen, index);
			if (!option.contains(mouseX, mouseY)) continue;
			boolean accepted = minecraft.player != null && minecraft.gameMode != null
				&& menu.clickMenuButton(minecraft.player, index);
			if (accepted) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
			}
			callback.setReturnValue(accepted);
			return;
		}
		int left = accessor.hgn$getLeftPos();
		int top = accessor.hgn$getTopPos();
		for (int index = 0; index < 3; index++) {
			HgnRect vanillaOption = new HgnRect(left + 60, top + 14 + index * 19, 108, 19);
			if (vanillaOption.contains(mouseX, mouseY)) {
				callback.setReturnValue(false);
				return;
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void hgn$clickModernEnchantment(@org.spongepowered.asm.mixin.injection.Coerce Object event,
		boolean doubleClick, CallbackInfoReturnable<Boolean> callback) {
		hgn$clickCustomEnchantment(online.horizongate.hgn.compat.HgnInputCompat.x(event),
			online.horizongate.hgn.compat.HgnInputCompat.y(event),
			online.horizongate.hgn.compat.HgnInputCompat.button(event), callback);
	}
}
