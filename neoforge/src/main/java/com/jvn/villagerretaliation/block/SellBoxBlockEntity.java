package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.DailySellMarket;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public final class SellBoxBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    public static final int SLOT_COUNT = 1;
    private static final String BALANCE_TAG = "SellBalance";
    private static final String DISPLAY_ITEM_TAG = "DisplayCurrencyItem";
    private static final String DISPLAY_AMOUNT_TAG = "DisplayCurrencyAmount";
    private static final String DISPLAY_COLOR_TAG = "DisplayCurrencyColor";
    private static final int DEFAULT_CURRENCY_COLOR = 0xFF55FF55;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private CurrencyAmount balance = CurrencyAmount.ZERO;
    private ItemStack displayCurrency = ItemStack.EMPTY;
    private String displayCurrencyAmount = "";
    private int displayCurrencyColor = DEFAULT_CURRENCY_COLOR;
    private final IItemHandler inputHandler = new InputHandler();
    private final IItemHandler outputHandler = new OutputHandler();
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
            signalOpenCount(level, pos, state, count, openCount);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof SellBoxMenu menu && menu.isContainer(SellBoxBlockEntity.this);
        }
    };
    private final ChestLidController chestLidController = new ChestLidController();

    public SellBoxBlockEntity(BlockPos pos, BlockState blockState) {
        super(VillagerRetaliationBlockEntityTypes.SELL_BOX.get(), pos, blockState);
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, SellBoxBlockEntity sellBox) {
        sellBox.chestLidController.tickLid();
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.villagerretaliation.sell_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SellBoxMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && level != null && DailySellMarket.price(level.getServer(), stack).isPresent();
    }

    public CurrencyAmount balance() {
        return this.balance;
    }

    public ItemStack displayCurrency() {
        return this.displayCurrency;
    }

    public String displayCurrencyAmount() {
        return this.displayCurrencyAmount;
    }

    public int displayCurrencyColor() {
        return this.displayCurrencyColor;
    }

    public CurrencyAmount pendingValue() {
        return level == null ? CurrencyAmount.ZERO : DailySellMarket.value(level.getServer(), getItem(0));
    }

    public boolean sellPending() {
        if (level == null || level.isClientSide || getItem(0).isEmpty()) {
            return false;
        }
        CurrencyAmount value = DailySellMarket.value(level.getServer(), getItem(0));
        if (value.isZero()) {
            return false;
        }
        this.balance = this.balance.add(value);
        this.items.set(0, ItemStack.EMPTY);
        changedAndSync();
        return true;
    }

    public ItemStack insertForSale(ItemStack incoming, boolean simulate) {
        if (incoming == null || incoming.isEmpty() || level == null || level.isClientSide) {
            return incoming == null ? ItemStack.EMPTY : incoming;
        }
        if (DailySellMarket.price(level.getServer(), incoming).isEmpty()) {
            return incoming;
        }

        ItemStack pending = getItem(0);
        if (!pending.isEmpty() && DailySellMarket.price(level.getServer(), pending).isEmpty()) {
            return incoming;
        }

        int accepted = Math.min(incoming.getCount(), incoming.getMaxStackSize());
        if (accepted <= 0) {
            return incoming;
        }
        if (!simulate) {
            if (!pending.isEmpty()) {
                CurrencyAmount value = DailySellMarket.value(level.getServer(), pending);
                if (value.isZero()) {
                    return incoming;
                }
                this.balance = this.balance.add(value);
            }
            this.items.set(0, incoming.copyWithCount(accepted));
            changedAndSync();
        }
        return accepted == incoming.getCount()
                ? ItemStack.EMPTY
                : incoming.copyWithCount(incoming.getCount() - accepted);
    }

    public ItemStack extractCurrency(int maximum, boolean simulate) {
        return extractCurrency(maximum, simulate, true);
    }

    public ItemStack extractCurrencyForCourier(int maximum, boolean simulate) {
        return extractCurrency(maximum, simulate, false);
    }

    private ItemStack extractCurrency(int maximum, boolean simulate, boolean obeyItemStackLimit) {
        if (maximum <= 0 || level == null || level.isClientSide) {
            return ItemStack.EMPTY;
        }
        BigInteger available = this.balance.wholeUnits();
        if (available.signum() <= 0) {
            return ItemStack.EMPTY;
        }
        BigInteger limit = BigInteger.valueOf(maximum);
        if (obeyItemStackLimit) {
            limit = limit.min(BigInteger.valueOf(VillagerCurrencyResources.maxStackSize(level.getServer())));
        }
        int count = available.min(limit).intValue();
        ItemStack extracted = obeyItemStackLimit
                ? VillagerCurrencyResources.createStack(level.getServer(), count)
                : new ItemStack(VillagerCurrencyResources.primaryItem(level.getServer()), count);
        if (!simulate && !extracted.isEmpty()) {
            this.balance = this.balance.withoutWholeUnits(BigInteger.valueOf(extracted.getCount()));
            changedAndSync();
        }
        return extracted;
    }

    public ItemStack restoreCurrency(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null || level.isClientSide
                || !stack.is(VillagerCurrencyResources.primaryItem(level.getServer()))) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        this.balance = this.balance.add(CurrencyAmount.of(stack.getCount(), 1));
        changedAndSync();
        return ItemStack.EMPTY;
    }

    public int collect(Player player) {
        if (level == null || level.isClientSide || player == null) {
            return 0;
        }
        int collected = 0;
        while (this.balance.wholeUnits().signum() > 0) {
            ItemStack offered = extractCurrency(Integer.MAX_VALUE, true);
            if (offered.isEmpty()) {
                break;
            }
            int before = offered.getCount();
            player.getInventory().add(offered);
            int accepted = before - offered.getCount();
            if (accepted <= 0) {
                break;
            }
            this.balance = this.balance.withoutWholeUnits(BigInteger.valueOf(accepted));
            collected += accepted;
        }
        if (collected > 0) {
            player.getInventory().setChanged();
            changedAndSync();
        }
        return collected;
    }

    public IItemHandler inputHandler() {
        return this.inputHandler;
    }

    public IItemHandler outputHandler() {
        return this.outputHandler;
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openersCounter.incrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openersCounter.decrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        }
        return super.triggerEvent(id, type);
    }

    @Override
    public float getOpenNess(float partialTicks) {
        return this.chestLidController.getOpenness(partialTicks);
    }

    private void signalOpenCount(Level level, BlockPos pos, BlockState state, int oldCount, int openCount) {
        level.blockEvent(pos, state.getBlock(), 1, openCount);
    }

    public void recheckOpen() {
        if (!remove) {
            openersCounter.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
        this.balance = tag.contains(BALANCE_TAG)
                ? CurrencyAmount.load(tag.getCompound(BALANCE_TAG))
                : CurrencyAmount.ZERO;
        ResourceLocation displayItemId = ResourceLocation.tryParse(tag.getString(DISPLAY_ITEM_TAG));
        this.displayCurrency = displayItemId == null
                ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(displayItemId)
                        .map(ItemStack::new)
                        .orElse(ItemStack.EMPTY);
        this.displayCurrencyAmount = tag.getString(DISPLAY_AMOUNT_TAG);
        this.displayCurrencyColor = tag.contains(DISPLAY_COLOR_TAG)
                ? tag.getInt(DISPLAY_COLOR_TAG)
                : DEFAULT_CURRENCY_COLOR;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }
        if (!this.balance.isZero()) {
            tag.put(BALANCE_TAG, this.balance.save());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        if (level != null && !level.isClientSide && level.getServer() != null && !this.balance.isZero()) {
            VillagerCurrencyResources.Text currencyText = VillagerCurrencyResources.text(level.getServer());
            tag.putString(
                    DISPLAY_ITEM_TAG,
                    BuiltInRegistries.ITEM.getKey(VillagerCurrencyResources.primaryItem(level.getServer())).toString());
            tag.putString(DISPLAY_AMOUNT_TAG, this.balance.decimal(2));
            tag.putInt(DISPLAY_COLOR_TAG, currencyText.textColor());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void changedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        SellBoxMenu.syncViewers(this);
    }

    private void playSound(SoundEvent sound) {
        if (level == null) {
            return;
        }
        level.playSound(
                null,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                sound,
                SoundSource.BLOCKS,
                0.5F,
                level.random.nextFloat() * 0.1F + 0.9F);
    }

    private final class InputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            checkSlot(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            checkSlot(slot);
            return insertForSale(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            checkSlot(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            checkSlot(slot);
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            checkSlot(slot);
            return level != null && DailySellMarket.price(level.getServer(), stack).isPresent();
        }
    }

    private final class OutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            checkSlot(slot);
            return extractCurrency(Integer.MAX_VALUE, true);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            checkSlot(slot);
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            checkSlot(slot);
            return extractCurrency(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            checkSlot(slot);
            return level == null ? 64 : VillagerCurrencyResources.maxStackSize(level.getServer());
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            checkSlot(slot);
            return false;
        }
    }

    private static void checkSlot(int slot) {
        if (slot != 0) {
            throw new IllegalArgumentException("Sell box handler has no slot " + slot);
        }
    }
}
