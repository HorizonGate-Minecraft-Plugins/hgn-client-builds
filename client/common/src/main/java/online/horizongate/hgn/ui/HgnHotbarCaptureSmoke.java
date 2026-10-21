package online.horizongate.hgn.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.HgnConfig;
import online.horizongate.hgn.screen.HgnDashboardScreen;
import online.horizongate.hgn.screen.HgnSelectWorldScreen;

/** Isolated production-jar world fixture and render-path markers for hosted CI screenshots. */
public final class HgnHotbarCaptureSmoke {
	private static final String COMMAND_FILE = "hotbar-capture-command.txt";
	private static final Set<String> REQUESTS = Set.of("native", "hgn", "unsupported", "creative", "spectator");
	private static final AtomicInteger customFrames = new AtomicInteger();
	private static final AtomicInteger nativeFrames = new AtomicInteger();
	private static boolean isolated;
	private static boolean worldRequested;
	private static boolean creationOpened;
	private static boolean worldSubmitted;
	private static boolean fixtureLogged;
	private static boolean failed;
	private static volatile boolean equipQueued;
	private static volatile boolean serverEquipped;
	private static volatile String serverFailure = "";
	private static String activeRequest = "";
	private static int titleTicks;
	private static int createTicks;
	private static int joinTicks;
	private static int equipTicks;
	private static int fixtureTicks;
	private static int stableTicks;
	private static int customBaseline;
	private static int nativeBaseline;
	private static boolean requestReady;
	private static boolean modeQueued;

	private HgnHotbarCaptureSmoke() {
	}

	private static boolean enabled() {
		return HgnClient.DEV_SCREEN.equals("hotbar-capture")
			&& Boolean.getBoolean("hgn.smokeHotbarCapture")
			&& "true".equals(System.getenv("GITHUB_ACTIONS"));
	}

	public static void customRendered() {
		if (enabled()) customFrames.incrementAndGet();
	}

	public static void nativeRendered() {
		if (enabled()) nativeFrames.incrementAndGet();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled() || failed) return;
		try {
			if (!isolated) {
				if (!(minecraft.screen instanceof HgnDashboardScreen)) return;
				checkIsolation(minecraft);
				isolated = true;
			}
			if (minecraft.level == null) {
				createWorld(minecraft);
				return;
			}
			if (!worldSubmitted) throw new IllegalStateException("Unexpected world before CI creation");
			if (minecraft.player == null || minecraft.screen != null) {
				if (++joinTicks > 6000) throw new IllegalStateException("Disposable world join timed out");
				return;
			}
			if (!serverFailure.isEmpty()) throw new IllegalStateException(serverFailure);
			if (!serverEquipped) {
				if (++equipTicks > 6000) throw new IllegalStateException("Disposable player equipment timed out");
				equipPlayer(minecraft);
				return;
			}
			if (!liveItemsReady(minecraft)) {
				if (++fixtureTicks > 1200) throw new IllegalStateException("Server fixture did not reach the client");
				return;
			}
			if (!fixtureLogged) {
				fixtureLogged = true;
				HgnClient.LOGGER.info("HGN_HOTBAR_FIXTURE_READY MC={} SWORD={} TORCH={} PEARL={} OFFHAND_SHIELD={} XP={} COOLDOWN={}",
					HgnClient.minecraftVersion(), minecraft.player.getInventory().getItem(0).getCount(),
					minecraft.player.getInventory().getItem(1).getCount(),
					minecraft.player.getInventory().getItem(2).getCount(),
					minecraft.player.getOffhandItem().is(Items.SHIELD), minecraft.player.experienceLevel,
					cooldownPercent(minecraft.player.getCooldowns(), minecraft.player.getInventory().getItem(2)));
			}
			captureRequest(minecraft);
		} catch (Exception exception) {
			failed = true;
			HgnClient.LOGGER.error("HGN_HOTBAR_CAPTURE_FAILED", exception);
			minecraft.stop();
		}
	}

	private static void checkIsolation(Minecraft minecraft) throws IOException {
		if (minecraft.gameDirectory == null) throw new IOException("No game directory");
		Path run = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
		Path saves = minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize();
		if (run.getFileName() == null || !run.getFileName().toString().equals("run-packaged-hotbar")
			|| !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(run)
			|| !saves.equals(run.resolve("saves")) || Files.isSymbolicLink(saves)) {
			throw new IOException("Hotbar capture requires its isolated packaged run directory");
		}
		if (Files.exists(saves, LinkOption.NOFOLLOW_LINKS)) {
			if (!Files.isDirectory(saves, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid saves directory");
			try (Stream<Path> entries = Files.list(saves)) {
				if (entries.findAny().isPresent()) throw new IOException("Disposable saves directory is not empty");
			}
		}
	}

	private static void createWorld(Minecraft minecraft) throws ReflectiveOperationException {
		if (!worldRequested) {
			if (++titleTicks < 40) return;
			minecraft.setScreen(new HgnSelectWorldScreen(minecraft.screen));
			worldRequested = true;
			HgnClient.LOGGER.info("HGN_HOTBAR_WORLD_SELECTION_OPENED={}", HgnClient.minecraftVersion());
			return;
		}
		if (++createTicks > 6000) throw new IllegalStateException("Disposable world creation timed out");
		if (worldSubmitted || createTicks < 20) return;
		Component createLabel = Component.translatable("selectWorld.create");
		if (!creationOpened && minecraft.screen instanceof HgnSelectWorldScreen selection) {
			if (pressLabeled(selection, createLabel)) {
				creationOpened = true;
				HgnClient.LOGGER.info("HGN_HOTBAR_WORLD_CREATION_OPENED={}", HgnClient.minecraftVersion());
			}
			return;
		}
		if (!(minecraft.screen instanceof CreateWorldScreen creation)) return;
		if (pressLabeled(creation, createLabel)) {
			worldSubmitted = true;
			HgnClient.LOGGER.info("HGN_HOTBAR_WORLD_CREATE_SUBMITTED={}", HgnClient.minecraftVersion());
		}
	}

	private static boolean pressLabeled(Screen screen, Component label) throws ReflectiveOperationException {
		for (var child : screen.children()) {
			if (!(child instanceof Button button) || !button.active
				|| !button.getMessage().getContents().equals(label.getContents())) continue;
			press(button);
			return true;
		}
		return false;
	}

	private static void press(Button button) throws ReflectiveOperationException {
		Field callbackField = null;
		Method callbackMethod = null;
		for (Field field : Button.class.getDeclaredFields()) {
			if (!field.getType().isInterface()) continue;
			for (Method method : field.getType().getMethods()) {
				Class<?>[] parameters = method.getParameterTypes();
				if (!Modifier.isAbstract(method.getModifiers()) || method.getReturnType() != void.class
					|| parameters.length < 1 || parameters.length > 2
					|| !parameters[0].isAssignableFrom(Button.class)) continue;
				if (callbackField != null) throw new NoSuchMethodException("Ambiguous Button press callback");
				callbackField = field;
				callbackMethod = method;
			}
		}
		if (callbackField == null || callbackMethod == null) throw new NoSuchMethodException("Button press callback");
		callbackField.setAccessible(true);
		Object callback = callbackField.get(button);
		if (callback == null) throw new IllegalStateException("Create World button has no callback");
		if (callbackMethod.getParameterCount() == 1) callbackMethod.invoke(callback, button);
		else callbackMethod.invoke(callback, button, null);
	}

	/** Minecraft method names are remapped in the production jar; exact JVM signatures are stable here. */
	private static Method runtimeMethod(Class<?> owner, Class<?> result, boolean staticMethod,
		Class<?>... parameters) throws NoSuchMethodException {
		Method found = null;
		for (Method method : owner.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) != staticMethod
				|| (result != null && method.getReturnType() != result)
				|| !Arrays.equals(method.getParameterTypes(), parameters)) continue;
			if (found != null) throw new NoSuchMethodException("Ambiguous mapped method on " + owner.getName()
				+ " with " + Arrays.toString(parameters));
			found = method;
		}
		if (found == null) throw new NoSuchMethodException("Mapped method on " + owner.getName()
			+ " with " + Arrays.toString(parameters));
		return found;
	}

	private static void equipPlayer(Minecraft minecraft) {
		if (equipQueued) return;
		var server = minecraft.getSingleplayerServer();
		if (server == null) return;
		UUID playerId = minecraft.player.getUUID();
		equipQueued = true;
		server.execute(() -> {
			try {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player == null) {
					equipQueued = false;
					return;
				}
				ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 16);
				player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				player.getInventory().setItem(1, new ItemStack(Items.TORCH, 32));
				player.getInventory().setItem(2, pearls);
				player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				player.giveExperienceLevels(7);
				addCooldown(player.getCooldowns(), pearls);
				serverEquipped = true;
			} catch (Exception exception) {
				serverFailure = exception.toString();
			}
		});
	}

	private static boolean liveItemsReady(Minecraft minecraft) throws ReflectiveOperationException {
		var player = minecraft.player;
		var inventory = player.getInventory();
		return inventory.getItem(0).is(Items.DIAMOND_SWORD) && inventory.getItem(0).getCount() == 1
			&& inventory.getItem(1).is(Items.TORCH) && inventory.getItem(1).getCount() == 32
			&& inventory.getItem(2).is(Items.ENDER_PEARL) && inventory.getItem(2).getCount() == 16
			&& player.getOffhandItem().is(Items.SHIELD) && player.experienceLevel >= 7
			&& cooldownPercent(player.getCooldowns(), inventory.getItem(2)) > 0.0f;
	}

	/** The early 1.21 mappings use Item keys; later targets use ItemStack keys. */
	private static void addCooldown(Object cooldowns, ItemStack stack) throws ReflectiveOperationException {
		try {
			runtimeMethod(cooldowns.getClass(), void.class, false, ItemStack.class, int.class)
				.invoke(cooldowns, stack, 2400);
		} catch (NoSuchMethodException older) {
			runtimeMethod(cooldowns.getClass(), void.class, false, Item.class, int.class)
				.invoke(cooldowns, stack.getItem(), 2400);
		}
	}

	private static float cooldownPercent(Object cooldowns, ItemStack stack) throws ReflectiveOperationException {
		try {
			return (float) runtimeMethod(cooldowns.getClass(), float.class, false, ItemStack.class, float.class)
				.invoke(cooldowns, stack, 0.0f);
		} catch (NoSuchMethodException older) {
			return (float) runtimeMethod(cooldowns.getClass(), float.class, false, Item.class, float.class)
				.invoke(cooldowns, stack.getItem(), 0.0f);
		}
	}

	private static void captureRequest(Minecraft minecraft) throws IOException, ReflectiveOperationException {
		Path command = minecraft.gameDirectory.toPath().resolve(COMMAND_FILE);
		if (!Files.isRegularFile(command, LinkOption.NOFOLLOW_LINKS)) return;
		if (Files.size(command) > 32L) throw new IOException("Hotbar command is too large");
		String request = Files.readString(command, StandardCharsets.US_ASCII).trim();
		if (!REQUESTS.contains(request)) throw new IOException("Unknown hotbar command: " + request);
		if (!request.equals(activeRequest)) {
			activeRequest = request;
			requestReady = false;
			modeQueued = false;
			stableTicks = 0;
			customBaseline = customFrames.get();
			nativeBaseline = nativeFrames.get();
			HgnConfig.get().experimentalHgnHotbar = !request.equals("native");
			minecraft.options.attackIndicator().set(AttackIndicatorStatus.HOTBAR);
		}
		int viewportWidth = minecraft.getWindow().getGuiScaledWidth();
		int viewportHeight = minecraft.getWindow().getGuiScaledHeight();
		if (request.equals("unsupported") ? viewportWidth >= 244 : viewportWidth < 244) {
			stableTicks = 0;
			customBaseline = customFrames.get();
			nativeBaseline = nativeFrames.get();
			return;
		}
		GameType expected = request.equals("creative") ? GameType.CREATIVE
			: request.equals("spectator") ? GameType.SPECTATOR : GameType.SURVIVAL;
		if (!modeQueued) {
			modeQueued = true;
			var server = minecraft.getSingleplayerServer();
			UUID playerId = minecraft.player.getUUID();
			server.execute(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player != null) player.setGameMode(expected);
			});
		}
		if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() != expected) {
			stableTicks = 0;
			customBaseline = customFrames.get();
			nativeBaseline = nativeFrames.get();
			return;
		}
		if (!request.equals("spectator")) minecraft.player.resetAttackStrengthTicker();
		if (requestReady || ++stableTicks < 20) return;
		int custom = customFrames.get() - customBaseline;
		int nativeCount = nativeFrames.get() - nativeBaseline;
		// Software rendering may produce fewer than five frames in twenty ticks. One frame proves the selected path ran.
		if (request.equals("spectator") ? custom != 0 || nativeCount != 0
			: request.equals("native") || request.equals("unsupported") ? nativeCount == 0 || custom != 0
			: custom == 0 || nativeCount != 0) {
			throw new IllegalStateException("Unexpected hotbar render path for " + request
				+ ": custom=" + custom + ", native=" + nativeCount);
		}
		requestReady = true;
		HgnClient.LOGGER.info("HGN_HOTBAR_CAPTURE_READY={} MODE={} OPT_IN={} VIEWPORT={}x{} SWORD={} TORCH={} PEARL={} OFFHAND_SHIELD={} XP={} COOLDOWN={} CUSTOM_FRAMES={} NATIVE_FRAMES={}",
			request, expected, HgnConfig.get().experimentalHgnHotbar, viewportWidth, viewportHeight,
			minecraft.player.getInventory().getItem(0).getCount(),
			minecraft.player.getInventory().getItem(1).getCount(),
			minecraft.player.getInventory().getItem(2).getCount(),
			minecraft.player.getOffhandItem().is(Items.SHIELD), minecraft.player.experienceLevel,
			cooldownPercent(minecraft.player.getCooldowns(), minecraft.player.getInventory().getItem(2)),
			custom, nativeCount);
	}
}
