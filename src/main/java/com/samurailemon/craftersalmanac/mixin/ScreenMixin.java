package com.samurailemon.craftersalmanac.mixin;

import com.samurailemon.craftersalmanac.gui.ScreenShift;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a container screen shifted right to make room for the almanac (see ScreenShift). */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	/** Vanilla's darkened background behind in-game screens (Screen.renderTransparentBackground). */
	private static final int BACKGROUND_TOP = 0xC0101010;
	private static final int BACKGROUND_BOTTOM = 0xD0101010;

	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "renderWithTooltip", at = @At("HEAD"))
	private void rbv$startShift(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		// Start clean even if a previous frame's drawing failed partway and never reached RETURN.
		ScreenShift.drawing = 0;
		int shift = ScreenShift.of((Screen) (Object) this);
		if (shift == 0) return;
		// The screen's own background is drawn shifted too; darken the strip it leaves on the left.
		g.fillGradient(0, 0, shift, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
		g.pose().pushPose();
		g.pose().translate(shift, 0, 0);
		ScreenShift.drawing = shift;
	}

	/** The screen sees the mouse where it would be without the shift, so hovering lines up. */
	@ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int rbv$shiftMouse(int mouseX) {
		return mouseX - ScreenShift.of((Screen) (Object) this);
	}

	@Inject(method = "renderWithTooltip", at = @At("RETURN"))
	private void rbv$endShift(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (ScreenShift.drawing == 0) return;
		g.pose().popPose();
		ScreenShift.drawing = 0;
	}
}
