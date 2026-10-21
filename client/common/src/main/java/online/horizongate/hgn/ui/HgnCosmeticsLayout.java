package online.horizongate.hgn.ui;

/** Native-pixel Cosmetics geometry; small windows switch details into a separate pane. */
public record HgnCosmeticsLayout(boolean compact, boolean wideStage, HgnRect header,
	HgnRect rail, HgnRect stage, HgnRect catalogue,
	HgnRect profile, HgnRect featured, HgnRect search, HgnRect rarity,
	HgnRect sort, HgnRect owned, HgnRect cards, HgnRect previous,
	HgnRect next, HgnRect preview, HgnRect selection, HgnRect equipped, HgnRect stats,
	HgnRect wallet, HgnRect back, HgnRect categoryPager,
	int categoryPageSize, int columns, int rows) {

	public static HgnCosmeticsLayout resolve(int width, int height) {
		int margin = clamp(Math.min(width, height) / 52, 4, 10);
		boolean wideStage = width >= 640 && height >= 360;
		boolean denseReference = wideStage && (width < 800 || height < 420);
		int headerHeight = wideStage ? denseReference ? 60
			: clamp(height * 16 / 100, 80, 132) : 0;
		int canvasWidth = Math.min(Math.max(0, width - margin * 2), wideStage ? 1600 : 1000);
		int canvasHeight = Math.min(Math.max(0, height - margin * 2 - headerHeight),
			wideStage ? 800 : 600);
		int left = (width - canvasWidth) / 2;
		int top = (height - canvasHeight - headerHeight) / 2 + headerHeight;
		HgnRect header = new HgnRect(left, top - headerHeight, canvasWidth, headerHeight);
		boolean compact = width < 500 || height < 260;
		int gap = compact ? 4 : 6;
		int railWidth = denseReference ? 100 : wideStage ? clamp(canvasWidth * 14 / 100, 125, 250)
			: compact ? clamp(canvasWidth * 21 / 100, 62, 80)
			: clamp(canvasWidth * 17 / 100, 88, 170);
		int stageWidth = denseReference ? 84
			: wideStage ? clamp(canvasWidth * 22 / 100, 180, 320) : 0;
		int profileWidth = compact ? 0 : denseReference ? 105 : wideStage
			? clamp(canvasWidth * 17 / 100, 140, 270)
			: clamp(canvasWidth * 25 / 100, 124, 250);
		HgnRect rail = new HgnRect(left, top, railWidth, canvasHeight);
		HgnRect stage = wideStage
			? new HgnRect(rail.right() + gap, top, stageWidth, canvasHeight)
			: new HgnRect(0, 0, 0, 0);
		int contentX = wideStage ? stage.right() + gap : rail.right() + gap;
		int contentWidth = Math.max(1, canvasWidth - railWidth - gap
			- (wideStage ? stageWidth + gap : 0)
			- (compact ? 0 : profileWidth + gap));
		HgnRect catalogue = new HgnRect(contentX, top, contentWidth, canvasHeight);
		HgnRect profile = compact
			? new HgnRect(contentX, top, contentWidth, canvasHeight)
			: new HgnRect(catalogue.right() + gap, top, profileWidth, canvasHeight);
		int featuredHeight = compact ? canvasHeight < 190 ? 30 : 40 : wideStage
			? clamp(canvasHeight * 29 / 100, 96, 170)
			: canvasHeight < 390 ? 54 : 70;
		HgnRect featured = new HgnRect(catalogue.x() + 5, top + 20,
			catalogue.width() - 10, featuredHeight);
		int controlsY = featured.bottom() + 4;
		HgnRect search;
		HgnRect rarity;
		HgnRect sort;
		HgnRect owned;
		int controlsBottom;
		if (compact) {
			search = new HgnRect(catalogue.x() + 5, controlsY,
				catalogue.width() - 10, 13);
			int filterY = search.bottom() + 2;
			int filterWidth = (catalogue.width() - 16) / 3;
			rarity = new HgnRect(catalogue.x() + 5, filterY, filterWidth, 14);
			sort = new HgnRect(rarity.right() + 3, filterY, filterWidth, 14);
			owned = new HgnRect(sort.right() + 3, filterY,
				catalogue.right() - 5 - sort.right() - 3, 14);
			controlsBottom = owned.bottom();
		} else {
			int innerWidth = catalogue.width() - 10;
			int searchWidth = Math.max(90, innerWidth * 37 / 100);
			int filterWidth = (innerWidth - searchWidth - 9) / 3;
			search = new HgnRect(catalogue.x() + 5, controlsY, searchWidth, 18);
			rarity = new HgnRect(search.right() + 3, controlsY, filterWidth, 18);
			sort = new HgnRect(rarity.right() + 3, controlsY, filterWidth, 18);
			owned = new HgnRect(sort.right() + 3, controlsY,
				catalogue.right() - 5 - sort.right() - 3, 18);
			controlsBottom = search.bottom();
		}
		int footerY = catalogue.bottom() - 19;
		HgnRect cards = new HgnRect(catalogue.x() + 5, controlsBottom + 5,
			catalogue.width() - 10, Math.max(1, footerY - controlsBottom - 9));
		int columns = wideStage ? clamp(cards.width() / 67, 4, 5)
			: clamp(cards.width() / 90, 3, 5);
		int rows = compact ? 1 : clamp(cards.height() / 92, 1, 2);
		int pagingWidth = compact ? 23 : 29;
		HgnRect previous = new HgnRect(catalogue.centerX() - pagingWidth - 23,
			footerY + 1, pagingWidth, 14);
		HgnRect next = new HgnRect(catalogue.centerX() + 23,
			footerY + 1, pagingWidth, 14);
		HgnRect preview;
		HgnRect selection;
		HgnRect equipped;
		HgnRect stats = new HgnRect(0, 0, 0, 0);
		HgnRect wallet;
		if (compact) {
			int previewWidth = clamp((profile.width() - 14) * 40 / 100, 80, 124);
			int previewHeight = Math.min(83, profile.height() - 80);
			preview = new HgnRect(profile.x() + 5, profile.y() + 20,
				previewWidth, previewHeight);
			selection = new HgnRect(preview.right() + 4, preview.y(),
				profile.right() - preview.right() - 9, previewHeight);
			wallet = new HgnRect(profile.x() + 5, profile.bottom() - 35,
				profile.width() - 10, 30);
			equipped = new HgnRect(profile.x() + 5, preview.bottom() + 4,
				profile.width() - 10, Math.max(1, wallet.y() - preview.bottom() - 8));
		} else if (wideStage) {
			preview = new HgnRect(stage.x() + 5, stage.y() + 5,
				stage.width() - 10, stage.height() - 10);
			selection = new HgnRect(profile.x() + 5, profile.y() + 45,
				profile.width() - 10, 70);
			wallet = new HgnRect(profile.x() + 5, profile.bottom() - 60,
				profile.width() - 10, 55);
			stats = new HgnRect(profile.x() + 5, wallet.y() - 54,
				profile.width() - 10, 50);
			equipped = new HgnRect(profile.x() + 5, selection.bottom() + 4,
				profile.width() - 10, Math.max(1, stats.y() - selection.bottom() - 8));
		} else {
			int previewHeight = Math.min(160, Math.max(89, profile.height() * 32 / 100));
			preview = new HgnRect(profile.x() + 5, profile.y() + 20,
				profile.width() - 10, previewHeight);
			selection = new HgnRect(profile.x() + 5, preview.bottom() + 4,
				profile.width() - 10, 68);
			wallet = new HgnRect(profile.x() + 5, profile.bottom() - 72,
				profile.width() - 10, 67);
			equipped = new HgnRect(profile.x() + 5, selection.bottom() + 4,
				profile.width() - 10, Math.max(1, wallet.y() - selection.bottom() - 8));
		}
		int backHeight = compact ? 14 : 18;
		HgnRect back = new HgnRect(rail.x() + 5,
			wideStage ? rail.y() + 5 : rail.bottom() - backHeight - 5,
			rail.width() - 10, backHeight);
		HgnRect categoryPager = compact
			? new HgnRect(rail.x() + 5, back.y() - 17, rail.width() - 10, 14)
			: new HgnRect(0, 0, 0, 0);
		return new HgnCosmeticsLayout(compact, wideStage, header, rail, stage, catalogue, profile, featured,
			search, rarity, sort, owned, cards, previous, next, preview, selection,
			equipped, stats, wallet, back, categoryPager, compact ? 6 : 11, columns, rows);
	}

	public HgnRect category(int visibleIndex) {
		int firstY = rail.y() + (compact ? 26 : wideStage ? 32 : 42);
		int gap = wideStage ? 3 : 2;
		int availableBottom = compact ? profileShortcut().y() - 3
			: wideStage ? rail.bottom() - 5 : back.y() - 4;
		int rowHeight = Math.max(10,
			Math.min(compact ? 15 : wideStage ? 43 : 23,
				(availableBottom - firstY - gap * (categoryPageSize - 1)) / categoryPageSize));
		return new HgnRect(rail.x() + 5, firstY + visibleIndex * (rowHeight + gap),
			rail.width() - 10, rowHeight);
	}

	public HgnRect card(int index) {
		int gap = compact ? 3 : 5;
		int cardWidth = (cards.width() - gap * (columns - 1)) / columns;
		int cardHeight = (cards.height() - gap * (rows - 1)) / rows;
		return new HgnRect(cards.x() + index % columns * (cardWidth + gap),
			cards.y() + index / columns * (cardHeight + gap), cardWidth, cardHeight);
	}

	public HgnRect emptyState() {
		return cards.inset(Math.min(8, Math.min(cards.width(), cards.height()) / 8));
	}

	public HgnRect emptyAction() {
		HgnRect empty = emptyState();
		int width = Math.min(150, empty.width() - 12);
		int height = Math.min(16, Math.max(12, empty.height() / 4));
		return new HgnRect(empty.centerX() - width / 2,
			empty.bottom() - height - 4, width, height);
	}

	public HgnRect profileShortcut() {
		return compact ? new HgnRect(rail.x() + 5, categoryPager.y() - 17,
			rail.width() - 10, 14) : new HgnRect(0, 0, 0, 0);
	}

	public HgnRect previewModel() {
		HgnRect inset = preview.inset(3);
		return new HgnRect(inset.x(), inset.y(), inset.width(),
			Math.max(1, inset.height() - (wideStage ? 32 : 18)));
	}

	public boolean denseReference() {
		return wideStage && rail.width() == 100;
	}

	public boolean shortWalletLabels() {
		return wallet.width() < 130;
	}

	public HgnRect walletTokenLabel() {
		int valueWidth = wallet.width() / 2;
		return new HgnRect(wallet.x() + 5, wallet.y() + 29,
			wallet.width() - valueWidth - 13, 10);
	}

	public HgnRect walletTokenValue() {
		int valueWidth = wallet.width() / 2;
		return new HgnRect(wallet.right() - valueWidth - 5, wallet.y() + 29,
			valueWidth, 10);
	}

	public static float walletTextScale(float preferredScale, int availableWidth, int textWidth) {
		return Math.min(preferredScale, Math.max(1, availableWidth) / (float) Math.max(1, textWidth));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
