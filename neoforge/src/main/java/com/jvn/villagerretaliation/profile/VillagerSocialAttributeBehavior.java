package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.mood.VillagerMood;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerSocialAttributeBehavior {
    public static final int BASELINE_VALUE = 50;

    private VillagerSocialAttributeBehavior() {
    }

    public static boolean enabled(VillagerRetaliationConfig.ConfigValue<Boolean> featureToggle) {
        return VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_BEHAVIOR.get()
                && (featureToggle == null || featureToggle.get());
    }

    public static int value(ServerLevel level, AbstractVillager villager, VillagerSocialAttribute attribute) {
        if (attribute == null) {
            return BASELINE_VALUE;
        }
        if (level == null || villager == null) {
            return VillagerSocialAttributes.DEFAULT.get(attribute);
        }
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        return profile == null || profile.socialAttributes() == null
                ? VillagerSocialAttributes.DEFAULT.get(attribute)
                : profile.socialAttributes().get(attribute);
    }

    public static int scaledOffset(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int maxAbsoluteOffset) {
        return scaledOffset(
                level,
                villager,
                attribute,
                maxAbsoluteOffset,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_BEHAVIOR
        );
    }

    public static int scaledOffset(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int maxAbsoluteOffset,
            VillagerRetaliationConfig.ConfigValue<Boolean> featureToggle) {
        if (!enabled(featureToggle) || maxAbsoluteOffset <= 0) {
            return 0;
        }

        double centered = (value(level, villager, attribute) - BASELINE_VALUE) / (double) BASELINE_VALUE;
        int offset = (int) Math.round(centered * maxAbsoluteOffset * VillagerRetaliationConfig.SOCIAL_ATTRIBUTE_EFFECT_SCALE.get());
        return Math.clamp(offset, -maxAbsoluteOffset, maxAbsoluteOffset);
    }

    public static int positiveBonus(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int maxBonus,
            VillagerRetaliationConfig.ConfigValue<Boolean> featureToggle) {
        return Math.max(0, scaledOffset(level, villager, attribute, maxBonus, featureToggle));
    }

    public static int adjustMoodIntensity(ServerLevel level, AbstractVillager villager, VillagerMood mood, int baseIntensity) {
        if (!enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS)) {
            return Math.clamp(baseIntensity, 0, 100);
        }

        int offset = switch (mood) {
            case GRATEFUL -> scaledOffset(level, villager, VillagerSocialAttribute.KINDNESS, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS)
                    + scaledOffset(level, villager, VillagerSocialAttribute.CHARM, 4,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case CONTENT, HOPEFUL -> scaledOffset(level, villager, VillagerSocialAttribute.KINDNESS, 5,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case AFRAID -> -scaledOffset(level, villager, VillagerSocialAttribute.GUTS, 10,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS)
                    - scaledOffset(level, villager, VillagerSocialAttribute.PROFICIENCY, 4,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case ANGRY, PROTECTIVE -> scaledOffset(level, villager, VillagerSocialAttribute.GUTS, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case SUSPICIOUS -> scaledOffset(level, villager, VillagerSocialAttribute.KNOWLEDGE, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case GRIEVING -> scaledOffset(level, villager, VillagerSocialAttribute.KINDNESS, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case STRESSED -> -scaledOffset(level, villager, VillagerSocialAttribute.PROFICIENCY, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS)
                    - scaledOffset(level, villager, VillagerSocialAttribute.GUTS, 4,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case PROUD -> scaledOffset(level, villager, VillagerSocialAttribute.PROFICIENCY, 8,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS)
                    + scaledOffset(level, villager, VillagerSocialAttribute.GUTS, 5,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case LONELY -> scaledOffset(level, villager, VillagerSocialAttribute.KINDNESS, 4,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case NEUTRAL -> 0;
        };
        return Math.clamp(baseIntensity + offset, 0, 100);
    }

    public static long adjustMoodDecay(ServerLevel level, AbstractVillager villager, VillagerMood mood, long baseDecayTicks) {
        if (!enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS) || baseDecayTicks <= 1L) {
            return Math.max(1L, baseDecayTicks);
        }

        int basisPointOffset = switch (mood) {
            case AFRAID -> -positiveBonus(level, villager, VillagerSocialAttribute.GUTS, 2500,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case GRATEFUL, CONTENT, HOPEFUL -> positiveBonus(level, villager, VillagerSocialAttribute.KINDNESS, 1800,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case SUSPICIOUS -> positiveBonus(level, villager, VillagerSocialAttribute.KNOWLEDGE, 1800,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case GRIEVING -> positiveBonus(level, villager, VillagerSocialAttribute.KINDNESS, 1500,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case PROTECTIVE, PROUD -> positiveBonus(level, villager, VillagerSocialAttribute.GUTS, 1500,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case STRESSED -> -positiveBonus(level, villager, VillagerSocialAttribute.PROFICIENCY, 1500,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS);
            case ANGRY, LONELY, NEUTRAL -> 0;
        };
        int basisPoints = Math.clamp(10_000 + basisPointOffset, 2_500, 15_000);
        return Math.max(1L, Math.round(baseDecayTicks * (basisPoints / 10_000.0D)));
    }

    public static int adjustCombatCooldownTicks(ServerLevel level, AbstractVillager villager, int baseCooldownTicks) {
        if (!enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS)) {
            return baseCooldownTicks;
        }

        int reduction = positiveBonus(
                level,
                villager,
                VillagerSocialAttribute.PROFICIENCY,
                4,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS
        );
        return Math.max(5, baseCooldownTicks - reduction);
    }

    public static float adjustRangedInaccuracy(ServerLevel level, AbstractVillager villager, float baseInaccuracy) {
        if (!enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS)) {
            return baseInaccuracy;
        }

        int proficiencyOffset = scaledOffset(
                level,
                villager,
                VillagerSocialAttribute.PROFICIENCY,
                20,
                VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS
        );
        float multiplier = Math.clamp(1.0F - proficiencyOffset / 100.0F, 0.8F, 1.2F);
        return Math.max(0.0F, baseInaccuracy * multiplier);
    }

    public static boolean canBravelyStandGround(ServerLevel level, AbstractVillager villager) {
        if (!enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS)) {
            return false;
        }
        return value(level, villager, VillagerSocialAttribute.GUTS) >= 72
                && value(level, villager, VillagerSocialAttribute.PROFICIENCY) >= 45
                && villager.getHealth() >= villager.getMaxHealth() * 0.6F;
    }
}
