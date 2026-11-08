package com.samurailemon.craftersalmanac.recipe;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A recipe tab: one per recipe type, plus "virtual" tabs for things that aren't recipes in the game's recipe list
 * (brewing, fuel). Vanilla's come first in a fixed order, then the virtual ones, then modded ones by ID.
 */
public final class Category implements Comparable<Category> {
	public static final Category CRAFTING = vanilla(RecipeType.CRAFTING, Items.CRAFTING_TABLE, 0);
	public static final Category SMELTING = vanilla(RecipeType.SMELTING, Items.FURNACE, 1);
	public static final Category BLASTING = vanilla(RecipeType.BLASTING, Items.BLAST_FURNACE, 2);
	public static final Category SMOKING = vanilla(RecipeType.SMOKING, Items.SMOKER, 3);
	public static final Category CAMPFIRE = vanilla(RecipeType.CAMPFIRE_COOKING, Items.CAMPFIRE, 4);
	public static final Category STONECUTTING = vanilla(RecipeType.STONECUTTING, Items.STONECUTTER, 5);
	public static final Category SMITHING = vanilla(RecipeType.SMITHING, Items.SMITHING_TABLE, 6);
	private static final List<Category> VANILLA = List.of(CRAFTING, SMELTING, BLASTING, SMOKING, CAMPFIRE, STONECUTTING, SMITHING);
	public static final Category BREWING = virtual("brewing", new ItemStack(Items.BREWING_STAND), new ItemStack(Items.BREWING_STAND).getHoverName(), 7);
	public static final Category FUEL = virtual("fuel", new ItemStack(Items.COAL), Component.translatable("craftersalmanac.category.fuel"), 8);
	private static final Map<RecipeType<?>, Category> MODDED = new HashMap<>();

	/** Null for virtual tabs. */
	public final RecipeType<?> type;
	public final ItemStack icon;
	private final Component title;
	private final int order;
	private final String id;

	private Category(RecipeType<?> type, ItemStack icon, Component title, int order) {
		this(type, icon, title, order, idOf(type));
	}

	private Category(RecipeType<?> type, ItemStack icon, Component title, int order, String id) {
		this.type = type;
		this.icon = icon;
		this.title = title;
		this.order = order;
		this.id = id;
	}

	private static String idOf(RecipeType<?> type) {
		ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		return key == null ? type.toString() : key.toString();
	}

	private static Category virtual(String id, ItemStack icon, Component title, int order) {
		return new Category(null, icon, title, order, "craftersalmanac:" + id);
	}

	/** Brewing and fuel: not in the game's recipe list, so they can't be filled into a grid and have no recipe ID. */
	public boolean isVirtual() {
		return type == null;
	}

	/** Reuses the workstation's name ("Crafting Table", "Furnace"...), so it is localized for free. */
	private static Category vanilla(RecipeType<?> type, ItemLike icon, int order) {
		ItemStack stack = new ItemStack(icon);
		return new Category(type, stack, stack.getHoverName(), order);
	}

	public Component title() {
		return title;
	}

	/** The recipe type's ID, or null for virtual tabs. */
	public ResourceLocation typeId() {
		return type == null ? null : BuiltInRegistries.RECIPE_TYPE.getKey(type);
	}

	/** The tab for a recipe's type, creating one the first time a modded type is seen. */
	public static Category of(Recipe<?> recipe, ItemStack result) {
		RecipeType<?> type = recipe.getType();
		for (Category category : VANILLA) {
			if (category.type == type) return category;
		}
		return MODDED.computeIfAbsent(type, t -> modded(t, recipe, result));
	}

	private static Category modded(RecipeType<?> type, Recipe<?> recipe, ItemStack result) {
		ItemStack icon = moddedIcon(type, recipe, result);
		return new Category(type, icon, moddedTitle(type, icon), Integer.MAX_VALUE);
	}

	/**
	 * The machine: a station layout's icon, else the recipe's toast symbol (mods usually set it to their machine; the
	 * default is a crafting table, which would be misleading), else an item named after the type ("pulverizer_block"
	 * for "pulverizer"), else what the recipe makes.
	 */
	private static ItemStack moddedIcon(RecipeType<?> type, Recipe<?> recipe, ItemStack result) {
		ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		Station station = Station.of(key);
		if (station != null && station.icon() != null && BuiltInRegistries.ITEM.containsKey(station.icon())) {
			return new ItemStack(BuiltInRegistries.ITEM.get(station.icon()));
		}
		ItemStack toast = recipe.getToastSymbol();
		if (!toast.isEmpty() && !toast.is(Items.CRAFTING_TABLE)) return toast;
		if (key != null) {
			for (String suffix : List.of("", "_block")) {
				ResourceLocation machine = key.withSuffix(suffix);
				if (BuiltInRegistries.ITEM.containsKey(machine)) return new ItemStack(BuiltInRegistries.ITEM.get(machine));
			}
		}
		return result.copyWithCount(1);
	}

	/** A translation the mod provides if any, else the machine's name, else "Crushing (Create)" from the type ID. */
	private static Component moddedTitle(RecipeType<?> type, ItemStack icon) {
		ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		if (key == null) return icon.getHoverName();
		for (String translation : List.of("recipe_type." + key.getNamespace() + "." + key.getPath(), "emi.category." + key.getNamespace() + "." + key.getPath())) {
			if (Language.getInstance().has(translation)) return Component.translatable(translation);
		}
		String modName = FabricLoader.getInstance().getModContainer(key.getNamespace())
				.map(mod -> mod.getMetadata().getName())
				.orElse(key.getNamespace());
		return Component.literal(humanize(key.getPath()) + " (" + modName + ")");
	}

	private static String humanize(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder result = new StringBuilder();
		for (String word : last.split("_")) {
			if (word.isEmpty()) continue;
			if (!result.isEmpty()) result.append(' ');
			result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return result.toString();
	}

	@Override
	public int compareTo(Category other) {
		int byOrder = Integer.compare(order, other.order);
		return byOrder != 0 ? byOrder : id.compareTo(other.id);
	}
}
