package com.samurailemon.craftersalmanac.config;

import com.samurailemon.craftersalmanac.gui.InventorySorter;
import com.samurailemon.craftersalmanac.gui.ScreenSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ConfigScreen extends Screen {
	/** Settings go in two columns, so they all fit above Done even on small screens. */
	private static final int COLUMN_WIDTH = 200;
	private static final int GAP = 10;
	private static final int TOP = 40;
	private static final int ROW = 24;
	private final Screen parent;
	/** How many settings are laid out so far; the next goes in the spot after them. */
	private int placed;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("craftersalmanac.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ViewerConfig config = ViewerConfig.get();
		placed = 0;
		toggle("show_viewer_button", config.showViewerButton, value -> config.showViewerButton = value);
		toggle("hide_vanilla_button", config.hideVanillaRecipeButton, value -> config.hideVanillaRecipeButton = value);
		toggle("show_recipe_ids", config.showRecipeIds, value -> config.showRecipeIds = value);
		toggle("highlight_search", config.highlightSearch, value -> config.highlightSearch = value);
		place(CycleButton.builder(InventorySorter.Mode::title)
				.withValues(InventorySorter.Mode.values())
				.withInitialValue(config.sortMode)
				.withTooltip(mode -> Tooltip.create(Component.translatable("craftersalmanac.config.sort_mode.tooltip")))
				.create(0, 0, COLUMN_WIDTH, 20, Component.translatable("craftersalmanac.config.sort_mode"),
						(button, mode) -> config.sortMode = mode));
		toggle("quick_stack", config.quickStack, value -> config.quickStack = value);
		toggle("tooltip_details", config.tooltipDetails, value -> config.tooltipDetails = value);
		toggle("recipe_preview", config.recipePreview, value -> config.recipePreview = value);
		toggle("match_texture_pack", config.matchTexturePack, value -> config.matchTexturePack = value);
		toggle("developer_mode", config.developerMode, value -> config.developerMode = value);

		// Opened from a crafting screen (right-clicking our button): offer to switch the viewer off just there.
		if (ScreenSupport.isCraftingScreen(parent)) {
			String screenName = parent.getClass().getSimpleName();
			place(CycleButton.onOffBuilder(!ScreenSupport.isDisabled(parent))
					.withTooltip(current -> Tooltip.create(Component.translatable("craftersalmanac.config.show_on_screen.tooltip", screenName)))
					.create(0, 0, COLUMN_WIDTH, 20, Component.translatable("craftersalmanac.config.show_on_screen"),
							(button, show) -> {
								if (show == ScreenSupport.isDisabled(parent)) ScreenSupport.toggleDisabled(parent);
							}));
		}
		if (!config.disabledScreens.isEmpty()) {
			place(Button.builder(Component.translatable("craftersalmanac.config.enable_all", config.disabledScreens.size()), button -> {
						config.disabledScreens.clear();
						ViewerConfig.save();
						rebuildWidgets();
					})
					.tooltip(Tooltip.create(Component.translatable("craftersalmanac.config.enable_all.tooltip")))
					.build());
		}

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 27, 200, 20)
				.build());
	}

	private void toggle(String key, boolean value, Consumer<Boolean> setter) {
		String prefix = "craftersalmanac.config." + key;
		Tooltip tooltip = Tooltip.create(Component.translatable(prefix + ".tooltip"));
		place(CycleButton.onOffBuilder(value)
				.withTooltip(current -> tooltip)
				.create(0, 0, COLUMN_WIDTH, 20, Component.translatable(prefix), (button, newValue) -> setter.accept(newValue)));
	}

	/** Adds a setting in the next spot, filling the two columns left to right, a row at a time. */
	private void place(AbstractWidget widget) {
		int columnWidth = Math.min(COLUMN_WIDTH, (width - 3 * GAP) / 2);
		int column = placed % 2;
		int row = placed / 2;
		widget.setRectangle(columnWidth, 20, width / 2 - columnWidth - GAP / 2 + column * (columnWidth + GAP), TOP + row * ROW);
		placed++;
		addRenderableWidget(widget);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
	}

	@Override
	public void onClose() {
		ViewerConfig.save();
		minecraft.setScreen(parent);
	}
}
