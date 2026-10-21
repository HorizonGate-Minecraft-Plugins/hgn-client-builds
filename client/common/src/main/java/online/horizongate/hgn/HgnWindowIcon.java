package online.horizongate.hgn;

import online.horizongate.hgn.compat.HgnCompat;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the game window's icon with the HGN cube.
 *
 * <p>Minecraft loads its window icon from the vanilla jar at bootstrap, before
 * any mod resources exist, so overriding the resource does nothing. Setting it
 * through GLFW at runtime does, and — unlike {@code Window.setIcon}, whose
 * signature moves between releases — the GLFW call is identical on every version.
 */
public final class HgnWindowIcon {
	private static final String[] SIZES = { "icon_16.png", "icon_32.png", "icon_48.png" };

	private static boolean applied;

	private HgnWindowIcon() {
	}

	public static void apply() {
		// Called both at start-up and from the title screen: the first attempt runs
		// before resources finish loading, so the icon files are not there yet. Only
		// mark it done once it actually succeeds, so a later call retries.
		if (applied) {
			return;
		}
		try {
			// The GLFW window whose context is current on the render thread is the
			// game window. Reading it this way avoids Window's handle accessor, which
			// was renamed mid-generation (getWindow() → handle() at 1.21.9).
			long handle = GLFW.glfwGetCurrentContext();
			if (handle == 0L) {
				return;
			}
			List<ByteBuffer> decoded = new ArrayList<>();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				GLFWImage.Buffer icons = GLFWImage.malloc(SIZES.length, stack);
				int count = 0;
				for (String name : SIZES) {
					byte[] bytes = HgnCompat.readAsset(name);
					if (bytes == null) {
						continue;
					}
					ByteBuffer png = MemoryUtil.memAlloc(bytes.length);
					png.put(bytes).flip();
					IntBuffer w = stack.mallocInt(1);
					IntBuffer h = stack.mallocInt(1);
					IntBuffer channels = stack.mallocInt(1);
					ByteBuffer pixels = STBImage.stbi_load_from_memory(png, w, h, channels, 4);
					MemoryUtil.memFree(png);
					if (pixels == null) {
						continue;
					}
					decoded.add(pixels);
					icons.position(count).width(w.get(0)).height(h.get(0)).pixels(pixels);
					count++;
				}
				if (count > 0) {
					icons.position(0);
					icons.limit(count);
					GLFW.glfwSetWindowIcon(handle, icons);
					applied = true;
				}
			} finally {
				for (ByteBuffer pixels : decoded) {
					STBImage.stbi_image_free(pixels);
				}
			}
		} catch (Throwable t) {
			HgnClient.LOGGER.warn("Could not set the HGN window icon", t);
		}
	}
}
