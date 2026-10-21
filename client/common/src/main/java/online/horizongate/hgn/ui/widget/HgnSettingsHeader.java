package online.horizongate.hgn.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;

/** Shared HGN header for every game-options category. */
public final class HgnSettingsHeader extends AbstractWidget {
	public HgnSettingsHeader(int width, Component title) {
		super(8, 6, Math.max(1, width - 16), 36, title);
		active = false;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnSurface.panel(graphics, getX(), getY(), getWidth(), getHeight(), false);
		var font = Minecraft.getInstance().font;
		HgnTypography.text(graphics, font, "HGN / SETTINGS", getX() + 10, getY() + 5, 0.65f, HgnPalette.CYAN);
		HgnTypography.fitted(graphics, font, getMessage().getString(), getX() + 10, getY() + 17,
			getWidth() - 20, 1.0f, HgnPalette.TEXT);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
