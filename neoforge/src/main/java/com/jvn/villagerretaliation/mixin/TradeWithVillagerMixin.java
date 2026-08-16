package com.jvn.villagerretaliation.mixin;

import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.TradeWithVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeWithVillager.class)
public abstract class TradeWithVillagerMixin {
    @Inject(method = "throwHalfStack", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$suppressVillagerItemSharing(
            Villager villager,
            Set<Item> items,
            LivingEntity target,
            CallbackInfo ci) {
        ci.cancel();
    }
}
