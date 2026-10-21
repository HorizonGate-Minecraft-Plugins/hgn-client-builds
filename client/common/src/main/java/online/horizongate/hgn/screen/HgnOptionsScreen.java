package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Custom settings hub retaining the game's real options, callbacks and save lifecycle. */
public final class HgnOptionsScreen extends OptionsScreen {
	private final List<AbstractWidget> controls = new ArrayList<>();
	private HgnDashboardLayout frame;
	private HgnRect compactPanel;
	private boolean compact;
	private int page;
	private boolean collecting;
	private int smokeVideoWidth = -1;
	private int smokeVideoHeight = -1;

	public HgnOptionsScreen(Screen parent, Options options) {
		super(parent, options);
	}

	@Override
	protected void init() {
		collecting = true;
		super.init();
		controls.clear();
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && !(widget instanceof StringWidget)
				&& !widget.getMessage().equals(Component.translatable("gui.done"))) controls.add(widget);
		}
		collecting = false;
		rebuild();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (!collecting && !controls.isEmpty()) rebuild();
	}

	private void rebuild() {
		int focusedOption = controls.indexOf(getFocused());
		clearWidgets();
		frame = HgnDashboardLayout.resolve(width, height);
		compact = width < 680 || height < 380;
		int pages = Math.max(1, (controls.size() + 7) / 8);
		page = Math.min(page, pages - 1);
		if (compact) {
			rebuildCompact(pages);
		} else {
			rebuildWide(pages);
		}
		if (focusedOption >= page * 8 && focusedOption < Math.min(controls.size(), page * 8 + 8)) {
			setFocused(controls.get(focusedOption));
		}
	}

	private void rebuildWide(int pages) {
		for (int index = 0; index < pages; index++) {
			int choice = index;
			button(34, 137 + index * 39, 165, 30, "Game options " + (index + 1), page == index,
				() -> { page = choice; rebuild(); focusPage(choice); });
		}
		button(34, 276, 165, 32, "HGN HUD & style", false,
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		button(34, 319, 165, 32, "Accessibility / language", false,
			() -> minecraft.setScreen(new HgnAccessibilityScreen(this)));
		for (int index = page * 8; index < Math.min(controls.size(), page * 8 + 8); index++) {
			AbstractWidget widget = controls.get(index);
			int cell = index - page * 8;
			var rect = frame.rect(247 + (cell % 2) * 279, 135 + (cell / 2) * 61, 256, 34);
			widget.setX(rect.x());
			widget.setY(rect.y());
			widget.setWidth(rect.width());
			widget.setHeight(rect.height());
			addRenderableWidget(widget);
		}
		button(526, 392, 256, 30, "Done", true, this::onClose);
	}

	private void rebuildCompact(int pages) {
		int margin = Math.max(4, Math.min(8, Math.min(width, height) / 24));
		compactPanel = new HgnRect(margin, margin, width - margin * 2, height - margin * 2);
		int gap = height < 210 ? 3 : 5;
		int tabs = pages + 2;
		int tabWidth = (compactPanel.width() - 16 - gap * (tabs - 1)) / tabs;
		int tabY = compactPanel.y() + 20;
		for (int index = 0; index < pages; index++) {
			int choice = index;
			button(new HgnRect(compactPanel.x() + 8 + index * (tabWidth + gap), tabY,
				tabWidth, 20), "Game options " + (index + 1), page == index,
				() -> { page = choice; rebuild(); focusPage(choice); });
		}
		button(new HgnRect(compactPanel.x() + 8 + pages * (tabWidth + gap), tabY,
			tabWidth, 20), "HUD & style", false,
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		button(new HgnRect(compactPanel.x() + 8 + (pages + 1) * (tabWidth + gap), tabY,
			tabWidth, 20), "Accessibility", false,
			() -> minecraft.setScreen(new HgnAccessibilityScreen(this)));
		int footerY = compactPanel.bottom() - 22;
		int rowTop = tabY + 24;
		int rowHeight = Math.max(20, Math.min(28, (footerY - 5 - rowTop - gap * 3) / 4));
		rowTop += Math.max(0, (footerY - 5 - rowTop - (rowHeight * 4 + gap * 3)) / 2);
		int cellWidth = (compactPanel.width() - 16 - gap) / 2;
		for (int index = page * 8; index < Math.min(controls.size(), page * 8 + 8); index++) {
			AbstractWidget widget = controls.get(index);
			int cell = index - page * 8;
			widget.setX(compactPanel.x() + 8 + (cell % 2) * (cellWidth + gap));
			widget.setY(rowTop + (cell / 2) * (rowHeight + gap));
			widget.setWidth(cellWidth);
			widget.setHeight(rowHeight);
			addRenderableWidget(widget);
		}
		button(new HgnRect(compactPanel.right() - 118, footerY, 110, 20), "Done", true, this::onClose);
	}

	private void focusPage(int choice) {
		String title = "Game options " + (choice + 1);
		for (var child : children()) {
			if (child instanceof HgnButton button && button.getMessage().getString().equals(title)) {
				setFocused(button);
				return;
			}
		}
	}

	private void button(int x, int y, int w, int h, String label, boolean selected, Runnable action) {
		var rect = frame.rect(x, y, w, h);
		button(rect, label, selected, action).referenceScale(frame.scale() * 0.85f);
	}

	private HgnButton button(HgnRect rect, String label, boolean selected, Runnable action) {
		return addRenderableWidget(HgnButton.dashboardCentered(rect.x(), rect.y(), rect.width(), rect.height(),
			Component.literal(label), selected, ignored -> action.run()).referenceScale(1.0f));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		if (compact) {
			HgnSurface.panel(graphics, compactPanel, false);
			HgnTypography.fitted(graphics, font, "HGN SETTINGS", compactPanel.x() + 8,
				compactPanel.y() + 5, compactPanel.width() - 16, 1.05f, HgnPalette.CYAN);
			super.render(graphics, mouseX, mouseY, partialTick);
			logVideoButtonForSmoke();
			return;
		}
		HgnSurface.panel(graphics, frame.rect(20, 45, 194, 389), false);
		HgnSurface.panel(graphics, frame.rect(226, 45, 588, 389), false);
		text(graphics, "HGN SETTINGS", 35, 70, 1.1f, HgnPalette.CYAN);
		text(graphics, "YOUR GAME. YOUR WAY.", 35, 98, 0.66f, HgnPalette.TEXT_MUTED);
		text(graphics, "Minecraft preferences", 247, 70, 1.3f, HgnPalette.TEXT);
		text(graphics, "Video, sound, controls, appearance and more", 247, 99, 0.82f, HgnPalette.TEXT_MUTED);
		text(graphics, "Inventories use the original Minecraft layout.", 247, 366, 0.8f, HgnPalette.TEXT_MUTED);
		super.render(graphics, mouseX, mouseY, partialTick);
		logVideoButtonForSmoke();
	}

	private void logVideoButtonForSmoke() {
		if (!HgnClient.videoRuntimeSmoke() || minecraft.screen != this
			|| (smokeVideoWidth == width && smokeVideoHeight == height)) return;
		String videoLabel = Component.translatable("options.video").getString();
		AbstractWidget video = controls.stream()
			.filter(widget -> children().contains(widget) && widget.getMessage().getString().equals(videoLabel))
			.findFirst().orElseThrow(() -> new AssertionError("Native Video Settings button is not visible in HGN Options"));
		smokeVideoWidth = width;
		smokeVideoHeight = height;
		HgnClient.LOGGER.info("HGN_SODIUM_VIDEO_BUTTON={},{}:{},{}", width, height,
			video.getX() + video.getWidth() / 2, video.getY() + video.getHeight() / 2);
	}

	private void text(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
		HgnTypography.text(graphics, font, text, frame.x(x), frame.y(y), frame.scale() * scale, color);
	}
}
