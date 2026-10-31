package com.samurailemon.craftersalmanac.config;

import com.samurailemon.craftersalmanac.gui.ScreenSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 250;
	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("craftersalmanac.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ViewerConfig config = ViewerConfig.get();
		int y = 40;
		y = toggle(y, "show_viewer_button", config.showViewerButton, value -> config.showViewerButton = value);
		y = toggle(y, "hide_vanilla_button", config.hideVanillaRecipeButton, value -> config.hideVanillaRecipeButton = value);
		y = toggle(y, "show_recipe_ids", config.showRecipeIds, value -> config.showRecipeIds = value);
		y = toggle(y, "highlight_search", config.highlightSearch, value -> config.highlightSearch = value);

		// Opened from a container screen (right-clicking our button): offer to switch the viewer off just there.
		if (parent instanceof AbstractContainerScreen<?>) {
			String screenName = parent.getClass().getSimpleName();
			addRenderableWidget(CycleButton.onOffBuilder(!ScreenSupport.isDisabled(parent))
					.withTooltip(current -> Tooltip.create(Component.translatable("craftersalmanac.config.show_on_screen.tooltip", screenName)))
					.create(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20, Component.translatable("craftersalmanac.config.show_on_screen"),
							(button, show) -> {
								if (show == ScreenSupport.isDisabled(parent)) ScreenSupport.toggleDisabled(parent);
							}));
			y += 24;
		}
		if (!config.disabledScreens.isEmpty()) {
			addRenderableWidget(Button.builder(Component.translatable("craftersalmanac.config.enable_all", config.disabledScreens.size()), button -> {
						config.disabledScreens.clear();
						ViewerConfig.save();
						rebuildWidgets();
					})
					.tooltip(Tooltip.create(Component.translatable("craftersalmanac.config.enable_all.tooltip")))
					.bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20)
					.build());
		}

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 27, 200, 20)
				.build());
	}

	private int toggle(int y, String key, boolean value, Consumer<Boolean> setter) {
		String prefix = "craftersalmanac.config." + key;
		Tooltip tooltip = Tooltip.create(Component.translatable(prefix + ".tooltip"));
		addRenderableWidget(CycleButton.onOffBuilder(value)
				.withTooltip(current -> tooltip)
				.create(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20, Component.translatable(prefix), (button, newValue) -> setter.accept(newValue)));
		return y + 24;
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
