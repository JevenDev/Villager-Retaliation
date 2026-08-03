package com.jvn.villagerretaliation.duel;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A server-side, datapack-defined set of temporary equipment for both duelists.
 */
public record DuelKit(
        ResourceLocation id,
        String name,
        String description,
        int sortOrder,
        boolean bringYourOwn,
        boolean rangedCombat,
        Participant player,
        Participant villager) {

    public DuelKit {
        if (id == null) throw new IllegalArgumentException("Duel kit id cannot be null");
        name = name == null || name.isBlank() ? id.toString() : name;
        description = description == null || description.isBlank() ? name : description;
        player = player == null ? Participant.EMPTY : player;
        villager = villager == null ? Participant.EMPTY : villager;
        if (bringYourOwn && (!player.isEmpty() || !villager.isEmpty())) {
            throw new IllegalArgumentException("Bring-your-own kits cannot assign items");
        }
    }

    public record Participant(List<InventoryItem> inventory, Map<EquipmentSlot, ItemStack> equipment) {
        public static final Participant EMPTY = new Participant(List.of(), Map.of());

        public Participant {
            inventory = inventory == null ? List.of() : List.copyOf(inventory);
            equipment = equipment == null ? Map.of() : Map.copyOf(equipment);
        }

        public boolean isEmpty() {
            return this.inventory.isEmpty() && this.equipment.isEmpty();
        }
    }

    public record InventoryItem(int slot, ItemStack stack) {
        public InventoryItem {
            if (slot < 0) throw new IllegalArgumentException("Inventory slot cannot be negative");
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException("Duel kit inventory item cannot be empty");
            }
            stack = stack.copy();
        }
    }

    public record Summary(ResourceLocation id, String name, String description) {
    }
}
