package online.horizongate.hgn.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.widget.HgnScaledEditBoxBase;

public final class HgnScaledEditBox extends HgnScaledEditBoxBase {
	public HgnScaledEditBox(Font font, int x, int y, int width, int height, Component message, float scale) {
		super(font, x, y, width, height, message, scale);
	}

	@Override
	public void onClick(double x, double y) {
		super.onClick(logicalX(x), logicalY(y));
	}
}
