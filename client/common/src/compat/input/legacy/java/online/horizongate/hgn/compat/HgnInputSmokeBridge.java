package online.horizongate.hgn.compat;
import net.minecraft.client.gui.components.AbstractWidget;

public final class HgnInputSmokeBridge {
	private HgnInputSmokeBridge() {}
	public static boolean click(AbstractWidget widget, double x, double y) {
		return widget.mouseClicked(x, y, 0);
	}
}
