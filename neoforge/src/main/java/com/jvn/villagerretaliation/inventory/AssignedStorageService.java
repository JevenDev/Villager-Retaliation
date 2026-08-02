package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.PaymentBoxBlockEntity;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignmentResult;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public static final String SUPPLY_PURPOSE = "supply";
    public static final String OUTPUT_PURPOSE = "output";
    private static final String LEGACY_INPUT_PURPOSE = "input";
    private static final String LEGACY_TOOL_PURPOSE = "tool";
    public static final String PAYMENT_PURPOSE = "payment";
    private static final double STORAGE_INTERACTION_REACH_SQR = 25.0D;
    private static final long STORAGE_RETRY_COOLDOWN_TICKS = 20L * 15L;
    private static final long STORAGE_FULL_COOLDOWN_TICKS = 20L * 45L;
    private static final Map<StorageFailureKey, StorageFailure> STORAGE_FAILURES = new HashMap<>();

    private AssignedStorageService() {
    }

    public static void clearRuntimeState() {
        STORAGE_FAILURES.clear();
        ContainerTransferClaimLedger.clear();
        ContainerFilterResolver.clearRuntimeState();
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
        int priorityBase = data.assignedTo(villager.getUUID(), normalizedPurpose).stream()
                .mapToInt(AssignedContainerRecord::priority)
                .max()
                .orElse(-1) + 1;
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
            boolean containerAlreadyAssigned = resolvedContainerPositions(targetLevel, position.pos()).stream()
                    .anyMatch(containerPos -> data.assignedAt(
                            position.dimension(),
                            containerPos,
                            villager.getUUID(),
                            normalizedPurpose) != null);
            if (containerAlreadyAssigned) {
                alreadyAssigned++;
                continue;
            }
            List<ItemStack> outputFilters = List.of();
            boolean outputFilterSnapshotKnown = false;
            VillagerInventoryOverflowService.ContainerCandidate candidate =
                    VillagerInventoryOverflowService.ContainerCandidate.resolve(targetLevel, position.pos());
            if (OUTPUT_PURPOSE.equals(normalizedPurpose) && candidate != null) {
                ContainerFilterResolver.Resolution resolution =
                        ContainerFilterResolver.resolve(targetLevel, candidate);
                outputFilters = resolution.rules();
                outputFilterSnapshotKnown = resolution.live();
            }
            AssignmentResult result = data.assign(new AssignedContainerRecord(
                    position.dimension(),
                    position.pos().immutable(),
                    villager.getUUID(),
                    player.getUUID(),
                    normalizedPurpose,
                    priorityBase + assigned,
                    "valid",
                    outputFilters,
                    outputFilterSnapshotKnown
            ));
            if (result == AssignmentResult.ASSIGNED) {
                assigned++;
            } else {
                alreadyAssigned++;
            }
        }
        if (PAYMENT_PURPOSE.equals(normalizedPurpose)
                && (assigned > 0 || alreadyAssigned > 0)
                && HiredVillagerContractService.currentContractHirer(villager)
                        .filter(player.getUUID()::equals)
                        .isPresent()) {
            HiredVillagerContractService.setAutoPaymentEnabled(villager, true);
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

    public static List<AssignedContainerRecord> assignedStorageAt(
            ServerLevel level,
            Villager villager,
            List<StoragePosition> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        return AssignedStorageSavedData.get(level).assignedTo(villager.getUUID()).stream()
                .filter(record -> positions.stream().anyMatch(position -> matchesResolvedContainer(level, record, position)))
                .toList();
    }

    public static int removeAssignedStorageAt(
            ServerLevel level,
            Villager villager,
            List<StoragePosition> positions) {
        int removed = 0;
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        for (AssignedContainerRecord record : assignedStorageAt(level, villager, positions)) {
            if (data.removeAssignment(record)) {
                removed++;
            }
        }
        return removed;
    }
    public static boolean removeAssignedContainer(ServerLevel level, BlockPos pos) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        boolean removed = false;
        for (BlockPos containerPos : resolvedContainerPositions(level, pos)) {
            removed |= data.removeAssignedAt(level.dimension(), containerPos);
        }
        return removed;
    }

    private static boolean matchesResolvedContainer(
            ServerLevel level,
            AssignedContainerRecord record,
            StoragePosition position) {
        if (!position.dimension().equals(record.dimension())) {
            return false;
        }
        ServerLevel targetLevel = level.getServer().getLevel(position.dimension());
        if (targetLevel == null) {
            return position.pos().equals(record.pos());
        }
        return resolvedContainerPositions(targetLevel, position.pos()).contains(record.pos());
    }

    private static List<BlockPos> resolvedContainerPositions(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return pos == null ? List.of() : List.of(pos.immutable());
        }
        VillagerInventoryOverflowService.ContainerCandidate candidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, pos);
        return candidate == null ? List.of(pos.immutable()) : candidate.positions();
    }

    public static boolean isValidContainer(ServerLevel level, BlockPos pos) {
        return level != null
                && pos != null
                && level.hasChunkAt(pos)
                && VillagerInventoryOverflowService.ContainerCandidate.resolve(level, pos) != null;
    }

    public static boolean isValidContainerForPurpose(ServerLevel level, BlockPos pos, String purpose) {
        if (!isValidContainer(level, pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String normalizedPurpose = normalizePurpose(purpose);
        if (PAYMENT_PURPOSE.equals(normalizedPurpose)) {
            return blockEntity instanceof PaymentBoxBlockEntity;
        }
        if (blockEntity instanceof PaymentBoxBlockEntity) {
            return false;
        }
        return !(blockEntity instanceof SellBoxBlockEntity)
                || SUPPLY_PURPOSE.equals(normalizedPurpose)
                || OUTPUT_PURPOSE.equals(normalizedPurpose);
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
        ItemStack remainder = insertIntoOutputContainers(level, villager, containers, stack, usedContainers);
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
        ItemStack remainder = insertIntoOutputContainers(level, villager, containers, stack, usedContainers);
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return remainder;
    }

    public static ItemStack depositStackAtAssignedStorage(Villager villager, BlockPos storagePos, ItemStack stack) {
        if (storagePos == null || stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : liveOutputContainerCandidates(level, villager)) {
            if (!candidate.matches(storagePos) || !candidate.isInInteractionRange(villager)) {
                continue;
            }
            List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
            ItemStack remainder = insertIntoOutputContainers(level, villager, List.of(candidate), stack, usedContainers);
            VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
            return remainder;
        }
        return stack;
    }

    private static ItemStack insertIntoOutputContainers(
            ServerLevel level,
            Villager villager,
            List<VillagerInventoryOverflowService.ContainerCandidate> containers,
            ItemStack stack,
            List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers) {
        ItemStack remainder = stack.copy();
        for (VillagerInventoryOverflowService.ContainerCandidate candidate : containers) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            OutputTarget target = durableOutputTarget(level, villager, candidate);
            if (!target.filterSnapshotKnown()) {
                continue;
            }
            int allowance = outputAllowance(
                    level, candidate, target.filters(), remainder, villager.getUUID(), List.of());
            if (allowance <= 0) {
                continue;
            }
            int offeredCount = Math.min(remainder.getCount(), allowance);
            ItemStack offered = remainder.copyWithCount(offeredCount);
            ItemStack uninserted = candidate.container() instanceof SellBoxBlockEntity sellBox
                    ? sellBox.insertForSale(offered, false)
                    : VillagerInventoryOverflowService.insertIntoContainer(candidate.container(), offered);
            int moved = offeredCount - uninserted.getCount();
            if (moved <= 0) {
                continue;
            }
            remainder.shrink(moved);
            if (usedContainers.stream().noneMatch(used -> used.matches(candidate.pos()))) {
                usedContainers.add(candidate);
            }
        }
        return remainder;
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
    public static void rememberInputStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos, String reason) {
        rememberStorageFailure(level, villager, storagePos, StorageUse.INPUT, reason, STORAGE_RETRY_COOLDOWN_TICKS);
    }

    public static void rememberToolStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos, String reason) {
        rememberStorageFailure(level, villager, storagePos, StorageUse.TOOL, reason, STORAGE_RETRY_COOLDOWN_TICKS);
    }

    public static void clearStorageFailure(ServerLevel level, Villager villager, BlockPos storagePos) {
        if (level == null || villager == null || storagePos == null) {
            return;
        }
        UUID villagerId = villager.getUUID();
        List<BlockPos> containerPositions = resolvedContainerPositions(level, storagePos);
        STORAGE_FAILURES.keySet().removeIf(key ->
                key.villagerId().equals(villagerId)
                        && key.dimension().equals(level.dimension())
                        && containerPositions.contains(key.pos()));
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

    /**
     * Selects a courier destination for its current cargo. Framed outputs that match any cargo
     * item take priority over unframed outputs, allowing item frames to act as destination filters.
     * A framed villager item filter applies its configured allowlist or denylist instead.
     */
    public static BlockPos nearestAssignedCourierOutputStoragePos(
            ServerLevel level,
            Villager villager,
            List<ItemStack> cargo,
            Predicate<BlockPos> positionFilter) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        List<ItemStack> nonEmptyCargo = cargo == null
                ? List.of()
                : cargo.stream().filter(stack -> stack != null && !stack.isEmpty()).toList();
        BlockPos villagerPos = villager.blockPosition();
        return outputCapacityPlan(level, villager).targets().stream()
                .filter(OutputTarget::filterSnapshotKnown)
                .filter(target -> target.candidate().anyPositionMatches(safeFilter))
                .filter(target -> !isStorageRecentlyFailed(level, villager, target.candidate(), StorageUse.OUTPUT))
                .filter(target -> nonEmptyCargo.stream().anyMatch(stack ->
                        outputAllowance(
                                level,
                                target.candidate(),
                                target.filters(),
                                stack,
                                villager.getUUID(),
                                List.of()) > 0))
                .min((first, second) -> {
                    int framedComparison = Boolean.compare(
                            !second.filters().isEmpty(),
                            !first.filters().isEmpty());
                    return framedComparison != 0
                            ? framedComparison
                            : Double.compare(first.candidate().distanceToSqr(villagerPos), second.candidate().distanceToSqr(villagerPos));
                })
                .map(target -> target.candidate().nearestPosition(villagerPos, safeFilter))
                .orElse(null);
    }

    public static boolean courierOutputStorageAccepts(
            ServerLevel level,
            Villager villager,
            BlockPos storagePos,
            ItemStack stack) {
        if (level == null || villager == null || storagePos == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (OutputTarget target : outputCapacityPlan(level, villager).targets()) {
            if (target.candidate().matches(storagePos)) {
                return target.filterSnapshotKnown()
                        && outputAllowance(
                                level,
                                target.candidate(),
                                target.filters(),
                                stack,
                                villager.getUUID(),
                                List.of()) > 0;
            }
        }
        return false;
    }

    /**
     * Returns whether at least one live assigned output can accept one item from the supplied
     * stacks. This includes both the container's physical capacity and any framed filter limit.
     */
    public static boolean hasAssignedOutputCapacityFor(Villager villager, List<ItemStack> stacks) {
        return assignedOutputStateFor(villager, stacks) == AssignedOutputState.AVAILABLE;
    }

    /**
     * Returns whether any live assigned output is configured for one of the supplied stacks,
     * regardless of its current amount limit or physical free space.
     */
    public static boolean hasAssignedOutputRouteFor(Villager villager, List<ItemStack> stacks) {
        return assignedOutputStateFor(villager, stacks) != AssignedOutputState.NO_ROUTE;
    }

    public static AssignedOutputState assignedOutputStateFor(Villager villager, List<ItemStack> stacks) {
        if (!(villager.level() instanceof ServerLevel level) || stacks == null || stacks.isEmpty()) {
            return AssignedOutputState.NO_ROUTE;
        }
        List<ItemStack> nonEmpty = stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .toList();
        if (nonEmpty.isEmpty()) {
            return AssignedOutputState.NO_ROUTE;
        }
        OutputCapacityPlan plan = outputCapacityPlan(level, villager);
        boolean hasRoute = false;
        for (ItemStack stack : nonEmpty) {
            if (plan.hasRoute(stack)) {
                hasRoute = true;
                if (plan.capacityFor(stack, 1) > 0) {
                    return AssignedOutputState.AVAILABLE;
                }
            }
        }
        return hasRoute ? AssignedOutputState.BACKPRESSURED : AssignedOutputState.NO_ROUTE;
    }

    public static int assignedOutputCapacityFor(Villager villager, ItemStack stack, int maximum) {
        if (!(villager.level() instanceof ServerLevel level) || stack == null || stack.isEmpty() || maximum <= 0) {
            return 0;
        }
        return outputCapacityPlan(level, villager).capacityFor(stack, maximum);
    }


    public static void releaseCourierClaims(Villager villager) {
        if (villager != null) {
            ContainerTransferClaimLedger.release(villager.getUUID());
        }
    }

    public static void reconcileCourierClaims(Villager villager, List<ItemStack> cargo) {
        if (villager == null) {
            return;
        }
        UUID ownerId = villager.getUUID();
        ContainerTransferClaimLedger.release(
                ownerId, VillagerFilterPolicy.TransferOperation.PROVIDE);
        if (!(villager.level() instanceof ServerLevel level)) {
            ContainerTransferClaimLedger.release(
                    ownerId, VillagerFilterPolicy.TransferOperation.RECEIVE);
            return;
        }
        OutputCapacityPlanner planner = outputCapacityPlan(level, villager).planner(Map.of());
        if (cargo != null) {
            for (ItemStack stack : cargo) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                OutputCapacityProposal proposal = planner.propose(stack, stack.getCount());
                planner.commit(proposal, stack, proposal.total());
            }
        }
        planner.syncClaims();
    }

    public static int reserveCourierTransferClaims(
            Villager villager,
            BlockPos storagePos,
            int maximum) {
        if (villager == null
                || storagePos == null
                || maximum <= 0
                || !(villager.level() instanceof ServerLevel level)) {
            if (villager != null) {
                ContainerTransferClaimLedger.release(
                        villager.getUUID(), VillagerFilterPolicy.TransferOperation.PROVIDE);
            }
            return 0;
        }

        UUID ownerId = villager.getUUID();
        Predicate<ItemStack> mayWithdraw = withdrawalPredicate(villager, ignored -> true);
        for (VillagerInventoryOverflowService.ContainerCandidate input : liveInputContainerCandidates(level, villager)) {
            if (!input.matches(storagePos)) {
                continue;
            }
            ContainerFilterResolver.Resolution resolution = ContainerFilterResolver.resolve(level, input);
            if (!resolution.live()) {
                ContainerTransferClaimLedger.release(
                        ownerId, VillagerFilterPolicy.TransferOperation.PROVIDE);
                return 0;
            }

            Map<BlockPos, List<ItemStack>> inbound = ContainerTransferClaimLedger.snapshot(
                    level, ownerId, VillagerFilterPolicy.TransferOperation.RECEIVE);
            OutputCapacityPlanner outputPlanner = outputCapacityPlan(level, villager).planner(inbound);
            List<ItemStack> outbound = new ArrayList<>();
            int plannedTotal = 0;
            Container container = input.container();
            for (int slot = 0; slot < container.getContainerSize() && plannedTotal < maximum; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !mayWithdraw.test(stack)) {
                    continue;
                }
                int allowance = sourceAllowance(
                        level, input, resolution.rules(), stack, ownerId, outbound);
                int requested = Math.min(
                        Math.min(maximum - plannedTotal, stack.getCount()),
                        allowance);
                if (requested <= 0) {
                    continue;
                }
                OutputCapacityProposal proposal = outputPlanner.propose(stack, requested);
                if (proposal.total() <= 0) {
                    continue;
                }
                outputPlanner.commit(proposal, stack, proposal.total());
                addClaimedStack(outbound, stack, proposal.total());
                plannedTotal += proposal.total();
            }

            ContainerTransferClaimLedger.replaceAll(
                    level,
                    ownerId,
                    VillagerFilterPolicy.TransferOperation.PROVIDE,
                    outbound.isEmpty() ? Map.of() : Map.of(input.pos(), List.copyOf(outbound)));
            outputPlanner.syncClaims();
            return plannedTotal;
        }
        ContainerTransferClaimLedger.release(
                ownerId, VillagerFilterPolicy.TransferOperation.PROVIDE);
        return 0;
    }

    private static void addClaimedStack(List<ItemStack> claims, ItemStack stack, int count) {
        if (claims == null || stack == null || stack.isEmpty() || count <= 0) {
            return;
        }
        for (ItemStack claim : claims) {
            if (ItemStack.isSameItemSameComponents(claim, stack)) {
                claim.grow(count);
                return;
            }
        }
        claims.add(stack.copyWithCount(count));
    }
    /**
     * Preflights a courier pickup so a full downstream buffer applies backpressure before cargo
     * is removed from a supply container.
     */
    public static CourierTransferState courierTransferState(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return CourierTransferState.NO_INPUT;
        }
        boolean hasInput = false;
        boolean hasOutputRoute = false;
        UUID ownerId = villager.getUUID();
        OutputCapacityPlan outputs = outputCapacityPlan(level, villager);
        for (VillagerInventoryOverflowService.ContainerCandidate input : liveInputContainerCandidates(level, villager)) {
            ContainerFilterResolver.Resolution resolution = ContainerFilterResolver.resolve(level, input);
            if (!resolution.live()) {
                continue;
            }
            Container container = input.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !VillagerItemFilterService.mayWithdraw(villager, stack)) {
                    continue;
                }
                ContainerFilterEvaluator.Evaluation source = evaluateContainerRules(
                        level,
                        input,
                        resolution.rules(),
                        stack,
                        VillagerFilterPolicy.TransferOperation.PROVIDE,
                        ownerId,
                        List.of());
                if (!source.valid() || !source.permitted() || source.allowance() <= 0) {
                    continue;
                }
                hasInput = true;
                if (outputs.hasRoute(stack)) {
                    hasOutputRoute = true;
                }
                if (outputs.capacityFor(stack, 1) > 0) {
                    return CourierTransferState.AVAILABLE;
                }
            }
        }
        if (!hasInput) {
            return CourierTransferState.NO_INPUT;
        }
        return hasOutputRoute
                ? CourierTransferState.OUTPUT_BACKPRESSURED
                : CourierTransferState.NO_OUTPUT_ROUTE;
    }

    private static int outputContainerCapacity(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> filters,
            ItemStack stack,
            int maximum) {
        return outputContainerCapacity(level, candidate, filters, stack, maximum, null, List.of());
    }

    private static int outputContainerCapacity(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> filters,
            ItemStack stack,
            int maximum,
            UUID excludedOwner,
            List<ItemStack> additionalReservations) {
        int allowance = Math.min(
                outputAllowance(level, candidate, filters, stack, excludedOwner, additionalReservations),
                Math.max(0, maximum));
        if (allowance <= 0) {
            return 0;
        }
        Container container = candidate.container();
        if (container instanceof SellBoxBlockEntity) {
            return allowance;
        }
        int locallyReserved = countMatchingStacks(additionalReservations,
                reserved -> ItemStack.isSameItemSameComponents(reserved, stack));
        int capacityGoal = (int) Math.min(Integer.MAX_VALUE, (long) allowance + locallyReserved);
        if (container instanceof ItemHandlerContainerAdapter adapter) {
            int physical = adapter.insertionCapacity(stack, capacityGoal);
            return Math.min(allowance, Math.max(0, physical - locallyReserved));
        }
        int capacity = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                if (container.canPlaceItem(slot, stack)) {
                    capacity += Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
                }
            } else if (ItemStack.isSameItemSameComponents(existing, stack)
                    && container.canPlaceItem(slot, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), container.getMaxStackSize())) {
                capacity += Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
            }
            if (capacity >= capacityGoal) {
                break;
            }
        }
        return Math.min(allowance, Math.max(0, capacity - locallyReserved));
    }

    private static int outputAllowance(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> filters,
            ItemStack stack) {
        return outputAllowance(level, candidate, filters, stack, null, List.of());
    }

    private static int outputAllowance(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> filters,
            ItemStack stack,
            UUID excludedOwner,
            List<ItemStack> additionalReservations) {
        if (candidate.container() instanceof SellBoxBlockEntity
                && !VillageSellMarket.canAcceptSale(level, candidate.pos(), stack)) {
            return 0;
        }
        ContainerFilterEvaluator.Evaluation evaluation = evaluateContainerRules(
                level,
                candidate,
                filters,
                stack,
                VillagerFilterPolicy.TransferOperation.RECEIVE,
                excludedOwner,
                additionalReservations);
        if (!evaluation.valid() || !evaluation.permitted()) {
            return 0;
        }
        return Math.min(
                evaluation.allowance(),
                legacyReceiveAllowance(
                        level, candidate, filters, stack, excludedOwner, additionalReservations));
    }

    private static int sourceAllowance(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> rules,
            ItemStack stack,
            UUID excludedOwner,
            List<ItemStack> additionalClaims) {
        ContainerFilterEvaluator.Evaluation evaluation = evaluateContainerRules(
                level,
                candidate,
                rules,
                stack,
                VillagerFilterPolicy.TransferOperation.PROVIDE,
                excludedOwner,
                additionalClaims);
        return evaluation.valid() && evaluation.permitted()
                ? evaluation.allowance()
                : 0;
    }

    private static ContainerFilterEvaluator.Evaluation evaluateContainerRules(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> rules,
            ItemStack stack,
            VillagerFilterPolicy.TransferOperation operation,
            UUID excludedOwner,
            List<ItemStack> additionalClaims) {
        return ContainerFilterEvaluator.evaluate(
                level,
                rules,
                stack,
                operation,
                (rule, policy, ignoredCandidate, ignoredOperation) -> {
                    Predicate<ItemStack> matcher = stored -> {
                        VillagerFilterMatcher.RawMatchResult result =
                                VillagerFilterMatcher.rawMatchResult(level, rule, stored);
                        if (!result.valid()) {
                            throw new IllegalStateException("Malformed framed filter predicate");
                        }
                        return result.matched();
                    };
                    int currentStock = operation == VillagerFilterPolicy.TransferOperation.RECEIVE
                                    && candidate.container() instanceof SellBoxBlockEntity
                            ? 0
                            : countMatchingContainer(candidate.container(), matcher);
                    int claims = ContainerTransferClaimLedger.count(
                            level, candidate, excludedOwner, operation, matcher);
                    claims = saturatingAdd(claims, countMatchingStacks(additionalClaims, matcher));
                    return new ContainerFilterEvaluator.StockState(currentStock, claims);
                });
    }

    private static int legacyReceiveAllowance(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> rules,
            ItemStack stack,
            UUID excludedOwner,
            List<ItemStack> additionalReservations) {
        int allowance = VillagerFilterPolicy.UNLIMITED_ALLOWANCE;
        for (ItemStack rule : rules) {
            if (!VillagerRetaliationItems.isItemFilter(rule)) {
                continue;
            }
            VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(rule);
            if (!policy.valid()
                    || policy.state() != VillagerFilterPolicy.PolicyState.LEGACY
                    || policy.listMode() != VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                    || !policy.direction().permits(VillagerFilterPolicy.TransferOperation.RECEIVE)) {
                continue;
            }
            VillagerFilterMatcher.RawMatchResult match =
                    VillagerFilterMatcher.rawMatchResult(level, rule, stack);
            if (!match.valid()) {
                return 0;
            }
            if (!match.matched()) {
                continue;
            }
            int limit = VillagerItemFilterData.amountLimit(rule, stack);
            if (limit == VillagerItemFilterData.UNLIMITED_AMOUNT) {
                continue;
            }
            Predicate<ItemStack> matcher = stored ->
                    VillagerItemFilterData.countsTowardAmountLimit(level, rule, stack, stored);
            int stored = candidate.container() instanceof SellBoxBlockEntity
                    ? 0
                    : countMatchingContainer(candidate.container(), matcher);
            int reservations = ContainerTransferClaimLedger.count(
                    level,
                    candidate,
                    excludedOwner,
                    VillagerFilterPolicy.TransferOperation.RECEIVE,
                    matcher);
            reservations = saturatingAdd(
                    reservations, countMatchingStacks(additionalReservations, matcher));
            allowance = Math.min(allowance, Math.max(0, limit - stored - reservations));
        }
        return allowance;
    }

    private static int countMatchingContainer(Container container, Predicate<ItemStack> matcher) {
        long count = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stored = container.getItem(slot);
            if (!stored.isEmpty() && matcher.test(stored)) {
                count += stored.getCount();
                if (count >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) count;
    }

    private static int countMatchingStacks(List<ItemStack> stacks, Predicate<ItemStack> matcher) {
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }
        long count = 0L;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && matcher.test(stack)) {
                count += stack.getCount();
                if (count >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) count;
    }

    private static int saturatingAdd(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) first) + Math.max(0L, (long) second));
    }

    private static OutputCapacityPlan outputCapacityPlan(ServerLevel level, Villager villager) {
        List<OutputTarget> targets = liveOutputContainerCandidates(level, villager).stream()
                .map(candidate -> durableOutputTarget(level, villager, candidate))
                .toList();
        return new OutputCapacityPlan(level, villager.getUUID(), targets);
    }

    private static OutputTarget durableOutputTarget(
            ServerLevel level,
            Villager villager,
            VillagerInventoryOverflowService.ContainerCandidate candidate) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        List<AssignedContainerRecord> records = candidate.positions().stream()
                .map(pos -> data.assignedAt(level.dimension(), pos, villager.getUUID(), OUTPUT_PURPOSE))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        ContainerFilterResolver.Resolution resolution = ContainerFilterResolver.resolve(level, candidate);
        if (resolution.live()) {
            for (AssignedContainerRecord record : records) {
                data.updateOutputFilterSnapshot(record, resolution.rules());
            }
            return new OutputTarget(candidate, resolution.rules(), true);
        }
        return records.stream()
                .filter(AssignedContainerRecord::outputFilterSnapshotKnown)
                .findFirst()
                .map(record -> new OutputTarget(candidate, record.outputFilters(), true))
                .orElseGet(() -> new OutputTarget(candidate, List.of(), false));
    }

    private record OutputTarget(
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            List<ItemStack> filters,
            boolean filterSnapshotKnown) {
    }

    private static final class OutputCapacityPlan {
        private final ServerLevel level;
        private final UUID ownerId;
        private final List<OutputTarget> targets;

        private OutputCapacityPlan(ServerLevel level, UUID ownerId, List<OutputTarget> targets) {
            this.level = level;
            this.ownerId = ownerId;
            this.targets = List.copyOf(targets);
        }

        private List<OutputTarget> targets() {
            return this.targets;
        }

        private boolean hasRoute(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            for (OutputTarget target : this.targets) {
                if (!target.filterSnapshotKnown()) {
                    continue;
                }
                ContainerFilterEvaluator.Evaluation evaluation = evaluateContainerRules(
                        this.level,
                        target.candidate(),
                        target.filters(),
                        stack,
                        VillagerFilterPolicy.TransferOperation.RECEIVE,
                        this.ownerId,
                        List.of());
                if (evaluation.valid() && evaluation.permitted()) {
                    return true;
                }
            }
            return false;
        }

        private int capacityFor(ItemStack stack, int maximum) {
            if (stack == null || stack.isEmpty() || maximum <= 0) {
                return 0;
            }
            int capacity = 0;
            for (OutputTarget target : this.targets) {
                if (!target.filterSnapshotKnown()) {
                    continue;
                }
                capacity += outputContainerCapacity(
                        this.level,
                        target.candidate(),
                        target.filters(),
                        stack,
                        maximum - capacity,
                        this.ownerId,
                        List.of());
                if (capacity >= maximum) {
                    return maximum;
                }
            }
            return capacity;
        }

        private OutputCapacityPlanner planner(Map<BlockPos, List<ItemStack>> existingClaims) {
            return new OutputCapacityPlanner(this, existingClaims);
        }
    }

    private static final class OutputCapacityPlanner {
        private final OutputCapacityPlan plan;
        private final Map<BlockPos, List<ItemStack>> reservations = new LinkedHashMap<>();

        private OutputCapacityPlanner(OutputCapacityPlan plan, Map<BlockPos, List<ItemStack>> existingClaims) {
            this.plan = plan;
            if (existingClaims == null) {
                return;
            }
            for (OutputTarget target : plan.targets()) {
                List<ItemStack> claimed = existingClaims.get(target.candidate().pos());
                if (claimed != null && !claimed.isEmpty()) {
                    this.reservations.put(target.candidate().pos(), claimed.stream()
                            .filter(stack -> stack != null && !stack.isEmpty())
                            .map(ItemStack::copy)
                            .toList());
                }
            }
        }

        private OutputCapacityProposal propose(ItemStack stack, int maximum) {
            if (stack == null || stack.isEmpty() || maximum <= 0) {
                return OutputCapacityProposal.EMPTY;
            }
            int remaining = maximum;
            List<OutputAllocation> allocations = new ArrayList<>();
            for (OutputTarget target : this.plan.targets()) {
                if (!target.filterSnapshotKnown() || remaining <= 0) {
                    continue;
                }
                List<ItemStack> localReservations = this.reservations.getOrDefault(
                        target.candidate().pos(), List.of());
                int available = outputContainerCapacity(
                        this.plan.level,
                        target.candidate(),
                        target.filters(),
                        stack,
                        remaining,
                        this.plan.ownerId,
                        localReservations);
                if (available <= 0) {
                    continue;
                }
                allocations.add(new OutputAllocation(target.candidate().pos(), available));
                remaining -= available;
            }
            return new OutputCapacityProposal(List.copyOf(allocations), maximum - remaining);
        }

        private void commit(OutputCapacityProposal proposal, ItemStack stack, int accepted) {
            int remaining = Math.min(Math.max(0, accepted), proposal.total());
            for (OutputAllocation allocation : proposal.allocations()) {
                if (remaining <= 0) {
                    break;
                }
                int count = Math.min(remaining, allocation.count());
                addReservation(allocation.pos(), stack, count);
                remaining -= count;
            }
        }

        private void syncClaims() {
            ContainerTransferClaimLedger.replaceAll(
                    this.plan.level,
                    this.plan.ownerId,
                    VillagerFilterPolicy.TransferOperation.RECEIVE,
                    this.reservations);
        }

        private void addReservation(BlockPos pos, ItemStack stack, int count) {
            if (count <= 0) {
                return;
            }
            List<ItemStack> claimed = new ArrayList<>(this.reservations.getOrDefault(pos, List.of()));
            for (ItemStack existing : claimed) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(count);
                    this.reservations.put(pos, List.copyOf(claimed));
                    return;
                }
            }
            claimed.add(stack.copyWithCount(count));
            this.reservations.put(pos, List.copyOf(claimed));
        }
    }

    private record OutputCapacityProposal(List<OutputAllocation> allocations, int total) {
        private static final OutputCapacityProposal EMPTY = new OutputCapacityProposal(List.of(), 0);
    }

    private record OutputAllocation(BlockPos pos, int count) {
        private OutputAllocation {
            pos = pos.immutable();
            count = Math.max(0, count);
        }
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

    /**
     * Finds cook recipe materials without applying the villager's output-selection filter.
     * Other callers should use {@link #nearestAssignedStoragePosContaining(ServerLevel, Villager, Predicate)}.
     */
    public static BlockPos nearestAssignedStoragePosContainingIgnoringFilter(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return nearestAssignedStoragePosContaining(
                level,
                villager,
                predicate,
                ignored -> true,
                StorageUse.INPUT,
                false);
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

    public static List<BlockPos> assignedCourierInputStoragePositionsContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        if (level == null || villager == null) {
            return List.of();
        }
        Predicate<ItemStack> mayWithdraw = withdrawalPredicate(villager, predicate);
        OutputCapacityPlan outputs = outputCapacityPlan(level, villager);
        UUID ownerId = villager.getUUID();
        BlockPos villagerPos = villager.blockPosition();
        return liveInputContainerCandidates(level, villager).stream()
                .filter(candidate -> !isStorageRecentlyFailed(level, villager, candidate, StorageUse.INPUT))
                .filter(candidate -> courierInputCanTransfer(
                        level, candidate, mayWithdraw, outputs, ownerId))
                .sorted((first, second) -> Double.compare(
                        first.distanceToSqr(villagerPos),
                        second.distanceToSqr(villagerPos)))
                .map(candidate -> candidate.nearestPosition(villagerPos, ignored -> true))
                .toList();
    }

    private static boolean courierInputCanTransfer(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate,
            Predicate<ItemStack> mayWithdraw,
            OutputCapacityPlan outputs,
            UUID ownerId) {
        ContainerFilterResolver.Resolution resolution = ContainerFilterResolver.resolve(level, candidate);
        if (!resolution.live()) {
            return false;
        }
        Container container = candidate.container();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()
                    && mayWithdraw.test(stack)
                    && sourceAllowance(level, candidate, resolution.rules(), stack, ownerId, List.of()) > 0
                    && outputs.capacityFor(stack, 1) > 0) {
                return true;
            }
        }
        return false;
    }

    public static List<BlockPos> assignedStoragePositionsContainingIgnoringFilter(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate) {
        return assignedStoragePositionsContaining(
                level,
                villager,
                predicate,
                ignored -> true,
                StorageUse.INPUT,
                false);
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
        return nearestAssignedStoragePosContaining(level, villager, predicate, positionFilter, use, true);
    }

    private static BlockPos nearestAssignedStoragePosContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use,
            boolean respectItemFilter) {
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate, respectItemFilter);
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
        return assignedStoragePositionsContaining(level, villager, predicate, positionFilter, use, true);
    }

    private static List<BlockPos> assignedStoragePositionsContaining(
            ServerLevel level,
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use,
            boolean respectItemFilter) {
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate, respectItemFilter);
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
                ItemStack removed = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, Math.min(remaining, stack.getCount()));
                if (removed.isEmpty()) {
                    continue;
                }
                remaining -= removed.getCount();
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
                ItemStack removed = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, Math.min(requested, stack.getCount()));
                if (removed.isEmpty()) {
                    continue;
                }
                consumedValue += removed.getCount() * itemValue;
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

    public static int countItemsIgnoringFilter(Villager villager, Predicate<ItemStack> predicate) {
        return countItems(villager, predicate, ignored -> true, StorageUse.INPUT, false);
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
        return countItems(villager, predicate, positionFilter, use, true);
    }

    private static int countItems(
            Villager villager,
            Predicate<ItemStack> predicate,
            Predicate<BlockPos> positionFilter,
            StorageUse use,
            boolean respectItemFilter) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate, respectItemFilter);
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

    public static int transferCourierItemsAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            int maxCount,
            Function<ItemStack, ItemStack> receiver) {
        if (villager == null
                || storagePos == null
                || maxCount <= 0
                || receiver == null
                || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        UUID ownerId = villager.getUUID();
        ContainerTransferClaimLedger.release(
                ownerId, VillagerFilterPolicy.TransferOperation.PROVIDE);
        Map<BlockPos, List<ItemStack>> inboundClaims = ContainerTransferClaimLedger.snapshot(
                level, ownerId, VillagerFilterPolicy.TransferOperation.RECEIVE);
        OutputCapacityPlanner outputPlanner = outputCapacityPlan(level, villager).planner(inboundClaims);
        Predicate<ItemStack> mayWithdraw = withdrawalPredicate(villager, ignored -> true);
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        int movedTotal = 0;
        for (VillagerInventoryOverflowService.ContainerCandidate input : liveInputContainerCandidates(level, villager)) {
            if (!input.matches(storagePos) || !input.isInInteractionRange(villager)) {
                continue;
            }
            ContainerFilterResolver.Resolution resolution = ContainerFilterResolver.resolve(level, input);
            if (!resolution.live()) {
                break;
            }
            Container container = input.container();
            for (int slot = 0; slot < container.getContainerSize() && movedTotal < maxCount; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !mayWithdraw.test(stack)) {
                    continue;
                }
                int sourceCapacity = sourceAllowance(
                        level, input, resolution.rules(), stack, ownerId, List.of());
                int requested = Math.min(
                        Math.min(maxCount - movedTotal, stack.getCount()),
                        sourceCapacity);
                if (requested <= 0) {
                    continue;
                }
                OutputCapacityProposal proposal = outputPlanner.propose(stack, requested);
                requested = proposal.total();
                if (requested <= 0) {
                    continue;
                }

                ItemStack extracted = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, requested);
                if (extracted.isEmpty()) {
                    continue;
                }
                ItemStack remainder;
                try {
                    remainder = receiver.apply(extracted.copy());
                } catch (RuntimeException exception) {
                    VillagerInventoryOverflowService.restoreToContainerOrDrop(
                            villager, container, extracted);
                    continue;
                }
                int moved = acceptedCount(extracted, remainder);
                int unaccepted = extracted.getCount() - moved;
                if (unaccepted > 0) {
                    VillagerInventoryOverflowService.restoreToContainerOrDrop(
                            villager, container, extracted.copyWithCount(unaccepted));
                }
                if (moved <= 0) {
                    continue;
                }
                outputPlanner.commit(proposal, extracted, moved);
                movedTotal += moved;
                if (!usedContainers.contains(input)) {
                    usedContainers.add(input);
                }
            }
            break;
        }
        outputPlanner.syncClaims();
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return movedTotal;
    }

    public static int transferItemsAtAssignedStorageIgnoringFilter(
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
                StorageUse.INPUT,
                false);
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
        return transferItemsAtAssignedStorage(villager, storagePos, predicate, maxCount, receiver, use, true);
    }

    private static int transferItemsAtAssignedStorage(
            Villager villager,
            BlockPos storagePos,
            Predicate<ItemStack> predicate,
            int maxCount,
            Function<ItemStack, ItemStack> receiver,
            StorageUse use,
            boolean respectItemFilter) {
        if (storagePos == null
                || maxCount <= 0
                || receiver == null
                || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = withdrawalPredicate(villager, predicate, respectItemFilter);
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
                ItemStack extracted = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, requested);
                if (extracted.isEmpty()) {
                    continue;
                }
                ItemStack remainder = receiver.apply(extracted.copy());
                int moved = acceptedCount(extracted, remainder);
                int unaccepted = extracted.getCount() - moved;
                if (unaccepted > 0) {
                    VillagerInventoryOverflowService.restoreToContainerOrDrop(
                            villager, container, extracted.copyWithCount(unaccepted));
                }
                if (moved <= 0) {
                    continue;
                }
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
                ItemStack extracted = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, stack.getCount());
                if (extracted.isEmpty()) {
                    return 0;
                }
                ItemStack remainder = receiver.apply(extracted.copy());
                int moved = acceptedCount(extracted, remainder);
                int unaccepted = extracted.getCount() - moved;
                if (unaccepted > 0) {
                    VillagerInventoryOverflowService.restoreToContainerOrDrop(
                            villager, container, extracted.copyWithCount(unaccepted));
                }
                if (moved <= 0) {
                    return 0;
                }
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
                ItemStack removed = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, Math.min(remaining, stack.getCount()));
                if (removed.isEmpty()) {
                    continue;
                }
                remaining -= removed.getCount();
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
                ItemStack removed = VillagerInventoryOverflowService.extractUpTo(
                        villager, container, slot, Math.min(remaining, stack.getCount()));
                if (removed.isEmpty()) {
                    continue;
                }
                remaining -= removed.getCount();
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
        return withdrawalPredicate(villager, workerPredicate, true);
    }

    private static int acceptedCount(ItemStack offered, ItemStack remainder) {
        if (offered.isEmpty() || remainder == null) {
            return 0;
        }
        if (!remainder.isEmpty() && !ItemStack.isSameItemSameComponents(offered, remainder)) {
            return 0;
        }
        return Math.clamp(offered.getCount() - remainder.getCount(), 0, offered.getCount());
    }

    private static Predicate<ItemStack> withdrawalPredicate(
            Villager villager,
            Predicate<ItemStack> workerPredicate,
            boolean respectItemFilter) {
        Predicate<ItemStack> safeWorkerPredicate = workerPredicate == null ? ignored -> true : workerPredicate;
        return stack -> safeWorkerPredicate.test(stack)
                && (!respectItemFilter || VillagerItemFilterService.mayWithdraw(villager, stack));
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
        List<VillagerInventoryOverflowService.ContainerCandidate> candidates =
                liveContainerCandidates(level, villager, record -> purposeMatchesUse(record.purpose(), use));
        if (use != StorageUse.INPUT && use != StorageUse.TOOL) {
            return candidates;
        }
        return candidates.stream()
                .map(AssignedStorageService::inputContainerView)
                .toList();
    }

    private static VillagerInventoryOverflowService.ContainerCandidate inputContainerView(
            VillagerInventoryOverflowService.ContainerCandidate candidate) {
        if (!(candidate.container() instanceof SellBoxBlockEntity sellBox)) {
            return candidate;
        }
        return new VillagerInventoryOverflowService.ContainerCandidate(
                candidate.pos(), new SellBoxCurrencyContainer(sellBox), candidate.positions());
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
        Optional<UUID> contractHirer = HiredVillagerContractService.currentContractHirer(villager);
        for (AssignedContainerRecord record : data.assignedTo(villager.getUUID())) {
            if (recordFilter != null && !recordFilter.test(record)) {
                continue;
            }
            if (contractHirer.isPresent() && !contractHirer.get().equals(record.hirerId())) {
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
            if (!isValidContainerForPurpose(level, record.pos(), record.purpose())) {
                data.removeAssignment(record);
                continue;
            }
            VillagerInventoryOverflowService.ContainerCandidate candidate =
                    VillagerInventoryOverflowService.ContainerCandidate.resolve(level, record.pos());
            if (candidate == null) {
                data.removeAssignment(record);
                continue;
            }
            data.updateValidation(record, "valid");
            containers.putIfAbsent(candidate.pos(), candidate);
        }
        return new ArrayList<>(containers.values());
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
        if (purpose == null || purpose.isBlank()) {
            return GENERAL_PURPOSE;
        }
        String normalized = purpose.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case LEGACY_INPUT_PURPOSE, LEGACY_TOOL_PURPOSE, "supplies" -> SUPPLY_PURPOSE;
            default -> normalized;
        };
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
            case INPUT -> SUPPLY_PURPOSE.equals(normalized);
            case OUTPUT -> OUTPUT_PURPOSE.equals(normalized);
            case TOOL -> SUPPLY_PURPOSE.equals(normalized);
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

    public enum AssignedOutputState {
        NO_ROUTE,
        AVAILABLE,
        BACKPRESSURED
    }

    private record StorageFailureKey(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, StorageUse use) {
    }

    private record StorageFailure(String reason, long expiresGameTime) {
    }

    public enum CourierTransferState {
        AVAILABLE,
        NO_INPUT,
        NO_OUTPUT_ROUTE,
        OUTPUT_BACKPRESSURED
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
