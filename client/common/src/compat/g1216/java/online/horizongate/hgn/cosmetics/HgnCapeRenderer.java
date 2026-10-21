package online.horizongate.hgn.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import online.horizongate.hgn.HgnClient;

/** Two-sided segmented cloth renderer for approved HGN cape textures. */
public final class HgnCapeRenderer {
	private static final String NAMESPACE = "hgn_cosmetics";
	private static final int SEGMENTS = 12;
	private static final float WIDTH = 10.0f / 16.0f;
	private static final float HEIGHT = 16.0f / 16.0f;
	private static final float FACE_OFFSET = 0.0025f;
	private static final Set<String> APPROVED_ASSETS = Set.of(
		"vinksmp_cape", "parsrng_cape", "hgn_network_cape", "owner_cape", "staff_cape",
		"zombie_dungeon_cape"
	);
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	private static final Map<CapeKey, Optional<ResourceLocation>> TEXTURES =
		new ConcurrentHashMap<>();

	private HgnCapeRenderer() {
	}

	public static boolean isCape(HgnCosmetics.ItemDefinition definition) {
		if (definition == null || !definition.active()
			|| !definition.equipmentSlot().equalsIgnoreCase("CAPE")
			|| !definition.tags().contains("supplied_cape")
			|| !definition.tags().contains("entitlement_only")) return false;
		ResourceLocation asset = ResourceLocation.tryParse(definition.assetId());
		return asset != null && NAMESPACE.equals(asset.getNamespace())
			&& APPROVED_ASSETS.contains(asset.getPath());
	}

	public static boolean canRender(HgnCosmetics.ItemDefinition definition) {
		return texture(definition) != null;
	}

	public static boolean renderPlayer(PoseStack pose, MultiBufferSource buffers, int light,
		PlayerModel model, PlayerRenderState state, HgnCosmetics.ItemDefinition definition) {
		return render(pose, buffers, light, model, definition, state.ageInTicks,
			state.capeFlap, state.capeLean, state.capeLean2);
	}

	public static boolean renderPreview(PoseStack pose, MultiBufferSource buffers, int light,
		PlayerModel model, HgnCosmetics.ItemDefinition definition, float ageInTicks) {
		return render(pose, buffers, light, model, definition, ageInTicks, 2.0f, 2.0f, 0.0f);
	}

	public static boolean renderGui(GuiGraphics graphics,
		HgnCosmetics.ItemDefinition definition, int x, int y, int width, int height) {
		ResourceLocation texture = texture(definition);
		if (texture == null) return false;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f,
			width, height, 256, 512, 512, 512);
		return true;
	}

	private static boolean render(PoseStack pose, MultiBufferSource buffers, int light,
		PlayerModel model, HgnCosmetics.ItemDefinition definition, float ageInTicks,
		float capeFlap, float capeLean, float capeLean2) {
		ResourceLocation texture = texture(definition);
		if (texture == null) return false;

		pose.pushPose();
		model.root().translateAndRotate(pose);
		model.body.translateAndRotate(pose);
		pose.translate(0.0f, 0.0f, 2.15f / 16.0f);
		pose.mulPose(Axis.XP.rotationDegrees(6.0f
			+ Mth.clamp(capeLean, 0.0f, 150.0f) * 0.5f
			+ Mth.clamp(capeFlap, -6.0f, 32.0f)));
		pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(capeLean2, -40.0f, 40.0f) * 0.5f));

		VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
		float motion = Mth.clamp((Math.abs(capeFlap) + Math.abs(capeLean2)) / 48.0f,
			0.0f, 1.0f);
		for (int segment = 0; segment < SEGMENTS; segment++) {
			float start = segment / (float) SEGMENTS;
			float end = (segment + 1) / (float) SEGMENTS;
			float y0 = start * HEIGHT;
			float y1 = end * HEIGHT;
			float z0 = curve(ageInTicks, motion, start);
			float z1 = curve(ageInTicks, motion, end);
			float halfWidth0 = WIDTH * (0.5f - start * 0.018f);
			float halfWidth1 = WIDTH * (0.5f - end * 0.018f);
			float normalY = -(z1 - z0);
			float normalZ = Math.max(0.0001f, y1 - y0);
			float inverseLength = Mth.invSqrt(normalY * normalY + normalZ * normalZ);
			normalY *= inverseLength;
			normalZ *= inverseLength;

			face(vertices, pose.last(), -halfWidth0, halfWidth0, y0, z0 + FACE_OFFSET,
				-halfWidth1, halfWidth1, y1, z1 + FACE_OFFSET,
				0.0f, 0.5f, start, end, light, normalY, normalZ, false);
			face(vertices, pose.last(), -halfWidth0, halfWidth0, y0, z0 - FACE_OFFSET,
				-halfWidth1, halfWidth1, y1, z1 - FACE_OFFSET,
				0.5f, 1.0f, start, end, light, -normalY, -normalZ, true);
		}
		pose.popPose();
		return true;
	}

	private static float curve(float ageInTicks, float motion, float progress) {
		float relaxedFold = 0.018f * progress * progress;
		float flutter = Mth.sin(ageInTicks * 0.16f + progress * 5.0f)
			* 0.018f * motion * progress;
		return relaxedFold + flutter;
	}

	private static void face(VertexConsumer vertices, PoseStack.Pose pose,
		float left0, float right0, float y0, float z0,
		float left1, float right1, float y1, float z1,
		float u0, float u1, float v0, float v1, int light,
		float normalY, float normalZ, boolean reverse) {
		if (reverse) {
			vertex(vertices, pose, left0, y0, z0, u0, v0, light, normalY, normalZ);
			vertex(vertices, pose, left1, y1, z1, u0, v1, light, normalY, normalZ);
			vertex(vertices, pose, right1, y1, z1, u1, v1, light, normalY, normalZ);
			vertex(vertices, pose, right0, y0, z0, u1, v0, light, normalY, normalZ);
			return;
		}
		vertex(vertices, pose, left0, y0, z0, u0, v0, light, normalY, normalZ);
		vertex(vertices, pose, right0, y0, z0, u1, v0, light, normalY, normalZ);
		vertex(vertices, pose, right1, y1, z1, u1, v1, light, normalY, normalZ);
		vertex(vertices, pose, left1, y1, z1, u0, v1, light, normalY, normalZ);
	}

	private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
		float x, float y, float z, float u, float v, int light,
		float normalY, float normalZ) {
		vertices.addVertex(pose, x, y, z)
			.setColor(255, 255, 255, 255)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0.0f, normalY, normalZ);
	}

	private static ResourceLocation texture(HgnCosmetics.ItemDefinition definition) {
		if (!isCape(definition)) return null;
		CapeKey key = new CapeKey(definition.id(), definition.assetId(), definition.assetRevision());
		return TEXTURES.computeIfAbsent(key, ignored -> resolveTexture(definition)).orElse(null);
	}

	private static Optional<ResourceLocation> resolveTexture(HgnCosmetics.ItemDefinition definition) {
		ResourceLocation asset = ResourceLocation.tryParse(definition.assetId());
		if (asset == null) return Optional.empty();
		ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(NAMESPACE,
			"textures/capes/" + asset.getPath() + ".png");
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getResourceManager().getResource(texture).isEmpty()) {
			warn(definition, "private cloth-cape texture is not loaded");
			return Optional.empty();
		}
		return Optional.of(texture);
	}

	static void invalidateResourceCache() {
		TEXTURES.clear();
	}

	private static void warn(HgnCosmetics.ItemDefinition definition, String reason) {
		if (WARNED.add(definition.id() + ':' + definition.assetRevision() + ':' + reason)) {
			HgnClient.LOGGER.warn("Cape {} hidden: {}.", definition.id(), reason);
		}
	}

	private record CapeKey(String id, String assetId, long assetRevision) {
	}
}
