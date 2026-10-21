package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Version-stable access to Screen's protected widget registration method. */
@Mixin(Screen.class)
public interface HgnScreenAccessor {
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T hgn$addRenderableWidget(T widget);
}
