package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class HiredWorkSkillGrowthService {
    private static final String PROGRESS_TAG = "HiredWorkSkillGrowthProgress";
    private static final double PROGRESS_EPSILON = 0.000_001D;

    private HiredWorkSkillGrowthService() {
    }

    public static void onWorkCompleted(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredVillagerRole role,
            CompoundTag workState) {
        if (!VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.get()
                || villager == null
                || villager.isBaby()
                || role == null
                || workState == null) {
            return;
        }

        double amount = roleGrowthAmount(role);
        if (amount <= 0.0D) {
            return;
        }

        List<VillagerSkill> skills = HiredVillagerRoles.roleSkills(role);
        if (skills.isEmpty()) {
            return;
        }

        VillagerSkill primary = skills.getFirst();
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerSkill improvedSkill = awardProgress(level, profile, workState, primary, amount) ? primary : null;

        if (skills.size() > 1 && villager.getRandom().nextDouble() < VillagerRetaliationConfig.SKILL_GROWTH_SECONDARY_CHANCE.get()) {
            VillagerSkill secondary = skills.get(1 + villager.getRandom().nextInt(skills.size() - 1));
            if (awardProgress(level, profile, workState, secondary, amount * 0.5D) && improvedSkill == null) {
                improvedSkill = secondary;
            }
        }

        if (improvedSkill == null) {
            return;
        }

        VillagerProfileSavedData.get(level).setDirty();
        if (hirer != null) {
            VillagerReputationNetworking.sendProfile(hirer, villager, profile);
            sendFeedback(hirer, villager, improvedSkill);
        }
    }

    private static boolean awardProgress(
            ServerLevel level,
            VillagerProfile profile,
            CompoundTag workState,
            VillagerSkill skill,
            double amount) {
        if (skill == null || amount <= 0.0D) {
            return false;
        }

        int oldValue = profile.skills().get(skill);
        if (oldValue >= VillagerSkillSet.MAX_VALUE) {
            progressTag(workState).remove(skill.serializedName());
            return false;
        }

        CompoundTag progressTag = progressTag(workState);
        double totalProgress = progressTag.getDouble(skill.serializedName()) + amount;
        int wholePoints = (int) Math.floor(totalProgress + PROGRESS_EPSILON);
        double remainingProgress = Math.max(0.0D, totalProgress - wholePoints);
        int awardedPoints = Math.min(wholePoints, VillagerSkillSet.MAX_VALUE - oldValue);

        if (oldValue + awardedPoints >= VillagerSkillSet.MAX_VALUE) {
            remainingProgress = 0.0D;
        }

        if (remainingProgress <= PROGRESS_EPSILON) {
            progressTag.remove(skill.serializedName());
        } else {
            progressTag.putDouble(skill.serializedName(), Math.min(0.999_999D, remainingProgress));
        }

        if (awardedPoints <= 0) {
            return false;
        }

        return profile.setSkill(skill, oldValue + awardedPoints, level.getGameTime());
    }

    private static CompoundTag progressTag(CompoundTag workState) {
        if (!workState.contains(PROGRESS_TAG, Tag.TAG_COMPOUND)) {
            workState.put(PROGRESS_TAG, new CompoundTag());
        }
        return workState.getCompound(PROGRESS_TAG);
    }

    private static double roleGrowthAmount(HiredVillagerRole role) {
        double amount = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_FARMING.get();
            case FISHING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_NAVIGATION.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_BREWING.get();
            case BUILDER -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_BUILDER.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_NITWIT.get();
        };
        return Math.max(0.0D, amount);
    }

    private static void sendFeedback(ServerPlayer player, Villager villager, VillagerSkill skill) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FEEDBACK.get()) {
            return;
        }

        player.displayClientMessage(
                Component.translatable(
                        "villagerretaliation.skill_growth.improved",
                        VillagerPresetNameRegistry.resolveDisplayName(villager),
                        Component.translatable(skill.translationKey())),
                true);
    }
}
