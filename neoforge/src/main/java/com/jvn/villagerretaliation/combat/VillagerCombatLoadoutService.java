package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import com.jvn.villagerretaliation.party.PartyWeaponPreference;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** Keeps a party villager's durable weapon preference separate from its current order. */
public final class VillagerCombatLoadoutService {
    private VillagerCombatLoadoutService() {
    }

    public static PartyWeaponPreference preference(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return PartyWeaponPreference.AUTO;
        }
        return PartyService.getPartyForVillager(level, villager.getUUID())
                .map(party -> party.villager(villager.getUUID()))
                .map(PartyVillagerRecord::weaponPreference)
                .orElse(PartyWeaponPreference.AUTO);
    }

    public static void applyPreference(Villager villager, PartyWeaponPreference preference) {
        if (villager == null || preference == null || preference == PartyWeaponPreference.AUTO) {
            return;
        }
        ensurePreferredWeapon(villager, preference);
    }

    public static boolean hasPersistentEquippedPreference(Villager villager) {
        return preference(villager) != PartyWeaponPreference.AUTO;
    }

    public static boolean maintainEquippedPreference(Villager villager) {
        return hasPersistentEquippedPreference(villager) && ensurePreferredWeapon(villager);
    }

    public static boolean ensurePreferredWeapon(Villager villager) {
        return ensurePreferredWeapon(villager, preference(villager));
    }

    private static boolean ensurePreferredWeapon(Villager villager, PartyWeaponPreference preference) {
        if (preference == PartyWeaponPreference.AUTO) {
            return false;
        }
        Predicate<ItemStack> preferred = preference == PartyWeaponPreference.RANGED
                ? stack -> canUseRangedWeapon(villager, stack)
                : VillagerRetaliationVillagerWeapons::isMeleeWeapon;
        if (tryEquip(villager, preferred)) {
            return true;
        }

        // Retain the preference, but fall back safely when its class or ammunition is absent.
        Predicate<ItemStack> fallback = preference == PartyWeaponPreference.RANGED
                ? VillagerRetaliationVillagerWeapons::isMeleeWeapon
                : stack -> canUseRangedWeapon(villager, stack);
        return tryEquip(villager, fallback);
    }

    static boolean equipCombatWeapon(Villager villager, Predicate<ItemStack> predicate) {
        return villager != null && predicate != null && tryEquip(villager, predicate);
    }

    static boolean hasCombatWeapon(Villager villager, Predicate<ItemStack> predicate) {
        if (villager == null || predicate == null || VillagerInventoryAccess.hasOpenInventory(villager)) {
            return false;
        }
        if (predicate.test(villager.getMainHandItem())) {
            return true;
        }
        if (HiredJobInventory.isJobInventoryAvailable(villager)
                && !HiredJobInventory.getJobInventory(villager).findTool(predicate).isEmpty()) {
            return true;
        }
        return VillagerInventoryAccess.hasCarriedItem(villager, predicate);
    }

    static boolean canUseRangedWeapon(Villager villager, ItemStack stack) {
        if (!isUsableRanged(stack)) {
            return false;
        }
        if (HiredRangedAmmo.canUseRangedAttack(villager, stack)) {
            return true;
        }
        ItemStack equippedWeapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
        return stack.getItem() instanceof CrossbowItem
                && ItemStack.isSameItem(stack, equippedWeapon)
                && (CrossbowItem.isCharged(stack)
                || VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(villager)
                || VillagerRangedCombatHelper.hasActiveCrossbowCycle(villager));
    }

    private static boolean tryEquip(Villager villager, Predicate<ItemStack> predicate) {
        if (predicate.test(villager.getMainHandItem())) {
            return true;
        }

        // Party/job equipment and supply slots are authoritative before personal inventory.
        if (HiredJobInventory.isJobInventoryAvailable(villager)) {
            if (VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)) {
                VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
                if (predicate.test(villager.getMainHandItem())) {
                    return true;
                }
            }
            ItemStack equipped = HiredJobInventory.getJobInventory(villager)
                    .equipBestTool(predicate, ignored -> 0.0D);
            if (!equipped.isEmpty()) {
                return true;
            }
        }
        if (VillagerInventoryAccess.tryBorrowCombatWeapon(villager, predicate)) {
            return predicate.test(villager.getMainHandItem());
        }
        return predicate.test(villager.getMainHandItem());
    }

    private static boolean isUsableRanged(ItemStack stack) {
        return VillagerRetaliationVillagerWeapons.isRangedWeapon(stack);
    }

}
