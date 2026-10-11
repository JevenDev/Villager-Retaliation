package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
public abstract class TradeJeiOverlayMixin {
    @Inject(method = "getGuiProperties", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideTradingOverlay(Screen screen, CallbackInfoReturnable<Optional<?>> callback) {
        if (VillagerTradeScreenLayout.hidesRecipeViewer(screen)) callback.setReturnValue(Optional.empty());
    }
}
