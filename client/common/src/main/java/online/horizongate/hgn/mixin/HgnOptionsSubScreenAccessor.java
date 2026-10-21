package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Parent identity for the exact-class language-menu redirect only. */
@Mixin(OptionsSubScreen.class)
public interface HgnOptionsSubScreenAccessor {
	@Accessor("lastScreen")
	Screen hgn$parent();
}
