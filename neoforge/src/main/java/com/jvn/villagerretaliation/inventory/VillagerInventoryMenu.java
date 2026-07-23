package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.mixin.AbstractContainerMenuAccessor;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

public class VillagerInventoryMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int MAX_VILLAGER_SLOT_COUNT = HiredJobInventory.SLOT_COUNT;
    private static final int PLAYER_HOTBAR_COUNT = 9;

    private static final int ARMOR_X = 48;
    private static final int ARMOR_Y = 27;
    private static final int HELD_X = 118;
    private static final int HELD_Y = 63;
    private static final int OFFHAND_Y = 81;
    private static final int VILLAGER_INVENTORY_X = 11;
    private static final int VILLAGER_INVENTORY_Y = 103;
    private static final int VILLAGER_HOTBAR_Y = 161;
    private static final int JOB_MAINHAND_SLOT = HiredJobInventory.MAINHAND_SLOT;
    private static final int JOB_OFFHAND_SLOT = HiredJobInventory.OFFHAND_SLOT;
    private static final int JOB_EQUIPMENT_SLOT_COUNT = JOB_OFFHAND_SLOT + 1;
    private static final int PLAYER_INVENTORY_BELOW_X = 12;
    private static final int PLAYER_INVENTORY_BELOW_Y = 200;
    private static final int PLAYER_HOTBAR_BELOW_Y = 258;
    private static final int PLAYER_INVENTORY_BESIDE_X = 194;
    private static final int PLAYER_INVENTORY_BESIDE_Y = 57;
    private static final int PLAYER_HOTBAR_BESIDE_Y = 115;
    private static final int SLOT_SIZE = 18;
    private static final ResourceLocation EMPTY_SLOT_SWORD_ICON = ResourceLocation.withDefaultNamespace("item/empty_slot_sword");
    private static final ResourceLocation EMPTY_SLOT_FILTER_ICON =
            ResourceLocation.withDefaultNamespace("item/empty_slot_smithing_template_armor_trim");

    private Container villagerInventory;
    private final SimpleContainer paddingInventory = new SimpleContainer(1);
    private final Villager villager;
    private final int villagerEntityId;
    private long contractEndGameTime;
    private ViewMode viewMode;
    private int villagerSlotCount;
    private int playerInventoryStart;
    private int playerHotbarStart;
    private int playerSlotEnd;
    private final Player player;
    private final Inventory playerInventory;
    private final boolean personalInventoryAccess;
    private final boolean jobInventoryAccess;
    private final ViewMode workInventoryViewMode;
    private VillagerGiftReturnTracker.GiftSnapshot giftSnapshot;
    private VillagerTradePaymentTracker.TradePaymentSnapshot tradePaymentSnapshot;
    private VillagerConfiscatedStolenItemTracker.StolenItemSnapshot stolenItemSnapshot;
    private int initialGearStackCount;
    private boolean personalTrackingInitialized;
    private boolean gearReportProcessed;
    private boolean giftReturnsProcessed;
    private boolean tradePaymentReturnsProcessed;
    private boolean stolenItemReturnsProcessed;
    private boolean playerInventoryBeside;

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
        this(containerId,
                playerInventory,
                villager,
                viewMode,
                personalInventoryAccess,
                jobInventoryAccess,
                workInventoryViewMode(villager));
    }

    private VillagerInventoryMenu(
            int containerId,
            Inventory playerInventory,
            Villager villager,
            ViewMode viewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess,
            ViewMode workInventoryViewMode) {
        this(
                containerId,
                playerInventory,
                createVillagerInventory(villager, allowedViewMode(
                        normalizeWorkViewMode(viewMode, workInventoryViewMode),
                        personalInventoryAccess,
                        jobInventoryAccess,
                        workInventoryViewMode)),
                villager,
                villager.getId(),
                allowedViewMode(
                        normalizeWorkViewMode(viewMode, workInventoryViewMode),
                        personalInventoryAccess,
                        jobInventoryAccess,
                        workInventoryViewMode),
                workInventoryViewMode,
                personalInventoryAccess,
                jobInventoryAccess,
                contractEndGameTime(villager)
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
                data.workInventoryViewMode(),
                data.personalInventoryAccess(),
                data.jobInventoryAccess(),
                data.contractEndGameTime());
    }

    private VillagerInventoryMenu(
            int containerId,
            Inventory playerInventory,
            Container villagerInventory,
            Villager villager,
            int villagerEntityId,
            ViewMode viewMode,
            ViewMode workInventoryViewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess,
            long contractEndGameTime) {
        super(VillagerRetaliationMenus.VILLAGER_INVENTORY.get(), containerId);
        this.viewMode = viewMode == null ? ViewMode.PERSONAL : viewMode;
        this.villagerSlotCount = this.viewMode.slotCount();
        this.playerInventoryStart = MAX_VILLAGER_SLOT_COUNT;
        this.playerHotbarStart = this.playerInventoryStart + PLAYER_INVENTORY_COUNT;
        this.playerSlotEnd = this.playerHotbarStart + PLAYER_HOTBAR_COUNT;
        checkContainerSize(villagerInventory, this.villagerSlotCount);
        this.villagerInventory = villagerInventory;
        this.villager = villager;
        this.villagerEntityId = villagerEntityId;
        this.contractEndGameTime = contractEndGameTime;
        this.workInventoryViewMode = workInventoryViewMode != null && workInventoryViewMode.isWorkInventory()
                ? workInventoryViewMode
                : ViewMode.JOB;
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;
        this.personalInventoryAccess = personalInventoryAccess;
        this.jobInventoryAccess = jobInventoryAccess;
        villagerInventory.startOpen(playerInventory.player);
        initializePersonalTrackingState();
        addVillagerSlots();
        addPaddingSlot();
        addPlayerSlots(playerInventory);
        addDataSlots(new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0
                        ? (int) VillagerInventoryMenu.this.contractEndGameTime
                        : (int) (VillagerInventoryMenu.this.contractEndGameTime >>> 32);
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    VillagerInventoryMenu.this.contractEndGameTime =
                            VillagerInventoryMenu.this.contractEndGameTime & 0xFFFFFFFF00000000L
                                    | value & 0xFFFFFFFFL;
                } else {
                    VillagerInventoryMenu.this.contractEndGameTime =
                            VillagerInventoryMenu.this.contractEndGameTime & 0xFFFFFFFFL
                                    | (long) value << 32;
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager == null
                || canStillUse(player)
                && this.villagerInventory.stillValid(player)
                && (this.viewMode != ViewMode.PERSONAL || hasPersonalInventoryAccess(player))
                && (!this.viewMode.isWorkInventory() || hasJobInventoryAccess(player));
    }

    @Override
    public void broadcastChanges() {
        refreshVillagerInventory();
        if (this.villager != null) {
            this.contractEndGameTime = contractEndGameTime(this.villager);
        }
        super.broadcastChanges();
        holdVillager();
        checkNetheriteAdvancement();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!hasCurrentViewAccess(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return ItemStack.EMPTY;
        }
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        // External inventory writers (combat loadouts, pickups, storage automation)
        // persist immediately. Always begin a player transaction from that current
        // state so one slot change cannot save an older full-inventory snapshot.
        refreshVillagerInventory();
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
        if (!hasCurrentViewAccess(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return;
        }
        refreshVillagerInventory();
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
        return this.viewMode.isWorkInventory();
    }

    public boolean isPartyInventory() {
        return this.viewMode == ViewMode.PARTY;
    }

    public ViewMode workInventoryViewMode() {
        return this.workInventoryViewMode;
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
        if (viewMode.isWorkInventory() && !this.jobInventoryAccess) {
            return;
        }
        if (this.viewMode == ViewMode.PERSONAL) {
            rememberAddedGear(this.playerInventory.player);
        }
        this.villagerInventory.stopOpen(this.playerInventory.player);
        this.viewMode = viewMode;
        this.villagerSlotCount = this.viewMode.slotCount();
        this.playerInventoryStart = MAX_VILLAGER_SLOT_COUNT;
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
        addPaddingSlot();
        addPlayerSlots(this.playerInventory);
    }

    public void setPlayerInventoryBeside(boolean playerInventoryBeside) {
        if (this.playerInventoryBeside == playerInventoryBeside) {
            return;
        }
        this.playerInventoryBeside = playerInventoryBeside;
        this.slots.clear();
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        accessor.villagerretaliation$getLastSlots().clear();
        accessor.villagerretaliation$getRemoteSlots().clear();
        addVillagerSlots();
        addPaddingSlot();
        addPlayerSlots(this.playerInventory);
    }

    private void addVillagerSlots() {
        if (this.viewMode.isWorkInventory()) {
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
        for (int column = 0; column < VillagerInventoryContainer.HOTBAR_SLOT_COUNT; column++) {
            addSlot(new Slot(
                    this.villagerInventory,
                    VillagerInventoryContainer.ARMOR_SLOT_COUNT + VillagerInventoryContainer.HOTBAR_START + column,
                    VILLAGER_INVENTORY_X + column * SLOT_SIZE,
                    VILLAGER_HOTBAR_Y
            ));
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
            if (slot == HiredJobInventory.FILTER_SLOT) {
                addSlot(new JobFilterSlot(this.villagerInventory, slot, jobSlotX(slot), jobSlotY(slot), this.villager));
            } else {
                addSlot(new JobInventorySlot(
                        this.villagerInventory,
                        slot,
                        jobSlotX(slot),
                        jobSlotY(slot)
                ));
            }
        }
    }

    private void addPaddingSlot() {
        if (this.villagerSlotCount >= MAX_VILLAGER_SLOT_COUNT) return;
        addSlot(new Slot(this.paddingInventory, 0, -1000, -1000) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
    }
    private void addPlayerSlots(Inventory playerInventory) {
        int inventoryX = this.playerInventoryBeside ? PLAYER_INVENTORY_BESIDE_X : PLAYER_INVENTORY_BELOW_X;
        int inventoryY = this.playerInventoryBeside ? PLAYER_INVENTORY_BESIDE_Y : PLAYER_INVENTORY_BELOW_Y;
        int hotbarY = this.playerInventoryBeside ? PLAYER_HOTBAR_BESIDE_Y : PLAYER_HOTBAR_BELOW_Y;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        9 + row * 9 + column,
                        inventoryX + column * SLOT_SIZE,
                        inventoryY + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, inventoryX + column * SLOT_SIZE, hotbarY));
        }
    }

    private boolean movePlayerStackToVillager(ItemStack stack) {
        EquipmentSlot equipmentSlot = equipmentSlotFor(stack);
        if (this.viewMode.isWorkInventory()) {
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
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(this.villager);
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
        if (this.gearReportProcessed
                || !this.personalTrackingInitialized
                || this.viewMode != ViewMode.PERSONAL
                || this.villager == null
                || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (gearStackCount(this.villagerInventory) <= this.initialGearStackCount) {
            return;
        }
        String gearKind = gearKind(this.villagerInventory);
        if (!gearKind.isBlank()) {
            VillagerInteractionTracker.rememberGearReport(serverPlayer.serverLevel(), this.villager, serverPlayer, gearKind);
            this.gearReportProcessed = true;
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
        return this.viewMode.isWorkInventory() && ProtectedVillagerProperty.isProtected(slot.getItem());
    }

    private static ClientMenuData clientData(RegistryFriendlyByteBuf data) {
        if (data == null) {
            return new ClientMenuData(-1, ViewMode.PERSONAL, ViewMode.JOB, true, false, -1L);
        }
        int entityId = data.readVarInt();
        ViewMode viewMode = data.isReadable() ? data.readEnum(ViewMode.class) : ViewMode.PERSONAL;
        ViewMode workInventoryViewMode = data.isReadable() ? data.readEnum(ViewMode.class) : ViewMode.JOB;
        if (!workInventoryViewMode.isWorkInventory()) {
            workInventoryViewMode = ViewMode.JOB;
        }
        boolean personalInventoryAccess = viewMode == ViewMode.PERSONAL;
        if (data.isReadable()) {
            personalInventoryAccess = data.readBoolean();
        }
        boolean jobInventoryAccess = viewMode.isWorkInventory();
        if (data.isReadable()) {
            jobInventoryAccess = data.readBoolean();
        }
        viewMode = allowedViewMode(
                normalizeWorkViewMode(viewMode, workInventoryViewMode),
                personalInventoryAccess,
                jobInventoryAccess,
                workInventoryViewMode);
        long contractEndGameTime = data.isReadable() && data.readBoolean() ? data.readVarLong() : -1L;
        return new ClientMenuData(
                entityId,
                viewMode,
                workInventoryViewMode,
                personalInventoryAccess,
                jobInventoryAccess,
                contractEndGameTime);
    }

    private static boolean personalInventoryAccess(Inventory playerInventory, Villager villager) {
        return !(playerInventory.player instanceof ServerPlayer serverPlayer)
                || villager.level() instanceof ServerLevel level && VillagerInventoryAccess.canAccess(level, villager, serverPlayer);
    }

    private static boolean jobInventoryAccess(Inventory playerInventory, Villager villager) {
        return !(playerInventory.player instanceof ServerPlayer serverPlayer)
                || villager.level() instanceof ServerLevel level
                && VillagerJobInventoryAuthorization.canAccess(level, villager, serverPlayer);
    }

    private static Container createVillagerInventory(Villager villager, ViewMode viewMode) {
        return viewMode.isWorkInventory() ? HiredJobInventory.getJobInventory(villager) : new VillagerInventoryContainer(villager);
    }

    private static ViewMode allowedViewMode(
            ViewMode viewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess,
            ViewMode workInventoryViewMode) {
        ViewMode safeViewMode = viewMode == null ? ViewMode.PERSONAL : viewMode;
        if (safeViewMode == ViewMode.PERSONAL && !personalInventoryAccess && jobInventoryAccess) {
            return workInventoryViewMode;
        }
        if (safeViewMode.isWorkInventory() && !jobInventoryAccess && personalInventoryAccess) {
            return ViewMode.PERSONAL;
        }
        return safeViewMode;
    }

    private static ViewMode normalizeWorkViewMode(ViewMode viewMode, ViewMode workInventoryViewMode) {
        return viewMode != null && viewMode.isWorkInventory() ? workInventoryViewMode : viewMode;
    }

    private static ViewMode workInventoryViewMode(Villager villager) {
        return villager != null
                && villager.level() instanceof ServerLevel level
                && com.jvn.villagerretaliation.party.PartyVillagerContractService.isActivePartyVillager(level, villager)
                ? ViewMode.PARTY
                : ViewMode.JOB;
    }

    private static long contractEndGameTime(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return -1L;
        }
        var partyEnd = com.jvn.villagerretaliation.party.PartyVillagerContractService
                .getPartyEndGameTime(level, villager);
        if (partyEnd.isPresent()) {
            return partyEnd.getAsLong();
        }
        return com.jvn.villagerretaliation.interaction.HiredVillagerContractService
                .getHireEndGameTime(level, villager)
                .orElse(-1L);
    }

    public long remainingContractTicks() {
        if (this.contractEndGameTime < 0L) {
            return -1L;
        }
        return Math.max(0L, this.contractEndGameTime - this.player.level().getGameTime());
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
        return viewMode.isWorkInventory() ? HiredJobInventory.getJobInventory(this.villager) : new VillagerInventoryContainer(this.villager);
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
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            super.setByPlayer(newStack, oldStack);
            if (this.container instanceof HiredJobInventory jobInventory) {
                if (!newStack.isEmpty()) {
                    jobInventory.markPlayerPlacedSupply(this.getSlotIndex());
                }
                if (this.getSlotIndex() == HiredJobInventory.OFFHAND_SLOT) {
                    VillagerDefensiveLoadoutService.markManualOffhand(jobInventory.villager(), !newStack.isEmpty());
                }
            }
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

    private static final class JobFilterSlot extends Slot {
        private final Villager villager;

        private JobFilterSlot(Container container, int slot, int x, int y, Villager villager) {
            super(container, slot, x, y);
            this.villager = villager;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !(player instanceof ServerPlayer serverPlayer)
                    || this.villager != null
                    && this.villager.level() instanceof ServerLevel level
                    && VillagerJobInventoryAuthorization.canAccess(
                            level,
                            this.villager,
                            serverPlayer);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_SLOT_FILTER_ICON);
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
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            super.setByPlayer(newStack, oldStack);
            if (this.equipmentSlot == EquipmentSlot.OFFHAND) {
                VillagerDefensiveLoadoutService.markManualOffhand(this.villager, !newStack.isEmpty());
            }
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, this.icon);
        }
    }

    public enum ViewMode {
        PERSONAL(VillagerInventoryContainer.SLOT_COUNT),
        JOB(HiredJobInventory.SLOT_COUNT),
        PARTY(HiredJobInventory.PARTY_SLOT_COUNT);

        private final int slotCount;

        ViewMode(int slotCount) {
            this.slotCount = slotCount;
        }

        public int slotCount() {
            return this.slotCount;
        }

        public boolean isWorkInventory() {
            return this != PERSONAL;
        }
    }

    private boolean hasJobInventoryAccess(Player player) {
        return this.jobInventoryAccess
                && (!(player instanceof ServerPlayer serverPlayer)
                || this.villager == null
                || this.villager.level() instanceof ServerLevel level
                && VillagerJobInventoryAuthorization.canAccess(level, this.villager, serverPlayer));
    }

    private boolean hasCurrentViewAccess(Player player) {
        if (this.villager == null) {
            return true;
        }
        return this.viewMode == ViewMode.PERSONAL
                ? hasPersonalInventoryAccess(player)
                : hasJobInventoryAccess(player);
    }

    private record ClientMenuData(
            int entityId,
            ViewMode viewMode,
            ViewMode workInventoryViewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess,
            long contractEndGameTime) {
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
        if (slot == HiredJobInventory.FILTER_SLOT) {
            return HELD_X + SLOT_SIZE * 2 + 1;
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
        if (slot == HiredJobInventory.FILTER_SLOT) {
            return OFFHAND_Y;
        }
        if (slot >= HiredJobInventory.HOTBAR_START && slot < HiredJobInventory.FILTER_SLOT) {
            return VILLAGER_HOTBAR_Y;
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
