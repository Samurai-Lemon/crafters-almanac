package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Panel state that survives closing and reopening inventories. */
public final class ViewerState {
	public static String search = "";
	public static int browsePage;
	/**
	 * Set when one of our widgets handles a mouse press. The container screen would otherwise treat the
	 * matching release as a click outside its slots and throw the item the player is carrying.
	 */
	public static boolean swallowNextRelease;
	/** Null while browsing the item list. */
	public static RecipeView view;
	/** The materials calculator, shown on top of the current view. Back closes it. */
	public static Materials materials;
	private static final Deque<RecipeView> history = new ArrayDeque<>();

	private ViewerState() {
	}

	/** Opens the recipes (or uses) of a stack. Returns false if there are none. */
	public static boolean show(ItemStack stack, boolean uses) {
		if (stack.isEmpty()) return false;
		List<Display> found = uses ? RecipeIndex.usesOf(stack) : RecipeIndex.recipesFor(stack);
		if (found.isEmpty()) return false;
		// Open the panel first: opening it resets to the item list.
		setHidden(false);
		if (view != null) history.push(view);
		view = new RecipeView(stack.copyWithCount(1), uses, found);
		materials = null;
		return true;
	}

	/** Opens the materials calculator for a recipe. */
	public static void showMaterials(Display display, Map<Item, Integer> inventory) {
		materials = new Materials(display, inventory);
	}

	/** Goes back one recipe view, or to the item list when there is no history. */
	public static void back() {
		if (materials != null) {
			materials = null;
			return;
		}
		view = history.poll();
		// Favorites may have changed since we left them.
		if (view != null && view.favorites) refreshFavorites();
	}

	/** Opens the favorites tab. Like the item list, it starts a fresh history. */
	public static void showFavorites() {
		materials = null;
		history.clear();
		view = RecipeView.favorites(Favorites.displays());
	}

	/** Rebuilds the favorites view after a change, staying on the same tab and page where possible. */
	public static void refreshFavorites() {
		RecipeView old = view;
		if (old == null || !old.favorites) return;
		view = RecipeView.favorites(Favorites.displays());
		view.page = old.page;
	}

	public static boolean isHidden() {
		return !ViewerConfig.get().panelOpen;
	}

	/** The panel is open and not pushed aside by the vanilla recipe book, which uses the same spot. */
	public static boolean isShowing() {
		return !isHidden() && !vanillaBookOpen();
	}

	public static void toggle() {
		setHidden(isShowing());
	}

	/** Opening the panel closes the vanilla recipe book, since both sit left of the container. */
	private static void setHidden(boolean hidden) {
		boolean changed = hidden != isHidden();
		if (changed) {
			ViewerConfig.get().panelOpen = !hidden;
			ViewerConfig.save();
			reset();
		}
		boolean closedVanilla = !hidden && closeVanillaBook();
		if (changed || closedVanilla) relayout();
	}

	private static boolean vanillaBookOpen() {
		return Minecraft.getInstance().screen instanceof RecipeUpdateListener listener && listener.getRecipeBookComponent().isVisible();
	}

	private static boolean closeVanillaBook() {
		if (!vanillaBookOpen()) return false;
		((RecipeUpdateListener) Minecraft.getInstance().screen).getRecipeBookComponent().toggleVisibility();
		return true;
	}

	/**
	 * Re-inits the open container so it moves to make room for the panel (or recenters). Deferred because this
	 * runs inside the screen's own input handling, and re-init replaces its widgets.
	 */
	public static void relayout() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) return;
		minecraft.tell(() -> {
			if (minecraft.screen == screen) screen.resize(minecraft, screen.width, screen.height);
		});
	}

	public static boolean consumeRelease() {
		boolean swallowed = swallowNextRelease;
		swallowNextRelease = false;
		return swallowed;
	}

	public static void browse() {
		materials = null;
		view = null;
		history.clear();
	}

	/** Back to the first page of the item list. Done when a screen opens and when the panel is opened or closed. */
	public static void reset() {
		browse();
		browsePage = 0;
	}
}
