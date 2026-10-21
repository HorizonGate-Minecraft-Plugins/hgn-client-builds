import java.util.List;
import java.util.Set;
import online.horizongate.hgn.ui.HgnCosmeticsCatalogueFilter;
import online.horizongate.hgn.ui.HgnCosmeticsLayout;
import online.horizongate.hgn.ui.HgnCosmeticsSelection;
import online.horizongate.hgn.ui.HgnRect;

/** Keeps the reference catalogue readable and clickable down to 320x180. */
public final class HgnCosmeticsLayoutTest {
	public static void main(String[] args) {
		for (int[] size : new int[][]{{320, 180}, {320, 240}, {426, 240}, {500, 260},
			{550, 350}, {639, 359}, {640, 360}, {800, 420}, {836, 471}, {1080, 500}, {1280, 720},
			{1672, 941}, {1920, 1080}}) {
			HgnCosmeticsLayout layout = HgnCosmeticsLayout.resolve(size[0], size[1]);
			HgnRect screen = new HgnRect(0, 0, size[0], size[1]);
			inside(screen, layout.rail());
			inside(screen, layout.catalogue());
			inside(screen, layout.profile());
			check(layout.rail().right() < layout.catalogue().x(), "Rail overlaps catalogue");
			if (layout.wideStage()) {
				inside(screen, layout.header());
				inside(screen, layout.stage());
				check(layout.header().bottom() <= layout.rail().y(),
					"Wide header overlaps content");
				check(layout.rail().right() < layout.stage().x()
					&& layout.stage().right() < layout.catalogue().x(),
					"Wide player stage must sit between rail and catalogue");
				check(layout.back().bottom() < layout.category(0).y(),
					"Wide back control overlaps categories");
				check(layout.columns() >= 4, "Wide catalogue needs a dense item grid");
			}
			if (layout.compact()) {
				check(layout.catalogue().equals(layout.profile()),
					"Compact details must replace the catalogue in one bounded pane");
			} else {
				check(layout.catalogue().right() < layout.profile().x(),
					"Catalogue overlaps profile");
			}
			for (HgnRect control : new HgnRect[]{layout.featured(), layout.search(),
				layout.rarity(), layout.sort(), layout.owned(), layout.cards(),
				layout.previous(), layout.next()}) inside(layout.catalogue(), control);
			check(layout.featured().bottom() < layout.search().y(), "Featured panel overlaps search");
			check(layout.search().bottom() < layout.cards().y(), "Search overlaps cards");
			check(layout.cards().bottom() < layout.previous().y(), "Cards overlap paging");
			for (int index = 0; index < layout.columns() * layout.rows(); index++) {
				HgnRect card = layout.card(index);
				inside(layout.cards(), card);
				check(card.width() >= 65 && card.height() >= 55, "Card became too small");
				if (index % layout.columns() < layout.columns() - 1) {
					check(card.right() < layout.card(index + 1).x(), "Card columns overlap");
				}
			}
			for (int index = 0; index < layout.categoryPageSize(); index++) {
				HgnRect category = layout.category(index);
				inside(layout.rail(), category);
				check(category.height() >= 10, "Category is too short to click");
			}
			int categoryCount = HgnCosmeticsCatalogueFilter.categories().size();
			check(layout.compact() ? layout.categoryPageSize() < categoryCount
				: layout.categoryPageSize() >= categoryCount,
				"Every category needs a reachable rail page");
			int lastPage = (categoryCount - 1) / layout.categoryPageSize();
			int lastRow = categoryCount - 1 - lastPage * layout.categoryPageSize();
			inside(layout.rail(), layout.category(lastRow));
			inside(layout.rail(), layout.back());
			if (layout.compact()) {
				inside(layout.rail(), layout.categoryPager());
				inside(layout.rail(), layout.profileShortcut());
				inside(layout.emptyState(), layout.emptyAction());
				check(layout.category(layout.categoryPageSize() - 1).bottom()
					< layout.profileShortcut().y(), "Categories overlap profile shortcut");
				check(layout.profileShortcut().bottom() < layout.categoryPager().y(),
					"Profile shortcut overlaps category paging");
				check(layout.categoryPager().bottom() < layout.back().y(),
					"Category paging overlaps back control");
			}
			inside(layout.wideStage() ? layout.stage() : layout.profile(), layout.preview());
			inside(layout.preview(), layout.previewModel());
			check(layout.previewModel().bottom() <= layout.preview().bottom() - 18,
				"Player model overlaps its preview footer label");
			if (layout.wideStage()) {
				check(layout.previewModel().bottom() <= layout.stage().bottom() - 34,
					"Wide-stage player model overlaps its preview caption");
				inside(layout.wallet(), layout.walletTokenLabel());
				inside(layout.wallet(), layout.walletTokenValue());
				check(layout.walletTokenLabel().right() < layout.walletTokenValue().x(),
					"Wallet token label overlaps its value");
			}
			for (HgnRect detail : new HgnRect[]{layout.selection(), layout.equipped(),
				layout.wallet()}) inside(layout.profile(), detail);
			if (!layout.wideStage()) check(layout.preview().bottom() < layout.wallet().y(),
				"Preview overlaps the wallet");
			else {
				inside(layout.profile(), layout.stats());
				check(layout.selection().y() >= layout.profile().y() + 41,
					"Wide selected item overlaps the account card");
				check(layout.selection().bottom() < layout.equipped().y()
				&& layout.equipped().bottom() < layout.stats().y()
				&& layout.stats().bottom() < layout.wallet().y(),
				"Wide account sections overlap");
			}
		}
		check(HgnCosmeticsLayout.resolve(320, 180).compact(), "320x180 needs compact details");
		check(HgnCosmeticsLayout.resolve(426, 240).compact(),
			"1280x720 at GUI scale 3 needs compact Cosmetics, not the wide stage");
		check(!HgnCosmeticsLayout.resolve(550, 350).compact(),
			"550x350 should show all three reference columns");
		check(!HgnCosmeticsLayout.resolve(639, 359).wideStage(),
			"A viewport below the reference breakpoint needs the three-column layout");
		HgnCosmeticsLayout denseReference = HgnCosmeticsLayout.resolve(640, 360);
		check(denseReference.wideStage(),
			"A 1280x720 GUI-scale-2 view needs the reference stage and header");
		check(denseReference.denseReference(),
			"A 1280x720 GUI-scale-2 view needs shortened text within its narrow rail");
		check(denseReference.shortWalletLabels(),
			"A narrow reference wallet needs short non-overlapping token labels");
		check(denseReference.header().height() < 80,
			"The 1280x720 reference header must fit the available height");
		check(denseReference.columns() == 4 && denseReference.rows() == 1,
			"The dense reference needs a single readable row of four cards");
		check(denseReference.category(0).height() >= 20,
			"The dense reference category rail needs readable hit targets");
		check(HgnCosmeticsLayout.resolve(836, 471).wideStage(),
			"A reference-sized logical viewport needs the player stage");
		HgnCosmeticsLayout reference = HgnCosmeticsLayout.resolve(836, 471);
		check(!reference.denseReference(),
			"Reference-sized rail must retain full category labels");
		check(reference.category(0).width() - 40 >= 75,
			"Reference-sized category labels need enough room beside their icons");
		check(reference.stage().width() >= reference.rail().width() + 55
			&& reference.stage().width() >= reference.profile().width() + 35,
			"The reference-sized player preview must be the prominent side stage");
		check(!reference.shortWalletLabels(),
			"Reference-sized wallet must retain its full token labels");
		check(reference.columns() == 5 && reference.rows() == 2,
			"The reference-sized catalogue needs five columns and two rows");
		check(reference.card(0).width() >= 65,
			"A wider player stage must not squeeze verified catalogue cards");
		check(reference.equipped().height() >= 130
			&& (reference.equipped().height() - 20) / 23 >= 5,
			"The reference-sized equipped rail must fit multiple rows and overflow status");
		check(reference.category(0).height() >= 26,
			"Reference-sized category rows need room for readable subtitles");
		check(HgnCosmeticsLayout.walletTextScale(0.96f, 55, 110) == 0.5f,
			"Long balances must fit their lane without changing the value");
		selectionTransitions();
		catalogueFilters();
		System.out.println("Cosmetics layout: thirteen viewport sizes passed.");
	}

	private static void selectionTransitions() {
		Card skin = new Card("skin", "Old skin");
		Card wings = new Card("wings", "Old wings");
		Card hat = new Card("hat", "Old hat");
		Card selected = HgnCosmeticsSelection.visibleOrFirst(List.of(skin, wings), null, Card::id);
		check(selected == skin, "Initial catalogue selection must be its first card");
		selected = HgnCosmeticsSelection.visibleOrFirst(List.of(wings), selected, Card::id);
		check(selected == wings, "Category change must not preview a hidden item");
		selected = HgnCosmeticsSelection.visibleOrFirst(List.of(hat), selected, Card::id);
		check(selected == hat, "Search change must not preview a hidden item");
		selected = HgnCosmeticsSelection.visibleOrFirst(List.of(skin), selected, Card::id);
		check(selected == skin, "Page change must not preview a card from the old page");
		Card updatedSkin = new Card("skin", "Updated skin");
		selected = HgnCosmeticsSelection.visibleOrFirst(List.of(updatedSkin), selected, Card::id);
		check(selected == updatedSkin, "A refreshed snapshot must use the new item definition");
		selected = HgnCosmeticsSelection.visibleOrFirst(List.<Card>of(), selected, Card::id);
		check(selected == null, "An empty result must clear the preview selection");
		check(HgnCosmeticsSelection.matchingOrNone(List.of(skin), wings, Card::id) == null,
			"Compact details must clear a selection hidden by a refreshed snapshot");
		check(HgnCosmeticsSelection.matchingOrNone(List.of(updatedSkin), skin, Card::id)
			== updatedSkin, "Compact details must use a refreshed visible definition");
	}

	private static void catalogueFilters() {
		Set<String> plain = Set.of("supplied_model");
		Set<String> limited = Set.of("LIMITED", "supplied_model");
		Set<String> entitlementOnly = Set.of("entitlement_only", "supplied_model");
		check(HgnCosmeticsCatalogueFilter.categories().containsAll(List.of("LIMITED", "CAPES")),
			"Limited and Capes must be reachable rail filters");
		check(HgnCosmeticsCatalogueFilter.visible(true, false, 0, plain),
			"A free ordinary cosmetic remains visible before ownership");
		check(!HgnCosmeticsCatalogueFilter.visible(true, false, 1, plain),
			"A paid cosmetic must stay hidden until owned");
		check(!HgnCosmeticsCatalogueFilter.visible(true, false, 0, limited),
			"A Limited cosmetic must stay hidden until owned");
		check(HgnCosmeticsCatalogueFilter.visible(true, true, 500, limited),
			"An owned paid Limited cosmetic must remain visible");
		check(!HgnCosmeticsCatalogueFilter.visible(true, false, 0, entitlementOnly),
			"A free entitlement-only cape must stay hidden until owned");
		check(HgnCosmeticsCatalogueFilter.visible(true, true, 0, entitlementOnly),
			"An owned entitlement-only cape must remain visible");
		check(!HgnCosmeticsCatalogueFilter.visible(false, true, 0, plain),
			"Inactive cosmetics remain hidden even when owned");
		check(HgnCosmeticsCatalogueFilter.matchesCategory("BACK_ITEMS", "CAPE", plain, "CAPES"),
			"The Capes rail must match authoritative CAPE equipment slots");
		check(!HgnCosmeticsCatalogueFilter.matchesCategory("WINGS", "WINGS", plain, "CAPES"),
			"The Capes rail must exclude wings");
		check(HgnCosmeticsCatalogueFilter.matchesCategory("HATS", "HEAD", limited, "LIMITED"),
			"The Limited rail must use the explicit backend tag");
		check(!HgnCosmeticsCatalogueFilter.matchesCategory("HATS", "HEAD", plain, "LIMITED"),
			"Rarity or price alone must not mark an item Limited");
		check(!HgnCosmeticsCatalogueFilter.matchesCategory("BACK_ITEMS", "CAPE", entitlementOnly, "LIMITED"),
			"Entitlement-only capes must not be mislabeled as Limited");
	}

	private record Card(String id, String name) {
	}

	private static void inside(HgnRect parent, HgnRect child) {
		check(child.width() > 0 && child.height() > 0
			&& parent.contains(child.x(), child.y())
			&& parent.contains(child.right() - 1, child.bottom() - 1),
			"Control escaped panel: " + child + " not in " + parent);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
