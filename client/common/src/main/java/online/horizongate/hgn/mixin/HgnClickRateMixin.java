package online.horizongate.hgn.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnClickRate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class HgnClickRateMixin {
	@Inject(method = "click", at = @At("HEAD"))
	private static void hgn$countClick(InputConstants.Key key, CallbackInfo callback) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
			|| key.getType() != InputConstants.Type.MOUSE) return;
		HgnClickRate.INSTANCE.record(key.getValue(), System.nanoTime());
	}
}
