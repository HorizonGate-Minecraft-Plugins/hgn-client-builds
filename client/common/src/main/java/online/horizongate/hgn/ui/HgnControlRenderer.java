package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.lwjgl.glfw.GLFW;

/** Shared renderer for HGN-owned and inherited vanilla buttons. */
public final class HgnControlRenderer {
	private HgnControlRenderer() {
	}

	public static void renderReference(AbstractButton button, GuiGraphics graphics,
		HgnIcon icon, boolean primary, float scale) {
		renderReference(button, graphics, icon, primary, scale, scale);
	}

	public static void renderReference(AbstractButton button, GuiGraphics graphics,
		HgnIcon icon, boolean primary, float textScale, float iconScale) {
		boolean hot = button.active && button.isHoveredOrFocused();
		HgnSurface.control(graphics, button.getX(), button.getY(), button.getWidth(),
			button.getHeight(), hot ? 1 : 0, button.isFocused(), primary,
			button.isHovered() && mouseDown(), !button.active);
		int iconSize = Math.max(4, Math.round(24 * iconScale));
		int centerY = button.getY() + button.getHeight() / 2;
		icon.draw(graphics, button.getX() + Math.round(7 * iconScale),
			centerY - iconSize / 2, iconSize, HgnPalette.CYAN_SOFT);
		var font = Minecraft.getInstance().font;
		if (icon == HgnIcon.NONE) {
			String label = button.getMessage().getString();
			float fit = Math.min(textScale, Math.max(1, button.getWidth() - Math.round(8 * iconScale))
				/ (float) Math.max(1, online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, label)));
			online.horizongate.hgn.ui.dashboard.HgnTypography.centered(graphics, font, label,
				button.getX() + button.getWidth() / 2, centerY - Math.round(font.lineHeight * fit / 2),
				fit, button.active ? HgnPalette.TEXT : HgnPalette.DISABLED);
			return;
		}
		int textX = button.getX() + Math.round(35 * iconScale);
		String label = button.getMessage().getString();
		int available = button.getX() + button.getWidth() - Math.round(9 * iconScale) - textX;
		float fit = Math.min(textScale, available / (float) Math.max(1,
			online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, label)));
		online.horizongate.hgn.ui.dashboard.HgnTypography.text(graphics, font, label,
			textX, centerY - Math.round(font.lineHeight * fit / 2), fit,
			button.active ? HgnPalette.TEXT : HgnPalette.DISABLED);
	}

	public static void render(AbstractButton button, GuiGraphics graphics, HgnIcon icon,
		boolean primary, boolean selected, boolean leftAligned) {
		render(button, graphics, icon, primary, selected, leftAligned, false);
	}

	public static void render(AbstractButton button, GuiGraphics graphics, HgnIcon icon,
		boolean primary, boolean selected, boolean leftAligned, boolean dashboardSurface) {
		if (button instanceof online.horizongate.hgn.ui.container.HgnTradeButtonState
			&& online.horizongate.hgn.HgnConfig.get().containerLayoutsEnabled()) {
			if (button.isFocused()) HgnSurface.focus(graphics, button.getX(), button.getY(),
				button.getWidth(), button.getHeight());
			return;
		}
		boolean hot = button.active && button.isHoveredOrFocused();
		boolean focused = button.active && button.isFocused();
		boolean pressed = button.active && button.isHovered() && mouseDown();
		float hover = hot || selected ? 1.0f : 0.0f;
		int x = button.getX();
		int y = button.getY() + (pressed ? 1 : 0);
		int width = button.getWidth();
		int height = button.getHeight();
		int right = x + width;
		int centerX = x + width / 2;
		int centerY = y + height / 2;
		HgnSurface.control(graphics, x, y, width, height, hover, focused,
			primary || selected, pressed, !button.active);

		int color = !button.active ? HgnPalette.DISABLED
			: hot || focused || primary || selected ? HgnPalette.CYAN_SOFT : HgnPalette.TEXT;
		int iconSize = dashboardSurface
			? Math.min(18, Math.max(12, height - 10))
			: Math.min(16, Math.max(10, height - 8));
		String label = button.getMessage().getString();
		if (label.isEmpty() && icon != null && icon != HgnIcon.NONE) {
			icon.draw(graphics, centerX - iconSize / 2,
				centerY - iconSize / 2, iconSize, color);
			return;
		}

		var font = Minecraft.getInstance().font;
		if (dashboardSurface && !leftAligned && !label.isEmpty()
			&& icon != null && icon != HgnIcon.NONE) {
			int featureIconSize = Math.min(14, Math.max(8, height / 2 - 2));
			int available = Math.max(1, width - 10);
			int needed = Math.max(1,
				online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, label));
			float emphasis = Math.max(0.54f, Math.min(1.0f, available / (float) needed));
			int lineHeight = Math.max(1,
				Math.round(online.horizongate.hgn.ui.dashboard.HgnTypography
					.lineHeight(font) * emphasis));
			int groupHeight = featureIconSize + 2 + lineHeight;
			int groupY = y + Math.max(2, (height - groupHeight) / 2);
			icon.draw(graphics, centerX - featureIconSize / 2,
				groupY, featureIconSize, color);
			online.horizongate.hgn.ui.dashboard.HgnTypography.centered(
				graphics, font, label, centerX,
				groupY + featureIconSize + 2, emphasis, color);
			return;
		}

		int textLeft = x + 9;
		if (icon != null && icon != HgnIcon.NONE) {
			icon.draw(graphics, x + 8, centerY - iconSize / 2, iconSize, color);
			textLeft += iconSize + 7;
		}

		if (label.isEmpty()) return;
		if (leftAligned) {
			int maxWidth = Math.max(1, right - textLeft - 12);
			if (!dashboardSurface) {
				String visible = HgnTypography.trim(font, label, maxWidth);
				HgnTypography.text(graphics, font, visible, textLeft,
					y + (height - HgnTypography.lineHeight(font)) / 2, color);
				return;
			}
			int needed = Math.max(1,
				online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, label));
			float emphasis = Math.min(1.0f, maxWidth / (float) needed);
			if (emphasis >= 0.58f) {
				int textY = y + Math.round((height
					- online.horizongate.hgn.ui.dashboard.HgnTypography
						.lineHeight(font) * emphasis) / 2.0f);
				online.horizongate.hgn.ui.dashboard.HgnTypography.text(
					graphics, font, label, textLeft, textY, emphasis, color);
			} else {
				String visible = online.horizongate.hgn.ui.dashboard.HgnTypography
					.trim(font, label, maxWidth);
				online.horizongate.hgn.ui.dashboard.HgnTypography.text(
					graphics, font, visible, textLeft,
					y + (height
						- online.horizongate.hgn.ui.dashboard.HgnTypography.lineHeight(font)) / 2,
					color);
			}
			return;
		}

		int available = Math.max(1, width - 14);
		int needed = Math.max(1, dashboardSurface
			? online.horizongate.hgn.ui.dashboard.HgnTypography.width(font, label)
			: HgnTypography.width(font, label));
		float fit = available / (float) needed;
		if (fit < 0.58f) {
			String visible = dashboardSurface
				? online.horizongate.hgn.ui.dashboard.HgnTypography.trim(font, label, available)
				: HgnTypography.trim(font, label, available);
			int compactY = y + (height - (dashboardSurface
				? online.horizongate.hgn.ui.dashboard.HgnTypography.lineHeight(font)
				: HgnTypography.lineHeight(font))) / 2;
			if (dashboardSurface) {
				online.horizongate.hgn.ui.dashboard.HgnTypography.centered(
					graphics, font, visible, centerX, compactY, color);
			} else {
				HgnTypography.centered(graphics, font, visible, centerX, compactY, color);
			}
			return;
		}
		float scale = Math.min(1.0f, fit);
		int lineHeight = dashboardSurface
			? online.horizongate.hgn.ui.dashboard.HgnTypography.lineHeight(font)
			: HgnTypography.lineHeight(font);
		int textY = y + Math.round((height - lineHeight * scale) / 2.0f);
		if (dashboardSurface) {
			online.horizongate.hgn.ui.dashboard.HgnTypography.centered(
				graphics, font, label, centerX, textY, scale, color);
		} else {
			HgnTypography.centered(graphics, font, label, centerX, textY, scale, color);
		}
	}

	private static boolean mouseDown() {
		long window = GLFW.glfwGetCurrentContext();
		return window != 0L
			&& GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}
}
