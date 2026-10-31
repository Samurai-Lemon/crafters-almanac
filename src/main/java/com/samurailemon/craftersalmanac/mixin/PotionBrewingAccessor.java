package com.samurailemon.craftersalmanac.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The brewing recipes, which vanilla keeps private. Includes modded ones added through PotionBrewing.Builder. */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {
	/** Potion bottle, splash and lingering. */
	@Accessor("containers")
	List<Ingredient> rbv$getContainers();

	/** e.g. awkward potion + sugar = swiftness. */
	@Accessor("potionMixes")
	List<PotionBrewing.Mix<Potion>> rbv$getPotionMixes();

	/** e.g. any potion + gunpowder = splash version. */
	@Accessor("containerMixes")
	List<PotionBrewing.Mix<Item>> rbv$getContainerMixes();
}
