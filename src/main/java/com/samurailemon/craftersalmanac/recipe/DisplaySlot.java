package com.samurailemon.craftersalmanac.recipe;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One item slot in a recipe, positioned relative to the recipe's top-left corner. The item is centered in it.
 * Almost every slot is both drawn and searched ("recipes for" / "uses of"). The exceptions are brewing's: its bottles
 * are drawn but swap between the starting potion and the result, so the real input and result are separate searched
 * slots that aren't drawn. {@code chance} is for outputs a recipe only sometimes gives (1 = always).
 */
public record DisplaySlot(int x, int y, int size, boolean output, List<ItemStack> stacks, boolean shown, boolean searched, float chance) {
	public DisplaySlot(int x, int y, int size, boolean output, List<ItemStack> stacks) {
		this(x, y, size, output, stacks, true, true, 1.0F);
	}

	public static DisplaySlot shownOnly(int x, int y, int size, List<ItemStack> stacks) {
		return new DisplaySlot(x, y, size, false, stacks, true, false, 1.0F);
	}

	public static DisplaySlot searchedOnly(boolean output, List<ItemStack> stacks) {
		return new DisplaySlot(0, 0, 0, output, stacks, false, true, 1.0F);
	}

	/** A recipe output that only comes some of the time, like a crusher's bonus dust. */
	public static DisplaySlot chanceOutput(int x, int y, int size, ItemStack stack, float chance) {
		return new DisplaySlot(x, y, size, true, List.of(stack.copy()), true, true, chance);
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
