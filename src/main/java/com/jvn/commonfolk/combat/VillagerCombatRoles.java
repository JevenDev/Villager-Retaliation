package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class VillagerCombatRoles {
    public static final float PLAYER_FIST_DAMAGE = 1.0F;
    private static final double VINDICATOR_STYLE_WEAPON_BASE_DAMAGE = 5.0D;
    private static final float FARMER_BREAD_WEAPON_CHANCE = 0.12F;
    private static final Map<VillagerProfession, BooleanSupplier> FIGHT_BACK_RULES = createFightBackRules();
    private static final Map<VillagerProfession, Function<Villager, ItemStack>> PREFERRED_WEAPON_RULES = createPreferredWeaponRules();
    private static final Set<VillagerProfession> LOOT_WEAPON_PROFESSIONS = Set.of(
            VillagerProfession.WEAPONSMITH,
            VillagerProfession.TOOLSMITH,
            VillagerProfession.BUTCHER,
            VillagerProfession.ARMORER
    );
    private static final Map<VillagerProfession, Integer> ATTACK_COOLDOWNS = Map.of(
            VillagerProfession.WEAPONSMITH, 16
    );

    private VillagerCombatRoles() {
    }

    public static boolean canFightBack(Villager villager) {
        if (villager.isBaby()) {
            return false;
        }

        return profession(villager) != VillagerProfession.NITWIT || CommonfolkVillagerWeapons.hasUsableWeapon(villager);
    }

    public static boolean canUseTemporaryCombatLoadout(Villager villager) {
        VillagerProfession profession = profession(villager);
        BooleanSupplier configuredRule = FIGHT_BACK_RULES.get(profession);
        if (configuredRule != null) {
            return configuredRule.getAsBoolean();
        }

        if (profession == VillagerProfession.FARMER) {
            return CommonfolkConfig.FARMERS_USE_BREAD.get();
        }
        if (profession == VillagerProfession.CLERIC) {
            return CommonfolkConfig.CLERICS_USE_POTIONS.get();
        }
        return profession == VillagerProfession.LIBRARIAN;
    }

    public static float meleeDamage(Villager villager) {
        ItemStack weapon = CommonfolkVillagerWeapons.getPrimaryWeapon(villager);
        if (weapon.isEmpty()) {
            return PLAYER_FIST_DAMAGE;
        }

        boolean[] hasAttackDamageModifier = new boolean[]{false};
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamageModifier[0] = true;
            }
        });

        if (!hasAttackDamageModifier[0]) {
            return PLAYER_FIST_DAMAGE;
        }

        double totalDamage = weapon.getAttributeModifiers().compute(VINDICATOR_STYLE_WEAPON_BASE_DAMAGE, EquipmentSlot.MAINHAND);
        return (float) Math.max(0.0D, totalDamage);
    }

    public static ItemStack preferredWeapon(Villager villager) {
        return PREFERRED_WEAPON_RULES.getOrDefault(profession(villager), ignored -> ItemStack.EMPTY).apply(villager);
    }

    public static ItemStack preferredLootWeapon(Villager villager) {
        if (LOOT_WEAPON_PROFESSIONS.contains(profession(villager))) {
            return preferredWeapon(villager);
        }

        if (isFletcher(villager)) {
            return fletcherRangedWeapon(villager);
        }

        return ItemStack.EMPTY;
    }

    public static double movementSpeed(Villager villager) {
        return 0.5D;
    }

    public static int attackCooldown(Villager villager) {
        return ATTACK_COOLDOWNS.getOrDefault(profession(villager), 20);
    }

    public static boolean isArmorer(Villager villager) {
        return profession(villager) == VillagerProfession.ARMORER;
    }

    public static boolean isCleric(Villager villager) {
        return profession(villager) == VillagerProfession.CLERIC;
    }

    public static boolean isFarmer(Villager villager) {
        return profession(villager) == VillagerProfession.FARMER;
    }

    public static boolean isFletcher(Villager villager) {
        return profession(villager) == VillagerProfession.FLETCHER;
    }

    public static boolean canScavengeGroundWeapons(Villager villager) {
        if (villager.isBaby()) {
            return false;
        }

        return !usesDedicatedRoleCombatItem(villager);
    }

    public static boolean usesDedicatedRoleCombatItem(Villager villager) {
        return isFletcher(villager) && CommonfolkConfig.FLETCHERS_FIGHT_BACK.get()
                || isCleric(villager) && CommonfolkConfig.CLERICS_USE_POTIONS.get();
    }

    private static VillagerProfession profession(Villager villager) {
        return villager.getVillagerData().getProfession();
    }

    private static Map<VillagerProfession, BooleanSupplier> createFightBackRules() {
        Map<VillagerProfession, BooleanSupplier> rules = new HashMap<>();
        rules.put(VillagerProfession.WEAPONSMITH, CommonfolkConfig.WEAPONSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.TOOLSMITH, CommonfolkConfig.TOOLSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.MASON, CommonfolkConfig.TOOLSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.ARMORER, CommonfolkConfig.ARMORERS_FIGHT_BACK::get);
        rules.put(VillagerProfession.FLETCHER, CommonfolkConfig.FLETCHERS_FIGHT_BACK::get);
        rules.put(VillagerProfession.BUTCHER, CommonfolkConfig.BUTCHERS_FIGHT_BACK::get);
        return Map.copyOf(rules);
    }

    private static Map<VillagerProfession, Function<Villager, ItemStack>> createPreferredWeaponRules() {
        Map<VillagerProfession, Function<Villager, ItemStack>> rules = new HashMap<>();
        rules.put(VillagerProfession.WEAPONSMITH, ignored -> new ItemStack(Items.IRON_SWORD));
        rules.put(VillagerProfession.ARMORER, ignored -> new ItemStack(Items.IRON_SWORD));
        rules.put(VillagerProfession.TOOLSMITH, ignored -> new ItemStack(Items.IRON_AXE));
        rules.put(VillagerProfession.MASON, ignored -> new ItemStack(Items.IRON_PICKAXE));
        rules.put(VillagerProfession.BUTCHER, ignored -> new ItemStack(Items.IRON_AXE));
        rules.put(VillagerProfession.FLETCHER, VillagerCombatRoles::fletcherRangedWeapon);
        rules.put(VillagerProfession.FARMER, villager -> {
            if (!CommonfolkConfig.FARMERS_USE_BREAD.get()) {
                return ItemStack.EMPTY;
            }
            return villager.getRandom().nextFloat() < FARMER_BREAD_WEAPON_CHANCE
                    ? new ItemStack(Items.BREAD)
                    : new ItemStack(Items.IRON_HOE);
        });
        rules.put(VillagerProfession.CLERIC, ignored -> CommonfolkConfig.CLERICS_USE_POTIONS.get()
                ? PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)
                : ItemStack.EMPTY);
        rules.put(VillagerProfession.LIBRARIAN, ignored -> new ItemStack(Items.BOOK));
        return Map.copyOf(rules);
    }

    private static ItemStack fletcherRangedWeapon(Villager villager) {
        boolean usesCrossbow = (villager.getUUID().getLeastSignificantBits() & 1L) == 0L;
        return new ItemStack(usesCrossbow ? Items.CROSSBOW : Items.BOW);
    }
}
