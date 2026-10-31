package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.recipe.ItemList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.Slot;

/** While the search box has text, fades out the container's items that don't match, so the matches stand out. */
public final class SearchHighlight {
	/** The slot background colour, mostly opaque, drawn over the item. */
	private static final int FADE = 0xC08B8B8B;

	private SearchHighlight() {
	}

	/** Called mid-render with the pose already moved to the container's corner, so slot coordinates work as-is. */
	public static void render(GuiGraphics g, AbstractContainerScreen<?> screen) {
		ViewerPanel panel = ViewerPanel.active();
		String query = ViewerState.search;
		if (panel == null || panel.screen() != screen || query.isBlank() || !ViewerConfig.get().highlightSearch) return;
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.isActive() || !slot.hasItem() || ItemList.matches(slot.getItem(), query)) continue;
			// guiOverlay skips the depth test, the same way vanilla draws its slot highlight over items.
			g.fill(RenderType.guiOverlay(), slot.x, slot.y, slot.x + 16, slot.y + 16, FADE);
		}
	}
}
