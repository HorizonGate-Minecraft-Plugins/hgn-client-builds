package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.mixin.HgnBossOverlayAccessor;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnGameplayHudLayout;

/** Client-local readouts that never replace Minecraft's survival or vehicle HUD. */
public final class HgnGameplayHud {
	private HgnGameplayHud() {
	}

	public static boolean visible(Minecraft minecraft) {
		return HgnConfig.get().interfaceEnabled && HgnConfig.get().hudEnabled
			&& minecraft.player != null && minecraft.level != null
			&& !minecraft.options.hideGui && !minecraft.getDebugOverlay().showDebugScreen()
			&& !HgnDragonEye.owningScreen()
			&& (minecraft.screen == null || minecraft.screen instanceof ChatScreen);
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!visible(minecraft)) return;
		if (HgnConfig.get().hudFrames) drawNativeFrames(graphics, minecraft);
		var layout = layout(graphics);
		if (!layout.readoutsVisible()) return;
		int top = layout.performance().y();
		float scale = HgnConfig.get().hudSize.multiplier();
		int leftWidth = (int) Math.floor(layout.performance().width() / scale);
		int rightWidth = (int) Math.floor(layout.context().width() / scale);
		if (HgnConfig.get().hudPerformance) {
			HgnDraw.beginScaleAt(graphics, scale, 5, top);
			try {
				HgnSurface.hudPlayerHeader(graphics, 5, top, leftWidth, 46);
				line(graphics, "HGN CLIENT", 10, top + 5, leftWidth - 10, HgnPalette.CYAN);
				line(graphics, minecraft.getFps() + " FPS", 10, top + 15, leftWidth - 10, HgnPalette.TEXT);
				var connection = minecraft.getConnection();
				var info = connection == null ? null : connection.getPlayerInfo(minecraft.player.getUUID());
				String latency = minecraft.hasSingleplayerServer() ? "LOCAL WORLD"
					: info == null ? "PING UNAVAILABLE" : info.getLatency() + " ms";
				line(graphics, latency, 10, top + 25, leftWidth - 10, HgnPalette.TEXT);
				line(graphics, HgnClickRate.INSTANCE.label(System.nanoTime()), 10, top + 35,
					leftWidth - 10, HgnPalette.TEXT);
			} finally {
				HgnDraw.endScale(graphics);
			}
		}
		if (!HgnConfig.get().hudContext) return;
		int right = layout.context().x();
		int rightTop = layout.context().y();
		HgnDraw.beginScaleAt(graphics, scale, right, rightTop);
		try {
		HgnSurface.hudPlayerHeader(graphics, right, rightTop, rightWidth, 46);
		String mode = minecraft.gameMode == null ? "" : minecraft.gameMode.getPlayerMode().getName();
		line(graphics, mode, right + 5, rightTop + 5, rightWidth - 10, HgnPalette.CYAN);
		line(graphics, "HGN " + HgnClient.CLIENT_VERSION, right + 5, rightTop + 15,
			rightWidth - 10, HgnPalette.TEXT);
		var pos = minecraft.player.blockPosition();
		if (HgnConfig.get().hudCoordinates) {
			line(graphics, "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ(),
				right + 5, rightTop + 25, rightWidth - 10, HgnPalette.TEXT);
		} else {
			line(graphics, "Coordinates hidden", right + 5, rightTop + 25, rightWidth - 10, HgnPalette.TEXT_MUTED);
		}
		String biome = HgnCompat.biomeName(minecraft);
		line(graphics, "Biome: " + biome, right + 5, rightTop + 35,
			rightWidth - 10, HgnPalette.TEXT);
		} finally {
			HgnDraw.endScale(graphics);
		}
	}

	private static void drawNativeFrames(GuiGraphics graphics, Minecraft minecraft) {
		int center = graphics.guiWidth() / 2;
		if (!minecraft.player.isSpectator() && !HgnHotbar.renderedThisFrame()
			&& graphics.guiWidth() >= 194) {
			int y = graphics.guiHeight() - 23;
			outline(graphics, center - 93, y, 186, 23, HgnPalette.EDGE_STRONG);
			int selected = Math.max(0, Math.min(8, HgnCompat.selectedHotbarSlot()));
			outline(graphics, center - 92 + selected * 20, y, 24, 23, HgnPalette.CYAN);
		}
		if (graphics.guiWidth() < 194) return;
		int count = ((HgnBossOverlayAccessor) minecraft.gui.getBossOverlay()).hgn$getEvents().size();
		int y = 12;
		for (int index = 0; index < count; index++) {
			outline(graphics, center - 92, y - 1, 184, 7, HgnPalette.CYAN);
			y += 19;
			if (y >= graphics.guiHeight() / 3) break;
		}
	}

	private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		HgnDraw.rect(graphics, x + 2, y, x + width - 2, y + 1, color);
		HgnDraw.rect(graphics, x + 2, y + height - 1, x + width - 2, y + height, color);
		HgnDraw.rect(graphics, x, y + 2, x + 1, y + height - 2, color);
		HgnDraw.rect(graphics, x + width - 1, y + 2, x + width, y + height - 2, color);
	}

	static HgnGameplayHudLayout layout(GuiGraphics graphics) {
		var minecraft = Minecraft.getInstance();
		return HgnGameplayHudLayout.resolve(graphics.guiWidth(), graphics.guiHeight(),
			!((HgnBossOverlayAccessor) minecraft.gui.getBossOverlay()).hgn$getEvents().isEmpty(),
			!minecraft.player.getActiveEffects().isEmpty(), HgnConfig.get().hudSize.multiplier());
	}

	private static void line(GuiGraphics graphics, String text, int x, int y, int width, int color) {
		var font = Minecraft.getInstance().font;
		String fitted = font.plainSubstrByWidth(text, Math.max(1, width));
		graphics.drawString(font, fitted, x, y, color, false);
	}
}
