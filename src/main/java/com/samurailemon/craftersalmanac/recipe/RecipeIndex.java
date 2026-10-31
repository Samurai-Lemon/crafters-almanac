package com.samurailemon.craftersalmanac.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * All recipes the client knows about. In 1.21.1 the server syncs the full recipe list to the client,
 * so we read it straight from the level's RecipeManager and rebuild whenever it changes.
 */
public final class RecipeIndex {
	private static final Logger LOGGER = LogUtils.getLogger();

	/** The manager's recipe collection, which Minecraft replaces (not edits) when recipes change, e.g. on /reload. */
	private static Collection<RecipeHolder<?>> cachedRecipes;
	private static List<Display> displays = List.of();

	private RecipeIndex() {
	}

	public static List<Display> recipesFor(ItemStack stack) {
		return find(display -> display.produces(stack));
	}

	public static List<Display> usesOf(ItemStack stack) {
		return find(display -> display.consumes(stack));
	}

	private static List<Display> find(Predicate<Display> filter) {
		List<Display> found = new ArrayList<>();
		for (Display display : all()) {
			if (filter.test(display)) found.add(display);
		}
		return found;
	}

	/** Same list instance until the recipes change, so callers can cache against it. */
	public static List<Display> all() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return List.of();

		Collection<RecipeHolder<?>> recipes = level.getRecipeManager().getRecipes();
		if (recipes != cachedRecipes) {
			cachedRecipes = recipes;
			displays = build(recipes, level);
		}
		return displays;
	}

	private static List<Display> build(Collection<RecipeHolder<?>> recipes, ClientLevel level) {
		List<Display> built = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			try {
				Display display = DisplayFactory.create(holder, level.registryAccess());
				if (display != null) built.add(display);
			} catch (RuntimeException | LinkageError e) {
				// Modded recipes can do surprising things (or come from a mod built for another version); skip them
				// rather than crash the screen.
				LOGGER.debug("Skipping recipe {}", holder.id(), e);
			}
		}
		try {
			built.addAll(ExtraRecipes.create(level));
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("Couldn't list brewing and fuel recipes", e);
		}
		built.sort(Comparator.comparing(Display::category).thenComparing(display -> display.id().toString()));
		return List.copyOf(built);
	}
}
