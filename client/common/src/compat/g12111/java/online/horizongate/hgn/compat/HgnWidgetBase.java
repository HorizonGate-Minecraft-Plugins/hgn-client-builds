package online.horizongate.hgn.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Base for HGN-styled buttons. 1.21.11 made AbstractButton.renderWidget final
 * and split the drawing into a new renderContents hook, so this generation
 * overrides that instead and forwards to the same shared paint method.
 */
public abstract class HgnWidgetBase extends Button {
	protected HgnWidgetBase(int x, int y, int w, int h, Component message, OnPress onPress) {
		super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		paint(g, mouseX, mouseY, partialTick);
	}

	protected abstract void paint(GuiGraphics g, int mouseX, int mouseY, float partialTick);
}
