package com.jvn.villagerretaliation.client.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;

public final class VillagerTradingTargetFinder {
    public static final double DEFAULT_LOOKUP_RADIUS = 8.0D;

    private VillagerTradingTargetFinder() {
    }

    public static List<AbstractVillager> nearbySorted(Minecraft minecraft) {
        return nearbySorted(minecraft, DEFAULT_LOOKUP_RADIUS);
    }

    public static List<AbstractVillager> nearbySorted(Minecraft minecraft, double lookupRadius) {
        if (minecraft.level == null || minecraft.player == null) {
            return List.of();
        }

        AABB searchArea = minecraft.player.getBoundingBox().inflate(lookupRadius);
        List<AbstractVillager> villagers = new ArrayList<>(minecraft.level.getEntitiesOfClass(AbstractVillager.class, searchArea));
        villagers.sort(Comparator.comparingDouble(minecraft.player::distanceToSqr));
        return villagers;
    }

    public static Optional<AbstractVillager> findTradingVillager(Minecraft minecraft) {
        return findTradingVillager(minecraft, DEFAULT_LOOKUP_RADIUS);
    }

    public static Optional<AbstractVillager> findTradingVillager(Minecraft minecraft, double lookupRadius) {
        if (minecraft.player == null) {
            return Optional.empty();
        }
        return nearbySorted(minecraft, lookupRadius).stream()
                .filter(villager -> villager.getTradingPlayer() == minecraft.player)
                .findFirst();
    }

    public static Optional<AbstractVillager> findTradingVillagerOrSingleNearby(Minecraft minecraft) {
        return findTradingVillagerOrSingleNearby(minecraft, DEFAULT_LOOKUP_RADIUS);
    }

    public static Optional<AbstractVillager> findTradingVillagerOrSingleNearby(Minecraft minecraft, double lookupRadius) {
        Optional<AbstractVillager> tradingVillager = findTradingVillager(minecraft, lookupRadius);
        if (tradingVillager.isPresent()) {
            return tradingVillager;
        }
        List<AbstractVillager> villagers = nearbySorted(minecraft, lookupRadius);
        if (villagers.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(villagers.get(0));
    }

}
