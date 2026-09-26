package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.settlement.SettlementDepot;
import com.jvn.villagerretaliation.settlement.SettlementDepotService;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementDepotBlockEntity extends BlockEntity implements Container, MenuProvider {
    private UUID settlementId;
    private UUID depotId;

    public SettlementDepotBlockEntity(BlockPos pos, BlockState state) {
        super(VillagerRetaliationBlockEntityTypes.SETTLEMENT_DEPOT.get(), pos, state);
    }

    public SettlementDepot inventory() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.settlementId == null || this.depotId == null) {
            return null;
        }
        var town = SettlementSavedData.get(serverLevel).get(new VillageAllegianceId(this.settlementId)).orElse(null);
        if (town == null || !town.dimension().equals(serverLevel.dimension().location())) {
            return null;
        }
        var depot = town.depots().get(this.depotId);
        return depot != null && depot.position().equals(this.worldPosition) ? depot : null;
    }

    public void bind(VillageAllegianceId settlement, UUID depot) {
        this.settlementId = settlement.value();
        this.depotId = depot;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            SettlementDepotService.restoreBinding(serverLevel, this);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.settlementId = tag.hasUUID("Settlement") ? tag.getUUID("Settlement") : null;
        this.depotId = tag.hasUUID("Depot") ? tag.getUUID("Depot") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.settlementId != null && this.depotId != null) {
            tag.putUUID("Settlement", this.settlementId);
            tag.putUUID("Depot", this.depotId);
        }
    }

    @Override
    public int getContainerSize() {
        return SettlementDepot.SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        SettlementDepot depot = inventory();
        return depot == null || depot.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        SettlementDepot depot = inventory();
        return depot == null ? ItemStack.EMPTY : depot.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        SettlementDepot depot = inventory();
        return depot == null ? ItemStack.EMPTY : depot.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        SettlementDepot depot = inventory();
        if (depot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = depot.removeItemNoUpdate(slot);
        depot.setChanged();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        SettlementDepot depot = inventory();
        if (depot != null) {
            depot.setItem(slot, stack);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        SettlementDepot depot = inventory();
        return depot != null && depot.canPlaceItem(slot, stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        SettlementDepot depot = inventory();
        if (depot != null) {
            depot.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.settlementId == null
                || player.isSpectator() || !player.isAlive()
                || serverLevel.getBlockEntity(this.worldPosition) != this
                || player.distanceToSqr(this.worldPosition.getCenter()) > 64.0D) {
            return false;
        }
        return SettlementSavedData.get(serverLevel).get(new VillageAllegianceId(this.settlementId))
                .map(town -> town.permits(player.getUUID(), SettlementPermission.LOGISTICS))
                .orElse(false) && inventory() != null;
    }

    @Override
    public void clearContent() {
        SettlementDepot depot = inventory();
        if (depot != null) {
            depot.clearContent();
            depot.setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.villagerretaliation.settlement_depot");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return stillValid(player) ? ChestMenu.sixRows(id, inventory, this) : null;
    }
}
