package com.jvn.villagerretaliation.duel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class DuelEquipment {
    private DuelEquipment() {}

    static Snapshots prepare(ServerPlayer player, Villager villager, DuelLoadout loadout) {
        Snapshots snapshots = new Snapshots(PlayerSnapshot.capture(player), VillagerSnapshot.capture(villager));
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        player.removeAllEffects();
        villager.setHealth(villager.getMaxHealth());
        villager.setAbsorptionAmount(0.0F);
        villager.removeAllEffects();
        villager.setCanPickUpLoot(false);
        if (loadout == DuelLoadout.BRING_YOUR_OWN) return snapshots;
        clear(player.getInventory());
        clear(villager);
        switch (loadout) {
            case BARE_HANDED -> {}
            case MELEE -> melee(player, villager);
            case RANGED -> ranged(player, villager);
            case ARMORED -> armored(player, villager);
            case BRING_YOUR_OWN -> {}
        }
        return snapshots;
    }

    private static void clear(Inventory inventory) {
        inventory.items.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.armor.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.offhand.replaceAll(ignored -> ItemStack.EMPTY);
        inventory.setChanged();
    }

    private static void clear(Villager villager) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            villager.getInventory().setItem(i, ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            villager.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void melee(ServerPlayer player, Villager villager) {
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    private static void ranged(ServerPlayer player, Villager villager) {
        player.getInventory().setItem(0, new ItemStack(Items.BOW));
        player.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        villager.getInventory().addItem(new ItemStack(Items.ARROW, 64));
    }

    private static void armored(ServerPlayer player, Villager villager) {
        melee(player, villager);
        player.getInventory().setItem(1, new ItemStack(Items.IRON_AXE));
        villager.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        equip(player, villager, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        equip(player, villager, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        equip(player, villager, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        equip(player, villager, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    private static void equip(ServerPlayer player, Villager villager, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack.copy());
        villager.setItemSlot(slot, stack.copy());
    }

    record Snapshots(PlayerSnapshot player, VillagerSnapshot villager) {}

    record PlayerSnapshot(List<ItemStack> items, List<ItemStack> armor, List<ItemStack> offhand,
                          int selectedSlot, float health, float absorption, int food, float saturation,
                          List<MobEffectInstance> effects) {
        static PlayerSnapshot capture(ServerPlayer player) {
            return new PlayerSnapshot(copy(player.getInventory().items), copy(player.getInventory().armor),
                    copy(player.getInventory().offhand), player.getInventory().selected, player.getHealth(), player.getAbsorptionAmount(),
                    player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                    player.getActiveEffects().stream().map(MobEffectInstance::new).toList());
        }

        void restore(ServerPlayer player, boolean restoreInventory) {
            if (restoreInventory) {
                restoreList(player.getInventory().items, this.items);
                restoreList(player.getInventory().armor, this.armor);
                restoreList(player.getInventory().offhand, this.offhand);
                player.getInventory().selected = this.selectedSlot;
            }
            player.getInventory().setChanged();
            player.removeAllEffects();
            this.effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, this.health)));
            player.setAbsorptionAmount(this.absorption);
            player.getFoodData().setFoodLevel(this.food);
            player.getFoodData().setSaturation(this.saturation);
        }
    }

    record VillagerSnapshot(List<ItemStack> inventory, Map<EquipmentSlot, ItemStack> equipment,
                            float health, float absorption, List<MobEffectInstance> effects, boolean pickup) {
        static VillagerSnapshot capture(Villager villager) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                inventory.add(villager.getInventory().getItem(i).copy());
            }
            Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) equipment.put(slot, villager.getItemBySlot(slot).copy());
            return new VillagerSnapshot(List.copyOf(inventory), equipment, villager.getHealth(),
                    villager.getAbsorptionAmount(), villager.getActiveEffects().stream().map(MobEffectInstance::new).toList(),
                    villager.canPickUpLoot());
        }

        void restore(Villager villager) {
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                villager.getInventory().setItem(i, i < this.inventory.size() ? this.inventory.get(i).copy() : ItemStack.EMPTY);
            }
            this.equipment.forEach((slot, stack) -> villager.setItemSlot(slot, stack.copy()));
            villager.removeAllEffects();
            this.effects.forEach(effect -> villager.addEffect(new MobEffectInstance(effect)));
            villager.setHealth(Math.min(villager.getMaxHealth(), Math.max(1.0F, this.health)));
            villager.setAbsorptionAmount(this.absorption);
            villager.setCanPickUpLoot(this.pickup);
        }
    }

    private static List<ItemStack> copy(List<ItemStack> source) {
        return source.stream().map(ItemStack::copy).toList();
    }

    private static void restoreList(List<ItemStack> target, List<ItemStack> source) {
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < source.size() ? source.get(i).copy() : ItemStack.EMPTY);
        }
    }
}
