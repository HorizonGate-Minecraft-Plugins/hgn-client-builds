package online.horizongate.hgn.ui.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.compat.HgnEnchantCompat;
import online.horizongate.hgn.mixin.HgnContainerAccessor;
import online.horizongate.hgn.mixin.HgnMerchantAccessor;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnTypography;

/** Draws the clean-room container artwork while Mojang's menu remains authoritative. */
public final class HgnContainerRenderer {
	private static Object savedHotbarsOwner;
	private static List<List<ItemStack>> savedHotbars = List.of();

	private HgnContainerRenderer() {
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics,
		int mouseX, int mouseY) {
		HgnSurface.veil(graphics, screen.width, screen.height);
		HgnContainerLayout layout = HgnContainers.layout(screen);
		if (layout == null) return;
		HgnContainerAccessor accessor = (HgnContainerAccessor) (Object) screen;
		int left = accessor.hgn$getLeftPos();
		int top = accessor.hgn$getTopPos();
		if (layout.kind() == HgnContainerLayout.Kind.CREATIVE) {
			HgnCreativeRenderer.render(screen, graphics, layout, left, top);
			return;
		}
		if (layout.kind() == HgnContainerLayout.Kind.MERCHANT) {
			HgnMerchantRenderer.render(screen, graphics, layout, (MerchantMenu) accessor.hgn$getMenu(), left, top);
			return;
		}
		if (layout.kind() == HgnContainerLayout.Kind.ENCHANTMENT) {
			HgnEnchantmentRenderer.render(graphics, layout, (EnchantmentMenu) accessor.hgn$getMenu(),
				left, top, mouseX, mouseY);
			return;
		}
		if (layout.kind() == HgnContainerLayout.Kind.FURNACE) {
			HgnFurnaceRenderer.render(graphics, layout, (AbstractFurnaceMenu) accessor.hgn$getMenu(), left, top);
			return;
		}
		int frameX = left;
		int frameY = layout.kind() == HgnContainerLayout.Kind.CREATIVE ? top - 30 : top;
		int frameWidth = layout.imageWidth();
		int frameHeight = layout.kind() == HgnContainerLayout.Kind.CREATIVE
			? layout.imageHeight() + 58 : layout.imageHeight();
		HgnSurface.panel(graphics, frameX, frameY, frameWidth, frameHeight, true);
		HgnSurface.glass(graphics, left + layout.header().x(), top + layout.header().y(),
			layout.header().width(), layout.header().height(), true);
		HgnSurface.glass(graphics, left + layout.machine().x(), top + layout.machine().y(),
			layout.machine().width(), layout.machine().height(), false);
		HgnSurface.glass(graphics, left + layout.playerInventory().x() - 4,
			top + layout.playerInventory().y() - 14, layout.playerInventory().width() + 8,
			layout.playerInventory().height() + 14, false);
		if (layout.kind() == HgnContainerLayout.Kind.MERCHANT) {
			HgnSurface.glass(graphics, left + 7, top + 27, 98,
				Math.max(32, layout.imageHeight() - 34), false);
		}

		drawModuleLabel(graphics, screen, layout, left, top);
		drawContextPreview(graphics, layout, left, top);
		drawMachineControls(graphics, accessor.hgn$getMenu(), layout, left, top);
		drawSlots(graphics, accessor, layout, left, top);
		if (layout.kind() == HgnContainerLayout.Kind.CREATIVE) {
			drawCreativeSavedHotbars(graphics, left, top, layout);
			drawCreativeControls(screen, graphics, left, top, layout);
		}
		if (layout.kind() == HgnContainerLayout.Kind.MERCHANT) {
			drawMerchantOffers(screen, graphics, left, top, accessor.hgn$getMenu());
		}
		drawFooter(graphics, frameX, frameY, frameWidth, frameHeight);
	}

	public static HgnRect enchantmentOption(AbstractContainerScreen<?> screen, int index) {
		HgnContainerLayout layout = HgnContainers.layout(screen);
		if (layout == null || layout.kind() != HgnContainerLayout.Kind.ENCHANTMENT
			|| index < 0 || index >= 3) return new HgnRect(0, 0, 0, 0);
		HgnContainerAccessor accessor = (HgnContainerAccessor) (Object) screen;
		return HgnEnchantmentRenderer.option(layout, accessor.hgn$getLeftPos(), accessor.hgn$getTopPos(), index);
	}

	public static boolean renderMerchantTooltip(AbstractContainerScreen<?> screen,
		GuiGraphics graphics, int mouseX, int mouseY) {
		return HgnMerchantRenderer.tooltip(screen, graphics, mouseX, mouseY);
	}

	private static boolean itemAt(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + 14 && mouseY >= y && mouseY < y + 14;
	}

	private static void drawModuleLabel(GuiGraphics graphics, AbstractContainerScreen<?> screen,
		HgnContainerLayout layout, int left, int top) {
		String label = switch (layout.kind()) {
			case PLAYER -> "SURVIVAL INVENTORY";
			case CREATIVE -> "HGN CREATIVE";
			case CHEST -> screen.getTitle().getString();
			case SHULKER -> "SHULKER BOX";
			case CRAFTING, CRAFTER -> "CRAFTING TABLE";
			case FURNACE -> "HGN FURNACE";
			case BREWING -> "BREWING STAND";
			case ANVIL -> "ANVIL";
			case SMITHING -> "SMITHING TABLE";
			case GRINDSTONE -> "GRINDSTONE";
			case STONECUTTER -> "STONECUTTER";
			case ENCHANTMENT -> "ENCHANTING TABLE";
			case LOOM -> "LOOM";
			case CARTOGRAPHY -> "CARTOGRAPHY TABLE";
			case HOPPER -> "HOPPER";
			case DISPENSER -> screen.getTitle().getString();
			case BEACON -> "BEACON CONTROL";
			case MERCHANT -> "VILLAGER MERCHANT";
			case MOUNT -> "MOUNT LOADOUT";
			case GENERIC -> "SERVER CONTAINER";
		};
		HgnContainerArt.module(graphics, layout.kind(), left + 6, top + 2, 20);
		int textWidth = Math.max(1, layout.imageWidth() - 36);
		online.horizongate.hgn.ui.dashboard.HgnTypography.fitted(graphics, Minecraft.getInstance().font,
			label, left + 29, top + 5, textWidth, 0.64f, HgnPalette.CYAN);
		String description = switch (layout.kind()) {
			case CREATIVE -> "BUILD WITHOUT LIMITS";
			case FURNACE -> "SMELT ORES INTO REFINED MATERIALS";
			case ENCHANTMENT -> "CHOOSE AN ENCHANTMENT";
			case MERCHANT -> "SERVER-AUTHORITATIVE TRADES";
			case PLAYER -> "GEAR UP. EXPLORE. BELONG.";
			case CHEST, SHULKER -> "STORE WHAT MATTERS.";
			case CRAFTING, CRAFTER -> "TURN IDEAS INTO ADVENTURES.";
			case GRINDSTONE -> "REPAIR. DISENCHANT. RENEW.";
			case SMITHING -> "UPGRADE. CUSTOMIZE.";
			case CARTOGRAPHY -> "CHART YOUR OWN ADVENTURES.";
			case HOPPER -> "MOVE MORE. DO MORE.";
			default -> "HORIZONGATE NETWORK";
		};
		online.horizongate.hgn.ui.dashboard.HgnTypography.fitted(graphics, Minecraft.getInstance().font,
			description, left + 29, top + 15, textWidth, 0.42f, HgnPalette.mutedText());
		if (layout.kind() != HgnContainerLayout.Kind.CREATIVE) {
			HgnContainerArt.icon(graphics, "icon_inventory_grid",
				left + layout.playerInventory().x(), top + layout.playerInventory().y() - 11, 9);
			online.horizongate.hgn.ui.dashboard.HgnTypography.fitted(graphics, Minecraft.getInstance().font, "INVENTORY",
				left + layout.playerInventory().x() + 11, top + layout.playerInventory().y() - 10,
				layout.playerInventory().width() - 11, 0.52f, HgnPalette.TEXT);
		}
	}

	private static void drawSlots(GuiGraphics graphics, HgnContainerAccessor accessor,
		HgnContainerLayout layout, int left, int top) {
		Slot hovered = accessor.hgn$getHoveredSlot();
		AbstractContainerMenu menu = accessor.hgn$getMenu();
		for (HgnContainerLayout.SlotPosition position : layout.slots()) {
			if (position.slotId() < 0 || position.slotId() >= menu.slots.size()) continue;
			Slot slot = menu.slots.get(position.slotId());
			if (!slot.isActive()) continue;
			HgnSurface.inset(graphics, left + position.x() - 1,
				top + position.y() - 1, 18, 18, slot == hovered);
		}
	}

	private static void drawContextPreview(GuiGraphics graphics, HgnContainerLayout layout, int left, int top) {
		var machine = layout.machine();
		var storage = absolute(layout.storagePreview(), left, top);
		if (storage.width() > 0) {
			HgnSurface.inset(graphics, storage.x(), storage.y(), storage.width(), storage.height(), false);
			int size = Math.min(storage.width() - 8, storage.height() - 8);
			HgnContainerArt.module(graphics, layout.kind(), storage.centerX() - size / 2,
				storage.centerY() - size / 2, size);
		}
		if (layout.kind() == HgnContainerLayout.Kind.PLAYER && Minecraft.getInstance().player != null) {
			var preview = absolute(layout.playerPreview(), left, top);
			HgnSurface.inset(graphics, preview.x(), preview.y(), preview.width(), preview.height(), false);
			online.horizongate.hgn.compat.HgnEntityPreview.render(graphics,
				preview.x(), preview.y(), preview.right(), preview.bottom(),
				Math.max(8, Math.min(preview.width() / 2, preview.height() / 2)), Minecraft.getInstance().player);
		}
		if (layout.kind() == HgnContainerLayout.Kind.GRINDSTONE) {
			HgnContainerArt.module(graphics, layout.kind(), left + machine.centerX() - 45,
				top + machine.centerY() - 19, 36);
		}
		if (layout.kind() == HgnContainerLayout.Kind.SMITHING) {
			String[] labels = {"TEMPLATE", "BASE", "ADDITION", "RESULT"};
			for (var slot : layout.slots()) {
				if (slot.slotId() < 0 || slot.slotId() >= labels.length) continue;
				online.horizongate.hgn.ui.dashboard.HgnTypography.fitted(graphics, Minecraft.getInstance().font,
					labels[slot.slotId()], left + slot.x() - 2, top + slot.y() - 10, 21, 0.38f, HgnPalette.CYAN);
			}
		}
	}

	private static void drawMachineControls(GuiGraphics graphics, AbstractContainerMenu menu,
		HgnContainerLayout layout, int left, int top) {
		HgnRect machine = absolute(layout.machine(), left, top);
		switch (layout.kind()) {
			case FURNACE -> {
				AbstractFurnaceMenu furnace = (AbstractFurnaceMenu) menu;
				float process = normalized(furnace.getBurnProgress(), 1.0f);
				float burn = normalized(furnace.getLitProgress(), 1.0f);
				HgnSurface.meter(graphics, machine.right() - 31, machine.y() + 18,
					8, Math.max(24, machine.height() - 36), burn, HgnPalette.CYAN);
				drawArrow(graphics, machine.centerX() - 26, machine.centerY() - 4, 46, process);
				HgnTypography.text(graphics, Minecraft.getInstance().font, "INPUT",
					machine.x() + 23, machine.y() + 7, HgnPalette.mutedText());
				HgnContainerArt.icon(graphics, "icon_fuel",
					machine.x() + 10, machine.centerY() + 1, 12);
				HgnTypography.text(graphics, Minecraft.getInstance().font, "FUEL",
					machine.x() + 23, machine.centerY() + 4, HgnPalette.mutedText());
				HgnContainerArt.icon(graphics, "icon_output",
					machine.right() - 108, machine.y() + 4, 12);
				HgnTypography.text(graphics, Minecraft.getInstance().font, "OUTPUT",
					machine.right() - 94, machine.y() + 7, HgnPalette.mutedText());
				HgnTypography.text(graphics, Minecraft.getInstance().font, "BURN",
					machine.right() - 40, machine.y() + 7, HgnPalette.mutedText());
			}
			case BREWING -> {
				float brew = normalized(((BrewingStandMenu) menu).getBrewingTicks(), 400);
				HgnSurface.meter(graphics, machine.centerX() - 2, machine.y() + 23,
					5, Math.max(8, machine.height() - 47), 1.0f - brew, HgnPalette.CYAN);
			}
			case CRAFTING, CRAFTER, ANVIL, SMITHING, GRINDSTONE, STONECUTTER,
				LOOM, CARTOGRAPHY -> drawArrow(graphics,
					machine.centerX() - 10, machine.centerY() - 4, 28, 1.0f);
			case MERCHANT -> {
				HgnTypography.text(graphics, Minecraft.getInstance().font, "YOU PAY",
					machine.x() + 26, machine.y() + 8, HgnPalette.mutedText());
				HgnTypography.text(graphics, Minecraft.getInstance().font, "YOU GET",
					machine.x() + 136, machine.y() + 8, HgnPalette.mutedText());
				drawArrow(graphics, machine.x() + 88, machine.centerY() - 4, 38, 1.0f);
				HgnContainerArt.module(graphics, HgnContainerLayout.Kind.MERCHANT,
					machine.right() - 72, machine.y() + 8, 64);
			}
			case ENCHANTMENT -> {
				EnchantmentMenu enchanting = (EnchantmentMenu) menu;
				int[] costs = enchanting.costs;
				int[] clues = enchanting.enchantClue;
				int[] levels = enchanting.levelClue;
				HgnContainerArt.module(graphics, HgnContainerLayout.Kind.ENCHANTMENT,
					machine.x() + 10, machine.y() + 6, 42);
				HgnTypography.text(graphics, Minecraft.getInstance().font, "ITEM",
					machine.x() + 11, machine.bottom() - 32, HgnPalette.mutedText());
				HgnContainerArt.icon(graphics, "icon_lapis",
					machine.x() + 34, machine.bottom() - 36, 11);
				HgnTypography.text(graphics, Minecraft.getInstance().font, "LAPIS",
					machine.x() + 46, machine.bottom() - 32, HgnPalette.mutedText());
				for (int line = 0; line < 3; line++) {
					int optionX = machine.x() + 68;
					int optionY = machine.y() + 7 + line * 28;
					int optionWidth = Math.max(60, machine.width() - 77);
					int cost = line < costs.length ? costs[line] : 0;
					HgnSurface.inset(graphics, optionX, optionY, optionWidth, 24, cost > 0);
					HgnTypography.text(graphics, Minecraft.getInstance().font,
						Integer.toString(line + 1), optionX + 7, optionY + 8,
						cost > 0 ? HgnPalette.CYAN : HgnPalette.mutedText());
					String enchantment = enchantmentName(menu,
						line < clues.length ? clues[line] : -1,
						line < levels.length ? levels[line] : 0);
					String detail = cost > 0
						? enchantment + "  /  " + cost + " XP  /  " + (line + 1) + " LAPIS"
						: "UNAVAILABLE";
					detail = Minecraft.getInstance().font.plainSubstrByWidth(detail,
						Math.max(8, optionWidth - 24));
					HgnTypography.text(graphics, Minecraft.getInstance().font,
						detail,
						optionX + 20, optionY + 8,
						cost > 0 ? HgnPalette.CYAN_SOFT : HgnPalette.mutedText());
				}
			}
			default -> {
			}
		}
	}

	private static void drawArrow(GuiGraphics graphics, int x, int y, int width, float progress) {
		int safeWidth = Math.max(12, width);
		int arrowSize = 18;
		int trackWidth = Math.max(1, safeWidth - arrowSize + 4);
		HgnSurface.meter(graphics, x, y + 4, trackWidth, 7, progress, HgnPalette.CYAN);
		HgnContainerArt.icon(graphics, "icon_process_arrow",
			x + safeWidth - arrowSize, y - 2, arrowSize);
	}

	private static void drawCreativeControls(AbstractContainerScreen<?> screen, GuiGraphics graphics,
		int left, int top, HgnContainerLayout layout) {
		HgnCreativeState creative = (HgnCreativeState) screen;
		CreativeModeTab selected = creative.hgn$selectedTab();
		int tabIndex = 0;
		for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
			int x = left + tab.column() * 28;
			int y = tab.row() == CreativeModeTab.Row.TOP ? top - 28 : top + layout.imageHeight() - 4;
			HgnSurface.control(graphics, x, y, 27, 28, tab == selected ? 1.0f : 0.0f,
				tab == selected, false, false, false);
			HgnContainerArt.creativeTab(graphics, tabIndex, x + 5, y + 5, 18);
			tabIndex++;
		}
		float scroll = creative.hgn$scrollOffset();
		int trackX = left + layout.imageWidth() - 15;
		int trackY = top + 18;
		int trackHeight = 112;
		HgnSurface.inset(graphics, trackX, trackY, 7, trackHeight, false);
		int thumbY = trackY + Math.round(Math.max(0.0f, Math.min(1.0f, scroll)) * (trackHeight - 15));
		HgnDraw.rect(graphics, trackX + 2, thumbY + 2, trackX + 5, thumbY + 13, HgnPalette.CYAN);
	}

	private static void drawCreativeSavedHotbars(GuiGraphics graphics, int left, int top,
		HgnContainerLayout layout) {
		int railX = left + 7;
		int railY = top + 27;
		int railWidth = 92;
		int railHeight = Math.max(42, layout.playerInventory().y() - 34);
		HgnSurface.glass(graphics, railX, railY, railWidth, railHeight, false);
		HgnContainerArt.icon(graphics, "creative_saved_hotbar",
			railX + 6, railY + 4, 12);
		HgnTypography.text(graphics, Minecraft.getInstance().font, "SAVED HOTBARS",
			railX + 19, railY + 7, HgnPalette.mutedText());
		Minecraft minecraft = Minecraft.getInstance();
		List<List<ItemStack>> hotbars = savedHotbars(minecraft);
		for (int row = 0; row < 5; row++) {
			int y = railY + 20 + row * 20;
			if (y + 18 >= railY + railHeight) break;
			HgnSurface.inset(graphics, railX + 5, y, railWidth - 10, 18, false);
			HgnTypography.text(graphics, Minecraft.getInstance().font, "0" + (row + 1),
				railX + 9, y + 6, HgnPalette.CYAN_SOFT);
			List<ItemStack> saved = row < hotbars.size() ? hotbars.get(row) : List.of();
			for (int column = 0; column < Math.min(5, saved.size()); column++) {
				ItemStack stack = saved.get(column);
				if (!stack.isEmpty()) {
					HgnDraw.item(graphics, stack,
						railX + 27 + column * 12, y + 3, 0.68f, row * 7 + column);
				}
			}
		}
	}

	static List<List<ItemStack>> savedHotbars(Minecraft minecraft) {
		if (minecraft.level == null) return List.of();
		// Decoding all saved ItemStacks can be expensive with custom components.
		// A creative screen needs one immutable snapshot, not a disk/registry decode
		// every second. Reopening the screen naturally refreshes the snapshot.
		Object owner = minecraft.screen;
		if (savedHotbarsOwner == owner && savedHotbars.size() == 9) return savedHotbars;
		ArrayList<List<ItemStack>> loaded = new ArrayList<>(9);
		for (int row = 0; row < 9; row++) {
			loaded.add(List.copyOf(minecraft.getHotbarManager().get(row)
				.load(minecraft.level.registryAccess())));
		}
		savedHotbars = List.copyOf(loaded);
		savedHotbarsOwner = owner;
		return savedHotbars;
	}

	private static void drawMerchantOffers(AbstractContainerScreen<?> screen, GuiGraphics graphics,
		int left, int top, AbstractContainerMenu rawMenu) {
		if (!(rawMenu instanceof MerchantMenu menu)) return;
		MerchantOffers offers = menu.getOffers();
		HgnMerchantAccessor merchant = (HgnMerchantAccessor) screen;
		int scroll = merchant.hgn$getScrollOffset();
		int selected = merchant.hgn$getSelectedOffer();
		int visible = Math.min(7, Math.max(0, offers.size() - scroll));
		for (int row = 0; row < visible; row++) {
			MerchantOffer offer = offers.get(scroll + row);
			int x = left + 7;
			int y = top + 28 + row * 22;
			HgnSurface.glass(graphics, x, y, 96, 21, selected == scroll + row);
			HgnDraw.item(graphics, offer.getCostA(), x + 4, y + 3, 0.82f, row);
			ItemStack second = offer.getCostB();
			if (!second.isEmpty()) HgnDraw.item(graphics, second, x + 27, y + 3, 0.82f, row + 17);
			HgnDraw.item(graphics, offer.getResult(), x + 74, y + 3, 0.82f, row + 31);
			HgnContainerArt.icon(graphics, "icon_process_arrow", x + 53, y + 4, 13);
			if (offer.isOutOfStock()) {
				HgnTypography.text(graphics, Minecraft.getInstance().font, "X",
					x + 54, y + 7, HgnPalette.DANGER);
			}
		}
		if (offers.size() > 7) {
			int trackY = top + 18;
			int trackHeight = 139;
			HgnSurface.inset(graphics, left + 94, trackY, 6, trackHeight, false);
			float progress = scroll / (float) Math.max(1, offers.size() - 7);
			int thumbY = trackY + Math.round(progress * (trackHeight - 27));
			HgnSurface.focus(graphics, left + 94, thumbY, 6, 27);
		}
	}

	private static String enchantmentName(AbstractContainerMenu rawMenu, int clue, int level) {
		if (!(rawMenu instanceof EnchantmentMenu) || clue < 0 || level <= 0) {
			return "ENCHANTMENT";
		}
		return HgnEnchantCompat.name(clue, level);
	}

	private static float normalized(int value, int maximum) {
		if (maximum <= 0) return 0.0f;
		return Math.max(0.0f, Math.min(1.0f, value / (float) maximum));
	}

	private static float normalized(float value, float maximum) {
		if (maximum <= 0.0f) return 0.0f;
		return Math.max(0.0f, Math.min(1.0f, value / maximum));
	}

	private static void drawFooter(GuiGraphics graphics, int x, int top, int width, int height) {
		int y = top + height - 5;
		int center = x + width / 2;
		HgnSurface.separator(graphics, x + 10, y,
			Math.max(1, center - x - 23), true);
		HgnSurface.separator(graphics, center + 13, y,
			Math.max(1, x + width - center - 23), true);
		HgnTypography.centered(graphics, Minecraft.getInstance().font, "HGN", center,
			y - 3, HgnPalette.CYAN);
	}

	private static HgnRect absolute(HgnRect relative, int left, int top) {
		return new HgnRect(left + relative.x(), top + relative.y(), relative.width(), relative.height());
	}

}
