package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only row geometry for the language menu; these methods are protected on Minecraft 1.21. */
@Mixin(AbstractSelectionList.class)
public interface HgnLanguageListAccessor {
	@Invoker("getRowTop")
	int hgn$languageRowTop(int index);

	@Invoker("getRowBottom")
	int hgn$languageRowBottom(int index);
}
