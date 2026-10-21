package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Read-only boss-bar occupancy used to keep the compact race header out of its lane. */
@Mixin(BossHealthOverlay.class)
public interface HgnBossOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> hgn$getEvents();
}
