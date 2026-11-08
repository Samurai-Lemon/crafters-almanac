package com.samurailemon.craftersalmanac.gui;

import com.samurailemon.craftersalmanac.config.ViewerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * While Alt is held, adds the things vanilla tooltips leave out: what food restores, a tool's mining speed and
 * durability, how armor compares with what's worn, compost chance, fuel time and stack size.
 */
public final class TooltipDetails {
	private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
	/** A furnace takes 200 ticks to smelt one item. */
	private static final int SMELT_TICKS = 200;

	private TooltipDetails() {
	}

	public static void append(ItemStack stack, TooltipFlag flag, List<Component> lines) {
		if (!ViewerConfig.get().tooltipDetails || !Screen.hasAltDown() || stack.isEmpty()) return;

		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null) line(lines, "food", food.nutrition(), NUMBER.format(food.saturation()));
		Tool tool = stack.get(DataComponents.TOOL);
		if (tool != null) {
			float speed = tool.rules().stream().flatMap(rule -> rule.speed().stream()).reduce(tool.defaultMiningSpeed(), Math::max);
			line(lines, "mining_speed", NUMBER.format(speed));
		}
		// Advanced tooltips (F3+H) already show durability.
		if (stack.isDamageableItem() && !flag.isAdvanced()) {
			line(lines, "durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage());
		}
		if (stack.getItem() instanceof ArmorItem armor) compareArmor(lines, stack, armor);
		float compost = ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
		if (compost > 0) line(lines, "compost", Math.round(compost * 100));
		Integer burnTime = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
		if (burnTime != null) line(lines, "fuel", NUMBER.format(burnTime / (double) SMELT_TICKS));
		if (stack.getMaxStackSize() > 1) {
			line(lines, "stacks", stack.getMaxStackSize());
		} else {
			line(lines, "no_stack");
		}
	}

	/** Armor and toughness against the piece worn in the same slot, green where this is better and red where worse. */
	private static void compareArmor(List<Component> lines, ItemStack stack, ArmorItem armor) {
		Player player = Minecraft.getInstance().player;
		if (player == null) return;
		ItemStack worn = player.getItemBySlot(armor.getEquipmentSlot());
		// Hovering the worn piece itself, in the inventory's armor slots.
		if (worn == stack) return;
		ArmorItem wornArmor = worn.getItem() instanceof ArmorItem item ? item : null;
		int defense = armor.getDefense() - (wornArmor == null ? 0 : wornArmor.getDefense());
		float toughness = armor.getToughness() - (wornArmor == null ? 0 : wornArmor.getToughness());
		lines.add(wornArmor == null
				? Component.translatable("craftersalmanac.tooltip.compare_nothing").withStyle(ChatFormatting.GRAY)
				: Component.translatable("craftersalmanac.tooltip.compare", worn.getHoverName()).withStyle(ChatFormatting.GRAY));
		if (defense == 0 && toughness == 0) {
			lines.add(Component.literal(" ").append(Component.translatable("craftersalmanac.tooltip.compare_same")).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (defense != 0) lines.add(difference("armor", defense));
		if (toughness != 0) lines.add(difference("toughness", toughness));
	}

	private static Component difference(String key, float amount) {
		String signed = (amount > 0 ? "+" : "") + NUMBER.format(amount);
		return Component.literal(" ").append(Component.translatable("craftersalmanac.tooltip." + key, signed))
				.withStyle(amount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static void line(List<Component> lines, String key, Object... args) {
		lines.add(Component.translatable("craftersalmanac.tooltip." + key, args).withStyle(ChatFormatting.GRAY));
	}
}
