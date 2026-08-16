package com.jvn.villagerretaliation.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Accessor("lastSlots")
    NonNullList<ItemStack> villagerretaliation$getLastSlots();

    @Accessor("remoteSlots")
    NonNullList<ItemStack> villagerretaliation$getRemoteSlots();
}
