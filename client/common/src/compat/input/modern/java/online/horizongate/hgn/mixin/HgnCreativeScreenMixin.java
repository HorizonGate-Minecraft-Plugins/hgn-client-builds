package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.ui.container.HgnContainerLayout;
import online.horizongate.hgn.ui.container.HgnContainers;
import online.horizongate.hgn.ui.container.HgnCreativeState;
import online.horizongate.hgn.ui.container.HgnCreativeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the real creative tabs clickable while replacing only Mojang's tab artwork. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class HgnCreativeScreenMixin implements HgnCreativeState {
	@Shadow private static CreativeModeTab selectedTab;
	@Shadow private float scrollOffs;
	@Shadow private boolean scrolling;
	@Shadow private net.minecraft.client.gui.components.EditBox searchBox;

	@Override
	public net.minecraft.client.gui.components.EditBox hgn$searchBox() {
		return searchBox;
	}

	@Override
	public CreativeModeTab hgn$selectedTab() {
		return selectedTab;
	}

	@Override
	public float hgn$scrollOffset() {
		return scrollOffs;
	}

	@Inject(method = "selectTab", at = @At("TAIL"))
	private void hgn$refreshTabLayout(CreativeModeTab tab, CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) {
			HgnContainers.refresh((CreativeModeInventoryScreen) (Object) this);
		}
	}

	@Inject(method = "checkTabClicked", at = @At("HEAD"), cancellable = true)
	private void hgn$clickReferenceTab(CreativeModeTab tab, double x, double y,
		CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		var layout = HgnContainers.layout((CreativeModeInventoryScreen) (Object) this);
		callback.setReturnValue(HgnCreativeRenderer.tab(layout, tab).contains(x, y));
	}

	@Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
	private void hgn$hoverReferenceTab(GuiGraphics graphics, CreativeModeTab tab, int mouseX, int mouseY,
		CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		var layout = HgnContainers.layout((CreativeModeInventoryScreen) (Object) this);
		var accessor = (HgnContainerAccessor) this;
		callback.setReturnValue(HgnCreativeRenderer.tab(layout, tab).contains(
			mouseX - accessor.hgn$getLeftPos(), mouseY - accessor.hgn$getTopPos()));
	}

	private void hgn$dragReferenceScrollbar(double mouseX, double mouseY, int button,
		double deltaX, double deltaY, CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled() || !scrolling) return;
		var screen = (CreativeModeInventoryScreen) (Object) this;
		var layout = HgnContainers.layout(screen);
		var rect = HgnCreativeRenderer.scrollbar(layout);
		int thumb = Math.max(8, Math.round(15 * layout.imageWidth() / 586.0f));
		scrollOffs = Math.max(0, Math.min(1, (float) ((mouseY - ((HgnContainerAccessor) this).hgn$getTopPos()
			- rect.y() - thumb / 2.0) / Math.max(1, rect.height() - thumb))));
		screen.getMenu().scrollTo(scrollOffs);
		callback.setReturnValue(true);
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void hgn$dragModernScrollbar(@org.spongepowered.asm.mixin.injection.Coerce Object event,
		double deltaX, double deltaY, CallbackInfoReturnable<Boolean> callback) {
		hgn$dragReferenceScrollbar(online.horizongate.hgn.compat.HgnInputCompat.x(event),
			online.horizongate.hgn.compat.HgnInputCompat.y(event),
			online.horizongate.hgn.compat.HgnInputCompat.button(event), deltaX, deltaY, callback);
	}

	@Inject(method = "renderTabButton", at = @At("HEAD"), cancellable = true)
	private void hgn$useClientTabs(CallbackInfo callback) {
		if (HgnConfig.get().containerLayoutsEnabled()) callback.cancel();
	}

	@Inject(method = "insideScrollbar", at = @At("HEAD"), cancellable = true)
	private void hgn$useClientScrollbar(double mouseX, double mouseY,
		CallbackInfoReturnable<Boolean> callback) {
		if (!HgnConfig.get().containerLayoutsEnabled()) return;
		CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
		HgnContainerLayout layout = HgnContainers.layout(screen);
		if (layout == null) return;
		HgnContainerAccessor accessor = (HgnContainerAccessor) (Object) screen;
		var track = HgnCreativeRenderer.scrollbar(layout);
		callback.setReturnValue(track.contains(mouseX - accessor.hgn$getLeftPos(), mouseY - accessor.hgn$getTopPos()));
	}
}
