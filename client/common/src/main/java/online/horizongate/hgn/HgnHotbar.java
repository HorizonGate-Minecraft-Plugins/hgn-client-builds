package online.horizongate.hgn;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnSurface;

/** A guarded replacement for Minecraft's item hotbar, backed by live player inventory. */
public final class HgnHotbar {
	private static final int HOTBAR_WIDTH = 182;
	private static final int HOTBAR_HEIGHT = 22;
	private static final int SLOT_SPACING = 20;
	private static boolean failedForSession;
	private static boolean renderedThisFrame;

	private HgnHotbar() {
	}

	private static boolean canReplace(Minecraft minecraft, GuiGraphics graphics) {
		return !failedForSession && HgnConfig.get().experimentalHgnHotbar
			&& HgnGameplayHud.visible(minecraft)
			&& !minecraft.player.isSpectator()
			&& minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR
			&& minecraft.getCameraEntity() == minecraft.player
			&& minecraft.font != null && graphics.guiWidth() >= 244 && graphics.guiHeight() >= 56;
	}

	/** Returns true only after the complete replacement has rendered. */
	public static boolean render(GuiGraphics graphics) {
		renderedThisFrame = false;
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (!canReplace(minecraft, graphics)) return false;
			LocalPlayer player = minecraft.player;
			int selected = HgnCompat.selectedHotbarSlot();
			if (selected < 0 || selected >= 9) return false;
			ItemStack[] slots = new ItemStack[9];
			for (int slot = 0; slot < slots.length; slot++) {
				slots[slot] = player.getInventory().getItem(slot);
				if (slots[slot] == null) return false;
			}
			ItemStack offhand = player.getOffhandItem();
			HumanoidArm offhandSide = player.getMainArm().getOpposite();
			if (offhand == null || offhandSide == null) return false;
			boolean attackIndicator = minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR;
			float attackStrength = attackIndicator ? player.getAttackStrengthScale(0.0f) : 1.0f;
			if (!Float.isFinite(attackStrength)) return false;
			draw(graphics, minecraft, player, slots, selected, offhand, offhandSide, attackStrength);
			renderedThisFrame = true;
			return true;
		} catch (RuntimeException | LinkageError error) {
			failedForSession = true;
			HgnClient.LOGGER.warn("HGN hotbar failed; using Minecraft's hotbar for this session", error);
			return false;
		}
	}

	static boolean renderedThisFrame() {
		return renderedThisFrame;
	}

	private static void draw(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player,
		ItemStack[] slots, int selected, ItemStack offhand, HumanoidArm offhandSide,
		float attackStrength) {
		int center = graphics.guiWidth() / 2;
		int left = center - HOTBAR_WIDTH / 2;
		int top = graphics.guiHeight() - HOTBAR_HEIGHT;
		HgnSurface.hudHotbar(graphics, left, top, HOTBAR_WIDTH, HOTBAR_HEIGHT);
		for (int slot = 0; slot < slots.length; slot++) {
			int itemX = center - 88 + slot * SLOT_SPACING;
			HgnDraw.rect(graphics, itemX - 1, top + 2, itemX + 17, top + 20, 0xB8081118);
		}
		HgnSurface.hudHotbarSelected(graphics,
			center - 92 + selected * SLOT_SPACING, top - 1, 24, 23);
		for (int slot = 0; slot < slots.length; slot++) {
			drawItem(graphics, minecraft, player, slots[slot],
				center - 88 + slot * SLOT_SPACING, graphics.guiHeight() - 19, slot + 1);
		}
		if (!offhand.isEmpty()) {
			boolean leftSide = offhandSide == HumanoidArm.LEFT;
			int frameX = leftSide ? center - 120 : center + 91;
			int itemX = leftSide ? center - 117 : center + 101;
			HgnSurface.hudHotbar(graphics, frameX, top - 1, 29, 24);
			HgnDraw.rect(graphics, itemX - 1, top + 2, itemX + 17, top + 20, 0xB8081118);
			drawItem(graphics, minecraft, player, offhand,
				itemX, graphics.guiHeight() - 19, 10);
		}
		if (attackStrength < 1.0f) {
			int x = offhandSide == HumanoidArm.RIGHT ? center - 113 : center + 97;
			int y = graphics.guiHeight() - 20;
			HgnSurface.hudHotbar(graphics, x - 1, y - 1, 20, 20);
			HgnDraw.rect(graphics, x + 2, y + 2, x + 16, y + 16, 0xD0081118);
			int fill = Math.max(0, Math.min(14, (int) (attackStrength * 15.0f)));
			if (fill > 0) {
				HgnDraw.rect(graphics, x + 2, y + 16 - fill, x + 16, y + 16, HgnPalette.CYAN);
			}
		}
	}

	private static void drawItem(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player,
		ItemStack stack, int x, int y, int seed) {
		if (stack.isEmpty()) return;
		graphics.renderItem(player, stack, x, y, seed);
		graphics.renderItemDecorations(minecraft.font, stack, x, y);
	}
}
