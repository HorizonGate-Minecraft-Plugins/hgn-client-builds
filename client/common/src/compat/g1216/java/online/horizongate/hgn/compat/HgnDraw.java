package online.horizongate.hgn.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Drawing calls whose signatures differ between Minecraft releases.
 * 1.21.6 replaced the 3D PoseStack in GUI rendering with a 2D Matrix3x2fStack
 * and moved blit onto render pipelines; this generation covers 1.21.6 - 1.21.10.
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
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) (screenWidth / 2), (float) y);
		pose.scale(scale, scale);
		// The logo texture is 256x64, not square; passing the wrong texture height
		// samples the wrong UV region and garbles the wordmark.
		g.blit(RenderPipelines.GUI_TEXTURED, LogoRenderer.MINECRAFT_LOGO, -LogoRenderer.LOGO_WIDTH / 2, 0,
			0.0f, 0.0f, LogoRenderer.LOGO_WIDTH, LogoRenderer.LOGO_HEIGHT, LogoRenderer.LOGO_WIDTH, 64);
		pose.popMatrix();
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
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
	}

	public static void beginScaleAt(GuiGraphics g, float scale, int x, int y) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x * (1.0f - scale), y * (1.0f - scale));
		pose.scale(scale, scale);
	}

	public static void endScale(GuiGraphics g) {
		g.pose().popMatrix();
	}

	public static void itemTooltip(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
		g.setTooltipForNextFrame(font, stack, x, y);
	}

	public static void item(GuiGraphics g, ItemStack stack, int x, int y, float scale, int seed) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		g.renderItem(stack, 0, 0, seed);
		pose.popMatrix();
	}

	public static boolean textureExists(String path) {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getResourceManager()
			.getResource(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)).isPresent();
	}

	public static void texture(GuiGraphics g, String path, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, w, h, w, h);
	}

	/** Draws the authenticated live skin face plus its hat layer into a compact HUD socket. */
	public static void playerFace(GuiGraphics g, int x, int y, int size) {
		var player = Minecraft.getInstance().player;
		if (player == null || size <= 0) return;
		var skin = player.getSkin().texture();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(size / 8.0f, size / 8.0f);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 8.0f, 8.0f, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 40.0f, 8.0f, 8, 8, 64, 64);
		pose.popMatrix();
	}

	public static void vanillaTexture(GuiGraphics g, String path, int x, int y, int width, int height) {
		g.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.withDefaultNamespace(path),
			x, y, 0.0f, 0.0f, width, height, 16, 16);
	}

	public static void textureScaled(GuiGraphics g, String path, int x, int y, int w, int h,
		int textureWidth, int textureHeight) {
		g.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, w, h, textureWidth, textureHeight);
	}

	public static void textureStretch(GuiGraphics g, String path, int x, int y, int width, int height,
		int textureWidth, int textureHeight) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(width / (float) textureWidth, height / (float) textureHeight);
		g.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight);
		pose.popMatrix();
	}

	public static void textureRegionStretch(GuiGraphics g, String path, int x, int y,
		int width, int height, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		int textureWidth, int textureHeight) {
		if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(width / (float) sourceWidth, height / (float) sourceHeight);
		g.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, (float) sourceX, (float) sourceY, sourceWidth, sourceHeight,
			textureWidth, textureHeight);
		pose.popMatrix();
	}

	public static void scaledText(GuiGraphics g, Font f, String s, int x, int y, float scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		g.drawString(f, s, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledText(GuiGraphics g, Font f, Component c,
		int x, int y, float scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		g.drawString(f, c, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledCentered(GuiGraphics g, Font f, String s, int cx, int y, float scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) cx, (float) y);
		pose.scale(scale, scale);
		g.drawCenteredString(f, s, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledCentered(GuiGraphics g, Font f, Component c, int cx, int y, float scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) cx, (float) y);
		pose.scale(scale, scale);
		g.drawCenteredString(f, c, 0, 0, color);
		pose.popMatrix();
	}

	public static void rotatedCentered(GuiGraphics g, Font f, String s, int cx, int y,
		float scale, float degrees, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) cx, (float) y);
		pose.rotate(degrees * ((float) Math.PI / 180.0f));
		pose.scale(scale, scale);
		g.drawCenteredString(f, s, 0, -8, color);
		pose.popMatrix();
	}

}
