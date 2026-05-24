package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.village.VillageMembership;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

public final class VillagerGossipHooks {
    private static final long GOSSIP_INTERVAL_TICKS = 20L * 30L;
    private static final int GOSSIP_RECEIVERS_PER_SOURCE = 4;
    private static final int PENDING_GOSSIP_PER_TICK = 8;
    private static final Map<UUID, Long> NEXT_GOSSIP_TICKS = new HashMap<>();
    private static final Deque<PendingGossip> PENDING_GOSSIP = new ArrayDeque<>();

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

        PENDING_GOSSIP.addLast(new PendingGossip(level, source, playerId, gossipedAmount));
    }

    public static void processPending(long gameTime) {
        int processed = 0;
        while (processed < PENDING_GOSSIP_PER_TICK && !PENDING_GOSSIP.isEmpty()) {
            PendingGossip gossip = PENDING_GOSSIP.removeFirst();
            if (gossip.level().getServer() == null) {
                continue;
            }
            for (Villager receiver : gossipReceivers(gossip.level(), gossip.source())) {
                VillagerReputationManager.addGossipReputation(gossip.level(), receiver, gossip.playerId(), gossip.amount(), gossip.source().getUUID());
            }
            processed++;
        }
        pruneCooldowns(gameTime);
    }

    public static void clear() {
        NEXT_GOSSIP_TICKS.clear();
        PENDING_GOSSIP.clear();
    }

    private static void pruneCooldowns(long gameTime) {
        NEXT_GOSSIP_TICKS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static List<Villager> gossipReceivers(ServerLevel level, Villager source) {
        return VillageMembership.resolve(level, source)
                .map(area -> nearestReceivers(source, area.membersMatching(receiver -> receiver != source && receiver.isAlive())))
                .filter(receivers -> !receivers.isEmpty())
                .orElseGet(() -> {
                    AABB area = source.getBoundingBox().inflate(VillagerRetaliationConfig.GOSSIP_RADIUS.get());
                    return nearestReceivers(source, level.getEntitiesOfClass(
                            Villager.class,
                            area,
                            receiver -> receiver != source && receiver.isAlive()
                    ));
                });
    }

    private static List<Villager> nearestReceivers(Villager source, Iterable<Villager> candidates) {
        List<Villager> nearest = new ArrayList<>(GOSSIP_RECEIVERS_PER_SOURCE);
        for (Villager candidate : candidates) {
            int insertAt = 0;
            double distanceSqr = source.distanceToSqr(candidate);
            while (insertAt < nearest.size() && source.distanceToSqr(nearest.get(insertAt)) <= distanceSqr) {
                insertAt++;
            }
            if (insertAt >= GOSSIP_RECEIVERS_PER_SOURCE) {
                continue;
            }
            nearest.add(insertAt, candidate);
            if (nearest.size() > GOSSIP_RECEIVERS_PER_SOURCE) {
                nearest.remove(nearest.size() - 1);
            }
        }
        return nearest;
    }

    private record PendingGossip(ServerLevel level, Villager source, UUID playerId, int amount) {
    }
}
