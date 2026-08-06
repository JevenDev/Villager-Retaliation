package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class HiredVillagerRoles {
    public static final int STANDARD_APTITUDE = 60;
    public static final double PRIMARY_SKILL_WEIGHT = 0.70D;
    public static final double SUPPORT_SKILL_WEIGHT = 0.30D;
    private static final Map<HiredVillagerRole, RoleDefinition> DEFINITIONS = definitions();

    private HiredVillagerRoles() {
    }

    public static List<HiredVillagerRole> availableRoles(ServerLevel level, Villager villager) {
        if (villager == null || villager.isBaby()) {
            return List.of();
        }
        VillagerSkillSet skills = VillagerProfileManager.getSkills(level, villager);
        String professionKey = VillagerProfessionSkills.professionKey(villager);
        List<HiredVillagerRole> roles = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            if (isSkillUnlocked(professionKey, false, skills, role)) {
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

    /** Weighted role aptitude: 70% primary skill and 30% support skill. */
    public static int roleScore(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return roleScore(VillagerProfileManager.getSkills(level, villager), role);
    }

    public static int roleScore(VillagerSkillSet skills, HiredVillagerRole role) {
        RoleDefinition definition = definition(role);
        VillagerSkillSet safeSkills = skills == null ? VillagerSkillSet.EMPTY : skills;
        return aptitude(
                safeSkills.get(definition.primarySkill()),
                safeSkills.get(definition.supportSkill()));
    }

    public static int aptitude(int primaryValue, int supportValue) {
        int primary = Math.clamp(primaryValue, 0, 100);
        int support = Math.clamp(supportValue, 0, 100);
        return Math.clamp((int) Math.round(primary * PRIMARY_SKILL_WEIGHT + support * SUPPORT_SKILL_WEIGHT), 0, 100);
    }

    public static int skillWorkSpeedPercent(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return skillWorkSpeedPercent(roleScore(level, villager, role));
    }

    public static int skillWorkSpeedPercent(VillagerSkillSet skills, HiredVillagerRole role) {
        return skillWorkSpeedPercent(roleScore(skills, role));
    }

    /**
     * Broad action-speed curve used only by roles whose learned skill directly changes
     * repeated work cadence, such as Farming, Animal Handling, Fishing, and Nitwit work.
     */
    public static int skillWorkSpeedPercent(int aptitude) {
        return piecewisePercent(aptitude, 50, 100, 125);
    }

    /**
     * Narrow block-work curve. Tool material, enchantments, and block hardness remain
     * the dominant factors for Mining and Logging.
     */
    public static int blockWorkSpeedPercent(int aptitude) {
        return piecewisePercent(aptitude, 85, 100, 110);
    }

    /** Skill-controlled speed for an action performed inside a worker implementation. */
    public static int roleActionSpeedPercent(HiredVillagerRole role, int aptitude) {
        if (role == null) {
            return 100;
        }
        return switch (role) {
            case MINING, LOGGING -> blockWorkSpeedPercent(aptitude);
            case FISHING -> skillWorkSpeedPercent(aptitude);
            default -> 100;
        };
    }

    /**
     * Skill-controlled decision cadence. Roles with a concrete action or capacity
     * effect stay at neutral cadence so aptitude is never counted twice.
     */
    public static int roleCadencePercent(HiredVillagerRole role, int aptitude) {
        if (role == null) {
            return 100;
        }
        return switch (role) {
            case FARMING, ANIMAL_HANDLING, BREWING, CRAFTSMAN, NITWIT -> skillWorkSpeedPercent(aptitude);
            case HUNTING, BUILDER -> blockWorkSpeedPercent(aptitude);
            default -> 100;
        };
    }

    public static int transferCapacityPercent(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return transferCapacityPercent(roleScore(level, villager, role));
    }

    public static int transferCapacityPercent(VillagerSkillSet skills, HiredVillagerRole role) {
        return transferCapacityPercent(roleScore(skills, role));
    }

    /** Role-specific material capacity. Brewer and Craftsman aptitude affects preparation speed instead. */
    public static int roleTransferCapacityPercent(HiredVillagerRole role, int aptitude) {
        if (role == HiredVillagerRole.COOK || role == HiredVillagerRole.SMELTER) {
            return transferCapacityPercent(aptitude);
        }
        return 100;
    }

    public static int transferCapacityPercent(int aptitude) {
        return piecewisePercent(aptitude, 50, 100, 150);
    }

    public static int transferLimit(int baseItems, int transferCapacityPercent) {
        if (baseItems <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(baseItems * Math.clamp(transferCapacityPercent, 50, 150) / 100.0F));
    }

    public static int courierTransferLimit(int aptitude) {
        int score = Math.clamp(aptitude, 0, 100);
        if (score >= 100) {
            return 128;
        }
        if (score >= 80) {
            return 96;
        }
        if (score >= 60) {
            return 64;
        }
        if (score >= 50) {
            return 32;
        }
        if (score >= 40) {
            return 16;
        }
        return score >= 30 ? 8 : score >= 20 ? 4 : score >= 10 ? 2 : 1;
    }

    public static int scaledDurationTicks(int normalTicks, int skillWorkSpeedPercent) {
        if (normalTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(normalTicks * 100.0F
                / Math.clamp(skillWorkSpeedPercent, 50, 125)));
    }

    public static int baseTransferItems(HiredVillagerRole role) {
        if (role == HiredVillagerRole.COURIER) {
            return 64;
        }
        if (role == HiredVillagerRole.CRAFTSMAN) {
            return 32;
        }
        return role == HiredVillagerRole.COOK
                || role == HiredVillagerRole.SMELTER
                || role == HiredVillagerRole.BREWING ? 16 : 0;
    }

    public static List<VillagerSkill> roleSkills(HiredVillagerRole role) {
        RoleDefinition definition = definition(role);
        return List.of(definition.primarySkill(), definition.supportSkill());
    }

    public static VillagerSkill primarySkill(HiredVillagerRole role) {
        return definition(role).primarySkill();
    }

    public static VillagerSkill supportSkill(HiredVillagerRole role) {
        return definition(role).supportSkill();
    }

    public static RoleDefinition definition(HiredVillagerRole role) {
        RoleDefinition definition = DEFINITIONS.get(role);
        if (definition == null) {
            throw new IllegalArgumentException("Missing hired role definition for " + role);
        }
        return definition;
    }

    public static boolean isSkillUnlocked(ServerLevel level, Villager villager, HiredVillagerRole role) {
        if (villager == null) {
            return false;
        }
        return isSkillUnlocked(
                VillagerProfessionSkills.professionKey(villager),
                villager.isBaby(),
                VillagerProfileManager.getSkills(level, villager),
                role);
    }

    public static boolean isSkillUnlocked(String professionKey, boolean baby, VillagerSkillSet skills, HiredVillagerRole role) {
        if (baby || role == null) {
            return false;
        }
        Set<String> restrictedProfessions = definition(role).restrictedProfessions();
        return restrictedProfessions.isEmpty()
                || restrictedProfessions.contains(safeProfession(professionKey));
    }

    /** Compatibility overload for callers that previously resolved a qualification total. */
    public static boolean isSkillUnlocked(Villager villager, HiredVillagerRole role, int ignoredQualificationTotal) {
        if (villager == null || villager.isBaby() || role == null) {
            return false;
        }
        Set<String> restrictedProfessions = definition(role).restrictedProfessions();
        return restrictedProfessions.isEmpty()
                || restrictedProfessions.contains(VillagerProfessionSkills.professionKey(villager));
    }

    public static boolean isProfessionPreferred(Villager villager, HiredVillagerRole role) {
        return villager != null && isCanonicalProfession(VillagerProfessionSkills.professionKey(villager), role);
    }

    public static boolean isCanonicalProfession(String professionKey, HiredVillagerRole role) {
        return role != null && definition(role).canonicalProfessions().contains(safeProfession(professionKey));
    }

    public static boolean isUniversal(HiredVillagerRole role) {
        return role != null && definition(role).universallyAvailable();
    }

    public static boolean isProfessionRestricted(HiredVillagerRole role) {
        return role != null && !definition(role).restrictedProfessions().isEmpty();
    }

    public static String roleSummary(ServerLevel level, Villager villager) {
        return String.join(", ", availableContractRoles(level, villager).stream()
                .map(HiredVillagerRole::label)
                .toList());
    }

    private static Map<HiredVillagerRole, RoleDefinition> definitions() {
        EnumMap<HiredVillagerRole, RoleDefinition> definitions = new EnumMap<>(HiredVillagerRole.class);
        definitions.put(HiredVillagerRole.COMBAT, role(VillagerSkill.GUARDING, VillagerSkill.ARCHERY, "weaponsmith"));
        definitions.put(HiredVillagerRole.HUNTING, role(VillagerSkill.ARCHERY, VillagerSkill.SURVIVAL, "fletcher"));
        definitions.put(HiredVillagerRole.MINING, role(VillagerSkill.MINING, VillagerSkill.MASONRY, "toolsmith"));
        definitions.put(HiredVillagerRole.LOGGING, role(VillagerSkill.GATHERING, VillagerSkill.CRAFTING));
        definitions.put(HiredVillagerRole.FARMING, role(VillagerSkill.FARMING, VillagerSkill.GATHERING, "farmer"));
        definitions.put(HiredVillagerRole.FISHING, role(VillagerSkill.FISHING, VillagerSkill.SURVIVAL, "fisherman"));
        definitions.put(HiredVillagerRole.BREWING, role(VillagerSkill.MEDICINE, VillagerSkill.SCHOLARSHIP, "cleric"));
        definitions.put(HiredVillagerRole.CRAFTSMAN, role(VillagerSkill.CRAFTING, VillagerSkill.GATHERING, "toolsmith"));
        definitions.put(HiredVillagerRole.BUILDER, role(VillagerSkill.MASONRY, VillagerSkill.CRAFTING, "mason"));
        definitions.put(HiredVillagerRole.ANIMAL_HANDLING, role(
                VillagerSkill.ANIMAL_HANDLING, VillagerSkill.FARMING, "shepherd", "leatherworker"));
        definitions.put(HiredVillagerRole.COOK, role(VillagerSkill.COOKING, VillagerSkill.GATHERING, "butcher"));
        definitions.put(HiredVillagerRole.SMELTER, role(VillagerSkill.SMITHING, VillagerSkill.MINING, "armorer"));
        definitions.put(HiredVillagerRole.COURIER, universalRole(VillagerSkill.GATHERING, VillagerSkill.SURVIVAL));
        definitions.put(HiredVillagerRole.NITWIT, restrictedRole(
                VillagerSkill.DIPLOMACY, VillagerSkill.SURVIVAL, Set.of("nitwit"), "nitwit"));
        return Map.copyOf(definitions);
    }

    private static RoleDefinition role(VillagerSkill primary, VillagerSkill support, String... canonicalProfessions) {
        return new RoleDefinition(primary, support, Set.of(canonicalProfessions), false, Set.of());
    }

    private static RoleDefinition universalRole(VillagerSkill primary, VillagerSkill support) {
        return new RoleDefinition(primary, support, Set.of(), true, Set.of());
    }

    private static RoleDefinition restrictedRole(
            VillagerSkill primary,
            VillagerSkill support,
            Set<String> restrictedProfessions,
            String... canonicalProfessions) {
        return new RoleDefinition(primary, support, Set.of(canonicalProfessions), false, restrictedProfessions);
    }

    private static int piecewisePercent(int aptitude, int minimum, int standard, int maximum) {
        int score = Math.clamp(aptitude, 0, 100);
        if (score <= STANDARD_APTITUDE) {
            return minimum + Math.round(score * (standard - minimum) / (float) STANDARD_APTITUDE);
        }
        return standard + Math.round(
                (score - STANDARD_APTITUDE) * (maximum - standard)
                        / (float) (100 - STANDARD_APTITUDE));
    }

    private static String safeProfession(String professionKey) {
        return professionKey == null || professionKey.isBlank() ? "none" : professionKey;
    }

    public record RoleDefinition(
            VillagerSkill primarySkill,
            VillagerSkill supportSkill,
            Set<String> canonicalProfessions,
            boolean universallyAvailable,
            Set<String> restrictedProfessions) {
        public RoleDefinition {
            if (primarySkill == null || supportSkill == null || primarySkill == supportSkill) {
                throw new IllegalArgumentException("Hired roles require two distinct skills");
            }
            canonicalProfessions = Set.copyOf(canonicalProfessions);
            restrictedProfessions = Set.copyOf(restrictedProfessions);
        }

        public Map<VillagerSkill, Double> skillWeights() {
            return Map.of(primarySkill, PRIMARY_SKILL_WEIGHT, supportSkill, SUPPORT_SKILL_WEIGHT);
        }
    }
}
