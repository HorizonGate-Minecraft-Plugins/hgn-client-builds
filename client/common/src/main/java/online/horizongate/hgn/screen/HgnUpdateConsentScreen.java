package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnUpdateOfferLayout;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.update.HgnUpdateRuntime;
import online.horizongate.hgn.update.HgnUpdateTransaction;

/** Explicit review and consent; preparation is never triggered by init, tick, or an offer reply. */
public final class HgnUpdateConsentScreen extends Screen {
	private final Screen parent;
	private final boolean detached;
	private HgnUpdateTransaction transaction;
	private HgnUpdateOfferLayout layout;
	private HgnButton primary, back, refresh;
	private String narrated = "";

	public HgnUpdateConsentScreen(Screen parent) { this(parent, null, false); }
	/** Detached smoke accepts fake effects and never attaches to the production runtime. */
	public HgnUpdateConsentScreen(Screen parent, HgnUpdateTransaction transaction) { this(parent, transaction, true); }
	private HgnUpdateConsentScreen(Screen parent, HgnUpdateTransaction transaction, boolean detached) {
		super(Component.literal("HGN Client update and restart"));
		this.parent = parent;
		this.transaction = transaction;
		this.detached = detached;
	}

	@Override
	protected void init() {
		clearWidgets();
		layout = HgnUpdateOfferLayout.resolve(width, height);
		primary = addRenderableWidget(HgnButton.centered(0, 0, 1, 24, Component.literal("Update and restart"), true, ignored -> {
			if (minecraft.screen != this || transaction == null) return;
			if (transaction.canCancel()) transaction.cancel();
			else transaction.accept(System.nanoTime());
			refreshControls();
		}));
		back = addRenderableWidget(HgnButton.centered(0, 0, 1, 24, Component.literal("Back"), false, ignored -> onClose()));
		refresh = addRenderableWidget(HgnButton.centered(0, 0, 62, 20, Component.literal("Recheck"), false, ignored -> {
			if (minecraft.screen == this && transaction != null && !transaction.busy()) transaction.check(System.nanoTime());
			refreshControls();
		}));
		if (transaction == null && !detached) transaction = HgnUpdateRuntime.attach(this, minecraft);
		if (transaction != null && transaction.phase() == HgnUpdateTransaction.Phase.IDLE) transaction.check(System.nanoTime());
		repositionElements();
		refreshControls();
	}

	@Override
	protected void setInitialFocus() { if (back != null) setInitialFocus(back); else super.setInitialFocus(); }

	@Override
	protected void repositionElements() {
		if (primary == null) return;
		layout = HgnUpdateOfferLayout.resolve(width, height);
		place(primary, layout.check());
		place(back, layout.back());
		place(refresh, new HgnRect(layout.panel().right() - 74, layout.panel().y() + 6, 62, 20));
	}

	private static void place(HgnButton button, HgnRect bounds) {
		button.setX(bounds.x()); button.setY(bounds.y()); button.setWidth(bounds.width()); button.setHeight(bounds.height());
	}

	private void refreshControls() {
		if (primary == null) return;
		boolean cancel = transaction != null && transaction.canCancel();
		primary.setMessage(Component.literal(cancel ? "Cancel update" : "Update and restart"));
		primary.active = transaction != null && (cancel || transaction.canAccept());
		back.active = transaction == null || !transaction.committed();
		back.setMessage(Component.literal(cancel ? "Cancel and back" : "Back"));
		refresh.active = transaction != null && !transaction.busy() && !transaction.closed();
		primary.setTooltip(Tooltip.create(getNarrationMessage()));
	}

	@Override
	public void tick() {
		if (detached && transaction != null) transaction.tick(System.nanoTime());
		refreshControls();
		String message = getNarrationMessage().getString();
		if (minecraft.screen == this && !message.equals(narrated)) { narrated = message; triggerImmediateNarration(false); }
	}

	@Override
	public Component getNarrationMessage() {
		if (transaction == null) return Component.literal("Another update handoff is still active. Close this page and try later.");
		var offer = transaction.offer();
		return Component.literal(transaction.title() + ". " + transaction.detail()
			+ (offer.currentVersion() == null ? "" : " Installed baseline: " + offer.currentVersion())
			+ (offer.candidateVersion() == null ? "" : ". Candidate: " + offer.candidateVersion() + ", " + offer.releaseName())
			+ ". Only Update and restart authorizes preparation, then native disconnect and closing Minecraft."
			+ " The launcher installs after this process exits and attempts to rejoin the same eligible multiplayer server. Restart can fail."
			+ " Automatic restart currently supports eligible multiplayer only. Singleplayer is unavailable because successful world saving cannot be confirmed."
			+ " Resolve any pending report draft before updating.");
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null) return;
		HgnSurface.veil(graphics, width, height);
		HgnSurface.panel(graphics, layout.panel(), false);
		var lane = layout.content();
		line(graphics, "HGN / UPDATE", lane.x(), layout.panel().y() + 12, lane.width() - 72, HgnPalette.CYAN);
		HgnSurface.inset(graphics, lane, transaction != null && transaction.canAccept());
		int x = lane.x() + 8, w = lane.width() - 16, y = lane.y() + 6;
		int step = layout.compact() ? 12 : 24;
		line(graphics, transaction == null ? "Another update is still active" : transaction.title(), x, y, w, HgnPalette.TEXT);
		line(graphics, transaction == null ? "Return after that handoff finishes." : transaction.detail(), x, y + step, w, HgnPalette.TEXT_MUTED);
		if (transaction != null) {
			var offer = transaction.offer();
			line(graphics, offer.currentVersion() == null ? "No verified baseline available" : "Installed: " + offer.currentVersion(), x, y + step * 2, w, HgnPalette.TEXT);
			line(graphics, offer.candidateVersion() == null ? "No actionable candidate" : "Candidate: " + offer.candidateVersion(), x, y + step * 3, w, HgnPalette.CYAN);
			line(graphics, offer.releaseName() == null ? "No files changed by checking" : offer.releaseName(), x, y + step * 4, w, HgnPalette.TEXT_MUTED);
		}
		line(graphics, "Multiplayer only / disconnect and restart", x, lane.bottom() - 13, w, HgnPalette.TEXT_MUTED);
		for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.render(graphics, mouseX, mouseY, partialTick);
	}

	private void line(GuiGraphics graphics, String value, int x, int y, int width, int color) {
		HgnTypography.text(graphics, font, HgnTypography.trim(font, value, width), x, y, color);
	}

	@Override
	public void onClose() {
		if (transaction != null && transaction.committed()) return;
		if (transaction != null) transaction.close();
		minecraft.setScreen(minecraft.level == null && !detached ? new TitleScreen() : parent);
	}

	@Override
	public void removed() {
		if (transaction != null) transaction.close();
		if (!detached) HgnUpdateRuntime.removed(this);
		super.removed();
	}
}
