package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Arranges original pause controls without replacing their actions, state or narration. */
public final class HgnPauseActions {
	private static final int PAGE_SIZE = 6;
	private final List<AbstractWidget> primary = new ArrayList<>();
	private final List<HgnRect> primaryBounds = new ArrayList<>();
	private final List<AbstractWidget> activities = new ArrayList<>();

	public HgnPauseActions(List<AbstractWidget> controls) {
		for (AbstractWidget control : controls) {
			String key = control.getMessage().getContents() instanceof TranslatableContents text
				? text.getKey() : "";
			HgnRect bounds = switch (key) {
				case "menu.returnToGame" -> new HgnRect(64, 148, 228, 42);
				case "menu.options" -> new HgnRect(64, 212, 228, 34);
				case "menu.returnToMenu", "menu.disconnect" -> new HgnRect(64, 377, 228, 34);
				default -> null;
			};
			if (bounds != null && !primaryBounds.contains(bounds)) {
				primary.add(control);
				primaryBounds.add(bounds);
			} else {
				activities.add(control);
			}
		}
	}

	public int pages() {
		return Math.max(1, (activities.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	public int clampPage(int page) {
		return Math.max(0, Math.min(page, pages() - 1));
	}

	public int compactPages(HgnPauseCompactLayout layout) {
		return layout.pages(activities.size());
	}

	public int clampCompactPage(HgnPauseCompactLayout layout, int page) {
		return layout.clampPage(page, activities.size());
	}

	public List<AbstractWidget> arrange(HgnDashboardLayout frame, int page) {
		List<AbstractWidget> visible = new ArrayList<>();
		for (int index = 0; index < primary.size(); index++) {
			place(primary.get(index), frame.rect(primaryBounds.get(index)));
			visible.add(primary.get(index));
		}
		int start = clampPage(page) * PAGE_SIZE;
		for (int index = start; index < Math.min(activities.size(), start + PAGE_SIZE); index++) {
			int cell = index - start;
			AbstractWidget control = activities.get(index);
			place(control, frame.rect(344 + (cell % 2) * 222, 184 + (cell / 2) * 48, 202, 34));
			visible.add(control);
		}
		return visible;
	}

	public List<AbstractWidget> arrangeCompact(HgnPauseCompactLayout layout, int page) {
		List<AbstractWidget> visible = new ArrayList<>();
		for (int index = 0; index < primary.size(); index++) {
			place(primary.get(index), layout.essential(index));
			visible.add(primary.get(index));
		}
		int start = clampCompactPage(layout, page) * layout.activitySlots();
		for (int index = start; index < Math.min(activities.size(), start + layout.activitySlots()); index++) {
			AbstractWidget control = activities.get(index);
			place(control, layout.activity(index - start));
			visible.add(control);
		}
		return visible;
	}

	private static void place(AbstractWidget control, HgnRect bounds) {
		control.setX(bounds.x());
		control.setY(bounds.y());
		control.setWidth(bounds.width());
		control.setHeight(bounds.height());
	}
}
