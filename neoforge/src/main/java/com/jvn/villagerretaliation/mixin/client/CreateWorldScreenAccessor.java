package com.jvn.villagerretaliation.mixin.client;

import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Accessor("lastScreen")
    @Nullable
    Screen villagerretaliation$getLastScreen();
}
