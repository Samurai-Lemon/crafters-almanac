package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Favorite recipes, saved by recipe ID in the config file. Each world (and each server) has its own. */
public final class Favorites {
	private Favorites() {
	}

	public static boolean contains(Display display) {
		return current(false).contains(display.id().toString());
	}

	public static void toggle(Display display) {
		List<String> favorites = current(true);
		String id = display.id().toString();
		if (!favorites.remove(id)) favorites.add(id);
		if (favorites.isEmpty()) ViewerConfig.get().worldFavorites.remove(worldKey());
		ViewerConfig.save();
		ViewerState.refreshFavorites();
	}

	/** Favorites in the order they were added. Recipes that no longer exist (removed mod) are skipped. */
	public static List<Display> displays() {
		Map<String, Display> byId = new HashMap<>();
		for (Display display : RecipeIndex.all()) {
			byId.put(display.id().toString(), display);
		}
		List<Display> result = new ArrayList<>();
		for (String id : current(false)) {
			Display display = byId.get(id);
			if (display != null) result.add(display);
		}
		return result;
	}

	/** This world's favorites; {@code create} adds an empty list to the config for a world that has none yet. */
	private static List<String> current(boolean create) {
		Map<String, List<String>> all = ViewerConfig.get().worldFavorites;
		String key = worldKey();
		return create ? all.computeIfAbsent(key, k -> new ArrayList<>()) : all.getOrDefault(key, List.of());
	}

	/** Singleplayer worlds by their save folder (unique, unlike their names), servers by their address. */
	private static String worldKey() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return "world:" + folder;
		}
		ServerData data = minecraft.getCurrentServer();
		return data != null ? "server:" + data.ip : "unknown";
	}
}
