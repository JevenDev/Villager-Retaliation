package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementSimulationHandoff {
    private SettlementSimulationHandoff() {
    }

    public static void stopTicking(Entity entity) {
        if (entity instanceof Villager courier && SettlementCourierService.owns(courier)) {
            SettlementCourierService.unloading(courier);
            return;
        }
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            SettlementProjectileService.onUnloaded(projectile);
        }
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) SettlementHuntingTargetService.onUnloaded(living);
        if (entity instanceof net.minecraft.world.entity.ExperienceOrb orb) {
            SettlementGroundExperienceService.onUnloaded(orb);
        }
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
            SettlementGroundItemService.onUnloaded(item);
        }
        if (entity instanceof net.minecraft.world.entity.animal.Animal animal) {
            SettlementLivestockService.onUnloaded(animal);
        }
        if (!(entity instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)
                || !villager.isAlive() || villager.getRemovalReason() != null
                || SettlementWorkforceService.home(level, villager).isEmpty()) {
            return;
        }
        VillagerRecoveryService.cancelForcedRecovery(villager);
        SettlementBackgroundService.onUnloaded(villager);
        SettlementCareService.onUnloaded(villager);
        HiredVillagerWorkService.onVillagerLeaveLevel(level, villager);
        AssignedStorageService.releaseCourierClaims(villager);
    }
}
