package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.trade.VillagerTradeWalletService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotWalletMixin {
    @Shadow
    @Final
    private MerchantContainer slots;

    @Shadow
    @Final
    private Merchant merchant;

    public boolean mayPickup(Player player) {
        if (this.merchant.isClientSide()
                || !(this.merchant instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return true;
        }
        return VillagerTradeWalletService.canCompleteTrade(level, villager, this.slots.getActiveOffer());
    }
}
