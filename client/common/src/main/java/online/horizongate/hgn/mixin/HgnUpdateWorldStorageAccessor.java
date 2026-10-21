package online.horizongate.hgn.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the owning world's folder identity without opening or mutating another storage handle. */
@Mixin(MinecraftServer.class)
public interface HgnUpdateWorldStorageAccessor {
	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess hgn$updateStorage();
}
