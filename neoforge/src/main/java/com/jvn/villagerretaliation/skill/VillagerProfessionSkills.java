package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.List;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerProfessionSkills {
    private VillagerProfessionSkills() {
    }

    public static VillagerSkill primarySkill(AbstractVillager villager) {
        return primarySkill(professionKey(villager));
    }

    public static VillagerSkill primarySkill(String professionKey) {
        return switch (safeKey(professionKey)) {
            case "farmer" -> VillagerSkill.FARMING;
            case "fisherman" -> VillagerSkill.FISHING;
            case "shepherd" -> VillagerSkill.ANIMAL_HANDLING;
            case "fletcher" -> VillagerSkill.ARCHERY;
            case "librarian" -> VillagerSkill.SCHOLARSHIP;
            case "cartographer" -> VillagerSkill.CARTOGRAPHY;
            case "cleric" -> VillagerSkill.MEDICINE;
            case "armorer", "weaponsmith" -> VillagerSkill.SMITHING;
            case "toolsmith" -> VillagerSkill.CRAFTING;
            case "mason" -> VillagerSkill.MASONRY;
            case "leatherworker" -> VillagerSkill.LEATHERWORKING;
            case "butcher" -> VillagerSkill.COOKING;
            case "wandering_trader" -> VillagerSkill.TRADING;
            default -> VillagerSkill.TRADING;
        };
    }

    public static List<VillagerSkill> tradeSkills(AbstractVillager villager) {
        return tradeSkills(professionKey(villager));
    }

    public static List<VillagerSkill> tradeSkills(String professionKey) {
        VillagerSkill primary = primarySkill(professionKey);
        return switch (safeKey(professionKey)) {
            case "farmer" -> withPrimary(primary, VillagerSkill.COOKING, VillagerSkill.GATHERING, VillagerSkill.ANIMAL_HANDLING);
            case "fisherman" -> withPrimary(primary, VillagerSkill.GATHERING, VillagerSkill.SURVIVAL, VillagerSkill.COOKING);
            case "shepherd" -> withPrimary(primary, VillagerSkill.FARMING, VillagerSkill.DIPLOMACY, VillagerSkill.CRAFTING);
            case "fletcher" -> withPrimary(primary, VillagerSkill.CRAFTING, VillagerSkill.TRADING, VillagerSkill.SURVIVAL);
            case "librarian" -> withPrimary(primary, VillagerSkill.DIPLOMACY, VillagerSkill.TRADING, VillagerSkill.CARTOGRAPHY);
            case "cartographer" -> withPrimary(primary, VillagerSkill.SCHOLARSHIP, VillagerSkill.SURVIVAL, VillagerSkill.TRADING);
            case "cleric" -> withPrimary(primary, VillagerSkill.SCHOLARSHIP, VillagerSkill.DIPLOMACY, VillagerSkill.SURVIVAL);
            case "armorer" -> withPrimary(primary, VillagerSkill.GUARDING, VillagerSkill.MINING, VillagerSkill.CRAFTING, VillagerSkill.TRADING);
            case "weaponsmith" -> withPrimary(primary, VillagerSkill.GUARDING, VillagerSkill.MINING, VillagerSkill.ARCHERY, VillagerSkill.CRAFTING);
            case "toolsmith" -> withPrimary(primary, VillagerSkill.SMITHING, VillagerSkill.MINING, VillagerSkill.GATHERING, VillagerSkill.TRADING);
            case "mason" -> withPrimary(primary, VillagerSkill.CRAFTING, VillagerSkill.MINING, VillagerSkill.GATHERING, VillagerSkill.TRADING);
            case "leatherworker" -> withPrimary(primary, VillagerSkill.CRAFTING, VillagerSkill.ANIMAL_HANDLING, VillagerSkill.TRADING);
            case "butcher" -> withPrimary(primary, VillagerSkill.ANIMAL_HANDLING, VillagerSkill.TRADING, VillagerSkill.GUARDING);
            case "wandering_trader" -> withPrimary(primary, VillagerSkill.DIPLOMACY, VillagerSkill.SURVIVAL, VillagerSkill.GATHERING, VillagerSkill.CARTOGRAPHY);
            case "nitwit" -> List.of(VillagerSkill.SURVIVAL, VillagerSkill.GATHERING, VillagerSkill.DIPLOMACY, VillagerSkill.CRAFTING);
            default -> withPrimary(primary, VillagerSkill.GATHERING, VillagerSkill.SURVIVAL, VillagerSkill.DIPLOMACY);
        };
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

    public static String professionKey(VillagerProfession profession) {
        return VillagerProfessionUtil.serializedKey(profession);
    }

    private static List<VillagerSkill> withPrimary(VillagerSkill primary, VillagerSkill... secondary) {
        return java.util.stream.Stream
                .concat(java.util.stream.Stream.of(primary), java.util.Arrays.stream(secondary))
                .distinct()
                .toList();
    }

    private static String safeKey(String professionKey) {
        return professionKey == null || professionKey.isBlank() ? "none" : professionKey;
    }
}
