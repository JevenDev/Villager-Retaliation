package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

public final class HiredVillagerContractService {
    private static final String CONTRACT_TAG = "VillagerRetaliationHireContract";
    private static final String HIRER_TAG = "Hirer";
    private static final String START_GAME_TIME_TAG = "StartGameTime";
    private static final String END_GAME_TIME_TAG = "EndGameTime";
    private static final String DURATION_DAYS_TAG = "DurationDays";
    private static final String DAILY_COST_TAG = "DailyCost";
    private static final String EMERALDS_PAID_TAG = "EmeraldsPaid";
    private static final String ROLE_TAG = "Role";
    private static final String STATUS_TAG = "Status";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ENDED = "ended";
    private static final String STATUS_EXPIRED = "expired";
    private static final long DAY_TICKS = 24000L;

    private HiredVillagerContractService() {
    }

    public static boolean isHired(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager).filter(HiredVillagerContractService::isActive).isPresent();
    }

    public static boolean isHiredBy(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHirer(level, villager).filter(player.getUUID()::equals).isPresent();
    }

    public static Optional<UUID> getHirer(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> tag.hasUUID(HIRER_TAG))
                .map(tag -> tag.getUUID(HIRER_TAG));
    }

    public static int getRemainingHireDays(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        return contract(villager)
                .filter(HiredVillagerContractService::isActive)
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
        return dailyCost * Math.max(1, days);
    }

    public static int getDailyCost(ServerLevel level, Villager villager, ServerPlayer player) {
        return getHireCost(level, villager, player, 1);
    }

    public static void startHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        int safeDays = Mth.clamp(days, 1, 30);
        long startGameTime = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        tag.putUUID(HIRER_TAG, player.getUUID());
        tag.putLong(START_GAME_TIME_TAG, startGameTime);
        tag.putLong(END_GAME_TIME_TAG, startGameTime + safeDays * DAY_TICKS);
        tag.putInt(DURATION_DAYS_TAG, safeDays);
        tag.putInt(DAILY_COST_TAG, Math.max(1, emeraldsPaid / safeDays));
        tag.putInt(EMERALDS_PAID_TAG, emeraldsPaid);
        tag.putString(ROLE_TAG, HiredVillagerRoles.defaultRole(level, villager).serializedName());
        tag.putString(STATUS_TAG, STATUS_ACTIVE);
        villager.getPersistentData().put(CONTRACT_TAG, tag);
        villager.setPersistenceRequired();
    }

    public static boolean extendHireContract(ServerLevel level, Villager villager, ServerPlayer player, int days, int emeraldsPaid) {
        expireHireContractIfNeeded(level, villager);
        Optional<CompoundTag> contract = contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> tag.hasUUID(HIRER_TAG) && tag.getUUID(HIRER_TAG).equals(player.getUUID()));
        if (contract.isEmpty()) {
            return false;
        }
        int safeDays = Mth.clamp(days, 1, 30);
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
        HiredVillagerRole role = roleFromContract(level, villager, tag);
        HiredVillagerWorkService.stopWork(level, villager, role, "Work stopped. Contract ended.");
        int refund = earlyEndRefund(level, tag);
        tag.putString(STATUS_TAG, STATUS_ENDED);
        AssignedStorageService.removeAssignedStorage(level, villager);
        return refund;
    }

    public static void expireHireContractIfNeeded(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> level.getGameTime() >= tag.getLong(END_GAME_TIME_TAG))
                .ifPresent(tag -> {
                    HiredVillagerRole role = roleFromContract(level, villager, tag);
                    HiredVillagerWorkService.stopWork(level, villager, role, "Work stopped. Contract expired.");
                    tag.putString(STATUS_TAG, STATUS_EXPIRED);
                    AssignedStorageService.removeAssignedStorage(level, villager);
                });
    }

    public static HiredVillagerRole activeRole(ServerLevel level, Villager villager) {
        expireHireContractIfNeeded(level, villager);
        HiredVillagerRole role = contract(villager)
                .filter(HiredVillagerContractService::isActive)
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
