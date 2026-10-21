package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnCreateWorldScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact-class composition only: constructors, state, native input and resource callbacks are untouched. */
@Mixin(CreateWorldScreen.class)
public abstract class HgnCreateWorldMixin extends Screen implements HgnCreateWorldScreen.Host {
	@Shadow @Final private HeaderAndFooterLayout layout;
	@Shadow @Final private TabManager tabManager;
	@Shadow private TabNavigationBar tabNavigationBar;
	@Unique private HgnCreateWorldScreen hgn$composition;

	protected HgnCreateWorldMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean hgn$ownsCreation() {
		return ((Object) this).getClass() == CreateWorldScreen.class && HgnConfig.get().interfaceEnabled;
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void hgn$resetCreation(CallbackInfo callback) {
		if (hgn$composition != null) hgn$composition.dispose();
		hgn$composition = null;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void hgn$attachCreation(CallbackInfo callback) {
		if (!hgn$ownsCreation()) return;
		hgn$composition = HgnCreateWorldScreen.attach((CreateWorldScreen) (Object) this, this);
		((HgnCreateWorldScreen.TabHost) tabManager).hgn$bindCreation(hgn$composition);
		if (hgn$composition != null) hgn$composition.arrange();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void hgn$arrangeCreation(CallbackInfo callback) {
		if (hgn$composition == null) return;
		if (hgn$ownsCreation()) hgn$composition.arrange();
		else {
			hgn$composition.dispose();
			hgn$composition = null;
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hgn$renderCreation(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
		if (hgn$composition == null) return;
		if (!hgn$ownsCreation()) {
			hgn$composition.dispose();
			hgn$composition = null;
			return;
		}
		if (!hgn$composition.isComposedTab()) return;
		hgn$composition.renderChrome(graphics, mouseX, mouseY);
		for (var renderable : ((HgnEditWorldRenderAccessor) this).hgn$editWorldRenderables()) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
		callback.cancel();
	}

	@Override public TabManager hgn$creationTabs() { return tabManager; }
	@Override public TabNavigationBar hgn$creationNavigation() { return tabNavigationBar; }
	@Override public int hgn$creationFooterHeight() { return layout.getFooterHeight(); }
	@Override public void hgn$creationAdd(AbstractWidget widget) { addRenderableWidget(widget); }
	@Override public void hgn$creationRemove(AbstractWidget widget) { removeWidget(widget); }
	@Override public HgnCreateWorldScreen hgn$creationComposition() { return hgn$composition; }
}
