package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnInputSmokeBridge;

/** Checks the pause arranger with real widgets without joining or altering a world. */
public final class HgnPauseMenuSmokeCheck {
	private HgnPauseMenuSmokeCheck() {
	}

	public static void run() {
		for (int[] size : new int[][] {{320, 180}, {427, 240}, {836, 471}, {1280, 720}}) {
			for (String exit : List.of("menu.returnToMenu", "menu.disconnect")) {
				assertActions(size[0], size[1], exit);
			}
		}
		HgnClient.LOGGER.info("HGN_PAUSE_LAYOUT_SMOKE_OK: native widget callbacks, disabled states, compact hit targets, bounds and overflow pages preserved; live-world pause flow remains a separate check");
	}

	private static void assertActions(int width, int height, String exit) {
		List<AbstractWidget> controls = new ArrayList<>();
		AtomicInteger clicks = new AtomicInteger();
		for (String label : List.of("menu.returnToGame", "gui.advancements", "gui.stats",
			"menu.sendFeedback", "menu.reportBugs", "menu.options", "menu.shareToLan", exit)) {
			controls.add(Button.builder(Component.translatable(label), ignored -> clicks.incrementAndGet()).build());
		}
		AbstractWidget disabled = controls.get(5);
		disabled.active = false;
		for (int index = 0; index < 10; index++) {
			controls.add(Button.builder(Component.literal("Server action " + index), ignored -> clicks.incrementAndGet()).build());
		}
		HgnPauseActions actions = new HgnPauseActions(controls);
		if (actions.pages() != 3 || actions.clampPage(-1) != 0 || actions.clampPage(99) != 2) {
			throw new AssertionError("Pause action pagination is incorrect");
		}
		HgnPauseCompactLayout compact = HgnPauseCompactLayout.needed(width, height)
			? HgnPauseCompactLayout.resolve(width, height) : null;
		int pages = compact == null ? actions.pages() : actions.compactPages(compact);
		if (compact != null && (actions.clampCompactPage(compact, -1) != 0
			|| actions.clampCompactPage(compact, 99) != pages - 1)) {
			throw new AssertionError("Compact pause action pagination is incorrect");
		}
		Set<AbstractWidget> reached = new HashSet<>();
		for (int page = 0; page < pages; page++) {
			List<AbstractWidget> visible = compact == null
				? actions.arrange(HgnDashboardLayout.resolve(width, height), page)
				: actions.arrangeCompact(compact, page);
			for (int index = 0; index < visible.size(); index++) {
				AbstractWidget widget = visible.get(index);
				HgnRect bounds = bounds(widget);
				if (bounds.x() < 0 || bounds.y() < 0 || bounds.right() > width || bounds.bottom() > height) {
					throw new AssertionError("Pause action lies outside viewport");
				}
				if (compact != null && (bounds.height() < 20 || bounds.width() < 100)) {
					throw new AssertionError("Compact pause action became too small");
				}
				for (int other = index + 1; other < visible.size(); other++) {
					HgnRect next = bounds(visible.get(other));
					if (bounds.x() < next.right() && bounds.right() > next.x()
						&& bounds.y() < next.bottom() && bounds.bottom() > next.y()) {
						throw new AssertionError("Pause actions overlap");
					}
				}
				if (reached.add(widget)) HgnInputSmokeBridge.click(widget, bounds.centerX(), bounds.centerY());
			}
		}
		if (!reached.equals(new HashSet<>(controls)) || clicks.get() != controls.size() - 1 || disabled.active) {
			throw new AssertionError("Pause actions, callbacks or disabled state changed");
		}
		List<AbstractWidget> resized = compact == null
			? actions.arrange(HgnDashboardLayout.resolve(width + 80, height + 40), 0)
			: actions.arrangeCompact(HgnPauseCompactLayout.resolve(width + 80, height + 40), 0);
		if (!resized.contains(controls.get(0)) || !resized.contains(disabled) || disabled.active) {
			throw new AssertionError("Pause resize replaced a native control or its state");
		}
	}

	private static HgnRect bounds(AbstractWidget widget) {
		return new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}
}
