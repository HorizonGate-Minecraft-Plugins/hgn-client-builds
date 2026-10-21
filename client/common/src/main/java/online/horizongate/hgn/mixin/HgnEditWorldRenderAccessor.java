package online.horizongate.hgn.mixin;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only rendering seam used only by the exact native Edit World composition. */
@Mixin(Screen.class)
public interface HgnEditWorldRenderAccessor {
	@Accessor("renderables") List<Renderable> hgn$editWorldRenderables();
}
