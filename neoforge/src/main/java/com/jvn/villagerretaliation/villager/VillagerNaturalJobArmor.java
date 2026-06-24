package com.jvn.villagerretaliation.villager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class VillagerNaturalJobArmor {
    private static final String PENDING_TAG = "VillagerRetaliationNaturalJobArmorPending";
    private static final String ROLLED_TAG = "VillagerRetaliationNaturalJobArmorRolled";
    private static final String ARMOR_TAG = "VillagerRetaliationNaturalJobArmor";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    private VillagerNaturalJobArmor() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()
                || !(event.getEntity() instanceof AbstractVillager villager)
                || !(event.getEntity() instanceof VillagerDataHolder)
                || !(event.getLevel() instanceof ServerLevel)
                || villager.isBaby()
                || !isNaturalSpawn(villager.getSpawnType())) {
            return;
        }

        villager.getPersistentData().putBoolean(PENDING_TAG, true);
    }

    public static void maybeRoll(AbstractVillager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || !(villager instanceof VillagerDataHolder villagerDataHolder)
                || villager.isBaby()
                || !villager.getPersistentData().getBoolean(PENDING_TAG)
                || villager.getPersistentData().getBoolean(ROLLED_TAG)) {
            return;
        }

        VillagerNaturalJobArmorResources.ArmorProfile profile = VillagerNaturalJobArmorResources
                .profile(level.getServer(), villagerDataHolder.getVillagerData().getProfession())
                .orElse(null);
        if (profile == null) {
            if (villagerDataHolder.getVillagerData().getProfession() != VillagerProfession.NONE) {
                villager.getPersistentData().putBoolean(ROLLED_TAG, true);
                villager.getPersistentData().remove(PENDING_TAG);
            }
            return;
        }

        villager.getPersistentData().putBoolean(ROLLED_TAG, true);
        villager.getPersistentData().remove(PENDING_TAG);

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(villager.blockPosition());
        RandomSource random = villager.getRandom();
        if (!profile.armorChance().passes(random, difficulty.getDifficulty())) {
            return;
        }

        VillagerNaturalJobArmorResources.WeightedArmorSet armorSet = profile
                .selectArmorSet(random, difficulty.getDifficulty())
                .orElse(null);
        if (armorSet == null) {
            return;
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!villager.getItemBySlot(slot).isEmpty()) {
                continue;
            }
            VillagerNaturalJobArmorResources.WeightedArmorSet slotArmorSet = profile.armorSetForSlot(
                    random,
                    difficulty.getDifficulty(),
                    armorSet);
            ItemStack stack = new ItemStack(slotArmorSet.items().itemForSlot(slot));
            if (stack.isEmpty() || stack.is(Items.AIR)) {
                continue;
            }
            maybeEnchant(level, random, difficulty, stack, profile);
            villager.setItemSlot(slot, stack);
            villager.setDropChance(slot, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
            rememberNaturalArmor(villager, slot, stack);

            if (slot != EquipmentSlot.HEAD && !profile.nextPieceChance().passes(random, difficulty.getDifficulty())) {
                break;
            }
        }
    }

    public static boolean isNaturalArmor(AbstractVillager villager, EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty() || !slot.isArmor()) {
            return false;
        }
        CompoundTag armorTag = villager.getPersistentData().getCompound(ARMOR_TAG);
        CompoundTag slotTag = armorTag.getCompound(slot.getName());
        if (slotTag.isEmpty()) {
            return false;
        }
        ItemStack remembered = ItemStack.parseOptional(villager.registryAccess(), slotTag);
        return !remembered.isEmpty() && ItemStack.isSameItem(stack, remembered);
    }

    public static void clearNaturalArmorSlot(AbstractVillager villager, EquipmentSlot slot) {
        if (!slot.isArmor()) {
            return;
        }
        CompoundTag persistentData = villager.getPersistentData();
        CompoundTag armorTag = persistentData.getCompound(ARMOR_TAG);
        if (armorTag.isEmpty()) {
            return;
        }
        armorTag.remove(slot.getName());
        if (armorTag.isEmpty()) {
            persistentData.remove(ARMOR_TAG);
        } else {
            persistentData.put(ARMOR_TAG, armorTag);
        }
    }

    private static boolean isNaturalSpawn(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.STRUCTURE;
    }

    private static void maybeEnchant(
            ServerLevel level,
            RandomSource random,
            DifficultyInstance difficulty,
            ItemStack stack,
            VillagerNaturalJobArmorResources.ArmorProfile profile
    ) {
        if (profile.enchantChance().passes(random, difficulty.getDifficulty())) {
            EnchantmentHelper.enchantItemFromProvider(
                    stack,
                    level.registryAccess(),
                    VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                    difficulty,
                    random
            );
        }
    }

    private static void rememberNaturalArmor(AbstractVillager villager, EquipmentSlot slot, ItemStack stack) {
        CompoundTag armorTag = villager.getPersistentData().getCompound(ARMOR_TAG);
        armorTag.put(slot.getName(), stack.saveOptional(villager.registryAccess()));
        villager.getPersistentData().put(ARMOR_TAG, armorTag);
    }

}
