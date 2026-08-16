package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;hasFarmSeeds()Z"))
    private boolean villagerretaliation$useJobInventoryForHiredFarmerSeedCheck(
            Villager owner,
            ServerLevel level,
            Villager tickOwner,
            long gameTime) {
        return HiredFarmingInventoryBridge.hasPlantingItem(level, owner);
    }

    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;getInventory()Lnet/minecraft/world/SimpleContainer;"))
    private SimpleContainer villagerretaliation$useJobInventoryForHiredFarmerPlanting(
            Villager owner,
            ServerLevel level,
            Villager tickOwner,
            long gameTime) {
        return HiredFarmingInventoryBridge.plantingInventory(level, owner);
    }

    @Inject(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
            at = @At("RETURN"))
    private void villagerretaliation$commitJobInventoryPlanting(
            ServerLevel level,
            Villager owner,
            long gameTime,
            CallbackInfo ci) {
        HiredFarmingInventoryBridge.finishPlantingInventory();
    }
}
