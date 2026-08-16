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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
    @Unique
    private boolean villagerretaliation$completedTrade;

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
        if (!this.merchant.isClientSide()) {
            return offer.getXp();
        }
        return this.villagerretaliation$completedTrade
                ? VillagerTradeLevelingClientDisplay.adjustedTradeXp(offer, true)
                : 0;
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void villagerretaliation$resetTradeCompletion(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        this.villagerretaliation$completedTrade = false;
    }

    @Inject(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"
            )
    )
    private void villagerretaliation$markTradeCompleted(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        this.villagerretaliation$completedTrade = true;
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void villagerretaliation$refreshNextTradeXp(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        if (this.merchant.isClientSide()) {
            this.slots.setChanged();
        }
    }
}
