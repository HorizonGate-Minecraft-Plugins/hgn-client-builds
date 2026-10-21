package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** Image-backed responsive gateway shared by startup, reload, connect, and world loading. */
public final class HgnLoadingArtwork {
	private static final int FRAME_TEXTURE_WIDTH = 1024;
	private static final int FRAME_TEXTURE_HEIGHT = 495;
	private static final String[] PERCENTAGES = percentages();
	private static Layout cachedLayout;

	private HgnLoadingArtwork() {
	}

	/** Bootstrap has a GUI shader, but its font shaders and glyph atlas are not loaded yet. */
	public static void renderBootstrap(GuiGraphics graphics, float progress) {
		HgnSurface.screen(graphics, graphics.guiWidth(), graphics.guiHeight());
		Layout layout = layout(graphics.guiWidth(), graphics.guiHeight());
		HgnSurface.glass(graphics, layout.x(), layout.y(), layout.frameWidth(), layout.frameHeight(), true);
		int logoWidth = Math.min(100, layout.contentWidth() / 2);
		HgnSurface.wordmark(graphics, layout.x() + (layout.frameWidth() - logoWidth) / 2,
			layout.y() + layout.frameHeight() / 3, logoWidth, logoWidth / 2, false);
		HgnSurface.meter(graphics, layout.laneX(), layout.laneY(), layout.laneWidth(),
			layout.laneHeight(), Math.max(0.0f, Math.min(1.0f, progress)), HgnPalette.CYAN);
	}

	public static void render(GuiGraphics graphics, float progress, String title, String detail) {
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		HgnSurface.screen(graphics, screenWidth, screenHeight);

		Layout layout = layout(screenWidth, screenHeight);
		int frameWidth = layout.frameWidth();
		int frameHeight = layout.frameHeight();
		int x = layout.x();
		int y = layout.y();
		// The cached opaque fallback remains readable if a resource reload swaps
		// textures between frames; the artwork replaces it when available.
		HgnSurface.glass(graphics, x, y, frameWidth, frameHeight, true);

		Minecraft minecraft = Minecraft.getInstance();
		int contentX = layout.contentX();
		int contentY = layout.contentY();
		int contentWidth = layout.contentWidth();
		int contentHeight = layout.contentHeight();
		boolean compact = layout.compact();
		int logoWidth = Math.max(28, Math.min(compact ? 46 : 82,
			Math.round(contentHeight * 0.52f)));
		int logoHeight = Math.round(logoWidth * 160.0f / 256.0f);
		HgnSurface.wordmark(graphics, x + frameWidth / 2 - logoWidth / 2,
			contentY, logoWidth, logoHeight);

		String safeTitle = HgnTypography.trim(minecraft.font,
			title == null || title.isBlank() ? "HGN CLIENT" : title, contentWidth);
		String safeDetail = HgnTypography.trim(minecraft.font,
			detail == null || detail.isBlank() ? "Preparing the client" : detail, contentWidth);
		int titleY = contentY + logoHeight + (compact ? 2 : 6);
		HgnTypography.centered(graphics, minecraft.font, safeTitle,
			x + frameWidth / 2, titleY, HgnPalette.TEXT);
		HgnTypography.centered(graphics, minecraft.font, safeDetail,
			x + frameWidth / 2, titleY + minecraft.font.lineHeight + 2, HgnPalette.mutedText());

		float normalized = Math.max(0.0f, Math.min(1.0f, progress));
		String phase = normalized >= 0.995f ? "RESOURCES READY" : "LOADING RESOURCES";
		int phaseY = Math.min(contentY + contentHeight - minecraft.font.lineHeight,
			titleY + minecraft.font.lineHeight * 2 + (compact ? 3 : 10));
		HgnTypography.centered(graphics, minecraft.font, phase,
			x + frameWidth / 2, phaseY, HgnPalette.CYAN_SOFT);

		HgnSurface.meter(graphics, layout.laneX(), layout.laneY(), layout.laneWidth(),
			layout.laneHeight(), normalized, HgnPalette.CYAN);
		String percent = PERCENTAGES[Math.round(normalized * 100.0f)];
		HgnTypography.centered(graphics, minecraft.font, percent,
			x + frameWidth / 2, layout.laneY() - minecraft.font.lineHeight - 2,
			HgnPalette.TEXT);
	}

	private static Layout layout(int screenWidth, int screenHeight) {
		Layout current = cachedLayout;
		if (current != null && current.screenWidth() == screenWidth
			&& current.screenHeight() == screenHeight) return current;
		int availableWidth = Math.max(1, screenWidth - 12);
		int availableHeight = Math.max(1, screenHeight - 12);
		int frameWidth = Math.min(720, Math.min(availableWidth,
			Math.max(1, availableHeight * FRAME_TEXTURE_WIDTH / FRAME_TEXTURE_HEIGHT)));
		int frameHeight = Math.max(1,
			Math.round(frameWidth * FRAME_TEXTURE_HEIGHT / (float) FRAME_TEXTURE_WIDTH));
		int x = (screenWidth - frameWidth) / 2;
		int y = (screenHeight - frameHeight) / 2;
		current = new Layout(screenWidth, screenHeight, x, y, frameWidth, frameHeight,
			x + Math.round(frameWidth * 0.14f), y + Math.round(frameHeight * 0.19f),
			Math.max(1, Math.round(frameWidth * 0.72f)),
			Math.max(1, Math.round(frameHeight * 0.54f)),
			x + Math.round(frameWidth * 0.292f), y + Math.round(frameHeight * 0.835f),
			Math.max(24, Math.round(frameWidth * 0.416f)),
			Math.max(3, Math.round(frameHeight * 0.044f)), frameHeight < 180);
		cachedLayout = current;
		return current;
	}

	private static String[] percentages() {
		String[] labels = new String[101];
		for (int value = 0; value <= 100; value++) labels[value] = value + "%";
		return labels;
	}

	private record Layout(int screenWidth, int screenHeight, int x, int y,
		int frameWidth, int frameHeight, int contentX, int contentY,
		int contentWidth, int contentHeight, int laneX, int laneY,
		int laneWidth, int laneHeight, boolean compact) {
	}
}
