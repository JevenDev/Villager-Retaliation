package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.PaymentBoxChunkLoadingService;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

public final class HiredVillagerContractService {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private static final String CONTRACT_ID_TAG = "ContractId";
    private static final String HIRER_TAG = "Hirer";
    private static final String END_GAME_TIME_TAG = "EndGameTime";
    private static final String DURATION_DAYS_TAG = "DurationDays";
    private static final String DAILY_COST_TAG = "DailyCost";
    private static final String EMERALDS_PAID_TAG = "EmeraldsPaid";
    private static final String AUTO_PAYMENT_TAG = "AutoPayment";
    private static final String MOUNTED_TRAVEL_TAG = "MountedTravel";
    private static final String ONE_OFF_BUILDER_JOB_TAG = "OneOffBuilderJob";
    private static final String LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG = "LastAutoPaymentAttemptGameTime";
    private static final String AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG = "AwaitingAutoPaymentStartGameTime";
    private static final String ROLE_TAG = "Role";
    private static final String STATUS_TAG = "Status";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ENDED = "ended";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_AWAITING_AUTO_PAYMENT = "awaiting_auto_payment";
    private static final long DAY_TICKS = VillagerContractTime.DAY_TICKS;
    private static final long CONTRACT_MAINTENANCE_INTERVAL_TICKS = 20L;
    private static final long AUTO_PAYMENT_RETRY_INTERVAL_TICKS = 100L;

    private HiredVillagerContractService() {
    }

    public static boolean isHired(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return hasActiveOrPendingContract(villager);
    }

    /**
     * Reads the current contract without performing lifecycle maintenance. Callers
     * that require up-to-date expiration state must run maintenance before taking
     * the snapshot.
     */
    public static HireContractSnapshot snapshot(ServerLevel level, Villager villager) {
        Optional<HireContract> storedContract = HireContractStore.load(villager);
        if (storedContract.isEmpty()) {
            return HireContractSnapshot.inactive(HiredVillagerRoles.defaultRole(level, villager));
        }
        HireContract contract = storedContract.get();
        boolean hired = contract.isActiveOrAwaitingAutoPayment();
        if (!hired) {
            return HireContractSnapshot.inactive(HiredVillagerRoles.defaultRole(level, villager));
        }
        HiredVillagerRole role = contract.role();
        if (role == null) {
            role = HiredVillagerRoles.defaultRole(level, villager);
        }
        return new HireContractSnapshot(
                true,
                contract.owner(),
                role,
                contract.isAwaitingAutoPayment(),
                contract.oneOffBuilderJob());
    }

    /** Non-mutating query for policies that run while vanilla AI evaluates behavior. */
    public static boolean hasActiveOrPendingContract(Villager villager) {
        return HireContractStore.load(villager).filter(HireContract::isActiveOrAwaitingAutoPayment).isPresent();
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        HireContractStore.clearInheritedStateForNewborn(child);
        HireOverflowClaimService.clear(child);
    }

    public static boolean isHiredBy(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHirer(level, villager).filter(player.getUUID()::equals).isPresent();
    }

    public static boolean canAccessJobInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        if (player == null || villager.isBaby()) {
            return false;
        }
        boolean activeHirer = HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .flatMap(HireContract::owner)
                .filter(player.getUUID()::equals)
                .isPresent();
        if (activeHirer) {
            return true;
        }
        return activeOverflowClaim(level, villager)
                .map(HireOverflowClaimService.Claim::ownerId)
                .filter(player.getUUID()::equals)
                .isPresent();
    }

    public static Optional<UUID> currentContractId(Villager villager) {
        return HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .map(HireContract::id);
    }

    public static Optional<UUID> currentContractHirer(Villager villager) {
        return HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .flatMap(HireContract::owner);
    }

    public static Optional<UUID> transferOwnership(ServerLevel level, Villager villager, UUID newOwnerId) {
        if (level == null || villager == null || newOwnerId == null) {
            return Optional.empty();
        }
        HireContract contract = HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .orElse(null);
        UUID currentOwnerId = contract == null ? null : contract.owner().orElse(null);
        if (currentOwnerId == null) {
            return Optional.empty();
        }
        if (!currentOwnerId.equals(newOwnerId)) {
            contract.owner(newOwnerId);
            HireContractStore.save(villager, contract);
            VillagerAssignmentStore.transferOwner(villager, currentOwnerId, newOwnerId);
            HireContractAssignmentAdapter.synchronize(level, villager);
            HiredWorkSession.invalidate(villager);
            HiredVillagerIndex.update(level, villager);
        }
        return Optional.of(currentOwnerId);
    }

    public static boolean hasBlockingJobInventoryOverflow(ServerLevel level, Villager villager) {
        return activeOverflowClaim(level, villager).isPresent();
    }

    public static boolean hasForeignJobInventoryOverflow(ServerLevel level, Villager villager, ServerPlayer player) {
        return activeOverflowClaim(level, villager)
                .filter(claim -> player == null || !claim.ownerId().equals(player.getUUID()))
                .isPresent();
    }

    public static void takeOverJobInventoryOverflow(Villager villager) {
        if (villager != null) {
            clearOverflowClaim(villager);
        }
    }

    /**
     * Preserves removable job-inventory items for their contract owner after any
     * contract system releases the villager.
     */
    public static void rememberJobInventoryOverflowClaim(
            ServerLevel level,
            Villager villager,
            UUID contractId,
            UUID ownerId) {
        HireOverflowClaimService.remember(level, villager, contractId, ownerId);
    }

    public static boolean tryOpenJobInventoryOverflowReminder(ServerLevel level, Villager villager, ServerPlayer player) {
        Optional<HireOverflowClaimService.Claim> claim = activeOverflowClaim(level, villager)
                .filter(tag -> tag.ownerId().equals(player.getUUID()));
        if (claim.isEmpty()) {
            return false;
        }
        HireOverflowClaimService.Claim tag = claim.get();
        long day = level.getDayTime() / DAY_TICKS;
        if (tag.lastReminderDay() == day) {
            return false;
        }
        Map<String, String> replacements = overflowReplacements(level, villager, tag);
        String line = VillagerDialogueResources
                .message(
                        VillagerInteractionService.createDialogueContext(level, player, villager),
                        "interaction.hire_overflow_claim_reminder",
                        replacements)
                .orElse("");
        boolean expiredPartyContract = com.jvn.villagerretaliation.party.PartyVillagerContractService
                .isRetainedPartyInventory(level, villager, player);
        boolean opened = expiredPartyContract
                ? ForcedDialogueService.openExpiredPartyContractDialogue(
                        player,
                        villager,
                        line,
                        com.jvn.villagerretaliation.party.PartyVillagerContractService
                                .canRenewExpiredContract(level, villager, player))
                : ForcedDialogueService.openSimpleForcedDialogue(
                        player,
                        villager,
                        "villagerretaliation:hired_job_inventory_overflow_claim",
                        line);
        if (opened) {
            HireOverflowClaimService.markReminded(villager, tag, day);
            return true;
        }
        return false;
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !villager.isAlive()
                || !hasContract(villager)
                || !TickThrottle.isSpreadTick(
                        villager.getUUID(), level.getGameTime(), CONTRACT_MAINTENANCE_INTERVAL_TICKS)) {
            return;
        }
        maybeAutoRenew(level, villager);
    }

    public static boolean hasContract(Villager villager) {
        return HireContractStore.exists(villager);
    }

    public static Optional<UUID> getHirer(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return currentContractHirer(villager);
    }

    public static int getRemainingHireDays(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .map(tag -> remainingDays(level, tag))
                .orElse(0);
    }

    public static OptionalLong getHireEndGameTime(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> activeContract = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag));
        return activeContract.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(activeContract.get().getLong(END_GAME_TIME_TAG));
    }

    public static int getHireCost(ServerLevel level, Villager villager, ServerPlayer player, int days) {
        return getHireCost(level, villager, player, days, null);
    }

    public static int getHireCost(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int days,
            HiredVillagerRole role) {
        return getDailyCost(level, villager, player, role) * clampedContractDays(days);
    }

    public static int getDailyCost(ServerLevel level, Villager villager, ServerPlayer player) {
        return getDailyCost(level, villager, player, null);
    }

    public static int getDailyCost(ServerLevel level, Villager villager, ServerPlayer player, HiredVillagerRole role) {
        // Contracts permit free role changes, so price every role against the villager's best available work.
        int skillScore = HiredVillagerRoles.bestRoleScore(level, villager);
        int skillPremium = Math.max(0, (skillScore - HiredVillagerRoles.STANDARD_APTITUDE) / 10)
                * Math.max(0, VillagerRetaliationConfig.HIRED_CONTRACT_SKILL_PREMIUM_PER_TEN.get());
        int reputationModifier = reputationCostModifier(level, villager, player);
        int minDailyCost = Math.max(1, VillagerRetaliationConfig.HIRED_CONTRACT_MINIMUM_DAILY_COST.get());
        int maxDailyCost = Math.max(minDailyCost, VillagerRetaliationConfig.HIRED_CONTRACT_MAXIMUM_DAILY_COST.get());
        int dailyCost = Mth.clamp(
                VillagerRetaliationConfig.HIRED_CONTRACT_BASE_DAILY_COST.get() + skillPremium + reputationModifier,
                minDailyCost,
                maxDailyCost
        );
        return dailyCost;
    }

    public static int getAvailableExtensionDays(ServerLevel level, Villager villager, ServerPlayer player, int requestedDays) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()))
                .map(tag -> effectiveExtensionDays(level, tag, requestedDays))
                .orElse(0);
    }

    public static int getExtensionCost(ServerLevel level, Villager villager, ServerPlayer player, int requestedDays) {
        int extensionDays = getAvailableExtensionDays(level, villager, player, requestedDays);
        if (extensionDays <= 0) {
            return 0;
        }
        return getDailyCost(level, villager, player) * extensionDays;
    }

    public static int getContractDailyCost(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .map(tag -> currentRenewalDailyCost(level, villager, tag))
                .orElseGet(() -> getDailyCost(level, villager, player));
    }

    public static boolean isAutoPaymentEnabled(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .map(tag -> tag.getBoolean(AUTO_PAYMENT_TAG))
                .orElse(false);
    }

    public static boolean isAwaitingAutoPayment(ServerLevel level, Villager villager) {
        return contract(villager)
                .filter(HiredVillagerContractService::isAwaitingAutoPayment)
                .isPresent();
    }

    public static boolean toggleAutoPayment(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> activeContract = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag));
        if (activeContract.isEmpty()) {
            return false;
        }
        CompoundTag tag = activeContract.get();
        boolean enabled = !tag.getBoolean(AUTO_PAYMENT_TAG);
        tag.putBoolean(AUTO_PAYMENT_TAG, enabled);
        villager.setPersistenceRequired();
        return enabled;
    }

    public static void setAutoPaymentEnabled(Villager villager, boolean enabled) {
        contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .ifPresent(tag -> {
                    tag.putBoolean(AUTO_PAYMENT_TAG, enabled);
                    villager.setPersistenceRequired();
                });
    }

    public static boolean isMountedTravelEnabled(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .map(tag -> !tag.contains(MOUNTED_TRAVEL_TAG) || tag.getBoolean(MOUNTED_TRAVEL_TAG))
                .orElse(false);
    }

    public static boolean toggleMountedTravel(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> activeContract = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> !isOneOffBuilderJob(tag));
        if (activeContract.isEmpty()) {
            return false;
        }
        CompoundTag tag = activeContract.get();
        boolean enabled = !isMountedTravelEnabled(level, villager);
        tag.putBoolean(MOUNTED_TRAVEL_TAG, enabled);
        villager.setPersistenceRequired();
        return enabled;
    }

    public static void onVillagerDeath(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .ifPresent(tag -> {
                    finishContract(level, villager, tag, STATUS_ENDED, "Work stopped. Villager died.", false);
                });
    }

    public static boolean startHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        return startHireContract(level, villager, player, days, emeraldsPaid, HiredVillagerRoles.defaultRole(level, villager));
    }

    public static boolean startHireContract(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int days,
            int emeraldsPaid,
            HiredVillagerRole role) {
        if (level == null || villager == null || villager.isBaby() || player == null) {
            return false;
        }
        VillagerAssignmentSnapshot assignment = synchronizeAssignment(level, villager);
        if (currentContractHirer(villager).isPresent()
                || hasActiveOrPendingContract(villager)
                || assignment.state() == VillagerAssignmentState.HIRED) {
            return false;
        }
        int safeDays = clampedContractDays(days);
        HiredVillagerRole safeRole = role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
        if (safeRole == null || !HiredVillagerRoles.availableContractRoles(level, villager).contains(safeRole)) {
            return false;
        }
        long startGameTime = level.getGameTime();
        HireContract contract = HireContract.regular(
                player.getUUID(), startGameTime, safeDays,
                Math.max(1, emeraldsPaid / safeDays), emeraldsPaid, safeRole);
        HireContractLifecycle.begin(level, villager, contract);
        return true;
    }

    public static void startOneOffBuilderJob(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || villager.isBaby() || player == null
                || !HiredVillagerRoles.canOfferBuilderService(level, villager)) {
            return;
        }
        VillagerAssignmentSnapshot assignment = synchronizeAssignment(level, villager);
        if (currentContractHirer(villager).isPresent()
                || hasActiveOrPendingContract(villager)
                || assignment.state() == VillagerAssignmentState.HIRED) {
            return;
        }
        long startGameTime = level.getGameTime();
        HireContractLifecycle.begin(level, villager, HireContract.oneOffBuilder(player.getUUID(), startGameTime));
    }

    public static boolean isOneOffBuilderJob(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(HiredVillagerContractService::isOneOffBuilderJob)
                .isPresent();
    }

    public static void finishOneOffBuilderJob(ServerLevel level, Villager villager, String workStatus) {
        contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(HiredVillagerContractService::isOneOffBuilderJob)
                .ifPresent(tag -> finishContract(level, villager, tag, STATUS_ENDED, workStatus, true));
    }

    public static boolean extendHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> contract = contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()));
        if (contract.isEmpty()) {
            return false;
        }
        int safeDays = effectiveExtensionDays(level, contract.get(), days);
        if (safeDays <= 0) {
            return false;
        }
        CompoundTag tag = contract.get();
        HirePaymentEscrow.releaseEarned(level, villager);
        long currentEnd = Math.max(level.getGameTime(), tag.getLong(END_GAME_TIME_TAG));
        tag.putLong(END_GAME_TIME_TAG, currentEnd + safeDays * DAY_TICKS);
        tag.putInt(DURATION_DAYS_TAG, tag.getInt(DURATION_DAYS_TAG) + safeDays);
        tag.putInt(EMERALDS_PAID_TAG, tag.getInt(EMERALDS_PAID_TAG) + emeraldsPaid);
        tag.putInt(DAILY_COST_TAG, Math.max(1, emeraldsPaid / safeDays));
        villager.setPersistenceRequired();
        return true;
    }

    public static int endHireContract(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> activeContract = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()));
        if (activeContract.isEmpty()) {
            return 0;
        }
        CompoundTag tag = activeContract.get();
        int refund = HirePaymentEscrow.earlyEndRefund(level, villager);
        finishContract(level, villager, tag, STATUS_ENDED, "Work stopped. Contract ended.", true, refund);
        return refund;
    }

    public static void expireHireContractIfNeeded(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> !isOneOffBuilderJob(tag))
                .filter(tag -> level.getGameTime() >= tag.getLong(END_GAME_TIME_TAG))
                .ifPresent(tag -> {
                    if (canAttemptAutoPaymentRenewal(level, villager, tag)) {
                        AutoPaymentResult result = tryAutoPaymentRenewal(level, villager, tag);
                        if (result == AutoPaymentResult.UNAVAILABLE) {
                            beginAwaitingAutoPayment(level, villager, tag);
                        } else if (result == AutoPaymentResult.INSUFFICIENT_FUNDS) {
                            beginAwaitingAutoPayment(level, villager, tag);
                        }
                    } else {
                        expireContract(level, villager, tag, "Work stopped. Contract expired.");
                    }
                });
    }

    /**
     * Reconciles the canonical command assignment with the authoritative hire
     * contract. This also releases assignment-only state left behind by an older
     * build so a new contract cannot be reported as successful without actually
     * being created.
     */
    public static VillagerAssignmentSnapshot synchronizeAssignment(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return VillagerAssignmentSnapshot.unassigned(0L);
        }
        expireHireContractIfNeeded(level, villager);
        return HireContractAssignmentAdapter.synchronize(level, villager);
    }

    public static HiredVillagerRole activeRole(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        HiredVillagerRole role = HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .map(HireContract::role)
                .orElse(null);
        return role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
    }

    /** Non-mutating role lookup for AI policy evaluation. */
    public static HiredVillagerRole activeRoleWithoutMaintenance(ServerLevel level, Villager villager) {
        HiredVillagerRole role = HireContractStore.load(villager)
                .filter(HireContract::isActiveOrAwaitingAutoPayment)
                .map(HireContract::role)
                .orElse(null);
        return role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
    }

    public static boolean setActiveRole(ServerLevel level, Villager villager, HiredVillagerRole role) {
        expireHireContractIfNeeded(level, villager);
        if (role == null || !HiredVillagerRoles.availableContractRoles(level, villager).contains(role)) {
            return false;
        }
        Optional<CompoundTag> contract = contract(villager).filter(HiredVillagerContractService::isActive);
        if (contract.isEmpty()) {
            return false;
        }
        CompoundTag tag = contract.get();
        HiredVillagerRole currentRole = roleFromContract(level, villager, tag);
        if (currentRole != role) {
            if (currentRole == HiredVillagerRole.BUILDER
                    && BuilderTaskState.hasTask(HiredWorkStateStore.state(villager))) {
                return false;
            }
            clearContractScopedOrders(level, villager, currentRole);
            HiredWorkStateStore.cancelWork(level, villager, currentRole, "Work stopped. Role changed.", Map.of());
            HiredWorkStateStore.resetReportProgress(villager);
        }
        tag.putString(ROLE_TAG, role.serializedName());
        HiredWorkSession.invalidate(villager);
        HireContractAssignmentAdapter.roleChanged(villager, role);
        villager.setPersistenceRequired();
        return true;
    }

    private static HiredVillagerRole roleFromContract(ServerLevel level, Villager villager, CompoundTag tag) {
        HiredVillagerRole role = HiredVillagerRole.bySerializedName(tag.getString(ROLE_TAG));
        return role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
    }

    private static int remainingDays(ServerLevel level, CompoundTag contract) {
        return VillagerContractTime.remainingDays(level.getGameTime(), contract.getLong(END_GAME_TIME_TAG));
    }

    private static void maybeAutoRenew(ServerLevel level, Villager villager) {
        Optional<CompoundTag> storedContract = contract(villager);
        if (storedContract.isEmpty()) {
            return;
        }

        CompoundTag tag = storedContract.get();
        if (isOneOffBuilderJob(tag)) {
            return;
        }
        HirePaymentEscrow.releaseEarned(level, villager);
        if (isAwaitingAutoPayment(tag)) {
            handleAwaitingAutoPayment(level, villager, tag);
            return;
        }

        if (!isActive(tag)) {
            return;
        }
        if (!tag.getBoolean(AUTO_PAYMENT_TAG)) {
            return;
        }
        if (!hasOnlineHirerForRenewal(level, villager, tag)) {
            return;
        }
        long gameTime = level.getGameTime();
        long remainingTicks = tag.getLong(END_GAME_TIME_TAG) - gameTime;
        if (remainingTicks > 0L) {
            return;
        }
        AutoPaymentResult result = tryAutoPaymentRenewal(level, villager, tag);
        if (result == AutoPaymentResult.UNAVAILABLE) {
            beginAwaitingAutoPayment(level, villager, tag);
        } else if (result == AutoPaymentResult.INSUFFICIENT_FUNDS) {
            beginAwaitingAutoPayment(level, villager, tag);
        }
    }

    private static boolean canAttemptAutoPaymentRenewal(ServerLevel level, Villager villager, CompoundTag tag) {
        return tag.getBoolean(AUTO_PAYMENT_TAG)
                && hasOnlineHirerForRenewal(level, villager, tag)
                && AssignedStorageService.hasAssignedPaymentStorage(level, villager);
    }

    private static boolean hasOnlineHirerForRenewal(ServerLevel level, Villager villager, CompoundTag tag) {
        if (!tag.hasUUID(HIRER_TAG)) {
            return true;
        }
        return level.getServer().getPlayerList().getPlayer(tag.getUUID(HIRER_TAG)) != null;
    }

    private static void beginAwaitingAutoPayment(ServerLevel level, Villager villager, CompoundTag tag) {
        if (isAwaitingAutoPayment(tag)) {
            return;
        }
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        HiredWorkStateStore.pauseWork(level, villager, role, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.", Map.of());
        setWorkStatus(villager, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
        tag.putString(STATUS_TAG, STATUS_AWAITING_AUTO_PAYMENT);
        tag.putLong(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG, level.getGameTime());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        villager.setPersistenceRequired();
    }

    private static void handleAwaitingAutoPayment(ServerLevel level, Villager villager, CompoundTag tag) {
        boolean gracePeriodExpired = isAwaitingAutoPaymentExpired(level, tag);
        long gameTime = level.getGameTime();
        long lastAttempt = tag.getLong(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG);
        if (!gracePeriodExpired
                && tag.contains(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG, Tag.TAG_LONG)
                && lastAttempt <= gameTime
                && gameTime - lastAttempt < AUTO_PAYMENT_RETRY_INTERVAL_TICKS) {
            return;
        }
        if (!canAttemptAutoPaymentRenewal(level, villager, tag)) {
            if (gracePeriodExpired) {
                expireContract(level, villager, tag, "Work stopped. Recurring payment was unpaid for more than a day.");
                return;
            }
            HiredWorkerBrain.setState(HiredWorkStateStore.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setWorkStatus(villager, "Contract paused. Recurring payment is unpaid.");
            return;
        }

        AutoPaymentResult result = tryAutoPaymentRenewal(level, villager, tag);
        if (result == AutoPaymentResult.SUCCESS) {
            return;
        }
        if (gracePeriodExpired) {
            expireContract(level, villager, tag, "Work stopped. Recurring payment was unpaid for more than a day.");
            return;
        }
        if (result == AutoPaymentResult.INSUFFICIENT_FUNDS) {
            HiredWorkerBrain.setState(HiredWorkStateStore.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setWorkStatus(villager, "Contract paused. Assigned payment box has no renewal payment.");
            return;
        }

        HiredWorkerBrain.setState(HiredWorkStateStore.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        setWorkStatus(villager, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
    }

    private static AutoPaymentResult tryAutoPaymentRenewal(ServerLevel level, Villager villager, CompoundTag tag) {
        tag.putLong(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG, level.getGameTime());
        int dailyCost = currentRenewalDailyCost(level, villager, tag);
        if (!AssignedStorageService.hasLoadedAssignedPaymentStorage(level, villager)) {
            PaymentBoxChunkLoadingService.requestLoads(level, villager);
            return AutoPaymentResult.UNAVAILABLE;
        }
        int availablePayment = AssignedStorageService.countPaymentItems(
                villager,
                stack -> VillagerCurrencyResources.isCurrency(level.getServer(), stack));
        if (availablePayment < dailyCost
                && PaymentBoxChunkLoadingService.requestLoads(level, villager) > 0) {
            return AutoPaymentResult.UNAVAILABLE;
        }
        if (availablePayment < dailyCost) {
            return AutoPaymentResult.INSUFFICIENT_FUNDS;
        }
        int consumed = AssignedStorageService.consumePaymentItems(
                villager,
                stack -> VillagerCurrencyResources.isCurrency(level.getServer(), stack),
                dailyCost);
        if (consumed < dailyCost) {
            return AutoPaymentResult.INSUFFICIENT_FUNDS;
        }
        PaymentBoxChunkLoadingService.releaseLoads(level, villager);
        currentContractHirer(villager).ifPresent(hirerId ->
                VillagerReputationAdvancements.onWagesPaid(level, hirerId, dailyCost));
        HirePaymentEscrow.releaseEarned(level, villager);
        extendActiveContract(level, tag, 1, dailyCost);
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        tag.remove(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG);
        HiredWorkerBrain.setState(HiredWorkStateStore.state(villager), HiredWorkerTaskState.IDLE, null);
        setWorkStatus(villager, "Contract renewed from assigned payment box.");
        villager.setPersistenceRequired();
        return AutoPaymentResult.SUCCESS;
    }

    private static void expireContract(ServerLevel level, Villager villager, CompoundTag tag, String status) {
        finishContract(level, villager, tag, STATUS_EXPIRED, status, true);
    }

    private static boolean isAwaitingAutoPaymentExpired(ServerLevel level, CompoundTag tag) {
        long startGameTime = tag.getLong(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG);
        if (startGameTime <= 0L) {
            startGameTime = tag.getLong(END_GAME_TIME_TAG);
            tag.putLong(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG, startGameTime);
        }
        return level.getGameTime() - startGameTime >= DAY_TICKS;
    }

    private static void finishContract(
            ServerLevel level,
            Villager villager,
            CompoundTag tag,
            String contractStatus,
            String workStatus,
            boolean depositJobInventory) {
        finishContract(level, villager, tag, contractStatus, workStatus, depositJobInventory, 0);
    }

    private static void finishContract(
            ServerLevel level,
            Villager villager,
            CompoundTag tag,
            String contractStatus,
            String workStatus,
            boolean depositJobInventory,
            int refund) {
        HirePaymentEscrow.settle(level, villager, refund);
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        UUID contractId = ensureContractId(tag);
        finalizeBuilderJobForContractEnd(level, villager, tag, role);
        HiredWorkStateStore.finishWork(level, villager, role, workStatus, Map.of());
        clearContractScopedOrders(level, villager, role);
        tag.putString(STATUS_TAG, contractStatus);
        HireContractLifecycle.unlockProfession(villager);
        if (depositJobInventory) {
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            inventory.markRemovableItemsForContract(contractId);
            inventory.depositRemovableItemsToAssignedStorage();
            rememberOverflowClaimIfNeeded(level, villager, tag, contractId);
        }
        PaymentBoxChunkLoadingService.releaseLoads(level, villager);
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        com.jvn.villagerretaliation.mount.VillagerMountAssignmentService
                .clearAssignment(level, villager.getUUID());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        VillagerRecruitmentService.stopFollowing(villager);
        HireContractAssignmentAdapter.contractEnded(villager);
        HiredWorkSession.invalidate(villager);
        villager.setPersistenceRequired();
        HiredVillagerIndex.remove(villager);
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.syncNameToTracking(villager);
        VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
    }

    private static void finalizeBuilderJobForContractEnd(
            ServerLevel level,
            Villager villager,
            CompoundTag contract,
            HiredVillagerRole role) {
        if (role != HiredVillagerRole.BUILDER) {
            return;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        if (!BuilderTaskState.hasTask(state)) {
            return;
        }
        Optional<UUID> jobId = BuilderTaskState.jobId(state);
        int paid = BuilderTaskState.paidCurrency(state);
        int placed = BuilderTaskState.placedIndex(state);
        if (placed == 0 && paid > 0) {
            refundUnstartedBuilderJob(level, villager, contract, jobId, paid);
        } else {
            BuilderPaymentEscrowService.releaseToWallet(villager, jobId);
        }
        jobId.ifPresent(id -> ConstructionBlueprintItem.expireMatchingBlueprints(level, id));
    }

    private static void refundUnstartedBuilderJob(
            ServerLevel level,
            Villager villager,
            CompoundTag contract,
            Optional<UUID> jobId,
            int paid) {
        if (paid <= 0 || !contract.hasUUID(HIRER_TAG)) {
            return;
        }
        ServerPlayer hirer = level.getServer().getPlayerList().getPlayer(contract.getUUID(HIRER_TAG));
        BuilderPaymentEscrowService.refundOrDrop(level, hirer, villager, jobId, paid);
    }

    private static void setWorkStatus(Villager villager, String status) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        String safeStatus = status == null ? "" : status;
        if (!safeStatus.equals(state.getString("Status"))) {
            state.putString("Status", safeStatus);
        }
    }

    private static void clearContractScopedOrders(ServerLevel level, Villager villager, HiredVillagerRole role) {
        if (role == HiredVillagerRole.BREWING) {
            BrewingWorker.clearOrder(HiredWorkStateStore.state(villager));
        }
    }

    private static void extendActiveContract(ServerLevel level, CompoundTag tag, int days, int emeraldsPaid) {
        int safeDays = effectiveExtensionDays(level, tag, days);
        if (safeDays <= 0) {
            return;
        }
        long currentEnd = Math.max(level.getGameTime(), tag.getLong(END_GAME_TIME_TAG));
        tag.putLong(END_GAME_TIME_TAG, currentEnd + safeDays * DAY_TICKS);
        tag.putInt(DURATION_DAYS_TAG, tag.getInt(DURATION_DAYS_TAG) + safeDays);
        tag.putInt(EMERALDS_PAID_TAG, tag.getInt(EMERALDS_PAID_TAG) + emeraldsPaid);
        tag.putInt(DAILY_COST_TAG, Math.max(1, emeraldsPaid / safeDays));
    }

    private static int clampedContractDays(int days) {
        return VillagerContractTime.clampedPurchaseDays(days);
    }

    private static int effectiveExtensionDays(ServerLevel level, CompoundTag tag, int requestedDays) {
        return VillagerContractTime.availableExtensionDays(
                level.getGameTime(),
                tag.getLong(END_GAME_TIME_TAG),
                requestedDays);
    }

    private static int currentRenewalDailyCost(ServerLevel level, Villager villager, CompoundTag tag) {
        ServerPlayer hirer = tag.hasUUID(HIRER_TAG)
                ? level.getServer().getPlayerList().getPlayer(tag.getUUID(HIRER_TAG))
                : null;
        if (hirer != null) {
            return Math.max(1, getDailyCost(level, villager, hirer));
        }
        return Math.max(1, tag.getInt(DAILY_COST_TAG));
    }

    private static boolean isActive(CompoundTag tag) {
        return STATUS_ACTIVE.equals(tag.getString(STATUS_TAG));
    }

    private static boolean isActiveOrAwaitingAutoPayment(CompoundTag tag) {
        return isActive(tag) || isAwaitingAutoPayment(tag);
    }

    private static boolean isAwaitingAutoPayment(CompoundTag tag) {
        return STATUS_AWAITING_AUTO_PAYMENT.equals(tag.getString(STATUS_TAG));
    }

    private static boolean isOneOffBuilderJob(CompoundTag tag) {
        return tag != null && tag.getBoolean(ONE_OFF_BUILDER_JOB_TAG);
    }

    private static UUID ensureContractId(CompoundTag tag) {
        if (!tag.hasUUID(CONTRACT_ID_TAG)) {
            tag.putUUID(CONTRACT_ID_TAG, UUID.randomUUID());
        }
        return tag.getUUID(CONTRACT_ID_TAG);
    }

    private static void rememberOverflowClaimIfNeeded(ServerLevel level, Villager villager, CompoundTag contract, UUID contractId) {
        if (contractId == null || !contract.hasUUID(HIRER_TAG)) {
            clearOverflowClaim(villager);
            return;
        }
        rememberJobInventoryOverflowClaim(level, villager, contractId, contract.getUUID(HIRER_TAG));
    }

    private static Optional<HireOverflowClaimService.Claim> activeOverflowClaim(ServerLevel level, Villager villager) {
        return HireOverflowClaimService.active(level, villager);
    }

    private static void clearOverflowClaim(Villager villager) {
        HireOverflowClaimService.clear(villager);
    }

    private static Map<String, String> overflowReplacements(
            ServerLevel level, Villager villager, HireOverflowClaimService.Claim claim) {
        int days = HireOverflowClaimService.remainingDays(level, claim);
        int items = HireOverflowClaimService.itemCount(villager, claim);
        return Map.of(
                "time_remaining", formatDays(days),
                "overflow_count", Integer.toString(items),
                "item_or_items", items == 1 ? "item" : "items");
    }

    static Map<String, String> jobInventoryOverflowReplacements(ServerLevel level, Villager villager) {
        return activeOverflowClaim(level, villager)
                .map(tag -> overflowReplacements(level, villager, tag))
                .orElseGet(() -> Map.of(
                        "time_remaining", formatDays(0),
                        "overflow_count", "0",
                        "item_or_items", "items"));
    }

    private static String formatDays(int count) {
        return count + " day" + (count == 1 ? "" : "s");
    }

    private enum AutoPaymentResult {
        SUCCESS,
        UNAVAILABLE,
        INSUFFICIENT_FUNDS
    }

    private static Optional<CompoundTag> contract(Villager villager) {
        if (!hasContract(villager)) {
            return Optional.empty();
        }
        return Optional.of(villager.getPersistentData().getCompound(CONTRACT_TAG));
    }

    private static int reputationCostModifier(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerReputationLevel reputation = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return switch (reputation) {
            case ROYALTY -> VillagerRetaliationConfig.HIRED_CONTRACT_ROYALTY_COST_MODIFIER.get();
            case REVERED -> VillagerRetaliationConfig.HIRED_CONTRACT_REVERED_COST_MODIFIER.get();
            case RESPECTED -> VillagerRetaliationConfig.HIRED_CONTRACT_RESPECTED_COST_MODIFIER.get();
            case TRUSTED -> VillagerRetaliationConfig.HIRED_CONTRACT_TRUSTED_COST_MODIFIER.get();
            case NEUTRAL -> VillagerRetaliationConfig.HIRED_CONTRACT_NEUTRAL_COST_MODIFIER.get();
            case SUSPICIOUS -> VillagerRetaliationConfig.HIRED_CONTRACT_SUSPICIOUS_COST_MODIFIER.get();
            case HOSTILE -> VillagerRetaliationConfig.HIRED_CONTRACT_HOSTILE_COST_MODIFIER.get();
            case DESPISED -> VillagerRetaliationConfig.HIRED_CONTRACT_DESPISED_COST_MODIFIER.get();
            case FEARED -> VillagerRetaliationConfig.HIRED_CONTRACT_FEARED_COST_MODIFIER.get();
        };
    }
}
