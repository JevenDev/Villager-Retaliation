package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
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
    public static final double PLAYER_FIST_DAMAGE = 1.0D;
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

        return profession(villager) != VillagerProfession.NITWIT || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager);
    }

    public static boolean canUseTemporaryCombatLoadout(Villager villager) {
        VillagerProfession profession = profession(villager);
        BooleanSupplier configuredRule = FIGHT_BACK_RULES.get(profession);
        if (configuredRule != null) {
            return configuredRule.getAsBoolean();
        }

        if (profession == VillagerProfession.FARMER) {
            return VillagerRetaliationConfig.FARMERS_USE_BREAD.get();
        }
        if (profession == VillagerProfession.CLERIC) {
            return VillagerRetaliationConfig.CLERICS_USE_POTIONS.get();
        }
        return profession == VillagerProfession.LIBRARIAN;
    }

    public static double meleeAttackDamageBase(Villager villager) {
        ItemStack weapon = villager.getMainHandItem();
        if (weapon.isEmpty()) {
            return PLAYER_FIST_DAMAGE;
        }

        if (!hasAttackDamageModifier(weapon)) {
            return PLAYER_FIST_DAMAGE;
        }

        return VINDICATOR_STYLE_WEAPON_BASE_DAMAGE;
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
        return isFletcher(villager) && VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK.get()
                || isCleric(villager) && VillagerRetaliationConfig.CLERICS_USE_POTIONS.get();
    }

    private static VillagerProfession profession(Villager villager) {
        return villager.getVillagerData().getProfession();
    }

    private static Map<VillagerProfession, BooleanSupplier> createFightBackRules() {
        Map<VillagerProfession, BooleanSupplier> rules = new HashMap<>();
        rules.put(VillagerProfession.WEAPONSMITH, VillagerRetaliationConfig.WEAPONSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.TOOLSMITH, VillagerRetaliationConfig.TOOLSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.MASON, VillagerRetaliationConfig.TOOLSMITHS_FIGHT_BACK::get);
        rules.put(VillagerProfession.ARMORER, VillagerRetaliationConfig.ARMORERS_FIGHT_BACK::get);
        rules.put(VillagerProfession.FLETCHER, VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK::get);
        rules.put(VillagerProfession.BUTCHER, VillagerRetaliationConfig.BUTCHERS_FIGHT_BACK::get);
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
            if (!VillagerRetaliationConfig.FARMERS_USE_BREAD.get()) {
                return ItemStack.EMPTY;
            }
            return villager.getRandom().nextFloat() < FARMER_BREAD_WEAPON_CHANCE
                    ? new ItemStack(Items.BREAD)
                    : new ItemStack(Items.IRON_HOE);
        });
        rules.put(VillagerProfession.CLERIC, ignored -> VillagerRetaliationConfig.CLERICS_USE_POTIONS.get()
                ? PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)
                : ItemStack.EMPTY);
        rules.put(VillagerProfession.LIBRARIAN, ignored -> new ItemStack(Items.BOOK));
        return Map.copyOf(rules);
    }

    private static ItemStack fletcherRangedWeapon(Villager villager) {
        boolean usesCrossbow = (villager.getUUID().getLeastSignificantBits() & 1L) == 0L;
        return new ItemStack(usesCrossbow ? Items.CROSSBOW : Items.BOW);
    }

    private static boolean hasAttackDamageModifier(ItemStack stack) {
        boolean[] hasAttackDamageModifier = new boolean[]{false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamageModifier[0] = true;
            }
        });
        return hasAttackDamageModifier[0];
    }
}
