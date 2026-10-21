package online.horizongate.hgn.compat;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.cosmetics.HgnCapeRenderer;
import online.horizongate.hgn.cosmetics.HgnCosmeticModels;
import online.horizongate.hgn.cosmetics.HgnCosmetics;

/** Draggable authenticated-skin preview with the selected supplied cosmetic attached. */
final class HgnCosmeticPreviewWidget extends AbstractWidget {
	private static final int FULL_BRIGHT = 0xF000F0;
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;
	private final Supplier<PlayerSkin> skin;
	private final float sizeMultiplier;
	private final boolean equippedOnly;
	private float rotationX = -5.0f;
	private float rotationY;

	HgnCosmeticPreviewWidget(int width, int height, EntityModelSet models,
		Supplier<PlayerSkin> skin) {
		this(width, height, models, skin, 30.0f, 1.0f, false);
	}

	HgnCosmeticPreviewWidget(int width, int height, EntityModelSet models,
		Supplier<PlayerSkin> skin, float initialYaw, float sizeMultiplier, boolean equippedOnly) {
		super(0, 0, width, height, CommonComponents.EMPTY);
		wideModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
		slimModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.skin = skin;
		this.rotationY = initialYaw;
		this.sizeMultiplier = sizeMultiplier;
		this.equippedOnly = equippedOnly;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(getX() + getWidth() / 2.0f, getY() + getHeight(), 100.0f);
		float scale = Math.min(getHeight() / 2.125f, getWidth() / 3.25f)
			* sizeMultiplier;
		pose.scale(scale, scale, scale);
		pose.translate(0.0f, -0.0625f, 0.0f);
		pose.rotateAround(Axis.XP.rotationDegrees(rotationX), 0.0f, -1.0625f, 0.0f);
		pose.mulPose(Axis.YP.rotationDegrees(rotationY));
		graphics.flush();
		Lighting.setupForEntityInInventory(Axis.XP.rotationDegrees(rotationX));
		renderPlayer(graphics, skin.get());
		graphics.flush();
		Lighting.setupFor3DItems();
		pose.popPose();
	}

	private void renderPlayer(GuiGraphics graphics, PlayerSkin playerSkin) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.scale(1.0f, 1.0f, -1.0f);
		pose.translate(0.0f, -1.501f, 0.0f);
		PlayerModel model = playerSkin.model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
		model.setAllVisible(true);
		RenderType renderType = model.renderType(playerSkin.texture());
		graphics.drawSpecial(buffers -> {
			model.renderToBuffer(pose, buffers.getBuffer(renderType),
				FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			renderCosmeticLoadout(pose, buffers, model);
		});
		pose.popPose();
	}

	private void renderCosmeticLoadout(PoseStack pose, MultiBufferSource buffers,
		PlayerModel model) {
		HgnConfig config = HgnConfig.get();
		float animationAge = config.reducedMotion || !config.interfaceAnimations
			? 0.0f : (System.currentTimeMillis() % 60_000L) / 50.0f;
		for (HgnCosmetics.EquippedDefinition equipped : equippedOnly
			? HgnCosmetics.equippedPreviewRenderPlan() : HgnCosmetics.previewRenderPlan()) {
			renderCosmetic(pose, buffers, model, equipped.definition(), animationAge);
		}
	}

	private void renderCosmetic(PoseStack pose, MultiBufferSource buffers,
		PlayerModel model, HgnCosmetics.ItemDefinition definition, float animationAge) {
		if (HgnCapeRenderer.isCape(definition)) {
			HgnCapeRenderer.renderPreview(pose, buffers, FULL_BRIGHT, model, definition,
				animationAge);
			return;
		}
		Optional<ItemStack> stack = HgnCosmeticModels.stack(definition);
		if (stack.isEmpty()) return;

		String slot = definition.equipmentSlot();
		boolean head = slot.equalsIgnoreCase("HEAD") || slot.equalsIgnoreCase("HAT")
			|| slot.equalsIgnoreCase("HEAD_EFFECT");
		boolean offhand = slot.equalsIgnoreCase("OFFHAND");
		pose.pushPose();
		if (head) {
			model.root().translateAndRotate(pose);
			model.head.translateAndRotate(pose);
			CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
		} else if (offhand) {
			model.translateToHand(HumanoidArm.LEFT, pose);
			pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
			pose.mulPose(Axis.YP.rotationDegrees(180.0f));
			pose.translate(-1.0f / 16.0f, 0.125f, -0.625f);
		} else {
			model.root().translateAndRotate(pose);
			model.body.translateAndRotate(pose);
			pose.translate(0.0f, 0.0f, 0.25f);
			pose.mulPose(Axis.YP.rotationDegrees(180.0f));
			pose.scale(0.625f, -0.625f, -0.625f);
		}
		ItemDisplayContext context = head ? ItemDisplayContext.HEAD
			: offhand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.FIXED;
		HgnCosmeticModels.renderStatic(stack.get(), context, pose, buffers,
			FULL_BRIGHT, definition.id().hashCode());
		pose.popPose();
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		if (!active) return;
		rotationX = Mth.clamp(rotationX - (float) dragY * 2.5f, -50.0f, 50.0f);
		rotationY += (float) dragX * 2.5f;
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}
}
