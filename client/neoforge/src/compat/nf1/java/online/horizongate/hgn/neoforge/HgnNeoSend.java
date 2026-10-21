package online.horizongate.hgn.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-to-server send for NeoForge up to 21.6. */
final class HgnNeoSend {
	private HgnNeoSend() {
	}

	static void toServer(CustomPacketPayload payload) {
		PacketDistributor.sendToServer(payload);
	}
}
