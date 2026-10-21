package online.horizongate.hgn.compat;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;

/** Numeric enchantment clue lookup for Minecraft 1.21.2-1.21.5. */
public final class HgnEnchantCompat {
	private HgnEnchantCompat() {
	}

	public static String name(int clue, int level) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) return "ENCHANTMENT";
		try {
			return minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.get(clue)
				.map(holder -> Enchantment.getFullname(holder, level).getString()
					.toUpperCase(Locale.ROOT))
				.orElse("ENCHANTMENT");
		} catch (RuntimeException ignored) {
			return "ENCHANTMENT";
		}
	}
}
