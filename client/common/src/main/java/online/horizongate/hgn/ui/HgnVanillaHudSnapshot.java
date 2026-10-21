package online.horizongate.hgn.ui;

import java.util.List;
import java.util.Objects;

/** Structurally immutable, version-neutral input; live item objects are not copied. */
public record HgnVanillaHudSnapshot<T>(List<T> hotbar, T offhand, int selectedSlot,
	boolean spectator, boolean hudHidden, boolean offhandVisible, boolean offhandOnLeft,
	boolean attackIndicatorVisible,
	float attackStrength, List<Float> itemCooldowns, float offhandCooldown,
	boolean experienceVisible, int experienceLevel, float experienceProgress,
	Vitals vitals) {
	public static final int HOTBAR_SLOTS = 9;

	public HgnVanillaHudSnapshot {
		hotbar = List.copyOf(hotbar);
		itemCooldowns = List.copyOf(itemCooldowns);
		if (hotbar.size() != HOTBAR_SLOTS || itemCooldowns.size() != HOTBAR_SLOTS) {
			throw new IllegalArgumentException("Hotbar items and cooldowns must each have nine slots");
		}
		Objects.requireNonNull(offhand, "offhand");
		Objects.requireNonNull(vitals, "vitals");
		selectedSlot = Math.max(0, Math.min(HOTBAR_SLOTS - 1, selectedSlot));
		attackStrength = unit(attackStrength);
		itemCooldowns = itemCooldowns.stream().map(HgnVanillaHudSnapshot::unit).toList();
		offhandCooldown = unit(offhandCooldown);
		experienceLevel = Math.max(0, experienceLevel);
		experienceProgress = unit(experienceProgress);
	}

	private static float unit(float value) {
		return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
	}

	/** Vanilla-derived survival values; a renderer must keep vehicle and air states visible. */
	public record Vitals(float health, float maxHealth, float absorption,
		int armor, int food, float saturation, int air, int maxAir,
		float vehicleHealth, float vehicleMaxHealth) {
		public Vitals {
			health = nonnegative(health);
			maxHealth = nonnegative(maxHealth);
			absorption = nonnegative(absorption);
			armor = Math.max(0, armor);
			food = Math.max(0, food);
			saturation = nonnegative(saturation);
			air = Math.max(0, air);
			maxAir = Math.max(0, maxAir);
			vehicleHealth = nonnegative(vehicleHealth);
			vehicleMaxHealth = nonnegative(vehicleMaxHealth);
		}

		private static float nonnegative(float value) {
			return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
		}
	}
}
