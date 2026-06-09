package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.PaymentBoxBlockEntity;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignmentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
    public static final String GENERAL_PURPOSE = "general";
    public static final String PAYMENT_PURPOSE = "payment";
    private static final double STORAGE_INTERACTION_REACH_SQR = 25.0D;
    private static final double OUTPUT_DEPOSIT_REACH_BLOCKS = 2.0D;
    private static final double OUTPUT_DEPOSIT_REACH_SQR = OUTPUT_DEPOSIT_REACH_BLOCKS * OUTPUT_DEPOSIT_REACH_BLOCKS;

    private AssignedStorageService() {
    }

    public static boolean hasAssignedStorage(ServerLevel level, Villager villager) {
        return !assignedStorage(level, villager).isEmpty();
    }

    public static boolean hasAssignedPaymentStorage(ServerLevel level, Villager villager) {
        return !assignedPaymentStorage(level, villager).isEmpty();
    }

    public static boolean hasLoadedAssignedPaymentStorage(ServerLevel level, Villager villager) {
        return !livePaymentContainerCandidates(level, villager).isEmpty();
    }

    public static List<AssignedContainerRecord> assignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).assignedTo(villager.getUUID()).stream()
                .filter(record -> !PAYMENT_PURPOSE.equals(normalizePurpose(record.purpose())))
                .toList();
    }

    public static List<AssignedContainerRecord> assignedPaymentStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).assignedTo(villager.getUUID(), PAYMENT_PURPOSE);
    }

    public static List<AssignedContainerRecord> allAssignedStorage(ServerLevel level, Villager villager) {
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
        String normalizedPurpose = normalizePurpose(purpose);
        int priorityBase = data.assignedTo(villager.getUUID(), normalizedPurpose).size();
        for (StoragePosition position : positions) {
            ServerLevel targetLevel = player.server.getLevel(position.dimension());
            if (targetLevel == null || !isValidContainerForPurpose(targetLevel, position.pos(), normalizedPurpose)) {
                invalid++;
                continue;
            }
            AssignmentResult result = data.assign(new AssignedContainerRecord(
                    position.dimension(),
                    position.pos().immutable(),
                    villager.getUUID(),
                    player.getUUID(),
                    normalizedPurpose,
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
        int removed = 0;
        for (AssignedContainerRecord record : assignedStorage(level, villager)) {
            if (AssignedStorageSavedData.get(level).removeAssignedAt(record.dimension(), record.pos())) {
                removed++;
            }
        }
        return removed;
    }

    public static int removeAllAssignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).removeAssignedTo(villager.getUUID());
    }

    public static int removeAssignedPaymentStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).removeAssignedTo(villager.getUUID(), PAYMENT_PURPOSE);
    }

    public static boolean removeAssignedContainer(ServerLevel level, BlockPos pos) {
        return AssignedStorageSavedData.get(level).removeAssignedAt(level.dimension(), pos);
    }

    public static boolean isValidContainer(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container;
    }

    public static boolean isValidContainerForPurpose(ServerLevel level, BlockPos pos, String purpose) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return false;
        }
        boolean paymentBox = blockEntity instanceof PaymentBoxBlockEntity;
        return PAYMENT_PURPOSE.equals(normalizePurpose(purpose)) ? paymentBox : !paymentBox;
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

    public static void closeStorageFeedback(ServerLevel level, BlockPos storagePos) {
        if (level == null || storagePos == null) {
            return;
        }
        VillagerInventoryOverflowService.closeContainerFeedbackNow(level, storagePos);
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

    public static BlockPos nearestAssignedStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager).stream()
                .filter(candidate -> containerHasItem(candidate.container(), safePredicate))
                .min((first, second) -> Double.compare(
                        first.pos().distSqr(villagerPos),
                        second.pos().distSqr(villagerPos)))
                .map(candidate -> candidate.pos().immutable())
                .orElse(null);
    }

    public static BlockPos nearestAssignedPaymentStoragePos(ServerLevel level, Villager villager) {
        BlockPos villagerPos = villager.blockPosition();
        return livePaymentContainerCandidates(level, villager).stream()
                .min((first, second) -> Double.compare(
                        first.pos().distSqr(villagerPos),
                        second.pos().distSqr(villagerPos)))
                .map(candidate -> candidate.pos().immutable())
                .orElse(null);
    }

    public static boolean canInteractWithAssignedPaymentStorage(Villager villager, BlockPos paymentPos) {
        if (paymentPos == null || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!isInInteractionRange(villager, paymentPos)) {
            return false;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            if (candidate.pos().equals(paymentPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAssignedPaymentStorageAvailable(ServerLevel level, Villager villager, BlockPos paymentPos) {
        if (paymentPos == null) {
            return false;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            if (candidate.pos().equals(paymentPos)) {
                return true;
            }
        }
        return false;
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

    public static int countItems(Villager villager, Predicate<ItemStack> predicate) {
        return countItems(villager, predicate, ignored -> true);
    }

    public static int countItems(Villager villager, Predicate<ItemStack> predicate, Predicate<BlockPos> positionFilter) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        int count = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!safeFilter.test(candidate.pos())) {
                continue;
            }
            Container container = candidate.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && safePredicate.test(stack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public static int transferItemsAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            int maxCount,
            Function<ItemStack, ItemStack> receiver) {
        if (storagePos == null
                || maxCount <= 0
                || receiver == null
                || !(villager.level() instanceof ServerLevel level)
                || !isInOutputDepositRange(villager, storagePos)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int movedTotal = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!candidate.pos().equals(storagePos)) {
                continue;
            }
            Container container = candidate.container();
            for (int slot = 0; slot < container.getContainerSize() && movedTotal < maxCount; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
                    continue;
                }
                int requested = Math.min(maxCount - movedTotal, stack.getCount());
                ItemStack offered = stack.copyWithCount(requested);
                ItemStack remainder = receiver.apply(offered.copy());
                int moved = offered.getCount() - remainder.getCount();
                if (moved <= 0) {
                    continue;
                }
                container.removeItem(slot, moved);
                movedTotal += moved;
                if (!usedContainers.contains(candidate)) {
                    usedContainers.add(candidate);
                }
            }
            break;
        }
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return movedTotal;
    }

    public static int consumePaymentItems(Villager villager, Predicate<ItemStack> predicate, int count) {
        if (count <= 0 || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
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

    public static int consumePaymentItemsAt(Villager villager, BlockPos paymentPos, Predicate<ItemStack> predicate, int count) {
        if (paymentPos == null || count <= 0 || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            if (!candidate.pos().equals(paymentPos)) {
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
            break;
        }
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return count - remaining;
    }

    public static int countPaymentItems(Villager villager, Predicate<ItemStack> predicate) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        int count = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            Container container = candidate.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public static int countPaymentItemsAt(Villager villager, BlockPos paymentPos, Predicate<ItemStack> predicate) {
        if (paymentPos == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        int count = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            if (!candidate.pos().equals(paymentPos)) {
                continue;
            }
            Container container = candidate.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    count += stack.getCount();
                }
            }
            break;
        }
        return count;
    }

    private static boolean containerHasItem(Container container, Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, record -> !PAYMENT_PURPOSE.equals(normalizePurpose(record.purpose())));
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> livePaymentContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, record -> PAYMENT_PURPOSE.equals(normalizePurpose(record.purpose())));
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(
            ServerLevel level,
            Villager villager,
            Predicate<AssignedContainerRecord> recordFilter) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = new ArrayList<>();
        for (AssignedContainerRecord record : data.assignedTo(villager.getUUID())) {
            if (recordFilter != null && !recordFilter.test(record)) {
                continue;
            }
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
            if (!isValidContainerForPurpose(level, record.pos(), record.purpose())) {
                data.updateValidation(record, "wrong_purpose");
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

    private static String normalizePurpose(String purpose) {
        return purpose == null || purpose.isBlank() ? GENERAL_PURPOSE : purpose;
    }

    public record StoragePosition(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record AssignSummary(int assigned, int alreadyAssigned, int invalid) {
    }
}
