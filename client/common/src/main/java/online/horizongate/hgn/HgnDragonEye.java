package online.horizongate.hgn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.compat.HgnEntityPreview;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dragon Eye is an aura sense, not an X-ray. The world remains black and is only
 * revealed for a moment when an aura pulse reaches a surface or living presence.
 * Range and beast knowledge remain server-authorised; the local Qi snapshot only
 * determines how clear and frequent the visual echoes are.
 */
public final class HgnDragonEye {
	private static final int BLOCK_COLOR = 0xFF2A759E;
	private static final int PLAYER_COLOR = 0xFF8EEFFF;
	private static final int ENEMY_COLOR = 0xFFFF526B;
	private static final int ANIMAL_COLOR = 0xFF74E6A2;
	private static final int OTHER_COLOR = 0xFFB596FF;
	private static final int UNKNOWN_BEAST_COLOR = 0xFFFFC84D;
	private static final int BACKGROUND_TOP = 0xF9020509;
	private static final int BACKGROUND_BOTTOM = 0xFC010A12;
	private static final int MAX_SENSE_RANGE = 48;
	private static final int MAX_BLOCK_RANGE = 24;
	private static final int BLOCK_SAMPLE_STEP = 2;
	private static final int BLOCK_CACHE_CELL = 3;
	private static final int MAX_BEAST_DESCRIPTORS = 512;
	private static final double NEAR_CLIP = 0.35;
	private static final long ANIM_MS = 450L;
	/** Ignore momentary malformed/partial state flips from the server channel. */
	private static final long ACTIVATION_DEBOUNCE_MS = 120L;
	private static final long DEACTIVATION_GRACE_MS = 180L;
	private static final long SOFT_RENDER_BUDGET_NS = 4_500_000L;
	private static final long HARD_RENDER_BUDGET_NS = 8_000_000L;
	private static final long MAX_RENDER_SAMPLE_NS = 50_000_000L;
	private static final long BLOCK_SCAN_INTERVAL_NS = 25_000_000L;
	private static final long BLOCK_SCAN_SLICE_NS = 500_000L;
	private static final long MISSING_CHUNK_RETRY_NS = 1_000_000_000L;
	private static final RenderBudget REDUCED_BUDGET = new RenderBudget(160, 48, 2, 64, 96);
	private static final RenderBudget LOW_BUDGET = new RenderBudget(240, 96, 3, 96, 160);
	private static final RenderBudget BALANCED_BUDGET = new RenderBudget(480, 180, 4, 160, 320);
	private static final RenderBudget HIGH_BUDGET = new RenderBudget(800, 300, 6, 240, 512);

	/** Per-entity data supplied by VinkSMP, keyed by entity UUID. */
	private static final Map<UUID, BeastDescriptor> beastByUuid = new HashMap<>();
	private static List<BlockPos> blockCache = new ArrayList<>();
	private static List<BlockPos> blockCacheBuild = new ArrayList<>();
	private static final BlockPos.MutableBlockPos BLOCK_SCAN_POS = new BlockPos.MutableBlockPos();
	private static final int[] SCRATCH = new int[2];
	private static final double[] CAMERA_BASIS = new double[12];

	private static JsonObject parsedBeasts;
	private static JsonObject parsedAuraHud;
	private static int parsedAuraRank = Integer.MIN_VALUE;
	private static int auraColor = 0xFF54DFFF;
	private static float auraStrength = 0.62f;
	private static int blockCacheX = Integer.MIN_VALUE;
	private static int blockCacheY = Integer.MIN_VALUE;
	private static int blockCacheZ = Integer.MIN_VALUE;
	private static int blockCacheRange = -1;
	private static int blockCacheBudget = -1;
	private static ClientLevel blockCacheLevel;
	private static int blockBuildX = Integer.MIN_VALUE;
	private static int blockBuildY = Integer.MIN_VALUE;
	private static int blockBuildZ = Integer.MIN_VALUE;
	private static int blockBuildRange = -1;
	private static int blockBuildBudget = -1;
	private static int blockBuildIndex;
	private static boolean blockBuildSkippedChunks;
	private static long nextBlockScanNanos;
	private static long blockCacheRetryNanos;
	private static boolean lastActive;
	private static boolean opening;
	private static long transitionStart = Long.MIN_VALUE;
	private static boolean requestedActive;
	private static boolean stableActive;
	private static long requestedChangedAt = Long.MIN_VALUE;
	private static long renderCostEwmaNanos;

	private record BeastDescriptor(String label, String rank, String model, String profile,
		String kind, Integer color, Integer rankColor, long health, long maximumHealth) {
	}

	private HgnDragonEye() {
	}

	/** True while Dragon Eye owns the complete HUD, including its exit animation. */
	public static boolean owningScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null && minecraft.screen == null && intensity() > 0.001f;
	}

	private static boolean serverActive() {
		boolean requested = HgnMessages.dragonEyeActive;
		long time = now();
		if (requestedChangedAt == Long.MIN_VALUE || requested != requestedActive) {
			requestedActive = requested;
			requestedChangedAt = time;
		}
		long settleTime = requested ? ACTIVATION_DEBOUNCE_MS : DEACTIVATION_GRACE_MS;
		if (requested != stableActive && time - requestedChangedAt >= settleTime) {
			stableActive = requested;
		}
		return stableActive;
	}

	private static float intensity() {
		boolean active = serverActive();
		if (active != lastActive) {
			lastActive = active;
			opening = active;
			transitionStart = now();
		}
		if (!motionEnabled()) {
			transitionStart = Long.MIN_VALUE;
			return active ? 1f : 0f;
		}
		if (transitionStart == Long.MIN_VALUE) {
			return active ? 1f : 0f;
		}
		float t = Math.min(1f, (now() - transitionStart) / (float) ANIM_MS);
		float eased = t * t * (3f - 2f * t);
		return opening ? eased : 1f - eased;
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float transition = intensity();
		if (transition <= 0.001f) {
			return;
		}
		long renderStarted = System.nanoTime();
		refreshBeasts();
		refreshAura();

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		long time = now();
		RenderBudget budget = renderBudget();
		int allowedRange = Mth.clamp(HgnMessages.dragonEyeRange > 0 ? HgnMessages.dragonEyeRange : 24,
			6, MAX_SENSE_RANGE);
		// Qi never extends server range. It only controls how much of that permitted
		// space is resolved by an individual aura pulse.
		int echoReach = Math.max(6, Math.round(allowedRange * (0.38f + auraStrength * 0.62f)));
		echoReach = Math.min(allowedRange, echoReach);

		HgnDraw.gradient(graphics, 0, 0, width, height,
			withAlpha(BACKGROUND_TOP, transition), withAlpha(BACKGROUND_BOTTOM, transition));
		drawAuraVignette(graphics, width, height, transition);

		HgnCompat.cameraBasis(CAMERA_BASIS);
		double[] camera = CAMERA_BASIS;
		double focal = (height / 2.0) / Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0);
		drawPulseRings(graphics, width, height, time, echoReach, transition);
		boolean chunksWarming = HgnPerformanceGuard.chunksWarming();
		if (!chunksWarming) {
			drawWorldEchoes(graphics, minecraft.level, minecraft.player.blockPosition(), echoReach,
				time, camera, focal, width, height, transition, budget);
			drawEntityEchoes(graphics, minecraft.level, minecraft.player, echoReach, time,
				camera, focal, width, height, transition, budget);
		}
		drawCrosshair(graphics, width, height, transition);
		drawScannerHud(graphics, minecraft, allowedRange, echoReach, width, height, transition);
		if (!chunksWarming) drawFocusedTarget(graphics, minecraft, width, height, transition);
		HgnHud.renderDragonEyeHotbar(graphics, withAlpha(auraColor, transition));
		recordRenderCost(System.nanoTime() - renderStarted);
	}

	private static void drawAuraVignette(GuiGraphics graphics, int width, int height, float transition) {
		int edge = Math.max(18, Math.min(width, height) / 9);
		int shade = withAlpha(0xD9000000, transition);
		HgnDraw.gradient(graphics, 0, 0, width, edge, shade, 0x00000000);
		HgnDraw.gradient(graphics, 0, height - edge, width, height, 0x00000000, shade);
		HgnDraw.rect(graphics, 0, 0, edge, height, withAlpha(0x72000000, transition));
		HgnDraw.rect(graphics, width - edge, 0, width, height, withAlpha(0x72000000, transition));
	}

	private static void drawPulseRings(GuiGraphics graphics, int width, int height, long time,
		int reach, float transition) {
		if (!motionEnabled() || !HgnConfig.get().sceneParticles) {
			return;
		}
		int period = pulsePeriod();
		float phase = (time % period) / (float) period;
		int maxRadius = Math.max(26, Math.min(Math.max(width, height), reach * 13));
		int radius = Math.round(phase * maxRadius);
		int alpha = Math.round((1f - phase) * (0.22f + auraStrength * 0.22f) * transition * 255f);
		int ring = (Math.max(0, Math.min(255, alpha)) << 24) | (auraColor & 0x00FFFFFF);
		int cx = width / 2;
		int cy = height / 2;
		int thickness = auraStrength > 0.72f ? 2 : 1;
		HgnDraw.rect(graphics, cx - radius, cy - radius, cx + radius + 1, cy - radius + thickness, ring);
		HgnDraw.rect(graphics, cx - radius, cy + radius, cx + radius + 1, cy + radius + thickness, ring);
		HgnDraw.rect(graphics, cx - radius, cy - radius, cx - radius + thickness, cy + radius + 1, ring);
		HgnDraw.rect(graphics, cx + radius, cy - radius, cx + radius + thickness, cy + radius + 1, ring);
	}

	/** Cache sparse physical surfaces. They are only painted while a pulse is passing them. */
	private static void drawWorldEchoes(GuiGraphics graphics, ClientLevel level, BlockPos center, int reach,
		long time, double[] camera, double focal, int width, int height, float transition,
		RenderBudget budget) {
		int range = Math.min(reach, MAX_BLOCK_RANGE);
		int cacheX = Math.floorDiv(center.getX(), BLOCK_CACHE_CELL) * BLOCK_CACHE_CELL;
		int cacheY = Math.floorDiv(center.getY(), BLOCK_CACHE_CELL) * BLOCK_CACHE_CELL;
		int cacheZ = Math.floorDiv(center.getZ(), BLOCK_CACHE_CELL) * BLOCK_CACHE_CELL;
		advanceBlockCache(level, cacheX, cacheY, cacheZ, range, budget);

		int echoColor = blend(BLOCK_COLOR, auraColor, 0.48f);
		int drawn = 0;
		for (BlockPos pos : blockCache) {
			if (drawn >= budget.blockPoints()) break;
			double distanceSq = center.distSqr(pos);
			if (distanceSq > (double) reach * reach) {
				continue;
			}
			double distance = Math.sqrt(distanceSq);
			float echo = echoAt(distance + stagger(pos), reach, time);
			if (echo < 0.035f || !project(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				camera, focal, width, height)) {
				continue;
			}
			int alpha = Math.round((0.12f + echo * 0.88f) * transition * 215f);
			int color = (Math.max(0, Math.min(255, alpha)) << 24) | (echoColor & 0x00FFFFFF);
			int dot = echo > 0.62f ? 2 : 1;
			HgnDraw.rect(graphics, SCRATCH[0] - dot, SCRATCH[1] - dot, SCRATCH[0] + dot + 1,
				SCRATCH[1] + dot + 1, color);
			drawn++;
			if (echo > 0.80f) {
				HgnDraw.rect(graphics, SCRATCH[0] - 4, SCRATCH[1], SCRATCH[0] + 5, SCRATCH[1] + 1,
					withAlpha(color, 0.55f));
			}
		}
	}

	/** Builds the next sparse surface cache over several frames and keeps the last complete cache visible. */
	private static void advanceBlockCache(ClientLevel level, int cacheX, int cacheY, int cacheZ,
		int range, RenderBudget budget) {
		if (blockCacheLevel != level) {
			blockCacheLevel = level;
			blockCache.clear();
			blockCacheBuild.clear();
			blockCacheX = Integer.MIN_VALUE;
			blockBuildX = Integer.MIN_VALUE;
			blockCacheRetryNanos = 0L;
			nextBlockScanNanos = 0L;
		}
		long now = System.nanoTime();
		boolean built = cacheX == blockCacheX && cacheY == blockCacheY && cacheZ == blockCacheZ
			&& range == blockCacheRange && budget.blockPoints() == blockCacheBudget;
		boolean retryDue = built && blockCacheRetryNanos != 0L && now >= blockCacheRetryNanos;
		if (built && !retryDue) return;
		if (now < nextBlockScanNanos) return;
		nextBlockScanNanos = now + BLOCK_SCAN_INTERVAL_NS;
		boolean sameBuild = !retryDue
			&& cacheX == blockBuildX && cacheY == blockBuildY && cacheZ == blockBuildZ
			&& range == blockBuildRange && budget.blockPoints() == blockBuildBudget;
		if (!sameBuild) {
			blockBuildX = cacheX;
			blockBuildY = cacheY;
			blockBuildZ = cacheZ;
			blockBuildRange = range;
			blockBuildBudget = budget.blockPoints();
			blockBuildIndex = 0;
			blockBuildSkippedChunks = false;
			blockCacheBuild.clear();
		}

		int dimension = range * 2 / BLOCK_SAMPLE_STEP + 1;
		int plane = dimension * dimension;
		int total = plane * dimension;
		int radiusSq = range * range;
		int work = 0;
		long deadline = now + BLOCK_SCAN_SLICE_NS;
		while (blockBuildIndex < total && work < budget.blockScanSamples()
			&& blockCacheBuild.size() < budget.blockPoints()
			&& System.nanoTime() < deadline) {
			int index = blockBuildIndex++;
			int ix = index / plane;
			int remainder = index % plane;
			int iy = remainder / dimension;
			int iz = remainder % dimension;
			int dx = -range + ix * BLOCK_SAMPLE_STEP;
			int dy = -range + iy * BLOCK_SAMPLE_STEP;
			int dz = -range + iz * BLOCK_SAMPLE_STEP;
			work++;
			if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
			BLOCK_SCAN_POS.set(cacheX + dx, cacheY + dy, cacheZ + dz);
			if (!level.hasChunkAt(BLOCK_SCAN_POS)) {
				blockBuildSkippedChunks = true;
				continue;
			}
			if (isSensedBlock(level.getBlockState(BLOCK_SCAN_POS))) {
				blockCacheBuild.add(BLOCK_SCAN_POS.immutable());
			}
		}
		if (blockBuildIndex < total && blockCacheBuild.size() < budget.blockPoints()) return;

		List<BlockPos> previous = blockCache;
		blockCache = blockCacheBuild;
		blockCacheBuild = previous;
		blockCacheBuild.clear();
		blockCacheX = cacheX;
		blockCacheY = cacheY;
		blockCacheZ = cacheZ;
		blockCacheRange = range;
		blockCacheBudget = budget.blockPoints();
		blockCacheRetryNanos = blockBuildSkippedChunks
			? System.nanoTime() + MISSING_CHUNK_RETRY_NS : 0L;
	}

	private static boolean isSensedBlock(BlockState state) {
		return !state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE;
	}

	/** Every nearby living entity has an independent, stronger echo body and health readout. */
	private static void drawEntityEchoes(GuiGraphics graphics, ClientLevel level, Entity self, int reach,
		long time, double[] camera, double focal, int width, int height, float transition,
		RenderBudget budget) {
		double reachSq = (double) reach * reach;
		int particleBudget = budget.entityParticles();
		int readouts = 0;
		int inspected = 0;
		for (Entity entity : level.entitiesForRendering()) {
			if (inspected++ >= budget.entityInspections()) break;
			if (!(entity instanceof LivingEntity living) || entity == self) {
				continue;
			}
			// The server must keep vanished or invisible entities invisible. Dragon Eye
			// senses presence; it never manufactures information the player was denied.
			if (entity.isInvisible() || (entity instanceof Player player && player.isSpectator())) {
				continue;
			}
			double distanceSq = entity.distanceToSqr(camera[0], camera[1], camera[2]);
			if (distanceSq > reachSq) {
				continue;
			}
			double distance = Math.sqrt(distanceSq);
			float echo = Math.max(0.12f, echoAt(distance, reach, time));
			AABB box = entity.getBoundingBox();
			if (!project((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0,
				(box.minZ + box.maxZ) / 2.0, camera, focal, width, height)) {
				continue;
			}
			BeastDescriptor info = beastByUuid.get(entity.getUUID());
			int baseColor = colorFor(entity, info);
			int color = withAlpha(baseColor, transition * (0.30f + echo * 0.70f));
			particleBudget = drawEntitySilhouette(graphics, box, info, color, echo, camera, focal, width, height,
				particleBudget);
			if (readouts < budget.entityReadouts() && (info != null || echo > 0.45f)) {
				drawEntityReadout(graphics, living, info, baseColor, camera, focal, width, height, transition);
				readouts++;
			}
			if (particleBudget <= 0) {
				break;
			}
		}
	}

	private static RenderBudget renderBudget() {
		HgnConfig config = HgnConfig.get();
		if (config.reducedMotion) return REDUCED_BUDGET;
		RenderBudget requested = switch (config.sceneQuality) {
			case LOW -> LOW_BUDGET;
			case HIGH -> HIGH_BUDGET;
			default -> BALANCED_BUDGET;
		};
		if (renderCostEwmaNanos >= HARD_RENDER_BUDGET_NS) return REDUCED_BUDGET;
		if (renderCostEwmaNanos >= SOFT_RENDER_BUDGET_NS) {
			return requested == HIGH_BUDGET ? BALANCED_BUDGET : LOW_BUDGET;
		}
		return requested;
	}

	private static void recordRenderCost(long elapsedNanos) {
		long sample = Math.max(0L, Math.min(MAX_RENDER_SAMPLE_NS, elapsedNanos));
		renderCostEwmaNanos = renderCostEwmaNanos == 0L
			? sample : (renderCostEwmaNanos * 7L + sample) / 8L;
	}

	private record RenderBudget(int blockPoints, int entityParticles, int entityReadouts,
		int blockScanSamples, int entityInspections) {
	}

	/** Rebuilds an entity from a sparse shell, so size and body profile remain legible in the dark. */
	private static int drawEntitySilhouette(GuiGraphics graphics, AABB box, BeastDescriptor info, int color, float echo,
		double[] camera, double focal, int width, int height, int budget) {
		if (!HgnDragonEyeBudgetPolicy.finiteBounds(box.minX, box.minY, box.minZ,
			box.maxX, box.maxY, box.maxZ)) return budget;
		double step = Math.max(0.16, (box.maxY - box.minY) / (8.0 + auraStrength * 4.0));
		int attempts = HgnDragonEyeBudgetPolicy.silhouetteSampleAttempts(budget);
		int skipMask = echo > 0.55f ? 0 : (auraStrength > 0.70f ? 1 : 3);
		int sample = 0;
		for (double x = box.minX; x <= box.maxX && budget > 0 && attempts > 0; x += step) {
			for (double y = box.minY; y <= box.maxY && budget > 0 && attempts > 0; y += step) {
				for (double z = box.minZ; z <= box.maxZ && budget > 0 && attempts > 0; z += step) {
					attempts--;
					boolean shell = x - box.minX < step || box.maxX - x < step
						|| y - box.minY < step || box.maxY - y < step
						|| z - box.minZ < step || box.maxZ - z < step;
					if (!shell || (sample++ & skipMask) != 0) {
						continue;
					}
					if (project(x, y, z, camera, focal, width, height)) {
						int dot = echo > 0.66f ? 2 : 1;
						HgnDraw.rect(graphics, SCRATCH[0] - dot, SCRATCH[1] - dot,
							SCRATCH[0] + dot + 1, SCRATCH[1] + dot + 1, color);
						budget--;
					}
				}
			}
		}
		if (info != null && project((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0,
			(box.minZ + box.maxZ) / 2.0, camera, focal, width, height)) {
			drawCustomProfile(graphics, info, SCRATCH[0], SCRATCH[1], box, color, echo);
		}
		return budget;
	}

	/**
	 * ModelEngine and MythicMobs models are normally rendered before this blackout.
	 * VinkSMP can identify their model in the beast snapshot, letting Dragon Eye add a
	 * recognisable aura profile rather than pretending every custom creature is a cube.
	 */
	private static void drawCustomProfile(GuiGraphics graphics, BeastDescriptor info, int cx, int cy, AABB box,
		int color, float echo) {
		String profile = info.profile();
		if (profile.isBlank()) {
			return;
		}
		int size = Math.max(4, Math.min(15, Math.round((float) (box.maxY - box.minY) * 5f)));
		int pulseColor = withAlpha(color, Math.min(1f, 0.38f + echo));
		if (profile.contains("wing") || profile.contains("dragon") || profile.contains("bird")) {
			HgnDraw.rect(graphics, cx - size * 2, cy - 1, cx - size / 2, cy + 1, pulseColor);
			HgnDraw.rect(graphics, cx + size / 2, cy - 1, cx + size * 2, cy + 1, pulseColor);
			HgnDraw.rect(graphics, cx - size * 2, cy - size / 2, cx - size * 2 + 1, cy + 2, pulseColor);
			HgnDraw.rect(graphics, cx + size * 2 - 1, cy - size / 2, cx + size * 2, cy + 2, pulseColor);
		} else if (profile.contains("serpent") || profile.contains("snake") || profile.contains("wyrm")) {
			for (int i = 1; i <= 4; i++) {
				int x = cx - i * size;
				int y = cy + ((i & 1) == 0 ? size / 3 : -size / 3);
				HgnDraw.rect(graphics, x - 1, y - 1, x + 2, y + 2, pulseColor);
			}
		} else if (profile.contains("quad") || profile.contains("wolf") || profile.contains("horse")
			|| profile.contains("beast")) {
			HgnDraw.rect(graphics, cx - size, cy + size / 2, cx + size + 1, cy + size / 2 + 2, pulseColor);
			HgnDraw.rect(graphics, cx - size + 1, cy + size / 2, cx - size + 2, cy + size + 2, pulseColor);
			HgnDraw.rect(graphics, cx + size - 1, cy + size / 2, cx + size, cy + size + 2, pulseColor);
		} else {
			HgnDraw.rect(graphics, cx - size / 2, cy - size - 1, cx + size / 2 + 1, cy - size, pulseColor);
			HgnDraw.rect(graphics, cx - size - 1, cy + size / 3, cx + size + 2, cy + size / 3 + 1, pulseColor);
		}
	}

	private static void drawCrosshair(GuiGraphics graphics, int width, int height, float transition) {
		if (transition > 0.02f) HgnSurface.hudCrosshair(graphics, width / 2, height / 2, 15);
	}

	private static void drawScannerHud(GuiGraphics graphics, Minecraft minecraft, int allowedRange,
		int echoReach, int width, int height, float transition) {
		float scale = HgnHud.overlayScale(width, height);
		int virtualWidth = Math.max(1, (int) Math.ceil(width / scale));
		int x = Math.max(1, Math.round(6 / scale));
		int y = Math.max(1, Math.round(HgnHud.overlayTopY(minecraft, height, false) / scale));
		int panelWidth = Math.min(156, Math.max(104, virtualWidth - x * 2));
		int accent = withAlpha(auraColor, transition);
		int dim = withAlpha(0xFF8CB0C4, transition);
		HgnDraw.beginScale(graphics, scale);
		HgnSurface.hudPlayerHeader(graphics, x, y, panelWidth, 44);
		HgnSurface.hudRaceModule(graphics, x + 5, y + 7, 30, 30, 3, 0);
		HgnSurface.hudRaceModule(graphics, x + panelWidth - 15, y + 7, 9, 9, 3, 3);
		String range = "RANGE " + echoReach + "/" + allowedRange + "m";
		String rank = HgnMessages.dragonEyeRank > 0 ? "R" + HgnMessages.dragonEyeRank : "SYNC";
		int textX = x + 38;
		int textWidth = panelWidth - 49;
		HgnHud.drawCenteredLane(graphics, minecraft.font, "DRAGON EYE  //  " + rank,
			textX, y + 3, textWidth, 11, accent);
		HgnHud.drawCenteredLane(graphics, minecraft.font, "AURA ECHO  //  " + range,
			textX, y + 14, textWidth, 11, dim);
		int barX = x + 38;
		int barY = y + 27;
		int barW = panelWidth - 48;
		HgnSurface.meter(graphics, barX, barY, barW, 5, auraStrength, HgnPalette.CYAN);
		HgnHud.drawCenteredLane(graphics, minecraft.font,
			"AURA " + Math.round(auraStrength * 100) + "%",
			textX, y + 33, textWidth, 10, dim);
		HgnDraw.endScale(graphics);

		if (motionEnabled() && HgnConfig.get().sceneParticles) {
			int scanY = 54 + (int) ((timeOffset() / 20L) % Math.max(1, height - 66));
			HgnDraw.rect(graphics, 0, scanY, width, scanY + 1, withAlpha(auraColor, transition * 0.10f));
		}
	}

	private static boolean motionEnabled() {
		HgnConfig config = HgnConfig.get();
		return config.interfaceAnimations && !config.reducedMotion;
	}

	/** The crosshair target gets one real renderer-backed model, never one model per sensed mob. */
	private static void drawFocusedTarget(GuiGraphics graphics, Minecraft minecraft,
		int width, int height, float transition) {
		if (!(minecraft.hitResult instanceof EntityHitResult hit)
			|| !(hit.getEntity() instanceof LivingEntity entity)
			|| entity == minecraft.player || entity.isInvisible()) return;
		BeastDescriptor info = beastByUuid.get(entity.getUUID());
		int color = colorFor(entity, info);
		float scale = HgnHud.overlayScale(width, height);
		int virtualWidth = Math.max(1, (int) Math.ceil(width / scale));
		int virtualHeight = Math.max(1, (int) Math.ceil(height / scale));
		int margin = Math.max(1, Math.round(6 / scale));
		int panelWidth = Math.min(156, Math.max(108, virtualWidth - margin * 2));
		int panelHeight = 58;
		int x = virtualWidth - margin - panelWidth;
		int safeTop = Math.max(margin,
			Math.round(HgnHud.overlayTopY(minecraft, height, true) / scale));
		int y = Math.min(Math.max(margin, virtualHeight - panelHeight - margin), safeTop);
		long currentHp = info != null && info.health() >= 0L
			? info.health() : Math.round(entity.getHealth());
		long maximumHp = Math.max(1L, info != null && info.maximumHealth() > 0L
			? info.maximumHealth() : Math.round(entity.getMaxHealth()));
		float health = Mth.clamp(currentHp / (float) maximumHp, 0.0f, 1.0f);
		int textX = x + 48;
		int textWidth = panelWidth - 54;

		HgnDraw.beginScale(graphics, scale);
		HgnSurface.hudTarget(graphics, x, y, panelWidth, panelHeight);
		HgnSurface.hudPortraitSocket(graphics, x + 4, y + 7, 39, 39);
		int modelScale = Math.max(10, Math.min(24,
			Math.round(32.0f / Math.max(1.0f, entity.getBbHeight()))));
		HgnEntityPreview.render(graphics, x + 5, y + 5, x + 42, y + 48, modelScale, entity);
		HgnHud.drawCenteredLane(graphics, minecraft.font,
			readoutLabel(entity, info).toUpperCase(Locale.ROOT),
			textX, y + 3, textWidth, 11, withAlpha(color, transition));
		HgnHud.drawCenteredLane(graphics, minecraft.font, rankLabel(info),
			textX, y + 14, textWidth, 11, withAlpha(rankColor(info, color), transition));
		HgnHud.drawCenteredLane(graphics, minecraft.font,
			"HP " + HgnHud.abbreviate(currentHp) + " / " + HgnHud.abbreviate(maximumHp),
			textX, y + 25, textWidth, 11, HgnPalette.TEXT);
		String model = info == null ? "" : info.model();
		String modelLabel = model.isBlank() ? entity.getType().getDescription().getString() : model;
		HgnHud.drawCenteredLane(graphics, minecraft.font, modelLabel,
			textX, y + 36, textWidth, 11, withAlpha(0xFF8CB0C4, transition));
		HgnSurface.meter(graphics, textX, y + 49, textWidth, 5, health,
			color == ENEMY_COLOR ? HgnPalette.DANGER : HgnPalette.CYAN);
		HgnDraw.endScale(graphics);
	}

	/** Health comes from the normal client entity sync; names/types follow server beast data when present. */
	private static void drawEntityReadout(GuiGraphics graphics, LivingEntity entity, BeastDescriptor info, int color,
		double[] camera, double focal, int width, int height, float transition) {
		AABB box = entity.getBoundingBox();
		if (!project((box.minX + box.maxX) / 2.0, box.maxY + 0.25, (box.minZ + box.maxZ) / 2.0,
			camera, focal, width, height)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		String label = readoutLabel(entity, info).toUpperCase(Locale.ROOT);
		String rank = rankLabel(info);
		long currentHp = info != null && info.health() >= 0L
			? info.health() : Math.round(entity.getHealth());
		long maximumHp = Math.max(1L, info != null && info.maximumHealth() > 0L
			? info.maximumHealth() : Math.round(entity.getMaxHealth()));
		float fraction = Mth.clamp(currentHp / (float) maximumHp, 0.0f, 1.0f);
		String hp = "HP " + HgnHud.abbreviate(currentHp) + "/" + HgnHud.abbreviate(maximumHp);
		int panelWidth = Math.min(116, Math.max(62, Math.max(Math.max(minecraft.font.width(label), minecraft.font.width(rank)),
			minecraft.font.width(hp)) + 10));
		int panelHeight = 24;
		int x = Mth.clamp(SCRATCH[0] - panelWidth / 2, 3, width - panelWidth - 3);
		float hudScale = HgnHud.overlayScale(width, height);
		int hotbarSafeTop = height - Math.max(31, Math.round(46.0f * hudScale));
		int y = Mth.clamp(SCRATCH[1] - 27, 3, Math.max(3, hotbarSafeTop - panelHeight));
		if (overlapsPersistentModule(minecraft, width, height, x, y, panelWidth, panelHeight, hudScale)) return;
		int accent = withAlpha(color, transition);
		HgnSurface.hudMetric(graphics, x, y, panelWidth, panelHeight);
		HgnHud.drawCenteredLane(graphics, minecraft.font, label,
			x + 3, y + 1, panelWidth - 6, 10, accent);
		HgnHud.drawCenteredLane(graphics, minecraft.font, rank + "  //  " + hp,
			x + 3, y + 10, panelWidth - 6, 10, withAlpha(rankColor(info, color), transition));
		HgnSurface.meter(graphics, x + 3, y + 20, panelWidth - 6, 3,
			fraction, color == ENEMY_COLOR ? HgnPalette.DANGER : HgnPalette.CYAN);
	}

	private static boolean overlapsPersistentModule(Minecraft minecraft, int width, int height,
		int x, int y, int panelWidth, int panelHeight, float scale) {
		int cornerWidth = Math.max(42, Math.round(164.0f * scale));
		int safeBottom = HgnHud.overlayTopY(minecraft, height, true)
			+ Math.max(16, Math.round(62.0f * scale));
		if (y >= safeBottom) return false;
		return x < cornerWidth || x + panelWidth > width - cornerWidth;
	}

	private static String readoutLabel(LivingEntity entity, BeastDescriptor info) {
		if (info != null && !info.label().isEmpty()) return info.label();
		return entity.getName().getString();
	}

	private static String rankLabel(BeastDescriptor info) {
		return info == null || info.rank().isEmpty() ? "LIFE SIGNAL" : info.rank();
	}

	private static String trim(Minecraft minecraft, String text, int width) {
		if (minecraft.font.width(text) <= width) return text;
		String suffix = "...";
		int available = Math.max(0, width - minecraft.font.width(suffix));
		String visible = minecraft.font.plainSubstrByWidth(text, available);
		return visible.isEmpty() ? suffix : visible + suffix;
	}

	private static int colorFor(Entity entity, BeastDescriptor info) {
		if (info != null) {
			if (info.rankColor() != null) return info.rankColor();
			if (info.color() != null) return info.color();
			switch (info.kind()) {
				case "mob" -> {
					return ENEMY_COLOR;
				}
				case "animal" -> {
					return ANIMAL_COLOR;
				}
				case "beast" -> {
					return UNKNOWN_BEAST_COLOR;
				}
				default -> {
				}
			}
		}
		if (entity instanceof Player) return PLAYER_COLOR;
		if (entity instanceof Enemy) return ENEMY_COLOR;
		if (entity instanceof Animal) return ANIMAL_COLOR;
		return OTHER_COLOR;
	}

	private static int rankColor(BeastDescriptor info, int fallback) {
		return info != null && info.rankColor() != null ? info.rankColor() : fallback;
	}

	private static Integer namedColor(JsonObject object, String key) {
		try {
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? parseColor(object.get(key)) : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void refreshBeasts() {
		JsonObject beasts = HgnMessages.lastBeasts;
		if (beasts == parsedBeasts) {
			return;
		}
		parsedBeasts = beasts;
		beastByUuid.clear();
		JsonElement entities = beasts != null ? beasts.get("entities") : null;
		if (entities == null || !entities.isJsonArray()) {
			return;
		}
		for (JsonElement element : entities.getAsJsonArray()) {
			if (beastByUuid.size() >= MAX_BEAST_DESCRIPTORS || !element.isJsonObject()) {
				break;
			}
			JsonObject object = element.getAsJsonObject();
			if (!object.has("uuid")) {
				continue;
			}
			try {
				beastByUuid.put(UUID.fromString(object.get("uuid").getAsString()),
					descriptor(object));
			} catch (Exception ignored) {
				// Bad plugin data is ignored instead of breaking a frame.
			}
		}
	}

	private static BeastDescriptor descriptor(JsonObject object) {
		String model = string(object, "model", "");
		String label = firstString(object, "displayName", "model", "species", "kind");
		String rankName = string(object, "rankName", "");
		String rawRank = string(object, "rank", "");
		String rank = !rankName.isEmpty() ? rankName
			: rawRank.isEmpty() ? "LIFE SIGNAL" : "RANK " + rawRank;
		String shape = string(object, "shape", model).toLowerCase(Locale.ROOT);
		long health = optionalNumber(object, "hp", "currentHp", "currentHP", "health", "currentHealth");
		long maximumHealth = optionalNumber(object,
			"hpMax", "hp_max", "maxHp", "maxHP", "healthMax", "maxHealth");
		return new BeastDescriptor(label, rank, model, shape,
			string(object, "kind", ""), namedColor(object, "color"),
			namedColor(object, "rankColor"), health, maximumHealth);
	}

	private static String firstString(JsonObject object, String... keys) {
		for (String key : keys) {
			String value = string(object, key, "");
			if (!value.isEmpty()) return value;
		}
		return "";
	}

	private static long optionalNumber(JsonObject object, String... keys) {
		for (String key : keys) {
			try {
				if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
					return Math.max(0L, object.get(key).getAsLong());
				}
			} catch (Exception ignored) {
			}
		}
		return -1L;
	}

	private static void refreshAura() {
		JsonObject hud = HgnMessages.lastHud;
		int rank = HgnMessages.dragonEyeRank;
		if (hud == parsedAuraHud && rank == parsedAuraRank) {
			return;
		}
		parsedAuraHud = hud;
		parsedAuraRank = rank;
		auraColor = color(hud, "auraColor", color(hud, "raceTheme", 0xFF54DFFF));
		long maxQi = number(hud, "qiMax");
		if (maxQi > 0) {
			auraStrength = clamp01(number(hud, "qi") / (float) maxQi);
		} else if (rank > 0) {
			auraStrength = clamp01(0.35f + rank * 0.22f);
		} else {
			auraStrength = 0.62f;
		}
	}

	private static int pulsePeriod() {
		return Math.round(2380f - auraStrength * 1260f);
	}

	private static float echoAt(double distance, int reach, long time) {
		if (distance > reach) {
			return 0f;
		}
		float phase = (time % pulsePeriod()) / (float) pulsePeriod();
		float waveDistance = phase * reach;
		float width = 1.4f + auraStrength * 3.6f;
		float offset = Math.abs((float) distance - waveDistance);
		if (offset >= width) {
			return 0.015f + auraStrength * 0.025f;
		}
		float signal = 1f - offset / width;
		return signal * signal * (0.58f + auraStrength * 0.42f);
	}

	private static float stagger(BlockPos pos) {
		int hash = pos.getX() * 73428767 ^ pos.getY() * 912931 ^ pos.getZ() * 19349663;
		return (hash & 7) * 0.11f;
	}

	private static int blend(int first, int second, float amount) {
		float t = clamp01(amount);
		int r = Math.round(((first >>> 16) & 0xFF) * (1f - t) + ((second >>> 16) & 0xFF) * t);
		int g = Math.round(((first >>> 8) & 0xFF) * (1f - t) + ((second >>> 8) & 0xFF) * t);
		int b = Math.round((first & 0xFF) * (1f - t) + (second & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static String string(JsonObject object, String key, String fallback) {
		try {
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsString() : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static long number(JsonObject object, String key) {
		try {
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsLong() : 0L;
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static int color(JsonObject object, String key, int fallback) {
		try {
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? parseColor(object.get(key)) : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static Integer parseColor(JsonElement element) {
		try {
			String hex = element.getAsString();
			String text = hex.startsWith("#") ? hex.substring(1) : hex;
			long value = Long.parseLong(text, 16);
			return text.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
		} catch (Exception ignored) {
			return null;
		}
	}

	public static void reset() {
		blockCache.clear();
		blockCacheBuild.clear();
		blockCacheX = Integer.MIN_VALUE;
		blockCacheY = Integer.MIN_VALUE;
		blockCacheZ = Integer.MIN_VALUE;
		blockCacheRange = -1;
		blockCacheBudget = -1;
		blockCacheLevel = null;
		blockBuildX = Integer.MIN_VALUE;
		blockBuildY = Integer.MIN_VALUE;
		blockBuildZ = Integer.MIN_VALUE;
		blockBuildRange = -1;
		blockBuildBudget = -1;
		blockBuildIndex = 0;
		beastByUuid.clear();
		parsedBeasts = null;
		parsedAuraHud = null;
		parsedAuraRank = Integer.MIN_VALUE;
		auraColor = 0xFF54DFFF;
		auraStrength = 0.62f;
		lastActive = false;
		opening = false;
		transitionStart = Long.MIN_VALUE;
		requestedActive = false;
		stableActive = false;
		requestedChangedAt = Long.MIN_VALUE;
		renderCostEwmaNanos = 0L;
	}

	private static int withAlpha(int argb, float factor) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * clamp01(factor));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	private static float clamp01(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	private static long timeOffset() {
		return HgnCompat.millis();
	}

	/** Pinhole projection using the compatibility camera basis, independent of renderer internals. */
	private static boolean project(double wx, double wy, double wz, double[] camera, double focal, int width, int height) {
		double dx = wx - camera[0];
		double dy = wy - camera[1];
		double dz = wz - camera[2];
		double forward = dx * camera[3] + dy * camera[4] + dz * camera[5];
		if (forward <= NEAR_CLIP) {
			return false;
		}
		double up = dx * camera[6] + dy * camera[7] + dz * camera[8];
		double right = -(dx * camera[9] + dy * camera[10] + dz * camera[11]);
		int x = (int) Math.round(width / 2.0 + (right / forward) * focal);
		int y = (int) Math.round(height / 2.0 - (up / forward) * focal);
		if (x < -8 || y < -8 || x > width + 8 || y > height + 8) {
			return false;
		}
		SCRATCH[0] = x;
		SCRATCH[1] = y;
		return true;
	}
}
