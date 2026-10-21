package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.HgnConnectionArtwork;
import online.horizongate.hgn.ui.HgnConnectionLayout;
import online.horizongate.hgn.ui.HgnRect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Composes behind native status, narration and the original abort callback. */
@Mixin(ConnectScreen.class)
public abstract class HgnConnectionScreenMixin extends Screen {
	@Unique private Button hgn$cancel;
	@Unique private HgnRect hgn$originalCancel;

	protected HgnConnectionScreenMixin(Component title) { super(title); }

	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$arrangeConnection(CallbackInfo callback) {
		hgn$cancel = null;
		hgn$originalCancel = null;
		if (((Object) this).getClass() != ConnectScreen.class || children().size() != 1
			|| !(children().get(0) instanceof Button button)
			|| !button.getMessage().equals(CommonComponents.GUI_CANCEL)) return;
		hgn$cancel = button;
		hgn$originalCancel = new HgnRect(button.getX(), button.getY(), button.getWidth(), button.getHeight());
		hgn$applyConnectionBounds();
	}

	@Unique
	private boolean hgn$ownsConnection() {
		return hgn$cancel != null && children().size() == 1 && children().get(0) == hgn$cancel
			&& HgnConfig.get().interfaceEnabled && HgnConnectionLayout.supported(width, height);
	}

	@Unique
	private void hgn$applyConnectionBounds() {
		if (hgn$cancel == null) return;
		HgnRect bounds = hgn$ownsConnection() ? HgnConnectionLayout.resolve(width, height).cancel() : hgn$originalCancel;
		hgn$cancel.setX(bounds.x());
		hgn$cancel.setY(bounds.y());
		hgn$cancel.setWidth(bounds.width());
		hgn$cancel.setHeight(bounds.height());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		hgn$applyConnectionBounds();
		if (hgn$ownsConnection()) HgnConnectionArtwork.render(graphics, HgnConnectionLayout.resolve(width, height));
		else super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}
}
