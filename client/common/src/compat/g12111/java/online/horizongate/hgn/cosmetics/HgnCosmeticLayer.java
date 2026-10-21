package online.horizongate.hgn.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import online.horizongate.hgn.compat.HgnEquipmentCompat;

/** Bone-attached rendering for authenticated supplied-model loadouts. */
public final class HgnCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;

	public HgnCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
		AvatarRenderState state, float limbAngle, float limbDistance) {
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
						HgnCapeRenderer.submitPlayer(pose, collector, light,
							getParentModel(), state, definition);
					}
					continue;
				}
				renderModel(pose, collector, light, state, player,
					equipped.slot(), definition);
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

	public static boolean isWearingElytra(AvatarRenderState state) {
		return HgnEquipmentCompat.isWearingElytra(state);
	}

	private static boolean inRenderRange(Minecraft minecraft, AbstractClientPlayer player) {
		if (minecraft == null || player == null) return false;
		Entity camera = minecraft.getCameraEntity();
		return camera == null || camera == player
			|| camera.distanceToSqr(player) <= MAX_RENDER_DISTANCE_SQUARED;
	}

	private void renderModel(PoseStack pose, SubmitNodeCollector collector, int light,
		AvatarRenderState state, AbstractClientPlayer player, String slot,
		HgnCosmetics.ItemDefinition definition) {
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
				getParentModel().translateToHand(state, HumanoidArm.LEFT, pose);
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
			ItemDisplayContext context = head ? ItemDisplayContext.HEAD
				: offhand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.FIXED;
			HgnCosmeticModels.submit(pose, collector, light, state.outlineColor,
				definition, player, context);
		} finally {
			pose.popPose();
		}
	}
}
