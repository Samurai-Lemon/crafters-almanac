package com.samurailemon.craftersalmanac.mixin;

import com.samurailemon.craftersalmanac.gui.ViewerPanel;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends typing straight to our search box while it's focused. Going through the screen only works if the screen
 * still has our panel focused and no other mod handles the key first, which isn't always true in modpacks.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void rbv$typeIntoSearch(long window, int codePoint, int modifiers, CallbackInfo ci) {
		ViewerPanel panel = searchPanel(window);
		if (panel == null) return;
		for (char c : Character.toChars(codePoint)) {
			panel.charTyped(c, modifiers);
		}
		ci.cancel();
	}

	/** Escape still closes the screen, and F-keys (screenshot, fullscreen, debug) still work, as with vanilla text boxes. */
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void rbv$searchKeys(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		ViewerPanel panel = searchPanel(window);
		if (panel == null || key == GLFW.GLFW_KEY_ESCAPE || (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25)) return;
		if (action != GLFW.GLFW_RELEASE) panel.searchKeyPressed(key, scancode, modifiers);
		ci.cancel();
	}

	private ViewerPanel searchPanel(long window) {
		if (window != minecraft.getWindow().getWindow()) return null;
		ViewerPanel panel = ViewerPanel.active();
		return panel != null && panel.isSearchFocused() ? panel : null;
	}
}
