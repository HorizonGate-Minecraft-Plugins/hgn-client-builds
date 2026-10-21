package online.horizongate.hgn.compat;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.Items;

/** Minecraft 1.21.9-1.21.10 avatar equipment field bridge. */
public final class HgnEquipmentCompat {
	private HgnEquipmentCompat() {
	}

	public static boolean isWearingElytra(AvatarRenderState state) {
		return state != null && state.chestEquipment != null
			&& state.chestEquipment.is(Items.ELYTRA);
	}
}
