package online.horizongate.hgn.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.cosmetics.HgnCosmetics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Announces the HGN client to the server on join. Every loader has its own
 * join event, so this uses the vanilla login handler instead — one hook that
 * behaves identically on Fabric, Forge and NeoForge.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void hgn$announceClient(ClientboundLoginPacket packet, CallbackInfo ci) {
		// Fresh join: clear any Dragon Eye / HUD / beast state left over from the
		// previous server so nothing carries over, then announce this client.
		HgnMessages.reset();
		HgnMessages.sendHello();
		HgnDungeons.subscribe();
		HgnCosmetics.onMinecraftJoin(Minecraft.getInstance());
	}
}
