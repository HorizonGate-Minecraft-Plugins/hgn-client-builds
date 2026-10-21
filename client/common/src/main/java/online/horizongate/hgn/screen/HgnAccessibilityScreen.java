package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSettingsLayout;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Readable entry points for native accessibility, language and HGN comfort settings. */
public final class HgnAccessibilityScreen extends Screen {
	private final Screen parent;
	private HgnSettingsLayout layout;
	private final HgnButton[] destinations = new HgnButton[2];
	private static final String[] TITLES = {"Minecraft accessibility", "Language"};
	private static final String[] DETAILS = {"Narrator, subtitles, text and visual options.",
		"Choose Minecraft's language and font options."};

	public HgnAccessibilityScreen(Screen parent) {
		super(Component.literal("Accessibility / Language"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearWidgets();
		layout = HgnSettingsLayout.resolve(width, height, 2, false);
		for (int index = 0; index < destinations.length; index++) {
			int destination = index;
			HgnRect bounds = layout.row(index);
			destinations[index] = addRenderableWidget(HgnButton.hitTarget(bounds.x(), bounds.y(),
				bounds.width(), bounds.height(), Component.literal(TITLES[index] + ". " + DETAILS[index]),
				ignored -> open(destination)));
		}
		button(layout.footerButton(0, 2), "HGN settings", false,
			() -> minecraft.setScreen(new HgnClientSettingsScreen(this)));
		button(layout.footerButton(1, 2), "Back", true, this::onClose);
	}

	private void open(int destination) {
		minecraft.setScreen(destination == 0
			? new AccessibilityOptionsScreen(this, minecraft.options)
			: HgnCompat.languageScreen(this, minecraft));
	}

	private void button(HgnRect bounds, String title, boolean primary, Runnable action) {
		addRenderableWidget(HgnButton.dashboardCentered(bounds.x(), bounds.y(), bounds.width(),
			bounds.height(), Component.literal(title), primary, ignored -> action.run()).referenceScale(1.0f));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.panel(), false);
		HgnTypography.fitted(graphics, font, "ACCESSIBILITY / LANGUAGE", layout.content().x(),
			layout.headerTitleY(), layout.content().width(), 1.15f, HgnPalette.TEXT);
		HgnTypography.fitted(graphics, font, "Minecraft tools for comfortable play.", layout.content().x(),
			layout.panel().y() + 34, layout.content().width(), 0.95f, HgnPalette.mutedText());
		for (int index = 0; index < destinations.length; index++) {
			HgnRect bounds = layout.row(index);
			HgnSurface.dashboardInset(graphics, bounds, destinations[index].isHoveredOrFocused());
			HgnTypography.fitted(graphics, font, TITLES[index], bounds.x() + 12, layout.rowTitleY(index),
				bounds.width() - 78, 1.05f, HgnPalette.TEXT);
			HgnTypography.fitted(graphics, font, "OPEN >", bounds.right() - 53, layout.rowTitleY(index) + 1,
				41, 0.9f, HgnPalette.CYAN);
			HgnTypography.fitted(graphics, font, DETAILS[index], bounds.x() + 12, layout.rowHelpY(index),
				bounds.width() - 24, 0.95f, HgnPalette.mutedText());
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
