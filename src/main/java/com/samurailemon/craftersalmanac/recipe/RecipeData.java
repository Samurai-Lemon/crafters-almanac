package com.samurailemon.craftersalmanac.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * What a modded recipe holds beyond its ingredients, read from the recipe the way its mod saves it (its serializer's
 * codec, written out as JSON). Mods name things differently, so this looks for the usual names: several outputs with
 * chances (a crusher's bonus dust, a cutting board's extra drops), cook or processing time, XP, a tool or container
 * the recipe needs, and fluids (shown as their bucket). Mods whose recipes say nothing like this just get their
 * ingredients and result, as before.
 */
public final class RecipeData {
	/** One output, and how likely it is (1 = always). */
	public record Output(ItemStack stack, float chance) {
	}

	private static final List<String> OUTPUT_KEYS = List.of("result", "results", "output", "outputs");
	private static final List<String> TIME_KEYS = List.of("cookingtime", "cooking_time", "cookTime", "time", "processingtime",
			"processing_time", "processingTime", "duration");
	private static final List<String> XP_KEYS = List.of("experience", "xp");
	/** Other things the recipe needs besides its ingredients. */
	private static final List<String> EXTRA_INPUT_KEYS = List.of("tool", "container");
	private static final List<String> FLUID_INPUT_KEYS = List.of("fluidInput", "fluid_input", "inputFluid", "input_fluid");
	private static final List<String> FLUID_OUTPUT_KEYS = List.of("fluidOutputs", "fluidOutput", "fluid_outputs", "fluid_output",
			"outputFluid", "output_fluid");

	private final JsonObject json;
	private final RegistryOps<JsonElement> ops;
	/** Worked out the first time they're asked for. */
	private List<Output> outputs;

	private RecipeData(JsonObject json, RegistryOps<JsonElement> ops) {
		this.json = json;
		this.ops = ops;
	}

	/** Null if the recipe can't be written out (some mods' recipes only go one way). */
	@SuppressWarnings("unchecked")
	public static RecipeData read(Recipe<?> recipe, HolderLookup.Provider registries) {
		try {
			RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
			MapCodec<Recipe<?>> codec = (MapCodec<Recipe<?>>) recipe.getSerializer().codec();
			return codec.codec().encodeStart(ops, recipe).result()
					.filter(JsonElement::isJsonObject)
					.map(json -> new RecipeData(json.getAsJsonObject(), ops))
					.orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Every output with its chance, then fluid outputs as buckets. */
	public List<Output> outputs() {
		if (outputs == null) outputs = List.copyOf(readOutputs());
		return outputs;
	}

	private List<Output> readOutputs() {
		List<Output> found = new ArrayList<>();
		for (String key : OUTPUT_KEYS) {
			for (JsonElement element : elements(key)) {
				Output output = output(element);
				if (output != null) found.add(output);
			}
		}
		for (String key : FLUID_OUTPUT_KEYS) {
			for (JsonElement element : elements(key)) {
				ItemStack bucket = bucket(element);
				if (!bucket.isEmpty()) found.add(new Output(bucket, 1.0F));
			}
		}
		return found;
	}

	/** Tools, containers and fluids (as buckets) the recipe needs as well as its ingredients. */
	public List<List<ItemStack>> extraInputs() {
		List<List<ItemStack>> inputs = new ArrayList<>();
		for (String key : EXTRA_INPUT_KEYS) {
			List<ItemStack> stacks = stacks(key);
			if (!stacks.isEmpty()) inputs.add(stacks);
		}
		for (String key : FLUID_INPUT_KEYS) {
			for (JsonElement element : elements(key)) {
				ItemStack bucket = bucket(element);
				if (!bucket.isEmpty()) inputs.add(List.of(bucket));
			}
		}
		return inputs;
	}

	/** The items a field holds, read as an ingredient (a tool: "any knife") or an item stack (a container: a bowl). */
	public List<ItemStack> stacks(String key) {
		JsonElement element = json.get(key);
		if (element == null) return List.of();
		List<ItemStack> fromIngredient = Ingredient.CODEC.parse(ops, element).result()
				.map(ingredient -> List.of(ingredient.getItems()))
				.orElse(List.of());
		if (!fromIngredient.isEmpty()) return fromIngredient;
		return ItemStack.CODEC.parse(ops, element).result().map(List::of).orElse(List.of());
	}

	/** In ticks, or 0 if the recipe doesn't say. */
	public int time() {
		return (int) number(TIME_KEYS);
	}

	public float xp() {
		return number(XP_KEYS);
	}

	private float number(List<String> keys) {
		for (String key : keys) {
			JsonElement element = json.get(key);
			if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return element.getAsFloat();
		}
		return 0;
	}

	/** A field as a list: an array's elements, or the one value. */
	private List<JsonElement> elements(String key) {
		JsonElement element = json.get(key);
		if (element == null) return List.of();
		if (element instanceof JsonArray array) return array.asList();
		return List.of(element);
	}

	/** An item stack, or {"item": stack, "chance": 0.5} as in Farmer's Delight's cutting board. */
	private Output output(JsonElement element) {
		float chance = 1.0F;
		JsonElement stack = element;
		if (element instanceof JsonObject object) {
			if (object.has("chance") && object.get("chance").isJsonPrimitive()) chance = object.get("chance").getAsFloat();
			if (object.get("item") instanceof JsonObject item) stack = item;
		}
		float odds = chance;
		return ItemStack.CODEC.parse(ops, stack).result().map(parsed -> new Output(parsed, odds)).orElse(null);
	}

	/** A fluid ({"fluid": "minecraft:water"} or a "#tag") as its bucket, or empty if it has none. */
	private static ItemStack bucket(JsonElement element) {
		JsonElement name = element instanceof JsonObject object ? firstOf(object, "fluid", "id", "FluidName") : element;
		if (name == null || !name.isJsonPrimitive()) return ItemStack.EMPTY;
		String text = name.getAsString();
		Fluid fluid = null;
		if (text.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(text.substring(1));
			if (tag == null) return ItemStack.EMPTY;
			for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(TagKey.create(Registries.FLUID, tag))) {
				fluid = holder.value();
				if (fluid.getBucket() != Items.AIR) break;
			}
		} else {
			ResourceLocation id = ResourceLocation.tryParse(text);
			if (id != null) fluid = BuiltInRegistries.FLUID.get(id);
		}
		Item bucket = fluid == null ? Items.AIR : fluid.getBucket();
		return bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
	}

	private static JsonElement firstOf(JsonObject object, String... keys) {
		for (String key : keys) {
			if (object.has(key)) return object.get(key);
		}
		return null;
	}
}
