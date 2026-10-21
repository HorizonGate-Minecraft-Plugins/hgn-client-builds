package online.horizongate.hgn.ui;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Presentation policy for the authenticated cosmetics catalogue. */
public final class HgnCosmeticsCatalogueFilter {
	private static final List<String> CATEGORIES = List.of("ALL", "SKINS", "HATS", "CAPES",
		"LIMITED", "WINGS", "BACK ITEMS", "EFFECTS", "EMOTES", "PETS", "BUNDLES");

	private HgnCosmeticsCatalogueFilter() {
	}

	public static List<String> categories() {
		return CATEGORIES;
	}

	public static boolean visible(boolean active, boolean owned, long price, Set<String> tags) {
		return active && (owned || price == 0L && !hasTag(tags, "limited")
			&& !hasTag(tags, "entitlement_only"));
	}

	public static boolean matchesCategory(String itemCategory, String equipmentSlot,
		Set<String> tags, String selectedCategory) {
		if ("ALL".equals(selectedCategory)) return true;
		if ("LIMITED".equals(selectedCategory)) return hasTag(tags, "limited");
		if ("CAPES".equals(selectedCategory) && "CAPE".equalsIgnoreCase(equipmentSlot)) return true;
		String category = itemCategory == null ? "" : itemCategory.toUpperCase(Locale.ROOT)
			.replace('_', ' ');
		if (category.equals("ANIMATED EFFECTS")) category = "EFFECTS";
		return category.equals(selectedCategory);
	}

	private static boolean hasTag(Set<String> tags, String wanted) {
		return tags != null && tags.stream().anyMatch(tag -> wanted.equalsIgnoreCase(tag));
	}
}
