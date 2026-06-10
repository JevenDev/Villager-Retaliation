package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HarvestFarmland.class)
public abstract class HarvestFarmlandMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$requireHoeForVanillaHarvest(
            ServerLevel level,
            Villager owner,
            CallbackInfoReturnable<Boolean> cir) {
        if (!FarmerHoeRequirement.hasHoe(owner)) {
            cir.setReturnValue(false);
        }
    }
}
