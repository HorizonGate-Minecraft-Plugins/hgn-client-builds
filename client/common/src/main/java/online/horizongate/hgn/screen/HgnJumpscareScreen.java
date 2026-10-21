package online.horizongate.hgn.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import java.util.concurrent.TimeUnit;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;

/**
 * A server-authorized, full-screen HGN Core event. The client deliberately
 * accepts no duration, sound, or asset fields from the network: every event is
 * the same 10-second local experience and cannot be used to make the client
 * fetch arbitrary content.
 */
public final class HgnJumpscareScreen extends Screen {
	public static final int DURATION_MS = 10_000;
	public static final int TRIGGER_COOLDOWN_SECONDS = 60;
	private static final String TEXTURE = "textures/gui/jumpscare/rift_stalker.png";
	private static final int TEXTURE_SIZE = 1_254;
	private static final long TRIGGER_COOLDOWN_NANOS =
		TimeUnit.SECONDS.toNanos(TRIGGER_COOLDOWN_SECONDS);
	private static long lastAcceptedTriggerNanos = Long.MIN_VALUE;

	private final Screen returnTo;
	private long startedAt;

	private HgnJumpscareScreen(Screen returnTo) {
		super(Component.literal("HGN Core Rift Event"));
		this.returnTo = returnTo;
	}

	/** Opens the fixed jumpscare on the Minecraft client thread. */
	public static void show() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.screen instanceof HgnJumpscareScreen) {
				return;
			}
			if (!claimTrigger(System.nanoTime())) return;
			minecraft.setScreen(new HgnJumpscareScreen(minecraft.screen));
		});
	}

	/** Claims at most one accepted local trigger per monotonic 60-second window. */
	static synchronized boolean claimTrigger(long nowNanos) {
		if (lastAcceptedTriggerNanos != Long.MIN_VALUE) {
			long elapsed = nowNanos - lastAcceptedTriggerNanos;
			if (elapsed < 0L || elapsed < TRIGGER_COOLDOWN_NANOS) return false;
		}
		lastAcceptedTriggerNanos = nowNanos;
		return true;
	}

	@Override
	protected void init() {
		startedAt = HgnCompat.millis();
		// Deliberately strong but still obeys the player's master sound setting.
		if (this.minecraft.player != null) {
			this.minecraft.player.playSound(SoundEvents.WARDEN_ROAR, 1.65f, 0.72f);
		}
	}

	@Override
	public void tick() {
		if (HgnCompat.millis() - startedAt >= DURATION_MS) {
			this.minecraft.setScreen(returnTo);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		HgnDraw.rect(graphics, 0, 0, this.width, this.height, 0xFF000000);
		int drawSize = Math.min(this.width, this.height);
		int drawX = (this.width - drawSize) / 2;
		int drawY = (this.height - drawSize) / 2;
		HgnDraw.textureStretch(graphics, TEXTURE, drawX, drawY, drawSize, drawSize,
			TEXTURE_SIZE, TEXTURE_SIZE);
		long elapsed = Math.max(0L, HgnCompat.millis() - startedAt);
		int flash = elapsed < 180L ? 0x70F3FCFF : elapsed < 520L ? 0x28A85CFF : 0x00000000;
		if (flash != 0) {
			HgnDraw.rect(graphics, 0, 0, this.width, this.height, flash);
		}
		int remaining = (int) Math.max(0L, (DURATION_MS - elapsed + 999L) / 1000L);
		HgnDraw.centered(graphics, this.font, "RIFT BREACH", this.width / 2, this.height - 32,
			0xD9EBDFFF);
		HgnDraw.centered(graphics, this.font, remaining + "s", this.width / 2, this.height - 18,
			0xC9A85CFF);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
