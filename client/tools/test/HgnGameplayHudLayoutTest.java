import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnGameplayHudLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnVanillaHotbarLayout;
import online.horizongate.hgn.ui.HgnVanillaHudSnapshot;

public final class HgnGameplayHudLayoutTest {
	public static void main(String[] args) {
		int cases = 0;
		for (int[] size : new int[][] {{214, 120}, {320, 180}, {427, 240}, {854, 480},
			{1280, 720}, {1920, 1080}, {1080, 1920}}) {
			for (boolean bosses : new boolean[] {false, true}) {
				for (boolean effects : new boolean[] {false, true}) {
					for (float scale : new float[] {0.78f, 0.90f, 1.0f}) {
					var layout = HgnGameplayHudLayout.resolve(size[0], size[1], bosses, effects, scale);
					if (layout.context().height() != (int) Math.ceil(46 * scale)
						|| layout.performance().height() != (int) Math.ceil(46 * scale)) throw new AssertionError(layout);
					var left = layout.performance();
					var right = layout.context();
					if (left.right() >= right.x() || right.right() > size[0]
						|| layout.raceTop() <= left.bottom()) throw new AssertionError(layout);
					if (layout.readoutsVisible() && (left.bottom() > size[1] - 60
						|| right.bottom() > size[1] - 60 || bosses && left.y() < size[1] / 3
						|| effects && right.y() < 60)) throw new AssertionError(layout);
					cases++;
					}
				}
			}
		}
		System.out.println("HUD layout: " + cases + " cases passed");
		verifyVanillaHudFoundation();
		HgnRaceHudLayoutTest.main(args);
	}

	private static void verifyVanillaHudFoundation() {
		var items = new ArrayList<>(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8"));
		var snapshot = new HgnVanillaHudSnapshot<>(items, "shield", 18,
			false, false, true, true, true, Float.NaN,
			List.of(-1.0f, 0.25f, 2.0f, Float.NaN, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
			2.0f, true, -3, Float.POSITIVE_INFINITY,
			new HgnVanillaHudSnapshot.Vitals(-1.0f, 20.0f, Float.NaN,
				-4, 24, -1.0f, -3, 300, 5.0f, 10.0f));
		items.set(0, "changed");
		if (!snapshot.hotbar().getFirst().equals("0") || snapshot.selectedSlot() != 8
			|| snapshot.attackStrength() != 0.0f || snapshot.offhandCooldown() != 1.0f
			|| snapshot.itemCooldowns().get(0) != 0.0f
			|| snapshot.itemCooldowns().get(1) != 0.25f
			|| snapshot.itemCooldowns().get(2) != 1.0f
			|| snapshot.itemCooldowns().get(3) != 0.0f
			|| snapshot.experienceLevel() != 0 || snapshot.experienceProgress() != 0.0f
			|| snapshot.vitals().health() != 0.0f || snapshot.vitals().absorption() != 0.0f
			|| snapshot.vitals().armor() != 0 || snapshot.vitals().food() != 24
			|| snapshot.vitals().air() != 0 || snapshot.vitals().maxAir() != 300) {
			throw new AssertionError("Vanilla HUD snapshot lost or fabricated live values");
		}
		for (int[] size : new int[][] {{268, 85}, {320, 180}, {427, 240}, {854, 480},
			{1920, 1080}, {1080, 1920}}) {
			var layout = HgnVanillaHotbarLayout.resolve(size[0], size[1], snapshot)
				.orElseThrow(() -> new AssertionError("Expected usable HGN hotbar geometry"));
			within(layout.frame(), size[0], size[1]);
			if (layout.frame().bottom() != size[1] - 3 || layout.slots().size() != 9) {
				throw new AssertionError(layout);
			}
			for (int index = 0; index < layout.slots().size(); index++) {
				HgnRect slot = layout.slots().get(index);
				if (slot.x() < layout.frame().x() || slot.right() > layout.frame().right()
					|| slot.y() < layout.frame().y() || slot.bottom() > layout.frame().bottom()) {
					throw new AssertionError("Hotbar slot escaped the frame: " + slot);
				}
				if (index > 0 && layout.slots().get(index - 1).right() >= slot.x()) {
					throw new AssertionError("Hotbar slots overlap");
				}
			}
			HgnRect selected = layout.slots().get(8);
			if (layout.selected().x() > selected.x() || layout.selected().right() < selected.right()) {
				throw new AssertionError("Selected frame does not contain the native selected slot");
			}
			layout.offhand().ifPresent(rect -> {
				within(rect, size[0], size[1]);
				if (rect.right() >= layout.frame().x()) throw new AssertionError("Offhand overlaps hotbar");
			});
			layout.attack().ifPresent(rect -> {
				within(rect, size[0], size[1]);
				if (rect.x() <= layout.frame().right()) throw new AssertionError("Attack meter overlaps hotbar");
			});
			layout.experience().ifPresent(rect -> {
				within(rect, size[0], size[1]);
				if (rect.bottom() >= layout.frame().y()) throw new AssertionError("XP bar overlaps hotbar");
			});
			layout.level().ifPresent(rect -> {
				within(rect, size[0], size[1]);
				if (rect.bottom() >= layout.experience().orElseThrow().y()) {
					throw new AssertionError("Level label overlaps XP bar");
				}
			});
		}
		var rightOffhand = snapshot(false, false, true, false, true, false);
		var right = HgnVanillaHotbarLayout.resolve(268, 85, rightOffhand).orElseThrow();
		if (right.offhand().orElseThrow().x() <= right.frame().right()
			|| right.experience().isPresent() || right.level().isPresent()
			|| right.attack().orElseThrow().right() >= right.frame().x()) {
			throw new AssertionError("Optional native HUD lanes were not preserved");
		}
		if (HgnVanillaHotbarLayout.resolve(427, 240, snapshot(true, false, true, true, true, true)).isPresent()
			|| HgnVanillaHotbarLayout.resolve(427, 240, snapshot(false, true, true, true, true, true)).isPresent()
			|| HgnVanillaHotbarLayout.resolve(267, 240, snapshot).isPresent()
			|| HgnVanillaHotbarLayout.resolve(427, 84, snapshot).isPresent()) {
			throw new AssertionError("Hidden, spectator or compact HUD must retain native fallback");
		}
		System.out.println("Vanilla HUD snapshot and planned hotbar geometry passed");
	}

	private static HgnVanillaHudSnapshot<String> snapshot(boolean spectator, boolean hudHidden,
		boolean offhandVisible, boolean offhandOnLeft,
		boolean attackIndicatorVisible, boolean experienceVisible) {
		return new HgnVanillaHudSnapshot<>(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8"),
			"offhand", 0, spectator, hudHidden, offhandVisible, offhandOnLeft, attackIndicatorVisible,
			1.0f, List.of(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
			0.0f, experienceVisible, 30, 0.5f,
			new HgnVanillaHudSnapshot.Vitals(20.0f, 20.0f, 0.0f,
				20, 20, 5.0f, 300, 300, 0.0f, 0.0f));
	}

	private static void within(HgnRect rect, int width, int height) {
		if (rect.x() < 0 || rect.y() < 0 || rect.right() > width || rect.bottom() > height) {
			throw new AssertionError("HUD layout escaped screen: " + rect);
		}
	}
}
