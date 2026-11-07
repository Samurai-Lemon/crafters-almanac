package com.samurailemon.craftersalmanac.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimRecipeAccessor {
	@Accessor("template")
	Ingredient rbv$getTemplate();

	@Accessor("base")
	Ingredient rbv$getBase();

	@Accessor("addition")
	Ingredient rbv$getAddition();
}
