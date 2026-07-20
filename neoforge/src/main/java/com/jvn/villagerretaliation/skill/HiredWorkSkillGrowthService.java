package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoleSettings;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class HiredWorkSkillGrowthService {
    static final String LEGACY_PROGRESS_TAG = "HiredWorkSkillGrowthProgress";

    private HiredWorkSkillGrowthService() {
    }

    public static VillagerSkillProgressionResult onPractice(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredVillagerRole role,
            CompoundTag workState,
            List<VillagerSkillPractice> practice) {
        if (level == null || villager == null || villager.isBaby() || role == null || workState == null) {
            return VillagerSkillProgressionResult.NONE;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        boolean migrated = migrateLegacyProgress(profile, workState, level.getGameTime());
        if (!VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.get()
                || practice == null
                || practice.isEmpty()) {
            if (migrated) {
                VillagerProfileSavedData.get(level).setDirty();
            }
            return VillagerSkillProgressionResult.NONE;
        }

        double xpPerUnit = HiredVillagerRoleSettings.skillGrowthAmount(role);
        if (!Double.isFinite(xpPerUnit) || xpPerUnit <= 0.0D) {
            return VillagerSkillProgressionResult.NONE;
        }
        long dayIndex = Math.floorDiv(level.getServer().overworld().getDayTime(), 24_000L);
        VillagerSkillProgressionResult result = VillagerSkillProgressionService.apply(
                profile, normalizeRolePractice(role, practice), dayIndex, level.getGameTime(), xpPerUnit);
        if (!migrated && !result.profileChanged()) {
            return result;
        }

        VillagerProfileSavedData.get(level).setDirty();
        if (hirer != null && result.increased()) {
            VillagerReputationNetworking.sendProfile(hirer, villager, profile);
            sendFeedback(hirer, villager, result.increases().getFirst().skill());
        }
        return result;
    }

    static List<VillagerSkillPractice> normalizeRolePractice(
            HiredVillagerRole role,
            List<VillagerSkillPractice> practice) {
        if (role == null || practice == null || practice.isEmpty()) {
            return List.of();
        }
        Map<PracticeKey, Double> groupedUnits = new LinkedHashMap<>();
        for (VillagerSkillPractice entry : practice) {
            if (entry == null) {
                continue;
            }
            PracticeKey key = new PracticeKey(entry.source(), entry.repetitionKey());
            groupedUnits.merge(key, entry.units(), Double::sum);
        }
        List<VillagerSkillPractice> normalized = new ArrayList<>(groupedUnits.size() * 2);
        for (Map.Entry<PracticeKey, Double> entry : groupedUnits.entrySet()) {
            double totalUnits = entry.getValue();
            if (!Double.isFinite(totalUnits) || totalUnits <= 0.0D) {
                continue;
            }
            normalized.add(new VillagerSkillPractice(
                    HiredVillagerRoles.primarySkill(role),
                    totalUnits * HiredVillagerRoles.PRIMARY_SKILL_WEIGHT,
                    entry.getKey().source(),
                    entry.getKey().repetitionKey()));
            normalized.add(new VillagerSkillPractice(
                    HiredVillagerRoles.supportSkill(role),
                    totalUnits * HiredVillagerRoles.SUPPORT_SKILL_WEIGHT,
                    entry.getKey().source(),
                    entry.getKey().repetitionKey()));
        }
        return List.copyOf(normalized);
    }

    private record PracticeKey(String source, long repetitionKey) {
    }

    static boolean migrateLegacyProgress(VillagerProfile profile, CompoundTag workState, long gameTime) {
        if (!workState.contains(LEGACY_PROGRESS_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag legacy = workState.getCompound(LEGACY_PROGRESS_TAG);
        boolean changed = false;
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (!legacy.contains(skill.serializedName(), Tag.TAG_DOUBLE)) {
                continue;
            }
            double oldFraction = legacy.getDouble(skill.serializedName());
            if (!Double.isFinite(oldFraction) || oldFraction <= 0.0D) {
                continue;
            }
            double preservedXp = Math.min(0.999_999D, oldFraction)
                    * VillagerSkillProgressionService.requiredXp(profile.skills().get(skill));
            changed |= profile.setSkillPracticeXp(
                    skill, profile.skillPracticeXp(skill) + preservedXp, gameTime);
        }
        workState.remove(LEGACY_PROGRESS_TAG);
        return true;
    }

    private static void sendFeedback(ServerPlayer player, Villager villager, VillagerSkill skill) {
        if (VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FEEDBACK.get()) {
            player.displayClientMessage(
                    Component.translatable(
                            "villagerretaliation.skill_growth.improved",
                            VillagerPresetNameRegistry.resolveDisplayName(villager),
                            Component.translatable(skill.translationKey())),
                    true);
        }
    }
}
