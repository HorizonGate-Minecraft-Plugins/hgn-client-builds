package online.horizongate.hgn.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only identity and native presentation, never a replacement for row callbacks. */
@Mixin(KeyBindsList.KeyEntry.class)
public interface HgnKeyBindEntryAccessor {
	@Accessor("key")
	KeyMapping hgn$key();
	@Accessor("name")
	Component hgn$name();
	@Accessor("changeButton")
	Button hgn$changeButton();
	@Accessor("resetButton")
	Button hgn$resetButton();
}
