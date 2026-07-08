package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class HiredVillagerRoles {
    private static final int SKILL_UNLOCK_THRESHOLD = 55;
    private static final Map<HiredVillagerRole, List<VillagerSkill>> ROLE_SKILLS = Map.of(
            HiredVillagerRole.COMBAT, List.of(VillagerSkill.GUARDING, VillagerSkill.ARCHERY, VillagerSkill.SMITHING),
            HiredVillagerRole.MINING, List.of(VillagerSkill.MINING, VillagerSkill.MASONRY),
            HiredVillagerRole.LOGGING, List.of(VillagerSkill.GATHERING, VillagerSkill.CRAFTING),
            HiredVillagerRole.FARMING, List.of(VillagerSkill.FARMING),
            HiredVillagerRole.FISHING, List.of(VillagerSkill.FISHING, VillagerSkill.SURVIVAL),
            HiredVillagerRole.BREWING, List.of(VillagerSkill.MEDICINE),
            HiredVillagerRole.BUILDER, List.of(VillagerSkill.MASONRY, VillagerSkill.CRAFTING, VillagerSkill.GATHERING),
            HiredVillagerRole.ANIMAL_HANDLING, List.of(VillagerSkill.ANIMAL_HANDLING),
            HiredVillagerRole.NITWIT, List.of(VillagerSkill.SURVIVAL, VillagerSkill.GATHERING, VillagerSkill.DIPLOMACY),
            HiredVillagerRole.COOK, List.of(VillagerSkill.COOKING)
    );

    private HiredVillagerRoles() {
    }

    public static List<HiredVillagerRole> availableRoles(ServerLevel level, Villager villager) {
        EnumSet<HiredVillagerRole> roles = preferredRoles(villager);
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            if (isSkillUnlocked(level, villager, role)) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            roles.add(HiredVillagerRole.FARMING);
        }
        return new ArrayList<>(roles);
    }

    public static List<HiredVillagerRole> availableContractRoles(ServerLevel level, Villager villager) {
        List<HiredVillagerRole> roles = availableRoles(level, villager).stream()
                .filter(role -> role != HiredVillagerRole.BUILDER)
                .toList();
        if (roles.isEmpty()) {
            return List.of(HiredVillagerRole.FARMING);
        }
        return roles;
    }

    public static boolean canOfferBuilderService(ServerLevel level, Villager villager) {
        return availableRoles(level, villager).contains(HiredVillagerRole.BUILDER);
    }

    public static HiredVillagerRole defaultRole(ServerLevel level, Villager villager) {
        List<HiredVillagerRole> available = availableContractRoles(level, villager);
        HiredVillagerRole bestRole = available.getFirst();
        int bestScore = -1;
        for (HiredVillagerRole role : available) {
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

    public static int roleScore(ServerLevel level, Villager villager, HiredVillagerRole role) {
        List<VillagerSkill> skills = roleSkills(role);
        if (skills.isEmpty()) {
            return 0;
        }

        int total = 0;
        int lowest = 100;
        int highest = 0;
        for (VillagerSkill skill : skills) {
            int value = VillagerProfileManager.getSkill(level, villager, skill);
            total += value;
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }

        int average = Math.round(total / (float) skills.size());
        return Math.round(average * 0.7F + lowest * 0.2F + highest * 0.1F);
    }

    public static List<VillagerSkill> roleSkills(HiredVillagerRole role) {
        return ROLE_SKILLS.getOrDefault(role, List.of());
    }

    public static boolean isSkillUnlocked(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return isSkillUnlocked(villager, role, roleScore(level, villager, role));
    }

    public static boolean isSkillUnlocked(Villager villager, HiredVillagerRole role, int roleScore) {
        if (!isProfessionEligible(villager, role)) {
            return false;
        }
        return roleScore >= SKILL_UNLOCK_THRESHOLD;
    }

    public static boolean isProfessionPreferred(Villager villager, HiredVillagerRole role) {
        return preferredRoles(villager).contains(role);
    }

    public static String roleSummary(ServerLevel level, Villager villager) {
        List<HiredVillagerRole> roles = availableContractRoles(level, villager);
        List<String> labels = roles.stream().map(HiredVillagerRole::label).toList();
        return String.join(", ", labels);
    }

    private static EnumSet<HiredVillagerRole> preferredRoles(Villager villager) {
        return switch (VillagerProfessionSkills.professionKey(villager)) {
            case "nitwit" -> EnumSet.of(HiredVillagerRole.NITWIT);
            case "armorer", "weaponsmith" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.MINING);
            case "toolsmith" -> EnumSet.of(HiredVillagerRole.MINING, HiredVillagerRole.LOGGING, HiredVillagerRole.BUILDER);
            case "mason" -> EnumSet.of(HiredVillagerRole.BUILDER, HiredVillagerRole.MINING);
            case "farmer" -> EnumSet.of(HiredVillagerRole.FARMING, HiredVillagerRole.COOK, HiredVillagerRole.ANIMAL_HANDLING);
            case "fisherman" -> EnumSet.of(HiredVillagerRole.FISHING);
            case "cartographer" -> EnumSet.of(HiredVillagerRole.FISHING, HiredVillagerRole.BUILDER);
            case "cleric" -> EnumSet.of(HiredVillagerRole.BREWING);
            case "butcher" -> EnumSet.of(HiredVillagerRole.COOK, HiredVillagerRole.ANIMAL_HANDLING);
            case "shepherd", "leatherworker" -> EnumSet.of(HiredVillagerRole.ANIMAL_HANDLING);
            case "fletcher" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.LOGGING);
            default -> EnumSet.noneOf(HiredVillagerRole.class);
        };
    }

    private static boolean isProfessionEligible(Villager villager, HiredVillagerRole role) {
        if (role != HiredVillagerRole.NITWIT) {
            return true;
        }
        return "nitwit".equals(VillagerProfessionSkills.professionKey(villager));
    }
}
