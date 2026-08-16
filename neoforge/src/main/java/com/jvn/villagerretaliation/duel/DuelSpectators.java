package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class DuelSpectators {
    private DuelSpectators() {}

    static Set<UUID> recruit(ServerLevel level, Villager duelist, Vec3 center) {
        int radius = VillagerRetaliationConfig.DUEL_SPECTATOR_RADIUS.get();
        int cap = VillagerRetaliationConfig.DUEL_SPECTATOR_CAP.get();
        var village = VillageEventMemory.villageForVillager(level, duelist);
        if (village.isEmpty() || cap <= 0) return Set.of();
        return level.getEntitiesOfClass(Villager.class, AABB.ofSize(center, radius * 2.0D, radius, radius * 2.0D),
                        candidate -> candidate != duelist && candidate.isAlive() && !candidate.isBaby()
                                && !VillagerDownedService.isDowned(candidate)
                                && VillageEventMemory.villageForVillager(level, candidate)
                                .map(village.get()::equals).orElse(false))
                .stream().sorted(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(center)))
                .limit(cap).map(Villager::getUUID)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static void maintain(ServerLevel level, Set<UUID> ids, Vec3 center, int arenaRadius, Villager duelist) {
        int index = 0;
        double spectatorRingRadius = arenaRadius + 3.0D;
        for (UUID id : ids) {
            if (!(level.getEntity(id) instanceof Villager spectator) || !spectator.isAlive()) continue;
            double angle = Math.PI * 2.0D * index++ / Math.max(1, ids.size());
            Vec3 target = new Vec3(
                    center.x + Math.cos(angle) * spectatorRingRadius,
                    spectator.getY(),
                    center.z + Math.sin(angle) * spectatorRingRadius);
            if (spectator.position().distanceToSqr(target) > 4.0D) {
                spectator.getNavigation().moveTo(target.x, target.y, target.z, 0.6D);
            }
            spectator.setTarget(null);
            spectator.setAggressive(false);
            spectator.getLookControl().setLookAt(duelist, 30.0F, 30.0F);
        }
    }

    static void release(ServerLevel level, Set<UUID> ids) {
        for (UUID id : ids) if (level.getEntity(id) instanceof Villager spectator) {
            spectator.getNavigation().stop();
            spectator.setTarget(null);
            spectator.setAggressive(false);
        }
    }

    static void reward(ServerLevel level, Set<UUID> ids, Vec3 center, int arenaRadius, ServerPlayer player) {
        int amount = VillagerRetaliationConfig.DUEL_WATCHER_REPUTATION.get();
        double radiusSqr = rewardRadiusSqr(arenaRadius);
        for (UUID id : ids) if (level.getEntity(id) instanceof Villager witness && witness.isAlive()
                && witness.position().distanceToSqr(center) <= radiusSqr) {
            VillagerReputationManager.addDialogueReputation(level, witness, player, amount);
        }
    }

    static double rewardRadiusSqr(int arenaRadius) {
        double radius = Math.max(
                VillagerRetaliationConfig.DUEL_SPECTATOR_RADIUS.get(),
                Math.max(0, arenaRadius) + 5.0D);
        return radius * radius;
    }
}
