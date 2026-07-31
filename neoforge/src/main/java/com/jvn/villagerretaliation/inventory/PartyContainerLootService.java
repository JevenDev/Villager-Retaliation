package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.LockCode;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Container discovery and transfer operations used by the party quick-command runtime. */
public final class PartyContainerLootService {
    public static final int SEARCH_HORIZONTAL_RADIUS = 8;
    public static final int SEARCH_VERTICAL_RADIUS = 4;
    private static final ThreadLocal<Boolean> CHECKING_ACCESS = ThreadLocal.withInitial(() -> false);

    private PartyContainerLootService() {
    }

    public static List<BlockPos> findContainersNear(ServerLevel level, BlockPos center, ServerPlayer commander) {
        if (level == null || center == null || commander == null) {
            return List.of();
        }
        Map<BlockPos, BlockPos> containers = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(
                center.offset(-SEARCH_HORIZONTAL_RADIUS, -SEARCH_VERTICAL_RADIUS, -SEARCH_HORIZONTAL_RADIUS),
                center.offset(SEARCH_HORIZONTAL_RADIUS, SEARCH_VERTICAL_RADIUS, SEARCH_HORIZONTAL_RADIUS))) {
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            if (level.getBlockEntity(cursor) == null) {
                continue;
            }
            VillagerInventoryOverflowService.ContainerCandidate candidate =
                    VillagerInventoryOverflowService.ContainerCandidate.resolve(level, cursor.immutable());
            if (candidate == null || !canAccess(level, candidate, commander)) {
                continue;
            }
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
        if (!canAccess(level, candidate, commander)) {
            return LootResult.UNAUTHORIZED;
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
            ItemStack extracted = VillagerInventoryOverflowService.extractUpTo(
                    villager, container, slot, stack.getCount());
            if (extracted.isEmpty()) {
                continue;
            }
            ItemStack remainder = inventory.insertPlainOutput(extracted.copy());
            int moved = extracted.getCount() - remainder.getCount();
            int unaccepted = extracted.getCount() - moved;
            if (unaccepted > 0) {
                VillagerInventoryOverflowService.restoreToContainerOrDrop(
                        villager, container, extracted.copyWithCount(unaccepted));
            }
            if (moved > 0) {
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

    public static boolean isAvailable(ServerLevel level, BlockPos pos, ServerPlayer commander) {
        VillagerInventoryOverflowService.ContainerCandidate candidate = resolve(level, pos);
        return candidate != null && canAccess(level, candidate, commander);
    }

    /** True while the service is posting a synthetic interaction solely for an access decision. */
    public static boolean isCheckingAccess() {
        return CHECKING_ACCESS.get();
    }

    private static boolean canAccess(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            ServerPlayer commander) {
        if (commander == null
                || !commander.isAlive()
                || commander.serverLevel() != level) {
            return false;
        }
        for (BlockPos pos : candidate.positions()) {
            if (!level.mayInteract(commander, pos)) {
                return false;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BaseContainerBlockEntity container) {
                LockCode lock = container.collectComponents().getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
                if (!lock.unlocksWith(commander.getMainHandItem())) {
                    return false;
                }
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            PlayerInteractEvent.RightClickBlock event;
            boolean alreadyCheckingAccess = CHECKING_ACCESS.get();
            CHECKING_ACCESS.set(true);
            try {
                event = CommonHooks.onRightClickBlock(commander, InteractionHand.MAIN_HAND, pos, hit);
            } finally {
                if (alreadyCheckingAccess) {
                    CHECKING_ACCESS.set(true);
                } else {
                    CHECKING_ACCESS.remove();
                }
            }
            if (event.isCanceled() || event.getUseBlock() == TriState.FALSE) {
                return false;
            }
        }
        return true;
    }

    private static VillagerInventoryOverflowService.ContainerCandidate resolve(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        return VillagerInventoryOverflowService.ContainerCandidate.resolve(level, pos.immutable());
    }

    public enum LootResult {
        MOVED,
        EMPTY,
        FULL,
        INVALID,
        UNAUTHORIZED,
        OUT_OF_REACH
    }
}
