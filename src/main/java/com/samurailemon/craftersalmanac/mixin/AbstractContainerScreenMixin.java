package com.samurailemon.craftersalmanac.mixin;

import com.samurailemon.craftersalmanac.gui.SearchHighlight;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fades out items that don't match the search. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	/**
	 * Right after the slots are drawn, while the pose is still at the container's corner. Optional (require = 0):
	 * inventory mods sometimes redirect this call, and then the highlight is skipped instead of the game crashing.
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"), require = 0)
	private void rbv$fadeSearchMisses(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		SearchHighlight.render(g, (AbstractContainerScreen<?>) (Object) this);
	}
}
