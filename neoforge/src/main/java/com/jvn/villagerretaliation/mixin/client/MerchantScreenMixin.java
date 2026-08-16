package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {
    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$renderExtendedTradeBackground(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY,
            CallbackInfo callbackInfo) {
        if (!VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled()) {
            return;
        }
        VillagerTradeScreenBackground.render(graphics, (MerchantScreen) (Object) this);
        callbackInfo.cancel();
    }
}
