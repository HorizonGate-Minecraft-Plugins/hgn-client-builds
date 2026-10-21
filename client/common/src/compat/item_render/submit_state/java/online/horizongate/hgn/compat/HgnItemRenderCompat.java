package online.horizongate.hgn.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21.9+ queued item-render bridge. */
public final class HgnItemRenderCompat {
	private HgnItemRenderCompat() {
	}

	public static ItemStackRenderState resolve(AbstractClientPlayer player,
		ItemStack stack, ItemDisplayContext context) {
		ItemStackRenderState state = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForLiving(
			state, stack, context, player);
		return state;
	}

	public static void submit(ItemStackRenderState state, PoseStack pose,
		SubmitNodeCollector collector, int light, int overlay, int outlineColor) {
		state.submit(pose, collector, light, overlay, outlineColor);
	}
}
