package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Container discovery and transfer operations used by the party quick-command runtime. */
public final class PartyContainerLootService {
    public static final int SEARCH_HORIZONTAL_RADIUS = 8;
    public static final int SEARCH_VERTICAL_RADIUS = 4;

    private PartyContainerLootService() {
    }

    public static List<BlockPos> findContainersNear(ServerLevel level, BlockPos center) {
        if (level == null || center == null) {
            return List.of();
        }
        Map<BlockPos, BlockPos> containers = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(
                center.offset(-SEARCH_HORIZONTAL_RADIUS, -SEARCH_VERTICAL_RADIUS, -SEARCH_HORIZONTAL_RADIUS),
                center.offset(SEARCH_HORIZONTAL_RADIUS, SEARCH_VERTICAL_RADIUS, SEARCH_HORIZONTAL_RADIUS))) {
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            VillagerInventoryOverflowService.ContainerCandidate candidate =
                    VillagerInventoryOverflowService.ContainerCandidate.resolve(level, cursor.immutable(), container);
            containers.putIfAbsent(candidate.pos(), candidate.pos());
        }
        List<BlockPos> result = new ArrayList<>(containers.values());
        result.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return List.copyOf(result);
    }

    public static LootResult loot(ServerLevel level, Villager villager, BlockPos pos, ServerPlayer commander) {
        VillagerInventoryOverflowService.ContainerCandidate candidate = resolve(level, pos);
        if (candidate == null) {
            return LootResult.INVALID;
        }
        if (!candidate.isInInteractionRange(villager)) {
            return LootResult.OUT_OF_REACH;
        }

        for (BlockPos containerPos : candidate.positions()) {
            BlockEntity blockEntity = level.getBlockEntity(containerPos);
            if (blockEntity instanceof RandomizableContainer randomizable) {
                randomizable.unpackLootTable(commander);
            }
        }

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        Container container = candidate.container();
        boolean movedAny = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !container.canTakeItem(inventory, slot, stack)) {
                continue;
            }
            ItemStack remainder = inventory.insertPlainOutput(stack.copy());
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                container.removeItem(slot, moved);
                movedAny = true;
            }
            if (!remainder.isEmpty()) {
                if (movedAny) {
                    container.setChanged();
                    VillagerInventoryOverflowService.openUsedContainers(level, List.of(candidate));
                }
                return LootResult.FULL;
            }
        }
        if (movedAny) {
            container.setChanged();
            VillagerInventoryOverflowService.openUsedContainers(level, List.of(candidate));
            return LootResult.MOVED;
        }
        return LootResult.EMPTY;
    }

    public static boolean isAvailable(ServerLevel level, BlockPos pos) {
        return resolve(level, pos) != null;
    }

    private static VillagerInventoryOverflowService.ContainerCandidate resolve(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        return VillagerInventoryOverflowService.ContainerCandidate.resolve(level, pos.immutable(), container);
    }

    public enum LootResult {
        MOVED,
        EMPTY,
        FULL,
        INVALID,
        OUT_OF_REACH
    }
}
