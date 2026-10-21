package online.horizongate.hgn.ui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnWidgetBase;
import online.horizongate.hgn.ui.HgnControlRenderer;
import online.horizongate.hgn.ui.HgnIcon;

/** Clean-room HGN Client command button. */
public final class HgnButton extends HgnWidgetBase {
	private final HgnIcon icon;
	private final boolean primary;
	private final boolean leftAligned;
	private final boolean visibleSurface;
	private final boolean dashboardSurface;
	private boolean selected;
	private float referenceScale;
	private float referenceIconScale;

	private HgnButton(int x, int y, int width, int height, Component message, OnPress onPress,
		HgnIcon icon, boolean primary, boolean leftAligned, boolean visibleSurface,
		boolean dashboardSurface) {
		super(x, y, width, height, message, onPress);
		this.icon = icon == null ? HgnIcon.NONE : icon;
		this.primary = primary;
		this.leftAligned = leftAligned;
		this.visibleSurface = visibleSurface;
		this.dashboardSurface = dashboardSurface;
	}

	public static HgnButton command(int x, int y, int width, int height, Component message,
		HgnIcon icon, boolean primary, OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, icon, primary,
			true, true, false);
	}

	public static HgnButton dashboardCommand(int x, int y, int width, int height,
		Component message, HgnIcon icon, boolean primary, OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, icon, primary,
			true, true, true);
	}

	/** Dashboard card with its icon stacked above a full-width title. */
	public static HgnButton feature(int x, int y, int width, int height, Component message,
		HgnIcon icon, OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, icon, false,
			false, true, true);
	}

	public static HgnButton centered(int x, int y, int width, int height, Component message,
		boolean primary, OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, HgnIcon.NONE,
			primary, false, true, false);
	}

	public static HgnButton dashboardCentered(int x, int y, int width, int height,
		Component message, boolean primary, OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, HgnIcon.NONE,
			primary, false, true, true);
	}

	public static HgnButton iconOnly(int x, int y, int width, int height,
		HgnIcon icon, OnPress onPress) {
		return new HgnButton(x, y, width, height, Component.empty(), onPress,
			icon, false, false, true, false);
	}

	public static HgnButton dashboardIconOnly(int x, int y, int width, int height,
		HgnIcon icon, OnPress onPress) {
		return new HgnButton(x, y, width, height, Component.empty(), onPress,
			icon, false, false, true, true);
	}

	/** Accessible click target for a surface painted by its owning screen. */
	public static HgnButton hitTarget(int x, int y, int width, int height, Component message,
		OnPress onPress) {
		return new HgnButton(x, y, width, height, message, onPress, HgnIcon.NONE,
			false, false, false, false);
	}

	public HgnButton selected(boolean value) {
		this.selected = value;
		return this;
	}

	public HgnButton referenceScale(float scale) {
		this.referenceScale = scale;
		this.referenceIconScale = scale;
		return this;
	}

	public HgnButton referenceScale(float textScale, float iconScale) {
		this.referenceScale = textScale;
		this.referenceIconScale = iconScale;
		return this;
	}

	@Override
	protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (!visibleSurface) {
			if (isFocused()) online.horizongate.hgn.ui.HgnSurface.focus(graphics,
				getX(), getY(), getWidth(), getHeight());
			return;
		}
		if (referenceScale > 0) {
			HgnControlRenderer.renderReference(this, graphics, icon, primary,
				referenceScale, referenceIconScale);
			return;
		}
		HgnControlRenderer.render(this, graphics, icon, primary, selected,
			leftAligned, dashboardSurface);
	}
}
