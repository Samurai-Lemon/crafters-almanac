package com.samurailemon.craftersalmanac.recipe;

import com.samurailemon.craftersalmanac.mixin.SmithingTransformRecipeAccessor;
import com.samurailemon.craftersalmanac.mixin.SmithingTrimRecipeAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts recipes into {@link Display}s, each laid out on a {@link Card}. Crafting, cooking and brewing use the art
 * cards, with slots at the positions drawn into them (vanilla's own screen positions). Everything else is laid out
 * from (0, 0) and then put on a plain card. The layout comes from the recipe's class, so a mod's own cooking or
 * single-item recipe types look like vanilla's. Other modded recipes go on their machine's art if there's a
 * {@link Station} layout for them, else the generic grid, with whatever else {@link RecipeData} finds in them.
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
	/** The widest a plain card's contents can be and still fit the page. */
	private static final int MAX_CONTENT_WIDTH = Card.WIDTH - 2 * (Card.BORDER + PADDING);
	/** Station cards: the machine art sits this far inside the card's edge. */
	private static final int STATION_INSET = Card.BORDER + 1;

	private DisplayFactory() {
	}

	/** Returns null for recipes we can't show (special/dynamic recipes, no result, no inputs, too many inputs). */
	public static Display create(RecipeHolder<?> holder, HolderLookup.Provider registries) {
		Recipe<?> recipe = holder.value();
		if (recipe.isSpecial()) return null;
		// A station file can hide its recipe type, or name the recipe's fields its own way.
		Station station = Station.of(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
		if (station != null && station.hidden()) return null;
		Map<String, List<String>> names = station == null ? Map.of() : station.fields();

		ItemStack result = recipe.getResultItem(registries);
		RecipeData data = null;
		if (result.isEmpty()) {
			// Machines with several outputs (Oritech's) may not name a single result; read them from the recipe itself.
			data = RecipeData.read(recipe, registries, names);
			List<RecipeData.Output> outputs = data == null ? List.of() : data.outputs();
			if (outputs.isEmpty()) return null;
			result = outputs.get(0).stack();
		}
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
		if (recipe instanceof SmithingTrimRecipe trim) {
			Display display = trim(holder.id(), category, (SmithingTrimRecipeAccessor) trim, registries);
			if (display != null) return display;
		}
		if (recipe instanceof SmithingTransformRecipe smithing) {
			SmithingTransformRecipeAccessor parts = (SmithingTransformRecipeAccessor) smithing;
			addInput(slots, 0, 4, INPUT, parts.rbv$getTemplate());
			addInput(slots, 18, 4, INPUT, parts.rbv$getBase());
			addInput(slots, 36, 4, INPUT, parts.rbv$getAddition());
			slots.add(output(85, 0, OUTPUT, result));
			return plainCard(holder.id(), category, Layout.SMITHING, 85 + OUTPUT, OUTPUT, slots, 0);
		}
		return modded(holder.id(), category, recipe, result, data != null ? data : RecipeData.read(recipe, registries, names), station);
	}

	/**
	 * Any other recipe, usually a modded machine's: on the machine's own art if there's a station layout for it that
	 * the recipe fits, else the generic card. Either way with the outputs, chances, time and XP the recipe holds.
	 */
	private static Display modded(ResourceLocation id, Category category, Recipe<?> recipe, ItemStack result, RecipeData data, Station station) {
		List<List<ItemStack>> inputs = new ArrayList<>(recipe.getIngredients().stream()
				.filter(ingredient -> !ingredient.isEmpty())
				.map(ingredient -> List.of(ingredient.getItems()))
				.toList());
		List<RecipeData.Output> outputs = data == null ? List.of() : data.outputs();
		if (outputs.isEmpty()) outputs = List.of(new RecipeData.Output(result, 1.0F));
		int time = data == null ? 0 : data.time();
		float xp = data == null ? 0 : data.xp();
		if (station != null && station.hasArt() && inputs.size() <= station.inputs().size()) {
			return station(id, category, station, inputs, outputs, data, time, xp);
		}
		if (data != null) inputs.addAll(data.extraInputs());
		return generic(id, category, inputs, outputs, time, xp);
	}

	/**
	 * A recipe on its machine's art (see Station): ingredients and outputs where the layout puts them, and the layout's
	 * extra slots filled from the recipe's fields (the Cooking Pot's bowl). Outputs the art has no place for are still
	 * searched. Time and XP go in the strip along the bottom, left of the buttons, unless the layout places them.
	 */
	private static Display station(ResourceLocation id, Category category, Station station, List<List<ItemStack>> inputs,
			List<RecipeData.Output> outputs, RecipeData data, int time, float xp) {
		int inset = STATION_INSET;
		List<DisplaySlot> slots = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			int[] at = station.inputs().get(i);
			slots.add(new DisplaySlot(inset + at[0], inset + at[1], CARD_SLOT, false, List.copyOf(inputs.get(i))));
		}
		RecipeData.Output first = outputs.get(0);
		slots.add(DisplaySlot.chanceOutput(inset + station.output()[0], inset + station.output()[1], CARD_SLOT, first.stack(), first.chance()));
		for (int i = 1; i < outputs.size(); i++) {
			RecipeData.Output other = outputs.get(i);
			if (i - 1 < station.outputs().size()) {
				int[] at = station.outputs().get(i - 1);
				slots.add(DisplaySlot.chanceOutput(inset + at[0], inset + at[1], CARD_SLOT, other.stack(), other.chance()));
			} else {
				slots.add(DisplaySlot.searchedOnly(true, List.of(other.stack().copy())));
			}
		}
		if (data != null) {
			for (Map.Entry<String, int[]> extra : station.slots().entrySet()) {
				List<ItemStack> stacks = data.stacks(extra.getKey());
				if (stacks.isEmpty()) continue;
				slots.add(new DisplaySlot(inset + extra.getValue()[0], inset + extra.getValue()[1], CARD_SLOT, false, List.copyOf(stacks)));
			}
		}
		int width = 2 * inset + station.width();
		int height = inset + station.height() + PADDING + BUTTON + PADDING + Card.BOTTOM_BORDER;
		return new Display(category, Layout.STATION, Card.PLAIN, width, height, width - Card.BORDER - 1 - BUTTON,
				height - Card.BOTTOM_BORDER - 1 - BUTTON, id, List.copyOf(slots), false, time, xp, station);
	}

	/**
	 * An armor trim: every armor piece the recipe takes with every trim material, as lists that cycle in step, so the
	 * result shows that piece trimmed in that material (the material changes every second, the armor after a full
	 * round). The template, armor and material go in a row above an arrow and the result; the armor stand that
	 * RecipeRenderer draws wearing the result fills the right of the card. Null if the template or materials aren't
	 * known trims, so the recipe falls back to the generic layout.
	 */
	private static Display trim(ResourceLocation id, Category category, SmithingTrimRecipeAccessor parts, HolderLookup.Provider registries) {
		List<ItemStack> templates = new ArrayList<>();
		List<ItemStack> bases = new ArrayList<>();
		List<ItemStack> additions = new ArrayList<>();
		List<ItemStack> results = new ArrayList<>();
		for (ItemStack template : parts.rbv$getTemplate().getItems()) {
			Optional<Holder.Reference<TrimPattern>> pattern = TrimPatterns.getFromTemplate(registries, template);
			if (pattern.isEmpty()) continue;
			for (ItemStack base : parts.rbv$getBase().getItems()) {
				for (ItemStack addition : parts.rbv$getAddition().getItems()) {
					Optional<Holder.Reference<TrimMaterial>> material = TrimMaterials.getFromIngredient(registries, addition);
					if (material.isEmpty()) continue;
					ItemStack result = base.copyWithCount(1);
					result.set(DataComponents.TRIM, new ArmorTrim(material.get(), pattern.get()));
					templates.add(template);
					bases.add(base);
					additions.add(addition);
					results.add(result);
				}
			}
		}
		if (results.isEmpty()) return null;
		List<DisplaySlot> slots = List.of(
				new DisplaySlot(0, 0, INPUT, false, List.copyOf(templates)),
				new DisplaySlot(18, 0, INPUT, false, List.copyOf(bases)),
				new DisplaySlot(36, 0, INPUT, false, List.copyOf(additions)),
				// Under the inputs, leaving room left of it for the arrow.
				new DisplaySlot(28, 22, OUTPUT, true, List.copyOf(results)));
		return plainCard(id, category, Layout.TRIM, Card.WIDTH - 2 * (Card.BORDER + PADDING), 22 + OUTPUT, slots, 0);
	}

	/**
	 * Inputs in a grid up to 3 wide, then an arrow and the outputs, all centered vertically. One output gets the big
	 * slot; several share a grid of normal ones, as many columns as fit, up to 3 rows (any more aren't drawn, but are
	 * still searched). Each input can list several stacks to cycle through; lists of the same length cycle in step.
	 */
	private static Display generic(ResourceLocation id, Category category, List<List<ItemStack>> inputs, List<RecipeData.Output> outputs,
			int time, float xp) {
		if (inputs.isEmpty() || outputs.isEmpty()) return null;
		int columns = Math.min(inputs.size(), GENERIC_COLUMNS);
		int rows = (inputs.size() + GENERIC_COLUMNS - 1) / GENERIC_COLUMNS;
		if (rows > GENERIC_MAX_ROWS) return null;

		// Arrow (24 wide) with a 4px gap each side; RecipeRenderer finds it from the outputs' position.
		int outputX = columns * INPUT + 4 + 24 + 4;
		boolean single = outputs.size() == 1;
		int outputSize = single ? OUTPUT : INPUT;
		int outputColumns = single ? 1 : Mth.clamp((MAX_CONTENT_WIDTH - outputX) / INPUT, 1, outputs.size());
		int outputRows = Math.min(Mth.positiveCeilDiv(outputs.size(), outputColumns), GENERIC_MAX_ROWS);

		int gridHeight = rows * INPUT;
		int outputsHeight = outputRows * outputSize;
		int height = Math.max(gridHeight, outputsHeight);
		int gridY = (height - gridHeight) / 2;
		int outputY = (height - outputsHeight) / 2;
		List<DisplaySlot> slots = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			slots.add(new DisplaySlot(i % GENERIC_COLUMNS * INPUT, gridY + i / GENERIC_COLUMNS * INPUT, INPUT, false, List.copyOf(inputs.get(i))));
		}
		for (int i = 0; i < outputs.size(); i++) {
			RecipeData.Output output = outputs.get(i);
			if (i >= outputColumns * outputRows) {
				slots.add(DisplaySlot.searchedOnly(true, List.of(output.stack().copy())));
				continue;
			}
			slots.add(DisplaySlot.chanceOutput(outputX + i % outputColumns * outputSize, outputY + i / outputColumns * outputSize, outputSize,
					output.stack(), output.chance()));
		}
		return plainCard(id, category, Layout.GENERIC, outputX + outputColumns * outputSize, height, slots, time, xp);
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

	/**
	 * The brewing recipe for one potion, as a card showing only that potion instead of cycling through every potion
	 * a recipe takes. A recipe made for just this potion (sugar on an awkward potion) wins over one for any potion
	 * (gunpowder on any potion). Null if no brewing recipe makes it.
	 */
	public static Display brewingFor(List<Display> displays, ItemStack potion) {
		Display found = null;
		for (Display display : displays) {
			if (display.layout() != Layout.BREWING) continue;
			// See brewing(): the ingredient slot first, then the searched-only potions and results.
			List<DisplaySlot> slots = display.slots();
			List<ItemStack> potions = slots.get(slots.size() - 2).stacks();
			List<ItemStack> results = slots.get(slots.size() - 1).stacks();
			for (int i = 0; i < results.size(); i++) {
				if (!ItemStack.isSameItemSameComponents(results.get(i), potion)) continue;
				Display narrowed = brewing(display.id(), List.of(potions.get(i % potions.size())), slots.get(0).stacks(), List.of(results.get(i)));
				if (results.size() == 1) return narrowed;
				if (found == null) found = narrowed;
				break;
			}
		}
		return found;
	}

	/** A fuel item and how long it burns: the item, then "Smelts / N items" (drawn by RecipeRenderer). Burn time goes in cookTime. */
	public static Display fuel(ResourceLocation id, ItemStack stack, int burnTime) {
		List<DisplaySlot> slots = List.of(new DisplaySlot(0, 4, INPUT, false, List.of(stack)));
		return plainCard(id, Category.FUEL, Layout.FUEL, 72, OUTPUT, slots, burnTime);
	}

	/** A 128x80 art card. The buttons go in its bottom-right corner, inside the border. */
	private static Display artCard(ResourceLocation id, Category category, Layout layout, Card card, List<DisplaySlot> slots,
			boolean shapeless, int cookTime, float xp) {
		int width = Card.WIDTH;
		int height = Card.HEIGHT;
		return new Display(category, layout, card, width, height, width - Card.BORDER - 1 - BUTTON, height - Card.BOTTOM_BORDER - 1 - BUTTON,
				id, List.copyOf(slots), shapeless, cookTime, xp, null);
	}

	/**
	 * Puts contents laid out from (0, 0) on a plain card sized to fit them. The star goes right of the contents when
	 * there's room on the page, otherwise below them in the bottom-right corner. A modded recipe's time and XP go
	 * along the bottom, left of the star, so those always put it below.
	 */
	private static Display plainCard(ResourceLocation id, Category category, Layout layout, int contentWidth, int contentHeight,
			List<DisplaySlot> content, int cookTime) {
		return plainCard(id, category, layout, contentWidth, contentHeight, content, cookTime, 0);
	}

	private static Display plainCard(ResourceLocation id, Category category, Layout layout, int contentWidth, int contentHeight,
			List<DisplaySlot> content, int cookTime, float xp) {
		int inset = Card.BORDER + PADDING;
		boolean info = layout == Layout.GENERIC && (cookTime > 0 || xp > 0);
		boolean beside = !info && inset + contentWidth + PADDING + BUTTON + inset <= Card.WIDTH;
		int width = beside ? inset + contentWidth + PADDING + BUTTON + inset : inset + contentWidth + inset;
		int height = beside
				? inset + contentHeight + PADDING + Card.BOTTOM_BORDER
				: inset + contentHeight + PADDING + BUTTON + PADDING + Card.BOTTOM_BORDER;
		int buttonX = beside ? inset + contentWidth + PADDING : width - Card.BORDER - 1 - BUTTON;
		int buttonY = beside ? inset + (contentHeight - BUTTON) / 2 : height - Card.BOTTOM_BORDER - 1 - BUTTON;
		List<DisplaySlot> slots = content.stream()
				.map(slot -> new DisplaySlot(slot.x() + inset, slot.y() + inset, slot.size(), slot.output(), slot.stacks(), slot.shown(),
						slot.searched(), slot.chance()))
				.toList();
		return new Display(category, layout, Card.PLAIN, width, height, buttonX, buttonY, id, slots, false, cookTime, xp, null);
	}

	private static void addInput(List<DisplaySlot> slots, int x, int y, int size, Ingredient ingredient) {
		if (ingredient.isEmpty()) return;
		slots.add(new DisplaySlot(x, y, size, false, List.of(ingredient.getItems())));
	}

	private static DisplaySlot output(int x, int y, int size, ItemStack result) {
		return new DisplaySlot(x, y, size, true, List.of(result.copy()));
	}
}
