package com.jvn.villagerretaliation.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable @Accessor("x") void villagerretaliation$setX(int x);

    @Mutable @Accessor("y") void villagerretaliation$setY(int y);
}
