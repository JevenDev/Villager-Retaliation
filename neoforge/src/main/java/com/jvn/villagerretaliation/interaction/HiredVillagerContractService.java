package com.jvn.villagerretaliation.interaction;

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
    private static final int BASE_DAILY_COST = 4;
    private static final int MIN_DAILY_COST = 2;
    private static final int MAX_DAILY_COST = 32;

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
        int skillPremium = Math.max(0, (skillScore - 50) / 10);
        int reputationModifier = reputationCostModifier(level, villager, player);
        int dailyCost = Mth.clamp(BASE_DAILY_COST + skillPremium + reputationModifier, MIN_DAILY_COST, MAX_DAILY_COST);
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
    }

    public static void endHireContract(Villager villager) {
        contract(villager).ifPresent(tag -> tag.putString(STATUS_TAG, STATUS_ENDED));
    }

    public static void expireHireContractIfNeeded(ServerLevel level, Villager villager) {
        contract(villager)
                .filter(HiredVillagerContractService::isActive)
                .filter(tag -> level.getGameTime() >= tag.getLong(END_GAME_TIME_TAG))
                .ifPresent(tag -> tag.putString(STATUS_TAG, STATUS_EXPIRED));
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
        contract.get().putString(ROLE_TAG, role.serializedName());
        return true;
    }

    private static int remainingDays(ServerLevel level, CompoundTag contract) {
        long remainingTicks = Math.max(0L, contract.getLong(END_GAME_TIME_TAG) - level.getGameTime());
        return (int) Math.max(1L, (remainingTicks + DAY_TICKS - 1L) / DAY_TICKS);
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
            case ROYALTY -> -2;
            case REVERED -> -1;
            case HOSTILE -> 4;
            case DESPISED -> 8;
            default -> 0;
        };
    }
}
