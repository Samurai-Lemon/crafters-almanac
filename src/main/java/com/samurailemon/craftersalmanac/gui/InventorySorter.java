package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.ItemList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Sorts the player's main inventory (the 27 slots above the hotbar; the hotbar stays as it is) or a storage
 * container (chests, barrels, shulker boxes...). It works by clicking slots the way a player would, so it works on
 * any server: first stacks of the same item are combined, then everything is put in order with swaps. Each click
 * updates the screen right away, so later steps see the result of earlier ones.
 */
public final class InventorySorter {
	/** How the inventory gets sorted, chosen in the settings. OFF hides the sort button. */
	public enum Mode {
		CREATIVE, NAME, MOD, AMOUNT, OFF;

		public Component title() {
			return Component.translatable("craftersalmanac.sort." + name().toLowerCase(Locale.ROOT));
		}
	}

	/** What a sort button sorts. */
	public enum Target {
		/** The player's main inventory rows, in the survival inventory screen. */
		PLAYER,
		/** Every slot of the storage container that's open. */
		CONTAINER
	}

	/** Player inventory slots 9-35 are the main rows; 0-8 are the hotbar. */
	private static final int FIRST_ROW_SLOT = 9;
	private static final int END_SLOT = 36;

	private InventorySorter() {
	}

	/**
	 * Storage-only screens, where any item can go in any slot, so sorting can't put things somewhere they don't
	 * belong (unlike a furnace's fuel and input slots). Includes mods' storage built on these menus.
	 */
	public static boolean isStorage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	/** The slots a target sorts, in the order they're filled. */
	public static List<Slot> slots(AbstractContainerMenu menu, Target target) {
		Player player = Minecraft.getInstance().player;
		if (player == null) return List.of();
		return menu.slots.stream()
				.filter(slot -> target == Target.PLAYER
						? slot.container == player.getInventory() && slot.getContainerSlot() >= FIRST_ROW_SLOT && slot.getContainerSlot() < END_SLOT
						: slot.container != player.getInventory())
				.sorted(Comparator.comparingInt(Slot::getContainerSlot))
				.toList();
	}

	public static void sort(AbstractContainerMenu menu, Target target, Mode mode) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		// Clicking with something on the cursor would drop it into the inventory mid-sort.
		if (player == null || gameMode == null || mode == Mode.OFF || !menu.getCarried().isEmpty()) return;

		List<Slot> slots = slots(menu, target);
		Clicker click = slot -> gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, player);

		// Combine: pick up a later stack, drop it on an earlier one of the same item, put back what didn't fit.
		for (int i = 0; i < slots.size(); i++) {
			Slot into = slots.get(i);
			for (int j = i + 1; j < slots.size() && !isFull(into.getItem()); j++) {
				Slot source = slots.get(j);
				if (!into.hasItem() || !ItemStack.isSameItemSameComponents(into.getItem(), source.getItem())) continue;
				click.on(source);
				click.on(into);
				if (!menu.getCarried().isEmpty()) click.on(source);
			}
		}

		// Order: selection sort, swapping each slot with the stack that belongs there. Same-item stacks are never
		// swapped with each other, because clicking one onto the other would combine them instead.
		Comparator<ItemStack> order = comparator(mode);
		for (int i = 0; i < slots.size(); i++) {
			int best = -1;
			for (int j = i; j < slots.size(); j++) {
				ItemStack stack = slots.get(j).getItem();
				if (!stack.isEmpty() && (best < 0 || order.compare(stack, slots.get(best).getItem()) < 0)) best = j;
			}
			if (best < 0) break;
			Slot here = slots.get(i);
			Slot there = slots.get(best);
			if (best == i || ItemStack.isSameItemSameComponents(here.getItem(), there.getItem())) continue;
			click.on(there);
			click.on(here);
			if (!menu.getCarried().isEmpty()) click.on(there);
		}
	}

	private static boolean isFull(ItemStack stack) {
		return !stack.isEmpty() && stack.getCount() >= stack.getMaxStackSize();
	}

	private static Comparator<ItemStack> comparator(Mode mode) {
		Comparator<ItemStack> byCreative = Comparator.comparingInt(stack -> ItemList.creativeOrder(stack.getItem()));
		Comparator<ItemStack> byName = Comparator.comparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
		Comparator<ItemStack> byMod = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
		Comparator<ItemStack> byAmount = Comparator.comparingInt(ItemStack::getCount).reversed();
		// Ties end with the item ID, then bigger stacks first, so the result is the same every time.
		Comparator<ItemStack> ties = Comparator.<ItemStack, String>comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
				.thenComparing(byAmount);
		return switch (mode) {
			case NAME -> byName.thenComparing(ties);
			case MOD -> byMod.thenComparing(byName).thenComparing(ties);
			case AMOUNT -> byAmount.thenComparing(byCreative).thenComparing(ties);
			default -> byCreative.thenComparing(byName).thenComparing(ties);
		};
	}

	@FunctionalInterface
	private interface Clicker {
		void on(Slot slot);
	}
}
