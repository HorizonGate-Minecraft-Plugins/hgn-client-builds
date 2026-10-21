package online.horizongate.hgn.mixin;

import java.util.OptionalInt;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only to restore the exact original reason's wrapping after its HGN viewport is detached. */
@Mixin(MultiLineTextWidget.class)
public interface HgnDisconnectedTextAccessor {
	@Accessor("maxWidth") OptionalInt hgn$disconnectMaxWidth();
	@Accessor("maxWidth") void hgn$disconnectMaxWidth(OptionalInt width);
	@Accessor("maxRows") OptionalInt hgn$disconnectMaxRows();
}
