package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

public final class VillagerSkillGrowthService {
    private static final double REGULAR_TRADE_PROGRESS_EPSILON = 0.000_001D;

    private VillagerSkillGrowthService() {
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player) {
        return onTradeCompleted(level, villager, player, 1);
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player,
            int completedTrades) {
        if (!(villager instanceof Villager villageResident) || villageResident.isBaby()) {
            return VillagerSkillGrowthResult.NONE;
        }

        int tradeCount = Math.max(1, completedTrades);
        int currentTradeLevel = Math.clamp(villageResident.getVillagerData().getLevel(), 1, 5);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        int previousAwardedLevel = profile.highestSkillGrowthTradeLevelAwarded();

        RandomSource random = villager.getRandom();
        List<VillagerSkillGrowthResult.SkillIncrease> increases = new ArrayList<>();
        boolean profileChanged = false;

        profileChanged |= awardRegularTradeGrowth(level, profile, villager, tradeCount, increases);

        if (VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS.get()
                && currentTradeLevel > previousAwardedLevel) {
            for (int milestone = previousAwardedLevel + 1; milestone <= currentTradeLevel; milestone++) {
                profileChanged |= awardMilestone(level, profile, villager, milestone, random, increases);
            }
            profileChanged |= profile.markSkillGrowthTradeLevelAwarded(currentTradeLevel, level.getGameTime());
        }

        VillagerSkillGrowthResult result = new VillagerSkillGrowthResult(previousAwardedLevel, currentTradeLevel, increases);
        if (profileChanged) {
            VillagerProfileSavedData.get(level).setDirty();
            if (player != null && result.changed()) {
                VillagerReputationNetworking.sendProfile(player, villager, profile);
                sendFeedback(player, villager, result);
            }
        }

        return result;
    }

    private static boolean awardRegularTradeGrowth(
            ServerLevel level,
            VillagerProfile profile,
            AbstractVillager villager,
            int completedTrades,
            List<VillagerSkillGrowthResult.SkillIncrease> increases) {
        if (!VillagerRetaliationConfig.ENABLE_REGULAR_TRADE_SKILL_GROWTH.get()) {
            return false;
        }

        double amount = Math.max(0.0D, VillagerRetaliationConfig.REGULAR_TRADE_SKILL_GROWTH_AMOUNT.get());
        if (amount <= 0.0D) {
            return false;
        }

        VillagerSkill primary = VillagerProfessionSkills.primarySkill(villager);
        int oldValue = profile.skills().get(primary);
        if (oldValue >= VillagerSkillSet.MAX_VALUE) {
            return profile.setRegularTradeSkillGrowthProgress(primary, 0.0D, level.getGameTime());
        }

        double totalProgress = profile.regularTradeSkillGrowthProgress(primary) + amount * Math.max(1, completedTrades);
        int wholePoints = (int) Math.floor(totalProgress + REGULAR_TRADE_PROGRESS_EPSILON);
        double remainingProgress = totalProgress - wholePoints;
        int awardedPoints = Math.min(wholePoints, VillagerSkillSet.MAX_VALUE - oldValue);
        boolean changed = false;

        if (awardedPoints > 0) {
            changed |= increaseSkill(level, profile, primary, awardedPoints, true, increases);
        }

        if (oldValue + awardedPoints >= VillagerSkillSet.MAX_VALUE) {
            remainingProgress = 0.0D;
        }
        changed |= profile.setRegularTradeSkillGrowthProgress(primary, remainingProgress, level.getGameTime());
        return changed;
    }

    private static boolean awardMilestone(
            ServerLevel level,
            VillagerProfile profile,
            AbstractVillager villager,
            int milestone,
            RandomSource random,
            List<VillagerSkillGrowthResult.SkillIncrease> increases) {
        boolean changed = false;
        VillagerSkill primary = VillagerProfessionSkills.primarySkill(villager);
        int primaryAmount = primaryGrowthAmount(milestone, random);
        changed |= increaseSkill(level, profile, primary, primaryAmount, true, increases);

        int secondaryMax = Math.clamp(VillagerRetaliationConfig.SKILL_GROWTH_SECONDARY_MAX.get(), 0, 5);
        if (secondaryMax > 0 && random.nextDouble() < VillagerRetaliationConfig.SKILL_GROWTH_SECONDARY_CHANCE.get()) {
            List<VillagerSkill> secondarySkills = VillagerProfessionSkills.tradeSkills(villager)
                    .stream()
                    .filter(skill -> skill != primary)
                    .toList();
            if (!secondarySkills.isEmpty()) {
                VillagerSkill secondary = secondarySkills.get(random.nextInt(secondarySkills.size()));
                int amount = 1 + random.nextInt(secondaryMax);
                changed |= increaseSkill(level, profile, secondary, amount, false, increases);
            }
        }
        return changed;
    }

    private static boolean increaseSkill(
            ServerLevel level,
            VillagerProfile profile,
            VillagerSkill skill,
            int amount,
            boolean primary,
            List<VillagerSkillGrowthResult.SkillIncrease> increases) {
        if (amount <= 0) {
            return false;
        }

        int oldValue = profile.skills().get(skill);
        int newValue = VillagerSkillSet.clamp(oldValue + amount);
        if (!profile.setSkill(skill, newValue, level.getGameTime())) {
            return false;
        }
        increases.add(new VillagerSkillGrowthResult.SkillIncrease(skill, newValue - oldValue, newValue, primary));
        return true;
    }

    private static int primaryGrowthAmount(int tradeLevel, RandomSource random) {
        int configuredMin = Math.clamp(VillagerRetaliationConfig.SKILL_GROWTH_PRIMARY_MIN.get(), 0, 10);
        int configuredMax = Math.clamp(VillagerRetaliationConfig.SKILL_GROWTH_PRIMARY_MAX.get(), configuredMin, 10);
        int milestoneMin = switch (tradeLevel) {
            case 2 -> configuredMin;
            case 3, 4 -> Math.min(configuredMax, configuredMin + 1);
            case 5 -> Math.min(configuredMax, configuredMin + 2);
            default -> configuredMin;
        };
        int milestoneMax = switch (tradeLevel) {
            case 2 -> Math.min(configuredMax, Math.max(configuredMin, 2));
            case 3 -> Math.min(configuredMax, Math.max(configuredMin, 3));
            case 4 -> Math.min(configuredMax, Math.max(configuredMin, 4));
            case 5 -> configuredMax;
            default -> configuredMin;
        };
        if (milestoneMax <= milestoneMin) {
            return milestoneMin;
        }
        return milestoneMin + random.nextInt(milestoneMax - milestoneMin + 1);
    }

    private static void sendFeedback(
            ServerPlayer player,
            AbstractVillager villager,
            VillagerSkillGrowthResult result) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FEEDBACK.get()) {
            return;
        }

        result.primaryIncrease()
                .or(() -> result.increases().stream().findFirst())
                .ifPresent(increase -> player.displayClientMessage(
                        Component.translatable(
                                "villagerretaliation.skill_growth.improved",
                                VillagerPresetNameRegistry.resolveDisplayName(villager),
                                Component.translatable(increase.skill().translationKey())),
                        true));
    }
}
