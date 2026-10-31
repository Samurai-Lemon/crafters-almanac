package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.Category;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the open crafting grid or furnace with a recipe's ingredients from the player's inventory.
 * Uses the same packet as clicking a recipe in the vanilla recipe book, so the server does the moving
 * and it works on any server. The catch is the same as vanilla's: the server only fills recipes the
 * player has unlocked.
 */
public final class RecipeTransfer {
	public enum Status {
		/** The open screen can't take this recipe (wrong station, or the grid is too small). No button is shown. */
		UNSUPPORTED,
		/** The player hasn't unlocked the recipe, so the server would ignore the request. */
		LOCKED,
		/** Some ingredients aren't in the inventory or the grid. */
		MISSING,
		READY
	}

	/** Status plus, when items are missing, which of the recipe's input slots couldn't be filled. */
	public record Check(Status status, List<DisplaySlot> missing) {
	}

	private static final Check UNSUPPORTED = new Check(Status.UNSUPPORTED, List.of());

	private RecipeTransfer() {
	}

	/** Runs every frame for each recipe on the page, and calls into the recipe's (possibly modded) code. */
	public static Check check(Display display, AbstractContainerMenu menu) {
		try {
			return checkUnsafe(display, menu);
		} catch (RuntimeException | LinkageError e) {
			// A broken modded recipe just loses its fill button instead of crashing the game while drawing.
			return UNSUPPORTED;
		}
	}

	private static Check checkUnsafe(Display display, AbstractContainerMenu menu) {
		LocalPlayer player = Minecraft.getInstance().player;
		RecipeHolder<?> holder = holder(display);
		if (player == null || holder == null || !(menu instanceof RecipeBookMenu<?, ?> recipeMenu) || !accepts(recipeMenu, display, holder)) {
			return UNSUPPORTED;
		}
		if (!player.getRecipeBook().contains(holder)) return new Check(Status.LOCKED, List.of());

		// Items already in the grid count too: the server moves them back to the inventory before filling.
		StackedContents contents = new StackedContents();
		player.getInventory().fillStackedContents(contents);
		recipeMenu.fillCraftSlotsStackedContents(contents);
		if (contents.canCraft(holder.value(), null)) return new Check(Status.READY, List.of());
		return new Check(Status.MISSING, missingSlots(display, available(menu)));
	}

	/** Asks the server to fill the grid. With shift held it fills as many crafts as the ingredients allow. */
	public static void transfer(Display display, AbstractContainerMenu menu, boolean max) {
		Minecraft minecraft = Minecraft.getInstance();
		RecipeHolder<?> holder = holder(display);
		if (holder == null || minecraft.gameMode == null) return;
		minecraft.gameMode.handlePlaceRecipe(menu.containerId, holder, max);
	}

	private static RecipeHolder<?> holder(Display display) {
		var level = Minecraft.getInstance().level;
		return level == null ? null : level.getRecipeManager().byKey(display.id()).orElse(null);
	}

	private static boolean accepts(RecipeBookMenu<?, ?> menu, Display display, RecipeHolder<?> holder) {
		RecipeBookType type = menu.getRecipeBookType();
		Category category = display.category();
		return switch (type) {
			case CRAFTING -> category == Category.CRAFTING && holder.value().canCraftInDimensions(menu.getGridWidth(), menu.getGridHeight());
			case FURNACE -> category == Category.SMELTING;
			case BLAST_FURNACE -> category == Category.BLASTING;
			case SMOKER -> category == Category.SMOKING;
			// Recipe book types added by other mods.
			default -> false;
		};
	}

	/** Item counts in the player's inventory plus the open crafting grid or furnace input. */
	public static Map<Item, Integer> available(AbstractContainerMenu menu) {
		Map<Item, Integer> available = new HashMap<>();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return available;
		for (ItemStack stack : player.getInventory().items) {
			if (!stack.isEmpty()) available.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		if (menu instanceof RecipeBookMenu<?, ?> recipeMenu) {
			// The menu's first getSize() slots are the grid and result; shouldMoveToInventory skips the furnace's fuel.
			for (int i = 0; i < recipeMenu.getSize() && i < menu.slots.size(); i++) {
				Slot slot = menu.slots.get(i);
				if (i != recipeMenu.getResultSlotIndex() && recipeMenu.shouldMoveToInventory(i) && slot.hasItem()) {
					available.merge(slot.getItem().getItem(), slot.getItem().getCount(), Integer::sum);
				}
			}
		}
		return available;
	}

	/**
	 * Which input slots can't be filled from {@code available}. Hands out items slot by slot, pickiest slots
	 * first, so it can rarely be wrong when ingredients overlap (e.g. "any planks" next to "oak planks").
	 */
	public static List<DisplaySlot> missingSlots(Display display, Map<Item, Integer> available) {
		List<DisplaySlot> inputs = new ArrayList<>();
		for (DisplaySlot slot : display.slots()) {
			if (!slot.output()) inputs.add(slot);
		}
		Map<Item, Integer> left = new HashMap<>(available);
		inputs.sort(Comparator.comparingInt(slot -> slot.stacks().size()));

		List<DisplaySlot> missing = new ArrayList<>();
		for (DisplaySlot slot : inputs) {
			if (!take(left, slot)) missing.add(slot);
		}
		return missing;
	}

	private static boolean take(Map<Item, Integer> available, DisplaySlot slot) {
		for (ItemStack option : slot.stacks()) {
			Integer count = available.get(option.getItem());
			if (count != null && count > 0) {
				available.put(option.getItem(), count - 1);
				return true;
			}
		}
		return false;
	}
}
