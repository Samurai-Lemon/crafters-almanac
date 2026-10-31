package com.samurailemon.craftersalmanac.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int rbv$getLeftPos();

	@Accessor("topPos")
	int rbv$getTopPos();

	@Accessor("imageWidth")
	int rbv$getImageWidth();

	@Accessor("imageHeight")
	int rbv$getImageHeight();

	@Accessor("hoveredSlot")
	Slot rbv$getHoveredSlot();

	@Accessor("titleLabelX")
	int rbv$getTitleLabelX();

	@Accessor("titleLabelY")
	int rbv$getTitleLabelY();
}
