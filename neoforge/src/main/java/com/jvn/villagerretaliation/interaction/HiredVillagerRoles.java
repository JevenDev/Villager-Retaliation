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
            HiredVillagerRole.BREWING, List.of(VillagerSkill.MEDICINE),
            HiredVillagerRole.NAVIGATION, List.of(VillagerSkill.CARTOGRAPHY, VillagerSkill.SURVIVAL),
            HiredVillagerRole.ANIMAL_HANDLING, List.of(VillagerSkill.ANIMAL_HANDLING)
    );

    private HiredVillagerRoles() {
    }

    public static List<HiredVillagerRole> availableRoles(ServerLevel level, Villager villager) {
        EnumSet<HiredVillagerRole> roles = preferredRoles(villager);
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            if (roleScore(level, villager, role) >= SKILL_UNLOCK_THRESHOLD) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            roles.add(HiredVillagerRole.FARMING);
        }
        return new ArrayList<>(roles);
    }

    public static HiredVillagerRole defaultRole(ServerLevel level, Villager villager) {
        List<HiredVillagerRole> available = availableRoles(level, villager);
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
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            best = Math.max(best, roleScore(level, villager, role));
        }
        return best;
    }

    public static int roleScore(ServerLevel level, Villager villager, HiredVillagerRole role) {
        int best = 0;
        for (VillagerSkill skill : ROLE_SKILLS.getOrDefault(role, List.of())) {
            best = Math.max(best, VillagerProfileManager.getSkill(level, villager, skill));
        }
        return best;
    }

    public static String roleSummary(ServerLevel level, Villager villager) {
        List<HiredVillagerRole> roles = availableRoles(level, villager);
        List<String> labels = roles.stream().map(HiredVillagerRole::label).toList();
        return String.join(", ", labels);
    }

    private static EnumSet<HiredVillagerRole> preferredRoles(Villager villager) {
        return switch (VillagerProfessionSkills.professionKey(villager)) {
            case "armorer", "weaponsmith" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.MINING);
            case "toolsmith", "mason" -> EnumSet.of(HiredVillagerRole.MINING, HiredVillagerRole.LOGGING);
            case "farmer" -> EnumSet.of(HiredVillagerRole.FARMING, HiredVillagerRole.ANIMAL_HANDLING);
            case "fisherman", "cartographer" -> EnumSet.of(HiredVillagerRole.NAVIGATION);
            case "cleric" -> EnumSet.of(HiredVillagerRole.BREWING);
            case "shepherd", "leatherworker", "butcher" -> EnumSet.of(HiredVillagerRole.ANIMAL_HANDLING);
            case "fletcher" -> EnumSet.of(HiredVillagerRole.COMBAT, HiredVillagerRole.LOGGING);
            default -> EnumSet.noneOf(HiredVillagerRole.class);
        };
    }
}
