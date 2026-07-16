package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.raid.PlayerRaidService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Friendly-fire rules for a villager riding behind a living Ride On driver. */
public final class VillagerMountedCombatPolicy {
    private VillagerMountedCombatPolicy() {
    }

    public static boolean shouldCancelDamage(LivingEntity damaged, DamageSource source) {
        if (damaged == null || source == null) {
            return false;
        }
        return VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(damaged, source)
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .map(attacker -> relationship(attacker, damaged) == FrontRelationship.PROTECTED)
                .orElse(false);
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event == null
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity frontRider)
                || !(event.getProjectile().getOwner() instanceof Villager rearVillager)) {
            return;
        }
        if (relationship(rearVillager, frontRider) == FrontRelationship.PROTECTED) {
            event.setCanceled(true);
        }
    }

    public static boolean allowsSameVehicleProjectileHit(Projectile projectile, Entity target) {
        return projectile != null
                && target instanceof LivingEntity frontRider
                && projectile.getOwner() instanceof Villager rearVillager
                && relationship(rearVillager, frontRider) == FrontRelationship.HOSTILE;
    }

    public static boolean isProtectedFrontRider(Villager rearVillager, LivingEntity frontRider) {
        return relationship(rearVillager, frontRider) == FrontRelationship.PROTECTED;
    }

    public static boolean isExplicitlyHostileFrontRider(Villager rearVillager, LivingEntity frontRider) {
        return relationship(rearVillager, frontRider) == FrontRelationship.HOSTILE;
    }

    private static FrontRelationship relationship(Villager rearVillager, LivingEntity frontRider) {
        if (!isRearSeatPair(rearVillager, frontRider)) {
            return FrontRelationship.UNRELATED;
        }
        if (VillagerRetaliationHandler.hasRetaliationTarget(rearVillager, frontRider)
                || VillageCombatAuthorizationService.isAuthorized(rearVillager, frontRider)
                || PlayerRaidService.areOpposingParticipants(rearVillager, frontRider)) {
            return FrontRelationship.HOSTILE;
        }
        ServerLevel level = (ServerLevel) rearVillager.level();
        if (frontRider instanceof Villager
                || PartyService.areInSameOrAlliedParty(rearVillager, frontRider)
                || rearVillager.isAlliedTo(frontRider)
                || frontRider.isAlliedTo(rearVillager)
                || VillageAllegianceRelations.sharesCommunity(level, rearVillager, frontRider)
                || HiredVillagerContractService.getHirer(level, rearVillager)
                        .filter(frontRider.getUUID()::equals)
                        .isPresent()) {
            return FrontRelationship.PROTECTED;
        }
        return FrontRelationship.NEUTRAL;
    }

    private static boolean isRearSeatPair(Villager rearVillager, LivingEntity frontRider) {
        if (!VillagerRideOnCompat.available()
                || rearVillager == null
                || frontRider == null
                || rearVillager == frontRider
                || !rearVillager.isAlive()
                || !frontRider.isAlive()
                || !(rearVillager.level() instanceof ServerLevel)
                || rearVillager.level() != frontRider.level()
                || !(rearVillager.getVehicle() instanceof AbstractHorse horse)
                || frontRider.getVehicle() != horse) {
            return false;
        }
        return VillagerRideOnCompat.occupant(horse, false) == frontRider
                && VillagerRideOnCompat.occupant(horse, true) == rearVillager;
    }

    private enum FrontRelationship {
        UNRELATED,
        NEUTRAL,
        PROTECTED,
        HOSTILE
    }
}
