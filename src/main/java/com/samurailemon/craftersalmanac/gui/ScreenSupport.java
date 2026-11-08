package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;

import java.util.HashMap;
import java.util.Map;

/**
 * Which screens get the almanac, and how we fit into screens other mods make:
 * <ul>
 * <li>Crafting screens: the almanac is for making things, so it stays open on the inventory, crafting stations,
 * furnaces and other machines. On storage it only opens when R or U asks for an item's recipes or uses.</li>
 * <li>Extras: some screens put slots or buttons outside their official container box (side tabs, upgrade slots,
 * accessory slots). We measure how far they reach after each init and remember it per screen class, so the next
 * layout leaves room for them.</li>
 * <li>Disabled screens: anything that still misbehaves can be switched off per screen class, from the settings
 * screen or a keybind. On a disabled screen we add nothing at all.</li>
 * </ul>
 */
public final class ScreenSupport {
	/** How far a screen's slots and buttons reach past the left and right of its container box. */
	public record Extras(int left, int right) {
		static final Extras NONE = new Extras(0, 0);
	}

	/**
	 * A modded screen with at least this many slots of its own (besides the player's) is taken to be storage, like a
	 * single chest. Machines, even big ones, have fewer.
	 */
	private static final int STORAGE_SLOTS = 27;
	/** Things further than this from the container are probably not attached to it (e.g. a button in a screen corner). */
	private static final int MAX_REACH = 100;
	private static final Map<Class<?>, Extras> EXTRAS = new HashMap<>();

	private ScreenSupport() {
	}

	/**
	 * Whether the almanac stays open on this screen: one for making things (the inventory, crafting table, furnaces,
	 * smithing, brewing, modded machines), not storage (chests, barrels, shulker boxes, hoppers, dispensers, mods'
	 * chests and backpacks), animal inventories, trading or beacons.
	 */
	public static boolean isCraftingScreen(Screen screen) {
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return true;
		if (!(screen instanceof AbstractContainerScreen<?> container)) return false;
		AbstractContainerMenu menu = container.getMenu();
		if (InventorySorter.isStorage(menu) || menu instanceof HorseInventoryMenu || menu instanceof MerchantMenu || menu instanceof BeaconMenu) {
			return false;
		}
		int own = 0;
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) own++;
		}
		return own < STORAGE_SLOTS;
	}

	public static Extras extras(Screen screen) {
		return EXTRAS.getOrDefault(screen.getClass(), Extras.NONE);
	}

	/**
	 * Measures the screen as it was just laid out, before we add our own widgets. Returns true if it differs from
	 * what was remembered, meaning the layout should be redone to make room.
	 */
	public static boolean measure(AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		int left = container.rbv$getLeftPos();
		int right = left + container.rbv$getImageWidth();
		int minX = left;
		int maxX = right;
		for (Slot slot : screen.getMenu().slots) {
			// Slots use container coordinates. Mods hide unused slots by moving them far away; MAX_REACH skips those.
			if (!slot.isActive()) continue;
			int x = left + slot.x;
			if (x >= left - MAX_REACH) minX = Math.min(minX, x);
			if (x + 16 <= right + MAX_REACH) maxX = Math.max(maxX, x + 16);
		}
		for (AbstractWidget widget : Screens.getButtons(screen)) {
			if (!widget.visible) continue;
			if (widget.getX() >= left - MAX_REACH) minX = Math.min(minX, widget.getX());
			if (widget.getRight() <= right + MAX_REACH) maxX = Math.max(maxX, widget.getRight());
		}
		Extras measured = new Extras(left - minX, maxX - right);
		Extras previous = EXTRAS.put(screen.getClass(), measured);
		return !measured.equals(previous == null ? Extras.NONE : previous);
	}

	public static boolean isDisabled(Screen screen) {
		return ViewerConfig.get().disabledScreens.contains(screen.getClass().getName());
	}

	/** Switches the viewer off or back on for this kind of screen. Returns true if it's now disabled. */
	public static boolean toggleDisabled(Screen screen) {
		String name = screen.getClass().getName();
		ViewerConfig config = ViewerConfig.get();
		boolean disabled = !config.disabledScreens.remove(name);
		if (disabled) config.disabledScreens.add(name);
		ViewerConfig.save();
		return disabled;
	}
}
