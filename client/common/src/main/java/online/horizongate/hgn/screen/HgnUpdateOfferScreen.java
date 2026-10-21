package online.horizongate.hgn.screen;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.HgnUpdateOfferLayout;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.update.HgnUpdateOfferBridge;
import online.horizongate.hgn.update.HgnUpdateOffer;
import online.horizongate.hgn.update.HgnUpdateOfferPresenter;

/** Information only: no install, exit, restart, browser or destination actions. */
public final class HgnUpdateOfferScreen extends Screen {
	private final Screen parent;
	private final Supplier<CompletionStage<String>> sender;
	private HgnUpdateOfferPresenter presenter;
	private HgnUpdateOfferLayout layout;
	private HgnButton check;
	private HgnButton back;
	private HgnUpdateOffer narrated;

	public HgnUpdateOfferScreen(Screen parent) {
		this(parent, HgnUpdateOfferBridge.resolve(ClassLoader.getSystemClassLoader()));
	}

	/** Injectable read-only source allows detached smoke without opening the launcher bridge. */
	public HgnUpdateOfferScreen(Screen parent, Supplier<CompletionStage<String>> sender) {
		super(Component.literal("HGN Client updates"));
		this.parent = parent;
		this.sender = sender;
	}

	@Override
	protected void init() {
		clearWidgets();
		layout = HgnUpdateOfferLayout.resolve(width, height);
		var checkBounds = layout.check();
		check = addRenderableWidget(HgnButton.centered(checkBounds.x(), checkBounds.y(), checkBounds.width(), checkBounds.height(),
			Component.literal("Check again"), false, ignored -> request()));
		var backBounds = layout.back();
		back = addRenderableWidget(HgnButton.centered(backBounds.x(), backBounds.y(), backBounds.width(), backBounds.height(),
			Component.literal("Back"), true, ignored -> onClose()));
		if (presenter == null || presenter.closed()) {
			presenter = new HgnUpdateOfferPresenter(sender, minecraft::execute);
			request();
		}
		refreshControl();
		narrated = presenter.offer();
	}

	@Override
	protected void setInitialFocus() {
		if (back != null) setInitialFocus(back);
		else super.setInitialFocus();
	}

	@Override
	protected void repositionElements() {
		if (check == null || back == null) return;
		layout = HgnUpdateOfferLayout.resolve(width, height);
		place(check, layout.check());
		place(back, layout.back());
	}

	private static void place(HgnButton button, online.horizongate.hgn.ui.HgnRect bounds) {
		button.setX(bounds.x());
		button.setY(bounds.y());
		button.setWidth(bounds.width());
		button.setHeight(bounds.height());
	}

	private void request() {
		if (presenter == null || presenter.closed()) return;
		presenter.check(System.nanoTime());
		refreshControl();
	}

	private void refreshControl() {
		if (check != null && presenter != null) {
			check.setMessage(Component.literal(presenter.pending() ? "Checking..." : "Check again"));
			check.active = !presenter.pending();
		}
	}

	@Override
	public void tick() {
		if (presenter != null) presenter.tick(System.nanoTime());
		refreshControl();
		if (presenter != null && minecraft.screen == this && !presenter.offer().equals(narrated)) {
			narrated = presenter.offer();
			triggerImmediateNarration(false);
		}
	}

	@Override
	public Component getNarrationMessage() {
		return Component.literal("HGN Client updates. " + (presenter == null ? "" : presenter.offer().narration()));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null || presenter == null) return;
		HgnSurface.veil(graphics, width, height);
		HgnSurface.panel(graphics, layout.panel(), false);
		var lane = layout.content();
		var offer = presenter.offer();
		line(graphics, "HGN / CLIENT UPDATES", lane.x(), layout.panel().y() + 12, lane.width(), HgnPalette.CYAN);
		HgnSurface.inset(graphics, lane, "available".equals(offer.status()));
		int x = lane.x() + 8, w = lane.width() - 16;
		int y = lane.y() + 9, step = layout.compact() ? 15 : 24;
		line(graphics, offer.title(), x, y, w, HgnPalette.TEXT);
		line(graphics, offer.detail(), x, y + step, w, HgnPalette.TEXT_MUTED);
		if (offer.currentVersion() != null) line(graphics, "Installed baseline: " + offer.currentVersion(), x, y + step * 2, w, HgnPalette.TEXT);
		if (offer.candidateVersion() != null) line(graphics, "Candidate: " + offer.candidateVersion() + " / " + offer.releaseName(), x, y + step * 3, w, HgnPalette.CYAN);
		line(graphics, "No files changed. No restart is available here.", x, lane.bottom() - 15, w, HgnPalette.TEXT_MUTED);
		for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.render(graphics, mouseX, mouseY, partialTick);
	}

	private void line(GuiGraphics graphics, String value, int x, int y, int width, int color) {
		HgnTypography.text(graphics, font, HgnTypography.trim(font, value, width), x, y, color);
	}

	@Override
	public void removed() {
		if (presenter != null) presenter.close();
		super.removed();
	}

	@Override
	public void onClose() {
		if (presenter == null || presenter.closed()) return;
		presenter.close();
		minecraft.setScreen(parent);
	}
}
