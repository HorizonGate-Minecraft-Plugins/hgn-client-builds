package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only tooltip inspection for font-menu smoke checks after the public getter was removed. */
@Mixin(AbstractWidget.class)
public interface HgnFontWidgetAccessor {
	@Accessor("tooltip")
	WidgetTooltipHolder hgn$fontTooltipHolder();
}
