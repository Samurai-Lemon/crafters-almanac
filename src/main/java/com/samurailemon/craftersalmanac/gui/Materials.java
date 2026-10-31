package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import com.samurailemon.craftersalmanac.recipe.Layout;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The raw materials calculator: everything needed to make some number of a recipe's result, broken down through
 * crafting recipes until it reaches things that aren't crafted (logs, ingots, glass...). Each grid slot uses one item
 * per craft, recipes make their real output count (one log makes 4 planks), and leftovers from crafting in batches
 * are used before crafting more. The rules for which recipe to break an item down with:
 * <ul>
 * <li>Only crafting-table recipes are expanded. Smelted items count as raw materials.</li>
 * <li>Packing pairs (iron ingot and iron block, ingot and nugget, wheat and hay bale) are only expanded towards the
 * item in the most pairs (the ingot), or on a tie towards the smaller unit. So a block costs 9 ingots and a nugget
 * costs a ninth of an ingot, but an ingot is never "made from a block".</li>
 * <li>Recolouring recipes ("dye any wool red") are skipped, and a few things nearly everyone gathers instead of
 * crafting (leather, white wool) count as raw.</li>
 * <li>When an item has several recipes: its own recipe first (minecraft:stick before stick_from_bamboo_item), then
 * the fewest steps from raw materials (sugar from sugar cane, not from honey bottles), then by name.</li>
 * <li>For "any of" ingredients (any planks), the option you have the most of is used.</li>
 * </ul>
 * These rules were checked against vanilla's full recipe list.
 */
public final class Materials {
	/** An item in the breakdown. For crafted steps, {@code count} is how many get made and {@code spare} what's left over. */
	public record Entry(ItemStack stack, int count, boolean crafted, int spare) {
	}

	private static final int MAX_AMOUNT = 999;
	/** Deeper than this is surely a loop the checks missed. */
	private static final int MAX_DEPTH = 16;
	/** Used when an item can't be reached without going in a loop. */
	private static final int UNREACHABLE = 99;
	/** Craftable, but almost always gathered instead (leather from rabbit hide, wool from string). */
	private static final Set<Item> GATHERED = Set.of(Items.LEATHER, Items.WHITE_WOOL);

	private static List<Display> cachedDisplays;
	private static Recipes recipes;

	private final Display root;
	public int amount = 1;
	public int page;
	private List<Entry> entries = List.of();

	public Materials(Display root, Map<Item, Integer> inventory) {
		this.root = root;
		recompute(inventory);
	}

	public ItemStack result() {
		return output(root);
	}

	public List<Entry> entries() {
		return entries;
	}

	public void setAmount(int amount, Map<Item, Integer> inventory) {
		this.amount = Mth.clamp(amount, 1, MAX_AMOUNT);
		recompute(inventory);
	}

	private void recompute(Map<Item, Integer> inventory) {
		Planner planner = new Planner(recipes(), inventory);
		ItemStack result = result();
		int crafts = Mth.positiveCeilDiv(amount, Math.max(1, result.getCount()));
		Set<Item> path = new HashSet<>();
		path.add(result.getItem());
		planner.craft(root, crafts, path, 0);

		List<Entry> list = new ArrayList<>();
		planner.raw.forEach((item, count) -> list.add(new Entry(planner.stacks.get(item), count, false, 0)));
		list.sort(Comparator.comparingInt(Entry::count).reversed());
		planner.made.forEach((item, count) ->
				list.add(new Entry(planner.stacks.get(item), count, true, planner.leftover.getOrDefault(item, 0))));
		entries = List.copyOf(list);
	}

	private static Recipes recipes() {
		List<Display> displays = RecipeIndex.all();
		if (displays != cachedDisplays) {
			recipes = new Recipes(displays);
			cachedDisplays = displays;
		}
		return recipes;
	}

	private static ItemStack output(Display display) {
		for (DisplaySlot slot : display.slots()) {
			if (slot.output() && !slot.stacks().isEmpty()) return slot.stacks().get(0);
		}
		return ItemStack.EMPTY;
	}

	private static List<DisplaySlot> inputs(Display display) {
		return display.slots().stream().filter(slot -> !slot.output() && !slot.stacks().isEmpty()).toList();
	}

	/** The crafting recipes and what's worked out from them once: packing pairs, item tags and step counts. */
	private static final class Recipes {
		private final Map<Item, List<Display>> producers = new HashMap<>();
		/** For each item, the items it packs into or out of. */
		private final Map<Item, Set<Item>> partners = new HashMap<>();
		private final Map<Item, Set<TagKey<Item>>> tags = new HashMap<>();
		private final Map<Item, Integer> depths = new HashMap<>();

		Recipes(List<Display> displays) {
			for (Display display : displays) {
				if (display.layout() != Layout.CRAFTING) continue;
				ItemStack output = output(display);
				if (!output.isEmpty()) producers.computeIfAbsent(output.getItem(), item -> new ArrayList<>()).add(display);
			}
			producers.forEach((item, list) -> {
				for (Display display : list) {
					Item input = singleInput(display);
					if (input == null) continue;
					for (Display back : producers.getOrDefault(input, List.of())) {
						if (singleInput(back) == item) partners.computeIfAbsent(item, i -> new HashSet<>()).add(input);
					}
				}
			});
		}

		/** The one item a recipe is made from, if every slot takes exactly that item (9 nuggets, 9 ingots...). */
		private static Item singleInput(Display display) {
			Item only = null;
			for (DisplaySlot slot : inputs(display)) {
				if (slot.stacks().size() != 1) return null;
				Item item = slot.stacks().get(0).getItem();
				if (only != null && only != item) return null;
				only = item;
			}
			return only;
		}

		boolean allowed(Display recipe, Item item, Set<Item> path) {
			if (GATHERED.contains(item) || recolors(recipe, item)) return false;
			for (DisplaySlot slot : inputs(recipe)) {
				boolean usable = slot.stacks().stream().anyMatch(option -> !path.contains(option.getItem()) && option.getItem() != item);
				if (!usable) return false;
			}
			Item input = singleInput(recipe);
			if (input != null && partners.getOrDefault(item, Set.of()).contains(input)) {
				int itemPairs = partners.getOrDefault(item, Set.of()).size();
				int inputPairs = partners.getOrDefault(input, Set.of()).size();
				boolean packs = inputs(recipe).size() > 1 && output(recipe).getCount() == 1;
				return inputPairs > itemPairs || inputPairs == itemPairs && packs;
			}
			return true;
		}

		/** "Dye any wool red": a slot offering several items that share a tag with the result. */
		private boolean recolors(Display recipe, Item item) {
			Set<TagKey<Item>> own = tags(item);
			for (DisplaySlot slot : inputs(recipe)) {
				long siblings = slot.stacks().stream().filter(option -> !Collections.disjoint(tags(option.getItem()), own)).count();
				if (siblings >= 2) return true;
			}
			return false;
		}

		private Set<TagKey<Item>> tags(Item item) {
			return tags.computeIfAbsent(item, i -> new ItemStack(i).getTags().collect(Collectors.toSet()));
		}

		/** Candidate recipes for an item, best first (see the class comment). */
		List<Display> ranked(Item item) {
			List<Display> list = new ArrayList<>(producers.getOrDefault(item, List.of()));
			Set<Item> visiting = Set.of(item);
			list.sort(Comparator.<Display, Boolean>comparing(display -> !isOwnRecipe(display, item))
					.thenComparingInt(display -> recipeDepth(display, visiting))
					.thenComparing(display -> display.id().toString()));
			return list;
		}

		private static boolean isOwnRecipe(Display display, Item item) {
			return display.id().equals(BuiltInRegistries.ITEM.getKey(item));
		}

		/** Fewest crafting steps from raw materials to the item; 0 means it's raw. */
		private int depth(Item item, Set<Item> visiting) {
			Integer known = depths.get(item);
			if (known != null) return known;
			if (visiting.contains(item)) return UNREACHABLE;
			Set<Item> deeper = new HashSet<>(visiting);
			deeper.add(item);
			int best = UNREACHABLE;
			boolean craftable = false;
			for (Display display : producers.getOrDefault(item, List.of())) {
				if (!allowed(display, item, visiting)) continue;
				craftable = true;
				best = Math.min(best, recipeDepth(display, deeper));
			}
			int result = craftable ? best : 0;
			depths.put(item, result);
			return result;
		}

		private int recipeDepth(Display display, Set<Item> visiting) {
			int deepest = 0;
			for (DisplaySlot slot : inputs(display)) {
				int easiest = UNREACHABLE;
				for (ItemStack option : slot.stacks()) {
					easiest = Math.min(easiest, depth(option.getItem(), visiting));
				}
				deepest = Math.max(deepest, easiest);
			}
			return 1 + deepest;
		}
	}

	private static final class Planner {
		private final Recipes recipes;
		private final Map<Item, Integer> inventory;
		/** Raw materials and crafted steps (how many made), in the order first needed. */
		final Map<Item, Integer> raw = new LinkedHashMap<>();
		final Map<Item, Integer> made = new LinkedHashMap<>();
		/** The stack to show for each item (keeps the exact option chosen). */
		final Map<Item, ItemStack> stacks = new HashMap<>();
		/** Extra made by crafting in batches, used up before crafting more. What's left at the end is spare. */
		final Map<Item, Integer> leftover = new HashMap<>();

		Planner(Recipes recipes, Map<Item, Integer> inventory) {
			this.recipes = recipes;
			this.inventory = inventory;
		}

		/** Runs a recipe {@code crafts} times: each input slot uses one item per craft. */
		void craft(Display recipe, int crafts, Set<Item> path, int depth) {
			for (DisplaySlot slot : inputs(recipe)) {
				need(choose(slot, path), crafts, path, depth + 1);
			}
		}

		private void need(ItemStack stack, int count, Set<Item> path, int depth) {
			Item item = stack.getItem();
			stacks.putIfAbsent(item, stack.copyWithCount(1));
			int fromLeftover = Math.min(count, leftover.getOrDefault(item, 0));
			leftover.merge(item, -fromLeftover, Integer::sum);
			count -= fromLeftover;
			if (count == 0) return;

			Display recipe = depth < MAX_DEPTH ? pick(item, path) : null;
			if (recipe == null) {
				raw.merge(item, count, Integer::sum);
				return;
			}
			int perCraft = Math.max(1, output(recipe).getCount());
			int crafts = Mth.positiveCeilDiv(count, perCraft);
			made.merge(item, crafts * perCraft, Integer::sum);
			leftover.merge(item, crafts * perCraft - count, Integer::sum);
			path.add(item);
			craft(recipe, crafts, path, depth);
			path.remove(item);
		}

		private Display pick(Item item, Set<Item> path) {
			for (Display candidate : recipes.ranked(item)) {
				if (recipes.allowed(candidate, item, path)) return candidate;
			}
			return null;
		}

		/** For "any of" slots: skip options that would loop, then prefer what the player has most of. */
		private ItemStack choose(DisplaySlot slot, Set<Item> path) {
			ItemStack best = null;
			int bestCount = -1;
			for (ItemStack option : slot.stacks()) {
				if (path.contains(option.getItem())) continue;
				int have = inventory.getOrDefault(option.getItem(), 0);
				if (have > bestCount) {
					best = option;
					bestCount = have;
				}
			}
			return best != null ? best : slot.stacks().get(0);
		}
	}
}
