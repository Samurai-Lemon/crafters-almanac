package com.samurailemon.craftersalmanac.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.samurailemon.craftersalmanac.recipe.Category;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import com.samurailemon.craftersalmanac.recipe.RecipeData;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import com.samurailemon.craftersalmanac.recipe.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Developer Mode's helper for mod and resource pack makers: Ctrl-clicking a recipe tab writes a starter station file
 * for its recipe type to {@code config/craftersalmanac/stations/}, filled in with what the almanac found: the machine,
 * the screen texture it frames the cards with, where the generic card puts the items, and every field the recipe has.
 * Edit it, press F3+T to see the change, and copy it into the mod or pack when it's right (see COMPATIBILITY.md).
 */
public final class StationTemplate {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private StationTemplate() {
	}

	public static void write(Category category) {
		ResourceLocation type = category.typeId();
		if (type == null) return;
		Path file = Station.configFile(type);
		Path shown = Minecraft.getInstance().gameDirectory.toPath().relativize(file);
		if (Files.exists(file)) {
			toast("craftersalmanac.dev.template_exists", shown);
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(template(category), writer);
			}
			toast("craftersalmanac.dev.template_written", shown);
		} catch (IOException e) {
			LOGGER.warn("Couldn't write {}", file, e);
		}
	}

	private static JsonObject template(Category category) {
		JsonObject json = new JsonObject();
		json.addProperty("_help", "Starter file from Crafter's Almanac's Developer Mode. Set the texture and region to your machine's art "
				+ "and move the items to its slots, then press F3+T to see it. Fields starting with _ are ignored. See COMPATIBILITY.md.");
		json.addProperty("icon", BuiltInRegistries.ITEM.getKey(category.icon.getItem()).toString());
		ResourceLocation screen = CardFrames.plainScreen(category);
		ResourceLocation type = category.typeId();
		json.addProperty("texture", screen != null ? screen.toString() : type.getNamespace() + ":textures/gui/" + type.getPath() + ".png");
		json.add("texture_size", array(256, 256));
		json.add("region", array(0, 0, 116, 56));

		Display sample = RecipeIndex.all().stream().filter(display -> display.category() == category).findFirst().orElse(null);
		if (sample != null) {
			// Where the generic card has the items, moved to start at the region's corner.
			int left = Integer.MAX_VALUE;
			int top = Integer.MAX_VALUE;
			for (DisplaySlot slot : sample.slots()) {
				if (!slot.shown()) continue;
				left = Math.min(left, itemX(slot));
				top = Math.min(top, itemY(slot));
			}
			JsonArray inputs = new JsonArray();
			JsonArray outputs = new JsonArray();
			for (DisplaySlot slot : sample.slots()) {
				if (!slot.shown()) continue;
				(slot.output() ? outputs : inputs).add(array(itemX(slot) - left, itemY(slot) - top));
			}
			json.add("inputs", inputs);
			if (!outputs.isEmpty()) json.add("output", outputs.remove(0));
			if (!outputs.isEmpty()) json.add("outputs", outputs);
			json.add("_recipe_fields", recipeFields(sample.id()));
		}
		JsonObject fields = new JsonObject();
		fields.add(RecipeData.TIME, new JsonArray());
		json.add("fields", fields);
		return json;
	}

	private static int itemX(DisplaySlot slot) {
		return slot.x() + (slot.size() - 16) / 2;
	}

	private static int itemY(DisplaySlot slot) {
		return slot.y() + (slot.size() - 16) / 2;
	}

	/** The sample recipe's field names, to fill "fields" from. */
	private static JsonArray recipeFields(ResourceLocation recipe) {
		JsonArray names = new JsonArray();
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return names;
		level.getRecipeManager().byKey(recipe)
				.map(holder -> RecipeData.read(holder.value(), level.registryAccess(), Map.of()))
				.ifPresent(data -> data.fieldNames().forEach(names::add));
		return names;
	}

	private static JsonArray array(int... values) {
		JsonArray array = new JsonArray();
		for (int value : values) {
			array.add(value);
		}
		return array;
	}

	private static void toast(String key, Path file) {
		Minecraft minecraft = Minecraft.getInstance();
		SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable(key), Component.literal(file.toString()));
	}
}
