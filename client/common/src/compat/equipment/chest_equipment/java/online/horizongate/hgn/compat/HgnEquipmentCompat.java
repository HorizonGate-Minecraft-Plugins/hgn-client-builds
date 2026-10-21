package online.horizongate.hgn.compat;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.item.Items;

/** Minecraft 1.21.4-1.21.5 equipment field bridge. */
public final class HgnEquipmentCompat {
	private HgnEquipmentCompat() {
	}

	public static boolean isWearingElytra(PlayerRenderState state) {
		return state != null && state.chestEquipment != null
			&& state.chestEquipment.is(Items.ELYTRA);
	}
}
