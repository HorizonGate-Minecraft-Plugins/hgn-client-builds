import java.util.ArrayList;
import java.util.HashSet;
import online.horizongate.hgn.ui.container.HgnContainerLayout;

/** Checks actual scaled furnace slot geometry without launching the game. */
public final class HgnFurnaceLayoutTest {
	public static void main(String[] args) {
		var source = new ArrayList<HgnContainerLayout.SourceSlot>();
		for (int i = 0; i < 3; i++) {
			source.add(new HgnContainerLayout.SourceSlot(i, 0, 0, false, -1));
		}
		for (int i = 0; i < 36; i++) {
			source.add(new HgnContainerLayout.SourceSlot(i + 3, 0, 0, true, i));
		}
		int[][] viewports = {{320, 240}, {427, 240}, {640, 360}, {836, 471}, {1280, 720}, {1920, 1080}};
		for (int[] viewport : viewports) {
			var playerSource = new ArrayList<HgnContainerLayout.SourceSlot>();
			for (int id = 0; id < 46; id++) {
				int index = id < 5 ? -1 : id < 9 ? 44 - id : id < 36 ? id : id < 45 ? id - 36 : 40;
				playerSource.add(new HgnContainerLayout.SourceSlot(id, 0, 0, index >= 0, index));
			}
			var playerLayout = HgnContainerLayout.standard(HgnContainerLayout.Kind.PLAYER, 176, 166,
				viewport[0], viewport[1], playerSource);
			checkInventoryRows(playerLayout);
			checkNoOverlap(playerLayout);
			var preview = playerLayout.playerPreview();
			for (var slot : playerLayout.slots()) {
				if (slot.x() < preview.right() && slot.x() + 16 > preview.x()
					&& slot.y() < preview.bottom() && slot.y() + 16 > preview.y()) {
					throw new AssertionError("Player preview overlaps a native slot");
				}
			}
			var grindstone = HgnContainerLayout.standard(HgnContainerLayout.Kind.GRINDSTONE, 176, 166,
				viewport[0], viewport[1], source);
			checkInventoryRows(grindstone);
			checkNoOverlap(grindstone);
			var inputs = grindstone.slots().stream().filter(slot -> slot.role() == HgnContainerLayout.Role.INGREDIENT).toList();
			if (inputs.size() != 2 || inputs.get(0).x() != inputs.get(1).x()
				|| inputs.get(1).y() <= inputs.get(0).y()) throw new AssertionError("Grindstone inputs must be vertical");
			for (int chestRows : new int[] {3, 6}) {
				var chestSource = new ArrayList<HgnContainerLayout.SourceSlot>();
				for (int i = 0; i < chestRows * 9; i++) chestSource.add(new HgnContainerLayout.SourceSlot(i,
					i % 9 * 18, i / 9 * 18, false, -1));
				for (int i = 0; i < 36; i++) chestSource.add(new HgnContainerLayout.SourceSlot(chestRows * 9 + i,
					0, 0, true, i));
				var chest = HgnContainerLayout.standard(HgnContainerLayout.Kind.CHEST, 176, 114 + chestRows * 18,
					viewport[0], viewport[1], chestSource);
				checkInventoryRows(chest);
				checkNoOverlap(chest);
				var storage = chest.storagePreview();
				for (var slot : chest.slots()) {
					if (storage.width() > 0 && slot.x() < storage.right() && slot.x() + 16 > storage.x()
						&& slot.y() < storage.bottom() && slot.y() + 16 > storage.y()) {
						throw new AssertionError("Storage illustration overlaps native slots");
					}
					if (slot.role() == HgnContainerLayout.Role.CONTAINER
						&& (slot.y() < 24 || slot.y() + 16 > chest.playerInventory().y() - 14)) {
						throw new AssertionError("Chest grid overlaps a heading");
					}
				}
			}
			var layout = HgnContainerLayout.furnace(viewport[0], viewport[1], source);
			if (layout.imageWidth() > viewport[0] || layout.imageHeight() > viewport[1]) {
				throw new AssertionError("Furnace exceeds viewport");
			}
			if (layout.slots().size() != 39) throw new AssertionError("Lost native slot");
			var ids = new HashSet<Integer>();
			for (var slot : layout.slots()) {
				if (!ids.add(slot.slotId())) throw new AssertionError("Duplicate native slot");
				int size = layout.slotSize();
				if (slot.x() < 0 || slot.y() < 0 || slot.x() + size > layout.imageWidth()
					|| slot.y() + size > layout.imageHeight()) throw new AssertionError("Slot outside frame");
				for (var other : layout.slots()) {
					if (other.slotId() <= slot.slotId()) continue;
					if (slot.x() < other.x() + size && slot.x() + size > other.x()
						&& slot.y() < other.y() + size && slot.y() + size > other.y()) {
						throw new AssertionError("Overlapping native slots");
					}
				}
			}
			if (layout.slots().stream().filter(s -> s.role() == HgnContainerLayout.Role.HOTBAR).count() != 9) {
				throw new AssertionError("Hotbar topology changed");
			}
			checkInventoryRows(layout);
			var enchantSource = new ArrayList<HgnContainerLayout.SourceSlot>();
			for (int i = 0; i < 2; i++) {
				enchantSource.add(new HgnContainerLayout.SourceSlot(i, 0, 0, false, -1));
			}
			for (int i = 0; i < 36; i++) {
				enchantSource.add(new HgnContainerLayout.SourceSlot(i + 2, 0, 0, true, i));
			}
			var enchanting = HgnContainerLayout.enchanting(viewport[0], viewport[1], enchantSource);
			if (enchanting.slots().size() != 38) throw new AssertionError("Lost enchanting slot");
			checkInventoryRows(enchanting);
			var merchant = HgnContainerLayout.merchant(viewport[0], viewport[1], source);
			if (merchant.slots().size() != 39) throw new AssertionError("Lost merchant slot");
			checkInventoryRows(merchant);
			var pickerSource = new ArrayList<HgnContainerLayout.SourceSlot>();
			for (int i = 0; i < 54; i++) pickerSource.add(new HgnContainerLayout.SourceSlot(i, 0, 0, i >= 45, i - 45));
			var picker = HgnContainerLayout.creative(viewport[0], viewport[1], pickerSource);
			var search = online.horizongate.hgn.ui.container.HgnCreativeRenderer.searchBounds(picker);
			if (search.bottom() >= picker.slots().get(0).y() || search.right() > picker.imageWidth()) {
				throw new AssertionError("Creative search overlaps the picker or leaves its panel");
			}
			for (int tab = 0; tab < 14; tab++) {
				var bounds = online.horizongate.hgn.ui.container.HgnCreativeRenderer.tabBounds(picker, tab);
				if (bounds.bottom() > search.y() || bounds.right() > picker.imageWidth()
					|| !bounds.contains(bounds.centerX(), bounds.centerY())) {
					throw new AssertionError("Creative category target is outside its lane");
				}
			}
			if (picker.slots().stream().filter(s -> s.role() == HgnContainerLayout.Role.CONTAINER).count() != 45
				|| picker.slots().stream().filter(s -> s.role() == HgnContainerLayout.Role.HOTBAR).count() != 9) {
				throw new AssertionError("Creative picker topology changed");
			}
			var creativeInventory = new ArrayList<HgnContainerLayout.SourceSlot>();
			for (int i = 0; i < 47; i++) {
				int inventoryIndex = i >= 9 && i < 36 ? i : i >= 36 && i < 45 ? i - 36 : i == 45 ? 40 : -1;
				creativeInventory.add(new HgnContainerLayout.SourceSlot(i, 0, 0, inventoryIndex >= 0, inventoryIndex));
			}
			var inventoryTab = HgnContainerLayout.creative(viewport[0], viewport[1], creativeInventory);
			checkInventoryRows(inventoryTab);
			for (var slot : inventoryTab.slots()) {
				if (slot.slotId() >= 36 && slot.slotId() <= 44
					&& slot.role() != HgnContainerLayout.Role.HOTBAR) throw new AssertionError("Creative hotbar identity lost");
				if (slot.slotId() >= 5 && slot.slotId() <= 8
					&& slot.role() != HgnContainerLayout.Role.EQUIPMENT) throw new AssertionError("Creative armor identity lost");
			}
			checkNoOverlap(picker);
			checkNoOverlap(inventoryTab);
			checkNoOverlap(enchanting);
			checkNoOverlap(merchant);
			for (var slot : merchant.slots()) {
				if (slot.x() + merchant.slotSize() > merchant.imageWidth()
					|| slot.y() + merchant.slotSize() > merchant.imageHeight()) {
					throw new AssertionError("Merchant slot outside frame");
				}
			}
		}
		System.out.println("All four container designs: 6 viewports preserve native inventory and picker topology without overlap");
	}

	private static void checkNoOverlap(HgnContainerLayout layout) {
		for (var slot : layout.slots()) {
			int size = layout.slotSize();
			if (slot.x() < 0 || slot.y() < 0 || slot.x() + size > layout.imageWidth()
				|| slot.y() + size > layout.imageHeight()) throw new AssertionError("Slot outside frame");
			for (var other : layout.slots()) {
				if (other.slotId() <= slot.slotId()) continue;
				if (slot.x() < other.x() + size && slot.x() + size > other.x()
					&& slot.y() < other.y() + size && slot.y() + size > other.y()) {
					throw new AssertionError("Overlapping slots in " + layout.kind());
				}
			}
		}
	}

	private static void checkInventoryRows(HgnContainerLayout layout) {
		var rows = new java.util.HashMap<Integer, Integer>();
		var hotbarRows = new java.util.HashMap<Integer, Integer>();
		for (var slot : layout.slots()) {
			if (slot.role() == HgnContainerLayout.Role.PLAYER) rows.merge(slot.y(), 1, Integer::sum);
			if (slot.role() == HgnContainerLayout.Role.HOTBAR) hotbarRows.merge(slot.y(), 1, Integer::sum);
		}
		if (rows.size() != 3 || rows.values().stream().anyMatch(count -> count != 9)) {
			throw new AssertionError("Must retain three main inventory rows of nine slots");
		}
		if (hotbarRows.size() != 1 || hotbarRows.values().iterator().next() != 9) {
			throw new AssertionError("Must retain one hotbar row of nine slots");
		}
	}
}
