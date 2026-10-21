package online.horizongate.hgn.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing calls whose signatures differ between Minecraft releases.
 * This generation covers 1.21.2 - 1.21.5: still the PoseStack GUI renderer, but
 * 1.21.2 changed blit to take the render type up front.
 */
public final class HgnDraw {
	private static final String NAMESPACE = "hgn-client";

	private HgnDraw() {
	}

	/**
	 * The authentic vanilla Minecraft wordmark, centred at the given top; returns
	 * its height. Blits the logo texture directly rather than going through
	 * LogoRenderer, which would also stamp the "Java Edition" strip the mockup omits.
	 */
	public static int minecraftLogo(GuiGraphics g, int screenWidth, int y, float scale) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(screenWidth / 2.0f, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		// The logo texture is 256x64, not square; passing the wrong texture height
		// samples the wrong UV region and garbles the wordmark.
		g.blit(RenderType::guiTextured, LogoRenderer.MINECRAFT_LOGO, -LogoRenderer.LOGO_WIDTH / 2, 0,
			0.0f, 0.0f, LogoRenderer.LOGO_WIDTH, LogoRenderer.LOGO_HEIGHT, LogoRenderer.LOGO_WIDTH, 64);
		pose.popPose();
		return Math.round(LogoRenderer.LOGO_HEIGHT * scale);
	}

	public static void text(GuiGraphics g, Font f, String s, int x, int y, int color) {
		g.drawString(f, s, x, y, color);
	}

	public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int color) {
		g.drawCenteredString(f, s, cx, y, color);
	}

	public static void centered(GuiGraphics g, Font f, Component c, int cx, int y, int color) {
		g.drawCenteredString(f, c, cx, y, color);
	}

	public static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y1, color);
	}

	public static void gradient(GuiGraphics g, int x0, int y0, int x1, int y1, int top, int bottom) {
		g.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	public static void beginScale(GuiGraphics g, float scale) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.scale(scale, scale, 1.0f);
	}

	public static void beginScaleAt(GuiGraphics g, float scale, int x, int y) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x * (1.0f - scale), y * (1.0f - scale), 0.0f);
		pose.scale(scale, scale, 1.0f);
	}

	public static void endScale(GuiGraphics g) {
		g.pose().popPose();
	}

	public static void itemTooltip(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
		g.renderTooltip(font, stack, x, y);
	}

	public static void item(GuiGraphics g, ItemStack stack, int x, int y, float scale, int seed) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		g.renderItem(stack, 0, 0, seed);
		pose.popPose();
	}

	public static boolean textureExists(String path) {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getResourceManager()
			.getResource(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)).isPresent();
	}

	public static void texture(GuiGraphics g, String path, int x, int y, int w, int h) {
		g.blit(RenderType::guiTextured, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, w, h, w, h);
	}

	/** Draws the authenticated live skin face plus its hat layer into a compact HUD socket. */
	public static void playerFace(GuiGraphics g, int x, int y, int size) {
		var player = Minecraft.getInstance().player;
		if (player == null || size <= 0) return;
		var skin = player.getSkin().texture();
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(size / 8.0f, size / 8.0f, 1.0f);
		g.blit(RenderType::guiTextured, skin, 0, 0, 8.0f, 8.0f, 8, 8, 64, 64);
		g.blit(RenderType::guiTextured, skin, 0, 0, 40.0f, 8.0f, 8, 8, 64, 64);
		pose.popPose();
	}

	public static void vanillaTexture(GuiGraphics g, String path, int x, int y, int width, int height) {
		g.blit(RenderType::guiTextured, ResourceLocation.withDefaultNamespace(path),
			x, y, 0.0f, 0.0f, width, height, 16, 16);
	}

	public static void textureScaled(GuiGraphics g, String path, int x, int y, int w, int h,
		int textureWidth, int textureHeight) {
		g.blit(RenderType::guiTextured, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, w, h, textureWidth, textureHeight);
	}

	public static void textureStretch(GuiGraphics g, String path, int x, int y, int width, int height,
		int textureWidth, int textureHeight) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(width / (float) textureWidth, height / (float) textureHeight, 1.0f);
		g.blit(RenderType::guiTextured, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight);
		pose.popPose();
	}

	public static void textureRegionStretch(GuiGraphics g, String path, int x, int y,
		int width, int height, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		int textureWidth, int textureHeight) {
		if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(width / (float) sourceWidth, height / (float) sourceHeight, 1.0f);
		g.blit(RenderType::guiTextured, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, (float) sourceX, (float) sourceY, sourceWidth, sourceHeight,
			textureWidth, textureHeight);
		pose.popPose();
	}

	/** Keeps generated panel corners crisp while stretching only their straight edges and center. */
	public static void textureNineSlice(GuiGraphics g, String path, int x, int y,
		int width, int height, int textureSize, int sourceSlice, int destinationSlice) {
		if (width <= 0 || height <= 0 || textureSize <= sourceSlice * 2) return;
		int edge = Math.max(1, Math.min(destinationSlice, Math.min(width / 2, height / 2)));
		int sourceMiddle = textureSize - sourceSlice * 2;
		int middleWidth = Math.max(0, width - edge * 2);
		int middleHeight = Math.max(0, height - edge * 2);

		textureRegion(g, path, x, y, edge, edge,
			0, 0, sourceSlice, sourceSlice, textureSize);
		textureRegion(g, path, x + edge, y, middleWidth, edge,
			sourceSlice, 0, sourceMiddle, sourceSlice, textureSize);
		textureRegion(g, path, x + width - edge, y, edge, edge,
			textureSize - sourceSlice, 0, sourceSlice, sourceSlice, textureSize);
		textureRegion(g, path, x, y + edge, edge, middleHeight,
			0, sourceSlice, sourceSlice, sourceMiddle, textureSize);
		textureRegion(g, path, x + edge, y + edge, middleWidth, middleHeight,
			sourceSlice, sourceSlice, sourceMiddle, sourceMiddle, textureSize);
		textureRegion(g, path, x + width - edge, y + edge, edge, middleHeight,
			textureSize - sourceSlice, sourceSlice, sourceSlice, sourceMiddle, textureSize);
		textureRegion(g, path, x, y + height - edge, edge, edge,
			0, textureSize - sourceSlice, sourceSlice, sourceSlice, textureSize);
		textureRegion(g, path, x + edge, y + height - edge, middleWidth, edge,
			sourceSlice, textureSize - sourceSlice, sourceMiddle, sourceSlice, textureSize);
		textureRegion(g, path, x + width - edge, y + height - edge, edge, edge,
			textureSize - sourceSlice, textureSize - sourceSlice,
			sourceSlice, sourceSlice, textureSize);
	}

	private static void textureRegion(GuiGraphics g, String path, int x, int y,
		int width, int height, int sourceX, int sourceY, int sourceWidth,
		int sourceHeight, int textureSize) {
		if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(width / (float) sourceWidth, height / (float) sourceHeight, 1.0f);
		g.blit(RenderType::guiTextured, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, (float) sourceX, (float) sourceY, sourceWidth, sourceHeight,
			textureSize, textureSize);
		pose.popPose();
	}

	public static void scaledText(GuiGraphics g, Font f, String s, int x, int y, float scale, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		g.drawString(f, s, 0, 0, color);
		pose.popPose();
	}

	public static void scaledText(GuiGraphics g, Font f, Component c,
		int x, int y, float scale, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) x, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		g.drawString(f, c, 0, 0, color);
		pose.popPose();
	}

	public static void scaledCentered(GuiGraphics g, Font f, String s, int cx, int y, float scale, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) cx, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		g.drawCenteredString(f, s, 0, 0, color);
		pose.popPose();
	}

	public static void scaledCentered(GuiGraphics g, Font f, Component c, int cx, int y, float scale, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) cx, (float) y, 0.0f);
		pose.scale(scale, scale, 1.0f);
		g.drawCenteredString(f, c, 0, 0, color);
		pose.popPose();
	}

	public static void rotatedCentered(GuiGraphics g, Font f, String s, int cx, int y,
		float scale, float degrees, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate((float) cx, (float) y, 0.0f);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		pose.scale(scale, scale, 1.0f);
		g.drawCenteredString(f, s, 0, -8, color);
		pose.popPose();
	}

}
