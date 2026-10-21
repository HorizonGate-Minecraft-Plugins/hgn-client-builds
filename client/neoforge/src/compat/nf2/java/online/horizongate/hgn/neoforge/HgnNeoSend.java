package online.horizongate.hgn.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-to-server send for NeoForge 21.7 and newer, which moved it off PacketDistributor. */
final class HgnNeoSend {
	private HgnNeoSend() {
	}

	static void toServer(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}
}
