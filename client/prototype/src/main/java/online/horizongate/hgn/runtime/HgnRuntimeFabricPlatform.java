package online.horizongate.hgn.runtime;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnPayloads;

/** Fabric services for the launcher-managed runtime, without a mod entrypoint. */
public final class HgnRuntimeFabricPlatform implements HgnPlatform {
	public HgnRuntimeFabricPlatform() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("hgn-client")) {
			throw new IllegalStateException("Remove the legacy HGN mod before launching the HGN runtime");
		}
		if (!loader.isModLoaded("fabric-networking-api-v1") ||
			!loader.isModLoaded("fabric-resource-loader-v0")) {
			throw new IllegalStateException(
				"The HGN runtime requires launcher-managed Fabric networking and resource-loader modules");
		}
	}

	public void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.Core.TYPE, HgnPayloads.Core.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.Vink.TYPE, HgnPayloads.Vink.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.DungeonsHello.TYPE, HgnPayloads.DungeonsHello.CODEC);
		PayloadTypeRegistry.playC2S().register(HgnPayloads.DungeonsAuth.TYPE, HgnPayloads.DungeonsAuth.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsMeta.TYPE, HgnPayloads.DungeonsMeta.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsCatalog.TYPE, HgnPayloads.DungeonsCatalog.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsMobs.TYPE, HgnPayloads.DungeonsMobs.CODEC);
		PayloadTypeRegistry.playS2C().register(HgnPayloads.DungeonsQuest.TYPE, HgnPayloads.DungeonsQuest.CODEC);
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
