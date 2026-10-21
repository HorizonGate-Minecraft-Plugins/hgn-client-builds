package online.horizongate.hgn.ui.container;

import net.minecraft.world.item.CreativeModeTab;

/** Native creative state exposed through remapped mixin fields. */
public interface HgnCreativeState {
	CreativeModeTab hgn$selectedTab();

	float hgn$scrollOffset();

	net.minecraft.client.gui.components.EditBox hgn$searchBox();
}
