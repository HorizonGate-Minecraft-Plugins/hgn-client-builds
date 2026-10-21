package online.horizongate.hgn.ui.container;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import online.horizongate.hgn.mixin.HgnContainerAccessor;
import online.horizongate.hgn.mixin.HgnSlotAccessor;

/** Installs and retains descriptors without rebuilding them during steady-state rendering. */
public final class HgnContainers {
	private static final WeakHashMap<AbstractContainerScreen<?>, State> STATES = new WeakHashMap<>();

	private HgnContainers() {
	}

	public static void refresh(AbstractContainerScreen<?> screen) {
		STATES.remove(screen);
		install(screen);
	}

	public static void install(AbstractContainerScreen<?> screen) {
		if (!online.horizongate.hgn.HgnConfig.get().containerLayoutsEnabled()) return;
		HgnContainerAccessor accessor = (HgnContainerAccessor) (Object) screen;
		AbstractContainerMenu menu = accessor.hgn$getMenu();
		State previous = STATES.get(screen);
		boolean newSlots = previous == null || previous.menu != menu
			|| previous.slotCount != menu.slots.size();
		int signature = newSlots ? signature(menu) : previous.signature;
		boolean newSize = previous == null || previous.screenWidth != screen.width
			|| previous.screenHeight != screen.height;
		if (!newSlots && !newSize) {
			if (previous.layout.kind().replacementSafe()) {
				positionWidgets(screen, previous.layout, accessor.hgn$getLeftPos(), accessor.hgn$getTopPos());
			}
			int dynamicSignature = dynamicSignature(menu);
			if (previous.layout.kind().replacementSafe()
				&& previous.dynamicSignature != dynamicSignature) {
				positionWidgets(screen, previous.layout,
					accessor.hgn$getLeftPos(), accessor.hgn$getTopPos());
				STATES.put(screen, new State(previous.signature, previous.screenWidth,
					previous.screenHeight, previous.originalWidth, previous.originalHeight,
					previous.source, previous.layout, menu, menu.slots.size(), dynamicSignature));
			}
			return;
		}

		List<HgnContainerLayout.SourceSlot> source = newSlots
			? capture(menu)
			: previous.source;
		int originalWidth = previous == null ? accessor.hgn$getImageWidth() : previous.originalWidth;
		int originalHeight = previous == null ? accessor.hgn$getImageHeight() : previous.originalHeight;
		HgnContainerLayout layout = HgnContainerLayout.resolve(screen, originalWidth, originalHeight, source);
		if (!layout.kind().replacementSafe()) {
			STATES.put(screen, new State(signature, screen.width, screen.height,
				originalWidth, originalHeight, source, layout, menu, menu.slots.size(),
				dynamicSignature(menu)));
			return;
		}

		int oldLeft = accessor.hgn$getLeftPos();
		int oldTop = accessor.hgn$getTopPos();
		int currentCenteredX = (screen.width - accessor.hgn$getImageWidth()) / 2;
		int horizontalOffset = oldLeft - currentCenteredX;
		int currentCenteredY = (screen.height - accessor.hgn$getImageHeight()) / 2;
		int verticalOffset = oldTop - currentCenteredY;
		int left = (screen.width - layout.imageWidth()) / 2 + horizontalOffset;
		int top = (screen.height - layout.imageHeight()) / 2 + verticalOffset;

		accessor.hgn$setImageWidth(layout.imageWidth());
		accessor.hgn$setImageHeight(layout.imageHeight());
		accessor.hgn$setLeftPos(left);
		accessor.hgn$setTopPos(top);
		accessor.hgn$setTitleLabelX(layout.titleX());
		accessor.hgn$setTitleLabelY(layout.titleY());
		accessor.hgn$setInventoryLabelX(layout.inventoryTitleX());
		accessor.hgn$setInventoryLabelY(layout.inventoryTitleY());
		shiftWidgets(screen, left - oldLeft, top - oldTop);
		positionWidgets(screen, layout, left, top);

		for (HgnContainerLayout.SlotPosition position : layout.slots()) {
			if (position.slotId() < 0 || position.slotId() >= menu.slots.size()) continue;
			HgnSlotAccessor slot = (HgnSlotAccessor) (Object) menu.slots.get(position.slotId());
			slot.hgn$setX(position.x());
			slot.hgn$setY(position.y());
		}
		STATES.put(screen, new State(signature, screen.width, screen.height,
			originalWidth, originalHeight, source, layout, menu, menu.slots.size(),
			dynamicSignature(menu)));
	}

	private static void positionWidgets(AbstractContainerScreen<?> screen,
		HgnContainerLayout layout, int left, int top) {
		if (layout.kind() == HgnContainerLayout.Kind.CREATIVE) {
			var search = ((HgnCreativeState) screen).hgn$searchBox();
			if (search != null) {
				var bounds = HgnCreativeRenderer.searchBounds(layout);
				search.setX(left + bounds.x());
				search.setY(top + bounds.y());
				search.setWidth(bounds.width());
				search.setHeight(bounds.height());
			}
		} else if (layout.kind() == HgnContainerLayout.Kind.MERCHANT) {
			int offers = accessor(screen).hgn$getMenu() instanceof net.minecraft.world.inventory.MerchantMenu menu
				? menu.getOffers().size() : 0;
			for (var child : screen.children()) {
				if (child instanceof AbstractWidget widget && child instanceof HgnTradeButtonState trade) {
					int row = trade.hgn$offerRow();
					var rect = HgnMerchantRenderer.offer(layout, left, top, row);
					widget.setX(rect.x());
					widget.setY(rect.y());
					widget.setWidth(rect.width());
					widget.setHeight(rect.height());
					widget.visible = row < offers;
				}
			}
		} else {
			// RecipeBookScreen leaves this control at its vanilla coordinates even
			// after the slots move. Keep it available without covering an input.
			for (var child : screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.ImageButton widget) {
					widget.setX(left + layout.imageWidth() - 24);
					widget.setY(top + 4);
				}
			}
		}
	}

	private static void shiftWidgets(AbstractContainerScreen<?> screen, int offsetX, int offsetY) {
		if (offsetX == 0 && offsetY == 0) return;
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget widget) {
				widget.setX(widget.getX() + offsetX);
				widget.setY(widget.getY() + offsetY);
			}
		}
	}



	private static HgnContainerAccessor accessor(AbstractContainerScreen<?> screen) {
		return (HgnContainerAccessor) (Object) screen;
	}

	public static HgnContainerLayout layout(AbstractContainerScreen<?> screen) {
		State state = STATES.get(screen);
		if (state == null) {
			install(screen);
			state = STATES.get(screen);
		}
		return state == null ? null : state.layout;
	}

	private static List<HgnContainerLayout.SourceSlot> capture(AbstractContainerMenu menu) {
		ArrayList<HgnContainerLayout.SourceSlot> source = new ArrayList<>(menu.slots.size());
		for (int slotId = 0; slotId < menu.slots.size(); slotId++) {
			Slot slot = menu.slots.get(slotId);
			HgnSlotAccessor accessor = (HgnSlotAccessor) (Object) slot;
			boolean player = slot.container instanceof Inventory;
			int inventoryIndex = player ? slot.getContainerSlot() : -1;
			if (menu instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu
				&& menu.slots.size() != 54) {
				player = slotId >= 5 && slotId <= 45;
				inventoryIndex = slotId >= 36 && slotId <= 44 ? slotId - 36
					: slotId == 45 ? 40 : slotId >= 9 && slotId <= 35 ? slotId
					: slotId >= 5 && slotId <= 8 ? 44 - slotId : -1;
			}
			source.add(new HgnContainerLayout.SourceSlot(slotId,
				accessor.hgn$getX(), accessor.hgn$getY(), player, inventoryIndex));
		}
		return List.copyOf(source);
	}

	private static int signature(AbstractContainerMenu menu) {
		int value = menu.slots.size();
		for (Slot slot : menu.slots) {
			value = 31 * value + System.identityHashCode(slot);
		}
		return value;
	}

	private static int dynamicSignature(AbstractContainerMenu menu) {
		return menu instanceof net.minecraft.world.inventory.MerchantMenu merchant
			? merchant.getOffers().size() : 0;
	}

	private record State(
		int signature,
		int screenWidth,
		int screenHeight,
		int originalWidth,
		int originalHeight,
		List<HgnContainerLayout.SourceSlot> source,
		HgnContainerLayout layout,
		AbstractContainerMenu menu,
		int slotCount,
		int dynamicSignature) {
	}
}
