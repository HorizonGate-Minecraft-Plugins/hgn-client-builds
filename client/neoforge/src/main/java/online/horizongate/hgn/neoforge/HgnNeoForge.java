package online.horizongate.hgn.neoforge;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnPayloads;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.net.HgnBossEvents;

import java.nio.file.Path;

/**
 * NeoForge entrypoint. Start-up and join are driven from vanilla mixins, so the
 * mod bus is only used for the payload registrar.
 */
@Mod(value = "hgn-client", dist = Dist.CLIENT)
public final class HgnNeoForge implements HgnPlatform {
	public HgnNeoForge(IEventBus modBus) {
		HgnPlatform.install(this);
		HgnClient.init();
		modBus.addListener(this::registerPayloads);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1").optional();
		registrar.playToClient(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnMessages.handleCore(payload.json())));
		registrar.playToClient(HgnPayloads.Vink.TYPE, HgnPayloads.Vink.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnMessages.handleVink(payload.json())));
		registrar.playToClient(HgnPayloads.DungeonsMeta.TYPE, HgnPayloads.DungeonsMeta.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnDungeons.handleMeta(payload.json())));
		registrar.playToClient(HgnPayloads.DungeonsCatalog.TYPE, HgnPayloads.DungeonsCatalog.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnDungeons.handleCatalog(payload.json())));
		registrar.playToClient(HgnPayloads.DungeonsMobs.TYPE, HgnPayloads.DungeonsMobs.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnDungeons.handleMobs(payload.json())));
		registrar.playToClient(HgnPayloads.DungeonsQuest.TYPE, HgnPayloads.DungeonsQuest.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnDungeons.handleQuest(payload.json())));
		registrar.playToClient(HgnPayloads.BossVoice.TYPE, HgnPayloads.BossVoice.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnBossEvents.handleVoice(payload.json())));
		registrar.playToClient(HgnPayloads.BossFx.TYPE, HgnPayloads.BossFx.CODEC,
			(payload, context) -> context.enqueueWork(() -> HgnBossEvents.handleFx(payload.json())));
		registrar.playToServer(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC,
			(payload, context) -> context.enqueueWork(() -> {
			}));
		registrar.playToServer(HgnPayloads.DungeonsHello.TYPE, HgnPayloads.DungeonsHello.CODEC,
			(payload, context) -> context.enqueueWork(() -> {
			}));
		registrar.playToServer(HgnPayloads.DungeonsAuth.TYPE, HgnPayloads.DungeonsAuth.CODEC,
			(payload, context) -> context.enqueueWork(() -> {
			}));
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean canSendCore() {
		return Minecraft.getInstance().getConnection() != null;
	}

	@Override
	public void sendCore(String json) {
		// The channel is registered as optional, so a server that does not know it
		// refuses the packet rather than disconnecting the player.
		try {
			HgnNeoSend.toServer(new HgnPayloads.Core(json));
		} catch (Exception e) {
			HgnClient.LOGGER.debug("[hgn:core] server does not accept the channel", e);
		}
	}

	@Override
	public boolean canSendDungeonsHello() {
		return Minecraft.getInstance().getConnection() != null;
	}

	@Override
	public void sendDungeonsHello() {
		try {
			HgnNeoSend.toServer(new HgnPayloads.DungeonsHello());
		} catch (Exception exception) {
			HgnClient.LOGGER.debug("[hgn:dungeons] server does not accept the subscription");
		}
	}

	@Override
	public boolean canSendDungeonsAuth() {
		return Minecraft.getInstance().getConnection() != null;
	}

	@Override
	public void sendDungeonsAuth(String token) {
		try {
			HgnNeoSend.toServer(new HgnPayloads.DungeonsAuth(token));
		} catch (Exception exception) {
			HgnClient.LOGGER.debug("[hgn:dungeons] server does not accept the proof channel");
		}
	}

	@Override
	public String loaderName() {
		return "neoforge";
	}
}
