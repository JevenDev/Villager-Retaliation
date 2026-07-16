package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class VillagerRetaliationVillagerEquipment {
    private static final String MAINHAND_STATE_TAG = "VillagerRetaliationMainhandState";
    private static final String MAINHAND_OWNER_TAG = "Owner";
    private static final String MAINHAND_STACK_TAG = "Stack";
    private static final String ROLE_MAINHAND_TAG = "VillagerRetaliationRoleMainhand";
    private static final String ROLE_MAINHAND_ROLLED_KEY_TAG = "VillagerRetaliationRoleMainhandRolledKey";
    private static final String ROLE_MAINHAND_KEY_TAG = "RoleKey";
    private static final String LEGACY_PICKED_UP_MAINHAND_TAG = "VillagerRetaliationPickedUpMainhand";
    private static final String OWNER_MANUAL = "MANUAL";
    private static final String OWNER_PICKED_UP = "PICKED_UP";
    private static final String OWNER_ROLE = "ROLE";

    private VillagerRetaliationVillagerEquipment() {
    }

    public enum MainHandOwner {
        NONE,
        MANUAL,
        PICKED_UP,
        ROLE
    }

    public static void setInventoryEquipment(AbstractVillager villager, EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            setInventoryMainHand(villager, stack);
            return;
        }
        if (villager instanceof Villager regularVillager && slot.isArmor()) {
            VillagerNaturalJobArmor.clearNaturalArmorSlot(regularVillager, slot);
        }
        setEquipment(villager, slot, stack, true);
    }

    public static void setRoleEquipment(AbstractVillager villager, EquipmentSlot slot, ItemStack stack) {
        setEquipment(villager, slot, stack, false);
        villager.setDropChance(slot, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
    }

    public static void setPickedUpMainHand(AbstractVillager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            clearMainHandState(villager);
            setEquipment(villager, EquipmentSlot.MAINHAND, ItemStack.EMPTY, false);
            return;
        }

        setEquipment(villager, EquipmentSlot.MAINHAND, stack, true);
        setPickedUpMainHandState(villager, stack);
    }

    public static void ensureRoleMainHand(AbstractVillager villager, String roleKey, ItemStack stack) {
        if (roleKey.isBlank()) {
            return;
        }
        if (isPlayerManagedMainHand(villager)) {
            return;
        }

        String rolledRoleKey = rolledRoleKey(villager);
        boolean alreadyRolledForRole = roleKey.equals(rolledRoleKey);
        boolean ownsCurrentMainHand = mainHandOwner(villager) == MainHandOwner.ROLE;
        ItemStack currentMainHand = villager.getMainHandItem();
        ItemStack storedRoleStack = roleMainHand(villager);
        ItemStack expectedRoleStack = storedRoleStack.isEmpty() ? stack : storedRoleStack;

        if (stack.isEmpty()) {
            if (ownsCurrentMainHand) {
                setEquipment(villager, EquipmentSlot.MAINHAND, ItemStack.EMPTY, false);
                clearRoleMainHand(villager);
            }
            return;
        }

        if (alreadyRolledForRole) {
            if (ownsCurrentMainHand && currentMainHand.isEmpty()) {
                setEquipment(villager, EquipmentSlot.MAINHAND, expectedRoleStack, false);
                setMainHandDropChance(villager, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
                setRoleMainHandState(villager, roleKey, expectedRoleStack);
            } else if (ownsCurrentMainHand && storedRoleStack.isEmpty() && !currentMainHand.isEmpty()) {
                setRoleMainHandState(villager, roleKey, currentMainHand);
            }
            return;
        }

        setRolledRoleKey(villager, roleKey);
        if (currentMainHand.isEmpty() || ownsCurrentMainHand) {
            setEquipment(villager, EquipmentSlot.MAINHAND, stack, false);
            setMainHandDropChance(villager, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
            setRoleMainHandState(villager, roleKey, stack);
        }
    }

    public static void setTemporaryMainHand(AbstractVillager villager, ItemStack stack, float dropChance) {
        setEquipment(villager, EquipmentSlot.MAINHAND, stack, false);
        setMainHandDropChance(villager, dropChance);
    }

    public static void setMainHandDropChance(AbstractVillager villager, float dropChance) {
        villager.setDropChance(EquipmentSlot.MAINHAND, dropChance);
    }

    public static void restoreMainHand(AbstractVillager villager, ItemStack stack) {
        setEquipment(villager, EquipmentSlot.MAINHAND, stack, !stack.isEmpty());
    }

    public static void restoreVisualMainHand(AbstractVillager villager, ItemStack stack) {
        setEquipment(villager, EquipmentSlot.MAINHAND, stack, false);
    }

    public static void setVisualMainHand(AbstractVillager villager, ItemStack stack) {
        setEquipment(villager, EquipmentSlot.MAINHAND, stack, false);
    }

    public static boolean mainHandMatchesItem(AbstractVillager villager, ItemStack expected) {
        return ItemStack.isSameItem(villager.getMainHandItem(), expected);
    }

    public static boolean mainHandMatchesStack(AbstractVillager villager, ItemStack expected) {
        return ItemStack.isSameItemSameComponents(villager.getMainHandItem(), expected);
    }

    public static MainHandOwner mainHandOwner(AbstractVillager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (persistentData.contains(MAINHAND_STATE_TAG, CompoundTag.TAG_COMPOUND)) {
            CompoundTag stateTag = persistentData.getCompound(MAINHAND_STATE_TAG);
            if (stateTag.contains(MAINHAND_OWNER_TAG)) {
                return switch (stateTag.getString(MAINHAND_OWNER_TAG)) {
                    case OWNER_MANUAL -> MainHandOwner.MANUAL;
                    case OWNER_PICKED_UP -> MainHandOwner.PICKED_UP;
                    case OWNER_ROLE -> MainHandOwner.ROLE;
                    default -> MainHandOwner.NONE;
                };
            }
        }

        if (persistentData.contains(LEGACY_PICKED_UP_MAINHAND_TAG, CompoundTag.TAG_COMPOUND)
                && !legacyPickedUpMainHand(villager).isEmpty()) {
            return MainHandOwner.PICKED_UP;
        }
        if (persistentData.contains(ROLE_MAINHAND_TAG, CompoundTag.TAG_COMPOUND) && isLegacyRoleMainHand(villager)) {
            return MainHandOwner.ROLE;
        }
        return MainHandOwner.NONE;
    }

    public static boolean isPlayerManagedMainHand(AbstractVillager villager) {
        MainHandOwner owner = mainHandOwner(villager);
        return owner == MainHandOwner.MANUAL || owner == MainHandOwner.PICKED_UP;
    }

    public static ItemStack playerManagedMainHandStack(AbstractVillager villager) {
        ItemStack stack = playerManagedMainHand(villager);
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    public static boolean hasManagedMainHand(AbstractVillager villager) {
        return mainHandOwner(villager) != MainHandOwner.NONE;
    }

    public static ItemStack visibleMainHand(AbstractVillager villager) {
        ItemStack mainHand = villager.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }

        return switch (mainHandOwner(villager)) {
            case MANUAL, PICKED_UP -> playerManagedMainHand(villager);
            case ROLE -> roleMainHand(villager);
            case NONE -> ItemStack.EMPTY;
        };
    }

    public static boolean maintainPlayerManagedMainHand(AbstractVillager villager) {
        ItemStack expectedStack = playerManagedMainHand(villager);
        if (expectedStack.isEmpty()) {
            return false;
        }

        ItemStack mainHand = villager.getMainHandItem();
        if (ItemStack.isSameItem(mainHand, expectedStack)) {
            if (!sameStack(mainHand, expectedStack)) {
                setPlayerManagedMainHand(villager, mainHand);
            }
            return true;
        }

        if (!mainHand.isEmpty()) {
            storeOrDropDisplacedMainHand(villager, mainHand.copy());
        }

        setPlayerManagedMainHand(villager, expectedStack);
        return true;
    }

    public static boolean restorePlayerManagedMainHand(AbstractVillager villager) {
        ItemStack expectedStack = playerManagedMainHand(villager);
        if (expectedStack.isEmpty()) {
            return false;
        }

        setPlayerManagedMainHand(villager, expectedStack);
        return true;
    }

    public static boolean hasPickedUpMainHand(AbstractVillager villager) {
        return !pickedUpMainHand(villager).isEmpty();
    }

    public static ItemStack pickedUpMainHand(AbstractVillager villager) {
        CompoundTag stateTag = villager.getPersistentData().getCompound(MAINHAND_STATE_TAG);
        if (!stateTag.isEmpty()
                && OWNER_PICKED_UP.equals(stateTag.getString(MAINHAND_OWNER_TAG))
                && stateTag.contains(MAINHAND_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(
                    villager.level().registryAccess(),
                    stateTag.getCompound(MAINHAND_STACK_TAG)
            );
            if (!stack.isEmpty()) {
                return stack;
            }
        }

        ItemStack legacyStack = legacyPickedUpMainHand(villager);
        if (!legacyStack.isEmpty()) {
            setPickedUpMainHandState(villager, legacyStack);
        }
        return legacyStack;
    }

    public static void clearPickedUpMainHand(AbstractVillager villager) {
        if (mainHandOwner(villager) == MainHandOwner.PICKED_UP) {
            clearMainHandState(villager);
        }
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);
    }

    public static void clearPlayerManagedMainHand(AbstractVillager villager) {
        if (isPlayerManagedMainHand(villager)) {
            clearMainHandState(villager);
        }
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);
    }

    public static void clearRoleMainHand(AbstractVillager villager) {
        villager.getPersistentData().remove(ROLE_MAINHAND_TAG);
        if (mainHandOwner(villager) == MainHandOwner.ROLE) {
            clearMainHandState(villager);
        }
    }

    public static boolean isRoleMainHand(AbstractVillager villager) {
        if (mainHandOwner(villager) == MainHandOwner.ROLE) {
            return true;
        }
        return isLegacyRoleMainHand(villager);
    }

    private static void setInventoryMainHand(AbstractVillager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            clearMainHandState(villager);
            setEquipment(villager, EquipmentSlot.MAINHAND, ItemStack.EMPTY, false);
            setMainHandDropChance(villager, Mob.DEFAULT_EQUIPMENT_DROP_CHANCE);
            return;
        }

        setEquipment(villager, EquipmentSlot.MAINHAND, stack, true);
        setManualMainHandState(villager, stack);
    }

    private static void storeOrDropDisplacedMainHand(AbstractVillager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack remainder;
        if (villager instanceof Villager regularVillager) {
            remainder = VillagerInventoryAccess.addItem(regularVillager, stack);
        } else {
            remainder = villager.getInventory().addItem(stack);
        }
        if (!remainder.isEmpty()) {
            villager.spawnAtLocation(remainder);
        }
    }

    private static void clearMainHandState(AbstractVillager villager) {
        villager.getPersistentData().remove(MAINHAND_STATE_TAG);
        villager.getPersistentData().remove(ROLE_MAINHAND_TAG);
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);
    }

    private static ItemStack playerManagedMainHand(AbstractVillager villager) {
        CompoundTag stateTag = villager.getPersistentData().getCompound(MAINHAND_STATE_TAG);
        MainHandOwner owner = mainHandOwner(villager);
        if ((owner == MainHandOwner.MANUAL || owner == MainHandOwner.PICKED_UP)
                && !stateTag.isEmpty()
                && stateTag.contains(MAINHAND_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            return ItemStack.parseOptional(villager.level().registryAccess(), stateTag.getCompound(MAINHAND_STACK_TAG));
        }
        if (owner == MainHandOwner.PICKED_UP) {
            return legacyPickedUpMainHand(villager);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack roleMainHand(AbstractVillager villager) {
        CompoundTag stateTag = villager.getPersistentData().getCompound(MAINHAND_STATE_TAG);
        if (mainHandOwner(villager) == MainHandOwner.ROLE
                && !stateTag.isEmpty()
                && stateTag.contains(MAINHAND_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            return ItemStack.parseOptional(villager.level().registryAccess(), stateTag.getCompound(MAINHAND_STACK_TAG));
        }
        return ItemStack.EMPTY;
    }

    private static void setPlayerManagedMainHand(AbstractVillager villager, ItemStack stack) {
        if (mainHandOwner(villager) == MainHandOwner.MANUAL) {
            setEquipment(villager, EquipmentSlot.MAINHAND, stack, true);
            setManualMainHandState(villager, stack);
            return;
        }
        setPickedUpMainHand(villager, stack);
    }

    private static void setManualMainHandState(AbstractVillager villager, ItemStack stack) {
        CompoundTag stateTag = new CompoundTag();
        stateTag.putString(MAINHAND_OWNER_TAG, OWNER_MANUAL);
        stateTag.put(MAINHAND_STACK_TAG, stack.saveOptional(villager.level().registryAccess()));
        villager.getPersistentData().put(MAINHAND_STATE_TAG, stateTag);
        villager.getPersistentData().remove(ROLE_MAINHAND_TAG);
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);
    }

    private static void setPickedUpMainHandState(AbstractVillager villager, ItemStack stack) {
        CompoundTag stateTag = new CompoundTag();
        stateTag.putString(MAINHAND_OWNER_TAG, OWNER_PICKED_UP);
        stateTag.put(MAINHAND_STACK_TAG, stack.saveOptional(villager.level().registryAccess()));
        villager.getPersistentData().put(MAINHAND_STATE_TAG, stateTag);
        villager.getPersistentData().remove(ROLE_MAINHAND_TAG);
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);
    }

    private static void setRoleMainHandState(AbstractVillager villager, String roleKey, ItemStack stack) {
        CompoundTag stateTag = new CompoundTag();
        stateTag.putString(MAINHAND_OWNER_TAG, OWNER_ROLE);
        stateTag.putString(ROLE_MAINHAND_KEY_TAG, roleKey);
        if (!stack.isEmpty()) {
            stateTag.put(MAINHAND_STACK_TAG, stack.saveOptional(villager.level().registryAccess()));
        }
        villager.getPersistentData().put(MAINHAND_STATE_TAG, stateTag);
        villager.getPersistentData().remove(LEGACY_PICKED_UP_MAINHAND_TAG);

        CompoundTag roleTag = new CompoundTag();
        roleTag.putString(ROLE_MAINHAND_KEY_TAG, roleKey);
        villager.getPersistentData().put(ROLE_MAINHAND_TAG, roleTag);
    }

    private static boolean isLegacyRoleMainHand(AbstractVillager villager) {
        CompoundTag roleTag = villager.getPersistentData().getCompound(ROLE_MAINHAND_TAG);
        return !roleTag.isEmpty() && roleTag.contains(ROLE_MAINHAND_KEY_TAG);
    }

    private static ItemStack legacyPickedUpMainHand(AbstractVillager villager) {
        CompoundTag trackedTag = villager.getPersistentData().getCompound(LEGACY_PICKED_UP_MAINHAND_TAG);
        if (trackedTag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(villager.level().registryAccess(), trackedTag);
    }

    private static void setEquipment(AbstractVillager villager, EquipmentSlot slot, ItemStack stack, boolean guaranteedDrop) {
        ItemStack equipmentStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (sameStack(villager.getItemBySlot(slot), equipmentStack)) {
            if (guaranteedDrop && !equipmentStack.isEmpty()) {
                villager.setGuaranteedDrop(slot);
            }
            return;
        }
        villager.setItemSlot(slot, equipmentStack);
        if (guaranteedDrop && !equipmentStack.isEmpty()) {
            villager.setGuaranteedDrop(slot);
        }
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static String rolledRoleKey(AbstractVillager villager) {
        return villager.getPersistentData().getString(ROLE_MAINHAND_ROLLED_KEY_TAG);
    }

    private static void setRolledRoleKey(AbstractVillager villager, String roleKey) {
        villager.getPersistentData().putString(ROLE_MAINHAND_ROLLED_KEY_TAG, roleKey);
    }
}
