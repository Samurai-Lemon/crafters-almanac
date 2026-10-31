package com.samurailemon.craftersalmanac.recipe;

import com.samurailemon.craftersalmanac.mixin.SmithingTransformRecipeAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts recipes into {@link Display}s, each laid out on a {@link Card}. Crafting, cooking and brewing use the art
 * cards, with slots at the positions drawn into them (vanilla's own screen positions). Everything else is laid out
 * from (0, 0) and then put on a plain card. The layout comes from the recipe's class, so a mod's own cooking or
 * single-item recipe types look like vanilla's; anything else gets the generic grid.
 */
public final class DisplayFactory {
	/** Slots drawn into the art cards are 16x16 (the item itself) and 24x24 for results. */
	private static final int CARD_SLOT = 16;
	private static final int CARD_RESULT = 24;
	/** Plain-card slots: 18 for inputs, 26 for the big result slot, like vanilla's. */
	private static final int INPUT = 18;
	private static final int OUTPUT = 26;
	/** The favorite star, and the fill and calculator buttons left of it. */
	private static final int BUTTON = 11;
	/** Space between a plain card's border and its contents. */
	private static final int PADDING = 2;
	private static final int GENERIC_COLUMNS = 3;
	/** More rows than this wouldn't fit on a page. */
	private static final int GENERIC_MAX_ROWS = 3;

	private DisplayFactory() {
	}

	/** Returns null for recipes we can't show (special/dynamic recipes, no result, no inputs, too many inputs). */
	public static Display create(RecipeHolder<?> holder, HolderLookup.Provider registries) {
		Recipe<?> recipe = holder.value();
		if (recipe.isSpecial()) return null;

		ItemStack result = recipe.getResultItem(registries);
		if (result.isEmpty()) return null;
		Category category = Category.of(recipe, result);

		List<DisplaySlot> slots = new ArrayList<>();
		if (recipe instanceof CraftingRecipe) {
			// The crafting table screen's grid at (30, 17) and result at (124, 35), as drawn in the card.
			NonNullList<Ingredient> ingredients = recipe.getIngredients();
			int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
			for (int i = 0; i < ingredients.size(); i++) {
				int col = i % width;
				int row = i / width;
				if (col > 2 || row > 2) return null;
				addInput(slots, Card.CRAFTING.x(30 + col * 18), 17 + row * 18, CARD_SLOT, ingredients.get(i));
			}
			slots.add(output(Card.CRAFTING.x(120), 31, CARD_RESULT, result));
			return artCard(holder.id(), category, Layout.CRAFTING, Card.CRAFTING, slots, !(recipe instanceof ShapedRecipe), 0, 0);
		}
		if (recipe instanceof AbstractCookingRecipe cooking) {
			// The furnace screen's input at (56, 17) and result at (116, 35); the fuel slot is left empty.
			addInput(slots, Card.FURNACE.x(56), 17, CARD_SLOT, cooking.getIngredients().get(0));
			slots.add(output(Card.FURNACE.x(112), 31, CARD_RESULT, result));
			return artCard(holder.id(), category, Layout.COOKING, Card.FURNACE, slots, false, cooking.getCookingTime(), cooking.getExperience());
		}
		if (recipe instanceof SingleItemRecipe single) {
			addInput(slots, 0, 4, INPUT, single.getIngredients().get(0));
			slots.add(output(53, 0, OUTPUT, result));
			return plainCard(holder.id(), category, Layout.SINGLE, 53 + OUTPUT, OUTPUT, slots, 0);
		}
		if (recipe instanceof SmithingTransformRecipe smithing) {
			SmithingTransformRecipeAccessor parts = (SmithingTransformRecipeAccessor) smithing;
			addInput(slots, 0, 4, INPUT, parts.rbv$getTemplate());
			addInput(slots, 18, 4, INPUT, parts.rbv$getBase());
			addInput(slots, 36, 4, INPUT, parts.rbv$getAddition());
			slots.add(output(85, 0, OUTPUT, result));
			return plainCard(holder.id(), category, Layout.SMITHING, 85 + OUTPUT, OUTPUT, slots, 0);
		}
		List<List<ItemStack>> inputs = recipe.getIngredients().stream()
				.filter(ingredient -> !ingredient.isEmpty())
				.map(ingredient -> List.of(ingredient.getItems()))
				.toList();
		return generic(holder.id(), category, inputs, List.of(result));
	}

	/**
	 * Inputs in a grid up to 3 wide, then an arrow and the output, all centered vertically. Each input and the output
	 * can list several stacks to cycle through; lists of the same length cycle in step.
	 */
	private static Display generic(ResourceLocation id, Category category, List<List<ItemStack>> inputs, List<ItemStack> outputs) {
		if (inputs.isEmpty() || outputs.isEmpty()) return null;
		int columns = Math.min(inputs.size(), GENERIC_COLUMNS);
		int rows = (inputs.size() + GENERIC_COLUMNS - 1) / GENERIC_COLUMNS;
		if (rows > GENERIC_MAX_ROWS) return null;

		int gridHeight = rows * INPUT;
		int height = Math.max(gridHeight, OUTPUT);
		int gridY = (height - gridHeight) / 2;
		List<DisplaySlot> slots = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			slots.add(new DisplaySlot(i % GENERIC_COLUMNS * INPUT, gridY + i / GENERIC_COLUMNS * INPUT, INPUT, false, List.copyOf(inputs.get(i))));
		}
		// Arrow (24 wide) with a 4px gap each side; RecipeRenderer finds it from the output's position.
		int outputX = columns * INPUT + 4 + 24 + 4;
		slots.add(new DisplaySlot(outputX, (height - OUTPUT) / 2, OUTPUT, true, outputs.stream().map(ItemStack::copy).toList()));
		return plainCard(id, category, Layout.GENERIC, outputX + OUTPUT, height, slots, 0);
	}

	/**
	 * A brewing recipe on the brewing stand card: the ingredient in the top slot, blaze powder in the fuel slot, and
	 * the three bottles at the stand's own positions. Like a real stand, the bottles swap between the starting potion
	 * and the result (every second, in step with the brew arrow). The real input and result are separate searched
	 * slots that aren't drawn. Lists of the same length line up, so gunpowder and dragon's breath recipes cycle
	 * through every potion with each bottle showing a potion and then its splash or lingering version.
	 */
	public static Display brewing(ResourceLocation id, List<ItemStack> potions, List<ItemStack> ingredient, List<ItemStack> results) {
		if (potions.isEmpty() || ingredient.isEmpty() || results.isEmpty()) return null;
		List<ItemStack> bottles = new ArrayList<>();
		for (int i = 0; i < Math.max(potions.size(), results.size()); i++) {
			bottles.add(potions.get(i % potions.size()));
			bottles.add(results.get(i % results.size()));
		}
		Card card = Card.BREWING;
		List<DisplaySlot> slots = List.of(
				new DisplaySlot(card.x(79), 17, CARD_SLOT, false, List.copyOf(ingredient)),
				DisplaySlot.shownOnly(card.x(17), 17, CARD_SLOT, List.of(new ItemStack(Items.BLAZE_POWDER))),
				DisplaySlot.shownOnly(card.x(56), 51, CARD_SLOT, List.copyOf(bottles)),
				DisplaySlot.shownOnly(card.x(79), 58, CARD_SLOT, List.copyOf(bottles)),
				DisplaySlot.shownOnly(card.x(102), 51, CARD_SLOT, List.copyOf(bottles)),
				DisplaySlot.searchedOnly(false, List.copyOf(potions)),
				DisplaySlot.searchedOnly(true, results.stream().map(ItemStack::copy).toList()));
		return artCard(id, Category.BREWING, Layout.BREWING, card, slots, false, 0, 0);
	}

	/** A fuel item and how long it burns: the item, then "Smelts / N items" (drawn by RecipeRenderer). Burn time goes in cookTime. */
	public static Display fuel(ResourceLocation id, ItemStack stack, int burnTime) {
		List<DisplaySlot> slots = List.of(new DisplaySlot(0, 4, INPUT, false, List.of(stack)));
		return plainCard(id, Category.FUEL, Layout.FUEL, 72, OUTPUT, slots, burnTime);
	}

	/** A 128x78 art card. The buttons go in its bottom-right corner, inside the border. */
	private static Display artCard(ResourceLocation id, Category category, Layout layout, Card card, List<DisplaySlot> slots,
			boolean shapeless, int cookTime, float xp) {
		int width = Card.WIDTH;
		int height = Card.TEXTURE_HEIGHT;
		return new Display(category, layout, card, width, height, width - Card.BORDER - 1 - BUTTON, height - Card.BOTTOM_BORDER - 1 - BUTTON,
				id, List.copyOf(slots), shapeless, cookTime, xp);
	}

	/**
	 * Puts contents laid out from (0, 0) on a plain card sized to fit them. The star goes right of the contents when
	 * there's room on the page, otherwise below them in the bottom-right corner.
	 */
	private static Display plainCard(ResourceLocation id, Category category, Layout layout, int contentWidth, int contentHeight,
			List<DisplaySlot> content, int cookTime) {
		int inset = Card.BORDER + PADDING;
		boolean beside = inset + contentWidth + PADDING + BUTTON + inset <= Card.WIDTH;
		int width = beside ? inset + contentWidth + PADDING + BUTTON + inset : inset + contentWidth + inset;
		int height = beside
				? inset + contentHeight + PADDING + Card.BOTTOM_BORDER
				: inset + contentHeight + PADDING + BUTTON + PADDING + Card.BOTTOM_BORDER;
		int buttonX = beside ? inset + contentWidth + PADDING : width - Card.BORDER - 1 - BUTTON;
		int buttonY = beside ? inset + (contentHeight - BUTTON) / 2 : height - Card.BOTTOM_BORDER - 1 - BUTTON;
		List<DisplaySlot> slots = content.stream()
				.map(slot -> new DisplaySlot(slot.x() + inset, slot.y() + inset, slot.size(), slot.output(), slot.stacks(), slot.shown(), slot.searched()))
				.toList();
		return new Display(category, layout, Card.PLAIN, width, height, buttonX, buttonY, id, slots, false, cookTime, 0);
	}

	private static void addInput(List<DisplaySlot> slots, int x, int y, int size, Ingredient ingredient) {
		if (ingredient.isEmpty()) return;
		slots.add(new DisplaySlot(x, y, size, false, List.of(ingredient.getItems())));
	}

	private static DisplaySlot output(int x, int y, int size, ItemStack result) {
		return new DisplaySlot(x, y, size, true, List.of(result.copy()));
	}
}
