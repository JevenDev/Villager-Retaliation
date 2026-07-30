package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import com.jvn.villagerretaliation.party.PartyWeaponPreference;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
        if (VillagerRetaliationVillagerCombatUtil.isInCombat(villager)) {
            ensurePreferredWeapon(villager, preference);
        } else {
            stowIdleWeapon(villager);
        }
    }

    public static boolean hasPersistentEquippedPreference(Villager villager) {
        return preference(villager) != PartyWeaponPreference.AUTO;
    }

    public static boolean maintainEquippedPreference(Villager villager) {
        return VillagerRetaliationVillagerCombatUtil.isInCombat(villager)
                && hasPersistentEquippedPreference(villager)
                && ensurePreferredWeapon(villager);
    }

    /** Stows held weapons, and optionally shields, while leaving them available for later combat. */
    public static boolean stowWeapons(Villager villager, boolean includeShield) {
        if (villager == null || VillagerInventoryAccess.hasOpenInventory(villager)
                || DuelService.isParticipant(villager)) {
            return false;
        }
        boolean changed = false;
        if (VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)) {
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
            changed = !VillagerInventoryAccess.hasBorrowedCombatWeapon(villager);
        }
        changed |= HiredJobInventory.stowCombatEquipment(villager, includeShield);
        changed |= stowPersonalHand(villager, EquipmentSlot.MAINHAND,
                stack -> VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)
                        || includeShield && stack.is(Items.SHIELD));
        if (includeShield) {
            if (villager.isUsingItem() && villager.getUseItem().is(Items.SHIELD)) {
                villager.stopUsingItem();
            }
            changed |= stowPersonalHand(villager, EquipmentSlot.OFFHAND, stack -> stack.is(Items.SHIELD));
        }
        return changed;
    }

    public static boolean stowIdleWeapon(Villager villager) {
        return villager != null
                && !VillagerRetaliationVillagerCombatUtil.isInCombat(villager)
                && !HiredVillagerWorkService.isActivelyWorking(villager)
                && !VillagerEquipmentMending.shouldKeepMainHandEquipped(villager)
                && stowWeapons(villager, false);
    }

    private static boolean stowPersonalHand(
            Villager villager, EquipmentSlot slot, Predicate<ItemStack> predicate) {
        if (HiredJobInventory.hasJobEquipmentForSlot(villager, slot)) {
            return false;
        }
        ItemStack held = villager.getItemBySlot(slot);
        if (held.isEmpty() || !predicate.test(held)) {
            return false;
        }
        ItemStack remainder = VillagerInventoryAccess.addItem(villager, held.copy());
        if (!remainder.isEmpty()) {
            return false;
        }
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, ItemStack.EMPTY);
        return true;
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
        if (!DuelService.isParticipant(villager)
                && HiredJobInventory.isJobInventoryAvailable(villager)
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
        if (!DuelService.isParticipant(villager)
                && HiredJobInventory.isJobInventoryAvailable(villager)) {
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
