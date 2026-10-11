package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.emi.emi.screen.EmiScreenBase", remap = false)
public abstract class TradeEmiOverlayMixin {
    @ModifyVariable(method = "of", at = @At("HEAD"), argsOnly = true)
    private static Screen villagerretaliation$hideTradingOverlay(Screen screen) {
        return VillagerTradeScreenLayout.hidesRecipeViewer(screen) ? null : screen;
    }
}
