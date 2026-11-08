package com.samurailemon.craftersalmanac.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a modded machine's recipes are drawn: a piece of the mod's own GUI art as the card, and where the ingredients,
 * the result and any extra fields (a container, a tool) go on it. Read from resource files, so new machines need no
 * code, and resource packs or other mods can add their own:
 * {@code assets/<any namespace>/craftersalmanac/stations/<mod>/<recipe type>.json} for the recipe type
 * {@code <mod>:<recipe type>}. Positions are where the 16x16 item goes, measured from the region's top-left corner.
 * <pre>{@code
 * {
 *   "icon": "farmersdelight:cooking_pot",
 *   "texture": "farmersdelight:textures/gui/jei/cooking_pot.png",
 *   "texture_size": [256, 256],
 *   "region": [0, 0, 116, 56],
 *   "inputs": [[1, 1], [19, 1], [37, 1], [1, 19], [19, 19], [37, 19]],
 *   "output": [95, 10],
 *   "slots": { "container": [63, 39] }
 * }
 * }</pre>
 * The texture is read from the mod while the game runs, so none of its art is copied into this mod.
 */
public record Station(ResourceLocation icon, ResourceLocation texture, int textureWidth, int textureHeight, int u, int v,
		int width, int height, List<int[]> inputs, int[] output, Map<String, int[]> slots) {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FOLDER = "craftersalmanac/stations";
	private static Map<ResourceLocation, Station> stations = Map.of();

	/** The layout for a recipe type, or null to use the generic card. */
	public static Station of(ResourceLocation recipeType) {
		return recipeType == null ? null : stations.get(recipeType);
	}

	public static final SimpleSynchronousResourceReloadListener LOADER = new SimpleSynchronousResourceReloadListener() {
		@Override
		public ResourceLocation getFabricId() {
			return ResourceLocation.fromNamespaceAndPath("craftersalmanac", "stations");
		}

		@Override
		public void onResourceManagerReload(ResourceManager manager) {
			Map<ResourceLocation, Station> loaded = new HashMap<>();
			for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
				// craftersalmanac/stations/farmersdelight/cooking.json is the recipe type farmersdelight:cooking.
				String path = entry.getKey().getPath();
				String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
				int slash = name.indexOf('/');
				ResourceLocation type = slash < 0 ? null : ResourceLocation.tryBuild(name.substring(0, slash), name.substring(slash + 1));
				if (type == null) continue;
				try (Reader reader = entry.getValue().openAsReader()) {
					loaded.put(type, parse(JsonParser.parseReader(reader).getAsJsonObject()));
				} catch (Exception e) {
					LOGGER.warn("Couldn't read recipe station layout {}", entry.getKey(), e);
				}
			}
			stations = Map.copyOf(loaded);
			RecipeIndex.invalidate();
		}
	};

	private static Station parse(JsonObject json) {
		int[] size = json.has("texture_size") ? point(json.get("texture_size")) : new int[]{256, 256};
		JsonArray region = json.getAsJsonArray("region");
		List<int[]> inputs = new ArrayList<>();
		for (JsonElement input : json.getAsJsonArray("inputs")) {
			inputs.add(point(input));
		}
		Map<String, int[]> slots = new HashMap<>();
		if (json.has("slots")) {
			for (Map.Entry<String, JsonElement> slot : json.getAsJsonObject("slots").entrySet()) {
				slots.put(slot.getKey(), point(slot.getValue()));
			}
		}
		return new Station(
				json.has("icon") ? ResourceLocation.parse(json.get("icon").getAsString()) : null,
				ResourceLocation.parse(json.get("texture").getAsString()), size[0], size[1],
				region.get(0).getAsInt(), region.get(1).getAsInt(), region.get(2).getAsInt(), region.get(3).getAsInt(),
				List.copyOf(inputs), point(json.get("output")), Map.copyOf(slots));
	}

	private static int[] point(JsonElement element) {
		JsonArray array = element.getAsJsonArray();
		return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
	}
}
