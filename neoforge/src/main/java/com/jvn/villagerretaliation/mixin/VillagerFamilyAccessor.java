package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface VillagerFamilyAccessor {
    @Accessor("foodLevel")
    int villagerretaliation$getFamilyFood();

    @Accessor("foodLevel")
    void villagerretaliation$setFamilyFood(int food);

    @Invoker("countFoodPointsInInventory")
    int villagerretaliation$countFamilyFood();
}
