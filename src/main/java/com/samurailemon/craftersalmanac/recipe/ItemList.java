package com.samurailemon.craftersalmanac.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every item in the creative search tab (which includes variants like potions and enchanted books), then any modded
 * item no creative tab lists, which mods sometimes leave out. Vanilla's unlisted items are operator blocks like
 * command blocks, so those stay hidden. Filtered by the search box.
 */
public final class ItemList {
	private static final Logger LOGGER = LogUtils.getLogger();

	/** An item with the text the search matches against, worked out once. */
	private record Entry(ItemStack stack, String name, String namespace, List<String> tags) {
		static Entry of(ItemStack stack) {
			return new Entry(
					stack,
					stack.getHoverName().getString().toLowerCase(Locale.ROOT),
					BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
					stack.getTags().map(tag -> tag.location().toString()).toList());
		}
	}

	private static ClientPacketListener cachedConnection;
	private static List<Entry> entries = List.of();
	/** Each item's first position in the list, built with it. */
	private static Map<Item, Integer> order = Map.of();
	private static String cachedQuery;
	private static List<ItemStack> cachedResult = List.of();

	private ItemList() {
	}

	/**
	 * Matches every space-separated word against the item name. Words starting with {@code @} match the mod id
	 * instead, and words starting with {@code #} match item tags, e.g. {@code @minecraft #logs} or {@code #c:ingots}.
	 */
	public static List<ItemStack> filtered(String query) {
		List<Entry> all = entries();
		String normalized = query.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals(cachedQuery)) return cachedResult;

		String[] words = words(normalized);
		List<ItemStack> result = new ArrayList<>();
		for (Entry entry : all) {
			if (matches(entry, words)) result.add(entry.stack());
		}
		cachedQuery = normalized;
		cachedResult = result;
		return result;
	}

	/** Where an item comes in the creative menu's order (items it doesn't list go last). */
	public static int creativeOrder(Item item) {
		entries();
		return order.getOrDefault(item, Integer.MAX_VALUE);
	}

	/** Whether any stack (e.g. one in the player's inventory) matches the search, using the same rules as the list. */
	public static boolean matches(ItemStack stack, String query) {
		String[] words = words(query.trim().toLowerCase(Locale.ROOT));
		return words.length == 0 || matches(Entry.of(stack), words);
	}

	private static String[] words(String normalized) {
		return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
	}

	private static boolean matches(Entry entry, String[] words) {
		for (String word : words) {
			if (!matches(entry, word)) return false;
		}
		return true;
	}

	private static boolean matches(Entry entry, String word) {
		if (word.startsWith("@")) return entry.namespace().contains(word.substring(1));
		if (word.startsWith("#")) {
			String tag = word.substring(1);
			for (String candidate : entry.tags()) {
				if (candidate.contains(tag)) return true;
			}
			return false;
		}
		return entry.name().contains(word);
	}

	private static List<Entry> entries() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || minecraft.level == null) return List.of();
		if (connection != cachedConnection) {
			cachedConnection = connection;
			cachedQuery = null;
			entries = build(minecraft, connection);
			Map<Item, Integer> positions = new HashMap<>();
			for (int i = 0; i < entries.size(); i++) {
				positions.putIfAbsent(entries.get(i).stack().getItem(), i);
			}
			order = positions;
		}
		return entries;
	}

	private static List<Entry> build(Minecraft minecraft, ClientPacketListener connection) {
		List<ItemStack> stacks = new ArrayList<>();
		try {
			CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), false, minecraft.level.registryAccess());
			stacks.addAll(CreativeModeTabs.searchTab().getDisplayItems());
		} catch (RuntimeException | LinkageError e) {
			// A mod's creative tab can fail to build. Fall back to the plain item registry below (no variants).
			LOGGER.warn("Couldn't read the creative search tab, listing registered items instead", e);
			stacks.clear();
		}
		Set<Item> listed = new HashSet<>();
		for (ItemStack stack : stacks) {
			listed.add(stack.getItem());
		}
		boolean fromTab = !stacks.isEmpty();
		for (Item item : BuiltInRegistries.ITEM) {
			// Normally only modded items missing from creative tabs; everything if the creative tab couldn't be read.
			boolean modded = !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
			if ((modded || !fromTab) && item != Items.AIR && !listed.contains(item) && item.isEnabled(connection.enabledFeatures())) {
				stacks.add(new ItemStack(item));
			}
		}

		List<Entry> built = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			try {
				built.add(Entry.of(stack));
			} catch (RuntimeException | LinkageError e) {
				LOGGER.debug("Skipping item {}", stack.getItem(), e);
			}
		}
		return List.copyOf(built);
	}
}
