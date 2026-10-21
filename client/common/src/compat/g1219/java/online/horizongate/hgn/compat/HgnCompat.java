package online.horizongate.hgn.compat;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import online.horizongate.hgn.screen.HgnJoin;
import org.joml.Vector3f;

import java.util.function.Supplier;

/** Non-drawing vanilla entry points for Minecraft 1.21.9-1.21.10. */
public final class HgnCompat {
	private static final FontDescription UI_FONT = new FontDescription.Resource(
		ResourceLocation.fromNamespaceAndPath("hgn-client", "ui"));
	private static final FontDescription HEADING_FONT = new FontDescription.Resource(
		ResourceLocation.fromNamespaceAndPath("hgn-client", "heading"));

	private HgnCompat() {
	}

	public static void openFreshWorld(Minecraft minecraft, Screen parent) {
		CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(parent));
	}

	public static String biomeName(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) return "Unknown";
		return minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey().map(key -> {
			var id = key.location();
			return Component.translatableWithFallback("biome." + id.getNamespace() + "."
				+ id.getPath(), id.getPath().replace('_', ' ')).getString();
		}).orElse("Unknown");
	}

	public static Component uiText(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(UI_FONT));
	}

	public static Component headingText(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(HEADING_FONT));
	}

	public static void styleSearchField(net.minecraft.client.gui.components.EditBox field) {
		field.addFormatter((value, offset) -> uiText(value).getVisualOrderText());
	}

	public static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	public static long millis() {
		return Util.getMillis();
	}

	public static void openUrl(String url) {
		Util.getPlatform().openUri(url);
	}

	public static void leftAlignLanguageLabel(net.minecraft.client.gui.components.StringWidget label) {
		// Native StringWidget paints from its x position starting in 1.21.9.
	}

	public static Screen languageScreen(Screen parent, Minecraft minecraft) {
		return new LanguageSelectScreen(parent, minecraft.options, minecraft.getLanguageManager());
	}

	public static AbstractWidget profileWidget(Minecraft minecraft, int x, int y, int width, int height) {
		PlayerSkinWidget widget = new PlayerSkinWidget(width, height, minecraft.getEntityModels(),
			resilientSkin(minecraft));
		widget.setPosition(x, y);
		return widget;
	}

	public static AbstractWidget heroWidget(Minecraft minecraft, int x, int y, int width, int height) {
		HgnCosmeticPreviewWidget widget = new HgnCosmeticPreviewWidget(width, height,
			minecraft.getEntityModels(), resilientSkin(minecraft), true);
		widget.setPosition(x, y);
		widget.active = false;
		return widget;
	}

	public static AbstractWidget cosmeticProfileWidget(Minecraft minecraft, int x, int y,
		int width, int height) {
		HgnCosmeticPreviewWidget widget = new HgnCosmeticPreviewWidget(width, height,
			minecraft.getEntityModels(), resilientSkin(minecraft));
		widget.setPosition(x, y);
		return widget;
	}

	private static Supplier<PlayerSkin> resilientSkin(Minecraft minecraft) {
		PlayerSkin fallback = DefaultPlayerSkin.get(minecraft.getGameProfile());
		Supplier<PlayerSkin> remote = minecraft.getSkinManager()
			.createLookup(minecraft.getGameProfile(), true);
		return () -> {
			try {
				PlayerSkin skin = minecraft.player == null ? remote.get() : minecraft.player.getSkin();
				return skin == null ? fallback : skin;
			} catch (RuntimeException unavailable) {
				return fallback;
			}
		};
	}

	public static void quit(Minecraft minecraft) {
		minecraft.stop();
	}

	/** Full vanilla disconnect path; caller must verify captured server shutdown afterward. */
	public static void disconnectForUpdate(Minecraft mc) {
		boolean local = mc.getSingleplayerServer() != null;
		if (mc.level != null) mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		if (local) mc.disconnectWithSavingScreen();
		else mc.disconnectWithProgressScreen();
	}

	public static void initScreen(Screen screen, int width, int height) {
		screen.init(Minecraft.getInstance(), width, height);
	}

	public static boolean isNativePlainButton(AbstractWidget widget) {
		return widget != null && widget.getClass() == Button.class;
	}

	public static void narrateSystemNow(Component message) {
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	public static double keyBindsScroll(net.minecraft.client.gui.screens.options.controls.KeyBindsList list) {
		return list.scrollAmount();
	}

	public static void resizeScreen(Screen screen, int width, int height) {
		screen.resize(Minecraft.getInstance(), width, height);
	}

	public static net.minecraft.world.entity.player.Inventory smokeInventory() {
		return HgnSmokeInventory.create();
	}

	public static int selectedHotbarSlot() {
		return Minecraft.getInstance().player.getInventory().getSelectedSlot();
	}

	public static void connect(Screen parent, Minecraft minecraft, String name, String address) {
		ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
		connect(parent, minecraft, data);
	}

	public static void connect(Screen parent, Minecraft minecraft, ServerData data) {
		HgnJoin.connecting(data.name, data.ip, minecraft.level != null);
		ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	public static void pingServer(ServerStatusPinger pinger, ServerData data, Runnable onSuccess, Runnable onFailure) {
		try {
			pinger.pingServer(data, onSuccess, onFailure);
		} catch (Exception ignored) {
			onFailure.run();
		}
	}

	public static byte[] readAsset(String path) {
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(id("hgn-client", path));
			if (resource.isEmpty()) {
				return null;
			}
			try (var input = resource.get().open()) {
				return input.readAllBytes();
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	public static double[] cameraBasis() {
		double[] basis = new double[12];
		cameraBasis(basis);
		return basis;
	}

	/** Writes the camera basis into a reusable buffer to avoid render-frame allocations. */
	public static void cameraBasis(double[] basis) {
		if (basis == null || basis.length < 12) return;
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		Vec3 position = camera.getPosition();
		Vector3f forward = camera.getLookVector();
		Vector3f up = camera.getUpVector();
		Vector3f left = camera.getLeftVector();
		basis[0] = position.x; basis[1] = position.y; basis[2] = position.z;
		basis[3] = forward.x(); basis[4] = forward.y(); basis[5] = forward.z();
		basis[6] = up.x(); basis[7] = up.y(); basis[8] = up.z();
		basis[9] = left.x(); basis[10] = left.y(); basis[11] = left.z();
	}

	public static Screen advancementsScreen(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		return new AdvancementsScreen(minecraft.player.connection.getAdvancements(), parent);
	}

	public static Screen statsScreen(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		return new StatsScreen(parent, minecraft.player.getStats());
	}

	public static void leaveToTitle() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.hasSingleplayerServer()) {
			minecraft.disconnectWithSavingScreen();
		} else {
			minecraft.disconnectWithProgressScreen();
		}
	}
}
