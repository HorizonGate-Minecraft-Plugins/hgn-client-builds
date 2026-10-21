package online.horizongate.hgn.ui.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.mixin.HgnContainerAccessor;
import online.horizongate.hgn.mixin.HgnMerchantAccessor;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;

/** Reference offer rail with native prices, stock and selection callbacks. */
public final class HgnMerchantRenderer {
	private static final java.util.Map<AbstractContainerScreen<?>, net.minecraft.world.entity.LivingEntity> PREVIEWS = new java.util.WeakHashMap<>();
	private HgnMerchantRenderer() {
	}

	public static HgnRect offer(HgnContainerLayout layout, int left, int top, int row) {
		float scale = layout.imageWidth() / 566.0f;
		return new HgnRect(left + Math.round(14 * scale), top + Math.round((65 + row * 40) * scale),
			Math.round(158 * scale), Math.round(36 * scale));
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics,
		HgnContainerLayout layout, MerchantMenu menu, int left, int top) {
		var font = Minecraft.getInstance().font;
		var state = (HgnMerchantAccessor) screen;
		if (!PREVIEWS.containsKey(screen)) {
			var hit = Minecraft.getInstance().hitResult;
			PREVIEWS.put(screen, hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
				&& entityHit.getEntity() instanceof net.minecraft.world.item.trading.Merchant
				&& entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity merchant ? merchant : null);
		}
		float scale = layout.imageWidth() / 566.0f;
		HgnDraw.beginScaleAt(graphics, scale, left, top);
		try {
			HgnSurface.panel(graphics, left, top, 566, 380, false);
			HgnTypography.text(graphics, font, "VILLAGER MERCHANT", left + 40, top + 20, HgnPalette.TEXT);
			HgnContainerArt.module(graphics, HgnContainerLayout.Kind.MERCHANT, left + 12, top + 12, 24);
			HgnTypography.text(graphics, font, HgnTypography.trim(font, screen.getTitle().getString(), 320),
				left + 210, top + 20, HgnPalette.mutedText());
			HgnSurface.glass(graphics, left + 8, top + 45, 174, 305, false);
			HgnTypography.text(graphics, font, "TRADES", left + 18, top + 51, HgnPalette.TEXT);
			int scroll = state.hgn$getScrollOffset();
			for (int row = 0; row < Math.min(7, menu.getOffers().size() - scroll); row++) {
				var offer = menu.getOffers().get(scroll + row);
				int y = top + 65 + row * 40;
				HgnSurface.glass(graphics, left + 14, y, 158, 36, state.hgn$getSelectedOffer() == scroll + row);
				HgnDraw.item(graphics, offer.getCostA(), left + 21, y + 8, 1.25f, row);
				if (!offer.getCostB().isEmpty()) HgnDraw.item(graphics, offer.getCostB(), left + 54, y + 8, 1.25f, row + 7);
				HgnDraw.item(graphics, offer.getResult(), left + 137, y + 8, 1.25f, row + 14);
				HgnContainerArt.icon(graphics, "icon_process_arrow", left + 99, y + 9, 18);
				if (offer.isOutOfStock()) HgnTypography.text(graphics, font, "X", left + 103, y + 15, HgnPalette.DANGER);
			}
			HgnTypography.text(graphics, font, "Scroll to browse offers", left + 18, top + 359, HgnPalette.mutedText());
			HgnSurface.glass(graphics, left + 195, top + 45, 361, 163, false);
			HgnTypography.text(graphics, font, "YOU PAY", left + 230, top + 71, HgnPalette.TEXT);
			HgnTypography.text(graphics, font, "YOU GET", left + 350, top + 71, HgnPalette.TEXT);
			HgnContainerArt.icon(graphics, "icon_process_arrow", left + 311, top + 92, 24);
			HgnSurface.glass(graphics, left + 427, top + 52, 120, 146, false);
			HgnContainerArt.module(graphics, HgnContainerLayout.Kind.MERCHANT, left + 438, top + 66, 98);
			HgnTypography.centered(graphics, font, "MERCHANT", left + 487, top + 178, HgnPalette.mutedText());
			HgnTypography.text(graphics, font, "Take the output to trade", left + 218, top + 153, HgnPalette.TEXT);
			HgnTypography.text(graphics, font, "Shift-click for repeated trades", left + 218, top + 174, HgnPalette.mutedText());
			HgnSurface.glass(graphics, left + 195, top + 218, 361, 152, false);
			HgnTypography.text(graphics, font, "INVENTORY", left + 202, top + 222, HgnPalette.TEXT);
		} finally {
			HgnDraw.endScale(graphics);
		}
		for (var slot : layout.slots()) HgnSurface.inset(graphics, left + slot.x() - 1, top + slot.y() - 1,
			layout.slotSize() + 2, layout.slotSize() + 2, false);
		var entity = PREVIEWS.get(screen);
		if (entity != null && entity.isAlive()) {
			online.horizongate.hgn.compat.HgnEntityPreview.render(graphics,
				left + Math.round(438 * scale), top + Math.round(58 * scale),
				left + Math.round(536 * scale), top + Math.round(169 * scale),
				Math.max(16, Math.round(48 * scale)), entity);
		}
	}

	public static boolean tooltip(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
		var accessor = (HgnContainerAccessor) screen;
		var menu = (MerchantMenu) screen.getMenu();
		var layout = HgnContainers.layout(screen);
		float scale = layout.imageWidth() / 566.0f;
		int scroll = ((HgnMerchantAccessor) screen).hgn$getScrollOffset();
		for (int row = 0; row < Math.min(7, menu.getOffers().size() - scroll); row++) {
			var rect = offer(layout, accessor.hgn$getLeftPos(), accessor.hgn$getTopPos(), row);
			if (!rect.contains(mouseX, mouseY)) continue;
			float x = (mouseX - rect.x()) / scale;
			var offer = menu.getOffers().get(scroll + row);
			ItemStack stack = x < 35 ? offer.getCostA() : x < 72 ? offer.getCostB()
				: x >= 120 ? offer.getResult() : ItemStack.EMPTY;
			if (stack.isEmpty()) return false;
			HgnDraw.itemTooltip(graphics, Minecraft.getInstance().font, stack, mouseX, mouseY);
			return true;
		}
		return false;
	}
}
