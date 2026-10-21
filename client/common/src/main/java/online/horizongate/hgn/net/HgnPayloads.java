package online.horizongate.hgn.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.horizongate.hgn.compat.HgnCompat;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * The {@code hgn:core} and {@code hgn:vinksmp} payload types.
 * These use only vanilla networking classes, so the same definitions are
 * registered by every loader adapter. The identifier type was renamed in
 * 1.21.11, so ids are built through the compat shim and never named here.
 */
public final class HgnPayloads {
	private HgnPayloads() {
	}

	public record Core(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Core> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "core"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Core> CODEC = jsonCodec(Core::new, Core::json);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Vink(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Vink> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "vinksmp"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Vink> CODEC = jsonCodec(Vink::new, Vink::json);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsHello() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsHello> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_hello"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsHello> CODEC = StreamCodec.of(
			(buf, ignored) -> buf.writeByte(1),
			buf -> {
				if (buf.readableBytes() != 1 || buf.readUnsignedByte() != 1) {
					throw new IllegalArgumentException("Unsupported dungeon subscription schema");
				}
				return new DungeonsHello();
			});

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsAuth(String token) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsAuth> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_auth"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsAuth> CODEC =
			jsonCodec(DungeonsAuth::new, DungeonsAuth::token, 512);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsMeta(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsMeta> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_meta"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsMeta> CODEC =
			jsonCodec(DungeonsMeta::new, DungeonsMeta::json, 24_000);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsCatalog(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsCatalog> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_catalog"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsCatalog> CODEC =
			jsonCodec(DungeonsCatalog::new, DungeonsCatalog::json, 24_000);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsMobs(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsMobs> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_mobs"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsMobs> CODEC =
			jsonCodec(DungeonsMobs::new, DungeonsMobs::json, 24_000);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DungeonsQuest(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DungeonsQuest> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "dungeons_quest"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsQuest> CODEC =
			jsonCodec(DungeonsQuest::new, DungeonsQuest::json, 1024);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Smart Mob speech is text-only; the client never receives generated audio or API credentials. */
	public record BossVoice(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<BossVoice> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "boss_voice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BossVoice> CODEC =
			jsonCodec(BossVoice::new, BossVoice::json, 4096);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Optional visual-only Smart Mob instructions. */
	public record BossFx(String json) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<BossFx> TYPE =
			new CustomPacketPayload.Type<>(HgnCompat.id("hgn", "boss_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BossFx> CODEC =
			jsonCodec(BossFx::new, BossFx::json, 2048);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> jsonCodec(
		Function<String, T> factory, Function<T, String> getter) {
		return jsonCodec(factory, getter, Integer.MAX_VALUE);
	}

	private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> jsonCodec(
		Function<String, T> factory, Function<T, String> getter, int maxBytes) {
		return StreamCodec.of(
			(buf, value) -> buf.writeBytes(getter.apply(value).getBytes(StandardCharsets.UTF_8)),
			buf -> {
				if (buf.readableBytes() > maxBytes) {
					buf.skipBytes(buf.readableBytes());
					return factory.apply("");
				}
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				return factory.apply(new String(bytes, StandardCharsets.UTF_8));
			});
	}
}
