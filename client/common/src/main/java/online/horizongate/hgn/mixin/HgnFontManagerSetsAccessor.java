package online.horizongate.hgn.mixin;

import java.util.Map;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observes registered font identifiers without creating a missing-font fallback set. */
@Mixin(FontManager.class)
public interface HgnFontManagerSetsAccessor {
	@Accessor("fontSets")
	Map<?, FontSet> hgn$fontSets();
}
