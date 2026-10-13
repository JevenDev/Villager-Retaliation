package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.storage.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Treasury inventories and virtual remainder serialize in the SAME settlement record. */
public final class BankTreasury {
    private BankTreasury() { }
    private static SettlementRecord town(StorageBlockEntity bank,UUID id) {
        return id!=null && bank.getLevel() instanceof ServerLevel level ? SettlementSavedData.get(level).get(new VillageAllegianceId(id)).filter(town->town.dimension().equals(level.dimension().location())).orElse(null):null;
    }
    public static boolean leader(StorageBlockEntity bank,UUID player) {
        var town=town(bank,bank.treasury); return town!=null && town.leader().equals(player);
    }
    public static Container inventory(StorageBlockEntity bank,SimpleContainer local) {
        if(bank.inventoryOwner==null) return local;
        var town=town(bank,bank.inventoryOwner);
        var stock=town==null?null:town.economy().bank.stocks.get(bank.identity);
        return stock!=null && stock.position.equals(bank.getBlockPos()) ? stock.inventory : EMPTY;
    }
    private static final Container EMPTY=new SimpleContainer(0);
    public static void depositOverflow(StorageBlockEntity bank) {
        var town=town(bank,bank.treasury);
        if(town!=null) town.economy().depositOverflow();
    }
    public static boolean unregister(ServerPlayer player,StorageBlockEntity bank) {
        if(!leader(bank,player.getUUID()) || !bank.allowed(player) || !StorageKeys.carries(player,bank.identity)) return false;
        bank.treasury=null; bank.setChanged(); bank.group().forEach(BankTreasury::reconcile); return true;
    }
    public static boolean register(ServerPlayer player,StorageBlockEntity bank) {
        var data=SettlementSavedData.get(player.serverLevel());
        var town=data.at(player.level().dimension().location(),bank.getBlockPos()).orElse(null);
        if(data.readOnly() || town==null || !town.leader().equals(player.getUUID()) || !bank.controller()
                || !bank.allowed(player) || !StorageKeys.carries(player,bank.identity) || bank.group().isEmpty()
                || bank.group().stream().anyMatch(member->!town.contains(member.getBlockPos()))) return false;
        bank.treasury=town.id().value(); bank.setChanged();
        bank.group().forEach(BankTreasury::reconcile);
        town.economy().bank.bind(player.getServer(),player.registryAccess());
        town.economy().depositOverflow();
        return true;
    }
    public static void reconcile(StorageBlockEntity bank) {
        if(!(bank.getLevel() instanceof ServerLevel level) || bank.group().isEmpty()) return;
        var lock=bank.lock(); UUID desired=lock==null?null:lock.treasury;
        var town=town(bank,desired);
        if(town!=null && !town.contains(bank.getBlockPos())) desired=null;
        if(Objects.equals(bank.inventoryOwner,desired)) return;
        detach(bank);
        if(desired==null || town==null) return;
        var account=town.economy().bank; account.bind(level.getServer(),level.registryAccess());
        var existing=account.stocks.get(bank.identity);
        if(existing!=null && !existing.position.equals(bank.getBlockPos())) return;
        if(existing==null) {
            var stock=account.newStock(bank.getBlockPos(),bank.localInventory().getContainerSize());
            for(int i=0;i<stock.inventory.getContainerSize();i++) stock.inventory.setItem(i,bank.localInventory().getItem(i).copy());
            account.stocks.put(bank.identity,stock);
        }
        bank.localInventory().clearContent(); bank.inventoryOwner=desired; bank.setChanged(); account.dirty.run();
    }
    private static void detach(StorageBlockEntity bank) {
        var town=town(bank,bank.inventoryOwner);
        if(town!=null) {
            var account=town.economy().bank; var stock=account.stocks.remove(bank.identity);
            if(stock!=null) for(int i=0;i<stock.inventory.getContainerSize();i++) bank.localInventory().setItem(i,stock.inventory.getItem(i).copy());
            account.dirty.run();
        }
        bank.inventoryOwner=null; bank.setChanged();
    }
    public static void remove(StorageBlockEntity bank) {
        var town=town(bank,bank.inventoryOwner);
        if(town!=null) { town.economy().bank.stocks.remove(bank.identity); town.economy().bank.dirty.run(); }
        bank.inventoryOwner=null;
    }
    public static final class Account {
        final Map<UUID,Stock> stocks=new LinkedHashMap<>();
        final Runnable dirty;
        private MinecraftServer server;
        private HolderLookup.Provider provider;
        private boolean loading;
        Account(Runnable dirty) { this.dirty=dirty; }
        void bind(MinecraftServer server,HolderLookup.Provider provider) { this.server=server; this.provider=provider; }
        Stock newStock(BlockPos pos,int slots) {
            return new Stock(pos.immutable(),new SimpleContainer(slots) { @Override public void setChanged() { if(!loading) dirty.run(); } });
        }
        CurrencyAmount value() {
            long count=0;
            for(var stock:stocks.values()) for(int i=0;i<stock.inventory.getContainerSize();i++) {
                var stack=stock.inventory.getItem(i); if(VillagerCurrencyResources.isCurrency(server,stack)) count+=stack.getCount();
            }
            return CurrencyAmount.of(count,1);
        }
        CurrencyAmount deposit(CurrencyAmount amount) {
            var units=amount.wholeUnits();
            for(var stock:stocks.values()) {
                var handler=new InvWrapper(stock.inventory);
                for(int i=0;i<handler.getSlots() && units.signum()>0;i++) {
                    int count=units.min(java.math.BigInteger.valueOf(VillagerCurrencyResources.maxStackSize(server))).intValue();
                    var left=handler.insertItem(i,VillagerCurrencyResources.createStack(server,count),false);
                    int deposited=count-left.getCount(); units=units.subtract(java.math.BigInteger.valueOf(deposited));
                    amount=amount.subtract(CurrencyAmount.of(deposited,1));
                }
            }
            return amount;
        }
        /** Take ceil(amount) physical units; exact fractional change returns to the virtual remainder. */
        CurrencyAmount withdraw(CurrencyAmount amount) {
            CurrencyAmount removed=CurrencyAmount.ZERO;
            for(var stock:stocks.values()) for(int i=0;i<stock.inventory.getContainerSize() && removed.compareTo(amount)<0;i++) {
                var stack=stock.inventory.getItem(i); if(!VillagerCurrencyResources.isCurrency(server,stack)) continue;
                var remaining=amount.subtract(removed);
                int count=remaining.wholeUnits().add(remaining.isWhole()?java.math.BigInteger.ZERO:java.math.BigInteger.ONE)
                        .min(java.math.BigInteger.valueOf(stack.getCount())).intValue();
                stock.inventory.removeItem(i,count); removed=removed.add(CurrencyAmount.of(count,1));
            }
            return removed;
        }
        CompoundTag save() {
            var tag=new CompoundTag(); var list=new ListTag();
            for(var entry:stocks.entrySet()) {
                var row=new CompoundTag(); row.putUUID("Id",entry.getKey()); row.putLong("Position",entry.getValue().position.asLong());
                row.putInt("Slots",entry.getValue().inventory.getContainerSize());
                row.put("Items",StorageInventoryCodec.save(entry.getValue().inventory,provider)); list.add(row);
            }
            tag.put("Stocks",list); return tag;
        }
        void load(CompoundTag tag,HolderLookup.Provider provider) {
            this.provider=provider;
            loading=true;
            try {
            for(var raw:tag.getList("Stocks",Tag.TAG_COMPOUND)) {
                var row=(CompoundTag)raw; int slots=row.getInt("Slots");
                if(!row.hasUUID("Id") || slots!=27 && slots!=54 || stocks.containsKey(row.getUUID("Id"))) throw new IllegalArgumentException("Invalid treasury storage");
                var stock=newStock(BlockPos.of(row.getLong("Position")),slots);
                StorageInventoryCodec.load(stock.inventory,row.getList("Items",Tag.TAG_COMPOUND),provider); stocks.put(row.getUUID("Id"),stock);
            }
            } finally { loading=false; }
        }
    }
    record Stock(BlockPos position,SimpleContainer inventory) { }
}
