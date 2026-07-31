package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PaymentBoxBlockEntity extends RandomizableContainerBlockEntity {
    static final int SLOT_COUNT = 27;
    private static final String DISPLAY_ITEM_TAG = "DisplayCurrencyItem";
    private static final String DISPLAY_AMOUNT_TAG = "DisplayCurrencyAmount";
    private static final String DISPLAY_COLOR_TAG = "DisplayCurrencyColor";
    private static final int DEFAULT_CURRENCY_COLOR = 0xFF55FF55;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private ItemStack displayCurrency = ItemStack.EMPTY;
    private String displayCurrencyAmount = "";
    private int displayCurrencyColor = DEFAULT_CURRENCY_COLOR;
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            PaymentBoxBlockEntity.this.playSound(state, SoundEvents.BARREL_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            PaymentBoxBlockEntity.this.playSound(state, SoundEvents.BARREL_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof PaymentBoxMenu menu && menu.isContainer(PaymentBoxBlockEntity.this);
        }
    };

    public PaymentBoxBlockEntity(BlockPos pos, BlockState blockState) {
        super(VillagerRetaliationBlockEntityTypes.PAYMENT_BOX.get(), pos, blockState);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.villagerretaliation.payment_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new PaymentBoxMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return VillagerCurrencyResources.isCurrency(this.level == null ? null : this.level.getServer(), stack);
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

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
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
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
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
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (level != null && !level.isClientSide && level.getServer() != null) {
            int amount = 0;
            for (ItemStack stack : this.items) {
                if (VillagerCurrencyResources.isCurrency(level.getServer(), stack)) {
                    amount += stack.getCount();
                }
            }
            if (amount > 0) {
                VillagerCurrencyResources.Text currencyText = VillagerCurrencyResources.text(level.getServer());
                tag.putString(
                        DISPLAY_ITEM_TAG,
                        BuiltInRegistries.ITEM.getKey(VillagerCurrencyResources.primaryItem(level.getServer())).toString());
                tag.putString(DISPLAY_AMOUNT_TAG, Integer.toString(amount));
                tag.putInt(DISPLAY_COLOR_TAG, currencyText.textColor());
            }
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void playSound(BlockState state, SoundEvent sound) {
        Vec3i normal = state.getValue(PaymentBoxBlock.FACING).getNormal();
        double x = this.worldPosition.getX() + 0.5 + normal.getX() / 2.0;
        double y = this.worldPosition.getY() + 0.5 + normal.getY() / 2.0;
        double z = this.worldPosition.getZ() + 0.5 + normal.getZ() / 2.0;
        this.level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
    }
}
