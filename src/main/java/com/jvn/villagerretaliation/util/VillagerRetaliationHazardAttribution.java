package com.jvn.villagerretaliation.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class VillagerRetaliationHazardAttribution {
    private static final long HAZARD_OWNER_TTL_TICKS = 20L * 60L * 2L;
    private static final int DAMAGE_SEARCH_BLOCK_RADIUS = 2;
    private static final Map<ResourceKey<Level>, Map<BlockPos, HazardOwner>> HAZARD_OWNERS = new HashMap<>();

    private VillagerRetaliationHazardAttribution() {
    }

    public static void rememberPlayerPlacedHazard(Player player, Level level, BlockPos clickedPos, Direction face, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)
                || player.isCreative()
                || player.isSpectator()
                || !isTrackedHazardItem(stack)) {
            return;
        }

        prune(serverLevel);
        Map<BlockPos, HazardOwner> owners = HAZARD_OWNERS.computeIfAbsent(serverLevel.dimension(), ignored -> new HashMap<>());
        HazardOwner owner = new HazardOwner(player.getUUID(), serverLevel.getGameTime());
        owners.put(clickedPos.immutable(), owner);
        if (face != null) {
            owners.put(clickedPos.relative(face).immutable(), owner);
        }
    }

    public static Optional<Player> resolvePlayerOwner(LivingEntity victim, DamageSource source) {
        if (!isTrackedHazardDamage(source) || !(victim.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }

        prune(level);
        Map<BlockPos, HazardOwner> owners = HAZARD_OWNERS.get(level.dimension());
        if (owners == null || owners.isEmpty()) {
            return Optional.empty();
        }

        HazardOwner owner = findNearestRecentOwner(victim, level, owners);
        if (owner == null) {
            return Optional.empty();
        }

        Player player = level.getPlayerByUUID(owner.playerId());
        return player == null ? Optional.empty() : Optional.of(player);
    }

    public static boolean isPlayerAttributedHazardDamage(LivingEntity victim, DamageSource source) {
        return resolvePlayerOwner(victim, source).isPresent();
    }

    private static boolean isTrackedHazardItem(ItemStack stack) {
        return stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.FLINT_AND_STEEL)
                || stack.is(Items.FIRE_CHARGE);
    }

    private static boolean isTrackedHazardDamage(DamageSource source) {
        return source.is(DamageTypes.LAVA) || source.is(DamageTypeTags.IS_FIRE);
    }

    private static HazardOwner findNearestRecentOwner(
            LivingEntity victim,
            ServerLevel level,
            Map<BlockPos, HazardOwner> owners
    ) {
        AABB searchArea = victim.getBoundingBox().inflate(DAMAGE_SEARCH_BLOCK_RADIUS);
        BlockPos min = BlockPos.containing(
                Mth.floor(searchArea.minX),
                Mth.floor(searchArea.minY),
                Mth.floor(searchArea.minZ)
        );
        BlockPos max = BlockPos.containing(
                Mth.floor(searchArea.maxX),
                Mth.floor(searchArea.maxY),
                Mth.floor(searchArea.maxZ)
        );

        HazardOwner bestOwner = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        long gameTime = level.getGameTime();
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            HazardOwner owner = owners.get(candidate);
            if (owner == null || gameTime - owner.gameTime() > HAZARD_OWNER_TTL_TICKS) {
                continue;
            }

            double distanceSqr = candidate.distToCenterSqr(victim.position());
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                bestOwner = owner;
            }
        }
        return bestOwner;
    }

    private static void prune(ServerLevel level) {
        Map<BlockPos, HazardOwner> owners = HAZARD_OWNERS.get(level.dimension());
        if (owners == null) {
            return;
        }

        long gameTime = level.getGameTime();
        owners.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > HAZARD_OWNER_TTL_TICKS);
        if (owners.isEmpty()) {
            HAZARD_OWNERS.remove(level.dimension());
        }
    }

    private record HazardOwner(UUID playerId, long gameTime) {
    }
}
