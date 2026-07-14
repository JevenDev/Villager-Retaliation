package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.combat.VillagerRetaliationCombatWeaponFactory;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.server.level.ServerLevel;

/** Applies persistent, empty-slot-only militia equipment at the start of a player raid. */
final class PlayerRaidLoadoutService {
    private static final String EQUIPPED = "VillagerRetaliationPlayerRaidEquipped";

    private PlayerRaidLoadoutService() {
    }

    static void equip(Villager villager) {
        if (villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || villager.getPersistentData().getBoolean(EQUIPPED)) {
            return;
        }
        RandomSource random = villager.getRandom();
        Difficulty difficulty = villager.level().getDifficulty();
        PlayerRaidLoadoutResources.DifficultyPool configured = villager.level().getServer() == null ? null
                : PlayerRaidLoadoutResources.profile(
                        villager.level().getServer(), villager.getVillagerData().getProfession())
                .map(profile -> profile.pool(difficulty)).orElse(null);
        if (villager.getMainHandItem().isEmpty()) {
            ItemStack weapon = configured == null ? ItemStack.EMPTY : new ItemStack(configured.weapon(random));
            if (weapon.isEmpty() || weapon.is(Items.AIR)) weapon = VillagerCombatRoles.preferredWeapon(villager);
            if (weapon.isEmpty()) {
                List<ItemStack> fallback = List.of(
                        new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE),
                        new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW));
                weapon = fallback.get(random.nextInt(fallback.size())).copy();
            }
            villager.setItemSlot(EquipmentSlot.MAINHAND, prepare(villager, weapon, configured));
            villager.setDropChance(EquipmentSlot.MAINHAND, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
        }
        float armorChance = configured == null
                ? difficulty == Difficulty.HARD ? 0.9F : difficulty == Difficulty.NORMAL ? 0.7F : 0.45F
                : configured.armorChance();
        if (random.nextFloat() < armorChance) {
            PlayerRaidLoadoutResources.WeightedArmorSet configuredArmor =
                    configured == null ? null : configured.armor(random);
            if (configuredArmor != null) {
                equipEmpty(villager, EquipmentSlot.HEAD, configuredArmor.item(EquipmentSlot.HEAD), configured);
                equipEmpty(villager, EquipmentSlot.CHEST, configuredArmor.item(EquipmentSlot.CHEST), configured);
                equipEmpty(villager, EquipmentSlot.LEGS, configuredArmor.item(EquipmentSlot.LEGS), configured);
                equipEmpty(villager, EquipmentSlot.FEET, configuredArmor.item(EquipmentSlot.FEET), configured);
            } else {
                ArmorSet armor = rollArmor(difficulty, random);
                equipEmpty(villager, EquipmentSlot.HEAD, armor.head(), configured);
                equipEmpty(villager, EquipmentSlot.CHEST, armor.chest(), configured);
                equipEmpty(villager, EquipmentSlot.LEGS, armor.legs(), configured);
                equipEmpty(villager, EquipmentSlot.FEET, armor.feet(), configured);
            }
        }
        villager.getPersistentData().putBoolean(EQUIPPED, true);
        villager.setPersistenceRequired();
    }

    private static ArmorSet rollArmor(Difficulty difficulty, RandomSource random) {
        int roll = random.nextInt(100);
        if (difficulty == Difficulty.HARD && roll < 4) {
            return new ArmorSet(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        }
        if ((difficulty == Difficulty.HARD && roll < 58) || (difficulty == Difficulty.NORMAL && roll < 32)) {
            return new ArmorSet(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        }
        if (difficulty != Difficulty.PEACEFUL && roll < 82) {
            return new ArmorSet(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        }
        return new ArmorSet(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
    }

    private static void equipEmpty(
            Villager villager, EquipmentSlot slot, net.minecraft.world.item.Item item,
            PlayerRaidLoadoutResources.DifficultyPool configured) {
        if (!villager.getItemBySlot(slot).isEmpty()) {
            return;
        }
        ItemStack stack = prepare(villager, new ItemStack(item), configured);
        villager.setItemSlot(slot, stack);
        villager.setDropChance(slot, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
    }

    private static ItemStack prepare(
            Villager villager, ItemStack stack, PlayerRaidLoadoutResources.DifficultyPool configured) {
        if (configured == null) {
            return VillagerRetaliationCombatWeaponFactory.prepareEquippedCombatWeapon(villager, stack);
        }
        if (villager.level() instanceof ServerLevel level
                && villager.getRandom().nextFloat() < configured.enchantChance()) {
            EnchantmentHelper.enchantItemFromProvider(
                    stack, level.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                    level.getCurrentDifficultyAt(villager.blockPosition()), villager.getRandom());
        }
        return stack;
    }

    private record ArmorSet(
            net.minecraft.world.item.Item head,
            net.minecraft.world.item.Item chest,
            net.minecraft.world.item.Item legs,
            net.minecraft.world.item.Item feet) {
    }
}
