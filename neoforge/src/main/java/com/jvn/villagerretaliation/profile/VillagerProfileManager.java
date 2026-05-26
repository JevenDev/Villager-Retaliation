package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerProfileManager {
    private VillagerProfileManager() {
    }

    public static VillagerProfile getOrCreateProfile(ServerLevel level, AbstractVillager villager) {
        VillagerProfileSavedData data = VillagerProfileSavedData.get(level);
        VillagerProfile profile = data.get(villager.getUUID());
        if (profile == null) {
            profile = VillagerProfileGenerator.generate(level, villager);
            data.put(profile);
            return profile;
        }

        String professionKey = VillagerProfileGenerator.professionKey(villager);
        boolean changed = profile.updateLastKnownProfession(professionKey, level.getGameTime());
        changed |= ensureSkills(profile, professionKey, level.getGameTime());
        if (changed) {
            data.setDirty();
        }
        return profile;
    }

    public static Optional<VillagerProfile> getProfile(ServerLevel level, UUID villagerUuid) {
        return Optional.ofNullable(VillagerProfileSavedData.get(level).get(villagerUuid));
    }

    public static VillagerProfile rerollProfile(ServerLevel level, AbstractVillager villager) {
        VillagerProfile profile = VillagerProfileGenerator.generate(level, villager);
        VillagerProfileSavedData.get(level).put(profile);
        return profile;
    }

    public static boolean setAttribute(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int value) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        boolean changed = profile.setSocialAttribute(attribute, value, level.getGameTime());
        if (changed) {
            VillagerProfileSavedData.get(level).setDirty();
        }
        return changed;
    }

    public static int getSkill(ServerLevel level, AbstractVillager villager, VillagerSkill skill) {
        return getOrCreateProfile(level, villager).skills().get(skill);
    }

    public static VillagerSkillRank getSkillRank(ServerLevel level, AbstractVillager villager, VillagerSkill skill) {
        return getOrCreateProfile(level, villager).skills().rank(skill);
    }

    public static VillagerSkillSet getSkills(ServerLevel level, AbstractVillager villager) {
        return getOrCreateProfile(level, villager).skills();
    }

    public static List<VillagerSkillValue> getBestSkills(ServerLevel level, AbstractVillager villager, int limit) {
        return getSkills(level, villager).best(limit);
    }

    public static VillagerSkill getProfessionPrimarySkill(AbstractVillager villager) {
        return VillagerProfessionSkills.primarySkill(villager);
    }

    public static List<VillagerSkill> getProfessionTradeSkills(AbstractVillager villager) {
        return VillagerProfessionSkills.tradeSkills(villager);
    }

    public static boolean hasSkillAtLeast(ServerLevel level, AbstractVillager villager, VillagerSkill skill, int value) {
        return getSkill(level, villager, skill) >= VillagerSkillSet.clamp(value);
    }

    public static boolean hasSkillRankAtLeast(ServerLevel level, AbstractVillager villager, VillagerSkill skill, VillagerSkillRank rank) {
        return rank != null && getSkill(level, villager, skill) >= rank.minInclusive();
    }

    public static boolean setSkill(ServerLevel level, AbstractVillager villager, VillagerSkill skill, int value) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        boolean changed = profile.setSkill(skill, value, level.getGameTime());
        if (changed) {
            VillagerProfileSavedData.get(level).setDirty();
        }
        return changed;
    }

    public static VillagerProfile rerollSkills(ServerLevel level, AbstractVillager villager) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        String professionKey = VillagerProfileGenerator.professionKey(villager);
        VillagerSkillSet skills = VillagerSkillGenerator.generate(professionKey, profile.socialAttributes(), profile.seed());
        profile.replaceSkills(skills, VillagerSkillGenerator.CURRENT_GENERATION_VERSION, level.getGameTime());
        VillagerProfileSavedData.get(level).setDirty();
        return profile;
    }

    public static String exportProfile(VillagerProfile profile) {
        VillagerSocialAttributes attributes = profile.socialAttributes();
        VillagerSkillSet skills = profile.skills();
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"villagerUuid\":\"").append(profile.villagerUuid()).append("\",");
        builder.append("\"generatedVersion\":").append(profile.generatedVersion()).append(",");
        builder.append("\"seed\":").append(profile.seed()).append(",");
        builder.append("\"lastKnownProfession\":\"").append(escape(profile.lastKnownProfession())).append("\",");
        builder.append("\"socialAttributes\":{");
        builder.append("\"knowledge\":").append(attributes.knowledge()).append(",");
        builder.append("\"guts\":").append(attributes.guts()).append(",");
        builder.append("\"proficiency\":").append(attributes.proficiency()).append(",");
        builder.append("\"kindness\":").append(attributes.kindness()).append(",");
        builder.append("\"charm\":").append(attributes.charm());
        builder.append("},");
        builder.append("\"skillGeneratedVersion\":").append(profile.skillGeneratedVersion()).append(",");
        builder.append("\"skills\":{");
        boolean first = true;
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(skill.serializedName()).append("\":").append(skills.get(skill));
        }
        builder.append("},");
        builder.append("\"createdGameTime\":").append(profile.createdGameTime()).append(",");
        builder.append("\"updatedGameTime\":").append(profile.updatedGameTime());
        builder.append("}");
        return builder.toString();
    }

    public static String displayLine(VillagerProfile profile, boolean exactValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("Profile ")
                .append(profile.villagerUuid())
                .append(" [")
                .append(profile.lastKnownProfession())
                .append("]");
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            int value = profile.socialAttributes().get(attribute);
            VillagerSocialAttributeRank rank = VillagerSocialAttributeRank.fromValue(value);
            builder.append(" ")
                    .append(titleCase(attribute.serializedName()))
                    .append("=")
                    .append(titleCase(rank.serializedName()));
            if (exactValues) {
                builder.append("(").append(value).append(")");
            }
        }
        return builder.toString();
    }

    public static String skillDisplayLine(VillagerProfile profile, boolean exactValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("Skills ")
                .append(profile.villagerUuid())
                .append(" [")
                .append(profile.lastKnownProfession())
                .append("]");
        for (VillagerSkillValue skillValue : profile.skills().best(8)) {
            VillagerSkillRank rank = skillValue.rank();
            builder.append(" ")
                    .append(titleCase(skillValue.skill().serializedName()))
                    .append("=")
                    .append(titleCase(rank.serializedName()));
            if (exactValues) {
                builder.append("(").append(skillValue.value()).append(")");
            }
        }
        return builder.toString();
    }

    public static String skillDisplayLine(VillagerProfile profile, VillagerSkill skill, boolean exactValues) {
        int value = profile.skills().get(skill);
        VillagerSkillRank rank = VillagerSkillRank.fromValue(value);
        StringBuilder builder = new StringBuilder();
        builder.append(titleCase(skill.serializedName()))
                .append("=")
                .append(titleCase(rank.serializedName()));
        if (exactValues) {
            builder.append("(").append(value).append(")");
        }
        return builder.toString();
    }

    private static boolean ensureSkills(VillagerProfile profile, String professionKey, long gameTime) {
        if (!profile.needsSkillGeneration()) {
            return false;
        }
        VillagerSkillSet generated = VillagerSkillGenerator.generate(professionKey, profile.socialAttributes(), profile.seed());
        return profile.replaceSkills(generated, VillagerSkillGenerator.CURRENT_GENERATION_VERSION, gameTime);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (String part : lower.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
