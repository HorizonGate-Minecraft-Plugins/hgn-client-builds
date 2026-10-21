package online.horizongate.hgn.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import online.horizongate.hgn.compat.HgnEquipmentCompat;
import online.horizongate.hgn.compat.HgnItemRenderCompat;

/** Bone-attached rendering for authenticated supplied-model loadouts. */
public final class HgnCosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;

	public HgnCosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light,
		PlayerRenderState state, float limbAngle, float limbDistance) {
		if (!HgnCosmeticRenderGuard.available() || state.isInvisible) return;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) return;
		Entity entity = level.getEntity(state.id);
		if (!(entity instanceof AbstractClientPlayer player)) return;
		if (!inRenderRange(minecraft, player)) return;
		try {
			for (HgnCosmetics.EquippedDefinition equipped :
				HgnCosmetics.renderPlanFor(player.getStringUUID())) {
				HgnCosmetics.ItemDefinition definition = equipped.definition();
				if (HgnCapeRenderer.isCape(definition)) {
					if (!isWearingElytra(state)) {
						HgnCapeRenderer.renderPlayer(pose, buffers, light, getParentModel(), state, definition);
					}
					continue;
				}
				Optional<ItemStack> stack = HgnCosmeticModels.stack(definition);
				if (stack.isPresent()) renderModel(pose, buffers, light, player,
					equipped.slot(), stack.get());
			}
		} catch (RuntimeException failure) {
			HgnCosmeticRenderGuard.failed();
		}
	}

	public static boolean hasRenderableCape(AbstractClientPlayer player) {
		if (!HgnCosmeticRenderGuard.available()) return false;
		Minecraft minecraft = Minecraft.getInstance();
		if (!inRenderRange(minecraft, player)) return false;
		try {
			for (HgnCosmetics.EquippedDefinition equipped :
				HgnCosmetics.renderPlanFor(player.getStringUUID())) {
				HgnCosmetics.ItemDefinition definition = equipped.definition();
				if (HgnCapeRenderer.isCape(definition) && HgnCapeRenderer.canRender(definition)) return true;
			}
		} catch (RuntimeException failure) {
			HgnCosmeticRenderGuard.failed();
		}
		return false;
	}

	public static boolean isWearingElytra(PlayerRenderState state) {
		return HgnEquipmentCompat.isWearingElytra(state);
	}

	private static boolean inRenderRange(Minecraft minecraft, AbstractClientPlayer player) {
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
				getParentModel().root().translateAndRotate(pose);
				getParentModel().head.translateAndRotate(pose);
				CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
			} else if (offhand) {
				getParentModel().translateToHand(HumanoidArm.LEFT, pose);
				pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
				pose.mulPose(Axis.YP.rotationDegrees(180.0f));
				pose.translate(-1.0f / 16.0f, 0.125f, -0.625f);
			} else {
				getParentModel().root().translateAndRotate(pose);
				getParentModel().body.translateAndRotate(pose);
				pose.translate(0.0f, 0.0f, 0.25f);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f));
				pose.scale(0.625f, -0.625f, -0.625f);
			}
			Minecraft minecraft = Minecraft.getInstance();
			ItemDisplayContext context = head ? ItemDisplayContext.HEAD
				: offhand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.FIXED;
			HgnItemRenderCompat.render(minecraft.getItemRenderer(), player, stack,
				context, offhand, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		} finally {
			pose.popPose();
		}
	}
}
