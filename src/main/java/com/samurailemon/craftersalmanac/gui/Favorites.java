package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Favorite recipes, saved by recipe ID in the config file. */
public final class Favorites {
	private Favorites() {
	}

	public static boolean contains(Display display) {
		return ViewerConfig.get().favorites.contains(display.id().toString());
	}

	public static void toggle(Display display) {
		List<String> favorites = ViewerConfig.get().favorites;
		String id = display.id().toString();
		if (!favorites.remove(id)) favorites.add(id);
		ViewerConfig.save();
		ViewerState.refreshFavorites();
	}

	/** Favorites in the order they were added. Recipes that no longer exist (removed mod, other server) are skipped. */
	public static List<Display> displays() {
		Map<String, Display> byId = new HashMap<>();
		for (Display display : RecipeIndex.all()) {
			byId.put(display.id().toString(), display);
		}
		List<Display> result = new ArrayList<>();
		for (String id : ViewerConfig.get().favorites) {
			Display display = byId.get(id);
			if (display != null) result.add(display);
		}
		return result;
	}
}
