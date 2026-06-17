package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public final class VillageScopeKeys {
    private static final String PREFIX = "village:";

    private VillageScopeKeys() {
    }

    public static String forVillager(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return "";
        }
        return VillageMembership.resolve(level, villager)
                .map(area -> forPosition(level.dimension(), area.centerBlock()))
                .or(() -> VillagerSocialGraphService.knownVillage(level, villager.getUUID())
                        .map(VillageScopeKeys::fromSavedSocialKey)
                        .filter(key -> !key.isBlank()))
                .orElseGet(() -> forPosition(level.dimension(), villager.blockPosition()));
    }

    public static String forResolvedVillageOrPosition(ServerLevel level, Villager villager, BlockPos fallbackPos) {
        if (level == null) {
            return "";
        }
        return VillageMembership.resolve(level, villager)
                .map(area -> forPosition(level.dimension(), area.centerBlock()))
                .orElseGet(() -> forPosition(level.dimension(), fallbackPos));
    }

    public static String forPosition(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension == null ? "" : forPosition(dimension.location(), pos);
    }

    public static String forPosition(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return "";
        }
        return PREFIX + dimension + ":" + posKey(pos);
    }

    public static String fromSavedSocialKey(String savedVillageKey) {
        if (savedVillageKey == null || savedVillageKey.isBlank()) {
            return "";
        }
        if (savedVillageKey.startsWith(PREFIX)) {
            return savedVillageKey;
        }
        int separator = savedVillageKey.indexOf('@');
        if (separator <= 0 || separator >= savedVillageKey.length() - 1) {
            return "";
        }
        return PREFIX + savedVillageKey.substring(0, separator) + ":" + savedVillageKey.substring(separator + 1);
    }

    public static Optional<ResourceKey<Level>> dimension(String scopeKey) {
        String dimension = dimensionText(scopeKey);
        if (dimension.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        return dimensionId == null
                ? Optional.empty()
                : Optional.of(ResourceKey.create(Registries.DIMENSION, dimensionId));
    }

    public static Optional<BlockPos> pos(String scopeKey) {
        String pos = posText(scopeKey);
        if (pos.isBlank()) {
            return Optional.empty();
        }
        String[] parts = pos.split(",");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String dimensionText(String scopeKey) {
        if (scopeKey == null || !scopeKey.startsWith(PREFIX)) {
            return "";
        }
        int separator = scopeKey.lastIndexOf(':');
        return separator <= PREFIX.length() ? "" : scopeKey.substring(PREFIX.length(), separator);
    }

    private static String posText(String scopeKey) {
        if (scopeKey == null || !scopeKey.startsWith(PREFIX)) {
            return "";
        }
        int separator = scopeKey.lastIndexOf(':');
        return separator < 0 || separator >= scopeKey.length() - 1 ? "" : scopeKey.substring(separator + 1);
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
