package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Villager.class)
public abstract class VillagerBrainTickMixin {
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void villagerretaliation$suppressHiredIdleBrainTick(
            Brain<Villager> brain,
            ServerLevel level,
            LivingEntity entity) {
        if (entity instanceof Villager villager) {
            if (HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager)
                    || VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager)) {
                return;
            }
            brain.tick(level, villager);
        }
    }
}
