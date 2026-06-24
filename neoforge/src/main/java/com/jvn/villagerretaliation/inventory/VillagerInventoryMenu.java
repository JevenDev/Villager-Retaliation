package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.mixin.AbstractContainerMenuAccessor;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

public class VillagerInventoryMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int PLAYER_HOTBAR_COUNT = 9;

    private static final int ARMOR_X = 45;
    private static final int ARMOR_Y = 8;
    private static final int HELD_X = 115;
    private static final int HELD_Y = 44;
    private static final int OFFHAND_Y = 62;
    private static final int VILLAGER_INVENTORY_X = 8;
    private static final int VILLAGER_INVENTORY_Y = 84;
    private static final int JOB_MAINHAND_SLOT = HiredJobInventory.MAINHAND_SLOT;
    private static final int JOB_OFFHAND_SLOT = HiredJobInventory.OFFHAND_SLOT;
    private static final int JOB_EQUIPMENT_SLOT_COUNT = JOB_OFFHAND_SLOT + 1;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 156;
    private static final int PLAYER_HOTBAR_Y = 214;
    private static final int SLOT_SIZE = 18;
    private static final ResourceLocation EMPTY_SLOT_SWORD_ICON = ResourceLocation.withDefaultNamespace("item/empty_slot_sword");

    private Container villagerInventory;
    private final Villager villager;
    private final int villagerEntityId;
    private ViewMode viewMode;
    private int villagerSlotCount;
    private int playerInventoryStart;
    private int playerHotbarStart;
    private int playerSlotEnd;
    private final Player player;
    private final Inventory playerInventory;
    private final boolean personalInventoryAccess;
    private final boolean jobInventoryAccess;
    private VillagerGiftReturnTracker.GiftSnapshot giftSnapshot;
    private VillagerTradePaymentTracker.TradePaymentSnapshot tradePaymentSnapshot;
    private VillagerConfiscatedStolenItemTracker.StolenItemSnapshot stolenItemSnapshot;
    private int initialGearStackCount;
    private boolean personalTrackingInitialized;
    private boolean giftReturnsProcessed;
    private boolean tradePaymentReturnsProcessed;
    private boolean stolenItemReturnsProcessed;

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, clientData(data));
    }

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, Villager villager) {
        this(containerId, playerInventory, villager, ViewMode.PERSONAL);
    }

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, Villager villager, ViewMode viewMode) {
        this(
                containerId,
                playerInventory,
                villager,
                viewMode,
                personalInventoryAccess(playerInventory, villager),
                jobInventoryAccess(playerInventory, villager));
    }

    public VillagerInventoryMenu(
            int containerId,
            Inventory playerInventory,
            Villager villager,
            ViewMode viewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess) {
        this(
                containerId,
                playerInventory,
                createVillagerInventory(villager, allowedViewMode(viewMode, personalInventoryAccess, jobInventoryAccess)),
                villager,
                villager.getId(),
                allowedViewMode(viewMode, personalInventoryAccess, jobInventoryAccess),
                personalInventoryAccess,
                jobInventoryAccess
        );
    }

    private VillagerInventoryMenu(int containerId, Inventory playerInventory, ClientMenuData data) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(data.viewMode().slotCount()),
                null,
                data.entityId(),
                data.viewMode(),
                data.personalInventoryAccess(),
                data.jobInventoryAccess());
    }

    private VillagerInventoryMenu(
            int containerId,
            Inventory playerInventory,
            Container villagerInventory,
            Villager villager,
            int villagerEntityId,
            ViewMode viewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess) {
        super(VillagerRetaliationMenus.VILLAGER_INVENTORY.get(), containerId);
        this.viewMode = viewMode == null ? ViewMode.PERSONAL : viewMode;
        this.villagerSlotCount = this.viewMode.slotCount();
        this.playerInventoryStart = this.villagerSlotCount;
        this.playerHotbarStart = this.playerInventoryStart + PLAYER_INVENTORY_COUNT;
        this.playerSlotEnd = this.playerHotbarStart + PLAYER_HOTBAR_COUNT;
        checkContainerSize(villagerInventory, this.villagerSlotCount);
        this.villagerInventory = villagerInventory;
        this.villager = villager;
        this.villagerEntityId = villagerEntityId;
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;
        this.personalInventoryAccess = personalInventoryAccess;
        this.jobInventoryAccess = jobInventoryAccess;
        villagerInventory.startOpen(playerInventory.player);
        initializePersonalTrackingState();
        addVillagerSlots();
        addPlayerSlots(playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager == null
                || canStillUse(player)
                && this.villagerInventory.stillValid(player)
                && (this.viewMode != ViewMode.PERSONAL || hasPersonalInventoryAccess(player))
                && (this.viewMode != ViewMode.JOB || hasJobInventoryAccess(player));
    }

    @Override
    public void broadcastChanges() {
        refreshVillagerInventory();
        super.broadcastChanges();
        holdVillager();
        checkNetheriteAdvancement();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack originalStack = sourceStack.copy();
        if (index < this.villagerSlotCount) {
            if (isProtectedJobSlot(sourceSlot)) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(sourceStack, this.playerInventoryStart, this.playerSlotEnd, true)) {
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
        stripPlayerSideTradePaymentTracking(player);
        return originalStack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        stripPlayerSideTradePaymentTracking(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        processTakenGifts(player);
        processTakenTradePayments(player);
        processTakenStolenItems(player);
        rememberAddedGear(player);
        this.villagerInventory.stopOpen(player);
    }

    public int villagerEntityId() {
        return this.villagerEntityId;
    }

    public ViewMode viewMode() {
        return this.viewMode;
    }

    public boolean isVillagerSlot(Slot slot) {
        int menuSlot = this.slots.indexOf(slot);
        return menuSlot >= 0 && menuSlot < this.villagerSlotCount;
    }

    public boolean isJobInventory() {
        return this.viewMode == ViewMode.JOB;
    }

    public boolean canSwitchToPersonalInventory() {
        return this.personalInventoryAccess;
    }

    public boolean canSwitchToJobInventory() {
        return this.jobInventoryAccess;
    }

    public void switchViewMode(ViewMode viewMode) {
        if (viewMode == null || this.viewMode == viewMode) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL && !this.personalInventoryAccess) {
            return;
        }
        if (viewMode == ViewMode.JOB && !this.jobInventoryAccess) {
            return;
        }
        this.villagerInventory.stopOpen(this.playerInventory.player);
        this.viewMode = viewMode;
        this.villagerSlotCount = this.viewMode.slotCount();
        this.playerInventoryStart = this.villagerSlotCount;
        this.playerHotbarStart = this.playerInventoryStart + PLAYER_INVENTORY_COUNT;
        this.playerSlotEnd = this.playerHotbarStart + PLAYER_HOTBAR_COUNT;
        this.villagerInventory = createVillagerInventory(viewMode);
        this.villagerInventory.startOpen(this.playerInventory.player);
        initializePersonalTrackingState();
        this.slots.clear();
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        accessor.villagerretaliation$getLastSlots().clear();
        accessor.villagerretaliation$getRemoteSlots().clear();
        addVillagerSlots();
        addPlayerSlots(this.playerInventory);
    }

    private void addVillagerSlots() {
        if (this.viewMode == ViewMode.JOB) {
            addJobSlots();
            return;
        }
        for (int slot = 0; slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT; slot++) {
            addSlot(new VillagerArmorSlot(
                    this.villagerInventory,
                    slot,
                    ARMOR_X,
                    ARMOR_Y + slot * SLOT_SIZE,
                    this.villager,
                    VillagerInventoryContainer.armorEquipmentSlot(slot)
            ));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        this.villagerInventory,
                        VillagerInventoryContainer.ARMOR_SLOT_COUNT + row * 9 + column,
                        VILLAGER_INVENTORY_X + column * SLOT_SIZE,
                        VILLAGER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        addSlot(new VillagerHandSlot(
                this.villagerInventory,
                VillagerInventoryContainer.HELD_SLOT,
                HELD_X,
                HELD_Y,
                EMPTY_SLOT_SWORD_ICON,
                this.villager,
                EquipmentSlot.MAINHAND
        ));
        addSlot(new VillagerHandSlot(
                this.villagerInventory,
                VillagerInventoryContainer.OFFHAND_SLOT,
                HELD_X,
                OFFHAND_Y,
                InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD,
                this.villager,
                EquipmentSlot.OFFHAND
        ));
    }

    private void addJobSlots() {
        for (int slot = 0; slot < this.villagerSlotCount; slot++) {
            addSlot(new JobInventorySlot(
                    this.villagerInventory,
                    slot,
                    jobSlotX(slot),
                    jobSlotY(slot)
            ));
        }
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
        if (this.viewMode == ViewMode.JOB) {
            return moveItemStackTo(stack, 0, this.villagerSlotCount, false);
        }

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

    private boolean hasPersonalInventoryAccess(Player player) {
        return this.personalInventoryAccess
                && player instanceof ServerPlayer serverPlayer
                && this.villager.level() instanceof ServerLevel level
                && VillagerInventoryAccess.canAccess(level, this.villager, serverPlayer);
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

    private void refreshVillagerInventory() {
        if (this.villager == null) {
            return;
        }
        if (this.villagerInventory instanceof HiredJobInventory jobInventory) {
            jobInventory.refreshFromVillager();
        } else if (this.villagerInventory instanceof VillagerInventoryContainer personalInventory) {
            personalInventory.refreshFromVillager();
        }
    }

    private void checkNetheriteAdvancement() {
        if (this.player instanceof ServerPlayer serverPlayer && this.villager != null) {
            VillagerReputationAdvancements.onVillagerEquipmentChanged(serverPlayer, this.villager);
        }
    }

    private void processTakenGifts(Player player) {
        if (this.giftReturnsProcessed || this.villager == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.giftReturnsProcessed = true;
        VillagerGiftReturnTracker.applyTakenGiftPenalties(serverPlayer, this.villager, this.giftSnapshot);
    }

    private void processTakenTradePayments(Player player) {
        if (this.tradePaymentReturnsProcessed || this.villager == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.tradePaymentReturnsProcessed = true;
        VillagerTradePaymentTracker.applyTakenTradePaymentPenalties(serverPlayer, this.villager, this.tradePaymentSnapshot);
    }

    private void processTakenStolenItems(Player player) {
        if (this.stolenItemReturnsProcessed || this.villager == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.stolenItemReturnsProcessed = true;
        VillagerConfiscatedStolenItemTracker.applyTakenStolenItemPenalties(serverPlayer, this.villager, this.stolenItemSnapshot);
    }

    private static void stripPlayerSideTradePaymentTracking(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            VillagerTradePaymentTracker.stripTradePaymentTrackingFromPlayerInventory(serverPlayer);
        }
    }

    private void rememberAddedGear(Player player) {
        if (this.villager == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (gearStackCount(this.villagerInventory) <= this.initialGearStackCount) {
            return;
        }
        String gearKind = gearKind(this.villagerInventory);
        if (!gearKind.isBlank()) {
            VillagerInteractionTracker.rememberGearReport(serverPlayer.serverLevel(), this.villager, serverPlayer, gearKind);
        }
    }

    private static int gearStackCount(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (isGear(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String gearKind(Container container) {
        boolean hasArmor = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)) {
                return "weapon";
            }
            hasArmor |= isArmor(stack);
        }
        return hasArmor ? "armor" : "";
    }

    private static boolean isGear(ItemStack stack) {
        return VillagerRetaliationVillagerWeapons.isUsableWeapon(stack) || isArmor(stack);
    }

    private static boolean isArmor(ItemStack stack) {
        return Equipable.get(stack) != null;
    }

    private boolean isProtectedJobSlot(Slot slot) {
        return this.viewMode == ViewMode.JOB && ProtectedVillagerProperty.isProtected(slot.getItem());
    }

    private static ClientMenuData clientData(RegistryFriendlyByteBuf data) {
        if (data == null) {
            return new ClientMenuData(-1, ViewMode.PERSONAL, true, false);
        }
        int entityId = data.readVarInt();
        ViewMode viewMode = data.isReadable() ? data.readEnum(ViewMode.class) : ViewMode.PERSONAL;
        boolean personalInventoryAccess = viewMode == ViewMode.PERSONAL;
        if (data.isReadable()) {
            personalInventoryAccess = data.readBoolean();
        }
        boolean jobInventoryAccess = viewMode == ViewMode.JOB;
        if (data.isReadable()) {
            jobInventoryAccess = data.readBoolean();
        }
        viewMode = allowedViewMode(viewMode, personalInventoryAccess, jobInventoryAccess);
        return new ClientMenuData(entityId, viewMode, personalInventoryAccess, jobInventoryAccess);
    }

    private static boolean personalInventoryAccess(Inventory playerInventory, Villager villager) {
        return !(playerInventory.player instanceof ServerPlayer serverPlayer)
                || villager.level() instanceof ServerLevel level && VillagerInventoryAccess.canAccess(level, villager, serverPlayer);
    }

    private static boolean jobInventoryAccess(Inventory playerInventory, Villager villager) {
        return !(playerInventory.player instanceof ServerPlayer serverPlayer)
                || villager.level() instanceof ServerLevel level
                && com.jvn.villagerretaliation.interaction.HiredVillagerContractService.canAccessJobInventory(level, villager, serverPlayer);
    }

    private static Container createVillagerInventory(Villager villager, ViewMode viewMode) {
        return viewMode == ViewMode.JOB ? HiredJobInventory.getJobInventory(villager) : new VillagerInventoryContainer(villager);
    }

    private static ViewMode allowedViewMode(ViewMode viewMode, boolean personalInventoryAccess, boolean jobInventoryAccess) {
        ViewMode safeViewMode = viewMode == null ? ViewMode.PERSONAL : viewMode;
        if (safeViewMode == ViewMode.PERSONAL && !personalInventoryAccess && jobInventoryAccess) {
            return ViewMode.JOB;
        }
        if (safeViewMode == ViewMode.JOB && !jobInventoryAccess && personalInventoryAccess) {
            return ViewMode.PERSONAL;
        }
        return safeViewMode;
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

    private void initializePersonalTrackingState() {
        if (this.personalTrackingInitialized || this.viewMode != ViewMode.PERSONAL || this.villager == null || !(this.playerInventory.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.giftSnapshot = VillagerGiftReturnTracker.capture(serverPlayer, this.villager);
        this.tradePaymentSnapshot = VillagerTradePaymentTracker.capture(serverPlayer, this.villager);
        this.stolenItemSnapshot = VillagerConfiscatedStolenItemTracker.capture(serverPlayer, this.villager);
        this.initialGearStackCount = gearStackCount(this.villagerInventory);
        this.personalTrackingInitialized = true;
    }

    private Container createVillagerInventory(ViewMode viewMode) {
        if (this.villager == null) {
            return new SimpleContainer(viewMode.slotCount());
        }
        return viewMode == ViewMode.JOB ? HiredJobInventory.getJobInventory(this.villager) : new VillagerInventoryContainer(this.villager);
    }

    private static final class VillagerArmorSlot extends Slot {
        private final Villager villager;
        private final EquipmentSlot equipmentSlot;

        private VillagerArmorSlot(Container container, int slot, int x, int y, Villager villager, EquipmentSlot equipmentSlot) {
            super(container, slot, x, y);
            this.villager = villager;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !HiredJobInventory.hasJobEquipmentForSlot(this.villager, this.equipmentSlot)
                    && equipmentSlotFor(stack) == this.equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, emptyArmorSlotIcon(this.equipmentSlot));
        }
    }

    private static final class JobInventorySlot extends Slot {
        private JobInventorySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            EquipmentSlot equipmentSlot = jobArmorEquipmentSlot(this.getSlotIndex());
            return equipmentSlot == null || equipmentSlotFor(stack) == equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return jobArmorEquipmentSlot(this.getSlotIndex()) != null ? 1 : super.getMaxStackSize();
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            ResourceLocation icon = jobSlotIcon(this.getSlotIndex());
            return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }

        @Override
        public boolean mayPickup(Player player) {
            return HiredJobInventory.canHirerRemoveFromJobInventory(getItem());
        }
    }

    private static final class VillagerHandSlot extends Slot {
        private final ResourceLocation icon;
        private final Villager villager;
        private final EquipmentSlot equipmentSlot;

        private VillagerHandSlot(
                Container container,
                int slot,
                int x,
                int y,
                ResourceLocation icon,
                Villager villager,
                EquipmentSlot equipmentSlot
        ) {
            super(container, slot, x, y);
            this.icon = icon;
            this.villager = villager;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !HiredJobInventory.hasJobEquipmentForSlot(this.villager, this.equipmentSlot);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, this.icon);
        }
    }

    public enum ViewMode {
        PERSONAL(VillagerInventoryContainer.SLOT_COUNT),
        JOB(HiredJobInventory.SLOT_COUNT);

        private final int slotCount;

        ViewMode(int slotCount) {
            this.slotCount = slotCount;
        }

        public int slotCount() {
            return this.slotCount;
        }
    }

    private boolean hasJobInventoryAccess(Player player) {
        return this.jobInventoryAccess
                && (!(player instanceof ServerPlayer serverPlayer)
                || this.villager == null
                || this.villager.level() instanceof ServerLevel level
                && com.jvn.villagerretaliation.interaction.HiredVillagerContractService.canAccessJobInventory(level, this.villager, serverPlayer));
    }

    private record ClientMenuData(int entityId, ViewMode viewMode, boolean personalInventoryAccess, boolean jobInventoryAccess) {
    }

    private static ResourceLocation emptyArmorSlotIcon(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            default -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
        };
    }

    private static ResourceLocation jobSlotIcon(int slot) {
        EquipmentSlot equipmentSlot = jobArmorEquipmentSlot(slot);
        if (equipmentSlot != null) {
            return emptyArmorSlotIcon(equipmentSlot);
        }
        if (slot == JOB_MAINHAND_SLOT) {
            return EMPTY_SLOT_SWORD_ICON;
        }
        if (slot == JOB_OFFHAND_SLOT) {
            return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
        }
        return null;
    }

    private static int jobSlotX(int slot) {
        if (slot >= 0 && slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT) {
            return ARMOR_X;
        }
        if (slot == JOB_MAINHAND_SLOT || slot == JOB_OFFHAND_SLOT) {
            return HELD_X;
        }
        int gridIndex = slot - JOB_EQUIPMENT_SLOT_COUNT;
        int column = gridIndex % 9;
        return VILLAGER_INVENTORY_X + column * SLOT_SIZE;
    }

    private static int jobSlotY(int slot) {
        if (slot >= 0 && slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT) {
            return ARMOR_Y + slot * SLOT_SIZE;
        }
        if (slot == JOB_MAINHAND_SLOT) {
            return HELD_Y;
        }
        if (slot == JOB_OFFHAND_SLOT) {
            return OFFHAND_Y;
        }
        int gridIndex = slot - JOB_EQUIPMENT_SLOT_COUNT;
        int row = gridIndex / 9;
        return VILLAGER_INVENTORY_Y + row * SLOT_SIZE;
    }

    private static EquipmentSlot jobArmorEquipmentSlot(int slot) {
        return slot >= 0 && slot < VillagerInventoryContainer.ARMOR_SLOT_COUNT
                ? VillagerInventoryContainer.armorEquipmentSlot(slot)
                : null;
    }
}
