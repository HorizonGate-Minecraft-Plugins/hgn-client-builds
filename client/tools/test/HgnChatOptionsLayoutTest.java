import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnChatOptionsLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Exercises every page without loading Minecraft or mutating any native chat preferences. */
public final class HgnChatOptionsLayoutTest {
	public static void main(String[] args) {
		assertFocusLookup();
		int[][] viewports = {{320, 180}, {427, 180}, {320, 239}, {320, 259}, {320, 260}, {320, 300},
			{579, 260}, {580, 260}, {596, 260}, {819, 299}, {820, 299}, {819, 300}, {820, 300},
			{836, 471}, {1280, 720}, {1920, 1080}, {3840, 2160}, {360, 640}};
		int combinations = 0;
		for (int[] size : viewports) {
			for (int count : new int[]{1, 8, 9, 18}) {
				for (int actionCount : new int[]{1, 2}) {
					boolean[] visited = new boolean[count];
					HgnChatOptionsLayout first = HgnChatOptionsLayout.resolve(size[0], size[1], count, 0, actionCount);
					HgnRect viewport = new HgnRect(0, 0, size[0], size[1]);
					for (int page = 0; page < first.pages(); page++) {
						HgnChatOptionsLayout layout = HgnChatOptionsLayout.resolve(size[0], size[1], count, page, actionCount);
						List<HgnRect> panels = List.of(layout.header(), layout.navigation(), layout.content(), layout.footer());
						for (int index = 0; index < panels.size(); index++) {
							inside(viewport, panels.get(index));
							for (int next = index + 1; next < panels.size(); next++) disjoint(panels.get(index), panels.get(next));
						}
						inside(layout.header(), layout.title());
						inside(layout.header(), layout.counter());
						inside(layout.navigation(), layout.visibility());
						check(layout.visibility().equals(first.visibility()), "Chat mode moved between pages");
						check(layout.visibility().width() >= 192 && layout.visibility().height() >= 20, "Chat mode target became too small");
						check(layout.tabs().size() == 2 && layout.tabs().equals(first.tabs()), "Chat groups moved or disappeared");
						if (layout.sideBySide()) {
							for (HgnRect tab : layout.tabs()) inside(layout.navigation(), tab);
							check(layout.visibility().y() >= layout.navigation().y() + 24, "Chat mode heading overlaps control");
						} else {
							check(layout.navigation().bottom() < layout.tabs().getFirst().y(), "Chat mode overlaps tabs");
							check(layout.tabs().getFirst().bottom() < layout.content().y(), "Tabs overlap preferences");
						}
						List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.counter(), layout.visibility(), layout.previous(), layout.next()));
						widgets.addAll(layout.tabs());
						widgets.addAll(layout.actions());
						widgets.addAll(layout.controls());
						inside(layout.footer(), layout.previous());
						inside(layout.footer(), layout.next());
						check(layout.actions().size() == actionCount, "Native footer actions disappeared");
						for (HgnRect action : layout.actions()) {
							inside(layout.footer(), action);
							check(action.width() >= 60 && action.height() >= 20, "Native footer target became too small");
						}
						for (int index = 0; index < layout.controls().size(); index++) {
							int nativeIndex = layout.firstControl() + index;
							check(!visited[nativeIndex], "Native chat preference appears on multiple pages");
							visited[nativeIndex] = true;
							check(first.pageContaining(nativeIndex) == page, "Resize chooses the wrong page for focused control");
							HgnRect control = layout.controls().get(index);
							inside(layout.content(), control);
							check(control.width() >= 250 && control.height() >= 20, "Native chat target became too small");
							check(control.contains(control.centerX(), control.centerY()), "Control center outside hit target");
							check(!control.contains(control.right(), control.centerY()), "Right hit boundary must remain exclusive");
						}
						for (int index = 0; index < widgets.size(); index++) {
							inside(viewport, widgets.get(index));
							for (int next = index + 1; next < widgets.size(); next++) disjoint(widgets.get(index), widgets.get(next));
						}
					}
					for (boolean found : visited) check(found, "A native chat preference is unreachable");
					check(HgnChatOptionsLayout.resolve(size[0], size[1], count, -1, actionCount).page() == 0, "Negative page not clamped");
					check(HgnChatOptionsLayout.resolve(size[0], size[1], count, 999, actionCount).page() == first.pages() - 1, "Final page not clamped");
					combinations++;
				}
			}
		}
		check(HgnChatOptionsLayout.resolve(320, 259, 8, 0, 1).compact(), "Short viewport needs compact spacing");
		check(!HgnChatOptionsLayout.resolve(320, 260, 8, 0, 1).compact(), "260px viewport should retain normal spacing");
		check(!HgnChatOptionsLayout.resolve(820, 299, 8, 0, 1).sideBySide(), "Short wide viewport needs stacked mode");
		check(HgnChatOptionsLayout.resolve(820, 300, 8, 0, 1).sideBySide(), "Wide viewport should use navigation lane");
		check(HgnChatOptionsLayout.resolve(320, 180, 8, 0, 1).pages() > 1, "Compact layout must page instead of shrinking widgets");
		check(HgnChatOptionsLayout.resolve(1280, 720, 9, 0, 1).pages() == 1, "Wide page should show the full conversation group");
		System.out.println("Chat layout: " + combinations + " viewport/roster/footer combinations passed across every page; immutable-list initial/cleared focus regression passed.");
	}

	private static void assertFocusLookup() {
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		List<Object> immutable = List.of(first, second, third);
		try {
			immutable.indexOf(null);
			throw new AssertionError("Fixture must reproduce immutable-list null lookup rejection");
		} catch (NullPointerException expected) {
			// This is the JDK behavior that caused Chat initialization to fail in the startup matrix.
		}
		check(HgnChatOptionsLayout.focusedControlIndex(immutable, null) == -1, "Initial focus must not query an immutable list with null");
		check(HgnChatOptionsLayout.focusedControlIndex(List.of(), null) == -1, "Cleared focus must not query an empty immutable list with null");
		check(HgnChatOptionsLayout.focusedControlIndex(immutable, first) == 0, "First native control lost its page");
		check(HgnChatOptionsLayout.focusedControlIndex(immutable, third) == 2, "Focused native control lost its index");
		check(HgnChatOptionsLayout.focusedControlIndex(immutable, new Object()) == -1, "Footer or group focus must not select a preference page");
		check(HgnChatOptionsLayout.focusedControlIndex(immutable, null) == -1, "Clearing retained focus must remain safe");
	}

	private static void inside(HgnRect outer, HgnRect inner) {
		check(inner.width() > 0 && inner.height() > 0 && outer.contains(inner.x(), inner.y())
			&& outer.contains(inner.right() - 1, inner.bottom() - 1), "Chat widget or panel outside bounds: " + inner + " in " + outer);
	}

	private static void disjoint(HgnRect a, HgnRect b) {
		check(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y(), "Chat widgets overlap");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
