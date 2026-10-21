package online.horizongate.hgn;

import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Gives Minecraft's chunk renderer priority after joins, dimension changes and
 * large position jumps. Optional HGN rendering and backend work can consult this
 * guard instead of competing with the client while a new chunk neighbourhood is
 * being assembled.
 */
public final class HgnPerformanceGuard {
	private static final double TELEPORT_DISTANCE_SQUARED = 32.0D * 32.0D;
	private static final long CHUNK_WARMUP_NANOS = TimeUnit.SECONDS.toNanos(4L);
	private static ClientLevel sampledLevel;
	private static double sampledX;
	private static double sampledY;
	private static double sampledZ;
	private static boolean sampledPlayer;
	private static volatile long chunkWarmupUntilNanos;

	private HgnPerformanceGuard() {
	}

	/** Called from the normal client tick; this method does no chunk access. */
	public static void tick(Minecraft minecraft) {
		if (minecraft == null || minecraft.level == null || minecraft.player == null) {
			sampledLevel = null;
			sampledPlayer = false;
			return;
		}
		double x = minecraft.player.getX();
		double y = minecraft.player.getY();
		double z = minecraft.player.getZ();
		boolean levelChanged = sampledLevel != minecraft.level;
		boolean teleported = sampledPlayer && squared(
			x - sampledX, y - sampledY, z - sampledZ) >= TELEPORT_DISTANCE_SQUARED;
		if (!sampledPlayer || levelChanged || teleported) {
			chunkWarmupUntilNanos = System.nanoTime() + CHUNK_WARMUP_NANOS;
		}
		sampledLevel = minecraft.level;
		sampledX = x;
		sampledY = y;
		sampledZ = z;
		sampledPlayer = true;
	}

	public static boolean chunksWarming() {
		return System.nanoTime() < chunkWarmupUntilNanos;
	}

	private static double squared(double x, double y, double z) {
		return x * x + y * y + z * z;
	}
}
