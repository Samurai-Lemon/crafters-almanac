package com.samurailemon.craftersalmanac.recipe;

/** How a recipe is drawn. Picked from the recipe's class, so modded recipes reuse vanilla layouts where they fit. */
public enum Layout {
	CRAFTING,
	COOKING,
	/** One input, one output: stonecutter and similar modded recipes. */
	SINGLE,
	SMITHING,
	/** Any other recipe: a grid of inputs, an arrow and the output. */
	GENERIC,
	/** The middle of the brewing stand screen (ingredient, tubes, three bottles), then an arrow and the result. */
	BREWING,
	/** A fuel item and how many items it smelts. */
	FUEL
}
