import java.util.List;
import online.horizongate.hgn.ui.HgnConnectionLayout;
import online.horizongate.hgn.ui.HgnRect;

/** Pure geometry only; actual connection/cancellation still requires runtime acceptance. */
public final class HgnConnectionLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int width : new int[] {320, 321, 427, 560, 836, 1280, 1920}) {
			for (int height = 180; height <= 1080; height += 9) {
				var layout = HgnConnectionLayout.resolve(width, height);
				check(layout.status().y() == height / 2 - 50, "Native status position changed");
				check(contains(new HgnRect(0, 0, width, height), layout.frame()), "Frame exceeds viewport");
				var content = List.of(layout.heading(), layout.status(), layout.activity(), layout.hint(), layout.cancel());
				for (int i = 0; i < content.size(); i++) {
					check(contains(layout.frame(), content.get(i)), "Content exceeds frame");
					for (int j = i + 1; j < content.size(); j++) check(!overlaps(content.get(i), content.get(j)), "Content overlaps native status or Cancel");
				}
				check(layout.cancel().height() == 20 && layout.cancel().width() >= 200, "Cancel target squeezed");
				cases++;
			}
		}
		for (int[] size : new int[][] {{319, 180}, {320, 179}, {0, 0}}) {
			check(!HgnConnectionLayout.supported(size[0], size[1]), "Unsupported fallback missing");
			try {
				HgnConnectionLayout.resolve(size[0], size[1]);
				throw new AssertionError("Invalid geometry accepted");
			} catch (IllegalArgumentException expected) { }
		}
		System.out.println("Connection: " + cases + " geometry cases passed; native status space and reachable Cancel retained; no network or input exercised.");
	}

	private static boolean contains(HgnRect outer, HgnRect inner) {
		return inner.width() > 0 && inner.height() > 0 && inner.x() >= outer.x() && inner.y() >= outer.y()
			&& inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
	}

	private static boolean overlaps(HgnRect a, HgnRect b) {
		return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}

	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
