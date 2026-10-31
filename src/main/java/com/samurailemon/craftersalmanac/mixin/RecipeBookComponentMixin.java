package com.samurailemon.craftersalmanac.mixin;

import com.samurailemon.craftersalmanac.gui.ViewerPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screens with a vanilla recipe book recompute their position here after init and whenever the book is toggled,
 * which would undo {@link AbstractContainerScreenMixin}. While the vanilla book is open our panel is hidden, so we
 * leave its layout alone.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
	@Shadow
	public abstract boolean isVisible();

	@Inject(method = "updateScreenPosition", at = @At("RETURN"), cancellable = true)
	private void rbv$makeRoomForPanel(int width, int imageWidth, CallbackInfoReturnable<Integer> cir) {
		if (isVisible()) return;
		int leftPos = cir.getReturnValue();
		// Called during the screen's init, after Minecraft has already made it the current screen.
		cir.setReturnValue(leftPos + ViewerPanel.containerOffset(Minecraft.getInstance().screen, width, leftPos, imageWidth));
	}
}
