package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class PartyVillagerDropCollection {
    private static final String SLAIN_ENTITY_DROP_TAG = "VillagerRetaliationSlainEntityDrop";
    private static final Set<ItemStack> LOADED_SLAIN_ENTITY_DROPS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PartyVillagerDropCollection() {
    }

    public static void markSlainEntityDrops(LivingDropsEvent event) {
        if (event == null) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                drop.getPersistentData().putBoolean(SLAIN_ENTITY_DROP_TAG, true);
                LOADED_SLAIN_ENTITY_DROPS.add(stack);
            }
        }
    }

    public static void onItemEntityLoaded(ItemEntity itemEntity) {
        if (itemEntity != null && itemEntity.getPersistentData().getBoolean(SLAIN_ENTITY_DROP_TAG)) {
            LOADED_SLAIN_ENTITY_DROPS.add(itemEntity.getItem());
        }
    }

    public static Boolean wantsToPickUp(ServerLevel level, Villager villager, ItemStack stack) {
        PartyDropCollectionMode mode = mode(level, villager);
        if (mode == PartyDropCollectionMode.OFF) {
            return null;
        }
        if (mode == PartyDropCollectionMode.SLAIN_ENTITIES && !LOADED_SLAIN_ENTITY_DROPS.contains(stack)) {
            return null;
        }
        return true;
    }

    public static boolean capturePickup(ServerLevel level, Villager villager, ItemEntity itemEntity) {
        PartyDropCollectionMode mode = mode(level, villager);
        ItemStack groundStack = itemEntity.getItem();
        if (mode == PartyDropCollectionMode.OFF
                || mode == PartyDropCollectionMode.SLAIN_ENTITIES
                && !itemEntity.getPersistentData().getBoolean(SLAIN_ENTITY_DROP_TAG)) {
            return false;
        }

        ItemStack collected = groundStack.copy();
        ItemStack remainder = HiredJobInventory.getJobInventory(villager).insertPlainOutput(collected);
        int moved = groundStack.getCount() - remainder.getCount();
        if (moved <= 0) {
            return true;
        }

        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, moved);
        if (remainder.isEmpty()) {
            itemEntity.discard();
        } else {
            remainder.setCount(groundStack.getCount() - moved);
            itemEntity.setItem(remainder);
            onItemEntityLoaded(itemEntity);
        }
        return true;
    }

    private static PartyDropCollectionMode mode(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return PartyDropCollectionMode.OFF;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        return record == null ? PartyDropCollectionMode.OFF : record.dropCollectionMode();
    }

}
