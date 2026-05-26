package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;

public final class VillagerSkillGenerator {
    public static final int CURRENT_GENERATION_VERSION = 1;

    private static final int UNRELATED_MIN = 8;
    private static final int UNRELATED_MAX = 35;
    private static final int SECONDARY_MIN = 26;
    private static final int SECONDARY_MAX = 58;
    private static final int PRIMARY_MIN = 44;
    private static final int PRIMARY_MAX = 72;
    private static final int VARIANCE = 7;
    private static final double OUTLIER_CHANCE = 0.14D;
    private static final long SKILL_SEED_SALT = 0x51A7E12B9D38C47FL;

    private VillagerSkillGenerator() {
    }

    public static VillagerSkillSet generate(String professionKey, VillagerSocialAttributes attributes, long profileSeed) {
        String safeProfessionKey = professionKey == null || professionKey.isBlank() ? "none" : professionKey;
        RandomSource random = RandomSource.create(skillSeed(profileSeed, safeProfessionKey));
        EnumMap<VillagerSkill, Integer> values = new EnumMap<>(VillagerSkill.class);
        if (safeProfessionKey.equals("nitwit")) {
            generateNitwit(values, random);
        } else {
            generateProfessionSpread(values, safeProfessionKey, random);
        }
        applySocialAttributeInfluence(values, attributes == null ? VillagerSocialAttributes.DEFAULT : attributes);
        applyVariance(values, random);
        applyOutlier(values, random);
        return VillagerSkillSet.of(values);
    }

    private static void generateProfessionSpread(
            EnumMap<VillagerSkill, Integer> values,
            String professionKey,
            RandomSource random) {
        for (VillagerSkill skill : VillagerSkill.values()) {
            values.put(skill, roll(random, UNRELATED_MIN, UNRELATED_MAX));
        }

        List<VillagerSkill> tradeSkills = VillagerProfessionSkills.tradeSkills(professionKey);
        VillagerSkill primary = VillagerProfessionSkills.primarySkill(professionKey);
        for (VillagerSkill skill : tradeSkills) {
            int min = skill == primary ? PRIMARY_MIN : SECONDARY_MIN;
            int max = skill == primary ? PRIMARY_MAX : SECONDARY_MAX;
            values.put(skill, Math.max(values.get(skill), roll(random, min, max)));
        }

        if (professionKey.equals("wandering_trader")) {
            boost(values, Map.of(
                    VillagerSkill.TRADING, 12,
                    VillagerSkill.DIPLOMACY, 8,
                    VillagerSkill.SURVIVAL, 7,
                    VillagerSkill.CARTOGRAPHY, 5
            ));
        }
    }

    private static void generateNitwit(EnumMap<VillagerSkill, Integer> values, RandomSource random) {
        for (VillagerSkill skill : VillagerSkill.values()) {
            values.put(skill, roll(random, 5, 55));
        }

        int standoutCount = 1 + random.nextInt(3);
        for (int index = 0; index < standoutCount; index++) {
            VillagerSkill skill = VillagerSkill.values()[random.nextInt(VillagerSkill.values().length)];
            values.put(skill, Math.max(values.get(skill), roll(random, 50, 88)));
        }
    }

    private static void applySocialAttributeInfluence(
            EnumMap<VillagerSkill, Integer> values,
            VillagerSocialAttributes attributes) {
        applyAttribute(values, attributes.get(VillagerSocialAttribute.KNOWLEDGE), List.of(
                VillagerSkill.SCHOLARSHIP,
                VillagerSkill.CARTOGRAPHY,
                VillagerSkill.MEDICINE,
                VillagerSkill.TRADING
        ));
        applyAttribute(values, attributes.get(VillagerSocialAttribute.GUTS), List.of(
                VillagerSkill.GUARDING,
                VillagerSkill.SURVIVAL,
                VillagerSkill.ARCHERY
        ));
        applyAttribute(values, attributes.get(VillagerSocialAttribute.PROFICIENCY), List.of(
                VillagerSkill.CRAFTING,
                VillagerSkill.SMITHING,
                VillagerSkill.FARMING,
                VillagerSkill.FISHING,
                VillagerSkill.MASONRY,
                VillagerSkill.LEATHERWORKING
        ));
        applyAttribute(values, attributes.get(VillagerSocialAttribute.KINDNESS), List.of(
                VillagerSkill.MEDICINE,
                VillagerSkill.ANIMAL_HANDLING,
                VillagerSkill.COOKING,
                VillagerSkill.DIPLOMACY
        ));
        applyAttribute(values, attributes.get(VillagerSocialAttribute.CHARM), List.of(
                VillagerSkill.TRADING,
                VillagerSkill.DIPLOMACY,
                VillagerSkill.SCHOLARSHIP
        ));
    }

    private static void applyAttribute(EnumMap<VillagerSkill, Integer> values, int attributeValue, List<VillagerSkill> skills) {
        int modifier = Math.round((attributeValue - 50) / 5.0F);
        if (modifier == 0) {
            return;
        }
        for (VillagerSkill skill : skills) {
            values.put(skill, VillagerSkillSet.clamp(values.get(skill) + modifier));
        }
    }

    private static void boost(EnumMap<VillagerSkill, Integer> values, Map<VillagerSkill, Integer> boosts) {
        for (Map.Entry<VillagerSkill, Integer> entry : boosts.entrySet()) {
            values.put(entry.getKey(), VillagerSkillSet.clamp(values.get(entry.getKey()) + entry.getValue()));
        }
    }

    private static void applyVariance(EnumMap<VillagerSkill, Integer> values, RandomSource random) {
        for (VillagerSkill skill : VillagerSkill.values()) {
            int value = values.get(skill) + random.nextInt(VARIANCE * 2 + 1) - VARIANCE;
            values.put(skill, VillagerSkillSet.clamp(value));
        }
    }

    private static void applyOutlier(EnumMap<VillagerSkill, Integer> values, RandomSource random) {
        if (random.nextDouble() >= OUTLIER_CHANCE) {
            return;
        }

        VillagerSkill skill = VillagerSkill.values()[random.nextInt(VillagerSkill.values().length)];
        int amount = 18 + random.nextInt(23);
        values.put(skill, VillagerSkillSet.clamp(values.get(skill) + amount));
    }

    private static int roll(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private static long skillSeed(long profileSeed, String professionKey) {
        long value = profileSeed ^ SKILL_SEED_SALT;
        value ^= (long) CURRENT_GENERATION_VERSION * 0x9E3779B97F4A7C15L;
        value ^= professionKey.hashCode() * 0xC2B2AE3D27D4EB4FL;
        return mix(value);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
