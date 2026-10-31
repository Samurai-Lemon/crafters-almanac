package com.samurailemon.recipebookviewer.recipe;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One item slot in a recipe, positioned relative to the recipe's top-left corner. The item is centered in it.
 * Almost every slot is both drawn and searched ("recipes for" / "uses of"). The exceptions are brewing's: its bottles
 * are drawn but swap between the starting potion and the result, so the real input and result are separate searched
 * slots that aren't drawn.
 */
public record DisplaySlot(int x, int y, int size, boolean output, List<ItemStack> stacks, boolean shown, boolean searched) {
	public DisplaySlot(int x, int y, int size, boolean output, List<ItemStack> stacks) {
		this(x, y, size, output, stacks, true, true);
	}

	public static DisplaySlot shownOnly(int x, int y, int size, List<ItemStack> stacks) {
		return new DisplaySlot(x, y, size, false, stacks, true, false);
	}

	public static DisplaySlot searchedOnly(boolean output, List<ItemStack> stacks) {
		return new DisplaySlot(0, 0, 0, output, stacks, false, true);
	}

	/** Ingredients that accept several items (e.g. any planks) cycle once per second. */
	public ItemStack current() {
		if (stacks.isEmpty()) return ItemStack.EMPTY;
		return stacks.get((int) (Util.getMillis() / 1000L % stacks.size()));
	}

	/**
	 * Plain stacks match by item. Stacks with components (potions, which are all the same item) must match those
	 * too, so "recipes for Potion of Swiftness" doesn't list every potion recipe.
	 */
	public boolean contains(ItemStack stack) {
		for (ItemStack candidate : stacks) {
			boolean same = candidate.getComponentsPatch().isEmpty()
					? ItemStack.isSameItem(candidate, stack)
					: ItemStack.isSameItemSameComponents(candidate, stack);
			if (same) return true;
		}
		return false;
	}
}
