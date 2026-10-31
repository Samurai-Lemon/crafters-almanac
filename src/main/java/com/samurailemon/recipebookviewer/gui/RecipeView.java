package com.samurailemon.recipebookviewer.gui;

import com.samurailemon.recipebookviewer.recipe.Category;
import com.samurailemon.recipebookviewer.recipe.Display;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The recipes (or uses) of one item, or the favorite recipes, split into category tabs and pages. */
public final class RecipeView {
	/** Vertical space for recipes below a one-line heading; a heading that wraps takes a line of it. */
	private static final int AREA_HEIGHT = 88;
	public static final int GAP = 4;

	/** Empty for the favorites view. */
	private final ItemStack focus;
	public final boolean uses;
	public final boolean favorites;
	public final List<Category> categories;
	private final Map<Category, List<List<Display>>> pages = new TreeMap<>();
	private final Map<Category, Integer> counts = new TreeMap<>();
	/** Null for the favorites view, which shows everything in one list, and when there are no recipes. */
	public Category selected;
	/** The favorites view's pages: all favorites in the order they were added, not split by category. */
	private final List<List<Display>> favoritePages;
	public int page;

	public RecipeView(ItemStack focus, boolean uses, List<Display> displays) {
		this(focus, uses, false, displays);
	}

	private RecipeView(ItemStack focus, boolean uses, boolean favorites, List<Display> displays) {
		this.focus = focus;
		this.uses = uses;
		this.favorites = favorites;
		int areaHeight = AREA_HEIGHT - (ViewerPanel.headingLines(Minecraft.getInstance().font, header()).size() - 1) * ViewerPanel.HEADING_LINE;
		this.favoritePages = favorites ? paginate(displays, areaHeight) : List.of();
		if (favorites) {
			this.categories = List.of();
			this.selected = null;
			return;
		}

		Map<Category, List<Display>> byCategory = new TreeMap<>();
		for (Display display : displays) {
			byCategory.computeIfAbsent(display.category(), category -> new ArrayList<>()).add(display);
		}
		byCategory.forEach((category, list) -> {
			pages.put(category, paginate(list, areaHeight));
			counts.put(category, list.size());
		});
		this.categories = List.copyOf(byCategory.keySet());
		this.selected = categories.isEmpty() ? null : categories.get(0);
	}

	public static RecipeView favorites(List<Display> displays) {
		return new RecipeView(ItemStack.EMPTY, false, true, displays);
	}

	public Component header() {
		if (favorites) return Component.translatable("recipebookviewer.header.favorites");
		return Component.translatable(uses ? "recipebookviewer.header.uses" : "recipebookviewer.header.recipes", focus.getHoverName());
	}

	/** Packs as many recipes onto each page as fit, so small ones (smelting) share a page and crafting gets one each. */
	private static List<List<Display>> paginate(List<Display> displays, int areaHeight) {
		List<List<Display>> result = new ArrayList<>();
		List<Display> current = new ArrayList<>();
		int used = 0;
		for (Display display : displays) {
			int height = display.height();
			if (!current.isEmpty() && used + GAP + height > areaHeight) {
				result.add(current);
				current = new ArrayList<>();
				used = 0;
			}
			used += current.isEmpty() ? height : GAP + height;
			current.add(display);
		}
		if (!current.isEmpty()) result.add(current);
		return result;
	}

	public void select(Category category) {
		selected = category;
		page = 0;
	}

	public int count(Category category) {
		return counts.getOrDefault(category, 0);
	}

	public boolean isEmpty() {
		return favorites ? favoritePages.isEmpty() : selected == null;
	}

	public int pageCount() {
		if (favorites) return Math.max(1, favoritePages.size());
		return selected == null ? 1 : pages.get(selected).size();
	}

	public List<Display> currentPage() {
		if (favorites) {
			if (favoritePages.isEmpty()) return List.of();
			page = Mth.clamp(page, 0, favoritePages.size() - 1);
			return favoritePages.get(page);
		}
		if (selected == null) return List.of();
		List<List<Display>> categoryPages = pages.get(selected);
		page = Mth.clamp(page, 0, categoryPages.size() - 1);
		return categoryPages.get(page);
	}
}
