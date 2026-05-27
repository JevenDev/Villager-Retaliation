package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

public final class VillagerSkillGrowthService {
    private VillagerSkillGrowthService() {
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS.get()
                || !(villager instanceof Villager villageResident)
                || villageResident.isBaby()) {
            return VillagerSkillGrowthResult.NONE;
        }

        int currentTradeLevel = Math.clamp(villageResident.getVillagerData().getLevel(), 1, 5);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        int previousAwardedLevel = profile.highestSkillGrowthTradeLevelAwarded();
        if (currentTradeLevel <= previousAwardedLevel) {
            return new VillagerSkillGrowthResult(previousAwardedLevel, currentTradeLevel, List.of());
        }

        RandomSource random = villager.getRandom();
        List<VillagerSkillGrowthResult.SkillIncrease> increases = new ArrayList<>();
        boolean changed = false;
        for (int milestone = previousAwardedLevel + 1; milestone <= currentTradeLevel; milestone++) {
            changed |= awardMilestone(level, profile, villager, milestone, random, increases);
        }
        changed |= profile.markSkillGrowthTradeLevelAwarded(currentTradeLevel, level.getGameTime());

        if (changed) {
            VillagerProfileSavedData.get(level).setDirty();
            if (player != null) {
                VillagerReputationNetworking.sendProfile(player, villager, profile);
            }
        }

        return new VillagerSkillGrowthResult(previousAwardedLevel, currentTradeLevel, increases);
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
}
