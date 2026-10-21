package online.horizongate.hgn.screen;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.cosmetics.HgnCapeRenderer;
import online.horizongate.hgn.cosmetics.HgnCosmeticModels;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import online.horizongate.hgn.cosmetics.HgnCosmeticsPageRefresh;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.ui.HgnCosmeticsCatalogueFilter;
import online.horizongate.hgn.ui.HgnCosmeticsLayout;
import online.horizongate.hgn.ui.HgnCosmeticsSelection;
import online.horizongate.hgn.ui.HgnIcon;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Reference-led catalogue backed only by the authenticated membership snapshot. */
public final class HgnCosmeticsScreen extends Screen {
	private static final List<String> CATEGORIES = HgnCosmeticsCatalogueFilter.categories();
	private static final Pattern SAFE_CATALOGUE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
	private final Screen parent;
	private String category = "ALL";
	private RarityFilter rarity = RarityFilter.ALL;
	private Sort sort = Sort.NAME;
	private boolean ownedOnly;
	private String search = "";
	private int page;
	private int categoryPage;
	private boolean compactDetails;
	private EditBox searchBox;
	private HgnCosmeticsLayout layout;
	private List<HgnCosmetics.ItemDefinition> visibleItems = List.of();
	private HgnCosmetics.ItemDefinition selected;
	private int pageCount = 1;
	private int matchedItemCount;
	private HgnCosmetics.Snapshot observedSnapshot;
	private HgnCosmetics.ConnectionState observedConnection;
	private CollectionStats collectionStats = new CollectionStats(0, 0, 0, 0);
	private boolean filterDirty;
	private int packRepairTicks;
	private final HgnCosmeticsPageRefresh pageRefresh = new HgnCosmeticsPageRefresh();

	public HgnCosmeticsScreen(Screen parent) {
		super(Component.literal("HGN Cosmetics"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HgnCosmeticModels.ensurePackLoaded();
		rebuild();
		pageRefresh.requestOnce(() -> HgnMessages.requestCosmetics());
	}

	@Override
	public void tick() {
		if (++packRepairTicks >= 20) {
			packRepairTicks = 0;
			HgnCosmeticModels.ensurePackLoaded();
		}
		HgnCosmetics.Snapshot current = HgnMessages.cosmetics();
		if (!current.equals(observedSnapshot)
			|| HgnCosmetics.connectionState() != observedConnection || filterDirty) {
			boolean focusSearch = filterDirty;
			filterDirty = false;
			rebuild();
			if (focusSearch && searchBox != null) searchBox.setFocused(true);
		}
	}

	private void rebuild() {
		clearWidgets();
		searchBox = null;
		HgnCosmetics.Snapshot snapshot = HgnMessages.cosmetics();
		observedSnapshot = snapshot;
		observedConnection = HgnCosmetics.connectionState();
		collectionStats = countCollection(snapshot);
		layout = HgnCosmeticsLayout.resolve(width, height);
		if (!layout.compact()) {
			categoryPage = 0;
			compactDetails = false;
		}
		List<HgnCosmetics.ItemDefinition> filtered = filtered(snapshot);
		if (selected != null) {
			selected = HgnCosmeticsSelection.matchingOrNone(filtered, selected,
				HgnCosmetics.ItemDefinition::id);
			if (selected == null) compactDetails = false;
		}
		addCategoryButtons();
		if (!layout.compact() || !compactDetails) addCatalogueControls(snapshot, filtered);
		HgnCosmetics.preview(selected == null || isBundle(selected) ? "" : selected.id());
		if (!layout.compact() || compactDetails) addProfileControls(snapshot);
	}

	private void addCategoryButtons() {
		int first = categoryPage * layout.categoryPageSize();
		for (int row = 0; row < layout.categoryPageSize()
			&& first + row < CATEGORIES.size(); row++) {
			String value = CATEGORIES.get(first + row);
			HgnRect bounds = layout.category(row);
			String label = layout.compact() ? compactCategory(value) : categoryLabel(value);
			HgnButton control = layout.wideStage()
				? HgnButton.hitTarget(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
					Component.literal(label), button -> selectCategory(value))
				: layout.compact()
				? HgnButton.centered(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
					Component.literal(label), false, button -> selectCategory(value))
				: HgnButton.command(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
					Component.literal(label), categoryIcon(value), false,
					button -> selectCategory(value));
			addRenderableWidget(control.selected(value.equals(category)
				&& (!layout.compact() || !compactDetails)));
		}
		if (layout.compact()) {
			HgnRect shortcut = layout.profileShortcut();
			addRenderableWidget(HgnButton.centered(shortcut.x(), shortcut.y(),
				shortcut.width(), shortcut.height(), Component.literal("YOUR LOOK"),
				compactDetails, button -> {
					compactDetails = true;
					rebuild();
				}).selected(compactDetails));
			HgnRect pager = layout.categoryPager();
			int half = (pager.width() - 2) / 2;
			HgnButton previous = HgnButton.iconOnly(pager.x(), pager.y(), half, pager.height(),
				HgnIcon.PAGE_LEFT, button -> {
					categoryPage = Math.max(0, categoryPage - 1);
					rebuild();
				});
			previous.active = categoryPage > 0;
			addRenderableWidget(previous);
			HgnButton next = HgnButton.iconOnly(pager.x() + half + 2, pager.y(),
				pager.width() - half - 2, pager.height(), HgnIcon.PAGE_RIGHT, button -> {
					categoryPage = Math.min((CATEGORIES.size() - 1) / layout.categoryPageSize(),
						categoryPage + 1);
					rebuild();
				});
			next.active = (categoryPage + 1) * layout.categoryPageSize() < CATEGORIES.size();
			addRenderableWidget(next);
		}
		HgnRect back = layout.back();
		addRenderableWidget(HgnButton.centered(back.x(), back.y(), back.width(), back.height(),
			Component.literal(layout.compact() ? "BACK" : "BACK TO MENU"), false,
			button -> onClose()));
	}

	private void selectCategory(String value) {
		category = value;
		page = 0;
		compactDetails = false;
		rebuild();
	}

	private void addCatalogueControls(HgnCosmetics.Snapshot snapshot,
		List<HgnCosmetics.ItemDefinition> filtered) {
		HgnRect searchBounds = layout.search();
		searchBox = new online.horizongate.hgn.compat.HgnScaledEditBox(font,
			searchBounds.x(), searchBounds.y(), searchBounds.width(), searchBounds.height(),
			Component.literal("Search cosmetics"), layout.compact() ? 0.82f : 0.95f);
		searchBox.setMaxLength(64);
		searchBox.setBordered(false);
		searchBox.setHint(HgnCompat.uiText(layout.compact() ? "Search..." : "Search cosmetics..."));
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			if (value.equals(search)) return;
			search = value;
			page = 0;
			filterDirty = true;
		});
		addRenderableWidget(searchBox);
		button(layout.rarity(), "RARITY " + rarity.label, false, () -> {
			rarity = rarity.next();
			page = 0;
			rebuild();
		});
		button(layout.sort(), "SORT " + sort.label, false, () -> {
			sort = sort.next();
			page = 0;
			rebuild();
		});
		button(layout.owned(), layout.compact() ? "OWNED" : "OWNED ONLY", ownedOnly, () -> {
			ownedOnly = !ownedOnly;
			page = 0;
			rebuild();
		});
		int perPage = layout.columns() * layout.rows();
		matchedItemCount = filtered.size();
		pageCount = Math.max(1, (filtered.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * perPage;
		visibleItems = List.copyOf(filtered.subList(start, Math.min(filtered.size(), start + perPage)));
		selected = HgnCosmeticsSelection.visibleOrFirst(visibleItems, selected,
			HgnCosmetics.ItemDefinition::id);
		if (layout.compact() && visibleItems.isEmpty()) {
			boolean noCatalogue = snapshot.catalogue().isEmpty();
			HgnCosmetics.ConnectionState state = HgnCosmetics.connectionState();
			boolean accountRequired = state == HgnCosmetics.ConnectionState.LAUNCHER_REQUIRED
				|| state == HgnCosmetics.ConnectionState.UNLINKED
				|| state == HgnCosmetics.ConnectionState.EXPIRED
				|| state == HgnCosmetics.ConnectionState.MEMBERSHIP_UNAVAILABLE;
			HgnButton emptyAction = button(layout.emptyAction(), noCatalogue
				? accountRequired ? "VIEW ACCOUNT" : state == HgnCosmetics.ConnectionState.CONNECTING
					? "CONNECTING" : "RETRY CATALOGUE"
				: "CLEAR FILTERS", false, () -> {
					if (!noCatalogue) {
						search = "";
						category = "ALL";
						rarity = RarityFilter.ALL;
						ownedOnly = false;
						page = 0;
						categoryPage = 0;
						rebuild();
					} else if (accountRequired) {
						compactDetails = true;
						rebuild();
					} else {
						HgnMessages.requestCosmetics();
					}
				});
			emptyAction.active = state != HgnCosmetics.ConnectionState.CONNECTING
				|| !noCatalogue;
		}
		for (int index = 0; index < visibleItems.size(); index++) {
			HgnCosmetics.ItemDefinition item = visibleItems.get(index);
			HgnRect card = layout.card(index);
			addRenderableWidget(HgnButton.hitTarget(card.x(), card.y(), card.width(), card.height(),
				Component.literal("Preview " + item.name()), control -> {
					selected = item;
					compactDetails = layout.compact();
					rebuild();
				}));
			if (!layout.compact()) {
				HgnRect action = new HgnRect(card.x() + 4, card.bottom() - 19,
					card.width() - 8, 15);
				boolean equipped = snapshot.profile().equipped().containsValue(item.id());
				HgnButton equip = button(action, equipped ? "EQUIPPED"
					: shortActionLabel(snapshot, item), equipped,
					() -> activate(item));
				equip.active = !equipped && !isLockedAction(snapshot, item);
			}
		}
		HgnRect previousBounds = layout.previous();
		HgnButton previous = button(previousBounds, "<", false, () -> {
			page = Math.max(0, page - 1);
			rebuild();
		});
		previous.active = page > 0;
		HgnButton next = button(layout.next(), ">", false, () -> {
			page = Math.min(pageCount - 1, page + 1);
			rebuild();
		});
		next.active = page + 1 < pageCount;
	}

	private void addProfileControls(HgnCosmetics.Snapshot snapshot) {
		if (layout.compact()) {
			HgnRect headerButton = new HgnRect(layout.profile().right() - 74,
				layout.profile().y() + 3, 69, 13);
			button(headerButton, "CATALOGUE", false, () -> {
				compactDetails = false;
				rebuild();
			});
		}
		HgnRect model = layout.previewModel();
		addRenderableWidget(HgnCompat.cosmeticProfileWidget(minecraft,
			model.x(), model.y(), model.width(), model.height()));
		HgnRect selection = layout.selection();
		int actionWidth = (selection.width() - 13) / 2;
		int actionY = selection.bottom() - 19;
		HgnRect actionBounds = new HgnRect(selection.x() + 4, actionY, actionWidth, 15);
		HgnRect removeBounds = new HgnRect(actionBounds.right() + 5, actionY,
			selection.right() - actionBounds.right() - 9, 15);
		if (selected != null) {
			boolean equipped = snapshot.profile().equipped().containsValue(selected.id());
			HgnButton action = button(actionBounds, equipped ? "EQUIPPED"
				: shortActionLabel(snapshot, selected), true, () -> activate(selected));
			action.active = !equipped && !isLockedAction(snapshot, selected);
			HgnButton remove = button(removeBounds, "UNEQUIP", false,
				() -> HgnMessages.unequipCosmetic(selected.equipmentSlot()));
			remove.active = equipped && !isBundle(selected);
		} else {
			button(actionBounds, "EQUIP", true, () -> {}).active = false;
			button(removeBounds, "UNEQUIP", false, () -> {}).active = false;
		}
		HgnRect wallet = layout.wallet();
		int buttonY = wallet.bottom() - (layout.compact() ? 13 : 18);
		UUID playerId = minecraft.getUser().getProfileId();
		HgnButton account = button(new HgnRect(wallet.x() + 4, buttonY, wallet.width() - 8,
			layout.compact() ? 11 : 14), "OPEN HGN ACCOUNT", false,
			() -> {
				if (playerId != null) HgnCompat.openUrl("hgn://account?playerId=" + playerId);
			});
		account.active = playerId != null;
		if (layout.wideStage()) addEquippedControls(snapshot.profile());
	}

	private void addEquippedControls(HgnCosmetics.Profile profile) {
		HgnRect equipped = layout.equipped();
		List<Map.Entry<String, String>> entries = new ArrayList<>(profile.equipped().entrySet());
		entries.sort(Map.Entry.comparingByKey());
		int capacity = Math.max(1, (equipped.height() - 20) / 23);
		if (entries.size() > capacity) capacity = Math.max(1, capacity - 1);
		for (int index = 0; index < Math.min(capacity, entries.size()); index++) {
			String slot = entries.get(index).getKey();
			HgnRect row = equippedRow(equipped, index);
			button(new HgnRect(row.right() - 18, row.y() + 3, 15, 15), "X", false,
				() -> HgnMessages.unequipCosmetic(slot));
		}
	}

	private HgnButton button(HgnRect bounds, String label, boolean selected, Runnable action) {
		return addRenderableWidget(HgnButton.centered(bounds.x(), bounds.y(),
			bounds.width(), bounds.height(), Component.literal(label), selected,
			control -> action.run()).selected(selected));
	}

	private List<HgnCosmetics.ItemDefinition> filtered(HgnCosmetics.Snapshot snapshot) {
		String query = search.toLowerCase(Locale.ROOT).trim();
		ArrayList<HgnCosmetics.ItemDefinition> items = new ArrayList<>();
		for (HgnCosmetics.ItemDefinition item : snapshot.catalogue()) {
			boolean owned = snapshot.profile().ownedIds().contains(item.id());
			if (!HgnCosmeticsCatalogueFilter.visible(item.active(), owned,
				item.price(), item.tags())) continue;
			if (!HgnCosmeticsCatalogueFilter.matchesCategory(item.category(),
				item.equipmentSlot(), item.tags(), category)) continue;
			if (rarity != RarityFilter.ALL && !item.rarity().equalsIgnoreCase(rarity.label)) continue;
			if (ownedOnly && !owned) continue;
			if (!query.isBlank() && !item.name().toLowerCase(Locale.ROOT).contains(query)
				&& item.tags().stream().noneMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(query))) continue;
			items.add(item);
		}
		Comparator<HgnCosmetics.ItemDefinition> comparator = switch (sort) {
			case NAME -> Comparator.comparing(HgnCosmetics.ItemDefinition::name, String.CASE_INSENSITIVE_ORDER);
			case PRICE -> Comparator.comparingLong(HgnCosmetics.ItemDefinition::price);
			case RARITY -> Comparator.comparingInt(item -> rarityRank(item.rarity()));
			case REVISION -> Comparator.comparingLong(HgnCosmetics.ItemDefinition::assetRevision).reversed();
		};
		items.sort(comparator.thenComparing(HgnCosmetics.ItemDefinition::id));
		return items;
	}

	private static CollectionStats countCollection(HgnCosmetics.Snapshot snapshot) {
		int visible = 0;
		int owned = 0;
		int capes = 0;
		int limited = 0;
		for (HgnCosmetics.ItemDefinition item : snapshot.catalogue()) {
			boolean playerOwns = snapshot.profile().ownedIds().contains(item.id());
			if (!HgnCosmeticsCatalogueFilter.visible(item.active(), playerOwns,
				item.price(), item.tags())) continue;
			visible++;
			if (!playerOwns) continue;
			owned++;
			if (HgnCosmeticsCatalogueFilter.matchesCategory(item.category(),
				item.equipmentSlot(), item.tags(), "CAPES")) capes++;
			if (HgnCosmeticsCatalogueFilter.matchesCategory(item.category(),
				item.equipmentSlot(), item.tags(), "LIMITED")) limited++;
		}
		return new CollectionStats(visible, owned, capes, limited);
	}

	private void activate(HgnCosmetics.ItemDefinition item) {
		if (item == null) return;
		String itemId = item.id();
		HgnCosmetics.Snapshot snapshot = HgnMessages.cosmetics();
		item = snapshot.catalogue().stream().filter(entry -> entry.id().equals(itemId))
			.findFirst().orElse(null);
		if (item == null) return;
		HgnCosmetics.Profile profile = snapshot.profile();
		if (!HgnCosmeticsCatalogueFilter.visible(item.active(),
			profile.ownedIds().contains(item.id()), item.price(), item.tags())) return;
		if (isBundle(item) && (item.bundleContents().isEmpty()
			|| profile.ownedIds().contains(item.id())
			|| bundleOwnedCount(profile, item) == item.bundleContents().size())) return;
		if (profile.equipped().containsValue(item.id())) {
			HgnMessages.unequipCosmetic(item.equipmentSlot());
			return;
		}
		if (profile.ownedIds().contains(item.id())) {
			HgnMessages.equipCosmetic(item.id());
			return;
		}
		if (profile.legacyCurrency() || isEntitlementOnly(item)) return;
		if (item.launcherExclusive() && !profile.launcherLinked()) {
			UUID playerId = minecraft.getUser().getProfileId();
			if (playerId != null) HgnCompat.openUrl("hgn://account?playerId=" + playerId);
			return;
		}
		String purchaseLink = launcherPurchaseLink(item);
		if (purchaseLink.isEmpty()) return;
		String confirmation = item.price() == 0L
			? "Continue in HGN Launcher. The server verifies eligibility and ownership before granting this cosmetic."
			: "Continue in HGN Launcher to approve " + item.price() + " "
				+ item.currency().displayName()
				+ ". The server verifies the final price, balance, and ownership before charging.";
		minecraft.setScreen(new ConfirmScreen(confirmed -> {
			minecraft.setScreen(this);
			if (confirmed) HgnCompat.openUrl(purchaseLink);
		}, Component.literal(item.price() == 0L ? "CLAIM " + item.name() : "PURCHASE " + item.name()),
			Component.literal(confirmation)));
	}

	private String launcherPurchaseLink(HgnCosmetics.ItemDefinition item) {
		String itemId = item.id();
		HgnCosmetics.ItemDefinition current = HgnCosmetics.definition(itemId);
		if (!SAFE_CATALOGUE_ID.matcher(itemId).matches() || current == null || !current.active()) return "";
		UUID playerId = minecraft.getUser().getProfileId();
		if (playerId == null) return "";
		return "hgn://cosmetic-purchase?playerId=" + playerId + "&itemId="
			+ URLEncoder.encode(itemId, StandardCharsets.UTF_8);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnCosmetics.Snapshot frame = HgnMessages.cosmetics();
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (layout.wideStage()) {
			renderHeader(graphics);
			renderStage(graphics);
		} else {
			HgnSurface.panel(graphics, layout.rail(), true);
		}
		renderRail(graphics);
		if (!layout.compact() || !compactDetails) {
			HgnSurface.panel(graphics, layout.catalogue(), false);
			renderCatalogue(graphics, frame, mouseX, mouseY);
		}
		if (!layout.compact() || compactDetails) {
			HgnSurface.panel(graphics, layout.profile(), false);
			renderProfile(graphics, frame.profile());
		}
		super.render(graphics, mouseX, mouseY, partialTick);
		if (!layout.compact() || compactDetails) renderPreviewLabel(graphics, frame.profile());
	}

	private void renderHeader(GuiGraphics graphics) {
		HgnRect header = layout.header();
		if (header.height() < 80) {
			HgnSurface.wordmark(graphics, header.x() + 5, header.y() + 4, 54, 38, false);
			heading(graphics, "HORIZONGATE", header.x() + 65, header.y() + 5,
				header.width() - 225, 1.35f, HgnPalette.CYAN_SOFT);
			heading(graphics, "C L I E N T", header.x() + 66, header.y() + 25,
				header.width() - 226, 0.9f, HgnPalette.TEXT);
			fitted(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
				header.x() + 66, header.y() + 42, header.width() - 226,
				0.6f, HgnPalette.TEXT_MUTED);
			HgnCosmetics.ConnectionState state = HgnCosmetics.connectionState();
			fittedRight(graphics, "HGN COSMETICS", header.right() - 5, header.y() + 13,
				150, 0.8f, HgnPalette.CYAN);
			fittedRight(graphics, state.label(), header.right() - 5, header.y() + 30,
				150, 0.69f, connectionColor(state));
			return;
		}
		HgnSurface.wordmark(graphics, header.x() + 10, header.y() + 13, 82, 54, false);
		heading(graphics, "HORIZONGATE", header.x() + 100, header.y() + 17,
			header.width() - 300, 2.05f, HgnPalette.CYAN_SOFT);
		heading(graphics, "C L I E N T", header.x() + 102, header.y() + 43,
			header.width() - 320, 1.35f, HgnPalette.TEXT);
		fitted(graphics, "PLAY  /  EXPLORE  /  CUSTOMIZE  /  BELONG",
			header.x() + 102, header.y() + 66, header.width() - 330,
			0.72f, HgnPalette.TEXT_MUTED);
		HgnCosmetics.ConnectionState state = HgnCosmetics.connectionState();
		fittedRight(graphics, "HGN COSMETICS", header.right() - 5, header.y() + 23,
			170, 0.9f, HgnPalette.CYAN);
		fittedRight(graphics, state.label(), header.right() - 5, header.y() + 38,
			170, 0.75f, connectionColor(state));
	}

	private void renderStage(GuiGraphics graphics) {
		HgnRect stage = layout.stage();
		HgnDraw.gradient(graphics, stage.x(), stage.y(),
			stage.right(), stage.bottom(), 0x4804090D, 0xD804090D);
		HgnSurface.focus(graphics, stage);
		fitted(graphics, "YOUR LOOK", stage.x() + 8, stage.y() + 8,
			stage.width() - 16, 0.72f, HgnPalette.CYAN_SOFT);
		HgnDraw.rect(graphics, stage.x() + 12, stage.bottom() - 42,
			stage.right() - 12, stage.bottom() - 40,
			HgnPalette.alpha(HgnPalette.CYAN_BRIGHT, 160));
		HgnSurface.separator(graphics, stage.x() + 8, stage.bottom() - 39,
			stage.width() - 16, true);
		HgnTypography.centered(graphics, font,
			HgnTypography.trim(font, minecraft.getUser().getName(), stage.width() - 16),
			stage.centerX(), stage.bottom() - 31, 0.75f, HgnPalette.CYAN_SOFT);
	}

	private void renderRail(GuiGraphics graphics) {
		HgnRect rail = layout.rail();
		if (layout.wideStage()) {
			for (int index = 0; index < CATEGORIES.size(); index++) {
				String value = CATEGORIES.get(index);
				HgnRect item = layout.category(index);
				boolean active = value.equals(category);
				HgnSurface.dashboardInset(graphics, item, active);
				if (active) HgnDraw.rect(graphics, item.x() + 1, item.y() + 4,
					item.x() + 3, item.bottom() - 4, HgnPalette.CYAN);
				categoryIcon(value).draw(graphics, item.x() + 7,
					item.y() + (item.height() - 20) / 2, 20,
					active ? HgnPalette.CYAN_SOFT : HgnPalette.CYAN);
				String railLabel = layout.denseReference() && value.equals("ALL") ? "ALL"
					: layout.denseReference() && value.equals("BACK ITEMS") ? "BACK"
					: value.equals("ALL") ? "ALL COSMETICS" : value;
				fitted(graphics, railLabel,
					item.x() + 34, item.y() + (item.height() >= 31 ? 7
						: item.height() >= 26 ? 5 : 9),
					item.width() - 40,
					0.85f, HgnPalette.TEXT);
				if (item.height() >= 26) {
					fitted(graphics, categorySubtitle(value), item.x() + 34,
						item.y() + (item.height() >= 31 ? 20 : 17), item.width() - 40,
						0.65f, HgnPalette.TEXT_MUTED);
				}
			}
			return;
		} else if (layout.compact()) {
			HgnSurface.wordmark(graphics, rail.centerX() - 26,
				rail.y() + 3, 52, 20, false);
			HgnTypography.centered(graphics, font, "CLIENT", rail.centerX(), rail.y() + 15,
				0.7f, HgnPalette.TEXT);
		} else {
			HgnSurface.wordmark(graphics, rail.centerX() - 27, rail.y() + 5, 54, 27, false);
			HgnTypography.centered(graphics, font, "COSMETICS",
				rail.centerX(), rail.y() + 31, 0.8f, HgnPalette.CYAN);
		}
	}

	private void renderCatalogue(GuiGraphics graphics, HgnCosmetics.Snapshot frame,
		int mouseX, int mouseY) {
		HgnRect catalogue = layout.catalogue();
		HgnCosmetics.ConnectionState connection = HgnCosmetics.connectionState();
		String title = layout.wideStage() ? category + "  /  " + matchedItemCount + " AVAILABLE"
			: layout.compact() ? "HGN COSMETICS" : "HORIZONGATE NETWORK COSMETICS";
		fitted(graphics, title,
			catalogue.x() + 6, catalogue.y() + 5, catalogue.width() - 110,
			layout.compact() ? 0.95f : 1.05f, HgnPalette.TEXT);
		fittedRight(graphics, connection.compactLabel(), catalogue.right() - 6,
			catalogue.y() + 6, 100, 0.75f, connectionColor(connection));
		renderFeatured(graphics, frame.profile());
		if (visibleItems.isEmpty()) renderEmpty(graphics, frame);
		else renderCards(graphics, mouseX, mouseY, frame.profile());
		HgnTypography.centered(graphics, font, (page + 1) + " / " + pageCount,
			catalogue.centerX(), layout.previous().y() + 3, 0.9f, HgnPalette.TEXT);
	}

	private void renderFeatured(GuiGraphics graphics, HgnCosmetics.Profile profileData) {
		HgnRect featured = layout.featured();
		if (layout.wideStage()) {
			renderWideFeatured(graphics, featured, profileData);
			return;
		}
		if (layout.compact()) {
			HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
				featured.x(), featured.y(), featured.width(), featured.height(),
				170, 240, 1320, 390, 1672, 941);
			HgnDraw.rect(graphics, featured.x(), featured.y(),
				featured.right(), featured.bottom(), 0xC804090D);
			HgnSurface.focus(graphics, featured);
		} else {
			HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
				featured.x(), featured.y(), featured.width(), featured.height(),
				120, 270, 1430, 230, 1672, 941);
			HgnDraw.rect(graphics, featured.x(), featured.y(),
				featured.x() + featured.width() * 72 / 100, featured.bottom(), 0xD804090D);
			HgnSurface.focus(graphics, featured);
		}
		if (selected == null) {
			fitted(graphics, layout.compact() ? "VERIFIED HGN COLLECTION" : "YOUR COSMETICS",
				featured.x() + 7, featured.y() + 5,
				featured.width() - 14, 0.95f, HgnPalette.CYAN);
			fitted(graphics, layout.compact() ? HgnCosmetics.connectionState().description()
				: "The verified catalogue appears here when available.",
				featured.x() + 7, featured.y() + 18, featured.width() - 14,
				0.72f, HgnPalette.TEXT_MUTED);
			return;
		}
		int modelSize = Math.min(56, featured.height() - 8);
		int modelX = featured.right() - modelSize - 7;
		int modelY = featured.y() + (featured.height() - modelSize) / 2;
		renderModel(graphics, selected, modelX, modelY, modelSize);
		int textWidth = modelX - featured.x() - 13;
		fitted(graphics, "SELECTED LOOK", featured.x() + 7, featured.y() + 5,
			textWidth, 0.7f, HgnPalette.CYAN);
		fitted(graphics, selected.name(), featured.x() + 7,
			featured.y() + (layout.compact() ? 16 : 21), textWidth,
			layout.compact() ? 0.88f : 1.2f, HgnPalette.TEXT);
		if (!layout.compact()) {
			String status = profileData.ownedIds().contains(selected.id()) ? "OWNED"
				: isEntitlementOnly(selected) ? unlockLabel(selected)
				: selected.rarity() + "  ·  " + selected.currency().displayName();
			fitted(graphics, status, featured.x() + 7, featured.bottom() - 16,
				textWidth, 0.75f, HgnPalette.TEXT_MUTED);
		}
	}

	private void renderWideFeatured(GuiGraphics graphics, HgnRect featured,
		HgnCosmetics.Profile profileData) {
		HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
			featured.x(), featured.y(), featured.width(), featured.height(),
			170, 240, 1320, 390, 1672, 941);
		HgnDraw.rect(graphics, featured.x(), featured.y(),
			featured.x() + featured.width() * 61 / 100, featured.bottom(), 0xD804090D);
		HgnSurface.focus(graphics, featured);
		int textX = featured.x() + 12;
		int textWidth = featured.width() * 57 / 100 - 16;
		fitted(graphics, selected == null ? "VERIFIED CATALOGUE" : "SELECTED COSMETIC",
			textX, featured.y() + 10, textWidth, 0.77f, HgnPalette.CYAN);
		if (selected == null) {
			if (layout.denseReference()) {
				heading(graphics, "HORIZONGATE", textX, featured.y() + 29,
					textWidth, 1.1f, HgnPalette.TEXT);
				heading(graphics, "NETWORK", textX, featured.y() + 44,
					textWidth, 1.1f, HgnPalette.TEXT);
			} else {
				heading(graphics, "HORIZONGATE NETWORK", textX, featured.y() + 33,
					textWidth, 1.6f, HgnPalette.TEXT);
			}
			fitted(graphics, HgnCosmetics.connectionState().description(), textX,
				featured.y() + 64, textWidth, 0.82f, HgnPalette.TEXT_MUTED);
			return;
		}
		fitted(graphics, selected.name(), textX, featured.y() + 32,
			textWidth, 1.7f, HgnPalette.TEXT);
		String status = visibleStatus(profileData, selected);
		fitted(graphics, selected.rarity() + "  /  " + status, textX,
			featured.y() + 61, textWidth, 0.83f, rarityColor(selected.rarity()));
		fitted(graphics, "Preview your verified HGN look", textX,
			featured.bottom() - 20, textWidth, 0.76f, HgnPalette.TEXT_MUTED);
		int modelSize = Math.min(featured.height() - 20, 125);
		renderModel(graphics, selected, featured.right() - modelSize - 22,
			featured.y() + (featured.height() - modelSize) / 2, modelSize);
	}

	private void renderEmpty(GuiGraphics graphics, HgnCosmetics.Snapshot frame) {
		HgnRect empty = layout.emptyState();
		if (layout.compact()) {
			HgnDraw.textureRegionStretch(graphics, HgnSurface.GATEWAY_SCENE,
				empty.x(), empty.y(), empty.width(), empty.height(),
				170, 240, 1320, 390, 1672, 941);
			HgnDraw.rect(graphics, empty.x(), empty.y(),
				empty.right(), empty.bottom(), 0xD804090D);
			HgnSurface.focus(graphics, empty);
		} else {
			HgnSurface.dashboardInset(graphics, empty, false);
		}
		boolean noCatalogue = frame.catalogue().isEmpty();
		HgnCosmetics.ConnectionState state = HgnCosmetics.connectionState();
		boolean verifiedEmpty = noCatalogue && state == HgnCosmetics.ConnectionState.CONNECTED;
		String title = verifiedEmpty ? "NO COSMETICS AVAILABLE"
			: noCatalogue ? state.label() : "NO MATCHING COSMETICS";
		String hint = verifiedEmpty ? "The verified catalogue is empty."
			: noCatalogue ? state.description() : "Try another category or filter.";
		if (layout.compact()) {
			if (empty.height() >= 76) HgnIcon.COSMETICS.draw(graphics,
				empty.centerX() - 10, empty.y() + 8, 20, HgnPalette.CYAN);
			int titleY = empty.y() + (empty.height() >= 76 ? 32 : 6);
			HgnTypography.centered(graphics, font, HgnTypography.trim(font, title,
				empty.width() - 10), empty.centerX(), titleY, 0.8f, HgnPalette.TEXT);
			HgnTypography.centered(graphics, font, HgnTypography.trim(font, hint,
				empty.width() - 10), empty.centerX(), titleY + 12,
				0.67f, HgnPalette.TEXT_MUTED);
			return;
		}
		HgnTypography.centered(graphics, font, HgnTypography.trim(font, title,
			empty.width() - 10), empty.centerX(), empty.centerY() - 8,
			0.9f, HgnPalette.TEXT);
		if (empty.height() >= 40) {
			HgnTypography.centered(graphics, font, HgnTypography.trim(font, hint,
				empty.width() - 10), empty.centerX(), empty.centerY() + 5,
				0.75f, HgnPalette.TEXT_MUTED);
		}
	}

	private void renderCards(GuiGraphics graphics, int mouseX, int mouseY,
		HgnCosmetics.Profile profileData) {
		for (int index = 0; index < visibleItems.size(); index++) {
			HgnCosmetics.ItemDefinition item = visibleItems.get(index);
			HgnRect card = layout.card(index);
			boolean highlighted = card.contains(mouseX, mouseY)
				|| selected != null && selected.id().equals(item.id());
			HgnSurface.cosmeticCard(graphics, card, highlighted);
			if (!layout.compact()) {
				renderWideCard(graphics, item, card, profileData);
				continue;
			}
			fitted(graphics, item.name(), card.x() + 4, card.y() + 4,
				card.width() - 8, 0.83f, HgnPalette.TEXT);
			int modelSize = Math.max(12, Math.min(64,
				Math.min(card.width() - 12, card.height() - 35)));
			int modelX = card.centerX() - modelSize / 2;
			int modelY = card.y() + 14;
			if (!renderModel(graphics, item, modelX, modelY, modelSize)) {
				HgnTypography.centered(graphics, font, "NO MODEL", card.centerX(),
					card.centerY(), 0.62f, HgnPalette.TEXT_MUTED);
			}
			fitted(graphics, item.rarity(), card.x() + 4,
				card.bottom() - 19, card.width() - 8, 0.67f,
				rarityColor(item.rarity()));
			String label = profileData.ownedIds().contains(item.id()) ? "OWNED"
				: isEntitlementOnly(item) ? unlockLabel(item)
				: item.price() == 0L ? "FREE" : null;
			if (label == null) {
				renderPrice(graphics, item, card.x(), card.bottom() - 10,
					card.width(), layout.compact() ? 7 : 9);
			} else {
				fitted(graphics, label, card.x() + 4, card.bottom() - 10,
					card.width() - 8, 0.7f, HgnPalette.TEXT_MUTED);
			}
		}
	}

	private void renderWideCard(GuiGraphics graphics, HgnCosmetics.ItemDefinition item,
		HgnRect card, HgnCosmetics.Profile profileData) {
		HgnDraw.rect(graphics, card.x() + 2, card.y() + 2,
			card.right() - 2, card.y() + 4, rarityColor(item.rarity()));
		fitted(graphics, item.rarity().toUpperCase(Locale.ROOT), card.x() + 5,
			card.y() + 8, card.width() - 10, 0.69f, rarityColor(item.rarity()));
		int modelSize = Math.max(16, Math.min(card.width() - 16, card.height() - 65));
		if (!renderModel(graphics, item, card.centerX() - modelSize / 2,
			card.y() + 22, modelSize)) {
			HgnTypography.centered(graphics, font, "NO MODEL", card.centerX(),
				card.centerY() - 6, 0.64f, HgnPalette.TEXT_MUTED);
		}
		fitted(graphics, item.name(), card.x() + 5, card.bottom() - 42,
			card.width() - 10, 0.86f, HgnPalette.TEXT);
		String status = profileData.equipped().containsValue(item.id())
			? "EQUIPPED" : visibleStatus(profileData, item);
		fitted(graphics, status, card.x() + 5, card.bottom() - 30,
			card.width() - 10, 0.7f, HgnPalette.TEXT_MUTED);
	}

	private boolean renderModel(GuiGraphics graphics, HgnCosmetics.ItemDefinition item,
		int x, int y, int size) {
		if (isBundle(item)) {
			HgnIcon.BUNDLES.draw(graphics, x, y, size, HgnPalette.CYAN);
			return true;
		}
		if (HgnCapeRenderer.isCape(item)) {
			return HgnCapeRenderer.renderGui(graphics, item,
				x + size / 4, y, size / 2, size);
		}
		return HgnCosmeticModels.renderGui(graphics, item, x, y, size);
	}

	private void renderProfile(GuiGraphics graphics, HgnCosmetics.Profile data) {
		HgnRect profile = layout.profile();
		HgnCosmetics.ConnectionState connection = HgnCosmetics.connectionState();
		if (layout.wideStage()) {
			HgnRect account = new HgnRect(profile.x() + 5, profile.y() + 5,
				profile.width() - 10, 36);
			HgnSurface.dashboardInset(graphics, account, data.launcherLinked());
			fitted(graphics, minecraft.getUser().getName(), account.x() + 6,
				account.y() + 5, account.width() - 12, 0.94f, HgnPalette.TEXT);
			fitted(graphics, connection.label(), account.x() + 6,
				account.y() + 18, account.width() - 12, 0.71f, connectionColor(connection));
		} else {
			fitted(graphics, "EQUIPPED LOOK", profile.x() + 6, profile.y() + 5,
				profile.width() - (layout.compact() ? 90 : 12), 0.9f, HgnPalette.CYAN);
			HgnSurface.cosmeticPreview(graphics, layout.preview());
		}
		HgnSurface.dashboardInset(graphics, layout.selection(), false);
		HgnSurface.dashboardInset(graphics, layout.equipped(), false);
		if (layout.wideStage()) HgnSurface.dashboardInset(graphics, layout.stats(), false);
		HgnSurface.cosmeticWallet(graphics, layout.wallet(), false);
		HgnRect selection = layout.selection();
		if (selected != null) {
			fitted(graphics, selected.name(), selection.x() + 5, selection.y() + 5,
				selection.width() - 10, 0.9f, HgnPalette.TEXT);
			fitted(graphics, selected.rarity(), selection.x() + 5, selection.y() + 17,
				selection.width() - 10, 0.72f, rarityColor(selected.rarity()));
			String detail = data.legacyCurrency() && !data.ownedIds().contains(selected.id())
				? "Legacy currency: purchases paused."
				: isBundle(selected) ? bundleContentsLabel(data, selected)
				: data.ownedIds().contains(selected.id()) ? "Owned in your HGN account."
				: isEntitlementOnly(selected) ? unlockLabel(selected)
				: "Purchase in HGN Launcher.";
			fitted(graphics, detail, selection.x() + 5, selection.y() + 30,
				selection.width() - 10, 0.7f, HgnPalette.TEXT_MUTED);
			if (!data.ownedIds().contains(selected.id()) && !data.legacyCurrency()
				&& !isEntitlementOnly(selected) && selected.price() > 0L) {
				fitted(graphics, selected.price() + " " + selected.currency().displayName(),
					selection.x() + 5, selection.y() + 40, selection.width() - 10,
					0.72f, currencyColor(selected.currency()));
			}
		} else {
			fitted(graphics, layout.denseReference() ? "Select a cosmetic"
				: "Select a cosmetic to preview.", selection.x() + 5,
				selection.y() + 7, selection.width() - 10, 0.75f, HgnPalette.TEXT_MUTED);
		}
		renderEquipped(graphics, data);
		if (layout.wideStage()) renderCollectionStats(graphics);
		renderWallet(graphics, data, connection);
		HgnCosmetics.Result result = HgnCosmetics.lastResult();
		if (result.receivedAt() > 0 && System.currentTimeMillis() - result.receivedAt() < 8_000L
			&& !layout.compact() && !layout.wideStage()) {
			fitted(graphics, result.message(), selection.x() + 5,
				selection.bottom() - 31, selection.width() - 10, 0.68f,
				result.success() ? HgnPalette.SUCCESS : HgnPalette.DANGER);
		}
	}

	private void renderEquipped(GuiGraphics graphics, HgnCosmetics.Profile data) {
		HgnRect equipped = layout.equipped();
		HgnCosmetics.Result result = HgnCosmetics.lastResult();
		boolean showResult = layout.wideStage() && result.receivedAt() > 0
			&& System.currentTimeMillis() - result.receivedAt() < 8_000L;
		fitted(graphics, showResult ? result.message() : "EQUIPPED  " + data.equipped().size(),
			equipped.x() + 5, equipped.y() + 4, equipped.width() - 10, 0.78f,
			showResult ? result.success() ? HgnPalette.SUCCESS : HgnPalette.DANGER
				: HgnPalette.CYAN);
		if (data.equipped().isEmpty()) {
			fitted(graphics, "No cosmetic equipped", equipped.x() + 5,
				equipped.y() + 16, equipped.width() - 10, 0.7f, HgnPalette.TEXT_MUTED);
			return;
		}
		List<Map.Entry<String, String>> entries = new ArrayList<>(data.equipped().entrySet());
		entries.sort(Map.Entry.comparingByKey());
		if (layout.wideStage()) {
			renderWideEquipped(graphics, equipped, entries);
			return;
		}
		int capacity = Math.max(1, (equipped.height() - 17) / 12);
		boolean showOverflow = entries.size() > capacity && equipped.height() > 42;
		if (showOverflow) capacity = Math.max(1, capacity - 1);
		for (int index = 0; index < Math.min(capacity, entries.size()); index++) {
			Map.Entry<String, String> entry = entries.get(index);
			HgnCosmetics.ItemDefinition item = HgnCosmetics.definition(entry.getValue());
			String name = item == null ? entry.getValue() : item.name();
			fitted(graphics, entry.getKey() + ": " + name, equipped.x() + 5,
				equipped.y() + 15 + index * 12, equipped.width() - 10,
				0.72f, HgnPalette.TEXT);
		}
		if (showOverflow) {
			fitted(graphics, "+" + (entries.size() - capacity) + " more equipped",
				equipped.x() + 5, equipped.bottom() - 10, equipped.width() - 10,
				0.65f, HgnPalette.TEXT_MUTED);
		}
	}

	private void renderWideEquipped(GuiGraphics graphics, HgnRect equipped,
		List<Map.Entry<String, String>> entries) {
		int capacity = Math.max(1, (equipped.height() - 20) / 23);
		boolean overflow = entries.size() > capacity;
		if (overflow) capacity = Math.max(1, capacity - 1);
		for (int index = 0; index < Math.min(capacity, entries.size()); index++) {
			Map.Entry<String, String> entry = entries.get(index);
			HgnRect row = equippedRow(equipped, index);
			HgnSurface.dashboardInset(graphics, row, false);
			HgnCosmetics.ItemDefinition item = HgnCosmetics.definition(entry.getValue());
			if (item == null || !renderModel(graphics, item, row.x() + 3, row.y() + 3, 18)) {
				HgnIcon.COSMETICS.draw(graphics, row.x() + 3, row.y() + 3,
					18, HgnPalette.CYAN);
			}
			fitted(graphics, item == null ? "Unknown cosmetic" : item.name(),
				row.x() + 25, row.y() + 4, row.width() - 47,
				0.77f, HgnPalette.TEXT);
			fitted(graphics, entry.getKey(), row.x() + 25, row.y() + 14,
				row.width() - 47, 0.64f, HgnPalette.TEXT_MUTED);
		}
		if (overflow) {
			fitted(graphics, "+" + (entries.size() - capacity) + " more equipped",
				equipped.x() + 5, equipped.bottom() - 12, equipped.width() - 10,
				0.7f, HgnPalette.TEXT_MUTED);
		}
	}

	private static HgnRect equippedRow(HgnRect equipped, int index) {
		return new HgnRect(equipped.x() + 4, equipped.y() + 19 + index * 23,
			equipped.width() - 8, 21);
	}

	private void renderCollectionStats(GuiGraphics graphics) {
		HgnRect stats = layout.stats();
		fitted(graphics, "VISIBLE COLLECTION", stats.x() + 5, stats.y() + 5,
			stats.width() - 10, 0.79f, HgnPalette.CYAN);
		if (collectionStats.visible() == 0) {
			fitted(graphics, layout.denseReference() ? "No items available"
				: "No verified items available", stats.x() + 5,
				stats.y() + 22, stats.width() - 10, 0.71f, HgnPalette.TEXT_MUTED);
			return;
		}
		fitted(graphics, "OWNED  " + collectionStats.owned() + " / "
			+ collectionStats.visible() + " shown", stats.x() + 5,
			stats.y() + 22, stats.width() - 10, 0.77f, HgnPalette.TEXT);
		fitted(graphics, "CAPES " + collectionStats.capes()
			+ "  /  LIMITED " + collectionStats.limited(), stats.x() + 5,
			stats.y() + 39, stats.width() - 10, 0.7f, HgnPalette.TEXT_MUTED);
	}

	private void renderWallet(GuiGraphics graphics, HgnCosmetics.Profile data,
		HgnCosmetics.ConnectionState connection) {
		HgnRect wallet = layout.wallet();
		boolean legacy = data.legacyCurrency();
		String golden = legacy ? data.legacyAccountCredits() : data.launcherLinked()
			? data.hgnGoldenTokens() : connection.compactLabel();
		String tokens = legacy ? data.legacyCosmeticCoins() : data.hgnTokens() < 0
			? connection.compactLabel() : Long.toString(data.hgnTokens());
		if (layout.compact()) {
			int half = wallet.width() / 2;
			fitted(graphics, (legacy ? "CREDITS " : "GOLDEN ") + golden,
				wallet.x() + 5, wallet.y() + 4, half - 7, 0.76f, HgnPalette.CYAN_SOFT);
			fitted(graphics, (legacy ? "COINS " : "TOKENS ") + tokens,
				wallet.x() + half, wallet.y() + 4, wallet.width() - half - 5,
				0.76f, HgnPalette.WARNING);
		} else {
			fitted(graphics, legacy ? "ACCOUNT CREDITS" : "HGN GOLDEN TOKENS",
				wallet.x() + 5, wallet.y() + 5, wallet.width() - 10,
				0.75f, HgnPalette.CYAN_SOFT);
			fittedRight(graphics, golden, wallet.right() - 5, wallet.y() + 16,
				wallet.width() - 10, 0.86f, HgnPalette.TEXT);
			HgnRect tokenLabel = layout.walletTokenLabel();
			HgnRect tokenValue = layout.walletTokenValue();
			fitted(graphics, legacy ? layout.shortWalletLabels() ? "COINS" : "COSMETIC COINS"
				: layout.shortWalletLabels() ? "TOKENS" : "HGN TOKENS",
				tokenLabel.x(), tokenLabel.y(), tokenLabel.width(),
				0.75f, HgnPalette.WARNING);
			fittedRight(graphics, tokens, tokenValue.right(), tokenValue.y(),
				tokenValue.width(), 0.86f, HgnPalette.TEXT);
		}
	}

	private void renderPreviewLabel(GuiGraphics graphics, HgnCosmetics.Profile data) {
		HgnRect preview = layout.preview();
		boolean previewing = selected != null && !isBundle(selected)
			&& !data.equipped().containsValue(selected.id());
		String label = previewing ? "PREVIEWING " + selected.name()
			: data.equipped().isEmpty() ? "YOUR PLAYER" : data.equipped().size() + " EQUIPPED";
		float scale = 0.68f;
		HgnTypography.centered(graphics, font,
			HgnTypography.trim(font, label, Math.max(1, Math.round((preview.width() - 8) / scale))),
			preview.centerX(), preview.bottom() - 11, scale, HgnPalette.TEXT_MUTED);
	}

	private void fitted(GuiGraphics graphics, String value, int x, int y,
		int availableWidth, float scale, int color) {
		String visible = HgnTypography.trim(font, value,
			Math.max(1, Math.round(availableWidth / Math.max(0.1f, scale))));
		HgnTypography.text(graphics, font, visible, x, y, scale, color);
	}

	private void heading(GuiGraphics graphics, String value, int x, int y,
		int availableWidth, float scale, int color) {
		HgnTypography.fittedHeading(graphics, font, value, x, y, availableWidth, scale, color);
	}

	private void fittedRight(GuiGraphics graphics, String value, int right, int y,
		int availableWidth, float scale, int color) {
		String visible = HgnTypography.trim(font, value,
			Math.max(1, Math.round(availableWidth / Math.max(0.1f, scale))));
		HgnTypography.text(graphics, font, visible,
			right - Math.round(HgnTypography.width(font, visible) * scale), y, scale, color);
	}

	private void renderPrice(GuiGraphics graphics, HgnCosmetics.ItemDefinition item,
		int x, int y, int width, int iconSize) {
		String amount = HgnTypography.trim(font, Long.toString(item.price()),
			Math.max(1, width - iconSize - 8));
		int groupWidth = Math.round(HgnTypography.width(font, amount) * 0.82f) + iconSize + 3;
		int groupX = x + (width - groupWidth) / 2;
		currencyIcon(item.currency()).draw(graphics, groupX, y - 1,
			iconSize, currencyColor(item.currency()));
		HgnTypography.text(graphics, font, amount, groupX + iconSize + 3, y,
			0.82f, currencyColor(item.currency()));
	}

	private static String shortActionLabel(HgnCosmetics.Snapshot snapshot,
		HgnCosmetics.ItemDefinition item) {
		if (isBundle(item)) {
			if (item.bundleContents().isEmpty()) return "UNAVAILABLE";
			if (snapshot.profile().ownedIds().contains(item.id())
				|| bundleOwnedCount(snapshot.profile(), item) == item.bundleContents().size()) return "OWNED";
		}
		if (snapshot.profile().ownedIds().contains(item.id())) return "EQUIP";
		if (snapshot.profile().legacyCurrency()) return "PAUSED";
		if (item.launcherExclusive() && !snapshot.profile().launcherLinked()) return "LINK";
		if (isEntitlementOnly(item)) return "LOCKED";
		return item.price() == 0L ? "CLAIM" : "PURCHASE";
	}

	private static boolean isLockedAction(HgnCosmetics.Snapshot snapshot,
		HgnCosmetics.ItemDefinition item) {
		if (snapshot.profile().legacyCurrency()
			&& !snapshot.profile().ownedIds().contains(item.id())) return true;
		if (isEntitlementOnly(item) && !snapshot.profile().ownedIds().contains(item.id())) return true;
		if (!isBundle(item)) return false;
		return item.bundleContents().isEmpty() || snapshot.profile().ownedIds().contains(item.id())
			|| bundleOwnedCount(snapshot.profile(), item) == item.bundleContents().size();
	}

	private static boolean isBundle(HgnCosmetics.ItemDefinition item) {
		return item != null && (normalizedCategory(item.category()).equals("BUNDLES")
			|| item.tags().contains("cosmetic_bundle"));
	}

	private static int bundleOwnedCount(HgnCosmetics.Profile profile,
		HgnCosmetics.ItemDefinition item) {
		return (int) item.bundleContents().stream().filter(profile.ownedIds()::contains).count();
	}

	private static String bundleContentsLabel(HgnCosmetics.Profile profile,
		HgnCosmetics.ItemDefinition item) {
		int total = item.bundleContents().size();
		return total == 0 ? "BUNDLE CONTENTS UNAVAILABLE"
			: bundleOwnedCount(profile, item) + " / " + total + " CONTENTS OWNED";
	}

	private static boolean isEntitlementOnly(HgnCosmetics.ItemDefinition item) {
		return item.tags().contains("entitlement_only") || item.tags().contains("villager_hat");
	}

	private static String visibleStatus(HgnCosmetics.Profile profile,
		HgnCosmetics.ItemDefinition item) {
		if (profile.ownedIds().contains(item.id())) return "OWNED";
		if (isBundle(item) && item.bundleContents().isEmpty()) return "UNAVAILABLE";
		if (profile.legacyCurrency()) return "CLAIM PAUSED";
		if (item.launcherExclusive() && !profile.launcherLinked()) return "LINK ACCOUNT";
		if (isEntitlementOnly(item)) return unlockLabel(item);
		return item.price() == 0L ? "FREE TO CLAIM" : "PURCHASE IN HGN";
	}

	private static String unlockLabel(HgnCosmetics.ItemDefinition item) {
		if (item.tags().contains("villager_hat")) return "PLAY 2H: VINKSMP / PARSRNG";
		if (item.tags().contains("playtime_vinksmp")) return "PLAY 10H: VINKSMP";
		if (item.tags().contains("playtime_parsrng")) return "PLAY 10H: PARSRNG";
		if (item.tags().contains("playtime_hgn_network")) return "PLAY 10H: HGN NETWORK";
		if (item.tags().contains("owner_uuid")) return "OWNER ONLY";
		if (item.tags().contains("dynamic_staff")) return "ACTIVE HGN STAFF";
		return "ENTITLEMENT REQUIRED";
	}

	private static HgnIcon currencyIcon(HgnCosmetics.Currency currency) {
		return currency == HgnCosmetics.Currency.HGN_GOLDEN_TOKENS
			|| currency == HgnCosmetics.Currency.ACCOUNT_CREDITS
			? HgnIcon.HGN_GOLDEN_TOKENS : HgnIcon.HGN_TOKENS;
	}

	private static int currencyColor(HgnCosmetics.Currency currency) {
		return currency == HgnCosmetics.Currency.HGN_GOLDEN_TOKENS
			|| currency == HgnCosmetics.Currency.ACCOUNT_CREDITS
			? HgnPalette.CYAN : HgnPalette.WARNING;
	}

	private static int connectionColor(HgnCosmetics.ConnectionState state) {
		return switch (state) {
			case CONNECTED -> HgnPalette.SUCCESS;
			case CONNECTING -> HgnPalette.CYAN;
			case OFFLINE_CACHED, OFFLINE, MODELS_UNAVAILABLE -> HgnPalette.WARNING;
			case UNLINKED, EXPIRED, MEMBERSHIP_UNAVAILABLE, LAUNCHER_REQUIRED -> HgnPalette.DANGER;
		};
	}

	private static String normalizedCategory(String value) {
		String category = value == null ? "ALL" : value.toUpperCase(Locale.ROOT).replace('_', ' ');
		return category.equals("ANIMATED EFFECTS") ? "EFFECTS" : category;
	}

	private static String categoryLabel(String value) {
		return value.equals("EFFECTS") ? "Effects"
			: value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String compactCategory(String value) {
		return switch (value) {
			case "BACK ITEMS" -> "BACK";
			case "EFFECTS" -> "FX";
			case "BUNDLES" -> "BUNDLE";
			default -> value;
		};
	}

	private static String categorySubtitle(String value) {
		return switch (value) {
			case "ALL" -> "Browse verified items";
			case "SKINS" -> "Player skins and outfits";
			case "HATS" -> "Head cosmetics";
			case "CAPES" -> "Capes and cloaks";
			case "LIMITED" -> "Owned limited pieces";
			case "WINGS" -> "Cosmetic wings";
			case "BACK ITEMS" -> "Back accessories";
			case "EFFECTS" -> "Animated effects";
			case "EMOTES" -> "Player emotes";
			case "PETS" -> "Companion pets";
			case "BUNDLES" -> "Curated collections";
			default -> "Verified cosmetics";
		};
	}

	private static HgnIcon categoryIcon(String value) {
		return switch (value) {
			case "SKINS" -> HgnIcon.SKINS;
			case "HATS" -> HgnIcon.HATS;
			case "CAPES" -> HgnIcon.CAPES;
			case "WINGS" -> HgnIcon.WINGS;
			case "BACK ITEMS" -> HgnIcon.BACK_ITEMS;
			case "EFFECTS" -> HgnIcon.EFFECTS;
			case "EMOTES" -> HgnIcon.EMOTES;
			case "PETS" -> HgnIcon.PETS;
			case "BUNDLES" -> HgnIcon.BUNDLES;
			default -> HgnIcon.COSMETICS;
		};
	}

	private static int rarityColor(String rarity) {
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "LEGENDARY" -> HgnPalette.WARNING;
			case "EPIC" -> 0xFFE471FF;
			case "RARE" -> HgnPalette.CYAN_SOFT;
			default -> HgnPalette.TEXT_MUTED;
		};
	}

	private static int rarityRank(String rarity) {
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "LEGENDARY" -> 0;
			case "EPIC" -> 1;
			case "RARE" -> 2;
			case "COMMON" -> 3;
			default -> 4;
		};
	}

	@Override
	public void onClose() {
		HgnCosmetics.preview("");
		minecraft.setScreen(parent);
	}

	private enum Sort {
		NAME("NAME"), PRICE("PRICE"), RARITY("RARITY"), REVISION("NEW");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		private Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private enum RarityFilter {
		ALL("ALL"), COMMON("COMMON"), RARE("RARE"), EPIC("EPIC"), LEGENDARY("LEGENDARY");

		private final String label;

		RarityFilter(String label) {
			this.label = label;
		}

		private RarityFilter next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private record CollectionStats(int visible, int owned, int capes, int limited) {
	}
}
