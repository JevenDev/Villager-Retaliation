package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class HiredVillagerContractService {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private static final String CONTRACT_ID_TAG = "ContractId";
    private static final String OVERFLOW_CLAIM_TAG = "VillagerRetaliationJobInventoryOverflowClaim";
    private static final String HIRER_TAG = "Hirer";
    private static final String OVERFLOW_CLAIM_OWNER_TAG = "Owner";
    private static final String OVERFLOW_CLAIM_CREATED_GAME_TIME_TAG = "CreatedGameTime";
    private static final String OVERFLOW_CLAIM_EXPIRES_GAME_TIME_TAG = "ExpiresGameTime";
    private static final String OVERFLOW_CLAIM_LAST_REMINDER_DAY_TAG = "LastReminderDay";
    private static final String START_GAME_TIME_TAG = "StartGameTime";
    private static final String END_GAME_TIME_TAG = "EndGameTime";
    private static final String DURATION_DAYS_TAG = "DurationDays";
    private static final String DAILY_COST_TAG = "DailyCost";
    private static final String EMERALDS_PAID_TAG = "EmeraldsPaid";
    private static final String EMERALDS_RELEASED_TAG = "EmeraldsReleased";
    private static final String EMERALDS_REFUNDED_TAG = "EmeraldsRefunded";
    private static final String AUTO_PAYMENT_TAG = "AutoPayment";
    private static final String ONE_OFF_BUILDER_JOB_TAG = "OneOffBuilderJob";
    private static final String LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG = "LastAutoPaymentAttemptGameTime";
    private static final String AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG = "AwaitingAutoPaymentStartGameTime";
    private static final String ROLE_TAG = "Role";
    private static final String STATUS_TAG = "Status";
    private static final String PROFESSION_LOCK_ARTIFICIAL_TAG = "ProfessionLockArtificial";
    private static final String PROFESSION_LOCK_ORIGINAL_XP_TAG = "ProfessionLockOriginalXp";
    private static final String PROFESSION_LOCK_APPLIED_XP_TAG = "ProfessionLockAppliedXp";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ENDED = "ended";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_AWAITING_AUTO_PAYMENT = "awaiting_auto_payment";
    private static final long DAY_TICKS = VillagerContractTime.DAY_TICKS;
    private static final long OVERFLOW_CLAIM_TICKS = 3L * DAY_TICKS;
    private static final long CONTRACT_MAINTENANCE_INTERVAL_TICKS = 20L;
    private static final long AUTO_PAYMENT_RETRY_INTERVAL_TICKS = 100L;
    private static final int HIRED_PROFESSION_LOCK_XP = 1;

    private HiredVillagerContractService() {
    }

    public static boolean isHired(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager).filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment).isPresent();
    }

    public static boolean isHiredBy(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHirer(level, villager).filter(player.getUUID()::equals).isPresent();
    }

    public static boolean canAccessJobInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        if (player == null || villager.isBaby()) {
            return false;
        }
        boolean activeHirer = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> tag.hasUUID(HIRER_TAG))
                .map(tag -> tag.getUUID(HIRER_TAG))
                .filter(player.getUUID()::equals)
                .isPresent();
        if (activeHirer) {
            return true;
        }
        return activeOverflowClaim(level, villager)
                .filter(tag -> tag.hasUUID(OVERFLOW_CLAIM_OWNER_TAG))
                .map(tag -> tag.getUUID(OVERFLOW_CLAIM_OWNER_TAG))
                .filter(player.getUUID()::equals)
                .isPresent();
    }

    public static Optional<UUID> currentContractId(Villager villager) {
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .map(HiredVillagerContractService::ensureContractId);
    }

    public static Optional<UUID> currentContractHirer(Villager villager) {
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> tag.hasUUID(HIRER_TAG))
                .map(tag -> tag.getUUID(HIRER_TAG));
    }

    public static boolean hasBlockingJobInventoryOverflow(ServerLevel level, Villager villager) {
        return activeOverflowClaim(level, villager).isPresent();
    }

    public static boolean hasForeignJobInventoryOverflow(ServerLevel level, Villager villager, ServerPlayer player) {
        return activeOverflowClaim(level, villager)
                .filter(tag -> !tag.hasUUID(OVERFLOW_CLAIM_OWNER_TAG)
                        || player == null
                        || !tag.getUUID(OVERFLOW_CLAIM_OWNER_TAG).equals(player.getUUID()))
                .isPresent();
    }

    public static int getJobInventoryOverflowRemainingDays(ServerLevel level, Villager villager) {
        return activeOverflowClaim(level, villager)
                .map(tag -> remainingOverflowDays(level, tag))
                .orElse(0);
    }

    public static int getJobInventoryOverflowItemCount(ServerLevel level, Villager villager) {
        return activeOverflowClaim(level, villager)
                .map(tag -> HiredJobInventory.getJobInventory(villager).countRemovableItemsForContract(tag.getUUID(CONTRACT_ID_TAG)))
                .orElse(0);
    }

    public static boolean tryOpenJobInventoryOverflowReminder(ServerLevel level, Villager villager, ServerPlayer player) {
        Optional<CompoundTag> claim = activeOverflowClaim(level, villager)
                .filter(tag -> tag.hasUUID(OVERFLOW_CLAIM_OWNER_TAG))
                .filter(tag -> tag.getUUID(OVERFLOW_CLAIM_OWNER_TAG).equals(player.getUUID()));
        if (claim.isEmpty()) {
            return false;
        }
        CompoundTag tag = claim.get();
        long day = level.getDayTime() / DAY_TICKS;
        if (tag.getLong(OVERFLOW_CLAIM_LAST_REMINDER_DAY_TAG) == day) {
            return false;
        }
        Map<String, String> replacements = overflowReplacements(level, villager, tag);
        String line = VillagerDialogueResources
                .message(
                        VillagerInteractionService.createDialogueContext(level, player, villager),
                        "interaction.hire_overflow_claim_reminder",
                        replacements)
                .orElse("");
        if (ForcedDialogueService.openSimpleForcedDialogue(
                player,
                villager,
                "villagerretaliation:hired_job_inventory_overflow_claim",
                line)) {
            tag.putLong(OVERFLOW_CLAIM_LAST_REMINDER_DAY_TAG, day);
            villager.setPersistenceRequired();
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
        return villager != null && villager.getPersistentData().contains(CONTRACT_TAG, Tag.TAG_COMPOUND);
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
        int skillPremium = Math.max(0, (skillScore - 50) / 10)
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

    public static void onVillagerDeath(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .ifPresent(tag -> {
                    finishContract(level, villager, tag, STATUS_ENDED, "Work stopped. Villager died.", false);
                });
    }

    public static void startHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        startHireContract(level, villager, player, days, emeraldsPaid, HiredVillagerRoles.defaultRole(level, villager));
    }

    public static void startHireContract(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int days,
            int emeraldsPaid,
            HiredVillagerRole role) {
        if (level == null || villager == null || villager.isBaby() || player == null) {
            return;
        }
        int safeDays = clampedContractDays(days);
        HiredVillagerRole safeRole = role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
        if (safeRole == null || !HiredVillagerRoles.availableContractRoles(level, villager).contains(safeRole)) {
            return;
        }
        VillagerRecruitmentService.stopFollowing(villager);
        long startGameTime = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        UUID contractId = UUID.randomUUID();
        tag.putUUID(CONTRACT_ID_TAG, contractId);
        tag.putUUID(HIRER_TAG, player.getUUID());
        tag.putLong(START_GAME_TIME_TAG, startGameTime);
        tag.putLong(END_GAME_TIME_TAG, startGameTime + safeDays * DAY_TICKS);
        tag.putInt(DURATION_DAYS_TAG, safeDays);
        tag.putInt(DAILY_COST_TAG, Math.max(1, emeraldsPaid / safeDays));
        tag.putInt(EMERALDS_PAID_TAG, emeraldsPaid);
        tag.putInt(EMERALDS_RELEASED_TAG, 0);
        tag.putInt(EMERALDS_REFUNDED_TAG, 0);
        tag.putBoolean(AUTO_PAYMENT_TAG, false);
        tag.putBoolean(ONE_OFF_BUILDER_JOB_TAG, false);
        tag.putString(ROLE_TAG, safeRole.serializedName());
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        lockProfessionForHire(villager, tag);
        villager.getPersistentData().put(CONTRACT_TAG, tag);
        villager.getPersistentData().remove(OVERFLOW_CLAIM_TAG);
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        villager.setPersistenceRequired();
        HiredVillagerIndex.update(level, villager);
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.syncNameToTracking(villager);
    }

    public static void startOneOffBuilderJob(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || villager.isBaby() || player == null) {
            return;
        }
        VillagerRecruitmentService.stopFollowing(villager);
        long startGameTime = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        UUID contractId = UUID.randomUUID();
        tag.putUUID(CONTRACT_ID_TAG, contractId);
        tag.putUUID(HIRER_TAG, player.getUUID());
        tag.putLong(START_GAME_TIME_TAG, startGameTime);
        tag.putLong(END_GAME_TIME_TAG, Long.MAX_VALUE);
        tag.putInt(DURATION_DAYS_TAG, 0);
        tag.putInt(DAILY_COST_TAG, 0);
        tag.putInt(EMERALDS_PAID_TAG, 0);
        tag.putInt(EMERALDS_RELEASED_TAG, 0);
        tag.putInt(EMERALDS_REFUNDED_TAG, 0);
        tag.putBoolean(AUTO_PAYMENT_TAG, false);
        tag.putBoolean(ONE_OFF_BUILDER_JOB_TAG, true);
        tag.putString(ROLE_TAG, HiredVillagerRole.BUILDER.serializedName());
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        villager.getPersistentData().put(CONTRACT_TAG, tag);
        villager.getPersistentData().remove(OVERFLOW_CLAIM_TAG);
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        villager.setPersistenceRequired();
        HiredVillagerIndex.update(level, villager);
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.syncNameToTracking(villager);
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
        releaseEarnedHirePayment(level, villager, tag);
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
        int refund = earlyEndRefund(level, tag);
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

    public static HiredVillagerRole activeRole(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        HiredVillagerRole role = contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .map(tag -> HiredVillagerRole.bySerializedName(tag.getString(ROLE_TAG)))
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
                    && BuilderTaskState.hasTask(HiredVillagerWorkService.state(villager))) {
                return false;
            }
            clearContractScopedOrders(level, villager, currentRole);
            HiredVillagerWorkService.cancelWork(level, villager, currentRole, "Work stopped. Role changed.");
            HiredVillagerWorkService.resetReportProgress(level, villager);
        }
        tag.putString(ROLE_TAG, role.serializedName());
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
        releaseEarnedHirePayment(level, villager, tag);
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
        if (HiredVillagerWorkService.WAITING_FOR_HIRER_STATUS.equals(
                HiredVillagerWorkService.state(villager).getString("Status"))) {
            return false;
        }
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
        HiredVillagerWorkService.pauseWork(level, villager, role, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
        setWorkStatus(villager, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
        tag.putString(STATUS_TAG, STATUS_AWAITING_AUTO_PAYMENT);
        tag.putLong(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG, level.getGameTime());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        villager.setPersistenceRequired();
    }

    private static void handleAwaitingAutoPayment(ServerLevel level, Villager villager, CompoundTag tag) {
        if (isAwaitingAutoPaymentExpired(level, tag)) {
            expireContract(level, villager, tag, "Work stopped. Recurring payment was unpaid for more than a day.");
            return;
        }
        long gameTime = level.getGameTime();
        long lastAttempt = tag.getLong(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG);
        if (tag.contains(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG, Tag.TAG_LONG)
                && lastAttempt <= gameTime
                && gameTime - lastAttempt < AUTO_PAYMENT_RETRY_INTERVAL_TICKS) {
            return;
        }
        if (!canAttemptAutoPaymentRenewal(level, villager, tag)) {
            HiredWorkerBrain.setState(HiredVillagerWorkService.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setWorkStatus(villager, "Contract paused. Recurring payment is unpaid.");
            return;
        }

        AutoPaymentResult result = tryAutoPaymentRenewal(level, villager, tag);
        if (result == AutoPaymentResult.SUCCESS) {
            return;
        }
        if (result == AutoPaymentResult.INSUFFICIENT_FUNDS) {
            HiredWorkerBrain.setState(HiredVillagerWorkService.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setWorkStatus(villager, "Contract paused. Assigned payment box has no renewal payment.");
            return;
        }

        HiredWorkerBrain.setState(HiredVillagerWorkService.state(villager), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        setWorkStatus(villager, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
    }

    private static AutoPaymentResult tryAutoPaymentRenewal(ServerLevel level, Villager villager, CompoundTag tag) {
        tag.putLong(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG, level.getGameTime());
        int dailyCost = currentRenewalDailyCost(level, villager, tag);
        if (!AssignedStorageService.hasLoadedAssignedPaymentStorage(level, villager)) {
            return AutoPaymentResult.UNAVAILABLE;
        }
        if (AssignedStorageService.countPaymentItems(
                villager,
                stack -> VillagerCurrencyResources.isCurrency(level.getServer(), stack)) < dailyCost) {
            return AutoPaymentResult.INSUFFICIENT_FUNDS;
        }
        int consumed = AssignedStorageService.consumePaymentItems(
                villager,
                stack -> VillagerCurrencyResources.isCurrency(level.getServer(), stack),
                dailyCost);
        if (consumed < dailyCost) {
            return AutoPaymentResult.INSUFFICIENT_FUNDS;
        }
        releaseEarnedHirePayment(level, villager, tag);
        extendActiveContract(level, tag, 1, dailyCost);
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        tag.remove(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG);
        HiredWorkerBrain.setState(HiredVillagerWorkService.state(villager), HiredWorkerTaskState.IDLE, null);
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
        settleHirePaymentEscrow(level, villager, tag, refund);
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        UUID contractId = ensureContractId(tag);
        finalizeBuilderJobForContractEnd(level, villager, tag, role);
        HiredVillagerWorkService.finishWork(level, villager, role, workStatus);
        clearContractScopedOrders(level, villager, role);
        tag.putString(STATUS_TAG, contractStatus);
        unlockProfessionAfterHire(villager, tag);
        if (depositJobInventory) {
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            inventory.markRemovableItemsForContract(contractId);
            inventory.depositRemovableItemsToAssignedStorage();
            rememberOverflowClaimIfNeeded(level, villager, tag, contractId);
        }
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        villager.setPersistenceRequired();
        HiredVillagerIndex.remove(villager);
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.syncNameToTracking(villager);
    }

    private static void finalizeBuilderJobForContractEnd(
            ServerLevel level,
            Villager villager,
            CompoundTag contract,
            HiredVillagerRole role) {
        if (role != HiredVillagerRole.BUILDER) {
            return;
        }
        CompoundTag state = HiredVillagerWorkService.state(villager);
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
        CompoundTag state = HiredVillagerWorkService.state(villager);
        String safeStatus = status == null ? "" : status;
        if (!safeStatus.equals(state.getString("Status"))) {
            state.putString("Status", safeStatus);
        }
    }

    private static void clearContractScopedOrders(ServerLevel level, Villager villager, HiredVillagerRole role) {
        if (role == HiredVillagerRole.BREWING) {
            BrewingWorker.clearOrder(HiredVillagerWorkService.state(villager));
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

    private static void lockProfessionForHire(Villager villager, CompoundTag tag) {
        clearProfessionLockTags(tag);
        if (villager.isBaby()) {
            return;
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return;
        }
        int originalXp = villager.getVillagerXp();
        if (originalXp > 0) {
            return;
        }
        villager.setVillagerXp(HIRED_PROFESSION_LOCK_XP);
        tag.putBoolean(PROFESSION_LOCK_ARTIFICIAL_TAG, true);
        tag.putInt(PROFESSION_LOCK_ORIGINAL_XP_TAG, originalXp);
        tag.putInt(PROFESSION_LOCK_APPLIED_XP_TAG, HIRED_PROFESSION_LOCK_XP);
    }

    private static void unlockProfessionAfterHire(Villager villager, CompoundTag tag) {
        if (!tag.getBoolean(PROFESSION_LOCK_ARTIFICIAL_TAG)) {
            clearProfessionLockTags(tag);
            return;
        }
        int originalXp = tag.getInt(PROFESSION_LOCK_ORIGINAL_XP_TAG);
        int appliedXp = tag.getInt(PROFESSION_LOCK_APPLIED_XP_TAG);
        if (appliedXp <= 0) {
            appliedXp = HIRED_PROFESSION_LOCK_XP;
        }
        if (villager.getVillagerXp() == appliedXp) {
            villager.setVillagerXp(originalXp);
        }
        clearProfessionLockTags(tag);
        villager.setPersistenceRequired();
    }

    private static void clearProfessionLockTags(CompoundTag tag) {
        tag.remove(PROFESSION_LOCK_ARTIFICIAL_TAG);
        tag.remove(PROFESSION_LOCK_ORIGINAL_XP_TAG);
        tag.remove(PROFESSION_LOCK_APPLIED_XP_TAG);
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

    private static int earlyEndRefund(ServerLevel level, CompoundTag contract) {
        int committedPayment = Math.max(releasedHirePayment(contract), accruedHirePayment(level, contract));
        int remainingPaidValue = Math.max(0, escrowedHirePayment(contract) - committedPayment);
        int refundPercent = Mth.clamp(VillagerRetaliationConfig.HIRED_CONTRACT_EARLY_END_REFUND_PERCENT.get(), 0, 100);
        return (int) Math.floor(remainingPaidValue * refundPercent / 100.0D);
    }

    private static void releaseEarnedHirePayment(ServerLevel level, Villager villager, CompoundTag contract) {
        if (isOneOffBuilderJob(contract)) {
            return;
        }
        int released = releasedHirePayment(contract);
        int earned = accruedHirePayment(level, contract);
        releaseHirePayment(villager, contract, Math.max(0, earned - released));
    }

    private static void settleHirePaymentEscrow(
            ServerLevel level,
            Villager villager,
            CompoundTag contract,
            int refund) {
        if (isOneOffBuilderJob(contract)) {
            return;
        }
        releaseEarnedHirePayment(level, villager, contract);
        int paid = escrowedHirePayment(contract);
        int released = releasedHirePayment(contract);
        int safeRefund = Mth.clamp(refund, 0, Math.max(0, paid - released));
        releaseHirePayment(villager, contract, Math.max(0, paid - released - safeRefund));
        contract.putInt(EMERALDS_REFUNDED_TAG, safeRefund);
    }

    private static int accruedHirePayment(ServerLevel level, CompoundTag contract) {
        int paid = escrowedHirePayment(contract);
        long start = contract.getLong(START_GAME_TIME_TAG);
        long end = Math.max(start, contract.getLong(END_GAME_TIME_TAG));
        long duration = end - start;
        if (paid <= 0 || duration <= 0L) {
            return paid;
        }
        long elapsed = Mth.clamp(level.getGameTime() - start, 0L, duration);
        return Mth.clamp((int) Math.floor(paid * (elapsed / (double) duration)), 0, paid);
    }

    private static int escrowedHirePayment(CompoundTag contract) {
        return Math.max(0, contract.getInt(EMERALDS_PAID_TAG));
    }

    private static int releasedHirePayment(CompoundTag contract) {
        int paid = escrowedHirePayment(contract);
        if (!contract.contains(EMERALDS_RELEASED_TAG, Tag.TAG_INT)) {
            contract.putInt(EMERALDS_RELEASED_TAG, paid);
            return paid;
        }
        return Mth.clamp(contract.getInt(EMERALDS_RELEASED_TAG), 0, paid);
    }

    private static void releaseHirePayment(Villager villager, CompoundTag contract, int amount) {
        if (amount <= 0) {
            return;
        }
        VillagerWalletService.addCurrency(villager, amount, VillagerWalletService.WalletSource.HIRE_PAYMENT);
        contract.putInt(EMERALDS_RELEASED_TAG, releasedHirePayment(contract) + amount);
        villager.setPersistenceRequired();
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
        int overflowCount = HiredJobInventory.getJobInventory(villager).countRemovableItemsForContract(contractId);
        if (overflowCount <= 0) {
            clearOverflowClaim(villager);
            return;
        }
        CompoundTag claim = new CompoundTag();
        claim.putUUID(CONTRACT_ID_TAG, contractId);
        claim.putUUID(OVERFLOW_CLAIM_OWNER_TAG, contract.getUUID(HIRER_TAG));
        claim.putLong(OVERFLOW_CLAIM_CREATED_GAME_TIME_TAG, level.getGameTime());
        claim.putLong(OVERFLOW_CLAIM_EXPIRES_GAME_TIME_TAG, level.getGameTime() + OVERFLOW_CLAIM_TICKS);
        claim.putLong(OVERFLOW_CLAIM_LAST_REMINDER_DAY_TAG, -1L);
        villager.getPersistentData().put(OVERFLOW_CLAIM_TAG, claim);
    }

    private static Optional<CompoundTag> activeOverflowClaim(ServerLevel level, Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(OVERFLOW_CLAIM_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag claim = persistentData.getCompound(OVERFLOW_CLAIM_TAG);
        if (!claim.hasUUID(CONTRACT_ID_TAG) || !claim.hasUUID(OVERFLOW_CLAIM_OWNER_TAG)) {
            clearOverflowClaim(villager);
            return Optional.empty();
        }
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        if (!inventory.hasRemovableItemsForContract(claim.getUUID(CONTRACT_ID_TAG))) {
            clearOverflowClaim(villager);
            return Optional.empty();
        }
        if (level.getGameTime() >= claim.getLong(OVERFLOW_CLAIM_EXPIRES_GAME_TIME_TAG)) {
            clearOverflowClaim(villager);
            return Optional.empty();
        }
        return Optional.of(claim);
    }

    private static void clearOverflowClaim(Villager villager) {
        if (villager.getPersistentData().contains(OVERFLOW_CLAIM_TAG)) {
            villager.getPersistentData().remove(OVERFLOW_CLAIM_TAG);
            villager.setPersistenceRequired();
        }
    }

    private static int remainingOverflowDays(ServerLevel level, CompoundTag claim) {
        long remainingTicks = Math.max(0L, claim.getLong(OVERFLOW_CLAIM_EXPIRES_GAME_TIME_TAG) - level.getGameTime());
        return (int) Math.max(1L, (remainingTicks + DAY_TICKS - 1L) / DAY_TICKS);
    }

    private static Map<String, String> overflowReplacements(ServerLevel level, Villager villager, CompoundTag claim) {
        int days = remainingOverflowDays(level, claim);
        int items = HiredJobInventory.getJobInventory(villager).countRemovableItemsForContract(claim.getUUID(CONTRACT_ID_TAG));
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
