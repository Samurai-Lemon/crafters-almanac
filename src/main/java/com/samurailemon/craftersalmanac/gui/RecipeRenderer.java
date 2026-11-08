package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.Card;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import com.samurailemon.craftersalmanac.recipe.Layout;
import com.samurailemon.craftersalmanac.recipe.Station;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Draws one recipe on its card (see {@link Card}). The art cards have their slots, arrows, flame and tubes drawn in.
 * The animated parts are "lit" copies of the cards' own arrow, flame and bubble shapes (white, or orange for the
 * flame), revealed a bit at a time, so they line up exactly with the art. Plain cards are the furnace card's frame as
 * a nine-slice sprite, and take their slots and arrow from the furnace card, so they match it.
 */
public final class RecipeRenderer {
	/** The furnace's and brewing stand's own progress sprites, at the same positions as on their screens. */
	private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
	private static final ResourceLocation ARROW_FILL = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
	private static final ResourceLocation BREW_ARROW = ResourceLocation.withDefaultNamespace("container/brewing_stand/brew_progress");
	private static final ResourceLocation BUBBLES = ResourceLocation.withDefaultNamespace("container/brewing_stand/bubbles");
	private static final ResourceLocation BLAZE_FUEL = ResourceLocation.withDefaultNamespace("container/brewing_stand/fuel_length");
	/** Same bubble animation frames as BrewingStandScreen. */
	private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};
	private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
	/** Plain cards take their result slot and arrow from the furnace screen. */
	private static final ResourceLocation FURNACE_SCREEN = Card.FURNACE.texture;
	/** Text on a card: vanilla's container label colour. */
	static final int CARD_TEXT = 0x404040;
	/** The "50%" on outputs a recipe only sometimes gives: small, in vanilla's gold, in front of the item. */
	private static final float CHANCE_SCALE = 0.75F;
	private static final int CHANCE_COLOR = 0xFFFFAA00;
	/** Text drawn over items has to be in front of them (items are drawn up to 150 deep plus their own depth). */
	private static final float OVER_ITEMS = 200.0F;
	private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
	/** Trim cards' armor stand: the smithing table's tilt and starting direction, a bit smaller, one turn every 8s. */
	private static final Quaternionf STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
	private static final float STAND_FACING = 210.0F;
	private static final float STAND_SCALE = 23.0F;
	private static final long STAND_TURN_MILLIS = 8000L;
	private static ArmorStand stand;

	private RecipeRenderer() {
	}

	public static void render(GuiGraphics g, Font font, Display display, int x, int y) {
		render(g, font, display, x, y, true);
	}

	/** {@code showXp} is false for the Shift preview, which puts the station's name where the XP goes. */
	public static void render(GuiGraphics g, Font font, Display display, int x, int y, boolean showXp) {
		card(g, display, x, y);
		Card card = display.card();
		switch (display.layout()) {
			case COOKING -> {
				// Over one cook, the flame burns down and the arrow fills, like the real furnace.
				long total = Math.max(1, display.cookTime()) * 50L;
				long time = Util.getMillis() % total;
				int lit = 14 - (int) (time * 13 / total);
				g.blitSprite(FLAME, 14, 14, 0, 14 - lit, x + card.x(56), y + 36 + 14 - lit, 14, lit);
				g.blitSprite(ARROW_FILL, 24, 16, 0, 0, x + card.x(79), y + 34, (int) (time * 24 / total), 16);
				// XP along the top of the card, inside the border; cook time under the arrow.
				if (showXp) g.drawString(font, Component.translatable("craftersalmanac.xp", NUMBER.format(display.xp())), x + card.x(79), y + 7, CARD_TEXT, false);
				g.drawString(font, Component.translatable("craftersalmanac.cook_time", NUMBER.format(display.cookTime() / 20.0)), x + card.x(79), y + 53, CARD_TEXT, false);
			}
			case BREWING -> {
				// The bottles show the starting potion for a second while the arrow fills, then the result.
				long millis = Util.getMillis();
				boolean brewed = millis / 1000 % 2 == 1;
				int progress = brewed ? 28 : (int) (millis % 1000 * 28 / 1000);
				g.blitSprite(BREW_ARROW, 9, 28, 0, 0, x + card.x(97), y + 16, 9, progress);
				g.blitSprite(BLAZE_FUEL, x + card.x(60), y + 44, 18, 4);
				int bubbles = BUBBLE_LENGTHS[(int) (millis / 100) % BUBBLE_LENGTHS.length];
				if (bubbles > 0) g.blitSprite(BUBBLES, 12, 29, 0, 29 - bubbles, x + card.x(63), y + 14 + 29 - bubbles, 12, bubbles);
			}
			case SINGLE, SMITHING, GENERIC -> {
				// Left of the outputs, centered on them (one big slot, or a grid of several).
				int left = Integer.MAX_VALUE;
				int top = Integer.MAX_VALUE;
				int bottom = Integer.MIN_VALUE;
				for (DisplaySlot slot : display.slots()) {
					if (!slot.output() || !slot.shown()) continue;
					left = Math.min(left, slot.x());
					top = Math.min(top, slot.y());
					bottom = Math.max(bottom, slot.y() + slot.size());
				}
				if (left != Integer.MAX_VALUE) {
					int arrowX = x + left - 28;
					int arrowY = y + (top + bottom) / 2 - 8;
					arrow(g, arrowX, arrowY);
					// A modded machine that says how long it takes fills its arrow over that time, like the furnace.
					if (display.layout() == Layout.GENERIC && display.cookTime() > 0) {
						long total = display.cookTime() * 50L;
						int filled = (int) (Util.getMillis() % total * 24 / total);
						g.blitSprite(ARROW_FILL, 24, 16, 0, 0, arrowX, arrowY, filled, 16);
					}
				}
				info(g, font, display, x, y);
			}
			case STATION -> {
				Station station = display.station();
				int inset = Card.BORDER + 1;
				g.blit(station.texture(), x + inset, y + inset, station.u(), station.v(), station.width(), station.height(),
						station.textureWidth(), station.textureHeight());
				info(g, font, display, x, y);
			}
			case TRIM -> {
				for (DisplaySlot slot : display.slots()) {
					if (!slot.output()) continue;
					arrow(g, x + slot.x() - 28, y + slot.y() + 5);
					armorStand(g, slot.current(), x + (slot.x() + slot.size() + display.width() - Card.BORDER) / 2, y + slot.y() + slot.size());
				}
			}
			case FUEL -> {
				// Two short lines so even "100 items" (lava bucket) stays clear of the favorite star.
				DisplaySlot item = display.slots().get(0);
				g.drawString(font, Component.translatable("craftersalmanac.fuel.smelts"), x + item.x() + 22, y + item.y() - 1, CARD_TEXT, false);
				Component items = Component.translatable("craftersalmanac.fuel.items", NUMBER.format(display.cookTime() / 200.0));
				g.drawString(font, items, x + item.x() + 22, y + item.y() + 10, CARD_TEXT, false);
			}
			case CRAFTING -> {
			}
		}

		for (DisplaySlot slot : display.slots()) {
			if (!slot.shown()) continue;
			int sx = x + slot.x();
			int sy = y + slot.y();
			// The art cards and machine art have their slots drawn in.
			if (card == Card.PLAIN && display.layout() != Layout.STATION) slot(g, sx, sy, slot.size());
			ItemStack stack = slot.current();
			int inset = (slot.size() - 16) / 2;
			g.renderItem(stack, sx + inset, sy + inset);
			g.renderItemDecorations(font, stack, sx + inset, sy + inset);
			if (slot.chance() < 1.0F) chance(g, font, slot.chance(), sx + inset, sy + inset);
		}
	}

	/** "50%" in the item's top-left corner. */
	private static void chance(GuiGraphics g, Font font, float chance, int x, int y) {
		g.pose().pushPose();
		g.pose().translate(x, y, OVER_ITEMS);
		g.pose().scale(CHANCE_SCALE, CHANCE_SCALE, 1.0F);
		g.drawString(font, percent(chance), 0, 0, CHANCE_COLOR, true);
		g.pose().popPose();
	}

	/** A chance as a whole percentage: 0.5 is "50%". */
	public static String percent(float chance) {
		return Math.round(chance * 100) + "%";
	}

	/** A modded recipe's time and XP ("5s  1 XP"), along the bottom of the card left of the buttons. */
	private static void info(GuiGraphics g, Font font, Display display, int x, int y) {
		if (display.cookTime() <= 0 && display.xp() <= 0) return;
		MutableComponent text = Component.empty();
		if (display.cookTime() > 0) {
			text.append(Component.translatable("craftersalmanac.cook_time", NUMBER.format(display.cookTime() / 20.0)));
		}
		if (display.xp() > 0) {
			if (display.cookTime() > 0) text.append("  ");
			text.append(Component.translatable("craftersalmanac.xp", NUMBER.format(display.xp())));
		}
		g.drawString(font, text, x + Card.BORDER + 2, y + display.buttonY() + 2, CARD_TEXT, false);
	}

	/** The frame (see CardFrames), and for art cards the middle of the station's screen inside it. */
	private static void card(GuiGraphics g, Display display, int x, int y) {
		CardFrames.draw(g, display, x, y, display.width(), display.height());
		Card card = display.card();
		if (card == Card.PLAIN) return;
		int b = Card.BORDER;
		g.blit(CardFrames.artTexture(display), x + b, y + b, card.keepX, b, Card.KEPT, display.height() - 2 * b);
	}

	/**
	 * An armor stand wearing {@code stack}, standing with its feet at (x, y) and slowly turning round, like the
	 * smithing table's preview. One stand is kept and dressed for each card as it's drawn.
	 */
	private static void armorStand(GuiGraphics g, ItemStack stack, int x, int y) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return;
		if (stand == null || stand.level() != level) {
			stand = new ArmorStand(level, 0, 0, 0);
			stand.setNoBasePlate(true);
			stand.setShowArms(true);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			stand.setItemSlot(slot, ItemStack.EMPTY);
		}
		stand.setItemSlot(stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : EquipmentSlot.OFFHAND, stack);
		float turn = STAND_FACING + Util.getMillis() % STAND_TURN_MILLIS * 360.0F / STAND_TURN_MILLIS;
		stand.yBodyRot = stand.yBodyRotO = turn;
		stand.yHeadRot = stand.yHeadRotO = turn;
		InventoryScreen.renderEntityInInventory(g, x, y, STAND_SCALE, new Vector3f(), STAND_ANGLE, null, stand);
	}

	/** The furnace screen's arrow (24x16), in the space layouts leave for an arrow. */
	private static void arrow(GuiGraphics g, int x, int y) {
		g.blit(FURNACE_SCREEN, x, y, 79, 34, 24, 16);
	}

	/** A slot centered in a slot area: the furnace screen's result slot for 26 and up, else vanilla's slot sprite. */
	static void slot(GuiGraphics g, int x, int y, int size) {
		if (size >= 26) {
			int inset = (size - 26) / 2;
			g.blit(FURNACE_SCREEN, x + inset, y + inset, 111, 30, 26, 26);
		} else {
			int inset = (size - 18) / 2;
			g.blitSprite(SLOT, x + inset, y + inset, 18, 18);
		}
	}
}
