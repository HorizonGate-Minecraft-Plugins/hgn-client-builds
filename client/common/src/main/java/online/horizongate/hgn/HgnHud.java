package online.horizongate.hgn;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.compat.HgnDraw;
import online.horizongate.hgn.mixin.HgnBossOverlayAccessor;
import online.horizongate.hgn.net.HgnMessages;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRaceHudLayout;
import online.horizongate.hgn.ui.HgnSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compact VinkSMP race header and metrics, with a Dragon Eye-only hotbar.
 * Every number comes from the server snapshot except vanilla food and the
 * selected inventory slot, which deliberately stay synchronized with Minecraft.
 */
public final class HgnHud {
	private static final int X = 6;
	private static final int Y = 28;
	private static final int HEADER_WIDTH = HgnRaceHudLayout.HEADER_WIDTH;
	private static final int HEADER_HEIGHT = HgnRaceHudLayout.HEADER_HEIGHT;
	private static final int BAR_INSET = 14;
	private static final int BAR_HEIGHT = HgnRaceHudLayout.BAR_HEIGHT;
	private static final int BAR_GAP = HgnRaceHudLayout.BAR_GAP;
	private static final int MAX_BARS = HgnRaceHudLayout.MAX_BARS;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 1;
	private static final int HOTBAR_HEIGHT = 25;
	private static final float MIN_MODULE_SCALE = 0.18f;
	private static final float MAX_MODULE_SCALE = 0.68f;
	private static final float MIN_HOTBAR_SCALE = 0.32f;
	private static final float MAX_HOTBAR_SCALE = 0.78f;
	private static final float MIN_ADJUSTED_MODULE_SCALE = 0.16f;
	private static final float MAX_ADJUSTED_MODULE_SCALE = 0.76f;
	private static final float MIN_ADJUSTED_HOTBAR_SCALE = 0.28f;
	private static final float MAX_ADJUSTED_HOTBAR_SCALE = 0.86f;
	private static final float MIN_TEXT_FIT_SCALE = 0.70f;
	private static final long DEFAULT_BLOOD_MAX = 100L;
	private static final long STALE_MS = 5_000L;

	private static final RaceStyle HUMAN = new RaceStyle("HUMAN", 0xFF2EC7FF, 0xFF8DF5FF, 60, 0);
	private static final RaceStyle VAMPIRE = new RaceStyle("VAMPIRE", 0xFFE23A4E, 0xFFFF7C91, 60, 1);
	private static final RaceStyle WEREWOLF = new RaceStyle("WEREWOLF", 0xFF8BA8D5, 0xFFD8E5FF, 60, 2);
	private static final RaceStyle DRAGON = new RaceStyle("DRAGON", 0xFFFFA52B, 0xFFFFDA7D, 80, 3);
	private static final RaceStyle DEMON = new RaceStyle("DEMON", 0xFFFF334F, 0xFFB36AFF, 60, 4);
	private static final RaceStyle DALKI = new RaceStyle("DALKI", 0xFFBFD1E4, 0xFF6EDBFF, 60, 5);

	private static final List<Metric> metrics = new ArrayList<>();
	private static JsonObject cachedHud;
	private static RaceStyle style = HUMAN;
	private static String race = "HUMAN";
	private static String player = "";
	private static String title = "";
	private static String nextRank = "";
	private static String level = "LVL 0 / 60";
	private static String headerRaceLine = "HUMAN  //  LVL 0 / 60";
	private static String headerDetail = "RACE LINKED";
	private static String hotbarLevel = "0 / 60";
	private static Component cachedHeldStyled = Component.empty();
	private static ItemStack cachedHeldStack = ItemStack.EMPTY;
	private static boolean evolutionReady;
	private static long xp;
	private static long xpMax;
	private static boolean serverHealth;
	private static long health;
	private static long healthMax;
	private static long blood;
	private static long bloodMax = DEFAULT_BLOOD_MAX;
	private static GameType cachedMode;
	private static long cachedClientHealth = Long.MIN_VALUE;
	private static long cachedClientHealthMax = Long.MIN_VALUE;
	private static long cachedFood = Long.MIN_VALUE;

	private record RaceStyle(String name, int primary, int secondary, int defaultMaxLevel, int atlasRow) {
	}

	private record Metric(String text, long current, long maximum, int color, int module) {
	}

	private HgnHud() {
	}

	/** Drops all server-derived and render caches when the active connection closes. */
	public static void reset() {
		metrics.clear();
		cachedHud = null;
		style = HUMAN;
		race = "HUMAN";
		player = "";
		title = "";
		nextRank = "";
		level = "LVL 0 / 60";
		headerRaceLine = "HUMAN  //  LVL 0 / 60";
		headerDetail = "RACE LINKED";
		hotbarLevel = "0 / 60";
		cachedHeldStyled = Component.empty();
		cachedHeldStack = ItemStack.EMPTY;
		evolutionReady = false;
		xp = 0L;
		xpMax = 0L;
		serverHealth = false;
		health = 0L;
		healthMax = 0L;
		blood = 0L;
		bloodMax = DEFAULT_BLOOD_MAX;
		cachedMode = null;
		cachedClientHealth = Long.MIN_VALUE;
		cachedClientHealthMax = Long.MIN_VALUE;
		cachedFood = Long.MIN_VALUE;
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!shouldRender(minecraft)) return;
		var gameplayLayout = HgnGameplayHud.layout(graphics);
		if (!gameplayLayout.readoutsVisible()) return;
		refresh(HgnMessages.lastHud, minecraft);
		var raceLayout = HgnRaceHudLayout.resolve(graphics.guiWidth(), graphics.guiHeight(),
			gameplayLayout.raceTop(), metrics.size(), HgnConfig.get().hudSize.multiplier());
		if (!raceLayout.visible()) return;
		float moduleScale = raceLayout.scale();
		int virtualWidth = virtual(graphics.guiWidth(), moduleScale);
		int x = Math.max(1, Math.round(X / moduleScale));
		int y = Math.max(1, Math.round(gameplayLayout.raceTop() / moduleScale));
		HgnDraw.beginScale(graphics, moduleScale);
		drawHeader(graphics, minecraft, x, y, virtualWidth);
		drawBars(graphics, minecraft, x, y, virtualWidth);
		HgnDraw.endScale(graphics);

	}

	/** Dragon Eye shares the exact same live hotbar instead of maintaining a stale copy. */
	static void renderDragonEyeHotbar(GuiGraphics graphics, int accent) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || mode(minecraft) == GameType.SPECTATOR) return;
		if (HgnMessages.lastHud != null) refresh(HgnMessages.lastHud, minecraft);
		float scale = hotbarScale(graphics.guiWidth(), graphics.guiHeight());
		int virtualWidth = virtual(graphics.guiWidth(), scale);
		int virtualHeight = virtual(graphics.guiHeight(), scale);
		HgnDraw.beginScale(graphics, scale);
		drawHotbar(graphics, minecraft, virtualWidth, virtualHeight,
			Math.max(2, Math.round(5 / scale)), accent);
		HgnDraw.endScale(graphics);
	}

	private static boolean shouldRender(Minecraft minecraft) {
		return HgnGameplayHud.visible(minecraft)
			&& HgnMessages.lastHud != null
			&& System.currentTimeMillis() - HgnMessages.lastHudAt <= STALE_MS;
	}

	private static void drawHeader(GuiGraphics graphics, Minecraft minecraft,
		int x, int y, int virtualWidth) {
		int width = Math.max(88, Math.min(HEADER_WIDTH, virtualWidth - x * 2));
		HgnSurface.hudPlayerHeader(graphics, x, y, width, HEADER_HEIGHT);
		HgnDraw.playerFace(graphics, x + 7, y + 10, 22);
		HgnSurface.hudPortraitSocket(graphics, x + 4, y + 7, 28, 28);
		int moduleX = x + width - 14;
		HgnSurface.hudRaceModule(graphics, moduleX, y + 4, 8, 8, style.atlasRow(), 0);
		HgnSurface.hudRaceModule(graphics, moduleX, y + 13, 8, 8, style.atlasRow(), 1);
		HgnSurface.hudRaceModule(graphics, moduleX, y + 22, 8, 8, style.atlasRow(), 3);
		HgnSurface.hudRaceModule(graphics, moduleX, y + 31, 8, 8, style.atlasRow(), 4);
		HgnDraw.rect(graphics, x + 36, y + HEADER_HEIGHT - 6, x + width - 18,
			y + HEADER_HEIGHT - 5, HgnPalette.alpha(style.secondary(), 210));

		Font font = minecraft.font;
		int textX = x + 35;
		int textWidth = Math.max(24, width - 52);
		String identity = player.isBlank() ? "VINKSMP PLAYER" : player;
		drawCenteredLane(graphics, font, identity, textX, y + 3, textWidth, 11, HgnPalette.TEXT);
		drawCenteredLane(graphics, font, headerRaceLine,
			textX, y + 14, textWidth, 11, style.primary());
		drawCenteredLane(graphics, font, headerDetail,
			textX, y + 25, textWidth, 11, style.secondary());
	}

	private static void drawBars(GuiGraphics graphics, Minecraft minecraft,
		int xOrigin, int yOrigin, int virtualWidth) {
		int headerWidth = Math.max(96, Math.min(HEADER_WIDTH, virtualWidth - xOrigin * 2));
		int width = Math.max(76, headerWidth - BAR_INSET * 2);
		int x = xOrigin + (headerWidth - width) / 2;
		int y = yOrigin + HEADER_HEIGHT + 3;
		int count = Math.min(MAX_BARS, metrics.size());
		for (int index = 0; index < count; index++) {
			drawMetric(graphics, minecraft.font, x, y, width, BAR_HEIGHT, metrics.get(index));
			y += BAR_HEIGHT + BAR_GAP;
		}
	}

	private static void drawMetric(GuiGraphics graphics, Font font,
		int x, int y, int width, int height, Metric metric) {
		HgnSurface.hudMetric(graphics, x, y, width, height);
		HgnSurface.hudRaceModule(graphics,
			x + 6, y + 2, 10, 10, style.atlasRow(), metric.module());
		drawCenteredLane(graphics, font, metric.text(),
			x + 18, y, width - 35, height - 3,
			HgnPalette.TEXT);
		if (metric.maximum() > 0) {
			int trackX = x + 20;
			int trackWidth = width - 28;
			int fillWidth = Math.round(trackWidth * progress(metric.current(), metric.maximum()));
			HgnDraw.rect(graphics, trackX, y + height - 3,
				trackX + trackWidth, y + height - 2, HgnPalette.alpha(HgnPalette.EDGE_STRONG, 150));
			if (fillWidth > 0) {
				HgnDraw.rect(graphics, trackX, y + height - 3,
					trackX + fillWidth, y + height - 2, metric.color());
			}
		}
	}

	private static void drawHotbar(GuiGraphics graphics, Minecraft minecraft,
		int virtualWidth, int virtualHeight, int bottomMargin, int accent) {
		int slotsWidth = SLOT * 9 + SLOT_GAP * 8;
		int frameWidth = slotsWidth + 12;
		int frameX = (virtualWidth - frameWidth) / 2;
		int x = frameX + 6;
		int y = virtualHeight - HOTBAR_HEIGHT - bottomMargin;
		HgnSurface.hudHotbar(graphics, frameX, y, frameWidth, HOTBAR_HEIGHT);

		int selected = Math.max(0, Math.min(8, HgnCompat.selectedHotbarSlot()));
		for (int slot = 0; slot < 9; slot++) {
			int slotX = x + slot * (SLOT + SLOT_GAP);
			if (slot == selected) {
				HgnSurface.hudHotbarSelected(graphics, slotX - 1, y + 3, SLOT + 2, SLOT + 2);
			}
			var stack = minecraft.player.getInventory().getItem(slot);
			graphics.renderItem(stack, slotX + 1, y + 5);
			graphics.renderItemDecorations(minecraft.font, stack, slotX + 1, y + 5);
		}

		if (xpMax > 0) {
			int xpWidth = Math.round((frameWidth - 22) * progress(xp, xpMax));
			if (xpWidth > 0) {
				HgnDraw.rect(graphics, frameX + 11, y + 2,
					frameX + 11 + xpWidth, y + 3, accent);
			}
		}
		HgnDraw.centered(graphics, minecraft.font, hotbarLevel,
			virtualWidth / 2, y - minecraft.font.lineHeight - 1, accent);
		drawHeldItem(graphics, minecraft, y, virtualWidth);
	}

	private static void drawHeldItem(GuiGraphics graphics, Minecraft minecraft,
		int hotbarY, int virtualWidth) {
		var held = minecraft.player.getMainHandItem();
		if (held.isEmpty()) return;
		// Inventory slots retain their ItemStack object until authoritative state
		// replaces it. Resolve the potentially component-heavy display name only
		// once per selected stack, including after a middle-click pick-block swap.
		if (held != cachedHeldStack) {
			cachedHeldStack = held;
			Component original = held.getHoverName();
			cachedHeldStyled = styledItemName(original);
		}
		Component label = cachedHeldStyled;
		int rawWidth = Math.max(1, minecraft.font.width(label));
		int maxWidth = Math.max(32, virtualWidth - 24);
		float scale = Math.min(1.0f, maxWidth / (float) rawWidth);
		int width = Math.max(52, Math.min(virtualWidth - 12, Math.round(rawWidth * scale) + 18));
		int height = Math.max(14, Math.round(10 * scale) + 5);
		int x = (virtualWidth - width) / 2;
		int y = hotbarY - height - minecraft.font.lineHeight - 5;
		HgnSurface.hudHeldLabel(graphics, x, y, width, height);
		float textHeight = minecraft.font.lineHeight * scale;
		int baseline = Math.round(y + Math.max(1.0f, (height - textHeight) / 2.0f));
		HgnDraw.scaledCentered(graphics, minecraft.font, label,
			virtualWidth / 2, baseline, scale, HgnPalette.TEXT);
	}

	static float moduleScale(int guiWidth, int guiHeight) {
		return moduleScale(guiWidth, guiHeight, metrics.size());
	}

	static float overlayScale(int guiWidth, int guiHeight) {
		return moduleScale(guiWidth, guiHeight, MAX_BARS);
	}

	private static float moduleScale(int guiWidth, int guiHeight, int metricCount) {
		int bars = Math.max(1, Math.min(MAX_BARS, metricCount));
		int stackHeight = HEADER_HEIGHT + 3 + bars * BAR_HEIGHT
			+ Math.max(0, bars - 1) * BAR_GAP;
		float widthScale = guiWidth * 0.15f / HEADER_WIDTH;
		float heightScale = guiHeight * 0.18f / stackHeight;
		float responsive = Math.max(MIN_MODULE_SCALE,
			Math.min(MAX_MODULE_SCALE, Math.min(widthScale, heightScale)));
		return adjustedScale(responsive, MIN_ADJUSTED_MODULE_SCALE, MAX_ADJUSTED_MODULE_SCALE);
	}

	private static float hotbarScale(int guiWidth, int guiHeight) {
		int frameWidth = SLOT * 9 + SLOT_GAP * 8 + 12;
		float widthScale = guiWidth * 0.34f / frameWidth;
		float heightScale = guiHeight * 0.085f / HOTBAR_HEIGHT;
		float responsive = Math.max(MIN_HOTBAR_SCALE,
			Math.min(MAX_HOTBAR_SCALE, Math.min(widthScale, heightScale)));
		return adjustedScale(responsive, MIN_ADJUSTED_HOTBAR_SCALE, MAX_ADJUSTED_HOTBAR_SCALE);
	}

	private static float adjustedScale(float responsive, float minimum, float maximum) {
		float multiplier = HgnConfig.get().hudSize.multiplier();
		return Math.max(minimum, Math.min(maximum, responsive * multiplier));
	}

	private static int virtual(int size, float scale) {
		return Math.max(1, (int) Math.ceil(size / scale));
	}

	/** Shared top safe-area contract for the race HUD and Dragon Eye modules. */
	static int overlayTopY(Minecraft minecraft, int guiHeight, boolean reserveStatusEffects) {
		int top = bossSafeY(minecraft, guiHeight);
		if (reserveStatusEffects && minecraft.player != null) {
			int effectCount = minecraft.player.getActiveEffects().size();
			if (effectCount > 0) top = Math.max(top, 5 + Math.min(6, effectCount) * 25);
		}
		return Math.min(Math.max(1, guiHeight - 18), top);
	}

	private static int bossSafeY(Minecraft minecraft, int guiHeight) {
		try {
			int count = ((HgnBossOverlayAccessor) (Object) minecraft.gui.getBossOverlay())
				.hgn$getEvents().size();
			if (count <= 1) return Y;
			int visible = Math.min(count, Math.max(1, (guiHeight / 3 - 12 + 18) / 19));
			return Math.max(Y, 21 + (visible - 1) * 19);
		} catch (Exception ignored) {
			return Y;
		}
	}

	private static Component styledItemName(Component original) {
		String raw = original.getString();
		return hasLegacyFormatting(raw) ? decodeLegacyFormatting(raw) : original;
	}

	private static boolean hasLegacyFormatting(String text) {
		for (int index = 0; index + 1 < text.length(); index++) {
			char marker = text.charAt(index);
			if (marker != '&' && marker != '\u00A7') continue;
			char code = text.charAt(index + 1);
			if (ChatFormatting.getByCode(code) != null
				|| code == '#' && hexAt(text, index + 2) != null
				|| Character.toLowerCase(code) == 'x' && sectionHexAt(text, index) != null) return true;
		}
		return false;
	}

	private static Component decodeLegacyFormatting(String text) {
		MutableComponent result = Component.empty();
		StringBuilder part = new StringBuilder();
		Style current = Style.EMPTY;
		for (int index = 0; index < text.length(); index++) {
			char marker = text.charAt(index);
			if ((marker == '&' || marker == '\u00A7') && index + 1 < text.length()) {
				char code = text.charAt(index + 1);
				Integer hex = code == '#' ? hexAt(text, index + 2)
					: Character.toLowerCase(code) == 'x' ? sectionHexAt(text, index) : null;
				ChatFormatting format = hex == null ? ChatFormatting.getByCode(code) : null;
				if (hex != null || format != null) {
					appendStyled(result, part, current);
					current = hex != null ? Style.EMPTY.withColor(hex)
						: format == ChatFormatting.RESET ? Style.EMPTY
						: format.isColor() ? Style.EMPTY.applyFormat(format) : current.applyFormat(format);
					index += hex != null && Character.toLowerCase(code) == 'x' ? 13 : hex != null ? 7 : 1;
					continue;
				}
			}
			part.append(marker);
		}
		appendStyled(result, part, current);
		return result;
	}

	private static void appendStyled(MutableComponent target, StringBuilder text, Style style) {
		if (text.isEmpty()) return;
		target.append(Component.literal(text.toString()).setStyle(style));
		text.setLength(0);
	}

	private static Integer hexAt(String text, int start) {
		if (start + 6 > text.length()) return null;
		try {
			return Integer.parseInt(text.substring(start, start + 6), 16);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Integer sectionHexAt(String text, int start) {
		if (start + 13 >= text.length()) return null;
		StringBuilder hex = new StringBuilder(6);
		for (int part = 0; part < 6; part++) {
			int markerIndex = start + 2 + part * 2;
			char marker = text.charAt(markerIndex);
			if ((marker != '&' && marker != '\u00A7')
				|| Character.digit(text.charAt(markerIndex + 1), 16) < 0) return null;
			hex.append(text.charAt(markerIndex + 1));
		}
		return Integer.parseInt(hex.toString(), 16);
	}

	private static void refresh(JsonObject hud, Minecraft minecraft) {
		boolean snapshotChanged = hud != cachedHud;
		if (snapshotChanged) {
			cachedHud = hud;
			race = str(hud, "race", "HUMAN").trim().toUpperCase(Locale.ROOT);
			style = styleFor(race);
			int primary = color(hud, "raceTheme", style.primary());
			int secondary = color(hud, "auraColor", style.secondary());
			style = new RaceStyle(style.name(), primary, secondary,
				style.defaultMaxLevel(), style.atlasRow());
			player = minecraft.player == null ? "" : minecraft.player.getName().getString();
			title = strFirst(hud, "title", "class", "rank", "subrace");
			nextRank = strFirst(hud, "nextRank", "next", "nextTier");
			long currentLevel = number(hud, "level");
			long maximumLevel = numberFirst(hud, "maxLevel", "levelMax", "max_level", "levelCap",
				"level_cap", "evolveLevel", "evolutionLevel", "evolveAt", "evolveAtLevel");
			if (maximumLevel <= 0) maximumLevel = style.defaultMaxLevel();
			boolean evolved = boolFirst(hud, "evolved", "isEvolved",
				"evolutionComplete", "evolution_complete");
			level = evolved ? "LVL " + currentLevel : "LVL " + currentLevel + " / " + maximumLevel;
			hotbarLevel = evolved ? Long.toString(currentLevel) : currentLevel + " / " + maximumLevel;
			evolutionReady = !evolved && maximumLevel > 0 && currentLevel >= maximumLevel;

			xp = number(hud, "xp");
			xpMax = numberFirst(hud, "xpMax", "xp_max");
			Long serverHp = numberPresent(hud, "hp", "currentHp", "currentHP", "health", "currentHealth",
				"realHp", "real_hp");
			Long serverHpMax = numberPresent(hud, "hpMax", "hp_max", "maxHp", "maxHP", "healthMax",
				"health_max", "maxHealth", "realHpMax", "real_hp_max");
			serverHealth = serverHp != null;
			if (serverHealth) {
				health = Math.max(0L, serverHp);
				// Older HGN-Core builds sent the authoritative MMO health without a maximum,
				// or paired it with vanilla's 20. Never throw that real value away.
				healthMax = serverHpMax == null || serverHpMax <= 0
					? Math.max(1L, health) : Math.max(health, serverHpMax);
			}

			Long serverBlood = numberPresent(hud, "blood", "bloodHunger", "bloodLevel", "bloodBank",
				"blood_bank");
			blood = Math.min(DEFAULT_BLOOD_MAX, serverBlood == null ? 0L : Math.max(0L, serverBlood));
			bloodMax = DEFAULT_BLOOD_MAX;
		}

		GameType gameType = mode(minecraft);
		long clientHealth = Math.round(minecraft.player.getHealth());
		long clientHealthMax = Math.round(minecraft.player.getMaxHealth());
		long food = minecraft.player.getFoodData().getFoodLevel();
		boolean dynamicChanged = gameType != cachedMode || clientHealth != cachedClientHealth
			|| clientHealthMax != cachedClientHealthMax || food != cachedFood;
		if (!serverHealth) {
			health = clientHealth;
			healthMax = clientHealthMax;
		}
		if (snapshotChanged || dynamicChanged) {
			cachedMode = gameType;
			cachedClientHealth = clientHealth;
			cachedClientHealthMax = clientHealthMax;
			cachedFood = food;
			rebuildMetrics(hud, gameType, food);
			rebuildHeader(gameType);
		}
	}

	private static void rebuildHeader(GameType gameType) {
		headerRaceLine = race + "  //  " + level;
		String detail = evolutionReady ? "EVOLVE READY" : !title.isBlank() ? title
			: nextRank.isBlank() ? "RACE LINKED" : "NEXT // " + nextRank;
		headerDetail = gameType == GameType.SURVIVAL ? detail
			: gameType.getName().toUpperCase(Locale.ROOT) + " // " + detail;
	}

	private static void rebuildMetrics(JsonObject hud, GameType gameType, long food) {
		metrics.clear();
		if (gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE) {
			metrics.add(metric("HP", health, healthMax, HgnPalette.DANGER, 0));
			if (style.name().equals("VAMPIRE")) {
				metrics.add(metric("BLOOD", blood, bloodMax, 0xFFD51D38, 2));
			} else {
				metrics.add(metric("FOOD", food, 20L, HgnPalette.WARNING, 2));
			}
		}
		if (xpMax > 0) metrics.add(metric("XP", xp, xpMax, style.primary(), 1));
		// Race identity wins the finite HUD space; generic currencies fill only what remains.
		if (style.name().equals("VAMPIRE")) {
			addMetric(hud, "BLOOD BANK", "bloodBank", "bloodBankMax", 0xFFD51D38, 2);
		} else if (style.name().equals("DALKI")) {
			addMetric(hud, "DALKI POWER", "dp", "dpMax", 0xFF70D6FF, 3);
		} else if (style.name().equals("DEMON")) {
			addUnboundedMetric(hud, "MIASMA", "miasma", 0xFFB0142B, 3);
		}
		addMetric(hud, "QI", "qi", "qiMax", style.secondary(), 3);
		addMetric(hud, "MC POINTS", "mc", "mcMax", 0xFFF0B640, 4);
		if (!style.name().equals("VAMPIRE") && !style.name().equals("DEMON")) {
			addMetric(hud, "BLOOD BANK", "bloodBank", "bloodBankMax", 0xFFD51D38, 2);
		}
		if (!style.name().equals("DALKI")) {
			addMetric(hud, "DALKI POWER", "dp", "dpMax", 0xFF70D6FF, 3);
		}
		if (!style.name().equals("DEMON")) {
			addUnboundedMetric(hud, "MIASMA", "miasma", 0xFFB0142B, 3);
		}
	}

	private static void addMetric(JsonObject hud, String label, String currentKey,
		String maximumKey, int color, int module) {
		long maximum = number(hud, maximumKey);
		if (maximum > 0 && metrics.size() < MAX_BARS) {
			metrics.add(metric(label, number(hud, currentKey), maximum, color, module));
		}
	}

	private static void addUnboundedMetric(JsonObject hud, String label,
		String currentKey, int color, int module) {
		long current = number(hud, currentKey);
		if (current > 0 && metrics.size() < MAX_BARS) {
			metrics.add(new Metric(label + "  //  " + abbreviate(current),
				current, 0L, color, module));
		}
	}

	private static Metric metric(String label, long current, long maximum, int color, int module) {
		return new Metric(label + "  //  " + abbreviate(current) + " / " + abbreviate(maximum),
			current, maximum, color, module);
	}

	private static RaceStyle styleFor(String name) {
		return switch (name) {
			case "VAMPIRE" -> VAMPIRE;
			case "WEREWOLF" -> WEREWOLF;
			case "DRAGON" -> DRAGON;
			case "DEMON" -> DEMON;
			case "DALKI" -> DALKI;
			default -> HUMAN;
		};
	}

	private static GameType mode(Minecraft minecraft) {
		return minecraft.gameMode == null ? GameType.SURVIVAL : minecraft.gameMode.getPlayerMode();
	}

	private static float progress(long current, long maximum) {
		if (maximum <= 0) return 0.0f;
		return (float) Math.max(0.0, Math.min(1.0, current / (double) maximum));
	}

	static void drawCenteredLane(GuiGraphics graphics, Font font, String text,
		int x, int y, int width, int height, int color) {
		int safeWidth = Math.max(1, width);
		int rawWidth = Math.max(1, font.width(text));
		if (rawWidth <= safeWidth) {
			int baseline = y + Math.max(0, (height - font.lineHeight + 1) / 2);
			HgnDraw.centered(graphics, font, text, x + width / 2, baseline, color);
			return;
		}

		// Preserve complete rank, HP and resource readouts whenever a modest text fit
		// is enough. Scaling around the lane's real center also keeps every module
		// visually centered instead of drifting when the left label grows.
		float textScale = Math.max(MIN_TEXT_FIT_SCALE, safeWidth / (float) rawWidth);
		int fitWidth = Math.max(1, (int) Math.floor(safeWidth / textScale));
		String visible = trim(font, text, fitWidth);
		float textHeight = font.lineHeight * textScale;
		float baseline = y + Math.max(0.0f, (height - textHeight) / 2.0f);
		HgnDraw.scaledCentered(graphics, font, visible,
			x + width / 2, Math.round(baseline), textScale, color);
	}

	private static String trim(Font font, String text, int width) {
		if (font.width(text) <= width) return text;
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
	}

	private static boolean has(JsonObject object, String key) {
		return object != null && object.has(key) && !object.get(key).isJsonNull();
	}

	private static String str(JsonObject object, String key, String fallback) {
		try {
			return has(object, key) ? object.get(key).getAsString() : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String strFirst(JsonObject object, String... keys) {
		for (String key : keys) {
			String value = str(object, key, "").trim();
			if (!value.isBlank()) return value;
		}
		return "";
	}

	private static long number(JsonObject object, String key) {
		try {
			return has(object, key) ? object.get(key).getAsLong() : 0L;
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static long numberFirst(JsonObject object, String... keys) {
		for (String key : keys) {
			long value = number(object, key);
			if (value > 0) return value;
		}
		return 0L;
	}

	private static Long numberPresent(JsonObject object, String... keys) {
		for (String key : keys) {
			try {
				if (has(object, key)) return object.get(key).getAsLong();
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static boolean boolFirst(JsonObject object, String... keys) {
		for (String key : keys) {
			try {
				if (has(object, key) && object.get(key).getAsBoolean()) return true;
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	private static int color(JsonObject object, String key, int fallback) {
		try {
			String value = object.get(key).getAsString();
			value = value.startsWith("#") ? value.substring(1) : value;
			return (int) (0xFF000000L | Long.parseLong(value, 16));
		} catch (Exception ignored) {
			return fallback;
		}
	}

	static String abbreviate(long raw) {
		long value = Math.max(0L, raw);
		if (value < 1_000L) return Long.toString(value);
		if (value < 1_000_000L) return compact(value, 1_000L, "K");
		if (value < 1_000_000_000L) return compact(value, 1_000_000L, "M");
		if (value < 1_000_000_000_000L) return compact(value, 1_000_000_000L, "B");
		if (value < 1_000_000_000_000_000L) {
			return compact(value, 1_000_000_000_000L, "T");
		}
		return compact(value, 1_000_000_000_000_000L, "Q");
	}

	private static String compact(long value, long divisor, String suffix) {
		long whole = value / divisor;
		long hundredths = ((value % divisor) * 100L + divisor / 2L) / divisor;
		if (hundredths == 100L) {
			whole++;
			hundredths = 0L;
		}
		if (hundredths == 0L) return whole + suffix;
		if (hundredths % 10L == 0L) return whole + "." + hundredths / 10L + suffix;
		return whole + "." + (hundredths < 10L ? "0" : "") + hundredths + suffix;
	}
}
