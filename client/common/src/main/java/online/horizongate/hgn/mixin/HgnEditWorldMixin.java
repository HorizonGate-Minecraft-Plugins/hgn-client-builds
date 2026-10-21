package online.horizongate.hgn.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnEditWorldScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rearranges only the exact native editor; all native storage, input and confirmation methods remain intact. */
@Mixin(EditWorldScreen.class)
public abstract class HgnEditWorldMixin extends Screen implements HgnEditWorldScreen.Host {
	@Shadow @Final private LinearLayout layout;
	@Shadow @Final private EditBox nameEdit;
	@Unique private HgnEditWorldScreen hgn$composition;

	protected HgnEditWorldMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean hgn$ownsEditWorld() {
		return ((Object) this).getClass() == EditWorldScreen.class && HgnConfig.get().interfaceEnabled;
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void hgn$resetComposition(CallbackInfo callback) {
		if (hgn$composition != null) hgn$composition.dispose();
		hgn$composition = null;
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void hgn$arrangeEditWorld(CallbackInfo callback) {
		if (!hgn$ownsEditWorld()) {
			if (hgn$composition != null) hgn$composition.dispose();
			hgn$composition = null;
			return;
		}
		if (hgn$composition == null) hgn$composition = new HgnEditWorldScreen((EditWorldScreen) (Object) this, this);
		hgn$composition.arrange();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hgn$renderEditWorld(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
		if (!hgn$ownsEditWorld() || hgn$composition == null) return;
		hgn$composition.renderChrome(graphics, mouseX, mouseY);
		for (var renderable : ((HgnEditWorldRenderAccessor) this).hgn$editWorldRenderables()) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
		callback.cancel();
	}

	@Override
	public List<AbstractWidget> hgn$editWorldWidgets() {
		List<AbstractWidget> widgets = new ArrayList<>();
		layout.visitWidgets(widgets::add);
		return List.copyOf(widgets);
	}

	@Override
	public EditBox hgn$editWorldName() {
		return nameEdit;
	}

	@Override
	public void hgn$editWorldAdd(AbstractWidget widget) {
		addRenderableWidget(widget);
	}

	@Override
	public void hgn$editWorldRemove(AbstractWidget widget) {
		removeWidget(widget);
	}

	@Override
	public HgnEditWorldScreen hgn$editWorldComposition() {
		return hgn$composition;
	}
}
