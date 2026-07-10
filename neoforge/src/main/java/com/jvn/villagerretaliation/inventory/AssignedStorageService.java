package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.PaymentBoxBlockEntity;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignmentResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
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
    public static final String INPUT_PURPOSE = "input";
    public static final String OUTPUT_PURPOSE = "output";
    public static final String TOOL_PURPOSE = "tool";
    public static final String PAYMENT_PURPOSE = "payment";
    private static final double STORAGE_INTERACTION_REACH_SQR = 25.0D;
    private static final long STORAGE_RETRY_COOLDOWN_TICKS = 20L * 15L;
    private static final long STORAGE_FULL_COOLDOWN_TICKS = 20L * 45L;
    private static final Map<StorageFailureKey, StorageFailure> STORAGE_FAILURES = new HashMap<>();

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

    public static boolean hasLiveAssignedOutputStorage(ServerLevel level, Villager villager) {
        return !liveOutputContainerCandidates(level, villager).isEmpty();
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
            if (!position.dimension().equals(villager.level().dimension())) {
                invalid++;
                continue;
            }
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
            if (AssignedStorageSavedData.get(level).removeAssignment(record)) {
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
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = liveOutputContainerCandidates(level, villager);
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
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = nearbyLiveOutputContainerCandidates(level, villager, safeFilter);
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
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!candidate.matches(storagePos) || !candidate.isInInteractionRange(villager)) {
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

    public static void rememberOutputStorageFull(ServerLevel level, Villager villager, BlockPos storagePos) {
        rememberStorageFailure(level, villager, storagePos, StorageUse.OUTPUT, "full", STORAGE_FULL_COOLDOWN_TICKS);
    }

    public static void rememberOutputStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos, String reason) {
        rememberStorageFailure(level, villager, storagePos, StorageUse.OUTPUT, reason, STORAGE_RETRY_COOLDOWN_TICKS);
    }

    public static void rememberToolStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos, String reason) {
        rememberStorageFailure(level, villager, storagePos, StorageUse.TOOL, reason, STORAGE_RETRY_COOLDOWN_TICKS);
    }

    public static void clearStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos) {
        if (level == null || villager == null || storagePos == null) {
            return;
        }
        UUID villagerId = villager.getUUID();
        STORAGE_FAILURES.keySet().removeIf(key ->
                key.villagerId().equals(villagerId)
                        && key.dimension().equals(level.dimension())
                        && key.pos().equals(storagePos));
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

    public static boolean canInteractWithAssignedOutputStorage(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return !nearbyLiveOutputContainerCandidates(level, villager, ignored -> true).isEmpty();
    }

    public static boolean canInteractWithAssignedStorage(Villager villager, BlockPos storagePos) {
        if (storagePos == null || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (candidate.matches(storagePos) && candidate.isInInteractionRange(villager)) {
                return true;
            }
        }
        return false;
    }

    public static List<BlockPos> assignedStorageInteractionPositions(ServerLevel level, Villager villager, BlockPos storagePos) {
        if (level == null || villager == null || storagePos == null) {
            return List.of();
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (candidate.matches(storagePos)) {
                return candidate.positions();
            }
        }
        return level.hasChunkAt(storagePos) ? List.of(storagePos.immutable()) : List.of();
    }

    public static BlockPos nearestAssignedStoragePos(ServerLevel level, Villager villager) {
        return nearestAssignedStoragePos(level, villager, ignored -> true);
    }

    public static BlockPos nearestAssignedStoragePos(ServerLevel level, Villager villager, Predicate<BlockPos> positionFilter) {
        return nearestAssignedStoragePos(level, villager, positionFilter, StorageUse.INPUT);
    }

    public static BlockPos nearestAssignedNonPaymentStoragePos(ServerLevel level, Villager villager) {
        return nearestAssignedStoragePos(level, villager, ignored -> true, StorageUse.ANY_NON_PAYMENT);
    }

    public static List<BlockPos> assignedNonPaymentStoragePositions(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter) {
        return assignedStoragePositions(level, villager, positionFilter, StorageUse.ANY_NON_PAYMENT);
    }

    public static BlockPos nearestAssignedOutputStoragePos(ServerLevel level, Villager villager) {
        return nearestAssignedStoragePos(level, villager, ignored -> true, StorageUse.OUTPUT);
    }

    public static BlockPos nearestAssignedOutputStoragePos(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter) {
        return nearestAssignedStoragePos(level, villager, positionFilter, StorageUse.OUTPUT);
    }

    private static BlockPos nearestAssignedStoragePos(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter,
            StorageUse use) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager, use).stream()
                .filter(candidate -> candidate.anyPositionMatches(safeFilter))
                .filter(candidate -> !isStorageRecentlyFailed(level, villager, candidate, use))
                .min((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, safeFilter))
                .orElse(null);
    }

    private static List<BlockPos> assignedStoragePositions(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter,
            StorageUse use) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager, use).stream()
                .filter(candidate -> candidate.anyPositionMatches(safeFilter))
                .filter(candidate -> !isStorageRecentlyFailed(level, villager, candidate, use))
                .sorted((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, safeFilter))
                .toList();
    }

    public static BlockPos nearestAssignedStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return nearestAssignedStoragePosContaining(level, villager, predicate, StorageUse.INPUT);
    }

    public static BlockPos nearestAssignedInputStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter) {
        return nearestAssignedStoragePosContaining(
                level,
                villager,
                predicate,
                positionFilter,
                StorageUse.INPUT);
    }

    public static List<BlockPos> assignedStoragePositionsContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return assignedStoragePositionsContaining(
                level,
                villager,
                predicate,
                ignored -> true,
                StorageUse.INPUT);
    }

    public static BlockPos nearestAssignedNonPaymentStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return nearestAssignedNonPaymentStoragePosContaining(level, villager, predicate, ignored -> true);
    }

    public static BlockPos nearestAssignedNonPaymentStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter) {
        return nearestAssignedStoragePosContaining(
                level,
                villager,
                predicate,
                positionFilter,
                StorageUse.ANY_NON_PAYMENT);
    }

    public static List<BlockPos> assignedNonPaymentStoragePositionsContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter) {
        return assignedStoragePositionsContaining(
                level,
                villager,
                predicate,
                positionFilter,
                StorageUse.ANY_NON_PAYMENT);
    }

    public static BlockPos nearestAssignedToolStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return nearestAssignedStoragePosContaining(level, villager, predicate, StorageUse.TOOL);
    }

    private static BlockPos nearestAssignedStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            StorageUse use) {
        return nearestAssignedStoragePosContaining(level, villager, predicate, ignored -> true, use);
    }

    private static BlockPos nearestAssignedStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use) {
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager, use).stream()
                .filter(candidate -> candidate.anyPositionMatches(safeFilter))
                .filter(candidate -> !isStorageRecentlyFailed(level, villager, candidate, use))
                .filter(candidate -> containerHasItem(candidate.container(), safePredicate))
                .min((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, safeFilter))
                .orElse(null);
    }

    private static List<BlockPos> assignedStoragePositionsContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use) {
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        BlockPos villagerPos = villager.blockPosition();
        return liveContainerCandidates(level, villager, use).stream()
                .filter(candidate -> candidate.anyPositionMatches(safeFilter))
                .filter(candidate -> !isStorageRecentlyFailed(level, villager, candidate, use))
                .filter(candidate -> containerHasItem(candidate.container(), safePredicate))
                .sorted((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, safeFilter))
                .toList();
    }

    public static BlockPos nearestAssignedPaymentStoragePos(ServerLevel level, Villager villager) {
        BlockPos villagerPos = villager.blockPosition();
        return livePaymentContainerCandidates(level, villager).stream()
                .min((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, ignored -> true))
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
            if (candidate.matches(paymentPos)) {
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
            if (candidate.matches(paymentPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInInteractionRange(Villager villager, BlockPos pos) {
        if (villager == null || pos == null) {
            return false;
        }
        Vec3 storageCenter = pos.getCenter();
        Vec3 centeredFeet = new Vec3(
                villager.blockPosition().getX() + 0.5D,
                villager.getY(),
                villager.blockPosition().getZ() + 0.5D);
        Vec3 centeredEye = new Vec3(
                centeredFeet.x,
                villager.blockPosition().getY() + villager.getEyeHeight(),
                centeredFeet.z);
        boolean positionWithinReach = villager.position().distanceToSqr(storageCenter) <= STORAGE_INTERACTION_REACH_SQR
                || centeredFeet.distanceToSqr(storageCenter) <= STORAGE_INTERACTION_REACH_SQR;
        boolean eyeWithinReach = villager.getEyePosition().distanceToSqr(storageCenter) <= STORAGE_INTERACTION_REACH_SQR
                || centeredEye.distanceToSqr(storageCenter) <= STORAGE_INTERACTION_REACH_SQR;
        return positionWithinReach
                && eyeWithinReach
                && hasLineOfSightToStorage(villager, pos);
    }

    private static boolean hasLineOfSightToStorage(Villager villager, BlockPos pos) {
        if (villager == null || pos == null) {
            return false;
        }
        if (hasLineOfSightToStorageFrom(villager, villager.getEyePosition(), pos)) {
            return true;
        }
        Vec3 centeredEye = new Vec3(
                villager.blockPosition().getX() + 0.5D,
                villager.blockPosition().getY() + villager.getEyeHeight(),
                villager.blockPosition().getZ() + 0.5D);
        return hasLineOfSightToStorageFrom(villager, centeredEye, pos);
    }

    private static boolean hasLineOfSightToStorageFrom(Villager villager, Vec3 start, BlockPos pos) {
        return hitsStorageBlock(villager, start, Vec3.atCenterOf(pos), pos)
                || hitsStorageBlock(villager, start, storageSightPoint(pos, start, 0.5D), pos)
                || hitsStorageBlock(villager, start, storageSightPoint(pos, start, 0.85D), pos)
                || hitsStorageBlock(villager, start, storageSightPoint(pos, start, 0.15D), pos);
    }

    private static Vec3 storageSightPoint(BlockPos pos, Vec3 start, double yOffset) {
        double x = Math.clamp(start.x, pos.getX() + 0.08D, pos.getX() + 0.92D);
        double z = Math.clamp(start.z, pos.getZ() + 0.08D, pos.getZ() + 0.92D);
        return new Vec3(x, pos.getY() + yOffset, z);
    }

    private static boolean hitsStorageBlock(Villager villager, Vec3 start, Vec3 end, BlockPos pos) {
        BlockHitResult hit = villager.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
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
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveInputContainerCandidates(level, villager)) {
            if (!candidate.anyPositionMatches(safeFilter)) {
                continue;
            }
            Container container = candidate.container();
            boolean used = false;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
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

    public static int consumeItemValue(
            Villager villager,
            Predicate<ItemStack> predicate,
            ToIntFunction<ItemStack> value,
            int targetValue,
            Predicate<BlockPos> positionFilter) {
        if (targetValue <= 0 || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        ToIntFunction<ItemStack> safeValue = value == null ? ignored -> 1 : value;
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int consumedValue = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveInputContainerCandidates(level, villager)) {
            if (!candidate.anyPositionMatches(safeFilter)) {
                continue;
            }
            Container container = candidate.container();
            boolean used = false;
            for (int slot = 0; slot < container.getContainerSize() && consumedValue < targetValue; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
                    continue;
                }
                int itemValue = Math.max(1, safeValue.applyAsInt(stack));
                int remainingValue = targetValue - consumedValue;
                int requested = Math.max(1, (remainingValue + itemValue - 1) / itemValue);
                int removed = Math.min(requested, stack.getCount());
                container.removeItem(slot, removed);
                consumedValue += removed * itemValue;
                used = true;
            }
            if (used) {
                usedContainers.add(candidate);
            }
            if (consumedValue >= targetValue) {
                break;
            }
        }
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return consumedValue;
    }

    public static int countItems(Villager villager, Predicate<ItemStack> predicate) {
        return countItems(villager, predicate, ignored -> true);
    }

    public static int countItems(Villager villager, Predicate<ItemStack> predicate, Predicate<BlockPos> positionFilter) {
        return countItems(villager, predicate, positionFilter, StorageUse.INPUT);
    }

    public static int countItemsInNonPaymentStorage(Villager villager, Predicate<ItemStack> predicate) {
        return countItems(villager, predicate, ignored -> true, StorageUse.ANY_NON_PAYMENT);
    }

    private static int countItems(
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        int count = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager, use)) {
            if (!candidate.anyPositionMatches(safeFilter)) {
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
        return transferItemsAtAssignedStorage(villager, storagePos, predicate, maxCount, receiver, StorageUse.INPUT);
    }

    public static int transferItemsAtAssignedNonPaymentStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            int maxCount,
            Function<ItemStack, ItemStack> receiver) {
        return transferItemsAtAssignedStorage(
                villager,
                storagePos,
                predicate,
                maxCount,
                receiver,
                StorageUse.ANY_NON_PAYMENT);
    }

    public static int transferToolAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            Function<ItemStack, ItemStack> receiver) {
        return transferItemsAtAssignedStorage(villager, storagePos, predicate, 1, receiver, StorageUse.TOOL);
    }

    private static int transferItemsAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            int maxCount,
            Function<ItemStack, ItemStack> receiver,
            StorageUse use) {
        if (storagePos == null
                || maxCount <= 0
                || receiver == null
                || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int movedTotal = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager, use)) {
            if (!candidate.matches(storagePos) || !candidate.isInInteractionRange(villager)) {
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

    public static int transferFirstMatchingStackAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            Function<ItemStack, ItemStack> receiver) {
        if (storagePos == null
                || receiver == null
                || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate);
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveContainerCandidates(level, villager)) {
            if (!candidate.matches(storagePos) || !candidate.isInInteractionRange(villager)) {
                continue;
            }
            Container container = candidate.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
                    continue;
                }
                ItemStack offered = stack.copy();
                ItemStack remainder = receiver.apply(offered.copy());
                int moved = offered.getCount() - remainder.getCount();
                if (moved <= 0) {
                    return 0;
                }
                container.removeItem(slot, moved);
                VillagerInventoryOverflowService.openUsedContainers(level, List.of(candidate));
                return moved;
            }
            break;
        }
        return 0;
    }

    public static int consumePaymentItems(Villager villager, Predicate<ItemStack> predicate, int count) {
        if (count <= 0 || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            Container container = candidate.container();
            boolean used = false;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
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
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int remaining = count;
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : livePaymentContainerCandidates(level, villager)) {
            if (!candidate.matches(paymentPos)) {
                continue;
            }
            Container container = candidate.container();
            boolean used = false;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !safePredicate.test(stack)) {
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
            if (!candidate.matches(paymentPos)) {
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

    private static Predicate<ItemStack> withdrawalPredicate(
            Villager villager,
            Predicate<ItemStack> workerPredicate) {
        Predicate<ItemStack> safeWorkerPredicate = workerPredicate == null ? ignored -> true : workerPredicate;
        return stack -> safeWorkerPredicate.test(stack) && VillagerItemFilterService.mayWithdraw(villager, stack);
    }

    static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, StorageUse.ANY_NON_PAYMENT);
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> liveInputContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, StorageUse.INPUT);
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> liveOutputContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, StorageUse.OUTPUT);
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(
            ServerLevel level,
            Villager villager,
            StorageUse use) {
        return liveContainerCandidates(level, villager, record -> purposeMatchesUse(record.purpose(), use));
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> livePaymentContainerCandidates(ServerLevel level, Villager villager) {
        return liveContainerCandidates(level, villager, record -> PAYMENT_PURPOSE.equals(normalizePurpose(record.purpose())));
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(
            ServerLevel level,
            Villager villager,
            Predicate<AssignedContainerRecord> recordFilter) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        Map<BlockPos, VillagerInventoryOverflowService.ContainerCandidate> containers = new LinkedHashMap<>();
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
                continue;
            }
            if (!level.hasChunkAt(record.pos())) {
                data.updateValidation(record, "unloaded");
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(record.pos());
            if (!(blockEntity instanceof Container container)) {
                data.removeAssignedAt(record.dimension(), record.pos());
                continue;
            }
            if (!isValidContainerForPurpose(level, record.pos(), record.purpose())) {
                data.removeAssignedAt(record.dimension(), record.pos());
                continue;
            }
            data.updateValidation(record, "valid");
            VillagerInventoryOverflowService.ContainerCandidate candidate =
                    VillagerInventoryOverflowService.ContainerCandidate.resolve(level, record.pos().immutable(), container);
            containers.putIfAbsent(candidate.pos(), candidate);
        }
        return new ArrayList<>(containers.values());
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
            if (candidate.anyPositionMatches(safeFilter) && candidate.isInInteractionRange(villager)) {
                containers.add(candidate);
            }
        }
        return containers;
    }

    private static List<VillagerInventoryOverflowService.ContainerCandidate> nearbyLiveOutputContainerCandidates(
            ServerLevel level,
            Villager villager,
            Predicate<BlockPos> positionFilter) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = new ArrayList<>();
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveOutputContainerCandidates(level, villager)) {
            if (candidate.anyPositionMatches(safeFilter)
                    && !isStorageRecentlyFailed(level, villager, candidate, StorageUse.OUTPUT)
                    && candidate.isInInteractionRange(villager)) {
                containers.add(candidate);
            }
        }
        return containers;
    }

    public static AssignmentSummaryMessage assignmentSummaryMessage(AssignSummary summary) {
        if (summary.assigned() > 0) {
            return new AssignmentSummaryMessage(
                    "interaction.storage.assign_result.assigned",
                    Map.of(
                            "count", Integer.toString(summary.assigned()),
                            "plural", summary.assigned() == 1 ? "" : "s"
                    )
            );
        }
        if (summary.alreadyAssigned() > 0) {
            return new AssignmentSummaryMessage("interaction.storage.assign_result.already_assigned", Map.of());
        }
        if (summary.invalid() > 0) {
            return new AssignmentSummaryMessage("interaction.storage.assign_result.invalid", Map.of());
        }
        return new AssignmentSummaryMessage("interaction.storage.assign_result.none", Map.of());
    }

    public static String normalizePurpose(String purpose) {
        return purpose == null || purpose.isBlank() ? GENERAL_PURPOSE : purpose;
    }

    private static boolean isGlobalPurpose(String purpose) {
        String normalized = normalizePurpose(purpose);
        return GENERAL_PURPOSE.equals(normalized) || "global".equals(normalized);
    }

    private static boolean purposeMatchesUse(String purpose, StorageUse use) {
        String normalized = normalizePurpose(purpose);
        if (PAYMENT_PURPOSE.equals(normalized)) {
            return use == StorageUse.PAYMENT;
        }
        if (isGlobalPurpose(normalized)) {
            return use != StorageUse.PAYMENT;
        }
        return switch (use) {
            case INPUT -> INPUT_PURPOSE.equals(normalized);
            case OUTPUT -> OUTPUT_PURPOSE.equals(normalized);
            case TOOL -> TOOL_PURPOSE.equals(normalized);
            case ANY_NON_PAYMENT -> true;
            case PAYMENT -> false;
        };
    }

    private static void rememberStorageFailure(
            ServerLevel level,
            Villager villager,
            BlockPos storagePos,
            StorageUse use,
            String reason,
            long cooldownTicks) {
        if (level == null || villager == null || storagePos == null || use == null) {
            return;
        }
        StorageFailureKey key = new StorageFailureKey(villager.getUUID(), level.dimension(), storagePos.immutable(), use);
        STORAGE_FAILURES.put(key, new StorageFailure(
                reason == null || reason.isBlank() ? "failed" : reason,
                level.getGameTime() + Math.max(1L, cooldownTicks)));
    }

    private static boolean isStorageRecentlyFailed(
            ServerLevel level,
            Villager villager,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            StorageUse use) {
        if (level == null || villager == null || candidate == null || use == null) {
            return false;
        }
        expireStorageFailures(level);
        for (BlockPos storagePos : candidate.positions()) {
            if (isStorageRecentlyFailed(level, villager, storagePos, use)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStorageRecentlyFailed(ServerLevel level, Villager villager, BlockPos storagePos, StorageUse use) {
        long now = level.getGameTime();
        for (StorageUse failureUse : StorageUse.values()) {
            if (!storageFailureApplies(failureUse, use)) {
                continue;
            }
            StorageFailureKey key = new StorageFailureKey(villager.getUUID(), level.dimension(), storagePos.immutable(), failureUse);
            StorageFailure failure = STORAGE_FAILURES.get(key);
            if (failure == null) {
                continue;
            }
            if (failure.expiresGameTime() <= now) {
                STORAGE_FAILURES.remove(key);
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean storageFailureApplies(StorageUse failureUse, StorageUse requestedUse) {
        if (failureUse == requestedUse) {
            return true;
        }
        if (failureUse == StorageUse.ANY_NON_PAYMENT) {
            return requestedUse != StorageUse.PAYMENT;
        }
        return requestedUse == StorageUse.ANY_NON_PAYMENT && failureUse != StorageUse.PAYMENT;
    }

    private static void expireStorageFailures(ServerLevel level) {
        long now = level.getGameTime();
        STORAGE_FAILURES.entrySet().removeIf(entry ->
                entry.getKey().dimension().equals(level.dimension())
                        && entry.getValue().expiresGameTime() <= now);
    }

    private enum StorageUse {
        INPUT,
        OUTPUT,
        TOOL,
        ANY_NON_PAYMENT,
        PAYMENT
    }

    private record StorageFailureKey(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, StorageUse use) {
    }

    private record StorageFailure(String reason, long expiresGameTime) {
    }

    public record StoragePosition(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record AssignSummary(int assigned, int alreadyAssigned, int invalid) {
    }

    public record AssignmentSummaryMessage(String key, Map<String, String> replacements) {
        public AssignmentSummaryMessage {
            key = key == null ? "" : key;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }
}
