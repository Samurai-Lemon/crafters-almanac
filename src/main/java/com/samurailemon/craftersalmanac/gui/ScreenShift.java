package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Makes room for the almanac by moving the whole container screen right, the way the vanilla recipe book moves it.
 * Rather than changing the screen's position (many screens, vanilla's chest included, work out their own position
 * and would come apart), the screen is laid out as usual and then drawn shifted, with the mouse shifted back by the
 * same amount before the screen sees it. Everything inside the screen stays lined up, however it does its maths.
 * See ScreenMixin (drawing), MouseHandlerMixin (mouse) and GuiGraphicsMixin (clipping and tooltip edges).
 */
public final class ScreenShift {
	/** The shift of the screen being drawn right now, else 0. Clipping and tooltip edges follow it. */
	public static int drawing;

	private ScreenShift() {
	}

	/** How far right a screen is drawn: room for the panel when it's open, else 0. */
	public static int of(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>)) return 0;
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		return ViewerPanel.containerOffset(screen, screen.width, container.rbv$getLeftPos(), container.rbv$getImageWidth());
	}
}
