package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

/** Client-thread-only dropped-item and XP visual budget. Entity simulation is never changed. */
public final class HgnEntityRenderBudget {
	private static ClientLevel sampledLevel;
	private static int ticks;
	private static int clutterCount;
	private static long skipped;
	private static long previousSkipped;
	private static boolean disabledForSession;

	private HgnEntityRenderBudget() {
	}

	public static void tick(Minecraft minecraft) {
		if (disabledForSession || !HgnConfig.get().crowdedItemRendering || minecraft.level == null) {
			sampledLevel = null;
			ticks = clutterCount = 0;
			skipped = previousSkipped = 0;
			return;
		}
		if (sampledLevel != minecraft.level) {
			sampledLevel = minecraft.level;
			ticks = clutterCount = 0;
			skipped = previousSkipped = 0;
		}
		if (ticks++ % 20 != 0) return;
		previousSkipped = skipped;
		skipped = 0;
		clutterCount = 0;
		int inspected = 0;
		try {
			for (Entity entity : sampledLevel.entitiesForRendering()) {
				if (++inspected > HgnEntityBudgetPolicy.MAX_SCAN) break;
				if (eligible(entity) && ++clutterCount > HgnEntityBudgetPolicy.HIGH_CROWD_THRESHOLD) break;
			}
		} catch (RuntimeException | LinkageError failure) {
			disableForSession(failure);
		}
	}

	public static boolean skip(Entity entity, double cameraX, double cameraY, double cameraZ) {
		if (disabledForSession || sampledLevel == null) return false;
		try {
			if (entity.level() != sampledLevel || !HgnConfig.get().crowdedItemRendering
				|| clutterCount <= HgnEntityBudgetPolicy.CROWD_THRESHOLD || !eligible(entity)) return false;
			boolean skip = HgnEntityBudgetPolicy.skip(true, clutterCount, true,
				entity.hasCustomName() || entity.isCurrentlyGlowing(),
				entity.distanceToSqr(cameraX, cameraY, cameraZ));
			if (skip) skipped++;
			return skip;
		} catch (RuntimeException | LinkageError failure) {
			disableForSession(failure);
			return false;
		}
	}

	/** Render checks skipped in the preceding sampling interval, not a count of entities removed. */
	public static long skippedRenderChecks() {
		return previousSkipped;
	}

	private static boolean eligible(Entity entity) {
		return entity instanceof ExperienceOrb || entity instanceof ItemEntity;
	}

	private static void disableForSession(Throwable failure) {
		disabledForSession = true;
		sampledLevel = null;
		clutterCount = 0;
		HgnClient.LOGGER.warn("Optional crowded drop/XP rendering disabled for this session", failure);
	}
}
