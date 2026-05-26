package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class VillagerProfileGenerator {
    private static final int BASE_MIN = 35;
    private static final int BASE_MAX = 65;
    private static final int VARIANCE = 5;
    private static final double OUTLIER_CHANCE = 0.08D;

    private VillagerProfileGenerator() {
    }

    public static VillagerProfile generate(ServerLevel level, AbstractVillager villager) {
        return generate(level, villager, List.of());
    }

    public static VillagerProfile generate(ServerLevel level, AbstractVillager villager, List<VillagerProfile> parentProfiles) {
        String professionKey = professionKey(villager);
        long seed = seed(level, villager.getUUID());
        RandomSource random = RandomSource.create(seed);
        VillagerSocialAttributes generated = generatedAttributes(professionKey, random);
        VillagerSocialAttributes inherited = blendParents(generated, parentProfiles, random);
        VillagerSkillSet skills = VillagerSkillGenerator.generate(professionKey, inherited, seed);
        return VillagerProfile.create(
                villager.getUUID(),
                VillagerProfile.CURRENT_GENERATION_VERSION,
                seed,
                inherited,
                VillagerSkillGenerator.CURRENT_GENERATION_VERSION,
                skills,
                professionKey,
                level.getGameTime()
        );
    }

    public static String professionKey(AbstractVillager villager) {
        if (villager instanceof Villager villageResident) {
            return VillagerProfessionUtil.serializedKey(villageResident.getVillagerData().getProfession());
        }
        if (villager instanceof WanderingTrader) {
            return "wandering_trader";
        }
        return "none";
    }

    private static VillagerSocialAttributes generatedAttributes(String professionKey, RandomSource random) {
        EnumMap<VillagerSocialAttribute, Integer> values = new EnumMap<>(VillagerSocialAttribute.class);
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            values.put(attribute, BASE_MIN + random.nextInt(BASE_MAX - BASE_MIN + 1));
        }

        applyProfessionBias(values, professionKey);
        applyVariance(values, random);
        applyOutliers(values, random);
        return fromMap(values);
    }

    private static VillagerSocialAttributes blendParents(
            VillagerSocialAttributes generated,
            List<VillagerProfile> parentProfiles,
            RandomSource random) {
        if (parentProfiles == null || parentProfiles.isEmpty()) {
            return generated;
        }

        List<VillagerSocialAttributes> parentAttributes = parentProfiles.stream()
                .filter(profile -> profile != null && profile.socialAttributes() != null)
                .map(VillagerProfile::socialAttributes)
                .toList();
        if (parentAttributes.isEmpty()) {
            return generated;
        }

        EnumMap<VillagerSocialAttribute, Integer> values = new EnumMap<>(VillagerSocialAttribute.class);
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            int parentAverage = Mth.floor(parentAttributes.stream()
                    .mapToInt(attributes -> attributes.get(attribute))
                    .average()
                    .orElse(generated.get(attribute)));
            int environmentalRoll = BASE_MIN + random.nextInt(BASE_MAX - BASE_MIN + 1);
            int value = Math.round(parentAverage * 0.55F + generated.get(attribute) * 0.30F + environmentalRoll * 0.15F);
            value += random.nextInt(13) - 6;
            values.put(attribute, VillagerSocialAttributes.clamp(value));
        }
        return fromMap(values);
    }

    private static void applyProfessionBias(EnumMap<VillagerSocialAttribute, Integer> values, String professionKey) {
        switch (professionKey) {
            case "librarian" -> bias(values,
                    Map.of(VillagerSocialAttribute.KNOWLEDGE, 16, VillagerSocialAttribute.CHARM, 6));
            case "cartographer" -> bias(values,
                    Map.of(VillagerSocialAttribute.KNOWLEDGE, 14, VillagerSocialAttribute.PROFICIENCY, 8));
            case "cleric" -> bias(values,
                    Map.of(VillagerSocialAttribute.KINDNESS, 11, VillagerSocialAttribute.KNOWLEDGE, 8, VillagerSocialAttribute.GUTS, 5));
            case "armorer" -> bias(values,
                    Map.of(VillagerSocialAttribute.GUTS, 13, VillagerSocialAttribute.PROFICIENCY, 10));
            case "weaponsmith" -> bias(values,
                    Map.of(VillagerSocialAttribute.GUTS, 14, VillagerSocialAttribute.PROFICIENCY, 11));
            case "toolsmith" -> bias(values,
                    Map.of(VillagerSocialAttribute.PROFICIENCY, 15, VillagerSocialAttribute.GUTS, 6));
            case "fletcher" -> bias(values,
                    Map.of(VillagerSocialAttribute.PROFICIENCY, 13, VillagerSocialAttribute.KNOWLEDGE, 5));
            case "mason" -> bias(values,
                    Map.of(VillagerSocialAttribute.PROFICIENCY, 12, VillagerSocialAttribute.KNOWLEDGE, 7));
            case "leatherworker" -> bias(values,
                    Map.of(VillagerSocialAttribute.PROFICIENCY, 10, VillagerSocialAttribute.CHARM, 8));
            case "fisherman" -> bias(values,
                    Map.of(VillagerSocialAttribute.PROFICIENCY, 11, VillagerSocialAttribute.GUTS, 7));
            case "farmer" -> bias(values,
                    Map.of(VillagerSocialAttribute.KINDNESS, 10, VillagerSocialAttribute.GUTS, 6));
            case "shepherd" -> bias(values,
                    Map.of(VillagerSocialAttribute.KINDNESS, 10, VillagerSocialAttribute.CHARM, 9));
            case "butcher" -> bias(values,
                    Map.of(VillagerSocialAttribute.GUTS, 10, VillagerSocialAttribute.CHARM, 6));
            case "wandering_trader" -> bias(values,
                    Map.of(VillagerSocialAttribute.CHARM, 15, VillagerSocialAttribute.KNOWLEDGE, 11, VillagerSocialAttribute.GUTS, 5));
            case "nitwit" -> {
            }
            default -> bias(values,
                    Map.of(VillagerSocialAttribute.KNOWLEDGE, 3, VillagerSocialAttribute.PROFICIENCY, 3));
        }
    }

    private static void bias(EnumMap<VillagerSocialAttribute, Integer> values, Map<VillagerSocialAttribute, Integer> biases) {
        for (Map.Entry<VillagerSocialAttribute, Integer> entry : biases.entrySet()) {
            values.put(entry.getKey(), VillagerSocialAttributes.clamp(values.get(entry.getKey()) + entry.getValue()));
        }
    }

    private static void applyVariance(EnumMap<VillagerSocialAttribute, Integer> values, RandomSource random) {
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            int value = values.get(attribute) + random.nextInt(VARIANCE * 2 + 1) - VARIANCE;
            values.put(attribute, VillagerSocialAttributes.clamp(value));
        }
    }

    private static void applyOutliers(EnumMap<VillagerSocialAttribute, Integer> values, RandomSource random) {
        if (random.nextDouble() >= OUTLIER_CHANCE) {
            return;
        }

        VillagerSocialAttribute attribute = VillagerSocialAttribute.values()[random.nextInt(VillagerSocialAttribute.values().length)];
        int direction = random.nextBoolean() ? 1 : -1;
        int amount = 15 + random.nextInt(11);
        values.put(attribute, VillagerSocialAttributes.clamp(values.get(attribute) + direction * amount));
    }

    private static VillagerSocialAttributes fromMap(EnumMap<VillagerSocialAttribute, Integer> values) {
        return new VillagerSocialAttributes(
                values.get(VillagerSocialAttribute.KNOWLEDGE),
                values.get(VillagerSocialAttribute.GUTS),
                values.get(VillagerSocialAttribute.PROFICIENCY),
                values.get(VillagerSocialAttribute.KINDNESS),
                values.get(VillagerSocialAttribute.CHARM)
        );
    }

    private static long seed(ServerLevel level, UUID villagerUuid) {
        long value = level.getSeed();
        value ^= villagerUuid.getMostSignificantBits();
        value = mix(value);
        value ^= villagerUuid.getLeastSignificantBits();
        value = mix(value);
        value ^= VillagerProfile.CURRENT_GENERATION_VERSION * 0x9E3779B97F4A7C15L;
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
