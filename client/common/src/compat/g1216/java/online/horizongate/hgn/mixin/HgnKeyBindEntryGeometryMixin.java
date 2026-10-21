package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.screen.HgnKeyBindsScreen;
import online.horizongate.hgn.ui.HgnKeyBindRow;
import online.horizongate.hgn.ui.HgnKeyBindsLabels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the dedicated page clips drawn names; native buttons, conflicts and narration are untouched. */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class HgnKeyBindEntryGeometryMixin implements HgnKeyBindRow {
	@Shadow @Final private Button changeButton;
	@Unique private HgnKeyBindsScreen hgn$keyBindsOwner;

	@Override
	public void hgn$keyBindsOwner(HgnKeyBindsScreen owner) {
		hgn$keyBindsOwner = owner;
	}

	@Override
	public HgnKeyBindsScreen hgn$keyBindsOwner() {
		return hgn$keyBindsOwner;
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void hgn$clipName(GuiGraphics graphics, Font font, Component name, int x, int y, int color) {
		if (hgn$keyBindsOwner == null) {
			graphics.drawString(font, name, x, y, color);
			return;
		}
		HgnKeyBindsLabels.draw(graphics, font, name, x, y, color, changeButton.getX());
	}
}
