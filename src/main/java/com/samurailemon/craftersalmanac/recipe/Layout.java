package com.samurailemon.craftersalmanac.recipe;

/** How a recipe is drawn. Picked from the recipe's class, so modded recipes reuse vanilla layouts where they fit. */
public enum Layout {
	CRAFTING,
	COOKING,
	/** One input, one output: stonecutter and similar modded recipes. */
	SINGLE,
	SMITHING,
	/** Template, armor and material above an arrow and the trimmed armor, with an armor stand wearing it on the right. */
	TRIM,
	/** Any other recipe: a grid of inputs, an arrow and the output (or several, with their chances). */
	GENERIC,
	/** A modded machine's recipe on a piece of the mod's own GUI art (see Station). */
	STATION,
	/** The middle of the brewing stand screen (ingredient, tubes, three bottles), then an arrow and the result. */
	BREWING,
	/** A fuel item and how many items it smelts. */
	FUEL
}
