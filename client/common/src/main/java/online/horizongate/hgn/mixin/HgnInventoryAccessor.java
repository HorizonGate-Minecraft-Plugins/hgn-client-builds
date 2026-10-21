package online.horizongate.hgn.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps the selected slot stable across releases with different field visibility. */
@Mixin(Inventory.class)
public interface HgnInventoryAccessor {
    @Accessor("selected")
    int hgn$getSelectedHotbarSlot();
}
