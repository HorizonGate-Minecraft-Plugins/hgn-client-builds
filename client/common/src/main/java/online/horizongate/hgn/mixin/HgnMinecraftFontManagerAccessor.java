package online.horizongate.hgn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to Minecraft's loaded font registry for packaged resource verification. */
@Mixin(Minecraft.class)
public interface HgnMinecraftFontManagerAccessor {
	@Accessor("fontManager")
	FontManager hgn$fontManager();
}
