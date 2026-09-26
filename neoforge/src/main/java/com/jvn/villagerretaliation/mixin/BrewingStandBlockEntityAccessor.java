package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
    @Accessor("ingredient")
    net.minecraft.world.item.Item villagerretaliation$getIngredient();

    @Accessor("ingredient")
    void villagerretaliation$setIngredient(net.minecraft.world.item.Item ingredient);

    @Accessor("fuel")
    int villagerretaliation$getFuel();

    @Accessor("fuel")
    void villagerretaliation$setFuel(int fuel);
}
