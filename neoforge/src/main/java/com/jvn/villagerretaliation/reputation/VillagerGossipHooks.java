package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
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

        int gossipedAmount = adjustedGossipAmount(level, source, originalAmount);
        if (gossipedAmount == 0) {
            gossipedAmount = originalAmount > 0 ? 1 : -1;
        }

        PENDING_GOSSIP.addLast(new PendingGossip(level, source, playerId, gossipedAmount, receiverLimit(level, source), gossipRadius(level, source)));
    }

    public static void processPending(long gameTime) {
        int processed = 0;
        while (processed < PENDING_GOSSIP_PER_TICK && !PENDING_GOSSIP.isEmpty()) {
            PendingGossip gossip = PENDING_GOSSIP.removeFirst();
            if (gossip.level().getServer() == null) {
                continue;
            }
            for (Villager receiver : gossipReceivers(gossip.level(), gossip.source(), gossip.receiverLimit(), gossip.radius())) {
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

    private static List<Villager> gossipReceivers(ServerLevel level, Villager source, int receiverLimit, double radius) {
        if (VillageAllegianceApi.canonicalPrimary(level, source).isEmpty()) {
            return List.of();
        }
        return VillageMembership.resolve(level, source)
                .map(area -> nearestReceivers(source, area.membersMatching(receiver -> receiver != source
                        && receiver.isAlive()
                        && VillageAllegianceRelations.sharesCommunity(level, source, receiver)), receiverLimit))
                .filter(receivers -> !receivers.isEmpty())
                .orElseGet(() -> {
                    AABB area = source.getBoundingBox().inflate(radius);
                    return nearestReceivers(source, level.getEntitiesOfClass(
                            Villager.class,
                            area,
                            receiver -> receiver != source
                                    && receiver.isAlive()
                                    && VillageAllegianceRelations.sharesCommunity(level, source, receiver)
                    ), receiverLimit);
                });
    }

    private static List<Villager> nearestReceivers(Villager source, Iterable<Villager> candidates, int receiverLimit) {
        List<Villager> nearest = new ArrayList<>(receiverLimit);
        double[] nearestDistances = new double[receiverLimit];
        for (Villager candidate : candidates) {
            int insertAt = 0;
            double distanceSqr = source.distanceToSqr(candidate);
            while (insertAt < nearest.size() && nearestDistances[insertAt] <= distanceSqr) {
                insertAt++;
            }
            if (insertAt >= receiverLimit) {
                continue;
            }
            nearest.add(insertAt, candidate);
            for (int i = Math.min(nearest.size() - 1, receiverLimit - 1); i > insertAt; i--) {
                nearestDistances[i] = nearestDistances[i - 1];
            }
            nearestDistances[insertAt] = distanceSqr;
            if (nearest.size() > receiverLimit) {
                nearest.remove(nearest.size() - 1);
            }
        }
        return nearest;
    }

    private static int adjustedGossipAmount(ServerLevel level, Villager source, int originalAmount) {
        double baseAmount = originalAmount * VillagerRetaliationConfig.GOSSIP_REPUTATION_MULTIPLIER.get();
        if (!VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS)) {
            return (int) Math.round(baseAmount);
        }

        int influenceOffset = VillagerSocialAttributeBehavior.scaledOffset(
                level,
                source,
                VillagerSocialAttribute.CHARM,
                15,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS
        ) + VillagerSocialAttributeBehavior.scaledOffset(
                level,
                source,
                VillagerSocialAttribute.KNOWLEDGE,
                8,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS
        );
        double influenceMultiplier = Math.clamp(1.0D + influenceOffset / 100.0D, 0.75D, 1.25D);
        return (int) Math.round(baseAmount * influenceMultiplier);
    }

    private static int receiverLimit(ServerLevel level, Villager source) {
        int bonus = VillagerSocialAttributeBehavior.positiveBonus(
                level,
                source,
                VillagerSocialAttribute.CHARM,
                2,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS
        );
        return Math.clamp(GOSSIP_RECEIVERS_PER_SOURCE + bonus, 1, 6);
    }

    private static double gossipRadius(ServerLevel level, Villager source) {
        int radiusBonus = VillagerSocialAttributeBehavior.positiveBonus(
                level,
                source,
                VillagerSocialAttribute.CHARM,
                4,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS
        );
        return VillagerRetaliationConfig.GOSSIP_RADIUS.get() + radiusBonus;
    }

    private record PendingGossip(ServerLevel level, Villager source, UUID playerId, int amount, int receiverLimit, double radius) {
    }
}
