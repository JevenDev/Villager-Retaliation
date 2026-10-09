package com.jvn.villagerretaliation.skill.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record SocialCheckBreakdown(ResourceLocation progression, int revision, String fingerprint, PlayerSocialSkill skill,
        long experience, int level, int threshold, int bonus, int rewardExperience) {
    public SocialCheckBreakdown {
        if (progression == null || revision < 1 || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || skill == null
                || experience < 0 || level < 0 || level >= 1024 || threshold < 0 || threshold > experience
                || (level == 0) != (threshold == 0) || bonus < 0 || bonus > SocialProgressionSchema.MAX_BONUS || rewardExperience < 0) {
            throw new IllegalArgumentException("Invalid saved social check breakdown");
        }
    }

    public static SocialCheckBreakdown capture(SocialProgressionDefinition definition, PlayerSocialSkill skill, long experience, int reward) {
        var rank = definition.rank(skill, experience);
        return new SocialCheckBreakdown(definition.id(), definition.revision(), definition.fingerprint(), skill,
                experience, rank.level(), rank.threshold(), rank.bonus(), reward);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Progression", progression.toString());
        tag.putInt("Revision", revision);
        tag.putString("Fingerprint", fingerprint);
        tag.putString("Skill", skill.id());
        tag.putLong("Experience", experience);
        tag.putInt("Level", level);
        tag.putInt("Threshold", threshold);
        tag.putInt("Bonus", bonus);
        tag.putInt("RewardExperience", rewardExperience);
        return tag;
    }

    public static SocialCheckBreakdown load(CompoundTag tag) {
        for (var field : java.util.List.of("Version", "Revision", "Level", "Threshold", "Bonus", "RewardExperience"))
            if (!tag.contains(field, Tag.TAG_INT)) throw new IllegalArgumentException("Missing social check number: " + field);
        for (var field : java.util.List.of("Progression", "Fingerprint", "Skill"))
            if (!tag.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Missing social check text: " + field);
        if (tag.getInt("Version") != 1 || !tag.contains("Experience", Tag.TAG_LONG))
            throw new IllegalArgumentException("Unsupported social check breakdown");
        return new SocialCheckBreakdown(ResourceLocation.tryParse(tag.getString("Progression")), tag.getInt("Revision"),
                tag.getString("Fingerprint"), PlayerSocialSkill.parse(tag.getString("Skill")), tag.getLong("Experience"),
                tag.getInt("Level"), tag.getInt("Threshold"), tag.getInt("Bonus"), tag.getInt("RewardExperience"));
    }
}
