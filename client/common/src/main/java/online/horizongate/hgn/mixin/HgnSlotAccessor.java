package online.horizongate.hgn.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Moves the real Slot object, keeping rendering and all vanilla hit tests synchronized. */
@Mixin(Slot.class)
public interface HgnSlotAccessor {
	@Accessor("x")
	int hgn$getX();

	@Mutable
	@Accessor("x")
	void hgn$setX(int value);

	@Accessor("y")
	int hgn$getY();

	@Mutable
	@Accessor("y")
	void hgn$setY(int value);
}
