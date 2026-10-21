package online.horizongate.hgn.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnDraw;

/** Native editing with matching text, clipping and pointer coordinates at reference scale.
 * Keep the native text formatter: EditBox cursor and scrolling measure the default font.
 */
public abstract class HgnScaledEditBoxBase extends EditBox {
	protected final float textScale;
	private boolean logicalBounds;

	protected HgnScaledEditBoxBase(Font font, int x, int y, int width, int height,
		Component message, float scale) {
		super(font, x, y, width, height, message);
		textScale = Math.max(0.1f, scale);
		setBordered(false);
	}

	protected double logicalX(double x) { return getX() + (x - getX()) / textScale; }
	protected double logicalY(double y) { return getY() + (y - getY()) / textScale; }

	@Override
	public int getInnerWidth() {
		int width = super.getInnerWidth();
		return logicalBounds || textScale == 0 ? width : Math.max(1, (int) (width / textScale));
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int width = getWidth();
		int height = getHeight();
		graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
		HgnDraw.beginScaleAt(graphics, textScale, getX(), getY());
		logicalBounds = true;
		try {
			setWidth(Math.max(1, (int) (width / textScale)));
			setHeight(Math.max(1, (int) (height / textScale)));
			super.renderWidget(graphics, (int) logicalX(mouseX), (int) logicalY(mouseY), partialTick);
		} finally {
			setWidth(width);
			setHeight(height);
			logicalBounds = false;
			HgnDraw.endScale(graphics);
			graphics.disableScissor();
		}
	}
}
