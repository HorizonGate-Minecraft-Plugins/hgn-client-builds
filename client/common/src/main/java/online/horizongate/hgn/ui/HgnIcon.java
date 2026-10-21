package online.horizongate.hgn.ui;

import net.minecraft.client.gui.GuiGraphics;
import online.horizongate.hgn.compat.HgnDraw;

/** Individually generated HGN raster assets shared by menus and catalogue controls. */
public enum HgnIcon {
	PLAY("play"),
	WORLD("world"),
	MULTIPLAYER("multiplayer"),
	SERVERS("servers"),
	SETTINGS("settings"),
	LANGUAGE("language"),
	ACCESSIBILITY("accessibility"),
	COSMETICS("cosmetics"),
	EXIT("exit"),
	BACK("back"),
	SEARCH("search"),
	PROFILE("profile"),
	WALLET("wallet"),
	PERFORMANCE("performance"),
	ANTI_CHEAT("anti_cheat"),
	HUD("hud"),
	MODS("mods"),
	SKINS("skins"),
	HATS("hats"),
	CAPES("capes"),
	WINGS("wings"),
	BACK_ITEMS("back_items"),
	EFFECTS("effects"),
	EMOTES("emotes"),
	PETS("pets"),
	BUNDLES("bundles"),
	HGN_GOLDEN_TOKENS("account_credits"),
	LINK("link"),
	HGN_TOKENS("cosmetic_coins"),
	GATEWAY("gateway"),
	PAGE_LEFT("textures/gui/chrome/cosmetic_page_left.png", true),
	PAGE_RIGHT("textures/gui/chrome/cosmetic_page_right.png", true),
	NONE(null);

	private static final int ICON_TEXTURE_SIZE = 128;
	private static final String DIRECTORY = "textures/gui/icons/";
	private final String path;
	private final int textureSize;

	HgnIcon(String name) {
		path = name == null ? null : DIRECTORY + name + ".png";
		textureSize = ICON_TEXTURE_SIZE;
	}

	HgnIcon(String path, boolean direct) {
		this.path = path;
		textureSize = 256;
	}

	public void draw(GuiGraphics graphics, int x, int y, int size, int color) {
		if (path == null || size < 4) return;
		HgnDraw.textureStretch(graphics, path, x, y, size, size, textureSize, textureSize);
	}
}
