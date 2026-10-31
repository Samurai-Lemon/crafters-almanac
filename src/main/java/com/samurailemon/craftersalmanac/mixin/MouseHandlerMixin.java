package com.samurailemon.craftersalmanac.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.samurailemon.craftersalmanac.gui.ScreenShift;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shifts the mouse position handed to a shifted screen (see ScreenShift), for clicks and releases, scrolling, and
 * movement and dragging. Each of these reads the raw mouse x once, only to work out the screen position.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyExpressionValue(method = {"onPress", "onScroll", "handleAccumulatedMovement"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D"))
	private double rbv$shiftMouse(double xpos) {
		int shift = ScreenShift.of(minecraft.screen);
		if (shift == 0) return xpos;
		// The shift is in GUI pixels; the raw position is in window pixels.
		return xpos - (double) shift * minecraft.getWindow().getScreenWidth() / minecraft.getWindow().getGuiScaledWidth();
	}
}
