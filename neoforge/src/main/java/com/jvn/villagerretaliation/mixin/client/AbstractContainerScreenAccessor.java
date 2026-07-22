package com.jvn.villagerretaliation.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos") void villagerretaliation$setLeftPos(int leftPos);
    @Accessor("topPos") void villagerretaliation$setTopPos(int topPos);
}
