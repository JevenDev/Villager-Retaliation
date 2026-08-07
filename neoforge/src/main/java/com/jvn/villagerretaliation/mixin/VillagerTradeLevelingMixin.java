package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.skill.VillagerTradeLevelingService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerTradeLevelingMixin {
    @Unique
    private int villagerretaliation$tradeLevelBeforeCareerIncrease;

    @Redirect(
            method = "rewardTradeXp",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/MerchantOffer;getXp()I"
            )
    )
    private int villagerretaliation$skillAdjustedTradeLevelXp(MerchantOffer offer) {
        Villager villager = (Villager) (Object) this;
        if (!(villager.level() instanceof ServerLevel level)) {
            return offer.getXp();
        }
        return VillagerTradeLevelingService.adjustedTradeLevelXp(level, villager, offer);
    }

    @Inject(method = "increaseMerchantCareer", at = @At("HEAD"))
    private void villagerretaliation$captureTradeLevelBeforeCareerIncrease(CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        this.villagerretaliation$tradeLevelBeforeCareerIncrease =
                villager.getVillagerData().getLevel();
    }

    @Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
    private void villagerretaliation$rewardTradeLevelKnowledge(CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (villager.level() instanceof ServerLevel level) {
            VillagerTradeLevelingService.onTradeLevelChanged(
                    level, villager, this.villagerretaliation$tradeLevelBeforeCareerIncrease,
                    villager.getVillagerData().getLevel());
        }
    }
}
