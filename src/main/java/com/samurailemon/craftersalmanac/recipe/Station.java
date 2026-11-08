package com.samurailemon.craftersalmanac.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * What a recipe type's file says about showing its recipes. Read from resources, so mods can ship them in their jar
 * and resource packs can add or replace them: {@code assets/<any namespace>/craftersalmanac/stations/<mod>/<type>.json}
 * for the recipe type {@code <mod>:<type>}. Files in {@code config/craftersalmanac/stations/<mod>/<type>.json} win over
 * those, for trying a layout out (F3+T reloads them). Every field is optional:
 * <ul>
 * <li>{@code texture}, {@code texture_size}, {@code region}, {@code inputs}, {@code output}: draw the recipes on a piece
 * of the machine's art, with the ingredients and result at those positions (where the 16x16 item goes, from the
 * region's top-left). {@code outputs} places further outputs, {@code slots} places other fields of the recipe (a
 * container, a tool), {@code info} moves the time and XP text, and {@code animations} fill parts of the art over the
 * recipe's time, like a progress arrow.</li>
 * <li>{@code fields}: the recipe's own names for things RecipeData looks for, e.g. {@code {"time": ["processing_ticks"]}}.</li>
 * <li>{@code frame}: the screen texture to frame the card with, when it isn't found by name.</li>
 * <li>{@code icon}: the machine, for the tab.</li>
 * <li>{@code hidden}: true to leave the type out of the almanac.</li>
 * </ul>
 * See COMPATIBILITY.md for the full format.
 */
public record Station(ResourceLocation icon, ResourceLocation texture, int textureWidth, int textureHeight, int u, int v,
		int width, int height, List<int[]> inputs, int[] output, List<int[]> outputs, Map<String, int[]> slots, int[] info,
		List<Animation> animations, Map<String, List<String>> fields, ResourceLocation frame, boolean hidden) {
	/**
	 * A piece of art drawn over the region, filling over the recipe's time from one side ({@code right}: left to right,
	 * {@code left}, {@code up}: bottom to top, {@code down}), or shown as it is with {@code none}.
	 */
	public record Animation(ResourceLocation texture, int textureWidth, int textureHeight, int u, int v, int width, int height,
			int x, int y, String fill) {
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FOLDER = "craftersalmanac/stations";
	private static final Path CONFIG_FOLDER = FabricLoader.getInstance().getConfigDir().resolve(FOLDER);
	private static Map<ResourceLocation, Station> stations = Map.of();

	/** The file for a recipe type, or null if it has none. */
	public static Station of(ResourceLocation recipeType) {
		return recipeType == null ? null : stations.get(recipeType);
	}

	/** Where Developer Mode writes starter files, and where files that win over the resources' go. */
	public static Path configFile(ResourceLocation recipeType) {
		return CONFIG_FOLDER.resolve(recipeType.getNamespace()).resolve(recipeType.getPath() + ".json");
	}

	/** Whether the file draws recipes on the machine's art, rather than only adjusting the generic card. */
	public boolean hasArt() {
		return texture != null;
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
				ResourceLocation type = typeOf(entry.getKey().getPath().substring(FOLDER.length() + 1));
				if (type == null) continue;
				try (Reader reader = entry.getValue().openAsReader()) {
					loaded.put(type, parse(JsonParser.parseReader(reader).getAsJsonObject()));
				} catch (Exception e) {
					LOGGER.warn("Couldn't read recipe station file {}", entry.getKey(), e);
				}
			}
			loadConfigFolder(loaded);
			stations = Map.copyOf(loaded);
			RecipeIndex.invalidate();
		}
	};

	private static void loadConfigFolder(Map<ResourceLocation, Station> loaded) {
		if (!Files.isDirectory(CONFIG_FOLDER)) return;
		try (Stream<Path> files = Files.walk(CONFIG_FOLDER)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				ResourceLocation type = typeOf(CONFIG_FOLDER.relativize(file).toString().replace('\\', '/'));
				if (type == null) continue;
				try (Reader reader = Files.newBufferedReader(file)) {
					loaded.put(type, parse(JsonParser.parseReader(reader).getAsJsonObject()));
				} catch (Exception e) {
					LOGGER.warn("Couldn't read recipe station file {}", file, e);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Couldn't list {}", CONFIG_FOLDER, e);
		}
	}

	/** "farmersdelight/cooking.json" is the recipe type farmersdelight:cooking. */
	private static ResourceLocation typeOf(String file) {
		String name = file.substring(0, file.length() - ".json".length());
		int slash = name.indexOf('/');
		return slash < 0 ? null : ResourceLocation.tryBuild(name.substring(0, slash), name.substring(slash + 1));
	}

	private static Station parse(JsonObject json) {
		int[] size = json.has("texture_size") ? point(json.get("texture_size")) : new int[]{256, 256};
		int[] region = json.has("region") ? ints(json.getAsJsonArray("region")) : new int[4];
		Map<String, int[]> slots = new HashMap<>();
		if (json.has("slots")) {
			for (Map.Entry<String, JsonElement> slot : json.getAsJsonObject("slots").entrySet()) {
				slots.put(slot.getKey(), point(slot.getValue()));
			}
		}
		Map<String, List<String>> fields = new HashMap<>();
		if (json.has("fields")) {
			for (Map.Entry<String, JsonElement> field : json.getAsJsonObject("fields").entrySet()) {
				List<String> names = new ArrayList<>();
				if (field.getValue().isJsonArray()) {
					field.getValue().getAsJsonArray().forEach(name -> names.add(name.getAsString()));
				} else {
					names.add(field.getValue().getAsString());
				}
				fields.put(field.getKey(), List.copyOf(names));
			}
		}
		List<Animation> animations = new ArrayList<>();
		if (json.has("animations")) {
			for (JsonElement element : json.getAsJsonArray("animations")) {
				JsonObject animation = element.getAsJsonObject();
				int[] animationSize = animation.has("texture_size") ? point(animation.get("texture_size")) : size;
				int[] uv = point(animation.get("uv"));
				int[] pieceSize = point(animation.get("size"));
				int[] at = point(animation.get("at"));
				animations.add(new Animation(ResourceLocation.parse(animation.get("texture").getAsString()), animationSize[0], animationSize[1],
						uv[0], uv[1], pieceSize[0], pieceSize[1], at[0], at[1], animation.has("fill") ? animation.get("fill").getAsString() : "none"));
			}
		}
		return new Station(
				location(json, "icon"), location(json, "texture"), size[0], size[1], region[0], region[1], region[2], region[3],
				points(json, "inputs"), json.has("output") ? point(json.get("output")) : new int[2], points(json, "outputs"),
				Map.copyOf(slots), json.has("info") ? point(json.get("info")) : null, List.copyOf(animations), Map.copyOf(fields),
				location(json, "frame"), json.has("hidden") && json.get("hidden").getAsBoolean());
	}

	private static ResourceLocation location(JsonObject json, String key) {
		return json.has(key) ? ResourceLocation.parse(json.get(key).getAsString()) : null;
	}

	private static List<int[]> points(JsonObject json, String key) {
		List<int[]> points = new ArrayList<>();
		if (json.has(key)) {
			for (JsonElement point : json.getAsJsonArray(key)) {
				points.add(point(point));
			}
		}
		return List.copyOf(points);
	}

	private static int[] point(JsonElement element) {
		return ints(element.getAsJsonArray());
	}

	private static int[] ints(JsonArray array) {
		int[] values = new int[array.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = array.get(i).getAsInt();
		}
		return values;
	}
}
