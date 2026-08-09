package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.util.TickThrottle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

final class VillagerSmithGolemRepairSupport {
    private static final long REPAIR_COOLDOWN_TICKS = 6000L;
    private static final long REPAIR_SCAN_INTERVAL_TICKS = 100L;
    private static final long APPROACH_RETRY_TICKS = 60L;
    private static final long PATH_FAILURE_COOLDOWN_TICKS = 200L;
    private static final double SEARCH_RADIUS = 12.0D;
    private static final double REACH_SQR = 9.0D;
    private static final float HEAL_AMOUNT = 25.0F;
    private static final Map<UUID, Long> NEXT_REPAIR_TICKS = new HashMap<>();

    private VillagerSmithGolemRepairSupport() {
    }

    static boolean tryRepairNearbyIronGolem(Villager villager, ServerLevel level, long gameTime, double movementSpeed) {
        if (!canProfessionRepairIronGolems(villager)
                || !TickThrottle.consume(villager.getUUID(), NEXT_REPAIR_TICKS, gameTime, REPAIR_SCAN_INTERVAL_TICKS)) {
            return false;
        }

        IronGolem ironGolem = findNearbyDamagedIronGolem(villager, level);
        if (ironGolem == null) {
            NEXT_REPAIR_TICKS.put(villager.getUUID(), gameTime + REPAIR_SCAN_INTERVAL_TICKS);
            return false;
        }

        if (!villager.hasLineOfSight(ironGolem)
                || villager.distanceToSqr(ironGolem) > REACH_SQR) {
            if (!villager.getNavigation().isDone()) {
                NEXT_REPAIR_TICKS.put(villager.getUUID(), gameTime + APPROACH_RETRY_TICKS);
                return true;
            }

            boolean pathStarted = villager.getNavigation().moveTo(ironGolem, movementSpeed * 0.6D);
            NEXT_REPAIR_TICKS.put(
                    villager.getUUID(),
                    gameTime + (pathStarted
                            ? APPROACH_RETRY_TICKS
                            : PATH_FAILURE_COOLDOWN_TICKS)
            );
            return true;
        }

        ironGolem.heal(HEAL_AMOUNT);
        villager.swing(InteractionHand.MAIN_HAND, true);
        NEXT_REPAIR_TICKS.put(villager.getUUID(), gameTime + REPAIR_COOLDOWN_TICKS);
        return true;
    }

    static void clearState(Villager villager) {
        NEXT_REPAIR_TICKS.remove(villager.getUUID());
    }

    static void clearRuntimeState() {
        NEXT_REPAIR_TICKS.clear();
    }

    private static IronGolem findNearbyDamagedIronGolem(Villager villager, ServerLevel level) {
        AABB area = villager.getBoundingBox().inflate(SEARCH_RADIUS);
        IronGolem bestTarget = null;
        float mostMissingHealth = 0.0F;
        for (IronGolem candidate : level.getEntitiesOfClass(
                IronGolem.class,
                area,
                ironGolem -> isRepairTarget(villager, level, ironGolem))) {
            float missingHealth = candidate.getMaxHealth() - candidate.getHealth();
            if (missingHealth > mostMissingHealth
                    || (missingHealth == mostMissingHealth
                    && bestTarget != null
                    && villager.distanceToSqr(candidate) < villager.distanceToSqr(bestTarget))) {
                mostMissingHealth = missingHealth;
                bestTarget = candidate;
            }
        }
        return bestTarget;
    }

    static boolean isRepairTarget(Villager villager, ServerLevel level, IronGolem ironGolem) {
        return villager != null
                && level != null
                && ironGolem != null
                && villager.level() == level
                && ironGolem.level() == level
                && ironGolem.isAlive()
                && !ironGolem.isPlayerCreated()
                && ironGolem.getHealth() < ironGolem.getMaxHealth()
                && VillageAllegianceRelations.sameCanonical(level, villager, ironGolem);
    }

    private static boolean canProfessionRepairIronGolems(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.WEAPONSMITH
                || profession == VillagerProfession.TOOLSMITH;
    }
}
