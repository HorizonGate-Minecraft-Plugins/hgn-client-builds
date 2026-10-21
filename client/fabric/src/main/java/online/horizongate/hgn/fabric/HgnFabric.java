package online.horizongate.hgn.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnPayloads;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.net.HgnBossEvents;

import java.nio.file.Path;

/**
 * Fabric entrypoint: installs the platform bridge and wires the HGN channels.
 * Start-up and join are driven from vanilla mixins shared with the other
 * loaders, so no Fabric lifecycle events are needed here.
 */
public final class HgnFabric implements ClientModInitializer, HgnPlatform {

	@Override
	public void onInitializeClient() {
		HgnPlatform.install(this);
		HgnClient.init();

		PayloadTypeRegistry.playS2C().register(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.Vink.TYPE, HgnPayloads.Vink.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.DungeonsHello.TYPE,
			HgnPayloads.DungeonsHello.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.DungeonsAuth.TYPE,
			HgnPayloads.DungeonsAuth.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsMeta.TYPE,
			HgnPayloads.DungeonsMeta.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsCatalog.TYPE,
			HgnPayloads.DungeonsCatalog.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsMobs.TYPE,
			HgnPayloads.DungeonsMobs.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsQuest.TYPE,
			HgnPayloads.DungeonsQuest.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.BossVoice.TYPE, HgnPayloads.BossVoice.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.BossFx.TYPE, HgnPayloads.BossFx.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.Core.TYPE, (payload, context) ->
			context.client().execute(() -> HgnMessages.handleCore(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.Vink.TYPE, (payload, context) ->
			context.client().execute(() -> HgnMessages.handleVink(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.DungeonsMeta.TYPE, (payload, context) ->
			context.client().execute(() -> HgnDungeons.handleMeta(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.DungeonsCatalog.TYPE, (payload, context) ->
			context.client().execute(() -> HgnDungeons.handleCatalog(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.DungeonsMobs.TYPE, (payload, context) ->
			context.client().execute(() -> HgnDungeons.handleMobs(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.DungeonsQuest.TYPE, (payload, context) ->
			context.client().execute(() -> HgnDungeons.handleQuest(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.BossVoice.TYPE, (payload, context) ->
			context.client().execute(() -> HgnBossEvents.handleVoice(payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(HgnPayloads.BossFx.TYPE, (payload, context) ->
			context.client().execute(() -> HgnBossEvents.handleFx(payload.json())));
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean canSendCore() {
		return ClientPlayNetworking.canSend(HgnPayloads.Core.TYPE);
	}

	@Override
	public void sendCore(String json) {
		ClientPlayNetworking.send(new HgnPayloads.Core(json));
	}

	@Override
	public boolean canSendDungeonsHello() {
		return ClientPlayNetworking.canSend(HgnPayloads.DungeonsHello.TYPE);
	}

	@Override
	public void sendDungeonsHello() {
		ClientPlayNetworking.send(new HgnPayloads.DungeonsHello());
	}

	@Override
	public boolean canSendDungeonsAuth() {
		return ClientPlayNetworking.canSend(HgnPayloads.DungeonsAuth.TYPE);
	}

	@Override
	public void sendDungeonsAuth(String token) {
		ClientPlayNetworking.send(new HgnPayloads.DungeonsAuth(token));
	}

	@Override
	public String loaderName() {
		return "fabric";
	}
}
