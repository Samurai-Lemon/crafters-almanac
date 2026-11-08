package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.Util;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

/**
 * Cheat mode: clicking an item in the almanac's list gives it to you, like taking it from the creative menu. Only for
 * players allowed to use commands ("Allow Cheats" in singleplayer, operators on a server), since it works through
 * /give, which also makes it work on any server. Its "Gave ..." replies are kept out of chat.
 */
public final class Cheats {
	/** The permission level /give needs; "Allow Cheats" grants it in singleplayer. */
	private static final int GIVE_PERMISSION = 2;
	/** /give's "Gave 64 [Stone] to Steve" (one player) and "... to 2 players". */
	private static final String GIVE_SUCCESS = "commands.give.success.";
	/** A reply this much later than our last give isn't one of ours (lag aside, they come back within a tick or two). */
	private static final long REPLY_MILLIS = 5000L;

	/** Gives sent whose "Gave ..." reply hasn't come back yet, so it can be hidden from chat. */
	private static int awaitingReplies;
	private static long lastGive;

	private Cheats() {
	}

	/** Whether the player may use cheat mode at all, so whether its tab shows. */
	public static boolean allowed() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.hasPermissions(GIVE_PERMISSION);
	}

	/** Whether clicking items gives them: switched on, and still allowed (an operator can be de-opped). */
	public static boolean active() {
		return ViewerConfig.get().cheatMode && allowed();
	}

	public static void toggle() {
		ViewerConfig config = ViewerConfig.get();
		config.cheatMode = !config.cheatMode;
		ViewerConfig.save();
	}

	/** Gives the item with all its components (a potion's effect, a book's enchantments). */
	public static void give(ItemStack stack, int count) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		if (minecraft.player == null || connection == null || stack.isEmpty()) return;
		String item = new ItemInput(stack.getItemHolder(), stack.getComponentsPatch()).serialize(minecraft.player.registryAccess());
		connection.sendCommand("give @s " + item + " " + count);
		awaitingReplies++;
		lastGive = Util.getMillis();
	}

	/**
	 * Returns false for the "Gave ..." chat line answering one of our gives, so cheat mode doesn't fill the chat.
	 * Anything else, a failed give or a /give typed in chat once ours are answered, still shows.
	 */
	public static boolean allowMessage(Component message) {
		if (awaitingReplies == 0) return true;
		if (Util.getMillis() - lastGive > REPLY_MILLIS) {
			awaitingReplies = 0;
			return true;
		}
		if (!(message.getContents() instanceof TranslatableContents contents) || !contents.getKey().startsWith(GIVE_SUCCESS)) return true;
		awaitingReplies--;
		return false;
	}
}
