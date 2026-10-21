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

/** Drawing calls for the 1.21.9-1.21.10 GUI renderer. */
public final class HgnDraw {
	private static final String NAMESPACE = "hgn-client";

	private HgnDraw() {
	}

	public static int minecraftLogo(GuiGraphics graphics, int screenWidth, int y, float scale) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) (screenWidth / 2), (float) y);
		pose.scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LogoRenderer.MINECRAFT_LOGO, -LogoRenderer.LOGO_WIDTH / 2, 0,
			0.0f, 0.0f, LogoRenderer.LOGO_WIDTH, LogoRenderer.LOGO_HEIGHT, LogoRenderer.LOGO_WIDTH, 64);
		pose.popMatrix();
		return Math.round(LogoRenderer.LOGO_HEIGHT * scale);
	}

	public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public static void centered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
		graphics.drawCenteredString(font, text, centerX, y, color);
	}

	public static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
		graphics.drawCenteredString(font, text, centerX, y, color);
	}

	public static void rect(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y1, color);
	}

	public static void gradient(GuiGraphics graphics, int x0, int y0, int x1, int y1, int top, int bottom) {
		graphics.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	public static void beginScale(GuiGraphics graphics, float scale) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
	}

	public static void beginScaleAt(GuiGraphics g, float scale, int x, int y) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x * (1.0f - scale), y * (1.0f - scale));
		pose.scale(scale, scale);
	}

	public static void endScale(GuiGraphics graphics) {
		graphics.pose().popMatrix();
	}

	public static void itemTooltip(GuiGraphics graphics, Font font, ItemStack stack,
		int x, int y) {
		graphics.setTooltipForNextFrame(font, stack, x, y);
	}

	public static void item(GuiGraphics graphics, ItemStack stack, int x, int y, float scale, int seed) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		graphics.renderItem(stack, 0, 0, seed);
		pose.popMatrix();
	}

	public static boolean textureExists(String path) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null && minecraft.getResourceManager()
			.getResource(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)).isPresent();
	}

	public static void texture(GuiGraphics graphics, String path, int x, int y, int width, int height) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, width, height, width, height);
	}

	/** Draws the authenticated live skin face plus its hat layer into a compact HUD socket. */
	public static void playerFace(GuiGraphics graphics, int x, int y, int size) {
		var player = Minecraft.getInstance().player;
		if (player == null || size <= 0) return;
		var skin = player.getSkin().body().texturePath();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(size / 8.0f, size / 8.0f);
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin,
			0, 0, 8.0f, 8.0f, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin,
			0, 0, 40.0f, 8.0f, 8, 8, 64, 64);
		pose.popMatrix();
	}

	public static void vanillaTexture(GuiGraphics graphics, String path,
		int x, int y, int width, int height) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.withDefaultNamespace(path),
			x, y, 0.0f, 0.0f, width, height, 16, 16);
	}

	public static void textureScaled(GuiGraphics graphics, String path, int x, int y, int width, int height,
		int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			x, y, 0.0f, 0.0f, width, height, textureWidth, textureHeight);
	}

	public static void textureStretch(GuiGraphics graphics, String path, int x, int y, int width, int height,
		int textureWidth, int textureHeight) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(width / (float) textureWidth, height / (float) textureHeight);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
			0, 0, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight);
		pose.popMatrix();
	}

	public static void textureRegionStretch(GuiGraphics graphics, String path, int x, int y,
		int width, int height, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		int textureWidth, int textureHeight) {
		if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(width / (float) sourceWidth, height / (float) sourceHeight);
		graphics.blit(RenderPipelines.GUI_TEXTURED,
			ResourceLocation.fromNamespaceAndPath(NAMESPACE, path), 0, 0,
			(float) sourceX, (float) sourceY, sourceWidth, sourceHeight,
			textureWidth, textureHeight);
		pose.popMatrix();
	}

	public static void scaledText(GuiGraphics graphics, Font font, String text, int x, int y,
		float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledText(GuiGraphics graphics, Font font, Component text,
		int x, int y, float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledCentered(GuiGraphics graphics, Font font, String text, int centerX, int y,
		float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) centerX, (float) y);
		pose.scale(scale, scale);
		graphics.drawCenteredString(font, text, 0, 0, color);
		pose.popMatrix();
	}

	public static void scaledCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y,
		float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) centerX, (float) y);
		pose.scale(scale, scale);
		graphics.drawCenteredString(font, text, 0, 0, color);
		pose.popMatrix();
	}

	public static void rotatedCentered(GuiGraphics graphics, Font font, String text, int centerX, int y,
		float scale, float degrees, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) centerX, (float) y);
		pose.rotate(degrees * ((float) Math.PI / 180.0f));
		pose.scale(scale, scale);
		graphics.drawCenteredString(font, text, 0, -8, color);
		pose.popMatrix();
	}
}
