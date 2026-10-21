package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnPauseActions;
import online.horizongate.hgn.ui.HgnPauseCompactLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;
import online.horizongate.hgn.update.HgnUpdateOfferBridge;
import online.horizongate.hgn.update.HgnUpdateOfferPresenter;

/** A session dashboard retaining native save, disconnect, reporting and server actions. */
public final class HgnPauseScreen extends PauseScreen {
	private HgnDashboardLayout frame;
	private HgnPauseCompactLayout compactLayout;
	private HgnPauseActions actions;
	private int page;
	private HgnUpdateOfferPresenter updates;
	private HgnButton updateButton;
	private HgnButton previousPageButton;
	private HgnButton nextPageButton;
	private HgnButton compactPreviousPageButton;
	private HgnButton compactNextPageButton;
	private boolean restoreUpdateFocus;

	public HgnPauseScreen() {
		super(true);
	}

	@Override
	protected void init() {
		super.init();
		if (updates == null || updates.closed()) {
			updates = new HgnUpdateOfferPresenter(HgnUpdateOfferBridge.resolve(ClassLoader.getSystemClassLoader()), minecraft::execute);
			updates.check(System.nanoTime());
		}
		actions = new HgnPauseActions(children().stream()
			.filter(child -> child instanceof AbstractWidget && !(child instanceof StringWidget))
			.map(child -> (AbstractWidget) child).toList());
		rebuild();
	}

	private void rebuild() {
		AbstractWidget focused = getFocused() instanceof AbstractWidget widget ? widget : null;
		boolean previousFocused = focused == previousPageButton;
		boolean nextFocused = focused == nextPageButton;
		clearWidgets();
		frame = HgnDashboardLayout.resolve(width, height);
		compactLayout = HgnPauseCompactLayout.needed(width, height)
			? HgnPauseCompactLayout.resolve(width, height) : null;
		previousPageButton = null;
		nextPageButton = null;
		if (compactLayout != null) {
			rebuildCompact();
			restoreFocus(focused, previousFocused, nextFocused);
			return;
		}
		page = actions.clampPage(page);
		for (AbstractWidget control : actions.arrange(frame, page)) addRenderableWidget(control);
		button(64, 258, 228, 34, "HGN HUD & style", false,
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		button(64, 304, 228, 34, "Cosmetics & account", false,
			() -> minecraft.setScreen(new HgnCosmeticsScreen(this)));
		updateButton = button(64, 344, 228, 25, "Client updates", false, () -> {
			if (minecraft.screen != this) return;
			restoreUpdateFocus = true;
			minecraft.setScreen(new HgnUpdateConsentScreen(this));
		});
		refreshUpdateLabel();
		if (actions.pages() > 1) {
			previousPageButton = button(688, 140, 34, 25, "<", false,
				() -> { page--; rebuild(); });
			previousPageButton.active = page > 0;
			nextPageButton = button(734, 140, 34, 25, ">", false,
				() -> { page++; rebuild(); });
			nextPageButton.active = page + 1 < actions.pages();
		}
		restoreFocus(focused, previousFocused, nextFocused);
	}

	private void rebuildCompact() {
		page = actions.clampCompactPage(compactLayout, page);
		for (AbstractWidget control : actions.arrangeCompact(compactLayout, page)) addRenderableWidget(control);
		compactButton(compactLayout.essential(3), "HGN HUD & style",
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		compactButton(compactLayout.essential(4), "Cosmetics & account",
			() -> minecraft.setScreen(new HgnCosmeticsScreen(this)));
		updateButton = compactButton(compactLayout.essential(5), "Client updates", () -> {
			if (minecraft.screen != this) return;
			restoreUpdateFocus = true;
			minecraft.setScreen(new HgnUpdateConsentScreen(this));
		});
		refreshUpdateLabel();
		compactPreviousPageButton = compactPageButton(compactPreviousPageButton,
			compactLayout.previous(), "Previous", () -> { page--; rebuild(); });
		previousPageButton = compactPreviousPageButton;
		previousPageButton.active = page > 0;
		compactNextPageButton = compactPageButton(compactNextPageButton,
			compactLayout.next(), "Next", () -> { page++; rebuild(); });
		nextPageButton = compactNextPageButton;
		nextPageButton.active = page + 1 < actions.compactPages(compactLayout);
	}

	private void restoreFocus(AbstractWidget focused, boolean previousFocused, boolean nextFocused) {
		if (focused != null && focused.active && children().contains(focused)) {
			setFocused(focused);
		} else if (previousFocused) {
			focusPageButton(previousPageButton, nextPageButton);
		} else if (nextFocused) {
			focusPageButton(nextPageButton, previousPageButton);
		}
	}

	private void focusPageButton(HgnButton preferred, HgnButton alternate) {
		if (preferred != null && preferred.active) setFocused(preferred);
		else if (alternate != null && alternate.active) setFocused(alternate);
	}

	@Override
	protected void setInitialFocus() {
		if (restoreUpdateFocus && updateButton != null) {
			restoreUpdateFocus = false;
			setInitialFocus(updateButton);
		} else super.setInitialFocus();
	}

	private HgnButton button(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
		var rect = frame.rect(x, y, w, h);
		return addRenderableWidget(HgnButton.dashboardCentered(rect.x(), rect.y(), rect.width(), rect.height(),
			Component.literal(label), primary, ignored -> action.run()).referenceScale(frame.scale() * 0.85f));
	}

	private HgnButton compactButton(HgnRect rect, String label, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(rect.x(), rect.y(), rect.width(), rect.height(),
			Component.literal(label), false, ignored -> action.run()).referenceScale(1.0f));
	}

	private HgnButton compactPageButton(HgnButton existing, HgnRect rect, String label, Runnable action) {
		if (existing == null) return compactButton(rect, label, action);
		existing.setX(rect.x());
		existing.setY(rect.y());
		existing.setWidth(rect.width());
		existing.setHeight(rect.height());
		return addRenderableWidget(existing);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (frame == null || actions == null) return;
		if (compactLayout != null) {
			renderCompactBackground(graphics);
			return;
		}
		HgnSurface.veil(graphics, width, height);
		HgnSurface.panel(graphics, frame.rect(48, 42, 740, 76), true);
		HgnSurface.panel(graphics, frame.rect(48, 130, 260, 299), false);
		HgnSurface.panel(graphics, frame.rect(324, 130, 464, 215), false);
		HgnSurface.panel(graphics, frame.rect(324, 359, 464, 70), false);
		HgnSurface.wordmark(graphics, frame.x(65), frame.y(59), frame.size(92), frame.size(41));
		text(graphics, "PAUSE MENU", 184, 59, 350, 1.3f, HgnPalette.TEXT);
		text(graphics, minecraft.getUser().getName() + "  /  " + sessionName(),
			184, 88, 583, 0.85f, HgnPalette.TEXT_MUTED);
		text(graphics, "ACTIVITY & COMMUNITY", 344, 147, actions.pages() > 1 ? 325 : 424,
			0.92f, HgnPalette.CYAN);
		text(graphics, minecraft.isPaused() ? "WORLD PAUSED" : "YOUR SESSION IS STILL RUNNING",
			344, 375, 424, 0.88f, HgnPalette.CYAN);
		text(graphics, updates == null ? "Client release check unavailable" : updates.offer().title(),
			344, 400, 424, 0.78f, HgnPalette.TEXT_MUTED);
		text(graphics, "ESC  /  RETURN TO GAME", 48, 446, 350, 0.72f, HgnPalette.TEXT_MUTED);
	}

	private void renderCompactBackground(GuiGraphics graphics) {
		HgnSurface.veil(graphics, width, height);
		HgnSurface.panel(graphics, compactLayout.panel(), false);
		HgnRect header = compactLayout.header();
		HgnSurface.separator(graphics, header.x(), header.bottom() + 1, header.width(), true);
		HgnSurface.separator(graphics, header.x(), compactLayout.essential(2).bottom() + 3,
			header.width(), false);
		HgnSurface.separator(graphics, header.x(), compactLayout.footer().y() - 3,
			header.width(), false);
		HgnTypography.text(graphics, font, "HGN PAUSE", header.x() + 3, header.y() + 5,
			1.15f, HgnPalette.CYAN);
		int sessionX = header.x() + 106;
		int sessionWidth = header.right() - sessionX - 3;
		HgnTypography.text(graphics, font,
			HgnTypography.trim(font, sessionName(), Math.round(sessionWidth / 0.9f)),
			sessionX, header.y() + 6, 0.9f, HgnPalette.TEXT_MUTED);
		HgnRect pageLabel = compactLayout.pageLabel();
		HgnTypography.centered(graphics, font,
			"ACTIONS " + (page + 1) + " / " + actions.compactPages(compactLayout),
			pageLabel.centerX(), pageLabel.y() + 6, 0.9f, HgnPalette.TEXT_MUTED);
	}

	private String sessionName() {
		return minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().name
			: minecraft.getSingleplayerServer() != null
				? minecraft.getSingleplayerServer().getWorldData().getLevelName() : "Current session";
	}

	@Override
	public void tick() {
		super.tick();
		if (updates != null) updates.tick(System.nanoTime());
		refreshUpdateLabel();
	}

	private void refreshUpdateLabel() {
		if (updateButton != null && updates != null) updateButton.setMessage(Component.literal(
			"available".equals(updates.offer().status()) ? "New client release" : "Client updates"));
	}

	@Override
	public void removed() {
		if (updates != null) updates.close();
		super.removed();
	}

	private void text(GuiGraphics graphics, String text, int x, int y, int lane, float scale, int color) {
		HgnTypography.fitted(graphics, font, text, frame.x(x), frame.y(y), frame.size(lane), frame.scale() * scale, color);
	}
}
