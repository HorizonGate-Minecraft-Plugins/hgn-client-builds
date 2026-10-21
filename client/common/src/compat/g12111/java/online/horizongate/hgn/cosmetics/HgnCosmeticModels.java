package online.horizongate.hgn.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.compat.HgnItemRenderCompat;

/** Private member-pack cosmetic item models for Minecraft 1.21.11. */
public final class HgnCosmeticModels {
	private static final String MODEL_NAMESPACE = "hgn_cosmetics";
	private static final String MEMBER_PACK_ID = "file/hgn-client-members.zip";
	private static final Identifier PACK_PROBE = Identifier.fromNamespaceAndPath(
		MODEL_NAMESPACE, "items/volt_wings.json");
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	private static final Map<ModelKey, Optional<ItemStack>> STACKS = new ConcurrentHashMap<>();
	private static final Map<RenderKey, Optional<ItemStackRenderState>> RENDER_STATES =
		new ConcurrentHashMap<>();
	private static volatile PackState packState = PackState.UNCHECKED;
	private static volatile long nextRepairAttemptNanos;

	private HgnCosmeticModels() {
	}

	public static Optional<ItemStack> stack(HgnCosmetics.ItemDefinition definition) {
		if (definition == null || !definition.active()
			|| !definition.tags().contains("supplied_model")) return Optional.empty();
		if (!packReady()) return Optional.empty();
		ModelKey key = new ModelKey(definition.id(), definition.assetId(), definition.assetRevision());
		return STACKS.computeIfAbsent(key, ignored -> createStack(definition));
	}

	private static Optional<ItemStack> createStack(HgnCosmetics.ItemDefinition definition) {
		Identifier itemModel = Identifier.tryParse(definition.assetId());
		if (itemModel == null || !MODEL_NAMESPACE.equals(itemModel.getNamespace())) {
			warn(definition, "unapproved item-model namespace");
			return Optional.empty();
		}
		Identifier wrapper = Identifier.fromNamespaceAndPath(itemModel.getNamespace(),
			"items/" + itemModel.getPath() + ".json");
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getResourceManager().getResource(wrapper).isEmpty()) {
			warn(definition, "private resource-pack model is not loaded");
			return Optional.empty();
		}
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, itemModel);
		return Optional.of(stack);
	}

	private static boolean packReady() {
		PackState current = packState;
		if (current == PackState.READY) return true;
		if (current != PackState.UNCHECKED) return false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return false;
		packState = minecraft.getResourceManager().getResource(PACK_PROBE).isPresent()
			? PackState.READY : PackState.MISSING;
		return packState == PackState.READY;
	}

	public static boolean renderGui(GuiGraphics graphics,
		HgnCosmetics.ItemDefinition definition, int x, int y, int size) {
		Optional<ItemStack> stack = stack(definition);
		if (stack.isEmpty()) return false;
		HgnDraw.item(graphics, stack.get(), x, y, size / 16.0f, definition.id().hashCode());
		return true;
	}

	/**
	 * Resolves each supplied static model once per resource generation and display
	 * context. Minecraft's queued renderer can then reuse the immutable render
	 * state instead of rebuilding model layers for every player on every frame.
	 */
	public static Optional<ItemStackRenderState> renderState(
		HgnCosmetics.ItemDefinition definition, AbstractClientPlayer player,
		ItemDisplayContext context) {
		if (definition == null || player == null || context == null) return Optional.empty();
		ModelKey model = new ModelKey(definition.id(), definition.assetId(),
			definition.assetRevision());
		RenderKey key = new RenderKey(model, context);
		return RENDER_STATES.computeIfAbsent(key, ignored -> stack(definition)
			.map(value -> HgnItemRenderCompat.resolve(player, value, context)));
	}

	public static boolean submit(PoseStack pose, SubmitNodeCollector collector, int light,
		int outlineColor, HgnCosmetics.ItemDefinition definition,
		AbstractClientPlayer player, ItemDisplayContext context) {
		Optional<ItemStackRenderState> state = renderState(definition, player, context);
		if (state.isEmpty()) return false;
		HgnItemRenderCompat.submit(state.get(), pose, collector, light,
			net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, outlineColor);
		return true;
	}

	public static void ensurePackLoaded() {
		if (!HgnMemberPackStatus.canRepairPrivatePack(
			System.getenv(HgnMemberPackStatus.STATUS_ENV), HgnMembershipClient.available())) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || packState == PackState.LOADING || packState == PackState.READY) return;
		long now = System.nanoTime();
		if (packState == PackState.MISSING && now < nextRepairAttemptNanos) return;
		if (minecraft.getResourceManager().getResource(PACK_PROBE).isPresent()) {
			packState = PackState.READY;
			nextRepairAttemptNanos = 0L;
			invalidateResourceCaches();
			return;
		}
		synchronized (HgnCosmeticModels.class) {
			if (packState == PackState.LOADING || packState == PackState.READY) return;
			packState = PackState.LOADING;
		}
		try {
			PackRepository repository = minecraft.getResourcePackRepository();
			repository.reload();
			if (!repository.isAvailable(MEMBER_PACK_ID)) {
				markMissing();
				invalidateResourceCaches();
				HgnClient.LOGGER.warn(
					"HGN cosmetic model pack is not present in the instance resource-pack directory.");
				return;
			}
			if (!HgnMemberPackStatus.canRepairPrivatePack(
				System.getenv(HgnMemberPackStatus.STATUS_ENV), HgnMembershipClient.available())) {
				markMissing();
				invalidateResourceCaches();
				return;
			}
			if (!repository.getSelectedIds().contains(MEMBER_PACK_ID)) {
				ArrayList<String> selected = new ArrayList<>(repository.getSelectedIds());
				selected.add(MEMBER_PACK_ID);
				repository.setSelected(selected);
				minecraft.options.updateResourcePacks(repository);
			}
			minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
				if (failure != null) {
					markMissing();
					invalidateResourceCaches();
					HgnClient.LOGGER.warn("HGN cosmetic model pack reload failed.", failure);
					return;
				}
				if (minecraft.getResourceManager().getResource(PACK_PROBE).isPresent()) {
					packState = PackState.READY;
					nextRepairAttemptNanos = 0L;
				} else {
					markMissing();
				}
				invalidateResourceCaches();
				if (packState == PackState.MISSING) {
					HgnClient.LOGGER.warn(
						"HGN cosmetic model pack reloaded but its model wrappers are unavailable.");
				}
			});
		} catch (RuntimeException failure) {
			markMissing();
			invalidateResourceCaches();
			HgnClient.LOGGER.warn("HGN cosmetic model pack repair failed.", failure);
		}
	}

	public static boolean packReloading() {
		return packState == PackState.LOADING;
	}

	private static void markMissing() {
		packState = PackState.MISSING;
		nextRepairAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
	}

	private static void warn(HgnCosmetics.ItemDefinition definition, String reason) {
		if (WARNED.add(definition.id() + ':' + definition.assetRevision() + ':' + reason)) {
			HgnClient.LOGGER.warn("Cosmetic {} hidden: {}.", definition.id(), reason);
		}
	}

	private static void invalidateResourceCaches() {
		STACKS.clear();
		RENDER_STATES.clear();
		HgnCapeRenderer.invalidateResourceCache();
	}

	private record ModelKey(String id, String assetId, long assetRevision) {
	}

	private record RenderKey(ModelKey model, ItemDisplayContext context) {
	}

	private enum PackState {
		UNCHECKED,
		LOADING,
		READY,
		MISSING
	}
}
