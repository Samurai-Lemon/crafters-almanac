package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import net.minecraft.ChatFormatting;
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
 * A small button just above the top-right corner of the slots it acts on: the player's inventory rows in the
 * survival inventory, or the storage grid in chests, barrels, shulker boxes and the like (on the title's line).
 * The sort button sorts the way the settings say (see {@link InventorySorter}) and is hidden when sorting is set
 * to Off. Storage also gets a quick stack button left of it, which can be turned off in the settings.
 */
public class SortButton extends AbstractWidget {
	/** What a button does. */
	public enum Kind {
		SORT("craftersalmanac.sort.button"),
		/** Moves inventory items the container already has into it, see {@link InventorySorter#quickStack}. */
		QUICK_STACK("craftersalmanac.quick_stack.button");

		final String title;

		Kind(String title) {
			this.title = title;
		}
	}

	private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
	private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
	private static final int SIZE = 11;

	private final AbstractContainerScreen<?> screen;
	private final InventorySorter.Target target;
	private final Kind kind;

	public SortButton(AbstractContainerScreen<?> screen, InventorySorter.Target target, Kind kind) {
		super(0, 0, SIZE, SIZE, Component.translatable(kind.title));
		this.screen = screen;
		this.target = target;
		this.kind = kind;
		setTooltip(Tooltip.create(kind == Kind.SORT
				? getMessage()
				: getMessage().copy().append("\n").append(Component.translatable(kind.title + ".hint").withStyle(ChatFormatting.GRAY))));
		layout();
	}

	private static boolean shows(Kind kind) {
		ViewerConfig config = ViewerConfig.get();
		return kind == Kind.SORT ? config.sortMode != InventorySorter.Mode.OFF : config.quickStack;
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
		visible = shows(kind) && !slots.isEmpty();
		if (!visible) return;
		// The buttons go in a row from the right, sort first; quick stack moves over only when sort is shown.
		int place = kind == Kind.QUICK_STACK && shows(Kind.SORT) ? 1 : 0;
		int count = target == InventorySorter.Target.CONTAINER && shows(Kind.QUICK_STACK) && shows(Kind.SORT) ? 2 : 1;
		// Right-aligned with the slots, just above the top row (slot frames start a pixel outside the item). If the
		// row would cover the screen's title (the dispenser centres its title over its grid), they go right of the
		// top row instead, one under the other.
		int rowLeft = right - count * SIZE - (count - 1);
		int y = top - SIZE - 2;
		int titleX = container.rbv$getTitleLabelX();
		int titleY = container.rbv$getTitleLabelY();
		int titleRight = titleX + Minecraft.getInstance().font.width(screen.getTitle());
		boolean coversTitle = rowLeft < titleRight && titleX < right && y < titleY + 9 && titleY < y + SIZE;
		int x;
		if (target == InventorySorter.Target.CONTAINER && coversTitle) {
			x = right + 2;
			y = top - 1 + place * (SIZE + 1);
		} else {
			x = right - SIZE - place * (SIZE + 1);
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
		if (kind == Kind.SORT) {
			InventorySorter.sort(screen.getMenu(), target, ViewerConfig.get().sortMode);
		} else {
			InventorySorter.quickStack(screen.getMenu());
		}
		playDownSound(Minecraft.getInstance().getSoundManager());
		ViewerState.swallowNextRelease = true;
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
