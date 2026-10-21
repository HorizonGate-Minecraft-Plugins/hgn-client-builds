package online.horizongate.hgn.compat;

import net.minecraft.world.entity.player.Inventory;

/** Development-only inventory for menu smoke checks before Minecraft 1.21.5. */
public final class HgnSmokeInventory {
	private HgnSmokeInventory() {
	}

	public static Inventory create() {
		return new Inventory(null);
	}
}
