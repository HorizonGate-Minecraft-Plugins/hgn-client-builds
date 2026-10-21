package online.horizongate.hgn.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Button renderer bridge for Minecraft 1.21.9-1.21.10. */
public abstract class HgnWidgetBase extends Button {
	protected HgnWidgetBase(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		paint(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
