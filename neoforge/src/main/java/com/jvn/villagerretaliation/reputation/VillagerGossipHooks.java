package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.village.VillageMembership;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

public final class VillagerGossipHooks {
    private static final long GOSSIP_INTERVAL_TICKS = 20L * 30L;
    private static final Map<UUID, Long> NEXT_GOSSIP_TICKS = new HashMap<>();

    private VillagerGossipHooks() {
    }

    public static void spreadReputation(ServerLevel level, Villager source, UUID playerId, int originalAmount) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get() || originalAmount == 0) {
            return;
        }

        long gameTime = level.getGameTime();
        UUID sourceId = source.getUUID();
        if (gameTime < NEXT_GOSSIP_TICKS.getOrDefault(sourceId, 0L)) {
            return;
        }
        NEXT_GOSSIP_TICKS.put(sourceId, gameTime + GOSSIP_INTERVAL_TICKS);

        int gossipedAmount = (int) Math.round(originalAmount * VillagerRetaliationConfig.GOSSIP_REPUTATION_MULTIPLIER.get());
        if (gossipedAmount == 0) {
            gossipedAmount = originalAmount > 0 ? 1 : -1;
        }

        List<Villager> receivers = gossipReceivers(level, source);
        int applied = 0;
        for (Villager receiver : receivers) {
            VillagerReputationManager.addGossipReputation(level, receiver, playerId, gossipedAmount, sourceId);
            if (++applied >= 4) {
                break;
            }
        }
    }

    private static List<Villager> gossipReceivers(ServerLevel level, Villager source) {
        return VillageMembership.resolve(level, source)
                .map(area -> sortedByDistance(source, area.membersMatching(receiver -> receiver != source && receiver.isAlive())))
                .filter(receivers -> !receivers.isEmpty())
                .orElseGet(() -> {
                    AABB area = source.getBoundingBox().inflate(VillagerRetaliationConfig.GOSSIP_RADIUS.get());
                    return sortedByDistance(source, level.getEntitiesOfClass(
                            Villager.class,
                            area,
                            receiver -> receiver != source && receiver.isAlive()
                    ));
                });
    }

    private static List<Villager> sortedByDistance(Villager source, List<Villager> villagers) {
        return villagers.stream()
                .sorted(Comparator.comparingDouble(source::distanceToSqr))
                .toList();
    }
}
