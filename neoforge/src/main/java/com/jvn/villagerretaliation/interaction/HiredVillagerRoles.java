package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class HiredVillagerRoles {
    public static final int PREFERRED_PROFESSION_THRESHOLD = 35;
    public static final int NONPREFERRED_PROFESSION_THRESHOLD = 55;
    private static final Map<HiredVillagerRole, RoleDefinition> DEFINITIONS = definitions();

    private HiredVillagerRoles() {
    }

    public static List<HiredVillagerRole> availableRoles(ServerLevel level, Villager villager) {
        if (villager == null || villager.isBaby()) {
            return List.of();
        }
        List<HiredVillagerRole> roles = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            if (isSkillUnlocked(level, villager, role)) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }

    public static List<HiredVillagerRole> availableContractRoles(ServerLevel level, Villager villager) {
        return availableRoles(level, villager).stream()
                .filter(role -> role != HiredVillagerRole.BUILDER)
                .toList();
    }

    public static boolean canOfferBuilderService(ServerLevel level, Villager villager) {
        return availableRoles(level, villager).contains(HiredVillagerRole.BUILDER);
    }

    public static HiredVillagerRole defaultRole(ServerLevel level, Villager villager) {
        HiredVillagerRole bestRole = null;
        int bestScore = -1;
        for (HiredVillagerRole role : availableContractRoles(level, villager)) {
            int score = roleScore(level, villager, role);
            if (score > bestScore) {
                bestRole = role;
                bestScore = score;
            }
        }
        return bestRole;
    }

    public static int bestRoleScore(ServerLevel level, Villager villager) {
        int best = 0;
        for (HiredVillagerRole role : availableContractRoles(level, villager)) {
            best = Math.max(best, roleScore(level, villager, role));
        }
        return best;
    }

    /** Weighted score: 70% primary skill, with 30% divided evenly among support skills. */
    public static int roleScore(ServerLevel level, Villager villager, HiredVillagerRole role) {
        RoleDefinition definition = definition(role);
        double score = 0.0D;
        for (Map.Entry<VillagerSkill, Double> weight : definition.skillWeights().entrySet()) {
            score += VillagerProfileManager.getSkill(level, villager, weight.getKey()) * weight.getValue();
        }
        return Math.clamp((int) Math.round(score), 0, 100);
    }

    public static List<VillagerSkill> roleSkills(HiredVillagerRole role) {
        return List.copyOf(definition(role).skillWeights().keySet());
    }

    public static VillagerSkill primarySkill(HiredVillagerRole role) {
        return definition(role).primarySkill();
    }

    public static RoleDefinition definition(HiredVillagerRole role) {
        RoleDefinition definition = DEFINITIONS.get(role);
        if (definition == null) {
            throw new IllegalArgumentException("Missing hired role definition for " + role);
        }
        return definition;
    }

    public static boolean isSkillUnlocked(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return isSkillUnlocked(villager, role, roleScore(level, villager, role));
    }

    public static boolean isSkillUnlocked(Villager villager, HiredVillagerRole role, int roleScore) {
        if (villager == null || role == null || !isProfessionEligible(villager, definition(role))) {
            return false;
        }
        RoleDefinition definition = definition(role);
        if (definition.universallyAvailable()) {
            return true;
        }
        return roleScore >= eligibilityThreshold(villager, role);
    }

    public static int eligibilityThreshold(Villager villager, HiredVillagerRole role) {
        RoleDefinition definition = definition(role);
        return isProfessionPreferred(villager, role)
                ? definition.preferredProfessionThreshold()
                : definition.nonpreferredThreshold();
    }

    public static boolean isProfessionPreferred(Villager villager, HiredVillagerRole role) {
        return preferredRoles(villager).contains(role);
    }

    public static String roleSummary(ServerLevel level, Villager villager) {
        return String.join(", ", availableContractRoles(level, villager).stream()
                .map(HiredVillagerRole::label)
                .toList());
    }

    private static Map<HiredVillagerRole, RoleDefinition> definitions() {
        EnumMap<HiredVillagerRole, RoleDefinition> definitions = new EnumMap<>(HiredVillagerRole.class);
        definitions.put(HiredVillagerRole.COMBAT, role(VillagerSkill.GUARDING, VillagerSkill.ARCHERY, VillagerSkill.SMITHING));
        definitions.put(HiredVillagerRole.HUNTING, role(VillagerSkill.ARCHERY, VillagerSkill.SURVIVAL, VillagerSkill.ANIMAL_HANDLING));
        definitions.put(HiredVillagerRole.MINING, role(VillagerSkill.MINING, VillagerSkill.MASONRY));
        definitions.put(HiredVillagerRole.LOGGING, role(VillagerSkill.GATHERING, VillagerSkill.CRAFTING));
        definitions.put(HiredVillagerRole.FARMING, role(VillagerSkill.FARMING));
        definitions.put(HiredVillagerRole.FISHING, role(VillagerSkill.FISHING, VillagerSkill.SURVIVAL));
        definitions.put(HiredVillagerRole.BREWING, role(VillagerSkill.MEDICINE));
        definitions.put(HiredVillagerRole.BUILDER, role(VillagerSkill.MASONRY, VillagerSkill.CRAFTING, VillagerSkill.GATHERING));
        definitions.put(HiredVillagerRole.ANIMAL_HANDLING, role(VillagerSkill.ANIMAL_HANDLING));
        definitions.put(HiredVillagerRole.NITWIT, restrictedRole(Set.of("nitwit"), VillagerSkill.DIPLOMACY, VillagerSkill.SURVIVAL, VillagerSkill.GATHERING));
        definitions.put(HiredVillagerRole.COOK, role(VillagerSkill.COOKING));
        definitions.put(HiredVillagerRole.SMELTER, role(VillagerSkill.SMITHING, VillagerSkill.MINING));
        definitions.put(HiredVillagerRole.COURIER, universalRole(VillagerSkill.GATHERING, VillagerSkill.SURVIVAL));
        return Map.copyOf(definitions);
    }

    private static RoleDefinition role(VillagerSkill primary, VillagerSkill... support) {
        return definition(primary, Set.of(), false, support);
    }

    private static RoleDefinition restrictedRole(Set<String> professions, VillagerSkill primary, VillagerSkill... support) {
        return definition(primary, professions, false, support);
    }

    private static RoleDefinition universalRole(VillagerSkill primary, VillagerSkill... support) {
        return definition(primary, Set.of(), true, support);
    }

    private static RoleDefinition definition(
            VillagerSkill primary,
            Set<String> professionRestrictions,
            boolean universal,
            VillagerSkill... support) {
        LinkedHashMap<VillagerSkill, Double> weights = new LinkedHashMap<>();
        if (support.length == 0) {
            weights.put(primary, 1.0D);
        } else {
            weights.put(primary, 0.70D);
            double supportWeight = 0.30D / support.length;
            for (VillagerSkill skill : support) {
                weights.put(skill, supportWeight);
            }
        }
        return new RoleDefinition(
                primary,
                weights,
                PREFERRED_PROFESSION_THRESHOLD,
                NONPREFERRED_PROFESSION_THRESHOLD,
                universal,
                professionRestrictions);
    }

    private static EnumSet<HiredVillagerRole> preferredRoles(Villager villager) {
        return switch (VillagerProfessionSkills.professionKey(villager)) {
            case "nitwit" -> EnumSet.of(HiredVillagerRole.NITWIT);
            case "armorer", "weaponsmith" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.HUNTING, HiredVillagerRole.MINING, HiredVillagerRole.SMELTER);
            case "toolsmith" -> EnumSet.of(HiredVillagerRole.MINING, HiredVillagerRole.LOGGING, HiredVillagerRole.BUILDER, HiredVillagerRole.SMELTER);
            case "mason" -> EnumSet.of(HiredVillagerRole.BUILDER, HiredVillagerRole.MINING);
            case "farmer" -> EnumSet.of(HiredVillagerRole.FARMING, HiredVillagerRole.COOK, HiredVillagerRole.ANIMAL_HANDLING);
            case "fisherman" -> EnumSet.of(HiredVillagerRole.FISHING);
            case "cartographer" -> EnumSet.of(HiredVillagerRole.FISHING, HiredVillagerRole.BUILDER);
            case "cleric" -> EnumSet.of(HiredVillagerRole.BREWING);
            case "butcher" -> EnumSet.of(HiredVillagerRole.COOK, HiredVillagerRole.ANIMAL_HANDLING, HiredVillagerRole.HUNTING);
            case "shepherd", "leatherworker" -> EnumSet.of(HiredVillagerRole.ANIMAL_HANDLING, HiredVillagerRole.HUNTING);
            case "fletcher" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.HUNTING, HiredVillagerRole.LOGGING);
            default -> EnumSet.noneOf(HiredVillagerRole.class);
        };
    }

    private static boolean isProfessionEligible(Villager villager, RoleDefinition definition) {
        return definition.professionRestrictions().isEmpty()
                || definition.professionRestrictions().contains(VillagerProfessionSkills.professionKey(villager));
    }

    public record RoleDefinition(
            VillagerSkill primarySkill,
            Map<VillagerSkill, Double> skillWeights,
            int preferredProfessionThreshold,
            int nonpreferredThreshold,
            boolean universallyAvailable,
            Set<String> professionRestrictions) {
        public RoleDefinition {
            skillWeights = Map.copyOf(skillWeights);
            professionRestrictions = Set.copyOf(professionRestrictions);
        }
    }
}
