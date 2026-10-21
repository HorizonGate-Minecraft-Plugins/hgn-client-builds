package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionsScreen.class)
public interface HgnOptionsScreenAccessor {
	@Accessor("lastScreen")
	Screen hgn$parent();
}
