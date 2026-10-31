package com.samurailemon.craftersalmanac.recipe;

import com.samurailemon.craftersalmanac.mixin.PotionBrewingAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Things that work like recipes but aren't in the game's recipe list: brewing (from the level's PotionBrewing,
 * which includes modded potions) and furnace fuels. Both are already on the client, so no server help is needed.
 * They get made-up IDs under our namespace so they can be favorited.
 */
public final class ExtraRecipes {
	private ExtraRecipes() {
	}

	public static List<Display> create(ClientLevel level) {
		List<Display> displays = new ArrayList<>();
		brewing(level.potionBrewing(), displays);
		fuel(displays);
		return displays;
	}

	private static void brewing(PotionBrewing brewing, List<Display> displays) {
		PotionBrewingAccessor mixes = (PotionBrewingAccessor) brewing;
		Set<Item> containers = new LinkedHashSet<>();
		for (Ingredient container : mixes.rbv$getContainers()) {
			for (ItemStack stack : container.getItems()) {
				containers.add(stack.getItem());
			}
		}

		// Potion + ingredient = potion, once per bottle type (normal, splash, lingering).
		Set<Holder<Potion>> brewable = new LinkedHashSet<>();
		List<PotionBrewing.Mix<Potion>> potionMixes = mixes.rbv$getPotionMixes();
		for (int i = 0; i < potionMixes.size(); i++) {
			PotionBrewing.Mix<Potion> mix = potionMixes.get(i);
			brewable.add(mix.from());
			brewable.add(mix.to());
			for (Item container : containers) {
				ResourceLocation id = id("brewing/" + i + "/" + BuiltInRegistries.ITEM.getKey(container).getPath());
				add(displays, DisplayFactory.brewing(id,
						List.of(PotionContents.createItemStack(container, mix.from())),
						List.of(mix.ingredient().getItems()),
						List.of(PotionContents.createItemStack(container, mix.to()))));
			}
		}

		// Any potion + ingredient = the same potion in another bottle (gunpowder = splash). The input and output
		// lists line up, so they cycle through the potions together.
		List<PotionBrewing.Mix<Item>> containerMixes = mixes.rbv$getContainerMixes();
		for (int i = 0; i < containerMixes.size(); i++) {
			PotionBrewing.Mix<Item> mix = containerMixes.get(i);
			List<ItemStack> from = new ArrayList<>();
			List<ItemStack> to = new ArrayList<>();
			for (Holder<Potion> potion : brewable) {
				from.add(PotionContents.createItemStack(mix.from().value(), potion));
				to.add(PotionContents.createItemStack(mix.to().value(), potion));
			}
			add(displays, DisplayFactory.brewing(id("brewing/container/" + i), from, List.of(mix.ingredient().getItems()), to));
		}
	}

	private static void fuel(List<Display> displays) {
		List<Map.Entry<Item, Integer>> fuels = new ArrayList<>(AbstractFurnaceBlockEntity.getFuel().entrySet());
		fuels.sort(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()));
		for (Map.Entry<Item, Integer> fuel : fuels) {
			ResourceLocation item = BuiltInRegistries.ITEM.getKey(fuel.getKey());
			displays.add(DisplayFactory.fuel(id("fuel/" + item.getNamespace() + "/" + item.getPath()), new ItemStack(fuel.getKey()), fuel.getValue()));
		}
	}

	private static void add(List<Display> displays, Display display) {
		if (display != null) displays.add(display);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("craftersalmanac", path);
	}
}
