package com.samurailemon.recipebookviewer.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
	@Accessor("template")
	Ingredient rbv$getTemplate();

	@Accessor("base")
	Ingredient rbv$getBase();

	@Accessor("addition")
	Ingredient rbv$getAddition();
}
