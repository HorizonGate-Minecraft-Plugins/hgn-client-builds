package online.horizongate.hgn.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import online.horizongate.hgn.compat.HgnItemRenderCompat;

/** Bone-attached cosmetic rendering for the pre-render-state player API. */
public final class HgnCosmeticLayer extends
	RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;

	public HgnCosmeticLayer(RenderLayerParent<AbstractClientPlayer,
		PlayerModel<AbstractClientPlayer>> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light,
		AbstractClientPlayer player, float limbAngle, float limbDistance,
		float partialTick, float ageInTicks, float headYaw, float headPitch) {
		if (!HgnCosmeticRenderGuard.available() || player == null || player.isInvisible()
			|| !inRenderRange(player)) return;
		try {
			for (HgnCosmetics.EquippedDefinition equipped :
				HgnCosmetics.renderPlanFor(player.getStringUUID())) {
				HgnCosmetics.ItemDefinition definition = equipped.definition();
				if (HgnCapeRenderer.isCape(definition)) {
					if (!isWearingElytra(player)) {
						HgnCapeRenderer.renderPlayer(pose, buffers, light, getParentModel(),
							player, definition);
					}
					continue;
				}
				Optional<ItemStack> stack = HgnCosmeticModels.stack(definition);
				if (stack.isPresent()) {
					renderModel(pose, buffers, light, player, equipped.slot(), stack.get());
				}
			}
		} catch (RuntimeException failure) {
			HgnCosmeticRenderGuard.failed();
		}
	}

	/** Used while vanilla configures its cloak model part for this player. */
	public static boolean hasRenderableCape(AbstractClientPlayer player) {
		if (!HgnCosmeticRenderGuard.available() || !inRenderRange(player)) return false;
		try {
			for (HgnCosmetics.EquippedDefinition equipped :
				HgnCosmetics.renderPlanFor(player.getStringUUID())) {
				HgnCosmetics.ItemDefinition definition = equipped.definition();
				if (HgnCapeRenderer.isCape(definition) && HgnCapeRenderer.canRender(definition)) {
					return true;
				}
			}
		} catch (RuntimeException failure) {
			HgnCosmeticRenderGuard.failed();
		}
		return false;
	}

	public static boolean isWearingElytra(AbstractClientPlayer player) {
		return player != null && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
	}

	private static boolean inRenderRange(AbstractClientPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || player == null) return false;
		Entity camera = minecraft.getCameraEntity();
		return camera == null || camera == player
			|| camera.distanceToSqr(player) <= MAX_RENDER_DISTANCE_SQUARED;
	}

	private void renderModel(PoseStack pose, MultiBufferSource buffers, int light,
		AbstractClientPlayer player, String slot, ItemStack stack) {
		boolean head = slot.equalsIgnoreCase("HEAD") || slot.equalsIgnoreCase("HAT")
			|| slot.equalsIgnoreCase("HEAD_EFFECT");
		boolean offhand = slot.equalsIgnoreCase("OFFHAND");
		pose.pushPose();
		try {
			if (head) {
				getParentModel().head.translateAndRotate(pose);
				CustomHeadLayer.translateToHead(pose, false);
			} else if (offhand) {
				getParentModel().translateToHand(HumanoidArm.LEFT, pose);
				pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
				pose.mulPose(Axis.YP.rotationDegrees(180.0f));
				pose.translate(-1.0f / 16.0f, 0.125f, -0.625f);
			} else {
				getParentModel().body.translateAndRotate(pose);
				pose.translate(0.0f, 0.0f, 0.25f);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f));
				pose.scale(0.625f, -0.625f, -0.625f);
			}
			ItemDisplayContext context = head ? ItemDisplayContext.HEAD
				: offhand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.FIXED;
			HgnItemRenderCompat.render(Minecraft.getInstance().getItemRenderer(), player, stack,
				context, offhand, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		} finally {
			pose.popPose();
		}
	}
}
