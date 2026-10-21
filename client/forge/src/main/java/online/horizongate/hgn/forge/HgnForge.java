package online.horizongate.hgn.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnPlatform;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.net.HgnBossEvents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Forge entrypoint. Forge has no vanilla-payload registrar, so the HGN channels
 * are raw {@link EventNetworkChannel}s carrying the same UTF-8 JSON bytes the
 * Fabric and NeoForge builds exchange.
 *
 * <p>Nothing here touches a Forge event bus: start-up and join are driven from
 * vanilla mixins instead, which keeps this one file valid across the EventBus 5
 * to EventBus 7 change in 1.21.6.
 */
@Mod("hgn-client")
public final class HgnForge implements HgnPlatform {
	private static EventNetworkChannel coreChannel;
	private static EventNetworkChannel dungeonsHelloChannel;
	private static EventNetworkChannel dungeonsAuthChannel;

	public HgnForge() {
		HgnPlatform.install(this);
		HgnClient.init();

		coreChannel = channel("core", HgnMessages::handleCore);
		channel("vinksmp", HgnMessages::handleVink);
		dungeonsHelloChannel = channel("dungeons_hello", ignored -> {});
		dungeonsAuthChannel = channel("dungeons_auth", ignored -> {}, 512);
		channel("dungeons_meta", HgnDungeons::handleMeta, 24_000);
		channel("dungeons_catalog", HgnDungeons::handleCatalog, 24_000);
		channel("dungeons_mobs", HgnDungeons::handleMobs, 24_000);
		channel("dungeons_quest", HgnDungeons::handleQuest, 1024);
		channel("boss_voice", HgnBossEvents::handleVoice, 4096);
		channel("boss_fx", HgnBossEvents::handleFx, 2048);
	}

	private static EventNetworkChannel channel(String path, Consumer<String> handler) {
		return channel(path, handler, 65_536);
	}

	private static EventNetworkChannel channel(String path, Consumer<String> handler, int maxBytes) {
		EventNetworkChannel ch = ChannelBuilder.named(HgnCompat.id("hgn", path)).optional().eventNetworkChannel();
		ch.addListener((CustomPayloadEvent event) -> {
			FriendlyByteBuf payload = event.getPayload();
			if (payload == null || payload.readableBytes() > maxBytes) {
				event.getSource().setPacketHandled(true);
				return;
			}
			byte[] bytes = new byte[payload.readableBytes()];
			payload.readBytes(bytes);
			String json = new String(bytes, StandardCharsets.UTF_8);
			event.getSource().enqueueWork(() -> handler.accept(json));
			event.getSource().setPacketHandled(true);
		});
		return ch;
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
		try {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeBytes(json.getBytes(StandardCharsets.UTF_8));
			coreChannel.send(buf, PacketDistributor.SERVER.with(null));
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
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(1));
			buffer.writeByte(1);
			dungeonsHelloChannel.send(buffer, PacketDistributor.SERVER.with(null));
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
			byte[] encoded = token.getBytes(StandardCharsets.UTF_8);
			if (encoded.length > 512) return;
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(encoded.length));
			buffer.writeBytes(encoded);
			dungeonsAuthChannel.send(buffer, PacketDistributor.SERVER.with(null));
		} catch (Exception exception) {
			HgnClient.LOGGER.debug("[hgn:dungeons] server does not accept the proof channel");
		}
	}

	@Override
	public String loaderName() {
		return "forge";
	}
}
