package com.samurailemon.craftersalmanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.samurailemon.craftersalmanac.gui.RecipePreview;
import com.samurailemon.craftersalmanac.gui.ScreenShift;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Keeps clipping and tooltips right while a screen is drawn shifted (see ScreenShift). Clipping areas are in screen
 * coordinates and ignore the drawing shift, so they're moved along with it (the inventory's player model, a
 * villager's trade list). Tooltips are kept from running off the real right edge of the screen, and cut down to the
 * item's name and moved to make room for the recipe preview under them.
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

	/** While the recipe preview is shown under a tooltip, the tooltip keeps just its first line, the item's name. */
	@ModifyVariable(method = "renderTooltipInternal", at = @At("HEAD"), argsOnly = true)
	private List<ClientTooltipComponent> rbv$nameOnly(List<ClientTooltipComponent> components) {
		return components.size() > 1 && RecipePreview.shortensTooltip() ? components.subList(0, 1) : components;
	}

	@WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
	private Vector2ic rbv$positionTooltip(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int mouseX, int mouseY,
			int width, int height, Operation<Vector2ic> original) {
		int visibleWidth = screenWidth - ScreenShift.drawing;
		// Whatever the tooltip is drawn inside (a shifted screen), in real screen coordinates.
		Matrix4f pose = ((GuiGraphics) (Object) this).pose().last().pose();
		int[] stacked = RecipePreview.placeTooltip(visibleWidth, screenHeight, mouseX, mouseY, width, height,
				(int) pose.m30(), (int) pose.m31());
		if (stacked != null) return new Vector2i(stacked[0], stacked[1]);
		return original.call(positioner, visibleWidth, screenHeight, mouseX, mouseY, width, height);
	}
}
