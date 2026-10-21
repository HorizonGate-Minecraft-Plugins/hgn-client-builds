package online.horizongate.hgn.mixin;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Detached smoke can inspect ownership and repeat initialization without calling a world action. */
@Mixin(EditWorldScreen.class)
public interface HgnEditWorldAccessor {
	@Accessor("callback") BooleanConsumer hgn$editWorldCallback();
	@Accessor("levelAccess") LevelStorageSource.LevelStorageAccess hgn$editWorldStorage();
	@Invoker("init") void hgn$initEditWorld();
}
