package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import com.samurailemon.craftersalmanac.recipe.Card;
import com.samurailemon.craftersalmanac.recipe.Category;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.DisplaySlot;
import com.samurailemon.craftersalmanac.recipe.ItemList;
import com.samurailemon.craftersalmanac.recipe.Layout;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The item list / recipe panel, drawn with the vanilla recipe book's own textures and sprites and
 * laid out with the same offsets as RecipeBookComponent, so it reads as "the recipe book".
 * It sits left of the container like the real recipe book, so only one of the two is open at a time.
 */
public class ViewerPanel extends AbstractWidget {
	private static final int WIDTH = 147;
	private static final int HEIGHT = 166;
	/** How far the category tabs stick out to the left of the book. */
	private static final int TAB_OUT = 32;
	private static final int GRID_X = 11;
	private static final int GRID_Y = 31;
	private static final int CELL = 25;
	private static final int COLS = 5;
	private static final int ROWS = 4;
	private static final int CONTENT_WIDTH = 125;
	/** Space between the book and the container. */
	private static final int GAP = 2;
	/** Clickable area of the favorite star. */
	private static final int FAVORITE_BUTTON = 11;
	private static final int TRANSFER_SIZE = 11;

	private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");
	private static final ResourceLocation TAB = ResourceLocation.withDefaultNamespace("recipe_book/tab");
	private static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace("recipe_book/tab_selected");
	private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");
	private static final ResourceLocation PAGE_FORWARD = ResourceLocation.withDefaultNamespace("recipe_book/page_forward");
	private static final ResourceLocation PAGE_FORWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("recipe_book/page_forward_highlighted");
	private static final ResourceLocation PAGE_BACKWARD = ResourceLocation.withDefaultNamespace("recipe_book/page_backward");
	private static final ResourceLocation PAGE_BACKWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("recipe_book/page_backward_highlighted");
	private static final ResourceLocation FILTER_ENABLED = ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled");
	private static final ResourceLocation FILTER_DISABLED = ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled");
	private static final ResourceLocation FILTER_ENABLED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled_highlighted");
	private static final ResourceLocation FILTER_DISABLED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled_highlighted");
	private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
	private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
	private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
	private static final Component SEARCH_HINT = Component.translatable("gui.recipebook.search_hint").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
	private static final ItemStack ALL_ITEMS_ICON = new ItemStack(Items.COMPASS);
	private static final ResourceLocation FAVORITE = ResourceLocation.fromNamespaceAndPath("craftersalmanac", "favorite");
	private static final int FAVORITE_SIZE = 9;
	private static final int TAB_HEIGHT = 27;
	/** Recipe tabs that fit below the All Items and Favorites tabs. More than this and the last becomes "+N". */
	private static final int CATEGORY_TABS = 4;
	/** Spacing between the lines of a heading that wraps. */
	public static final int HEADING_LINE = 10;
	private static final String ELLIPSIS = "...";
	private static final int MATERIAL_CELL = 18;
	private static final int MATERIAL_COLS = 6;
	private static final int MATERIAL_ROWS = 4;

	/** Something under the mouse: an item, or a button with an optional tooltip. Rebuilt every frame. */
	private record Hit(int x, int y, int w, int h, ItemStack stack, List<Component> extra, Component tooltip, Runnable action) {
		static Hit item(int x, int y, int size, ItemStack stack, List<Component> extra) {
			return new Hit(x, y, size, size, stack, extra, null, null);
		}

		static Hit button(int x, int y, int w, int h, Component tooltip, Runnable action) {
			return button(x, y, w, h, tooltip, List.of(), action);
		}

		static Hit button(int x, int y, int w, int h, Component tooltip, List<Component> extra, Runnable action) {
			return new Hit(x, y, w, h, ItemStack.EMPTY, extra, tooltip, action);
		}

		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
		}
	}

	private final AbstractContainerScreen<?> screen;
	private final Font font;
	private final EditBox search;
	private final List<Hit> hits = new ArrayList<>();
	/** The panel for the open screen, so the keyboard mixin can find it. */
	private static ViewerPanel active;

	private boolean shown;
	/** Set when a click lands on the search box, so {@link #setFocused} keeps it focused. */
	private boolean searchClicked;
	/** The item list after search and the craftable filter, cached until either changes. */
	private List<ItemStack> browseItems = List.of();
	private List<ItemStack> browseSearchResult;
	private Set<Item> browseCraftable;
	private int bookX;
	private int bookY;

	public ViewerPanel(AbstractContainerScreen<?> screen) {
		super(0, 0, WIDTH + TAB_OUT, HEIGHT, Component.translatable("key.categories.craftersalmanac"));
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
		this.search = new EditBox(font, 0, 0, 81, font.lineHeight + 5, Component.translatable("itemGroup.search"));
		search.setMaxLength(50);
		search.setTextColor(0xFFFFFF);
		search.setHint(SEARCH_HINT);
		search.setValue(ViewerState.search);
		search.setResponder(value -> {
			if (value.equals(ViewerState.search)) return;
			ViewerState.search = value;
			ViewerState.browsePage = 0;
			ViewerState.browse();
		});
		layout();
		active = this;
	}

	public AbstractContainerScreen<?> screen() {
		return screen;
	}

	/** The open screen's panel if it's showing, else null. */
	public static ViewerPanel active() {
		ViewerPanel panel = active;
		return panel != null && panel.screen == Minecraft.getInstance().screen && panel.shown ? panel : null;
	}

	/**
	 * How far to move a centered container right so the open panel fits on its left, like the vanilla recipe
	 * book does (see ScreenShift). The panel and container (with any extras sticking out of it) are centered
	 * together. Returns 0 if they can't both fit, the panel isn't showing, or the viewer is off for this screen.
	 */
	public static int containerOffset(Screen screen, int screenWidth, int leftPos, int imageWidth) {
		if (screen == null || !ViewerState.isShowing() || ScreenSupport.isDisabled(screen)) return 0;
		ScreenSupport.Extras extras = ScreenSupport.extras(screen);
		int total = TAB_OUT + WIDTH + GAP + extras.left() + imageWidth + extras.right();
		int newLeftPos = Math.max((screenWidth - total) / 2, 0) + TAB_OUT + WIDTH + GAP + extras.left();
		if (newLeftPos + imageWidth + extras.right() > screenWidth) return 0;
		return Math.max(newLeftPos - leftPos, 0);
	}

	/**
	 * Recomputed every frame and before input. Positions are in the screen's own layout; the whole screen, panel
	 * included, is drawn shifted right by ScreenShift to make room.
	 */
	private void layout() {
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		bookX = container.rbv$getLeftPos() - ScreenSupport.extras(screen).left() - GAP - WIDTH;
		bookY = Mth.clamp(container.rbv$getTopPos() + (container.rbv$getImageHeight() - HEIGHT) / 2, 2, Math.max(2, screen.height - HEIGHT - 2));
		// Not enough room beside the container (very high GUI scale): hide rather than cover the inventory.
		shown = ViewerState.isShowing() && bookX + ScreenShift.of(screen) >= TAB_OUT;
		setPosition(bookX - TAB_OUT, bookY);
		search.setPosition(bookX + 25, bookY + 13);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		layout();
		hits.clear();
		if (!shown) return;

		g.blit(BOOK, bookX, bookY, 1, 1, WIDTH, HEIGHT);
		renderTabs(g);
		search.render(g, mouseX, mouseY, partialTick);
		RecipeView view = ViewerState.view;
		if (ViewerState.materials != null) {
			renderMaterials(g, ViewerState.materials, mouseX, mouseY);
		} else if (view == null) {
			renderFilterButton(g, mouseX, mouseY);
			renderItemGrid(g);
		} else {
			renderRecipes(g, view, mouseX, mouseY);
		}
		renderPageButtons(g, mouseX, mouseY);
	}

	private void renderTabs(GuiGraphics g) {
		RecipeView view = ViewerState.view;
		boolean calculator = ViewerState.materials != null;
		List<Category> categories = view == null || calculator ? List.of() : view.categories;

		renderTab(g, 0, ALL_ITEMS_ICON, null, view == null && !calculator, Component.translatable("craftersalmanac.tab.all_items"), ViewerState::browse);
		renderTab(g, 1, null, null, view != null && view.favorites && !calculator, Component.translatable("craftersalmanac.tab.favorites"), ViewerState::showFavorites);
		int count = categories.size();
		if (count == 0) return;
		// Modpacks can give one item recipes in a dozen machine types. Show a few tabs at a time, the last spot
		// becoming "+N" to show the next few (the mouse wheel over the tabs scrolls them too).
		boolean overflow = count > CATEGORY_TABS;
		int shownTabs = overflow ? CATEGORY_TABS - 1 : count;
		view.tabScroll = Math.floorMod(view.tabScroll, count);
		for (int i = 0; i < shownTabs; i++) {
			Category category = categories.get((view.tabScroll + i) % count);
			Component tooltip = Component.empty().append(category.title()).append(" (" + view.count(category) + ")");
			renderTab(g, i + 2, category.icon, null, view.selected == category, tooltip, () -> view.select(category));
		}
		if (overflow) {
			Component tooltip = Component.translatable("craftersalmanac.tab.more", count - shownTabs, count);
			renderTab(g, CATEGORY_TABS + 1, null, "+" + (count - shownTabs), false, tooltip, () -> view.tabScroll += shownTabs);
		}
	}

	/**
	 * One tab down the side of the book, showing an item icon, a short label, or (with neither) our star for the
	 * favorites tab, drawn at its real size where an item icon goes.
	 */
	private void renderTab(GuiGraphics g, int index, ItemStack icon, String label, boolean selected, Component tooltip, Runnable action) {
		int x = bookX - 30 + (selected ? -2 : 0);
		int y = bookY + 3 + TAB_HEIGHT * index;
		g.blitSprite(selected ? TAB_SELECTED : TAB, x, y, 35, TAB_HEIGHT);
		if (icon != null) {
			g.renderFakeItem(icon, x + 9, y + 5);
		} else if (label != null) {
			g.drawString(font, label, x + 17 - font.width(label) / 2, y + 9, 0xFFFFFFFF, true);
		} else {
			int inset = (16 - FAVORITE_SIZE) / 2;
			g.blitSprite(FAVORITE, x + 9 + inset, y + 5 + inset, FAVORITE_SIZE, FAVORITE_SIZE);
		}
		hits.add(Hit.button(x, y, 35, TAB_HEIGHT, tooltip, action));
	}

	/** Vanilla's "Showing Craftable / Showing All" toggle, in the same spot as in the vanilla book. */
	private void renderFilterButton(GuiGraphics g, int mouseX, int mouseY) {
		boolean enabled = ViewerConfig.get().craftableOnly;
		Component tooltip = Component.translatable(enabled ? "gui.recipebook.toggleRecipes.craftable" : "gui.recipebook.toggleRecipes.all");
		Hit hit = Hit.button(bookX + 110, bookY + 12, 26, 16, tooltip, () -> {
			ViewerConfig.get().craftableOnly = !ViewerConfig.get().craftableOnly;
			ViewerConfig.save();
			ViewerState.browsePage = 0;
		});
		boolean hovered = hit.contains(mouseX, mouseY);
		ResourceLocation sprite = enabled
				? hovered ? FILTER_ENABLED_HIGHLIGHTED : FILTER_ENABLED
				: hovered ? FILTER_DISABLED_HIGHLIGHTED : FILTER_DISABLED;
		g.blitSprite(sprite, hit.x(), hit.y(), hit.w(), hit.h());
		hits.add(hit);
	}

	private List<ItemStack> browseItems() {
		List<ItemStack> searchResult = ItemList.filtered(ViewerState.search);
		Set<Item> craftable = ViewerConfig.get().craftableOnly ? Craftable.items(screen.getMenu()) : null;
		if (searchResult != browseSearchResult || craftable != browseCraftable) {
			browseSearchResult = searchResult;
			browseCraftable = craftable;
			browseItems = craftable == null ? searchResult : searchResult.stream().filter(stack -> craftable.contains(stack.getItem())).toList();
		}
		return browseItems;
	}

	private void renderItemGrid(GuiGraphics g) {
		List<ItemStack> items = browseItems();
		ViewerState.browsePage = Mth.clamp(ViewerState.browsePage, 0, itemPageCount(items) - 1);
		if (items.isEmpty()) {
			Component text = Component.translatable(ViewerConfig.get().craftableOnly ? "craftersalmanac.no_craftable_items" : "craftersalmanac.no_items");
			centeredText(g, text, bookY + 75);
			return;
		}

		int start = ViewerState.browsePage * COLS * ROWS;
		for (int i = 0; i < COLS * ROWS && start + i < items.size(); i++) {
			ItemStack stack = items.get(start + i);
			int x = bookX + GRID_X + CELL * (i % COLS);
			int y = bookY + GRID_Y + CELL * (i / COLS);
			g.blitSprite(SLOT, x, y, CELL, CELL);
			g.renderItem(stack, x + 4, y + 4);
			hits.add(Hit.item(x, y, CELL, stack, List.of()));
		}
	}

	private void renderRecipes(GuiGraphics g, RecipeView view, int mouseX, int mouseY) {
		int headingLines = renderHeading(g, view.header(), bookX + GRID_X, bookY + GRID_Y + 1);
		if (view.isEmpty()) {
			int below = centeredText(g, Component.translatable("craftersalmanac.favorites.empty"), bookY + 70);
			centeredText(g, Component.translatable("craftersalmanac.favorites.empty.hint"), below + 2);
			return;
		}

		int y = bookY + GRID_Y + 12 + (headingLines - 1) * HEADING_LINE;
		for (Display display : view.currentPage()) {
			int x = bookX + GRID_X + (CONTENT_WIDTH - display.width()) / 2;
			RecipeRenderer.render(g, font, display, x, y);
			List<Component> idLine = ViewerConfig.get().showRecipeIds && !display.category().isVirtual()
					? List.of(Component.translatable("craftersalmanac.recipe_id", display.id().toString()).withStyle(ChatFormatting.DARK_GRAY))
					: List.of();
			for (DisplaySlot slot : display.slots()) {
				if (!slot.shown()) continue;
				hits.add(Hit.item(x + slot.x(), y + slot.y(), slot.size(), slot.current(), slot.output() ? idLine : List.of()));
			}
			if (display.shapeless()) {
				// Over the arrow drawn in the crafting card.
				hits.add(Hit.button(x + Card.CRAFTING.x(90), y + 34, 22, 16, Component.translatable("craftersalmanac.shapeless"), null));
			}
			// The star where the card puts it, then the fill and calculator buttons to its left.
			int starX = x + display.buttonX();
			int buttonY = y + display.buttonY();
			renderFavoriteButton(g, display, starX, buttonY, mouseX, mouseY);
			RecipeTransfer.Check check = RecipeTransfer.check(display, screen.getMenu());
			int nextX = starX;
			if (check.status() != RecipeTransfer.Status.UNSUPPORTED) {
				nextX -= 1 + TRANSFER_SIZE;
				renderTransferButton(g, display, check, nextX, buttonY, x, y, mouseX, mouseY);
			}
			if (display.layout() == Layout.CRAFTING) {
				renderMaterialsButton(g, display, nextX - 1 - TRANSFER_SIZE, buttonY, mouseX, mouseY);
			}
			y += display.height() + RecipeView.GAP;
		}
	}

	/**
	 * A heading as full-size lines across the page: wrapped onto a second line when it doesn't fit, and if even two
	 * lines aren't enough, the second ends in "...". Static so RecipeView can leave room for it when making pages.
	 */
	public static List<String> headingLines(Font font, Component text) {
		List<String> lines = new ArrayList<>();
		for (FormattedText line : font.getSplitter().splitLines(text, CONTENT_WIDTH, Style.EMPTY)) {
			lines.add(line.getString());
		}
		if (lines.size() <= 2) return lines;
		String rest = String.join(" ", lines.subList(1, lines.size()));
		return List.of(lines.get(0), font.plainSubstrByWidth(rest, CONTENT_WIDTH - font.width(ELLIPSIS)) + ELLIPSIS);
	}

	/** Draws a heading (see headingLines) and returns how many lines it took. Hover shows the full text if it was cut. */
	private int renderHeading(GuiGraphics g, Component text, int x, int y) {
		List<String> lines = headingLines(font, text);
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(font, lines.get(i), x, y + i * HEADING_LINE, BookColors.textColor(), false);
		}
		if (lines.get(lines.size() - 1).endsWith(ELLIPSIS) && !text.getString().endsWith(ELLIPSIS)) {
			hits.add(Hit.button(x, y, CONTENT_WIDTH, lines.size() * HEADING_LINE, text, null));
		}
		return lines.size();
	}

	/** A message centred on the page, wrapped to the page width. Returns the y just below it. */
	private int centeredText(GuiGraphics g, Component text, int y) {
		for (FormattedCharSequence line : font.split(text, CONTENT_WIDTH)) {
			g.drawString(font, line, bookX + 73 - font.width(line) / 2, y, BookColors.textColor(), false);
			y += HEADING_LINE;
		}
		return y;
	}

	/** The plain button (left of the fill button) that opens the materials calculator for a crafting recipe. */
	private void renderMaterialsButton(GuiGraphics g, Display display, int x, int y, int mouseX, int mouseY) {
		Hit hit = Hit.button(x, y, TRANSFER_SIZE, TRANSFER_SIZE, Component.translatable("craftersalmanac.materials.button"),
				List.of(Component.translatable("craftersalmanac.materials.button.hint").withStyle(ChatFormatting.GRAY)),
				() -> ViewerState.showMaterials(display, RecipeTransfer.available(screen.getMenu())));
		hits.add(hit);
		g.blitSprite(hit.contains(mouseX, mouseY) ? BUTTON_HIGHLIGHTED : BUTTON, hit.x(), hit.y(), hit.w(), hit.h());
	}

	/** An 11x11 vanilla button with one character on it. */
	private void smallButton(GuiGraphics g, Hit hit, String label, int color, int mouseX, int mouseY) {
		g.blitSprite(hit.contains(mouseX, mouseY) ? BUTTON_HIGHLIGHTED : BUTTON, hit.x(), hit.y(), hit.w(), hit.h());
		g.drawString(font, label, hit.x() + (hit.w() - font.width(label) + 1) / 2, hit.y() + 2, color, true);
	}

	/**
	 * The materials calculator page: what you're making and how many (with - and + buttons), then a grid of raw
	 * materials (count green if you have enough, red if not) followed by the crafted steps in between.
	 */
	private void renderMaterials(GuiGraphics g, Materials materials, int mouseX, int mouseY) {
		int left = bookX + GRID_X;
		int top = bookY + GRID_Y;
		// A heading that wraps pushes everything below it down a line.
		top += (renderHeading(g, materialsHeading(materials), left, top + 1) - 1) * HEADING_LINE;

		Map<Item, Integer> have = RecipeTransfer.available(screen.getMenu());
		String amount = "\u00D7" + materials.amount;
		int amountX = left + (CONTENT_WIDTH - font.width(amount)) / 2;
		g.drawString(font, amount, amountX, top + 14, BookColors.textColor(), false);
		amountButton(g, materials, have, "-", -1, amountX - 3 - TRANSFER_SIZE, top + 12, mouseX, mouseY);
		amountButton(g, materials, have, "+", 1, amountX + font.width(amount) + 3, top + 12, mouseX, mouseY);

		List<Materials.Entry> entries = materials.entries();
		if (entries.isEmpty()) {
			centeredText(g, Component.translatable("craftersalmanac.materials.empty"), top + 50);
			return;
		}
		int perPage = MATERIAL_COLS * materialRows(materials);
		materials.page = Mth.clamp(materials.page, 0, materialsPageCount(materials) - 1);
		int gridLeft = left + (CONTENT_WIDTH - MATERIAL_COLS * MATERIAL_CELL) / 2;
		int gridTop = top + 27;
		int start = materials.page * perPage;
		for (int i = 0; i < perPage && start + i < entries.size(); i++) {
			Materials.Entry entry = entries.get(start + i);
			int x = gridLeft + MATERIAL_CELL * (i % MATERIAL_COLS);
			int y = gridTop + MATERIAL_CELL * (i / MATERIAL_COLS);
			RecipeRenderer.slot(g, x, y, MATERIAL_CELL);
			g.renderItem(entry.stack(), x + 1, y + 1);

			int owned = have.getOrDefault(entry.stack().getItem(), 0);
			ChatFormatting color = entry.crafted() ? ChatFormatting.WHITE : owned >= entry.count() ? ChatFormatting.GREEN : ChatFormatting.RED;
			g.renderItemDecorations(font, entry.stack(), x + 1, y + 1, color + shortCount(entry.count()));

			List<Component> lines = entry.crafted()
					? entry.spare() > 0
							? List.of(Component.translatable("craftersalmanac.materials.crafted", entry.count()).withStyle(ChatFormatting.GRAY),
									Component.translatable("craftersalmanac.materials.spare", entry.spare()).withStyle(ChatFormatting.GRAY))
							: List.of(Component.translatable("craftersalmanac.materials.crafted", entry.count()).withStyle(ChatFormatting.GRAY))
					: List.of(
							Component.translatable("craftersalmanac.materials.need", entry.count()).withStyle(ChatFormatting.GRAY),
							Component.translatable("craftersalmanac.materials.have", owned).withStyle(color));
			hits.add(Hit.item(x, y, MATERIAL_CELL, entry.stack(), lines));
		}
	}

	private void amountButton(GuiGraphics g, Materials materials, Map<Item, Integer> have, String label, int direction, int x, int y, int mouseX, int mouseY) {
		Hit hit = Hit.button(x, y, TRANSFER_SIZE, TRANSFER_SIZE, Component.translatable("craftersalmanac.materials.amount"),
				() -> materials.setAmount(materials.amount + direction * (Screen.hasShiftDown() ? 10 : 1), have));
		hits.add(hit);
		smallButton(g, hit, label, 0xFFFFFFFF, mouseX, mouseY);
	}

	/** Fits counts in a slot corner: 1234 becomes 1.2k. */
	private static String shortCount(int count) {
		if (count < 1000) return Integer.toString(count);
		return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
	}

	private int materialsPageCount(Materials materials) {
		return Math.max(1, Mth.positiveCeilDiv(materials.entries().size(), MATERIAL_COLS * materialRows(materials)));
	}

	private static Component materialsHeading(Materials materials) {
		return Component.translatable("craftersalmanac.materials.header", materials.result().getHoverName());
	}

	/** One row fewer when the heading takes two lines, so the grid stays clear of the page buttons. */
	private int materialRows(Materials materials) {
		return MATERIAL_ROWS - (headingLines(font, materialsHeading(materials)).size() - 1);
	}

	/** The star that adds or removes a recipe from the favorites tab. */
	private void renderFavoriteButton(GuiGraphics g, Display display, int x, int y, int mouseX, int mouseY) {
		boolean favorite = Favorites.contains(display);
		Component tooltip = Component.translatable(favorite ? "craftersalmanac.favorite.remove" : "craftersalmanac.favorite.add");
		Hit hit = Hit.button(x, y, FAVORITE_BUTTON, FAVORITE_BUTTON, tooltip, () -> Favorites.toggle(display));
		hits.add(hit);
		// Full colour when it's a favorite, faded when not, a bit stronger on hover.
		float alpha = favorite ? 1.0F : hit.contains(mouseX, mouseY) ? 0.7F : 0.3F;
		int inset = (FAVORITE_BUTTON - FAVORITE_SIZE) / 2;
		RenderSystem.enableBlend();
		g.setColor(1.0F, 1.0F, 1.0F, alpha);
		g.blitSprite(FAVORITE, x + inset, y + inset, FAVORITE_SIZE, FAVORITE_SIZE);
		g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}

	/** The small "+" next to the star that fills the crafting grid or furnace. */
	private void renderTransferButton(GuiGraphics g, Display display, RecipeTransfer.Check check, int x, int y, int recipeX, int recipeY, int mouseX, int mouseY) {
		Hit hit = switch (check.status()) {
			case READY -> Hit.button(x, y, TRANSFER_SIZE, TRANSFER_SIZE, Component.translatable("craftersalmanac.transfer"),
					List.of(Component.translatable("craftersalmanac.transfer.max").withStyle(ChatFormatting.GRAY)),
					() -> RecipeTransfer.transfer(display, screen.getMenu(), Screen.hasShiftDown()));
			case MISSING -> Hit.button(x, y, TRANSFER_SIZE, TRANSFER_SIZE, Component.translatable("craftersalmanac.transfer.missing").withStyle(ChatFormatting.RED), null);
			default -> Hit.button(x, y, TRANSFER_SIZE, TRANSFER_SIZE, Component.translatable("craftersalmanac.transfer.locked"),
					List.of(Component.translatable("craftersalmanac.transfer.locked.hint").withStyle(ChatFormatting.GRAY)), null);
		};
		hits.add(hit);

		boolean hovered = hit.contains(mouseX, mouseY);
		boolean locked = check.status() == RecipeTransfer.Status.LOCKED;
		g.blitSprite(locked ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON, x, y, TRANSFER_SIZE, TRANSFER_SIZE);
		int color = locked ? 0xFFA0A0A0 : check.status() == RecipeTransfer.Status.MISSING ? 0xFFFF5555 : 0xFFFFFFFF;
		g.drawString(font, "+", x + 3, y + 2, color, true);

		// Like REI: while hovering, tint the ingredients you don't have.
		if (hovered) {
			for (DisplaySlot slot : check.missing()) {
				int sx = recipeX + slot.x();
				int sy = recipeY + slot.y();
				g.fill(sx + 1, sy + 1, sx + slot.size() - 1, sy + slot.size() - 1, 0x80FF0000);
			}
		}
	}

	private void renderPageButtons(GuiGraphics g, int mouseX, int mouseY) {
		int pages = pageCount();
		if (pages <= 1) return;
		int page = page();
		String text = (page + 1) + "/" + pages;
		g.drawString(font, text, bookX + 73 - font.width(text) / 2, bookY + 141, -1, false);
		if (page > 0) pageButton(g, bookX + 38, PAGE_BACKWARD, PAGE_BACKWARD_HIGHLIGHTED, -1, mouseX, mouseY);
		if (page < pages - 1) pageButton(g, bookX + 93, PAGE_FORWARD, PAGE_FORWARD_HIGHLIGHTED, 1, mouseX, mouseY);
	}

	private void pageButton(GuiGraphics g, int x, ResourceLocation sprite, ResourceLocation highlighted, int delta, int mouseX, int mouseY) {
		int y = bookY + 137;
		Hit hit = Hit.button(x, y, 12, 17, null, () -> turnPage(delta));
		g.blitSprite(hit.contains(mouseX, mouseY) ? highlighted : sprite, x, y, 12, 17);
		hits.add(hit);
	}

	private static int itemPageCount(List<ItemStack> items) {
		return Math.max(1, Mth.positiveCeilDiv(items.size(), COLS * ROWS));
	}

	private int page() {
		if (ViewerState.materials != null) return ViewerState.materials.page;
		return ViewerState.view == null ? ViewerState.browsePage : ViewerState.view.page;
	}

	private int pageCount() {
		if (ViewerState.materials != null) return materialsPageCount(ViewerState.materials);
		return ViewerState.view == null ? itemPageCount(browseItems()) : ViewerState.view.pageCount();
	}

	private void turnPage(int delta) {
		int page = Mth.clamp(page() + delta, 0, pageCount() - 1);
		if (ViewerState.materials != null) {
			ViewerState.materials.page = page;
		} else if (ViewerState.view == null) {
			ViewerState.browsePage = page;
		} else {
			ViewerState.view.page = page;
		}
	}

	/** Checks the last-drawn first, so whatever is drawn on top gets the click. */
	private Hit hitAt(double mouseX, double mouseY) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (hits.get(i).contains(mouseX, mouseY)) return hits.get(i);
		}
		return null;
	}

	private boolean overBook(double mouseX, double mouseY) {
		return mouseX >= bookX && mouseY >= bookY && mouseX < bookX + WIDTH && mouseY < bookY + HEIGHT;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return shown && (overBook(mouseX, mouseY) || hitAt(mouseX, mouseY) != null);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		if (!shown) {
			search.setFocused(false);
			return false;
		}
		if (search.isMouseOver(mouseX, mouseY)) {
			search.setFocused(true);
			searchClicked = true;
			if (button == 1) {
				search.setValue("");
			} else {
				search.mouseClicked(mouseX, mouseY, button);
			}
			return swallow();
		}
		search.setFocused(false);

		Hit hit = hitAt(mouseX, mouseY);
		if (hit != null && !hit.stack().isEmpty() && (button == 0 || button == 1)) {
			if (ViewerState.show(hit.stack(), button == 1)) playClick();
			return swallow();
		}
		if (hit != null && hit.action() != null && button == 0) {
			hit.action().run();
			playClick();
			return swallow();
		}
		return isMouseOver(mouseX, mouseY) && swallow();
	}

	/** See {@link ViewerState#swallowNextRelease}. */
	private static boolean swallow() {
		ViewerState.swallowNextRelease = true;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
		RecipeView view = ViewerState.view;
		// Over the tabs (left of the book), scroll through recipe tabs when there are more than fit.
		if (mouseX < bookX && view != null && ViewerState.materials == null && view.categories.size() > CATEGORY_TABS) {
			view.tabScroll += scrollY > 0 ? -1 : 1;
			return true;
		}
		turnPage(scrollY > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return isSearchFocused() && search.charTyped(codePoint, modifiers);
	}

	/**
	 * After our mouseClicked returns, the screen re-focuses this panel with setFocused(false) then setFocused(true),
	 * even when it was already focused. Without restoring the search box here, clicking it would only work on the
	 * first click after the panel lost focus.
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			search.setFocused(false);
		} else if (searchClicked) {
			search.setFocused(true);
			searchClicked = false;
		}
	}

	public boolean isSearchFocused() {
		return shown && search.isFocused();
	}

	public void searchKeyPressed(int keyCode, int scanCode, int modifiers) {
		search.keyPressed(keyCode, scanCode, modifiers);
	}

	public ItemStack stackAt(double mouseX, double mouseY) {
		Hit hit = shown ? hitAt(mouseX, mouseY) : null;
		return hit == null ? ItemStack.EMPTY : hit.stack();
	}

	/**
	 * Called after the whole screen has rendered (with the real mouse position) so tooltips sit above the
	 * container's items. Applies the screen's shift itself, since it runs after the shifted drawing has ended.
	 */
	public void renderTooltip(GuiGraphics g, int realMouseX, int mouseY) {
		int shift = ScreenShift.of(screen);
		g.pose().pushPose();
		g.pose().translate(shift, 0, 0);
		ScreenShift.drawing = shift;
		renderShiftedTooltip(g, realMouseX - shift, mouseY);
		ScreenShift.drawing = 0;
		g.pose().popPose();
	}

	private void renderShiftedTooltip(GuiGraphics g, int mouseX, int mouseY) {
		if (!shown || !screen.getMenu().getCarried().isEmpty()) return;
		Hit hit = hitAt(mouseX, mouseY);
		if (hit == null) return;
		if (!hit.stack().isEmpty()) {
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hit.stack()));
			lines.addAll(hit.extra());
			g.renderTooltip(font, lines, hit.stack().getTooltipImage(), mouseX, mouseY);
		} else if (hit.tooltip() != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(hit.tooltip());
			lines.addAll(hit.extra());
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	private static void playClick() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
