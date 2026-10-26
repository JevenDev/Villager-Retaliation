package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BoatItem.class)
public interface DiplomaticBoatItemAccessor {
    @Accessor("type") Boat.Type villagerretaliation$type();
    @Accessor("hasChest") boolean villagerretaliation$hasChest();
}
