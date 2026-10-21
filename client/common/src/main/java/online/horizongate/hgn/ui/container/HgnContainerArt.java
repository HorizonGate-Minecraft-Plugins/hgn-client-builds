package online.horizongate.hgn.ui.container;

import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** Raster-only container symbols generated for the HGN interface kit. */
public final class HgnContainerArt {
	private static final String DIRECTORY = "textures/gui/container/";
	private static final int TEXTURE_SIZE = 128;
	private static final String[] CREATIVE_TABS = {
		"creative_building",
		"creative_colored",
		"creative_natural",
		"creative_functional",
		"creative_redstone",
		"creative_saved_hotbar",
		"creative_search",
		"creative_tools",
		"creative_combat",
		"creative_food",
		"creative_ingredients",
		"creative_spawn_eggs",
		"creative_operator",
		"creative_inventory"
	};

	private HgnContainerArt() {
	}

	public static void module(GuiGraphics graphics, HgnContainerLayout.Kind kind,
		int x, int y, int size) {
		icon(graphics, switch (kind) {
			case PLAYER -> "module_player";
			case CREATIVE -> "module_creative";
			case CHEST -> "module_chest";
			case SHULKER -> "module_shulker";
			case CRAFTING -> "module_crafting";
			case CRAFTER -> "module_crafter";
			case FURNACE -> "module_furnace";
			case BREWING -> "module_brewing";
			case ANVIL -> "module_anvil";
			case SMITHING -> "module_smithing";
			case GRINDSTONE -> "module_grindstone";
			case STONECUTTER -> "module_stonecutter";
			case ENCHANTMENT -> "module_enchantment";
			case LOOM -> "module_loom";
			case CARTOGRAPHY -> "module_cartography";
			case HOPPER -> "module_hopper";
			case DISPENSER -> "module_dispenser";
			case BEACON -> "module_beacon";
			case MERCHANT -> "module_merchant";
			case MOUNT -> "module_mount";
			case GENERIC -> "module_generic";
		}, x, y, size);
	}

	public static void creativeTab(GuiGraphics graphics, int index, int x, int y, int size) {
		icon(graphics, index >= 0 && index < CREATIVE_TABS.length
			? CREATIVE_TABS[index] : "module_generic", x, y, size);
	}

	public static void icon(GuiGraphics graphics, String name, int x, int y, int size) {
		if (size <= 0) return;
		HgnDraw.textureStretch(graphics, DIRECTORY + name + ".png",
			x, y, size, size, TEXTURE_SIZE, TEXTURE_SIZE);
	}
}
