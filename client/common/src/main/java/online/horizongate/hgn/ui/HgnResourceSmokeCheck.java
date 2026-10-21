package online.horizongate.hgn.ui;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnFontManagerSetsAccessor;
import online.horizongate.hgn.mixin.HgnFontSetProvidersAccessor;
import online.horizongate.hgn.mixin.HgnMinecraftFontManagerAccessor;

/** Runtime resource and font resolution check, not a rendering acceptance test. */
public final class HgnResourceSmokeCheck {
	private HgnResourceSmokeCheck() {
	}

	public static void run() {
		Minecraft minecraft = Minecraft.getInstance();
		var resources = minecraft.getResourceManager();
		for (String path : new String[] {
			"textures/gui/dashboard_welcome_city.png",
			HgnSurface.GATEWAY_SCENE,
			"textures/gui/hgn_gateway_twilight.png",
			"textures/gui/servers/server-hub.png",
			"textures/gui/theme_v2/text_field.png"
		}) {
			var resource = resources.getResource(HgnCompat.id("hgn-client", path))
				.orElseThrow(() -> new AssertionError("Missing loaded HGN texture: " + path));
			try (var input = resource.open(); var image = NativeImage.read(input)) {
				if (image.getWidth() <= 0 || image.getHeight() <= 0) {
					throw new AssertionError("Empty HGN texture: " + path);
				}
				if (path.endsWith("server-hub.png")
					&& (image.getWidth() != 1254 || image.getHeight() != 1254)) {
					throw new AssertionError("Unexpected HGN Hub texture dimensions: "
						+ image.getWidth() + "x" + image.getHeight());
				}
				if (path.equals(HgnSurface.GATEWAY_SCENE)
					&& (image.getWidth() != 1672 || image.getHeight() != 941)) {
					throw new AssertionError("Unexpected HGN gateway scene dimensions: "
						+ image.getWidth() + "x" + image.getHeight());
				}
			} catch (java.io.IOException exception) {
				throw new AssertionError("Cannot decode HGN texture: " + path, exception);
			}
		}
		var font = resources.getResource(HgnCompat.id("hgn-client", "font/inter_regular.ttf"))
			.orElseThrow(() -> new AssertionError("Missing loaded HGN font"));
		try (var input = new java.io.DataInputStream(font.open())) {
			if (input.readInt() != 0x00010000) {
				throw new AssertionError("Invalid HGN TrueType font signature");
			}
		} catch (java.io.IOException exception) {
			throw new AssertionError("Cannot read HGN font", exception);
		}
		verifyUiFont(minecraft);
		HgnClient.LOGGER.info("HGN_RESOURCE_SMOKE_OK");
	}

	private static void verifyUiFont(Minecraft minecraft) {
		var manager = ((HgnMinecraftFontManagerAccessor) minecraft).hgn$fontManager();
		var fontId = HgnCompat.id("hgn-client", "ui");
		var fontSet = ((HgnFontManagerSetsAccessor) manager).hgn$fontSets().get(fontId);
		if (fontSet == null) throw new AssertionError("Minecraft did not register the HGN UI font");
		var providers = ((HgnFontSetProvidersAccessor) fontSet).hgn$activeProviders();
		for (int codepoint : "HGN012a".codePoints().toArray()) {
			GlyphProvider selected = null;
			for (GlyphProvider provider : providers) {
				if (provider.getGlyph(codepoint) != null) {
					selected = provider;
					break;
				}
			}
			if (!(selected instanceof TrueTypeGlyphProvider)) {
				throw new AssertionError("HGN UI font did not resolve TrueType glyph " + codepoint);
			}
		}
		if (minecraft.font.width(HgnCompat.uiText("HGN012a")) <= 0) {
			throw new AssertionError("Minecraft cannot measure text through the HGN UI font");
		}
		HgnClient.LOGGER.info("HGN_FONT_SMOKE_OK: registered HGN UI font resolved representative Latin glyphs");
	}
}
