package online.horizongate.hgn.ui.container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.*;
import online.horizongate.hgn.ui.HgnRect;

/** Complete screen-specific geometry for one vanilla-backed container screen. */
public record HgnContainerLayout(
	Kind kind,
	int imageWidth,
	int imageHeight,
	int titleX,
	int titleY,
	int inventoryTitleX,
	int inventoryTitleY,
	HgnRect header,
	HgnRect machine,
	HgnRect playerInventory,
	HgnRect recipeBook,
	List<SlotPosition> slots) {

	public enum Kind {
		PLAYER,
		CREATIVE,
		CHEST,
		SHULKER,
		CRAFTING,
		CRAFTER,
		FURNACE,
		BREWING,
		ANVIL,
		SMITHING,
		GRINDSTONE,
		STONECUTTER,
		ENCHANTMENT,
		LOOM,
		CARTOGRAPHY,
		HOPPER,
		DISPENSER,
		BEACON,
		MERCHANT,
		MOUNT,
		GENERIC;

		/**
		 * Screens whose non-slot controls are fully represented by the HGN
		 * renderer. Complex vanilla recipe/pattern/entity panels stay on their
		 * functional vanilla screen until their real controls have an HGN layout.
		 */
		public boolean replacementSafe() {
			return switch (this) {
				case CRAFTER, ANVIL, STONECUTTER, LOOM, CARTOGRAPHY, BEACON, MOUNT, GENERIC -> false;
				default -> true;
			};
		}
	}

	public enum Role {
		PLAYER,
		HOTBAR,
		EQUIPMENT,
		CONTAINER,
		CRAFTING,
		OUTPUT,
		FUEL,
		INGREDIENT,
		SPECIAL
	}

	public record SourceSlot(int slotId, int x, int y, boolean playerInventory, int inventoryIndex) {
	}

	public record SlotPosition(int slotId, int x, int y, Role role) {
	}

	public HgnContainerLayout {
		slots = List.copyOf(slots);
	}

	public HgnRect playerPreview() {
		if (kind != Kind.PLAYER) return new HgnRect(0, 0, 0, 0);
		int right = slots.stream().filter(slot -> slot.role() == Role.CRAFTING)
			.mapToInt(SlotPosition::x).min().orElse(machine.centerX()) - 6;
		return new HgnRect(32, machine.y() + 4, Math.max(1, right - 32), Math.max(20, machine.height() - 8));
	}

	public HgnRect storagePreview() {
		if ((kind != Kind.CHEST && kind != Kind.SHULKER) || machine.width() < 270) return new HgnRect(0, 0, 0, 0);
		return new HgnRect(machine.right() - 82, machine.y() + 4, 74, Math.max(1, machine.height() - 8));
	}

	public static HgnContainerLayout resolve(AbstractContainerScreen<?> screen, int originalWidth,
		int originalHeight, List<SourceSlot> source) {
		Kind kind = classify(screen);
		if (kind == Kind.FURNACE) return furnace(screen.width, screen.height, source);
		if (kind == Kind.ENCHANTMENT) return enchanting(screen.width, screen.height, source);
		if (kind == Kind.MERCHANT) return merchant(screen.width, screen.height, source);
		if (kind == Kind.CREATIVE) return creative(screen.width, screen.height, source);
		return standard(kind, originalWidth, originalHeight, screen.width, screen.height, source);
	}

	public static HgnContainerLayout standard(Kind kind, int originalWidth, int originalHeight,
		int screenWidth, int screenHeight, List<SourceSlot> source) {
		int width = preferredWidth(kind, originalWidth, screenWidth);
		int height = preferredHeight(kind, originalHeight, screenHeight);
		HgnRect header = new HgnRect(0, 0, width, 24);
		HgnRect player = kind == Kind.MERCHANT
			? new HgnRect(Math.max(7, width - 169), Math.max(76, height - 84), 162, 77)
			: kind == Kind.CREATIVE
			? new HgnRect(112, Math.max(25, height - 27), 162, 20)
			: new HgnRect(Math.max(7, (width - 162) / 2), Math.max(76, height - 84), 162, 77);
		HgnRect machine = kind == Kind.MERCHANT
			? new HgnRect(112, 27, Math.max(1, width - 119), Math.max(42, player.y() - 32))
			: kind == Kind.CREATIVE
			? new HgnRect(105, 27, Math.max(1, width - 112), Math.max(42, player.y() - 32))
			: new HgnRect(7, 27, Math.max(1, width - 14), Math.max(42, player.y() - 44));
		if ((kind == Kind.CHEST || kind == Kind.SHULKER) && machine.width() >= 270) {
			player = new HgnRect(19, player.y(), player.width(), player.height());
		}
		HgnRect recipe = new HgnRect(-156, 0, 150, height);
		ArrayList<SlotPosition> positions = new ArrayList<>(source.size());

		if (kind == Kind.CREATIVE) {
			// The vanilla picker owns exactly 45 real slots and pages them as a
			// nine-by-five grid. Keeping that topology preserves item ordering,
			// scrolling, dragging, and hotbar actions instead of merely rearranging
			// the same 45 entries into a misleading three-row display.
			int gridColumns = 9;
			int gridX = machine.x() + Math.max(0, (machine.width() - gridColumns * 18) / 2);
			int gridY = 55;
			for (int index = 0; index < source.size(); index++) {
				SourceSlot slot = source.get(index);
				if (index < 45) {
					positions.add(new SlotPosition(slot.slotId(), gridX + index % gridColumns * 18,
						gridY + index / gridColumns * 18, Role.CONTAINER));
				} else if (index < 54) {
					positions.add(new SlotPosition(slot.slotId(), player.x() + (index - 45) * 18,
						player.y(), Role.HOTBAR));
				} else {
					positions.add(new SlotPosition(slot.slotId(), -10_000, -10_000, Role.SPECIAL));
				}
			}
			return new HgnContainerLayout(kind, width, height, -10_000, -10_000, -10_000, -10_000,
				header, machine, player, recipe, positions);
		}

		ArrayList<SourceSlot> containerSlots = new ArrayList<>();
		for (SourceSlot slot : source) {
			if (slot.playerInventory() && slot.inventoryIndex() >= 0 && slot.inventoryIndex() <= 35) {
				positions.add(placePlayer(slot, player));
			} else if (kind == Kind.PLAYER && slot.playerInventory()) {
				positions.add(placeEquipment(slot, machine, width));
			} else {
				containerSlots.add(slot);
			}
		}

		placeContainer(kind, containerSlots, machine, positions);
		return new HgnContainerLayout(kind, width, height, -10_000, -10_000,
			-10_000, -10_000, header, machine, player, recipe, positions);
	}

	private static int preferredWidth(Kind kind, int original, int screenWidth) {
		int target = switch (kind) {
			case CREATIVE, MERCHANT -> 400;
			case FURNACE, ENCHANTMENT -> 340;
			case PLAYER -> 220;
			case CHEST, SHULKER -> 300;
			default -> Math.max(220, original);
		};
		return Math.max(original, Math.min(target, Math.max(original, screenWidth - 18)));
	}

	public int slotSize() {
		if (kind == Kind.CREATIVE) return Math.max(16, Math.round(imageWidth * 32.0f / 586));
		if (kind == Kind.MERCHANT) return Math.max(16, Math.round(imageWidth * 28.0f / 566));
		return kind == Kind.FURNACE || kind == Kind.ENCHANTMENT
			? Math.max(16, Math.round(imageWidth * 32.0f / 482)) : 16;
	}

	public static HgnContainerLayout creative(int screenWidth, int screenHeight, List<SourceSlot> source) {
		float scale = Math.max(0.5f, Math.min(screenWidth / 836.0f, screenHeight / 471.0f));
		int width = Math.round(586 * scale);
		int height = Math.round(374 * scale);
		ArrayList<SlotPosition> slots = new ArrayList<>(source.size());
		boolean inventory = source.size() != 54;
		for (int i = 0; i < source.size(); i++) {
			SourceSlot slot = source.get(i);
			int x;
			int y;
			Role role;
			if (!inventory) {
				x = 99 + i % 9 * 43;
				y = i < 45 ? 116 + i / 9 * 35 : 309;
				role = i < 45 ? Role.CONTAINER : Role.HOTBAR;
			} else if (slot.playerInventory() && slot.inventoryIndex() >= 0 && slot.inventoryIndex() < 36) {
				int index = slot.inventoryIndex();
				x = 169 + index % 9 * 43;
				y = index < 9 ? 309 : 180 + (index - 9) / 9 * 35;
				role = index < 9 ? Role.HOTBAR : Role.PLAYER;
			} else {
				int id = slot.slotId();
				if (id >= 5 && id <= 8) {
					x = 29; y = 132 + (id - 5) * 38;
				} else if (id >= 45) {
					x = 29 + (id - 45) * 50; y = 296;
				} else if (id == 0) {
					x = 298; y = 126;
				} else {
					x = 169 + (id - 1) % 2 * 38; y = 106 + (id - 1) / 2 * 38;
				}
				role = id == 0 ? Role.OUTPUT : id < 5 ? Role.CRAFTING : Role.EQUIPMENT;
			}
			slots.add(new SlotPosition(slot.slotId(), Math.round(x * scale), Math.round(y * scale), role));
		}
		return new HgnContainerLayout(Kind.CREATIVE, width, height, -10_000, -10_000, -10_000, -10_000,
			furnaceRect(10, 4, 566, 72, scale, scale), furnaceRect(inventory ? 155 : 85, 101, 421, 190, scale, scale),
			furnaceRect(inventory ? 155 : 85, 301, 421, 48, scale, scale), new HgnRect(0, 0, 0, 0), slots);
	}

	public static HgnContainerLayout merchant(int screenWidth, int screenHeight, List<SourceSlot> source) {
		float scale = Math.max(0.5f, Math.min(screenWidth / 836.0f, screenHeight / 471.0f));
		int width = Math.round(566 * scale);
		int height = Math.round(380 * scale);
		ArrayList<SlotPosition> slots = new ArrayList<>(source.size());
		for (SourceSlot slot : source) {
			int x;
			int y;
			Role role;
			if (slot.playerInventory() && slot.inventoryIndex() >= 0 && slot.inventoryIndex() < 36) {
				int index = slot.inventoryIndex();
				x = 202 + index % 9 * 38;
				y = 235 + (index < 9 ? 3 : (index - 9) / 9) * 34;
				role = index < 9 ? Role.HOTBAR : Role.PLAYER;
			} else {
				x = slot.slotId() == 2 ? 356 : slot.slotId() == 1 ? 267 : 230;
				y = 90;
				role = slot.slotId() == 2 ? Role.OUTPUT : Role.INGREDIENT;
			}
			slots.add(new SlotPosition(slot.slotId(), Math.round(x * scale), Math.round(y * scale), role));
		}
		return new HgnContainerLayout(Kind.MERCHANT, width, height, -10_000, -10_000, -10_000, -10_000,
			furnaceRect(10, 10, 546, 30, scale, scale), furnaceRect(195, 45, 361, 163, scale, scale),
			furnaceRect(195, 218, 361, 152, scale, scale), new HgnRect(0, 0, 0, 0), slots);
	}

	public static HgnContainerLayout enchanting(int screenWidth, int screenHeight, List<SourceSlot> source) {
		HgnContainerLayout base = furnace(screenWidth, screenHeight, source);
		float scale = base.imageWidth() / 482.0f;
		ArrayList<SlotPosition> slots = new ArrayList<>(source.size());
		for (SlotPosition slot : base.slots()) {
			if (slot.role() == Role.PLAYER || slot.role() == Role.HOTBAR) {
				slots.add(slot);
			} else {
				slots.add(new SlotPosition(slot.slotId(), Math.round((slot.slotId() == 0 ? 30 : 90) * scale),
					Math.round(166 * scale), Role.INGREDIENT));
			}
		}
		return new HgnContainerLayout(Kind.ENCHANTMENT, base.imageWidth(), base.imageHeight(),
			-10_000, -10_000, -10_000, -10_000,
			furnaceRect(11, 10, 460, 26, scale, scale), furnaceRect(20, 42, 442, 157, scale, scale),
			base.playerInventory(), base.recipeBook(), slots);
	}

	public static HgnContainerLayout furnace(int screenWidth, int screenHeight, List<SourceSlot> source) {
		float scale = Math.max(0.5f, Math.min(screenWidth / 836.0f, screenHeight / 471.0f));
		int width = Math.round(482 * scale);
		int height = Math.round(409 * scale);
		float sx = width / 482.0f;
		float sy = height / 409.0f;
		HgnRect header = furnaceRect(11, 10, 460, 67, sx, sy);
		HgnRect machine = furnaceRect(20, 78, 442, 118, sx, sy);
		HgnRect player = furnaceRect(20, 205, 442, 159, sx, sy);
		ArrayList<SlotPosition> slots = new ArrayList<>(source.size());
		for (SourceSlot slot : source) {
			int x;
			int y;
			Role role;
			if (slot.playerInventory() && slot.inventoryIndex() >= 0 && slot.inventoryIndex() < 36) {
				int index = slot.inventoryIndex();
				int row = index < 9 ? 3 : (index - 9) / 9;
				x = 27 + index % 9 * 48;
				y = 226 + row * 35;
				role = index < 9 ? Role.HOTBAR : Role.PLAYER;
			} else {
				x = slot.slotId() == 2 ? 277 : 100;
				y = slot.slotId() == 0 ? 99 : slot.slotId() == 1 ? 150 : 120;
				role = slot.slotId() == 2 ? Role.OUTPUT : slot.slotId() == 1 ? Role.FUEL : Role.INGREDIENT;
			}
			slots.add(new SlotPosition(slot.slotId(), Math.round(x * sx), Math.round(y * sy), role));
		}
		return new HgnContainerLayout(Kind.FURNACE, width, height, -10_000, -10_000,
			-10_000, -10_000, header, machine, player, new HgnRect(-156, 0, 150, height), slots);
	}

	private static HgnRect furnaceRect(int x, int y, int width, int height, float sx, float sy) {
		return new HgnRect(Math.round(x * sx), Math.round(y * sy), Math.round(width * sx), Math.round(height * sy));
	}

	private static int preferredHeight(Kind kind, int original, int screenHeight) {
		int target = switch (kind) {
			case CREATIVE -> 190;
			case FURNACE, ENCHANTMENT, MERCHANT -> 240;
			default -> Math.max(210, original + 32);
		};
		return Math.max(original, Math.min(target, Math.max(original, screenHeight - 4)));
	}

	private static SlotPosition placePlayer(SourceSlot slot, HgnRect panel) {
		int baseX = panel.x();
		int index = slot.inventoryIndex();
		if (index <= 8) {
			return new SlotPosition(slot.slotId(), baseX + index * 18, panel.y() + 58, Role.HOTBAR);
		}
		int normalized = index - 9;
		return new SlotPosition(slot.slotId(), baseX + normalized % 9 * 18,
			panel.y() + normalized / 9 * 18, Role.PLAYER);
	}

	private static SlotPosition placeEquipment(SourceSlot slot, HgnRect machine, int width) {
		int index = slot.inventoryIndex();
		if (index == 40) {
			return new SlotPosition(slot.slotId(), width - 26, machine.y() + 4, Role.EQUIPMENT);
		}
		int row = Math.max(0, Math.min(3, 39 - index));
		return new SlotPosition(slot.slotId(), 10, machine.y() + 4 + row * 18, Role.EQUIPMENT);
	}

	private static void placeContainer(Kind kind, List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		if (source.isEmpty()) return;
		switch (kind) {
			case PLAYER -> placeCrafting(source, panel, output, 2);
			case CRAFTING, CRAFTER -> placeCrafting(source, panel, output, 3);
			case FURNACE -> placeFurnace(source, panel, output);
			case BREWING -> placeBrewing(source, panel, output);
			case ANVIL, CARTOGRAPHY -> placeInputsAndOutput(source, panel, output, 2);
			case GRINDSTONE -> {
				for (int index = 0; index < Math.min(2, source.size()); index++) {
					output.add(new SlotPosition(source.get(index).slotId(), panel.x() + 12,
						panel.centerY() - 20 + index * 24, Role.INGREDIENT));
				}
				if (source.size() > 2) output.add(new SlotPosition(source.get(2).slotId(),
					panel.right() - 30, panel.centerY() - 8, Role.OUTPUT));
				placeRemainder(source, 3, panel, output);
			}
			case MERCHANT -> placeMerchant(source, panel, output);
			case SMITHING -> placeInputsAndOutput(source, panel, output, 3);
			case STONECUTTER -> placeInputsAndOutput(source, panel, output, 1);
			case ENCHANTMENT -> placeEnchantment(source, panel, output);
			case LOOM -> placeInputsAndOutput(source, panel, output, Math.max(1, source.size() - 1));
			case HOPPER -> placeGrid(source, panel, output, source.size(), Role.CONTAINER);
			case DISPENSER -> placeGrid(source, panel, output, 3, Role.CONTAINER);
			case BEACON -> output.add(new SlotPosition(source.get(0).slotId(), panel.centerX() - 8,
				panel.centerY() - 8, Role.SPECIAL));
			case CHEST, SHULKER -> placeGrid(source,
				panel.width() >= 270 ? new HgnRect(panel.x() + 12, panel.y(), 162, panel.height()) : panel,
				output, columns(source), Role.CONTAINER);
			case MOUNT, GENERIC -> placeNormalized(source, panel, output);
			default -> placeNormalized(source, panel, output);
		}
	}

	private static void placeCrafting(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output, int dimension) {
		SourceSlot result = source.get(0);
		output.add(new SlotPosition(result.slotId(), panel.right() - 28, panel.centerY() - 8, Role.OUTPUT));
		int gridCount = Math.min(dimension * dimension, source.size() - 1);
		int startX = panel.x() + (dimension == 2 ? panel.width() / 2 : 14);
		int startY = panel.y() + Math.max(2, (panel.height() - dimension * 18) / 2);
		for (int index = 0; index < gridCount; index++) {
			SourceSlot slot = source.get(index + 1);
			output.add(new SlotPosition(slot.slotId(), startX + index % dimension * 18,
				startY + index / dimension * 18, Role.CRAFTING));
		}
		for (int index = gridCount + 1; index < source.size(); index++) {
			SourceSlot slot = source.get(index);
			output.add(new SlotPosition(slot.slotId(), slot.x(), slot.y(), Role.SPECIAL));
		}
	}

	private static void placeFurnace(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		int y = panel.centerY() - 8;
		if (!source.isEmpty()) output.add(new SlotPosition(source.get(0).slotId(), panel.x() + 28, y - 16, Role.INGREDIENT));
		if (source.size() > 1) output.add(new SlotPosition(source.get(1).slotId(), panel.x() + 28, y + 16, Role.FUEL));
		if (source.size() > 2) output.add(new SlotPosition(source.get(2).slotId(), panel.right() - 78, y, Role.OUTPUT));
		placeRemainder(source, 3, panel, output);
	}

	private static void placeBrewing(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		int center = panel.centerX();
		for (int index = 0; index < Math.min(3, source.size()); index++) {
			output.add(new SlotPosition(source.get(index).slotId(), center - 27 + index * 27,
				panel.bottom() - 23, Role.CONTAINER));
		}
		if (source.size() > 3) output.add(new SlotPosition(source.get(3).slotId(), center - 8,
			panel.y() + 4, Role.INGREDIENT));
		if (source.size() > 4) output.add(new SlotPosition(source.get(4).slotId(), panel.x() + 8,
			panel.y() + 4, Role.FUEL));
		placeRemainder(source, 5, panel, output);
	}

	private static void placeInputsAndOutput(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output, int inputCount) {
		int actualInputs = Math.min(inputCount, Math.max(0, source.size() - 1));
		int startX = panel.x() + 12;
		int y = panel.centerY() - 8;
		for (int index = 0; index < actualInputs; index++) {
			output.add(new SlotPosition(source.get(index).slotId(), startX + index * 22, y, Role.INGREDIENT));
		}
		if (source.size() > actualInputs) {
			output.add(new SlotPosition(source.get(actualInputs).slotId(), panel.right() - 30, y, Role.OUTPUT));
		}
		placeRemainder(source, actualInputs + 1, panel, output);
	}

	private static void placeMerchant(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		int y = panel.centerY() - 8;
		if (!source.isEmpty()) {
			output.add(new SlotPosition(source.get(0).slotId(), panel.x() + 30, y, Role.INGREDIENT));
		}
		if (source.size() > 1) {
			output.add(new SlotPosition(source.get(1).slotId(), panel.x() + 52, y, Role.INGREDIENT));
		}
		if (source.size() > 2) {
			output.add(new SlotPosition(source.get(2).slotId(), panel.x() + 142, y, Role.OUTPUT));
		}
		placeRemainder(source, 3, panel, output);
	}

	private static void placeEnchantment(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		int y = panel.bottom() - 25;
		if (!source.isEmpty()) {
			output.add(new SlotPosition(source.get(0).slotId(), panel.x() + 14, y, Role.INGREDIENT));
		}
		if (source.size() > 1) {
			output.add(new SlotPosition(source.get(1).slotId(), panel.x() + 43, y, Role.INGREDIENT));
		}
		placeRemainder(source, 2, panel, output);
	}

	private static void placeGrid(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output, int columns, Role role) {
		int safeColumns = Math.max(1, columns);
		int rows = (source.size() + safeColumns - 1) / safeColumns;
		int startX = panel.centerX() - safeColumns * 18 / 2;
		int startY = panel.centerY() - rows * 18 / 2;
		for (int index = 0; index < source.size(); index++) {
			output.add(new SlotPosition(source.get(index).slotId(), startX + index % safeColumns * 18,
				startY + index / safeColumns * 18, role));
		}
	}

	private static void placeNormalized(List<SourceSlot> source, HgnRect panel,
		List<SlotPosition> output) {
		int minX = source.stream().mapToInt(SourceSlot::x).min().orElse(0);
		int minY = source.stream().mapToInt(SourceSlot::y).min().orElse(0);
		int maxX = source.stream().mapToInt(SourceSlot::x).max().orElse(minX);
		int maxY = source.stream().mapToInt(SourceSlot::y).max().orElse(minY);
		int offsetX = panel.centerX() - (maxX - minX + 16) / 2 - minX;
		int offsetY = panel.centerY() - (maxY - minY + 16) / 2 - minY;
		for (SourceSlot slot : source) {
			output.add(new SlotPosition(slot.slotId(), slot.x() + offsetX, slot.y() + offsetY,
				Role.CONTAINER));
		}
	}

	private static void placeRemainder(List<SourceSlot> source, int start,
		HgnRect panel, List<SlotPosition> output) {
		for (int index = start; index < source.size(); index++) {
			SourceSlot slot = source.get(index);
			output.add(new SlotPosition(slot.slotId(), panel.x() + (index - start) * 18,
				panel.bottom() - 18, Role.SPECIAL));
		}
	}

	private static int columns(List<SourceSlot> source) {
		HashSet<Integer> x = new HashSet<>();
		for (SourceSlot slot : source) x.add(slot.x());
		int distinct = x.size();
		return distinct > 0 && distinct <= 12 ? distinct : Math.min(9, source.size());
	}

	private static Kind classify(AbstractContainerScreen<?> screen) {
		if (screen instanceof InventoryScreen) return Kind.PLAYER;
		if (screen instanceof CreativeModeInventoryScreen) return Kind.CREATIVE;
		AbstractContainerMenu menu = screen.getMenu();
		if (menu instanceof ShulkerBoxMenu) return Kind.SHULKER;
		if (menu instanceof ChestMenu) return Kind.CHEST;
		if (menu instanceof CrafterMenu) return Kind.CRAFTER;
		if (menu instanceof CraftingMenu) return Kind.CRAFTING;
		if (menu instanceof AbstractFurnaceMenu) return Kind.FURNACE;
		if (menu instanceof BrewingStandMenu) return Kind.BREWING;
		if (menu instanceof AnvilMenu) return Kind.ANVIL;
		if (menu instanceof SmithingMenu) return Kind.SMITHING;
		if (menu instanceof GrindstoneMenu) return Kind.GRINDSTONE;
		if (menu instanceof StonecutterMenu) return Kind.STONECUTTER;
		if (menu instanceof EnchantmentMenu) return Kind.ENCHANTMENT;
		if (menu instanceof LoomMenu) return Kind.LOOM;
		if (menu instanceof CartographyTableMenu) return Kind.CARTOGRAPHY;
		if (menu instanceof HopperMenu) return Kind.HOPPER;
		if (menu instanceof DispenserMenu) return Kind.DISPENSER;
		if (menu instanceof BeaconMenu) return Kind.BEACON;
		if (menu instanceof MerchantMenu) return Kind.MERCHANT;
		if (menu instanceof HorseInventoryMenu) return Kind.MOUNT;
		return Kind.GENERIC;
	}
}
