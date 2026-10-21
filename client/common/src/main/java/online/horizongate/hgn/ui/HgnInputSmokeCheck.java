package online.horizongate.hgn.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnInputSmokeBridge;
import online.horizongate.hgn.compat.HgnScaledEditBox;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Exercises native widget dispatch and text cursor placement; no captures or live actions. */
public final class HgnInputSmokeCheck {
	private HgnInputSmokeCheck() {}

	public static void run() {
		for (int[] viewport : new int[][] {{320, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			var layout = HgnDashboardLayout.resolve(viewport[0], viewport[1]);
			for (int row = 0; row < 6; row++) {
				var bounds = layout.rect(28, 159 + row * 41, 160, 34);
				int[] clicks = {0};
				var button = HgnButton.hitTarget(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
					Component.literal("Input test"), ignored -> clicks[0]++);
				for (double[] point : new double[][] {{bounds.x(), bounds.y()},
					{bounds.right() - 0.1, bounds.bottom() - 0.1}, {bounds.centerX(), bounds.centerY()}}) {
					if (!HgnInputSmokeBridge.click(button, point[0], point[1])) throw new AssertionError("Visible button missed click");
				}
				if (clicks[0] != 3 || HgnInputSmokeBridge.click(button, bounds.right(), bounds.centerY())
					|| HgnInputSmokeBridge.click(button, bounds.x() - 0.1, bounds.centerY())) {
					throw new AssertionError("Button accepted a click outside its painted bounds");
				}
				button.active = false;
				if (HgnInputSmokeBridge.click(button, bounds.centerX(), bounds.centerY())) throw new AssertionError("Disabled button clicked");
			}
		}
		var font = Minecraft.getInstance().font;
		for (float scale : new float[] {0.25f, 0.5f, 1.0f, 2.0f}) {
			var field = new HgnScaledEditBox(font, 20, 20, 150, 20, Component.literal("Search"), scale);
			field.setValue("123456789");
			field.setFocused(true);
			HgnInputSmokeBridge.click(field, 20 + font.width("123") * scale + 0.01, 21);
			if (field.getCursorPosition() != 3) throw new AssertionError("Scaled search cursor does not match click: " + scale);
		}
	}
}
