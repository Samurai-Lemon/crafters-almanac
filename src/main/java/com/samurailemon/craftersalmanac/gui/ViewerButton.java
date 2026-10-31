package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ConfigScreen;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/** Uses the vanilla recipe book button's art. Click toggles the panel, right-click opens settings. */
public class ViewerButton extends AbstractWidget {

	/** Where to try placing the button relative to the vanilla one: right, below, left, above. */
	private static final int[][] BESIDE_OFFSETS = {{22, 0}, {0, 20}, {-22, 0}, {0, -20}};
	/** Keeps the button from touching slots, so it doesn't look stuck onto them. */
	private static final int SLOT_MARGIN = 2;

	private final AbstractContainerScreen<?> screen;
	/** The vanilla recipe book button, or null if this screen has none. */
	private final ImageButton vanillaButton;
	/** True when the vanilla button is hidden and we take its spot instead of sitting beside it. */
	private final boolean replacesVanilla;

	public ViewerButton(AbstractContainerScreen<?> screen, ImageButton vanillaButton, boolean replacesVanilla) {
		super(0, 0, 20, 18, Component.translatable("craftersalmanac.button"));
		this.screen = screen;
		this.vanillaButton = vanillaButton;
		this.replacesVanilla = replacesVanilla;
		setTooltip(Tooltip.create(getMessage()));
		layout();
	}

	/** Runs every frame because the vanilla button moves when its recipe book slides the container over. */
	private void layout() {
		if (vanillaButton != null) {
			if (replacesVanilla) {
				setPosition(vanillaButton.getX(), vanillaButton.getY());
			} else {
				layoutBesideVanilla();
			}
			return;
		}
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		int left = container.rbv$getLeftPos();
		int top = container.rbv$getTopPos();
		if (screen instanceof CreativeModeInventoryScreen) {
			// Creative has tabs above and below and the panel on the left, so sit on the right edge
			// (overlapping the corner if the container was pushed against the screen edge).
			setPosition(Math.min(left + container.rbv$getImageWidth() + 2, screen.width - width), top + 4);
		} else {
			// Just above the container's top-right corner.
			setPosition(left + container.rbv$getImageWidth() - width, top - height - 2);
		}
	}

	/** Takes the first spot next to the vanilla button that doesn't cover a slot (e.g. the furnace's fuel slot). */
	private void layoutBesideVanilla() {
		for (int[] offset : BESIDE_OFFSETS) {
			int x = vanillaButton.getX() + offset[0];
			int y = vanillaButton.getY() + offset[1];
			if (!coversSlot(x, y)) {
				setPosition(x, y);
				return;
			}
		}
		setPosition(vanillaButton.getX() + BESIDE_OFFSETS[0][0], vanillaButton.getY() + BESIDE_OFFSETS[0][1]);
	}

	private boolean coversSlot(int x, int y) {
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		for (Slot slot : screen.getMenu().slots) {
			int slotX = container.rbv$getLeftPos() + slot.x;
			int slotY = container.rbv$getTopPos() + slot.y;
			if (x - SLOT_MARGIN < slotX + 16 && slotX < x + width + SLOT_MARGIN
					&& y - SLOT_MARGIN < slotY + 16 && slotY < y + height + SLOT_MARGIN) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		layout();
		g.blitSprite(RecipeBookComponent.RECIPE_BUTTON_SPRITES.get(isActive(), isHoveredOrFocused()), getX(), getY(), width, height);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		if (!visible || !active || !isMouseOver(mouseX, mouseY)) return false;
		if (button == 0) {
			ViewerState.toggle();
			ViewerState.swallowNextRelease = true;
		} else if (button == 1) {
			// The release lands on the settings screen, so it isn't marked as ours.
			Minecraft.getInstance().setScreen(new ConfigScreen(screen));
		} else {
			return false;
		}
		playDownSound(Minecraft.getInstance().getSoundManager());
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
