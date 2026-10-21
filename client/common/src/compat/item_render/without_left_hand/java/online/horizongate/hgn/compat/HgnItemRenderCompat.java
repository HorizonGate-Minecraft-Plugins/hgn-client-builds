package online.horizongate.hgn.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21.5+ item-render call bridge. */
public final class HgnItemRenderCompat {
	private HgnItemRenderCompat() {
	}

	public static void render(ItemRenderer renderer, AbstractClientPlayer player,
		ItemStack stack, ItemDisplayContext context, boolean leftHand,
		PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		renderer.renderStatic(player, stack, context, pose, buffers,
			player.level(), light, overlay, player.getId());
	}
}
