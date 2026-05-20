package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

public class VillagerInventoryMenu extends AbstractContainerMenu {
    private static final int VILLAGER_SLOT_COUNT = VillagerInventoryContainer.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = VILLAGER_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_COUNT;
    private static final int PLAYER_HOTBAR_COUNT = 9;
    private static final int PLAYER_SLOT_END = PLAYER_HOTBAR_START + PLAYER_HOTBAR_COUNT;

    private static final int ARMOR_X = 45;
    private static final int ARMOR_Y = 8;
    private static final int HELD_X = 115;
    private static final int HELD_Y = 44;
    private static final int OFFHAND_Y = 62;
    private static final int VILLAGER_INVENTORY_X = 8;
    private static final int VILLAGER_INVENTORY_Y = 84;
    private static final int VILLAGER_HOTBAR_Y = 120;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 156;
    private static final int PLAYER_HOTBAR_Y = 214;
    private static final int SLOT_SIZE = 18;

    private final Container villagerInventory;
    private final Villager villager;
    private final int villagerEntityId;
    private final Player player;

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(VILLAGER_SLOT_COUNT), null, data == null ? -1 : data.readVarInt());
    }

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, Villager villager) {
        this(containerId, playerInventory, new VillagerInventoryContainer(villager), villager, villager.getId());
    }

    private VillagerInventoryMenu(int containerId, Inventory playerInventory, Container villagerInventory, Villager villager, int villagerEntityId) {
        super(VillagerRetaliationMenus.VILLAGER_INVENTORY.get(), containerId);
        checkContainerSize(villagerInventory, VILLAGER_SLOT_COUNT);
        this.villagerInventory = villagerInventory;
        this.villager = villager;
        this.villagerEntityId = villagerEntityId;
        this.player = playerInventory.player;
        villagerInventory.startOpen(playerInventory.player);
        addVillagerSlots();
        addPlayerSlots(playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager == null || canStillUse(player) && this.villagerInventory.stillValid(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        holdVillager();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack originalStack = sourceStack.copy();
        if (index < VILLAGER_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!movePlayerStackToVillager(sourceStack)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return originalStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.villagerInventory.stopOpen(player);
    }

    public int villagerEntityId() {
        return this.villagerEntityId;
    }

    private void addVillagerSlots() {
        for (int slot = 0; slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT; slot++) {
            addSlot(new VillagerArmorSlot(
                    this.villagerInventory,
                    slot,
                    ARMOR_X,
                    ARMOR_Y + slot * SLOT_SIZE,
                    VillagerInventoryContainer.armorEquipmentSlot(slot)
            ));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        this.villagerInventory,
                        VillagerInventoryContainer.ARMOR_SLOT_COUNT + 9 + row * 9 + column,
                        VILLAGER_INVENTORY_X + column * SLOT_SIZE,
                        VILLAGER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    this.villagerInventory,
                    VillagerInventoryContainer.ARMOR_SLOT_COUNT + column,
                    VILLAGER_INVENTORY_X + column * SLOT_SIZE,
                    VILLAGER_HOTBAR_Y
            ));
        }

        addSlot(new Slot(this.villagerInventory, VillagerInventoryContainer.HELD_SLOT, HELD_X, HELD_Y));
        addSlot(new Slot(this.villagerInventory, VillagerInventoryContainer.OFFHAND_SLOT, HELD_X, OFFHAND_Y));
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        9 + row * 9 + column,
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }
    }

    private boolean movePlayerStackToVillager(ItemStack stack) {
        EquipmentSlot equipmentSlot = equipmentSlotFor(stack);
        if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            int armorSlot = armorSlotFor(equipmentSlot);
            if (armorSlot >= 0 && !moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
                return false;
            }
            if (stack.isEmpty()) {
                return true;
            }
        }

        if (equipmentSlot == EquipmentSlot.OFFHAND && moveItemStackTo(stack, VillagerInventoryContainer.OFFHAND_SLOT, VillagerInventoryContainer.OFFHAND_SLOT + 1, false)) {
            return true;
        }

        if (moveItemStackTo(stack, VillagerInventoryContainer.ARMOR_SLOT_COUNT, VillagerInventoryContainer.HELD_SLOT, false)) {
            return true;
        }

        return moveItemStackTo(stack, VillagerInventoryContainer.HELD_SLOT, VillagerInventoryContainer.HELD_SLOT + 1, false);
    }

    private boolean canStillUse(Player player) {
        if (this.villager == null || !this.villager.isAlive()) {
            return false;
        }
        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return player.distanceToSqr(this.villager) <= maxDistance * maxDistance;
    }

    private void holdVillager() {
        if (this.villager == null || this.player == null || !canStillUse(this.player)) {
            return;
        }
        if (this.villager.isSleeping()) {
            this.villager.stopSleeping();
        }
        this.villager.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
        this.villager.getNavigation().stop();
        this.villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.villager.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    private static int armorSlotFor(EquipmentSlot equipmentSlot) {
        for (int slot = 0; slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT; slot++) {
            if (VillagerInventoryContainer.armorEquipmentSlot(slot) == equipmentSlot) {
                return slot;
            }
        }
        return -1;
    }

    private static EquipmentSlot equipmentSlotFor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return EquipmentSlot.MAINHAND;
    }

    private static final class VillagerArmorSlot extends Slot {
        private final EquipmentSlot equipmentSlot;

        private VillagerArmorSlot(Container container, int slot, int x, int y, EquipmentSlot equipmentSlot) {
            super(container, slot, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return equipmentSlotFor(stack) == this.equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
