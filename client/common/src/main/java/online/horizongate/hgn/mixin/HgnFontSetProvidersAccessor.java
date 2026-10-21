package online.horizongate.hgn.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the providers selected after a client resource reload. */
@Mixin(FontSet.class)
public interface HgnFontSetProvidersAccessor {
	@Accessor("activeProviders")
	List<GlyphProvider> hgn$activeProviders();
}
