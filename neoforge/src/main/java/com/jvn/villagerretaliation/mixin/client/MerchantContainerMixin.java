package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.trade.VillagerTradeLevelingClientDisplay;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
    @Shadow
    @Final
    private Merchant merchant;

    @Redirect(
            method = "updateSellItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/MerchantOffer;getXp()I"
            )
    )
    private int villagerretaliation$skillAdjustedFutureTradeXp(MerchantOffer offer) {
        return this.merchant.isClientSide()
                ? VillagerTradeLevelingClientDisplay.adjustedTradeXp(offer, false)
                : offer.getXp();
    }
}
