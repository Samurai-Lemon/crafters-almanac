package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.recipe.Card;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
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
 * flame), revealed a bit at a time, so they line up exactly with the art. Plain cards take their border, slots and
 * arrow from the furnace card so they match it.
 */
public final class RecipeRenderer {
	/** Lit copies of the card art: flame 14x14 at (56, 36), arrow 24x16 at (79, 34), brew arrow 9x28 at (97, 16), bubbles 12x29 at (63, 14). */
	private static final ResourceLocation FLAME = sprite("card_furnace_flame");
	private static final ResourceLocation ARROW_FILL = sprite("card_furnace_arrow");
	private static final ResourceLocation BREW_ARROW = sprite("card_brewing_arrow");
	private static final ResourceLocation BUBBLES = sprite("card_brewing_bubbles");
	/** Same bubble animation frames as BrewingStandScreen. */
	private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};
	/** Plain cards and the materials grid copy these parts of the furnace card: input slot, result slot, arrow. */
	private static final ResourceLocation FURNACE_CARD = sprite(Card.FURNACE.sprite);
	/** An empty strip of the furnace card's middle, tiled to make plain cards any size. */
	private static final int EMPTY_X = 140;
	private static final int EMPTY_WIDTH = 30;
	private static final int EMPTY_Y = 6;
	private static final int EMPTY_HEIGHT = 9;
	/** Text on a card: vanilla's container label colour. */
	static final int CARD_TEXT = 0x404040;
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
				int bubbles = BUBBLE_LENGTHS[(int) (millis / 100) % BUBBLE_LENGTHS.length];
				if (bubbles > 0) g.blitSprite(BUBBLES, 12, 29, 0, 29 - bubbles, x + card.x(63), y + 14 + 29 - bubbles, 12, bubbles);
			}
			case SINGLE, SMITHING, GENERIC -> {
				for (DisplaySlot slot : display.slots()) {
					if (slot.output() && slot.shown()) arrow(g, x + slot.x() - 28, y + slot.y() + 5);
				}
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
			// The art cards have their slots drawn in.
			if (card == Card.PLAIN) slot(g, sx, sy, slot.size());
			ItemStack stack = slot.current();
			int inset = (slot.size() - 16) / 2;
			g.renderItem(stack, sx + inset, sy + inset);
			g.renderItemDecorations(font, stack, sx + inset, sy + inset);
		}
	}

	/**
	 * Art cards: the left border, the kept middle and the right border, each full height. Plain cards: see
	 * {@link #plainCard}.
	 */
	private static void card(GuiGraphics g, Display display, int x, int y) {
		Card card = display.card();
		ResourceLocation sprite = sprite(card.sprite);
		int b = Card.BORDER;
		if (card != Card.PLAIN) {
			blit(g, sprite, 0, 0, x, y, b, Card.TEXTURE_HEIGHT);
			blit(g, sprite, card.keepX, 0, x + b, y, Card.KEPT, Card.TEXTURE_HEIGHT);
			blit(g, sprite, Card.TEXTURE_WIDTH - b, 0, x + b + Card.KEPT, y, b, Card.TEXTURE_HEIGHT);
			return;
		}
		plainCard(g, x, y, display.width(), display.height());
	}

	/** A plain card of any size: the border's corners and edges with the empty middle strip tiled to fill it. */
	public static void plainCard(GuiGraphics g, int x, int y, int width, int height) {
		ResourceLocation sprite = sprite(Card.PLAIN.sprite);
		int b = Card.BORDER;
		int bottom = Card.BOTTOM_BORDER;
		cardRow(g, sprite, x, y, width, 0, b);
		for (int row = b; row < height - bottom; row += EMPTY_HEIGHT) {
			cardRow(g, sprite, x, y + row, width, EMPTY_Y, Math.min(EMPTY_HEIGHT, height - bottom - row));
		}
		cardRow(g, sprite, x, y + height - bottom, width, Card.TEXTURE_HEIGHT - bottom, bottom);
	}

	/** One band of a plain card: left edge, tiled middle, right edge, from row {@code v} of the card image. */
	private static void cardRow(GuiGraphics g, ResourceLocation sprite, int x, int y, int width, int v, int rows) {
		int b = Card.BORDER;
		blit(g, sprite, 0, v, x, y, b, rows);
		for (int column = b; column < width - b; column += EMPTY_WIDTH) {
			blit(g, sprite, EMPTY_X, v, x + column, y, Math.min(EMPTY_WIDTH, width - b - column), rows);
		}
		blit(g, sprite, Card.TEXTURE_WIDTH - b, v, x + width - b, y, b, rows);
	}

	/** Draws part of a card image at its real size. */
	private static void blit(GuiGraphics g, ResourceLocation sprite, int u, int v, int x, int y, int width, int height) {
		g.blitSprite(sprite, Card.TEXTURE_WIDTH, Card.TEXTURE_HEIGHT, u, v, x, y, width, height);
	}

	private static ResourceLocation sprite(String name) {
		return ResourceLocation.fromNamespaceAndPath("craftersalmanac", name);
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

	/** The furnace card's arrow (22x16), centered in the 24-wide space layouts leave for an arrow. */
	private static void arrow(GuiGraphics g, int x, int y) {
		blit(g, FURNACE_CARD, 80, 34, x + 1, y, 22, 16);
	}

	/** A slot in the furnace card's style, centered in a slot area: its result slot for 26 and up, else its input slot. */
	static void slot(GuiGraphics g, int x, int y, int size) {
		boolean large = size >= 26;
		int art = large ? 24 : 16;
		int inset = (size - art) / 2;
		blit(g, FURNACE_CARD, large ? 112 : 56, large ? 31 : 17, x + inset, y + inset, art, art);
	}
}
