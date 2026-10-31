package com.samurailemon.craftersalmanac.mixin;

import com.samurailemon.craftersalmanac.gui.ScreenShift;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps clipping and tooltips right while a screen is drawn shifted (see ScreenShift). Clipping areas are in screen
 * coordinates and ignore the drawing shift, so they're moved along with it (the inventory's player model, a
 * villager's trade list). Tooltips are kept from running off the real right edge of the screen.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
	@ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int rbv$shiftScissorLeft(int left) {
		return left + ScreenShift.drawing;
	}

	@ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int rbv$shiftScissorRight(int right) {
		return right + ScreenShift.drawing;
	}

	@ModifyArg(method = "renderTooltipInternal", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"), index = 0)
	private int rbv$tooltipScreenWidth(int screenWidth) {
		return screenWidth - ScreenShift.drawing;
	}
}
