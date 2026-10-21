package online.horizongate.hgn.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import online.horizongate.hgn.HgnClientCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts the small set of HGN client-only commands before they reach a server. */
@Mixin(ClientPacketListener.class)
public abstract class HgnClientCommandMixin {

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void hgn$handleClientCommand(String command, CallbackInfo ci) {
		if (HgnClientCommands.handle(command)) {
			ci.cancel();
		}
	}
}
