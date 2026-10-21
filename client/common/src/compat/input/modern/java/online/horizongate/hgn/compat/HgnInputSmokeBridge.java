package online.horizongate.hgn.compat;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class HgnInputSmokeBridge {
	private HgnInputSmokeBridge() {}
	public static boolean click(AbstractWidget widget, double x, double y) {
		return widget.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
	}
}
