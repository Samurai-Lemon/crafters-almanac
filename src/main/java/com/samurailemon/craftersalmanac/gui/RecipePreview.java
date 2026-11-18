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
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * While Shift is held over an item that has a recipe, cuts its tooltip down to just its name and draws its recipe
 * card right under it, so a recipe can be checked without opening the almanac. The name and card are placed together
 * as one block (GuiGraphicsMixin shortens and moves the tooltip), with the station's name along the top of the card. Items with
 * several recipes cycle through them, starting from the first each time a new item is hovered, with dots under the
 * card showing how many there are and which one is shown.
 */
public final class RecipePreview {
	/** Vanilla puts a tooltip's text this far right of, and above, the mouse. */
	private static final int TOOLTIP_OFFSET = 12;
	/** A tooltip's background and border reach this far around its text. */
	private static final int TOOLTIP_BORDER = 4;
	/** When there's no room on the right, vanilla puts the tooltip's right edge this far left of the mouse. */
	private static final int TOOLTIP_FLIPPED = 20;
	/** Between the tooltip and the card. */
	private static final int GAP = 2;
	private static final int EDGE = 2;
	/** In front of everything else on the screen, tooltips included. */
	private static final float Z = 500.0F;
	/** Smaller than on the almanac's pages, so it doesn't cover much of the screen. */
	private static final float SCALE = 0.95F;
	/** The header sits in the strip between the card's top border and its first slots. */
	private static final int HEADER_Y = 6;
	/** Room added above the contents of cards without a strip of their own, for the header. */
	private static final int TITLE_SPACE = 9;
	private static final String ELLIPSIS = "...";
	/** Long names wrap onto more lines, up to this many. */
	private static final int MAX_TITLE_LINES = 3;
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
	 * every frame: when that was, so its recipes cycle from the first, and its recipes.
	 */
	private static ItemStack shown = ItemStack.EMPTY;
	private static long since;
	private static List<Display> recipes = List.of();

	/** While a screen with the almanac is being drawn, finds the item under the mouse. Null between frames. */
	private static Supplier<ItemStack> hovered;

	/**
	 * This frame's card, worked out the first time it's needed (when the tooltip is placed, or when the card is
	 * drawn), so the tooltip makes room for the same card that gets drawn.
	 */
	private static boolean prepared;
	private static boolean active;
	private static int index;
	private static Display display;
	private static List<FormattedCharSequence> title;
	private static int titleSpace;
	private static int cardWidth;
	private static int cardHeight;
	/** Where the tooltip left room for the card this frame, in real screen coordinates, if it did. */
	private static boolean placed;
	private static int placedX;
	private static int placedY;

	private RecipePreview() {
	}

	/** Called before a screen with the almanac is drawn. */
	public static void beginFrame(Supplier<ItemStack> hoveredStack) {
		hovered = hoveredStack;
		prepared = false;
		placed = false;
	}

	/**
	 * Called by GuiGraphicsMixin before a tooltip is drawn: true while a card is shown under it, so the tooltip is cut
	 * down to just the item's name. Only the first tooltip of the frame, the one the card goes under.
	 */
	public static boolean shortensTooltip() {
		return !placed && prepare();
	}

	/**
	 * Called by GuiGraphicsMixin as a tooltip is placed, with the tooltip's size and where vanilla would put it, all
	 * in the coordinates it's drawn in; {@code offsetX} and {@code offsetY} turn those into real screen coordinates.
	 * While a card is shown, returns where the tooltip's text goes so the card fits right under it, or null to leave
	 * the tooltip alone.
	 */
	public static int[] placeTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height,
			int offsetX, int offsetY) {
		if (placed || !prepare()) return null;
		int boxWidth = width + 2 * TOOLTIP_BORDER;
		int boxHeight = height + 2 * TOOLTIP_BORDER;
		int blockWidth = Math.max(boxWidth, cardWidth);
		int blockHeight = boxHeight + GAP + cardHeight;
		int left = mouseX + TOOLTIP_OFFSET - TOOLTIP_BORDER;
		if (left + blockWidth > screenWidth - EDGE) left = mouseX - TOOLTIP_FLIPPED - blockWidth;
		left = Mth.clamp(left, EDGE, Math.max(EDGE, screenWidth - blockWidth - EDGE));
		int top = Mth.clamp(mouseY - TOOLTIP_OFFSET - TOOLTIP_BORDER, EDGE, Math.max(EDGE, screenHeight - blockHeight - EDGE));
		placed = true;
		placedX = left + offsetX;
		placedY = top + boxHeight + GAP + offsetY;
		return new int[] {left + TOOLTIP_BORDER, top + TOOLTIP_BORDER};
	}

	/** Called after the screen is drawn, with the real mouse position. */
	public static void render(GuiGraphics g, int mouseX, int mouseY) {
		boolean show = hovered != null && prepare();
		hovered = null;
		if (!show) return;
		int x;
		int y;
		if (placed) {
			x = placedX;
			y = placedY;
		} else {
			// No tooltip this frame: where vanilla would put one, right of the mouse or left of it.
			int screenWidth = g.guiWidth();
			x = mouseX + TOOLTIP_OFFSET;
			if (x + cardWidth > screenWidth - EDGE) x = mouseX - TOOLTIP_OFFSET - cardWidth;
			x = Mth.clamp(x, EDGE, Math.max(EDGE, screenWidth - cardWidth - EDGE));
			y = Mth.clamp(mouseY - TOOLTIP_OFFSET, EDGE, Math.max(EDGE, g.guiHeight() - cardHeight - EDGE));
		}
		Font font = Minecraft.getInstance().font;
		int bodyHeight = cardHeight - (recipes.size() > 1 ? FOOTER : 0);
		g.pose().pushPose();
		g.pose().translate(x, y, Z);
		if (recipes.size() > 1) count(g, font, index, recipes.size(), cardWidth, bodyHeight);
		g.pose().scale(SCALE, SCALE, 1.0F);
		RecipeRenderer.render(g, font, display, 0, 0, false, titleSpace);
		header(g, font, title);
		g.pose().popPose();
	}

	/** Works out this frame's card, once. True when there's one to show. */
	private static boolean prepare() {
		if (prepared) return active;
		prepared = true;
		active = hovered != null && update(hovered.get());
		if (!active) return false;
		Font font = Minecraft.getInstance().font;
		index = (int) ((Util.getMillis() - since) / CYCLE_MILLIS % recipes.size());
		display = recipes.get(index);
		title = titleLines(font, display, display.category().station().getString());
		// Lines past the first push the card's contents down, making the card taller.
		titleSpace = (hasStrip(display) ? 0 : TITLE_SPACE) + (title.size() - 1) * font.lineHeight;
		cardWidth = Mth.ceil(display.width() * SCALE);
		cardHeight = Mth.ceil((display.height() + titleSpace) * SCALE) + (recipes.size() > 1 ? FOOTER : 0);
		return true;
	}

	/** Catches up with the hovered item. True when the preview is on and that item has a recipe to show. */
	private static boolean update(ItemStack stack) {
		if (!ViewerConfig.get().recipePreview || !Screen.hasShiftDown() || stack.isEmpty()) {
			shown = ItemStack.EMPTY;
			return false;
		}
		if (!ItemStack.isSameItemSameComponents(stack, shown)) {
			shown = stack.copy();
			since = Util.getMillis();
			recipes = recipesFor(stack);
		}
		return !recipes.isEmpty();
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
	 * previews to make room). Other cards get one added (TITLE_SPACE).
	 */
	private static boolean hasStrip(Display display) {
		Layout layout = display.layout();
		return layout == Layout.CRAFTING || layout == Layout.COOKING || layout == Layout.BREWING;
	}

	/**
	 * The card's title, wrapped to fit the card's width, at most MAX_TITLE_LINES lines (the last cut short with "..."
	 * in the rare case even that isn't enough).
	 */
	private static List<FormattedCharSequence> titleLines(Font font, Display display, String title) {
		int room = display.width() - 2 * (Card.BORDER + 1);
		List<FormattedText> wrapped = font.getSplitter().splitLines(title, room, Style.EMPTY);
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (int i = 0; i < Math.min(wrapped.size(), MAX_TITLE_LINES); i++) {
			String line = wrapped.get(i).getString();
			if (i == MAX_TITLE_LINES - 1 && wrapped.size() > MAX_TITLE_LINES) {
				line = font.plainSubstrByWidth(line, room - font.width(ELLIPSIS)) + ELLIPSIS;
			}
			lines.add(Language.getInstance().getVisualOrder(FormattedText.of(line)));
		}
		return lines;
	}

	/** The station's name ("Smithing Table", "Cooking Pot") along the top of the card. */
	private static void header(GuiGraphics g, Font font, List<FormattedCharSequence> lines) {
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(font, lines.get(i), Card.BORDER + 1, HEADER_Y + i * font.lineHeight, Theme.cardText(), false);
		}
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
