package online.horizongate.hgn.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.widget.HgnScaledEditBoxBase;

public final class HgnScaledEditBox extends HgnScaledEditBoxBase {
	public HgnScaledEditBox(Font font, int x, int y, int width, int height, Component message, float scale) {
		super(font, x, y, width, height, message, scale);
	}

	private MouseButtonEvent logicalEvent(MouseButtonEvent event) {
		return new MouseButtonEvent(logicalX(event.x()), logicalY(event.y()), event.buttonInfo());
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		super.onClick(logicalEvent(event), doubleClick);
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
		super.onDrag(logicalEvent(event), deltaX / textScale, deltaY / textScale);
	}
}
