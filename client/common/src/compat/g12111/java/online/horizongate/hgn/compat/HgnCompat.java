package online.horizongate.hgn.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
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
import net.minecraft.resources.Identifier;
import online.horizongate.hgn.screen.HgnJoin;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

/**
 * Non-drawing vanilla entry points whose names or signatures move between releases.
 * 1.21.11 moved roughly 580 classes: ResourceLocation became Identifier and
 * net.minecraft.Util moved to net.minecraft.util.Util.
 */
public final class HgnCompat {
	private static final FontDescription UI_FONT = new FontDescription.Resource(
		Identifier.fromNamespaceAndPath("hgn-client", "ui"));
	private static final FontDescription HEADING_FONT = new FontDescription.Resource(
		Identifier.fromNamespaceAndPath("hgn-client", "heading"));

	private HgnCompat() {
	}

	public static void openFreshWorld(Minecraft minecraft, Screen parent) {
		CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(parent));
	}

	public static String biomeName(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) return "Unknown";
		return minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey().map(key -> {
			var id = key.identifier();
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

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public static long millis() {
		return Util.getMillis();
	}

	/** Hands a URL to the desktop, which is how the client reaches the launcher. */
	public static void openUrl(String url) {
		Util.getPlatform().openUri(url);
	}

	public static void leftAlignLanguageLabel(net.minecraft.client.gui.components.StringWidget label) {
		// Native StringWidget paints from its x position starting in 1.21.9.
	}

	public static Screen languageScreen(Screen parent, Minecraft mc) {
		return new LanguageSelectScreen(parent, mc.options, mc.getLanguageManager());
	}

	public static AbstractWidget profileWidget(Minecraft mc, int x, int y, int width, int height) {
		PlayerSkinWidget widget = new PlayerSkinWidget(width, height, mc.getEntityModels(),
			resilientSkin(mc));
		widget.setPosition(x, y);
		return widget;
	}

	public static AbstractWidget heroWidget(Minecraft mc, int x, int y, int width, int height) {
		HgnCosmeticPreviewWidget widget = new HgnCosmeticPreviewWidget(width, height,
			mc.getEntityModels(), resilientSkin(mc), true);
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

	public static void quit(Minecraft mc) {
		mc.stop();
	}

	/** Full vanilla disconnect path; caller must verify captured server shutdown afterward. */
	public static void disconnectForUpdate(Minecraft mc) {
		boolean local = mc.getSingleplayerServer() != null;
		if (mc.level != null) mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		if (local) mc.disconnectWithSavingScreen();
		else mc.disconnectWithProgressScreen();
	}

	public static void initScreen(Screen screen, int width, int height) {
		screen.init(width, height);
	}

	public static boolean isNativePlainButton(AbstractWidget widget) {
		return widget != null && widget.getClass() == Button.Plain.class;
	}

	public static void narrateSystemNow(Component message) {
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	public static double keyBindsScroll(net.minecraft.client.gui.screens.options.controls.KeyBindsList list) {
		return list.scrollAmount();
	}

	public static void resizeScreen(Screen screen, int width, int height) {
		screen.resize(width, height);
	}

	public static net.minecraft.world.entity.player.Inventory smokeInventory() {
		return HgnSmokeInventory.create();
	}

	public static int selectedHotbarSlot() {
		return Minecraft.getInstance().player.getInventory().getSelectedSlot();
	}

	public static void connect(Screen parent, Minecraft mc, String name, String address) {
		ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
		connect(parent, mc, data);
	}

	public static void connect(Screen parent, Minecraft mc, ServerData data) {
		HgnJoin.connecting(data.name, data.ip, mc.level != null);
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(data.ip), data, false, null);
	}

	/**
	 * Starts an asynchronous ping. 1.21.11 requires the caller to supply the event
	 * loop the ping runs on, where earlier versions chose one themselves.
	 */
	public static void pingServer(ServerStatusPinger pinger, ServerData data,
		Runnable onSuccess, Runnable onFailure) {
		try {
			pinger.pingServer(data, onSuccess, onFailure, EventLoopGroupHolder.remote(false));
		} catch (Exception e) {
			onFailure.run();
		}
	}


	/** Raw bytes of one of the client's own assets, or null if it is missing. */
	public static byte[] readAsset(String path) {
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(id("hgn-client", path));
			if (res.isEmpty()) {
				return null;
			}
			try (var in = res.get().open()) {
				return in.readAllBytes();
			}
		} catch (Exception e) {
			return null;
		}
	}

	/** Camera basis for projecting world points to the screen: pos, forward, up, left. */
	public static double[] cameraBasis() {
		double[] basis = new double[12];
		cameraBasis(basis);
		return basis;
	}

	/** Writes the camera basis into a reusable buffer to avoid render-frame allocations. */
	public static void cameraBasis(double[] basis) {
		if (basis == null || basis.length < 12) return;
		Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
		Vec3 p = c.position();
		Vector3fc f = c.forwardVector();
		Vector3fc u = c.upVector();
		Vector3fc l = c.leftVector();
		basis[0] = p.x; basis[1] = p.y; basis[2] = p.z;
		basis[3] = f.x(); basis[4] = f.y(); basis[5] = f.z();
		basis[6] = u.x(); basis[7] = u.y(); basis[8] = u.z();
		basis[9] = l.x(); basis[10] = l.y(); basis[11] = l.z();
	}

	/** The vanilla advancements screen, returning to {@code parent} when closed. */
	public static Screen advancementsScreen(Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		return new AdvancementsScreen(mc.player.connection.getAdvancements(), parent);
	}

	/** The vanilla statistics screen. */
	public static Screen statsScreen(Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		return new StatsScreen(parent, mc.player.getStats());
	}

	/** Leaves the current world/server; the title-screen redirect makes it the HGN menu. */
	public static void leaveToTitle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.hasSingleplayerServer()) {
			mc.disconnectWithSavingScreen();
		} else {
			mc.disconnectWithProgressScreen();
		}
	}
}
