package com.samurailemon.recipebookviewer.mixin;

import com.samurailemon.recipebookviewer.gui.SearchHighlight;
import com.samurailemon.recipebookviewer.gui.ViewerPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves containers right to make room for the panel, and fades out items that don't match the search. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int imageWidth;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	/** Runs before subclasses add their widgets, so those follow the container. */
	@Inject(method = "init()V", at = @At("TAIL"))
	private void rbv$makeRoomForPanel(CallbackInfo ci) {
		leftPos += ViewerPanel.containerOffset(this, width, leftPos, imageWidth);
	}

	/** Right after the slots are drawn, while the pose is still at the container's corner. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
	private void rbv$fadeSearchMisses(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		SearchHighlight.render(g, (AbstractContainerScreen<?>) (Object) this);
	}
}
