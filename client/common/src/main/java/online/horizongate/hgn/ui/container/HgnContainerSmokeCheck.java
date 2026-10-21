package online.horizongate.hgn.ui.container;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import online.horizongate.hgn.HgnClient;

/** Development-only class transformation check; does not capture or inspect the UI. */
public final class HgnContainerSmokeCheck {
	private HgnContainerSmokeCheck() {
	}

	public static void run() {
		online.horizongate.hgn.ui.HgnResourceSmokeCheck.run();
		if (online.horizongate.hgn.HgnGameplayHud.visible(net.minecraft.client.Minecraft.getInstance())) {
			throw new AssertionError("Gameplay HUD must remain hidden outside a world");
		}
		online.horizongate.hgn.ui.HgnInputSmokeCheck.run();
		online.horizongate.hgn.ui.HgnMenuSmokeCheck.run();
		for (Class<?> type : new Class<?>[] {net.minecraft.client.gui.Gui.class,
			net.minecraft.client.renderer.entity.EntityRenderer.class,
			net.minecraft.client.gui.components.BossHealthOverlay.class,
			net.minecraft.client.KeyMapping.class,
			AbstractContainerScreen.class, FurnaceScreen.class,
			net.minecraft.client.gui.screens.inventory.HorseInventoryScreen.class,
			net.minecraft.client.gui.screens.inventory.BeaconScreen.class,
			net.minecraft.client.gui.screens.inventory.CrafterScreen.class,
			net.minecraft.client.gui.screens.inventory.CartographyTableScreen.class,
			EnchantmentScreen.class, MerchantScreen.class, CreativeModeInventoryScreen.class,
			net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class,
			net.minecraft.client.gui.screens.inventory.InventoryScreen.class,
			net.minecraft.client.gui.screens.inventory.CraftingScreen.class,
			net.minecraft.client.gui.screens.inventory.AnvilScreen.class,
			net.minecraft.client.gui.screens.inventory.StonecutterScreen.class,
			net.minecraft.client.gui.screens.inventory.LoomScreen.class}) {
			type.getDeclaredMethods();
			type.getDeclaredFields();
			for (Class<?> nested : type.getDeclaredClasses()) nested.getDeclaredMethods();
			HgnClient.LOGGER.info("HGN container smoke transformed {}", type.getSimpleName());
		}
		HgnClient.LOGGER.info("HGN_CONTAINER_SMOKE_OK");
	}
}
