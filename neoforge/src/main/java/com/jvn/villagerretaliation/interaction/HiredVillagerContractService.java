package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class HiredVillagerContractService {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private static final String HIRER_TAG = "Hirer";
    private static final String START_GAME_TIME_TAG = "StartGameTime";
    private static final String END_GAME_TIME_TAG = "EndGameTime";
    private static final String DURATION_DAYS_TAG = "DurationDays";
    private static final String DAILY_COST_TAG = "DailyCost";
    private static final String EMERALDS_PAID_TAG = "EmeraldsPaid";
    private static final String AUTO_PAYMENT_TAG = "AutoPayment";
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
    private static final long DAY_TICKS = 24000L;
    private static final int MAX_CONTRACT_DAYS = 30;
    private static final int HIRED_PROFESSION_LOCK_XP = 1;

    private HiredVillagerContractService() {
    }

    public static boolean isHired(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager).filter(HiredVillagerContractService::isActive).isPresent();
    }

    public static boolean isHiredBy(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHirer(level, villager).filter(player.getUUID()::equals).isPresent();
    }

    public static boolean canAccessJobInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        return player != null
                && !villager.isBaby()
                && contract(villager)
                .filter(tag -> tag.hasUUID(HIRER_TAG))
                .map(tag -> tag.getUUID(HIRER_TAG))
                .filter(player.getUUID()::equals)
                .isPresent();
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || !villager.isAlive()) {
            return;
        }
        maybeAutoRenew(level, villager);
    }

    public static Optional<UUID> getHirer(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .filter(tag -> tag.hasUUID(HIRER_TAG))
                .map(tag -> tag.getUUID(HIRER_TAG));
    }

    public static int getRemainingHireDays(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .map(tag -> remainingDays(level, tag))
                .orElse(0);
    }

    public static int getHireCost(ServerLevel level, Villager villager, ServerPlayer player, int days) {
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
        return dailyCost * clampedContractDays(days);
    }

    public static int getDailyCost(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHireCost(level, villager, player, 1);
    }

    public static int getContractDailyCost(ServerLevel level, Villager villager, ServerPlayer player) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
                .map(tag -> Math.max(0, tag.getInt(DAILY_COST_TAG)))
                .filter(cost -> cost > 0)
                .orElseGet(() -> getDailyCost(level, villager, player));
    }

    public static boolean isAutoPaymentEnabled(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActiveOrAwaitingAutoPayment)
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
        Optional<CompoundTag> activeContract = contract(villager).filter(HiredVillagerContractService::isActive);
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
                .filter(HiredVillagerContractService::isActive)
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
        int safeDays = clampedContractDays(days);
        long startGameTime = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        tag.putUUID(HIRER_TAG, player.getUUID());
        tag.putLong(START_GAME_TIME_TAG, startGameTime);
        tag.putLong(END_GAME_TIME_TAG, startGameTime + safeDays * DAY_TICKS);
        tag.putInt(DURATION_DAYS_TAG, safeDays);
        tag.putInt(DAILY_COST_TAG, Math.max(1, emeraldsPaid / safeDays));
        tag.putInt(EMERALDS_PAID_TAG, emeraldsPaid);
        tag.putBoolean(AUTO_PAYMENT_TAG, false);
        tag.putString(ROLE_TAG, HiredVillagerRoles.defaultRole(level, villager).serializedName());
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        lockProfessionForHire(villager, tag);
        villager.getPersistentData().put(CONTRACT_TAG, tag);
        villager.setPersistenceRequired();
        HiredVillagerIndex.update(level, villager);
    }

    public static boolean extendHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> contract = contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()));
        if (contract.isEmpty()) {
            return false;
        }
        int safeDays = clampedContractDays(days);
        CompoundTag tag = contract.get();
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
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()));
        if (activeContract.isEmpty()) {
            return 0;
        }
        CompoundTag tag = activeContract.get();
        int refund = earlyEndRefund(level, tag);
        finishContract(level, villager, tag, STATUS_ENDED, "Work stopped. Contract ended.", true);
        return refund;
    }

    public static void expireHireContractIfNeeded(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActive)
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
        if (role == null || !HiredVillagerRoles.availableRoles(level, villager).contains(role)) {
            return false;
        }
        Optional<CompoundTag> contract = contract(villager).filter(HiredVillagerContractService::isActive);
        if (contract.isEmpty()) {
            return false;
        }
        CompoundTag tag = contract.get();
        HiredVillagerRole currentRole = roleFromContract(level, villager, tag);
        if (currentRole != role) {
            HiredVillagerWorkService.stopWork(level, villager, currentRole, "Work stopped. Role changed.");
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
        long remainingTicks = Math.max(0L, contract.getLong(END_GAME_TIME_TAG) - level.getGameTime());
        return (int) Math.max(1L, (remainingTicks + DAY_TICKS - 1L) / DAY_TICKS);
    }

    private static void maybeAutoRenew(ServerLevel level, Villager villager) {
        Optional<CompoundTag> awaitingPaymentContract = contract(villager)
                .filter(HiredVillagerContractService::isAwaitingAutoPayment);
        if (awaitingPaymentContract.isPresent()) {
            handleAwaitingAutoPayment(level, villager, awaitingPaymentContract.get());
            return;
        }

        Optional<CompoundTag> activeContract = contract(villager).filter(HiredVillagerContractService::isActive);
        if (activeContract.isEmpty()) {
            return;
        }
        CompoundTag tag = activeContract.get();
        if (!tag.getBoolean(AUTO_PAYMENT_TAG)) {
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
                && AssignedStorageService.hasAssignedPaymentStorage(level, villager);
    }

    private static void beginAwaitingAutoPayment(ServerLevel level, Villager villager, CompoundTag tag) {
        if (isAwaitingAutoPayment(tag)) {
            return;
        }
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        HiredVillagerWorkService.stopWork(level, villager, role, "Contract paused. Assigned payment box must be in a loaded chunk for renewal.");
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
        int dailyCost = Math.max(1, tag.getInt(DAILY_COST_TAG));
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
        extendActiveContract(level, tag, 1, dailyCost);
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        tag.putLong(LAST_AUTO_PAYMENT_ATTEMPT_GAME_TIME_TAG, level.getGameTime());
        tag.remove(AWAITING_AUTO_PAYMENT_START_GAME_TIME_TAG);
        HiredWorkerBrain.setState(HiredVillagerWorkService.state(villager), HiredWorkerTaskState.IDLE, null);
        setWorkStatus(villager, "Contract renewed from assigned payment box.");
        VillagerWalletService.addCurrency(villager, dailyCost, VillagerWalletService.WalletSource.HIRE_PAYMENT);
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

    private static void depositJobInventoryToAssignedStorage(Villager villager) {
        HiredJobInventory.getJobInventory(villager).depositRemovableItemsToAssignedStorage();
    }

    private static void finishContract(
            ServerLevel level,
            Villager villager,
            CompoundTag tag,
            String contractStatus,
            String workStatus,
            boolean depositJobInventory) {
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        HiredVillagerWorkService.stopWork(level, villager, role, workStatus);
        tag.putString(STATUS_TAG, contractStatus);
        unlockProfessionAfterHire(villager, tag);
        if (depositJobInventory) {
            depositJobInventoryToAssignedStorage(villager);
        }
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        villager.setPersistenceRequired();
        HiredVillagerIndex.remove(villager);
    }

    private static void setWorkStatus(Villager villager, String status) {
        HiredVillagerWorkService.state(villager).putString("Status", status == null ? "" : status);
    }

    private static void extendActiveContract(ServerLevel level, CompoundTag tag, int days, int emeraldsPaid) {
        int safeDays = clampedContractDays(days);
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
        return Mth.clamp(days, 1, MAX_CONTRACT_DAYS);
    }

    private static int earlyEndRefund(ServerLevel level, CompoundTag contract) {
        long remainingTicks = Math.max(0L, contract.getLong(END_GAME_TIME_TAG) - level.getGameTime());
        if (remainingTicks <= 0L) {
            return 0;
        }
        int paid = Math.max(0, contract.getInt(EMERALDS_PAID_TAG));
        int durationDays = Math.max(1, contract.getInt(DURATION_DAYS_TAG));
        double averagePaidPerTick = paid / (durationDays * (double) DAY_TICKS);
        double remainingPaidValue = averagePaidPerTick * remainingTicks;
        int refundPercent = Mth.clamp(VillagerRetaliationConfig.HIRED_CONTRACT_EARLY_END_REFUND_PERCENT.get(), 0, 100);
        return (int) Math.floor(remainingPaidValue * refundPercent / 100.0D);
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

    private enum AutoPaymentResult {
        SUCCESS,
        UNAVAILABLE,
        INSUFFICIENT_FUNDS
    }

    private static Optional<CompoundTag> contract(Villager villager) {
        if (!villager.getPersistentData().contains(CONTRACT_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
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
