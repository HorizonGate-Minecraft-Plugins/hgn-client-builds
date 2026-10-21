package online.horizongate.hgn.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Version-stable access to the container geometry used by drawing and hit testing. */
@Mixin(AbstractContainerScreen.class)
public interface HgnContainerAccessor {
	@Accessor("imageWidth")
	int hgn$getImageWidth();

	@Accessor("imageWidth")
	void hgn$setImageWidth(int value);

	@Accessor("imageHeight")
	int hgn$getImageHeight();

	@Accessor("imageHeight")
	void hgn$setImageHeight(int value);

	@Accessor("leftPos")
	int hgn$getLeftPos();

	@Accessor("leftPos")
	void hgn$setLeftPos(int value);

	@Accessor("topPos")
	int hgn$getTopPos();

	@Accessor("topPos")
	void hgn$setTopPos(int value);

	@Accessor("titleLabelX")
	void hgn$setTitleLabelX(int value);

	@Accessor("titleLabelY")
	void hgn$setTitleLabelY(int value);

	@Accessor("inventoryLabelX")
	void hgn$setInventoryLabelX(int value);

	@Accessor("inventoryLabelY")
	void hgn$setInventoryLabelY(int value);

	@Accessor("menu")
	AbstractContainerMenu hgn$getMenu();

	@Accessor("hoveredSlot")
	Slot hgn$getHoveredSlot();

	@Invoker("renderTooltip")
	void hgn$renderTooltip(GuiGraphics graphics, int mouseX, int mouseY);
}
