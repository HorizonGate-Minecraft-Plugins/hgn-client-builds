import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnEditWorldLayout;
import online.horizongate.hgn.ui.HgnRect;

public final class HgnEditWorldLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {320, 299}, {320, 300},
			{799, 359}, {800, 359}, {799, 360}, {800, 360}, {836, 471}, {1280, 720}, {1920, 1080}}) {
			for (int count : new int[] {0, 5, 6, 7, 25, 40}) {
				var initial = HgnEditWorldLayout.resolve(size[0], size[1], count, 0);
				boolean[] visited = new boolean[count];
				for (int page = 0; page < initial.pages(); page++) {
					var layout = HgnEditWorldLayout.resolve(size[0], size[1], count, page);
					List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.nameLabel(), layout.name(), layout.save(), layout.cancel()));
					if (layout.pages() > 1) widgets.addAll(List.of(layout.previous(), layout.next()));
					for (var slot : layout.actions()) {
						check(!visited[slot.index()], "Repeated action");
						visited[slot.index()] = true;
						widgets.add(slot.bounds());
						check(slot.bounds().height() >= 20, "Native action too short");
						check(slot.bounds().width() >= 140, "Native action too narrow");
						check(slot.index() / layout.capacity() == page, "Focused action mapped to wrong page");
					}
					for (int index = 0; index < widgets.size(); index++) {
						HgnRect rect = widgets.get(index);
						check(rect.width() > 0 && rect.height() > 0 && rect.x() >= 0 && rect.y() >= 0
							&& rect.right() <= size[0] && rect.bottom() <= size[1], "Outside viewport " + size[0] + "x" + size[1]);
						for (int next = index + 1; next < widgets.size(); next++) check(!overlap(rect, widgets.get(next)), "Widget overlap");
					}
					check(layout.name().equals(initial.name()) && layout.save().equals(initial.save())
						&& layout.cancel().equals(initial.cancel()), "Name or confirmation paged away");
				}
				for (boolean found : visited) check(found, "Action lost");
				check(HgnEditWorldLayout.resolve(size[0], size[1], count, -1).page() == 0, "Negative page");
				check(HgnEditWorldLayout.resolve(size[0], size[1], count, 999).page() == initial.pages() - 1, "Unclamped page");
				if (count == 5 && size[1] < 300) check(initial.pages() == 1, "Compact native five actions should fit without paging");
				cases++;
			}
		}
		check(HgnEditWorldLayout.focusedIndex(List.of(new Object()), null) == -1, "Null focus must not query immutable list");
		System.out.println("Edit World layout: " + cases + " viewport/roster cases passed, native controls fit and extra controls remain reachable.");
	}

	private static boolean overlap(HgnRect a, HgnRect b) {
		return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
	}

	private static void check(boolean success, String message) {
		if (!success) throw new AssertionError(message);
	}
}
