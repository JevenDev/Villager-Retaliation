package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignmentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AssignedStorageService {
    private static final double STORAGE_INTERACTION_REACH_SQR = 25.0D;
    private static final double OUTPUT_DEPOSIT_REACH_BLOCKS = 2.0D;
    private static final double OUTPUT_DEPOSIT_REACH_SQR = OUTPUT_DEPOSIT_REACH_BLOCKS * OUTPUT_DEPOSIT_REACH_BLOCKS;

    private AssignedStorageService() {
    }

    public static boolean hasAssignedStorage(ServerLevel level, Villager villager) {
        return !AssignedStorageSavedData.get(level).assignedTo(villager.getUUID()).isEmpty();
    }

    public static List<AssignedContainerRecord> assignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).assignedTo(villager.getUUID());
    }

    public static AssignSummary assign(
            ServerPlayer player,
            Villager villager,
            List<StoragePosition> positions,
            String purpose) {
        if (positions.isEmpty()) {
            return new AssignSummary(0, 0, 0);
        }
        AssignedStorageSavedData data = AssignedStorageSavedData.get(player.serverLevel());
        int assigned = 0;
        int alreadyAssigned = 0;
        int invalid = 0;
        int priorityBase = data.assignedTo(villager.getUUID()).size();
        for (StoragePosition position : positions) {
            ServerLevel targetLevel = player.server.getLevel(position.dimension());
            if (targetLevel == null || !isValidContainer(targetLevel, position.pos())) {
                invalid++;
                continue;
            }
            AssignmentResult result = data.assign(new AssignedContainerRecord(
                    position.dimension(),
                    position.pos().immutable(),
                    villager.getUUID(),
                    player.getUUID(),
                    purpose == null || purpose.isBlank() ? "general" : purpose,
                    priorityBase + assigned,
                    "valid"
            ));
            if (result == AssignmentResult.ASSIGNED) {
                assigned++;
            } else {
                alreadyAssigned++;
            }
        }
        return new AssignSummary(assigned, alreadyAssigned, invalid);
    }

    public static int removeAssignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).removeAssignedTo(villager.getUUID());
    }

    public static boolean removeAssignedContainer(ServerLevel level, BlockPos pos) {
        return AssignedStorageSavedData.get(level).removeAssignedAt(level.dimension(), pos);
    }

    public static boolean isValidContainer(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container;
    }

    public static ItemStack depositStack(Villager villager, ItemStack stack) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = liveContainerCandidates(level, villager);
        if (containers.isEmpty()) {
            return stack;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        ItemStack remainder = VillagerInventoryOverflowService.insertIntoContainers(containers, stack, usedContainers);
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return remainder;
    }

    public static ItemStack depositStackNearVillager(Villager villager, ItemStack stack) {
        return depositStackNearVillager(villager, stack, ignored -> true);
    }

    public static ItemStack depositStackNearVillager(Villager villager, ItemStack stack, Predicate<BlockPos> positionFilter) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = nearbyLiveContainerCandidates(level, villager, safeFilter);
        if (containers.isEmpty()) {
            return stack;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        ItemStack remainder = VillagerInventoryOverflowService.insertIntoContainers(containers, stack, usedContainers);
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return remainder;
    }

    public static ItemStack depositStackAtAssignedStorage(Villager villager, BlockPos storagePos, ItemStack stack) {
        if (storagePos == null || stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        if (!isInOutputDepositRange(villager, storagePos)) {
            return stack;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!candidate.pos().equals(storagePos)) {
                continue;
            }
            List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
            ItemStack remainder = VillagerInventoryOverflowService.insertIntoContainers(List.of(candidate), stack, usedContainers);
            VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
            return remainder;
        }
        return stack;
    }

    public static boolean canInteractWithAssignedStorage(Villager villager) {
        return canInteractWithAssignedStorage(villager, ignored -> true);
    }

    public static boolean canInteractWithAssignedStorage(Villager villager, Predicate<BlockPos> positionFilter) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return !nearbyLiveContainerCandidates(level, villager, positionFilter == null ? ignored -> true : positionFilter).isEmpty();
    }

    public static boolean canInteractWithAssignedStorage(Villager villager, BlockPos storagePos) {
        if (storagePos == null || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!isInOutputDepositRange(villager, storagePos)) {
            return false;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (candidate.pos().equals(storagePos)) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos nearestAssignedStoragePos(ServerLevel level, Villager villager) {
        return nearestAssignedStoragePos(level, villager, ignored -> true);
    }

    public static BlockPos nearestAssignedStoragePos(ServerLevel level, Villager villager, Predicate<BlockPos> positionFilter) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager).stream()
                .filter(candidate -> safeFilter.test(candidate.pos()))
                .min((first, second) -> Double.compare(
                        first.pos().distSqr(villagerPos),
                        second.pos().distSqr(villagerPos)))
                .map(candidate -> candidate.pos().immutable())
                .orElse(null);
    }

    public static boolean isInInteractionRange(Villager villager, BlockPos pos) {
        return villager.getEyePosition().distanceToSqr(pos.getCenter()) <= STORAGE_INTERACTION_REACH_SQR
                && villager.position().distanceToSqr(pos.getCenter()) <= STORAGE_INTERACTION_REACH_SQR;
    }

    public static boolean isInOutputDepositRange(Villager villager, BlockPos pos) {
        if (villager.blockPosition().distSqr(pos) > OUTPUT_DEPOSIT_REACH_SQR) {
            return false;
        }
        return hasLineOfSightToStorage(villager, pos);
    }

    private static boolean hasLineOfSightToStorage(Villager villager, BlockPos pos) {
        Vec3 start = villager.getEyePosition();
        Vec3 end = Vec3.atCenterOf(pos);
        BlockHitResult hit = villager.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                villager));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public static int consumeItems(Villager villager, Predicate<ItemStack> predicate, int count) {
        return consumeItems(villager, predicate, count, ignored -> true);
    }

    public static int consumeItems(Villager villager, Predicate<ItemStack> predicate, int count, Predicate<BlockPos> positionFilter) {
        if (count <= 0 || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!safeFilter.test(candidate.pos())) {
                continue;
            }
            Container container = candidate.container();
            boolean used = false;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !predicate.test(stack)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                container.removeItem(slot, removed);
                remaining -= removed;
                used = true;
            }
            if (used) {
                usedContainers.add(candidate);
            }
            if (remaining <= 0) {
                break;
            }
        }
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return count - remaining;
    }

    static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(ServerLevel level, Villager villager) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = new ArrayList<>();
        for (AssignedContainerRecord record : data.assignedTo(villager.getUUID())) {
            ServerLevel targetLevel = level.getServer().getLevel(record.dimension());
            if (targetLevel == null) {
                data.updateValidation(record, "missing_dimension");
                continue;
            }
            if (targetLevel != level) {
                data.updateValidation(record, "wrong_dimension");
                continue;
            }
            if (!level.hasChunkAt(record.pos())) {
                data.updateValidation(record, "unloaded");
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(record.pos());
            if (!(blockEntity instanceof Container container)) {
                data.updateValidation(record, "missing_container");
                continue;
            }
            data.updateValidation(record, "valid");
            containers.add(new VillagerInventoryOverflowService.ContainerCandidate(record.pos().immutable(), container));
        }
        return containers;
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> nearbyLiveContainerCandidates(
            ServerLevel level,
            Villager villager) {
        return nearbyLiveContainerCandidates(level, villager, ignored -> true);
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> nearbyLiveContainerCandidates(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = new ArrayList<>();
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (safeFilter.test(candidate.pos()) && isInInteractionRange(villager, candidate.pos())) {
                containers.add(candidate);
            }
        }
        return containers;
    }

    public static Component assignmentSummary(AssignSummary summary) {
        if (summary.assigned() > 0) {
            return Component.literal("Assigned " + summary.assigned() + " container" + (summary.assigned() == 1 ? "" : "s") + ".");
        }
        if (summary.alreadyAssigned() > 0) {
            return Component.literal("That storage is already assigned to another villager.");
        }
        if (summary.invalid() > 0) {
            return Component.literal("No valid selected containers were found.");
        }
        return Component.literal("No storage was assigned.");
    }

    public record StoragePosition(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record AssignSummary(int assigned, int alreadyAssigned, int invalid) {
    }
}
