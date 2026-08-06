package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class VillagerCombatRoles {
    public static final double PLAYER_FIST_DAMAGE = RetaliationCombatStats.PLAYER_FIST_DAMAGE;
    private static final RetaliationActorPolicy<Villager> POLICY = new VillagerActorPolicy();
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

    static RetaliationActorPolicy<Villager> policy() {
        return POLICY;
    }

    public static boolean canFightBack(Villager villager) {
        if (villager.isBaby()) {
            return false;
        }

        VillagerProfession profession = profession(villager);
        BooleanSupplier configuredRule = FIGHT_BACK_RULES.get(profession);
        if (configuredRule != null) {
            return configuredRule.getAsBoolean();
        }

        return profession != VillagerProfession.NITWIT
                || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerInventoryAccess.hasUsableWeapon(villager)
                || canScavengeNearbyGroundWeapon(villager);
    }

    public static boolean canUseTemporaryCombatLoadout(Villager villager) {
        VillagerProfession profession = profession(villager);
        BooleanSupplier configuredRule = FIGHT_BACK_RULES.get(profession);
        if (configuredRule != null) {
            return configuredRule.getAsBoolean();
        }

        if (profession == VillagerProfession.FARMER) {
            return true;
        }
        if (profession == VillagerProfession.CLERIC) {
            return VillagerRetaliationConfig.CLERICS_USE_POTIONS.get();
        }
        return profession == VillagerProfession.LIBRARIAN;
    }

    public static double meleeAttackDamageBase(Villager villager) {
        return RetaliationCombatStats.meleeAttackDamageBase(
                villager.getMainHandItem(), villager.level().getDifficulty());
    }

    public static ItemStack preferredWeapon(Villager villager) {
        return PREFERRED_WEAPON_RULES.getOrDefault(profession(villager), ignored -> ItemStack.EMPTY).apply(villager);
    }

    public static ItemStack persistentRoleWeapon(Villager villager) {
        VillagerProfession profession = profession(villager);
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get() || !canUseTemporaryCombatLoadout(villager)) {
            return ItemStack.EMPTY;
        }

        if (profession == VillagerProfession.CLERIC || profession == VillagerProfession.LIBRARIAN) {
            return ItemStack.EMPTY;
        }
        if (profession == VillagerProfession.FARMER) {
            return new ItemStack(Items.IRON_HOE);
        }

        return preferredWeapon(villager);
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
        return RetaliationCombatStats.COMBAT_SPEED_MODIFIER;
    }

    public static int attackCooldown(Villager villager) {
        int normalTicks = ATTACK_COOLDOWNS.getOrDefault(profession(villager), 20);
        return villager.level() instanceof ServerLevel level
                ? VillagerCombatSkillBehavior.adjustMeleeRecoveryTicks(level, villager, normalTicks)
                : normalTicks;
    }

    static int rangedAttackRecoveryTicks(Villager villager, int normalTicks) {
        return villager.level() instanceof ServerLevel level
                ? VillagerCombatSkillBehavior.adjustRangedRecoveryTicks(level, villager, normalTicks)
                : normalTicks;
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

        return VillagerRetaliationConfig.VILLAGERS_PICK_UP_GROUND_WEAPONS.get()
                && !usesDedicatedRoleCombatItem(villager);
    }

    public static boolean canScavengeNearbyGroundWeapon(Villager villager) {
        return canScavengeGroundWeapons(villager)
                && VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
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
        rules.put(VillagerProfession.CLERIC, VillagerRetaliationConfig.CLERICS_USE_POTIONS::get);
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
        rules.put(VillagerProfession.FARMER, villager -> new ItemStack(Items.IRON_HOE));
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

    private static final class VillagerActorPolicy implements RetaliationActorPolicy<Villager> {
        @Override
        public boolean canFightBack(Villager villager) {
            return VillagerCombatRoles.canFightBack(villager);
        }

        @Override
        public boolean canUseTemporaryCombatLoadout(Villager villager) {
            return VillagerCombatRoles.canUseTemporaryCombatLoadout(villager);
        }

        @Override
        public boolean canScavengeGroundWeapons(Villager villager) {
            return VillagerCombatRoles.canScavengeGroundWeapons(villager);
        }

        @Override
        public ItemStack preferredWeapon(Villager villager) {
            return VillagerCombatRoles.preferredWeapon(villager);
        }

        @Override
        public double meleeAttackDamageBase(Villager villager) {
            return VillagerCombatRoles.meleeAttackDamageBase(villager);
        }

        @Override
        public double movementSpeed(Villager villager) {
            return VillagerCombatRoles.movementSpeed(villager);
        }

        @Override
        public int attackCooldown(Villager villager) {
            return VillagerCombatRoles.attackCooldown(villager);
        }
    }
}
