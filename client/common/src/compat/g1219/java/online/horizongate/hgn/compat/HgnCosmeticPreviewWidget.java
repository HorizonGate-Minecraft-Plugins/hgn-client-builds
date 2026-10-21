package online.horizongate.hgn.compat;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.entity.player.PlayerSkin;
import online.horizongate.hgn.cosmetics.HgnCapeRenderer;
import online.horizongate.hgn.cosmetics.HgnCosmeticModels;
import online.horizongate.hgn.cosmetics.HgnCosmetics;

/** Draggable authenticated-skin preview with every equipped private model visible. */
final class HgnCosmeticPreviewWidget extends PlayerSkinWidget {
	private final boolean equippedOnly;

	HgnCosmeticPreviewWidget(int width, int height, EntityModelSet models,
		Supplier<PlayerSkin> skin) {
		this(width, height, models, skin, false);
	}

	HgnCosmeticPreviewWidget(int width, int height, EntityModelSet models,
		Supplier<PlayerSkin> skin, boolean equippedOnly) {
		super(width, height, models, skin);
		this.equippedOnly = equippedOnly;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int centerX = getX() + getWidth() / 2;
		int capeWidth = Math.max(8, Math.min(18, getWidth() / 4));
		int capeHeight = Math.max(16, Math.min(36, getHeight() / 2));
		for (HgnCosmetics.EquippedDefinition equipped : equippedOnly
			? HgnCosmetics.equippedPreviewRenderPlan() : HgnCosmetics.previewRenderPlan()) {
			if (HgnCapeRenderer.isCape(equipped.definition())) {
				HgnCapeRenderer.renderGui(graphics, equipped.definition(),
					centerX - capeWidth / 2, getY() + getHeight() / 3,
					capeWidth, capeHeight);
			}
		}

		super.renderWidget(graphics, mouseX, mouseY, partialTick);

		for (HgnCosmetics.EquippedDefinition equipped : equippedOnly
			? HgnCosmetics.equippedPreviewRenderPlan() : HgnCosmetics.previewRenderPlan()) {
			HgnCosmetics.ItemDefinition definition = equipped.definition();
			if (HgnCapeRenderer.isCape(definition)) continue;
			String slot = definition.equipmentSlot();
			int size = Math.max(12, Math.min(20, getWidth() / 4));
			int x = centerX - size / 2;
			int y = getY() + getHeight() / 2 - size / 2;
			if (slot.equalsIgnoreCase("HEAD") || slot.equalsIgnoreCase("HAT")
				|| slot.equalsIgnoreCase("HEAD_EFFECT")) {
				y = getY() + Math.max(1, getHeight() / 7);
			} else if (slot.equalsIgnoreCase("OFFHAND")) {
				x = centerX - size - Math.max(2, getWidth() / 8);
				y = getY() + getHeight() / 2;
			} else {
				x = centerX + Math.max(1, getWidth() / 10) - size / 2;
			}
			HgnCosmeticModels.renderGui(graphics, definition, x, y, size);
		}
	}
}
