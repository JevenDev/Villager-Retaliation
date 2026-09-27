package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class SettlementMendingEquipment {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementMendingVersion";
    private final long version;
    private final Map<EquipmentSlot, CompoundTag> equipment;

    private SettlementMendingEquipment(long version, Map<EquipmentSlot, CompoundTag> equipment) {
        if (version <= 0L) throw new IllegalArgumentException("Mending equipment has no revision");
        this.version = version;
        this.equipment = equipment;
    }

    static SettlementMendingEquipment capture(Villager villager, SettlementMendingEquipment previous, Map<EquipmentSlot, ItemStack> before) {
        long version = Math.incrementExact(Math.max(previous == null ? 0L : previous.version,
                villager.getPersistentData().getLong(VERSION_TAG)));
        var equipment = new EnumMap<EquipmentSlot, CompoundTag>(EquipmentSlot.class);
        if (previous != null) equipment.putAll(previous.equipment);
        boolean changed = false;
        for (var slot : EquipmentSlot.values()) {
            var stack = villager.getItemBySlot(slot);
            var original = before.get(slot);
            if (original != null && !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() < original.getDamageValue()) {
                var entry = new CompoundTag();
                entry.putLong("Version", version);
                entry.put("Item", stack.save(villager.registryAccess()));
                equipment.put(slot, entry);
                changed = true;
            }
        }
        if (!changed) return previous;
        villager.getPersistentData().putLong(VERSION_TAG, version);
        return new SettlementMendingEquipment(version, equipment);
    }

    boolean restore(Villager villager) {
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) return false;
        for (var entry : this.equipment.entrySet()) {
            if (entry.getValue().getLong("Version") <= villager.getPersistentData().getLong(VERSION_TAG)) continue;
            var repaired = ItemStack.parse(villager.registryAccess(), entry.getValue().getCompound("Item"))
                    .orElseThrow(() -> new IllegalArgumentException("Mending equipment contains an invalid item"));
            var current = villager.getItemBySlot(entry.getKey());
            if (current.isEmpty() || !current.isDamageableItem()) continue;
            var comparable = current.copy();
            comparable.setDamageValue(repaired.getDamageValue());
            if (ItemStack.isSameItemSameComponents(comparable, repaired)) {
                current.setDamageValue(Math.min(current.getDamageValue(), repaired.getDamageValue()));
                HiredJobInventory.synchronizeEquipmentDurability(villager, entry.getKey());
            }
        }
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Version", this.version);
        var equipment = new CompoundTag();
        this.equipment.forEach((slot, stack) -> equipment.put(slot.getName(), stack.copy()));
        tag.put("Equipment", equipment);
        return tag;
    }

    static SettlementMendingEquipment load(CompoundTag tag) {
        var equipment = new EnumMap<EquipmentSlot, CompoundTag>(EquipmentSlot.class);
        var entries = tag.getCompound("Equipment");
        for (var name : entries.getAllKeys()) {
            var slot = EquipmentSlot.byName(name);
            var entry = entries.getCompound(name);
            if (entry.getCompound("Item").isEmpty() || entry.getLong("Version") <= 0L || entry.getLong("Version") > tag.getLong("Version")) {
                throw new IllegalArgumentException("Mending equipment item or revision is invalid");
            }
            equipment.put(slot, entries.getCompound(name).copy());
        }
        return new SettlementMendingEquipment(tag.getLong("Version"), equipment);
    }
}
