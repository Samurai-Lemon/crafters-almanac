package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * A small sort button just above the top-right corner of the slots it sorts: the player's inventory rows in the
 * survival inventory, or the storage grid in chests, barrels, shulker boxes and the like (on the title's line).
 * Sorts the way the settings say (see {@link InventorySorter}); hidden when sorting is set to Off.
 */
public class SortButton extends AbstractWidget {
	private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
	private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
	private static final int SIZE = 11;

	private final AbstractContainerScreen<?> screen;
	private final InventorySorter.Target target;

	public SortButton(AbstractContainerScreen<?> screen, InventorySorter.Target target) {
		super(0, 0, SIZE, SIZE, Component.translatable("craftersalmanac.sort.button"));
		this.screen = screen;
		this.target = target;
		setTooltip(Tooltip.create(getMessage()));
		layout();
	}

	/** Runs every frame because the screen moves when the almanac or the vanilla recipe book opens. */
	private void layout() {
		AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
		List<Slot> slots = InventorySorter.slots(screen.getMenu(), target);
		int right = 0;
		int top = Integer.MAX_VALUE;
		for (Slot slot : slots) {
			right = Math.max(right, slot.x + 16);
			top = Math.min(top, slot.y);
		}
		InventorySorter.Mode mode = ViewerConfig.get().sortMode;
		visible = mode != InventorySorter.Mode.OFF && !slots.isEmpty();
		if (slots.isEmpty()) return;
		// Right-aligned with the slots, just above the top row (slot frames start a pixel outside the item). If that
		// would cover the screen's title (the dispenser centres its title over its grid), go right of the top row.
		int x = right - SIZE;
		int y = top - SIZE - 2;
		int titleX = container.rbv$getTitleLabelX();
		int titleY = container.rbv$getTitleLabelY();
		int titleRight = titleX + Minecraft.getInstance().font.width(screen.getTitle());
		boolean coversTitle = x < titleRight && titleX < x + SIZE && y < titleY + 9 && titleY < y + SIZE;
		if (target == InventorySorter.Target.CONTAINER && coversTitle) {
			x = right + 2;
			y = top - 1;
		}
		setPosition(container.rbv$getLeftPos() + x, container.rbv$getTopPos() + y);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		layout();
		if (visible) g.blitSprite(isHoveredOrFocused() ? BUTTON_HIGHLIGHTED : BUTTON, getX(), getY(), width, height);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		if (!visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
		InventorySorter.sort(screen.getMenu(), target, ViewerConfig.get().sortMode);
		playDownSound(Minecraft.getInstance().getSoundManager());
		ViewerState.swallowNextRelease = true;
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
