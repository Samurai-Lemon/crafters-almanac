package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Items you could make right now from what you're carrying, for the item list's filter button. Counts every
 * recipe type, wherever you are, and ignores fuel. Only recomputed when the inventory changes.
 */
public final class Craftable {
	private static Map<Item, Integer> cachedAvailable;
	private static List<Display> cachedDisplays;
	private static Set<Item> items = Set.of();

	private Craftable() {
	}

	/** Returns the same set instance until it changes, so callers can cache against it. */
	public static Set<Item> items(AbstractContainerMenu menu) {
		Map<Item, Integer> available = RecipeTransfer.available(menu);
		List<Display> displays = RecipeIndex.all();
		if (!available.equals(cachedAvailable) || displays != cachedDisplays) {
			cachedAvailable = available;
			cachedDisplays = displays;
			items = compute(displays, available);
		}
		return items;
	}

	private static Set<Item> compute(List<Display> displays, Map<Item, Integer> available) {
		Set<Item> result = new HashSet<>();
		for (Display display : displays) {
			// Brewing and fuel aren't crafted; potions also all count as one item here.
			if (display.category().isVirtual()) continue;
			if (!RecipeTransfer.missingSlots(display, available).isEmpty()) continue;
			for (DisplaySlot slot : display.slots()) {
				if (slot.output()) slot.stacks().forEach(stack -> result.add(stack.getItem()));
			}
		}
		return Set.copyOf(result);
	}
}
