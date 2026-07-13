package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class PartyVillagerDropCollection {
    private static final String SLAIN_ENTITY_DROP_TAG = "VillagerRetaliationSlainEntityDrop";
    private static final Set<ItemStack> LOADED_SLAIN_ENTITY_DROPS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PartyVillagerDropCollection() {
    }

    public static void clearRuntimeState() {
        LOADED_SLAIN_ENTITY_DROPS.clear();
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
        if (mode == PartyDropCollectionMode.OFF
                || mode == PartyDropCollectionMode.SLAIN_ENTITIES
                && !itemEntity.getPersistentData().getBoolean(SLAIN_ENTITY_DROP_TAG)) {
            return false;
        }

        collectAny(villager, itemEntity);
        return true;
    }

    public static void onArrowEntityLoaded(AbstractArrow arrow) {
        if (arrow == null || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        boolean consumedCrossbowProjectile =
                HiredRangedAmmo.clearConsumedCrossbowProjectileMarker(arrow.getPickupItemStackOrigin());
        if (!(arrow.getOwner() instanceof Villager villager)
                || !PartyService.isRecruitedPartyVillager(level, villager.getUUID())
                || !HiredRangedAmmo.isAmmo(arrow.getPickupItemStackOrigin())
                || arrow.pickup != AbstractArrow.Pickup.DISALLOWED
                && !(arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && consumedCrossbowProjectile)) {
            return;
        }
        // Vanilla makes a player's consumed projectile recoverable while leaving
        // intangible projectiles (Infinity and Multishot copies) CREATIVE_ONLY.
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    public static int collectAny(Villager villager, ItemEntity itemEntity) {
        if (villager == null || itemEntity == null || !itemEntity.isAlive() || itemEntity.hasPickUpDelay()) {
            return 0;
        }

        ItemStack groundStack = itemEntity.getItem();
        ItemStack collected = groundStack.copy();
        ItemStack remainder = HiredJobInventory.getJobInventory(villager).insertPlainOutput(collected);
        int moved = groundStack.getCount() - remainder.getCount();
        if (moved <= 0) {
            return 0;
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
        return moved;
    }

    public static boolean isRecoverableArrow(Villager villager, AbstractArrow arrow) {
        return villager != null
                && arrow != null
                && arrow.isAlive()
                && arrow.getOwner() == villager
                && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                && (((AbstractArrowAccessor) arrow).villagerretaliation$isInGround() || arrow.isNoPhysics())
                && arrow.shakeTime <= 0
                && HiredRangedAmmo.isAmmo(arrow.getPickupItemStackOrigin());
    }

    public static int collectArrow(Villager villager, AbstractArrow arrow) {
        if (!isRecoverableArrow(villager, arrow)) {
            return 0;
        }
        ItemStack remainder = HiredJobInventory.getJobInventory(villager)
                .insertPlainSupply(arrow.getPickupItemStackOrigin().copyWithCount(1));
        if (!remainder.isEmpty()) {
            return 0;
        }
        villager.take(arrow, 1);
        arrow.discard();
        return 1;
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
