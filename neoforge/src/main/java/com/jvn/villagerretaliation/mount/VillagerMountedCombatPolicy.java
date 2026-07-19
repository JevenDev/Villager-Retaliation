package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Prevents an allied rear passenger from hurting the living entity driving the same mount. */
public final class VillagerMountedCombatPolicy {
    private VillagerMountedCombatPolicy() {
    }

    public static boolean shouldCancelDamage(LivingEntity damaged, DamageSource source) {
        if (damaged == null || source == null) {
            return false;
        }
        return VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(damaged, source)
                .map(attacker -> isProtectedPair(attacker, damaged))
                .orElse(false);
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event == null
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity driver)
                || !(event.getProjectile().getOwner() instanceof LivingEntity passenger)) {
            return;
        }
        if (isProtectedPair(passenger, driver)) {
            // Preserve the projectile instead of consuming it against a protected co-rider.
            event.setCanceled(true);
        }
    }

    public static boolean isProtectedPair(LivingEntity passenger, LivingEntity driver) {
        if (!isRearSeatPair(passenger, driver)) {
            return false;
        }
        ServerLevel level = (ServerLevel) passenger.level();
        return PartyService.areInSameOrAlliedParty(passenger, driver)
                || passenger.isAlliedTo(driver)
                || driver.isAlliedTo(passenger)
                || VillageAllegianceRelations.sharesCommunity(level, passenger, driver)
                || isActiveHirerPair(level, passenger, driver);
    }

    private static boolean isActiveHirerPair(
            ServerLevel level,
            LivingEntity passenger,
            LivingEntity driver) {
        return passenger instanceof Villager passengerVillager
                && HiredVillagerContractService.getHirer(level, passengerVillager)
                        .filter(driver.getUUID()::equals)
                        .isPresent()
                || driver instanceof Villager driverVillager
                && HiredVillagerContractService.getHirer(level, driverVillager)
                        .filter(passenger.getUUID()::equals)
                        .isPresent();
    }

    private static boolean isRearSeatPair(LivingEntity passenger, LivingEntity driver) {
        if (!VillagerRideOnCompat.available()
                || passenger == null
                || driver == null
                || passenger == driver
                || !passenger.isAlive()
                || !driver.isAlive()
                || !(passenger.level() instanceof ServerLevel)
                || passenger.level() != driver.level()
                || !(passenger.getVehicle() instanceof AbstractHorse horse)
                || driver.getVehicle() != horse) {
            return false;
        }
        return VillagerRideOnCompat.occupant(horse, false) == driver
                && VillagerRideOnCompat.occupant(horse, true) == passenger;
    }
}
