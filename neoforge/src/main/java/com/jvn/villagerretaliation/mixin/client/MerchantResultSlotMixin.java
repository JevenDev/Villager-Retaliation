package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.trade.VillagerTradeLevelingClientDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
    @Shadow
    @Final
    private MerchantContainer slots;

    @Shadow
    @Final
    private Merchant merchant;

    @Redirect(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/MerchantOffer;getXp()I"
            )
    )
    private int villagerretaliation$skillAdjustedDisplayedTradeXp(MerchantOffer offer) {
        return this.merchant.isClientSide()
                ? VillagerTradeLevelingClientDisplay.adjustedTradeXp(offer, true)
                : offer.getXp();
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void villagerretaliation$refreshNextTradeXp(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        if (this.merchant.isClientSide()) {
            this.slots.setChanged();
        }
    }
}
