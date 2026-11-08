package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.recipe.Card;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplayFactory;
import com.samurailemon.craftersalmanac.recipe.Layout;
import com.samurailemon.craftersalmanac.recipe.RecipeIndex;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * While Shift is held over an item, draws its recipe card beside the tooltip, so a recipe can be checked without
 * opening the almanac. The card goes on the side of the mouse away from the tooltip. Items with several recipes
 * cycle through them, starting from the first each time a new item is hovered, with dots under the card showing
 * how many there are and which one is shown.
 */
public final class RecipePreview {
	/** Vanilla puts the tooltip this far right of the mouse, and its border and padding add this to the text width. */
	private static final int TOOLTIP_OFFSET = 12;
	private static final int TOOLTIP_PADDING = 8;
	private static final int GAP = 4;
	private static final int EDGE = 2;
	/** In front of the tooltip, which vanilla draws at 400. */
	private static final float Z = 500.0F;
	/** Smaller than on the almanac's pages, so it doesn't cover much of the screen. */
	private static final float SCALE = 0.95F;
	/** The header sits in the strip between the card's top border and its first slots. */
	private static final int HEADER_Y = 6;
	private static final String ELLIPSIS = "...";
	private static final long CYCLE_MILLIS = 2000L;
	/** Room under the card for the dots, or for "2/15" when there are too many recipes for dots. */
	private static final int FOOTER = 10;
	private static final int MAX_DOTS = 8;
	private static final int DOT = 4;
	private static final int DOT_GAP = 3;
	private static final int DOT_OUTLINE = 0xFF202020;
	private static final int DOT_CURRENT = 0xFFFFFFFF;
	private static final int DOT_OTHER = 0xFF808080;

	/**
	 * The item being previewed, worked out once when hovering it starts (Shift down over a new item) rather than
	 * every frame: when that was, so its recipes cycle from the first, its recipes, and how wide its tooltip is.
	 */
	private static ItemStack shown = ItemStack.EMPTY;
	private static long since;
	private static List<Display> recipes = List.of();
	private static int tooltipWidth;

	private RecipePreview() {
	}

	/** Called after the screen is drawn, with the real mouse position. */
	public static void render(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
		if (!ViewerConfig.get().recipePreview || !Screen.hasShiftDown() || stack.isEmpty()) {
			shown = ItemStack.EMPTY;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		long now = Util.getMillis();
		if (!ItemStack.isSameItemSameComponents(stack, shown)) {
			shown = stack.copy();
			since = now;
			recipes = recipesFor(stack);
			tooltipWidth = 0;
			for (Component line : Screen.getTooltipFromItem(minecraft, stack)) {
				tooltipWidth = Math.max(tooltipWidth, font.width(line));
			}
			tooltipWidth += TOOLTIP_PADDING;
		}
		if (recipes.isEmpty()) return;
		int index = (int) ((now - since) / CYCLE_MILLIS % recipes.size());
		Display display = recipes.get(index);
		int width = Mth.ceil(display.width() * SCALE);
		int cardHeight = Mth.ceil(display.height() * SCALE);
		int height = cardHeight + (recipes.size() > 1 ? FOOTER : 0);

		int screenWidth = g.guiWidth();
		// Vanilla flips the tooltip to the left of the mouse when it would run off the right edge.
		boolean tooltipOnRight = mouseX + TOOLTIP_OFFSET + tooltipWidth <= screenWidth;
		int x;
		if (tooltipOnRight) {
			x = mouseX - GAP - width;
			if (x < EDGE) x = mouseX + TOOLTIP_OFFSET + tooltipWidth + GAP;
		} else {
			x = mouseX + TOOLTIP_OFFSET;
		}
		x = Mth.clamp(x, EDGE, Math.max(EDGE, screenWidth - width - EDGE));
		int y = Mth.clamp(mouseY - TOOLTIP_OFFSET, EDGE, Math.max(EDGE, g.guiHeight() - height - EDGE));

		g.pose().pushPose();
		g.pose().translate(x, y, Z);
		if (recipes.size() > 1) count(g, font, index, recipes.size(), width, cardHeight);
		g.pose().scale(SCALE, SCALE, 1.0F);
		RecipeRenderer.render(g, font, display, 0, 0, false);
		header(g, font, display);
		g.pose().popPose();
	}

	/** A potion shows just the brewing that makes it, not every potion a recipe can take. */
	private static List<Display> recipesFor(ItemStack stack) {
		List<Display> found = RecipeIndex.recipesFor(stack);
		if (!stack.has(DataComponents.POTION_CONTENTS)) return found;
		Display brewing = DisplayFactory.brewingFor(found, stack);
		return brewing == null ? found : List.of(brewing);
	}

	/**
	 * The crafting, furnace and brewing cards have an empty strip above their slots (the furnace's XP is left out of
	 * previews to make room): it gets the station's name. Plain cards have no room, so they go without.
	 */
	private static void header(GuiGraphics g, Font font, Display display) {
		Layout layout = display.layout();
		if (layout != Layout.CRAFTING && layout != Layout.COOKING && layout != Layout.BREWING) return;
		int left = Card.BORDER + 1;
		int room = display.width() - Card.BORDER - 1 - left;
		String title = display.category().title().getString();
		if (font.width(title) > room) title = font.plainSubstrByWidth(title, room - font.width(ELLIPSIS)) + ELLIPSIS;
		g.drawString(font, title, left, HEADER_Y, RecipeRenderer.CARD_TEXT, false);
	}

	/** Under the card: a dot per recipe with the shown one lit, or "2/15" when there are too many for dots. */
	private static void count(GuiGraphics g, Font font, int index, int total, int width, int cardHeight) {
		int top = cardHeight + 2;
		if (total > MAX_DOTS) {
			String text = (index + 1) + "/" + total;
			g.drawString(font, text, (width - font.width(text)) / 2, top, DOT_CURRENT, true);
			return;
		}
		int dotsWidth = total * DOT + (total - 1) * DOT_GAP;
		int left = (width - dotsWidth) / 2;
		for (int i = 0; i < total; i++) {
			int dx = left + i * (DOT + DOT_GAP);
			g.fill(dx - 1, top - 1, dx + DOT + 1, top + DOT + 1, DOT_OUTLINE);
			g.fill(dx, top, dx + DOT, top + DOT, i == index ? DOT_CURRENT : DOT_OTHER);
		}
	}
}
