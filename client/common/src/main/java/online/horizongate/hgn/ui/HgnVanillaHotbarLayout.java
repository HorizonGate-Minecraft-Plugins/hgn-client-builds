package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Planned HGN hotbar geometry; empty results require a future renderer to defer to native HUD. */
public record HgnVanillaHotbarLayout(HgnRect frame, List<HgnRect> slots, HgnRect selected,
	Optional<HgnRect> offhand, Optional<HgnRect> experience, Optional<HgnRect> level,
	Optional<HgnRect> attack) {
	private static final int FRAME_WIDTH = 204;
	private static final int FRAME_HEIGHT = 28;
	private static final int MIN_WIDTH = 268;
	private static final int MIN_HEIGHT = 85;

	public HgnVanillaHotbarLayout {
		slots = List.copyOf(slots);
		if (slots.size() != HgnVanillaHudSnapshot.HOTBAR_SLOTS) {
			throw new IllegalArgumentException("A hotbar layout must contain nine slots");
		}
	}

	public static Optional<HgnVanillaHotbarLayout> resolve(int width, int height,
		HgnVanillaHudSnapshot<?> state) {
		if (state.spectator() || state.hudHidden() || width < MIN_WIDTH || height < MIN_HEIGHT) {
			return Optional.empty();
		}
		int x = (width - FRAME_WIDTH) / 2;
		int y = height - FRAME_HEIGHT - 3;
		HgnRect frame = new HgnRect(x, y, FRAME_WIDTH, FRAME_HEIGHT);
		List<HgnRect> slots = new ArrayList<>(HgnVanillaHudSnapshot.HOTBAR_SLOTS);
		for (int index = 0; index < HgnVanillaHudSnapshot.HOTBAR_SLOTS; index++) {
			slots.add(new HgnRect(x + 12 + index * 20, y + 5, 18, 18));
		}
		HgnRect selectedSlot = slots.get(state.selectedSlot());
		HgnRect selected = new HgnRect(selectedSlot.x() - 2, selectedSlot.y() - 2, 22, 22);
		Optional<HgnRect> offhand = state.offhandVisible()
			? Optional.of(new HgnRect(state.offhandOnLeft() ? x - 31 : frame.right() + 3,
				y + 2, 28, 24)) : Optional.empty();
		Optional<HgnRect> experience = state.experienceVisible()
			? Optional.of(new HgnRect(x + 12, y - 6, 180, 2)) : Optional.empty();
		Optional<HgnRect> level = state.experienceVisible()
			? Optional.of(new HgnRect(width / 2 - 16, y - 20, 32, 10)) : Optional.empty();
		Optional<HgnRect> attack = state.attackIndicatorVisible()
			? Optional.of(new HgnRect(state.offhandOnLeft() ? frame.right() + 8 : x - 13,
				y + 4, 5, 20)) : Optional.empty();
		return Optional.of(new HgnVanillaHotbarLayout(frame, slots, selected,
			offhand, experience, level, attack));
	}
}
