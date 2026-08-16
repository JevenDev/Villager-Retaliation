package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Short-lived server-side claims for cargo planned against framed stock targets. */
final class ContainerTransferClaimLedger {
    private static final long CLAIM_TTL_TICKS = 20L * 10L;
    private static final Map<ClaimKey, Claim> CLAIMS = new HashMap<>();

    private ContainerTransferClaimLedger() {
    }

    static void replaceAll(
            ServerLevel level,
            UUID owner,
            VillagerFilterPolicy.TransferOperation operation,
            Map<BlockPos, List<ItemStack>> claims) {
        if (level == null || owner == null || operation == null) {
            return;
        }
        cleanup(level);
        CLAIMS.keySet().removeIf(key -> key.owner().equals(owner) && key.operation() == operation);
        if (claims == null || claims.isEmpty()) {
            return;
        }
        long expiresAt = level.getGameTime() + CLAIM_TTL_TICKS;
        for (Map.Entry<BlockPos, List<ItemStack>> entry : claims.entrySet()) {
            BlockPos pos = entry.getKey();
            List<ItemStack> stacks = copyNonEmpty(entry.getValue());
            if (pos == null || stacks.isEmpty()) {
                continue;
            }
            CLAIMS.put(
                    new ClaimKey(level.dimension(), pos, owner, operation),
                    new Claim(stacks, expiresAt));
        }
    }

    static Map<BlockPos, List<ItemStack>> snapshot(
            ServerLevel level,
            UUID owner,
            VillagerFilterPolicy.TransferOperation operation) {
        if (level == null || owner == null || operation == null) {
            return Map.of();
        }
        cleanup(level);
        Map<BlockPos, List<ItemStack>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<ClaimKey, Claim> entry : CLAIMS.entrySet()) {
            ClaimKey key = entry.getKey();
            if (key.dimension().equals(level.dimension())
                    && key.owner().equals(owner)
                    && key.operation() == operation) {
                snapshot.put(key.pos(), copyNonEmpty(entry.getValue().stacks()));
            }
        }
        return snapshot;
    }

    static int count(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            UUID excludedOwner,
            VillagerFilterPolicy.TransferOperation operation,
            Predicate<ItemStack> matcher) {
        if (level == null || candidate == null || operation == null || matcher == null) {
            return 0;
        }
        cleanup(level);
        long count = 0L;
        for (Map.Entry<ClaimKey, Claim> entry : CLAIMS.entrySet()) {
            ClaimKey key = entry.getKey();
            if (!key.dimension().equals(level.dimension())
                    || !key.pos().equals(candidate.pos())
                    || key.operation() != operation
                    || excludedOwner != null && excludedOwner.equals(key.owner())) {
                continue;
            }
            for (ItemStack stack : entry.getValue().stacks()) {
                if (matcher.test(stack)) {
                    count += stack.getCount();
                    if (count >= Integer.MAX_VALUE) {
                        return Integer.MAX_VALUE;
                    }
                }
            }
        }
        return (int) count;
    }

    static void release(UUID owner) {
        if (owner != null) {
            CLAIMS.keySet().removeIf(key -> key.owner().equals(owner));
        }
    }

    static void release(UUID owner, VillagerFilterPolicy.TransferOperation operation) {
        if (owner != null && operation != null) {
            CLAIMS.keySet().removeIf(key ->
                    key.owner().equals(owner) && key.operation() == operation);
        }
    }

    static void clear() {
        CLAIMS.clear();
    }

    static int activeClaimCount() {
        return CLAIMS.size();
    }

    private static void cleanup(ServerLevel level) {
        long gameTime = level.getGameTime();
        CLAIMS.entrySet().removeIf(entry ->
                entry.getKey().dimension().equals(level.dimension())
                        && entry.getValue().expiresAt() <= gameTime);
    }

    private static List<ItemStack> copyNonEmpty(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }

    private record ClaimKey(
            ResourceKey<Level> dimension,
            BlockPos pos,
            UUID owner,
            VillagerFilterPolicy.TransferOperation operation) {
        private ClaimKey {
            pos = pos.immutable();
        }
    }

    private record Claim(List<ItemStack> stacks, long expiresAt) {
    }
}
