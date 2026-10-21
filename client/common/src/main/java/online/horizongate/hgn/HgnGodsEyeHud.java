package online.horizongate.hgn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.net.HgnDungeons;
import online.horizongate.hgn.ui.HgnPalette;

/** Passive, server-authorized dungeon insight above the right-side scoreboard. */
public final class HgnGodsEyeHud {
	private HgnGodsEyeHud() {
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!HgnGameplayHud.visible(minecraft)) return;
		HgnDungeons.QuestState quest = HgnDungeons.quest();
		if (quest == null || !HgnDungeons.inRun()) return;
		var layout = HgnGameplayHud.layout(graphics);
		if (!layout.readoutsVisible()) return;
		int width = Math.min(184, graphics.guiWidth() - 12);
		if (width < 100 || graphics.guiHeight() < 110) return;
		int x = graphics.guiWidth() - width - 6;
		int height = quest.zombieUnlocked() ? 68 : 38;
		int y = Math.max(56, layout.context().bottom() + 5);
		if (y + height > graphics.guiHeight() - 50) return;
		HgnDraw.rect(graphics, x, y, x + width, y + height, 0xDF061621);
		HgnDraw.rect(graphics, x, y, x + width, y + 1, HgnPalette.CYAN);
		HgnDraw.rect(graphics, x, y + height - 1, x + width, y + height, HgnPalette.EDGE_STRONG);
		HgnDraw.rect(graphics, x, y, x + 1, y + height, HgnPalette.EDGE_STRONG);
		HgnDraw.rect(graphics, x + width - 1, y, x + width, y + height, HgnPalette.EDGE_STRONG);
		HgnDraw.text(graphics, minecraft.font,
			quest.zombieUnlocked() ? "GOD'S EYE  //  ZOMBIE" : "GOD'S EYE  //  LOCKED",
			x + 6, y + 6, HgnPalette.CYAN);
		if (!quest.zombieUnlocked()) {
			HgnDraw.text(graphics, minecraft.font,
				"INFINITE ZOMBIE  " + quest.clearedZombieWave() + "/25",
				x + 6, y + 21, HgnPalette.TEXT);
			return;
		}
		HgnDungeons.MobInsight mob = HgnDungeons.insight();
		if (mob == null) {
			HgnDraw.text(graphics, minecraft.font, "PASSIVE INSIGHT ACTIVE",
				x + 6, y + 21, HgnPalette.TEXT);
		} else {
			HgnDraw.text(graphics, minecraft.font, fit(minecraft, mob.name(), width - 12),
				x + 6, y + 21, HgnPalette.TEXT);
			HgnDraw.text(graphics, minecraft.font,
				fit(minecraft, mob.role().toUpperCase(java.util.Locale.ROOT) + "  //  HP "
					+ Math.round(mob.hp()) + "/" + Math.round(mob.maxHp()), width - 12),
				x + 6, y + 35, HgnPalette.TEXT_MUTED);
			if (mob.bestPower() != null) {
				HgnDraw.text(graphics, minecraft.font,
					fit(minecraft, "BEST POWER  " + mob.bestPower(), width - 12),
					x + 6, y + 49, HgnPalette.CYAN);
			}
		}
	}

	private static String fit(Minecraft minecraft, String value, int maxWidth) {
		if (minecraft.font.width(value) <= maxWidth) return value;
		String clipped = value;
		while (!clipped.isEmpty() && minecraft.font.width(clipped + "..") > maxWidth) {
			clipped = clipped.substring(0, clipped.length() - 1);
		}
		return clipped + "..";
	}
}
