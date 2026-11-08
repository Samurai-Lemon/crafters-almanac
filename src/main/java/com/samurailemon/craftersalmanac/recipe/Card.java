package com.samurailemon.craftersalmanac.recipe;

import net.minecraft.resources.ResourceLocation;

/**
 * The card a recipe is drawn on. Every card is framed with its station's own screen frame (see CardFrames). The art
 * cards then show the middle of the station's screen texture inside the frame: the crafting table's grid, the
 * furnace's slots and flame, the brewing stand's tubes. The recipes' slots sit at the same positions as on those
 * screens. All of it is the game's own textures, so resource packs restyle the cards along with the game. Smoker,
 * blast furnace, campfire and modded cooking use the furnace card's layout. Everything else gets a plain card of any
 * size, the frame alone.
 */
public enum Card {
	CRAFTING("crafting_table", 28),
	FURNACE("furnace", 36),
	BREWING("brewing_stand", 12),
	PLAIN(null, 0);

	/** Art cards' height: the screens' tops, down to just below the brewing stand's lowest bottle. */
	public static final int HEIGHT = 80;
	/** The frame's thickness. */
	public static final int BORDER = 4;
	public static final int BOTTOM_BORDER = 4;
	/** How wide the art cards are drawn, and how much of the screen's middle that keeps. */
	public static final int WIDTH = 128;
	public static final int KEPT = WIDTH - 2 * BORDER;

	/** The station's screen texture (art cards only). */
	public final ResourceLocation texture;
	/** Where in the screen texture the kept middle starts (art cards only). */
	public final int keepX;

	Card(String screen, int keepX) {
		this.texture = screen == null ? null : ResourceLocation.withDefaultNamespace("textures/gui/container/" + screen + ".png");
		this.keepX = keepX;
	}

	/** Converts an x position on the station's screen to one on the drawn card. */
	public int x(int screenX) {
		return screenX - keepX + BORDER;
	}
}
