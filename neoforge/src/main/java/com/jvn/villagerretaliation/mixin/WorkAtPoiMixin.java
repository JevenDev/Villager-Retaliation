package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorkAtPoi.class)
public abstract class WorkAtPoiMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressHiredJobSiteBlockUse(
            ServerLevel level,
            Villager owner,
            CallbackInfoReturnable<Boolean> cir) {
        if (VillagerBehaviorSuppressionPolicy.suppresses(
                        owner, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_WORKING)
                || HiredVillagerFocusService.shouldSuppressClaimedJobSiteBlockUse(level, owner)) {
            cir.setReturnValue(false);
        }
    }
}
