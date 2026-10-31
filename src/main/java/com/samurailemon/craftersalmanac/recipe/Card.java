package com.samurailemon.craftersalmanac.recipe;

/**
 * The card a recipe is drawn on. The three art cards are 176x78 images (the width of a vanilla container screen)
 * with their slots drawn in at the same positions as vanilla's screens. They're drawn 128 wide so they fit the book:
 * the 5px border on each side as is, and the 118px of the middle from {@code keepX}, which holds the art. Smoker,
 * blast furnace, campfire and modded cooking use the furnace card. Everything else gets a plain card of any size,
 * built from the furnace card's border and empty middle.
 */
public enum Card {
	CRAFTING("card_crafting", 28),
	FURNACE("card_furnace", 36),
	BREWING("card_brewing", 12),
	PLAIN("card_furnace", 0);

	public static final int TEXTURE_WIDTH = 176;
	public static final int TEXTURE_HEIGHT = 78;
	/** Border thickness in the card images. */
	public static final int BORDER = 5;
	public static final int BOTTOM_BORDER = 7;
	/** How wide the art cards are drawn, and how much of their middle that keeps. */
	public static final int WIDTH = 128;
	public static final int KEPT = WIDTH - 2 * BORDER;

	public final String sprite;
	/** Where in the image the kept middle starts (art cards only). */
	public final int keepX;

	Card(String sprite, int keepX) {
		this.sprite = sprite;
		this.keepX = keepX;
	}

	/** Converts an x position in the card image to one on the drawn card. */
	public int x(int imageX) {
		return imageX - keepX + BORDER;
	}
}
