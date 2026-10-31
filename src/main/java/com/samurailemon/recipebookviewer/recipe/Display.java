package com.samurailemon.recipebookviewer.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A recipe flattened into slots the panel can draw, independent of the recipe class it came from. It's drawn on a
 * card; width and height are the card's, and (buttonX, buttonY) is where the favorite star goes, with the fill and
 * calculator buttons to its left.
 */
public record Display(Category category, Layout layout, Card card, int width, int height, int buttonX, int buttonY,
		ResourceLocation id, List<DisplaySlot> slots, boolean shapeless, int cookTime, float xp) {
	public boolean produces(ItemStack stack) {
		for (DisplaySlot slot : slots) {
			if (slot.searched() && slot.output() && slot.contains(stack)) return true;
		}
		return false;
	}

	public boolean consumes(ItemStack stack) {
		for (DisplaySlot slot : slots) {
			if (slot.searched() && !slot.output() && slot.contains(stack)) return true;
		}
		return false;
	}
}
