package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;

/** Owns automatic off-hand totems without giving the equipped stack a second inventory owner. */
public final class VillagerDefensiveLoadoutService {
    private static final String STATE_TAG = "VillagerRetaliationDefensiveLoadout";
    private static final String GUARD_STATE_TAG = "VillagerRetaliationGuardShieldLoan";
    private static final String SOURCE_KIND_TAG = "SourceKind";
    private static final String SOURCE_SLOT_TAG = "SourceSlot";
    private static final String BORROWED_STACK_TAG = "BorrowedStack";
    private static final String DISPLACED_STACK_TAG = "DisplacedStack";
    private static final String MANUAL_OFFHAND_TAG = "VillagerRetaliationManualOffhand";
    private static final String SOURCE_PERSONAL = "PERSONAL";
    private static final String SOURCE_JOB = "JOB";

    private VillagerDefensiveLoadoutService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (villager == null || villager.level().isClientSide || villager.isBaby() || !villager.isAlive()) {
            return;
        }
        if (!com.jvn.villagerretaliation.party.PartyQuickCommandService.isStandingGuard(villager)
                && hasBorrowedGuardShield(villager)) {
            returnGuardShield(villager);
        }
        reconcileChangedLoan(villager);
        if (VillagerInventoryAccess.hasOpenInventory(villager)
                || villager.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || villager.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || hasManualOffhandOverride(villager)) {
            return;
        }
        if (!villager.getOffhandItem().isEmpty()
                && !VillagerRetaliationVillagerEquipment.isRoleOffhand(villager)
                && !(villager.getOffhandItem().is(Items.SHIELD)
                && villager.getPersistentData().getBoolean("VillagerRetaliationArmorerShieldRolled"))) {
            markManualOffhand(villager, true);
            return;
        }
        borrowTotem(villager);
    }

    public static void onLivingUseTotem(LivingUseTotemEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Villager villager)) {
            return;
        }

        InteractionHand hand = event.getHandHolding();
        EquipmentSlot equipmentSlot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;

        // Vanilla shrinks the live hand stack after this event. Job equipment and
        // tracked main-hand gear also retain an authoritative saved stack, so consume
        // that owner now or equipment maintenance will recreate the used totem.
        HiredJobInventory.consumeEquippedTotem(villager, equipmentSlot, event.getTotem());
        if (hand == InteractionHand.MAIN_HAND) {
            VillagerRetaliationVillagerEquipment.forgetConsumedMainHand(villager, event.getTotem());
        } else if (hasBorrowedTotem(villager)) {
            clearState(villager);
        }
    }

    public static void prepareForInventoryAccess(Villager villager) {
        com.jvn.villagerretaliation.party.PartyQuickCommandService.prepareGuardAttack(villager);
        returnGuardShield(villager);
        returnBorrowedTotem(villager);
    }

    public static boolean ensureGuardShield(Villager villager) {
        if (villager.getMainHandItem().is(Items.SHIELD)) {
            return true;
        }
        if (VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)) {
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        }
        Loan existing = readLoan(villager, GUARD_STATE_TAG);
        if (existing != null) {
            VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, existing.borrowed(), 0.0F);
            return true;
        }
        Source source = findSource(villager, stack -> stack.is(Items.SHIELD));
        if (source == null || !replaceSource(villager, source, ItemStack.EMPTY)) {
            return false;
        }
        ItemStack shield = source.stack().copyWithCount(1);
        ItemStack displaced = villager.getMainHandItem().copy();
        persist(villager, GUARD_STATE_TAG, source.kind(), source.slot(), shield, displaced);
        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, shield, 0.0F);
        return true;
    }

    public static boolean hasBorrowedGuardShield(Villager villager) {
        return !state(villager, GUARD_STATE_TAG).isEmpty();
    }

    public static void returnGuardShield(Villager villager) {
        Loan loan = readLoan(villager, GUARD_STATE_TAG);
        if (loan == null) {
            return;
        }
        ItemStack hand = villager.getMainHandItem();
        if (hand.is(Items.SHIELD)) {
            if (!writeEmptySource(villager, loan, hand.copy())) {
                ItemStack remainder = VillagerInventoryContainer.addItem(villager, hand.copy());
                if (!remainder.isEmpty()) {
                    villager.spawnAtLocation(remainder);
                }
            }
        }
        VillagerRetaliationVillagerEquipment.restoreMainHand(villager, loan.displaced().copy());
        clearState(villager, GUARD_STATE_TAG);
    }

    public static void markManualOffhand(Villager villager, boolean manual) {
        if (villager == null) {
            return;
        }
        if (manual) {
            villager.getPersistentData().putBoolean(MANUAL_OFFHAND_TAG, true);
        } else {
            villager.getPersistentData().remove(MANUAL_OFFHAND_TAG);
        }
    }

    public static boolean hasManualOffhandOverride(Villager villager) {
        if (!villager.getPersistentData().getBoolean(MANUAL_OFFHAND_TAG)) {
            return false;
        }
        boolean occupied = !villager.getOffhandItem().isEmpty()
                || HiredJobInventory.hasJobEquipmentForSlot(villager, EquipmentSlot.OFFHAND);
        if (!occupied) {
            markManualOffhand(villager, false);
        }
        return occupied;
    }

    public static boolean hasBorrowedTotem(Villager villager) {
        return !state(villager).isEmpty();
    }

    static void returnBorrowedTotem(Villager villager) {
        Loan loan = readLoan(villager);
        if (loan == null) {
            return;
        }
        ItemStack hand = villager.getOffhandItem();
        if (hand.isEmpty() || !ItemStack.isSameItemSameComponents(hand, loan.borrowed())) {
            clearState(villager);
            return;
        }
        if (!writeSource(villager, loan, hand.copy())) {
            ItemStack remainder = VillagerInventoryContainer.addItem(villager, hand.copy());
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
        }
        VillagerRetaliationVillagerEquipment.setRoleEquipment(
                villager, EquipmentSlot.OFFHAND, loan.displaced().copy());
        clearState(villager);
    }

    private static void reconcileChangedLoan(Villager villager) {
        Loan loan = readLoan(villager);
        if (loan == null) {
            return;
        }
        ItemStack hand = villager.getOffhandItem();
        if (hand.isEmpty()) {
            clearState(villager);
        } else if (!ItemStack.isSameItemSameComponents(hand, loan.borrowed())) {
            clearState(villager);
            markManualOffhand(villager, true);
        }
    }

    private static void borrowTotem(Villager villager) {
        Source source = findSource(villager, stack -> stack.is(Items.TOTEM_OF_UNDYING));
        if (source == null) {
            return;
        }
        ItemStack displaced = villager.getOffhandItem().copy();
        ItemStack borrowed = source.stack().copyWithCount(1);
        if (!replaceSource(villager, source, displaced)) {
            return;
        }
        persist(villager, STATE_TAG, source.kind(), source.slot(), borrowed, displaced);
        VillagerRetaliationVillagerEquipment.setRoleEquipment(villager, EquipmentSlot.OFFHAND, borrowed);
    }

    private static Source findSource(Villager villager, java.util.function.Predicate<ItemStack> predicate) {
        if (HiredJobInventory.isJobInventoryAvailable(villager)) {
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (predicate.test(stack)) {
                    return new Source(SOURCE_JOB, slot, stack);
                }
            }
        }
        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (predicate.test(stack)) {
                return new Source(SOURCE_PERSONAL, slot, stack);
            }
        }
        return null;
    }

    private static boolean replaceSource(Villager villager, Source source, ItemStack replacement) {
        if (SOURCE_JOB.equals(source.kind())) {
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            ItemStack current = inventory.getItem(source.slot());
            if (!ItemStack.isSameItemSameComponents(current, source.stack())) {
                return false;
            }
            inventory.setItem(source.slot(), replacement.copy());
            return true;
        }
        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        ItemStack current = inventory.get(source.slot());
        if (!ItemStack.isSameItemSameComponents(current, source.stack())) {
            return false;
        }
        inventory.set(source.slot(), replacement.copy());
        VillagerInventoryContainer.saveFullInventory(villager, inventory);
        return true;
    }

    private static boolean writeSource(Villager villager, Loan loan, ItemStack stack) {
        if (SOURCE_JOB.equals(loan.kind())) {
            if (!HiredJobInventory.isJobInventoryAvailable(villager)) {
                return false;
            }
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            if (!sameStack(inventory.getItem(loan.slot()), loan.displaced())) {
                return false;
            }
            inventory.setItem(loan.slot(), stack);
            return true;
        }
        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        if (loan.slot() < 0 || loan.slot() >= inventory.size()
                || !sameStack(inventory.get(loan.slot()), loan.displaced())) {
            return false;
        }
        inventory.set(loan.slot(), stack);
        VillagerInventoryContainer.saveFullInventory(villager, inventory);
        return true;
    }

    private static boolean writeEmptySource(Villager villager, Loan loan, ItemStack stack) {
        if (SOURCE_JOB.equals(loan.kind())) {
            if (!HiredJobInventory.isJobInventoryAvailable(villager)) {
                return false;
            }
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
            if (!inventory.getItem(loan.slot()).isEmpty()) {
                return false;
            }
            inventory.setItem(loan.slot(), stack);
            return true;
        }
        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        if (loan.slot() < 0 || loan.slot() >= inventory.size() || !inventory.get(loan.slot()).isEmpty()) {
            return false;
        }
        inventory.set(loan.slot(), stack);
        VillagerInventoryContainer.saveFullInventory(villager, inventory);
        return true;
    }

    private static void persist(
            Villager villager, String stateKey, String kind, int slot, ItemStack borrowed, ItemStack displaced) {
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE_KIND_TAG, kind);
        tag.putInt(SOURCE_SLOT_TAG, slot);
        tag.put(BORROWED_STACK_TAG, borrowed.saveOptional(villager.level().registryAccess()));
        if (!displaced.isEmpty()) {
            tag.put(DISPLACED_STACK_TAG, displaced.saveOptional(villager.level().registryAccess()));
        }
        villager.getPersistentData().put(stateKey, tag);
    }

    private static Loan readLoan(Villager villager) {
        return readLoan(villager, STATE_TAG);
    }

    private static Loan readLoan(Villager villager, String stateKey) {
        CompoundTag tag = state(villager, stateKey);
        if (tag.isEmpty() || !tag.contains(BORROWED_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            clearState(villager, stateKey);
            return null;
        }
        ItemStack borrowed = ItemStack.parseOptional(
                villager.level().registryAccess(), tag.getCompound(BORROWED_STACK_TAG));
        if (borrowed.isEmpty()) {
            clearState(villager, stateKey);
            return null;
        }
        ItemStack displaced = tag.contains(DISPLACED_STACK_TAG, CompoundTag.TAG_COMPOUND)
                ? ItemStack.parseOptional(villager.level().registryAccess(), tag.getCompound(DISPLACED_STACK_TAG))
                : ItemStack.EMPTY;
        return new Loan(tag.getString(SOURCE_KIND_TAG), tag.getInt(SOURCE_SLOT_TAG), borrowed, displaced);
    }

    private static CompoundTag state(Villager villager) {
        return state(villager, STATE_TAG);
    }

    private static CompoundTag state(Villager villager, String stateKey) {
        return villager.getPersistentData().getCompound(stateKey);
    }

    private static void clearState(Villager villager) {
        clearState(villager, STATE_TAG);
    }

    private static void clearState(Villager villager, String stateKey) {
        villager.getPersistentData().remove(stateKey);
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private record Source(String kind, int slot, ItemStack stack) {
    }

    private record Loan(String kind, int slot, ItemStack borrowed, ItemStack displaced) {
    }
}
