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
        restoreAuthority(bank);
        var town=town(bank,bank.treasury); return town!=null && town.leader().equals(player);
    }
    public static Container inventory(StorageBlockEntity bank,SimpleContainer local) {
        var town=owner(bank);
        if(town==null) return bank.inventoryOwner==null?local:EMPTY;
        var stock=town==null?null:town.economy().bank.stocks.get(bank.identity);
        return stock!=null && stock.position.equals(bank.getBlockPos()) ? stock.inventory : EMPTY;
    }
    private static SettlementRecord owner(StorageBlockEntity bank) {
        if(!(bank.getLevel() instanceof ServerLevel level)) return null;
        var town=SettlementSavedData.get(level).bankOwners.get(bank.identity);
        return town!=null && town.dimension().equals(level.dimension().location()) ? town : null;
    }
    public static void restoreAuthority(StorageBlockEntity bank) {
        var town=owner(bank);
        if(town==null) return;
        var stock=town.economy().bank.stocks.get(bank.identity);
        if(stock.position.equals(bank.getBlockPos())) {bank.inventoryOwner=town.id().value();bank.treasury=stock.registered?town.id().value():null;}
    }
    private static final Container EMPTY=new SimpleContainer(0);
    public static void depositOverflow(StorageBlockEntity bank) {
        var town=town(bank,bank.treasury);
        if(town!=null) {
            var account=town.economy().bank;long now=bank.now();
            if(account.lastAutomaticDeposit>=0 && now-account.lastAutomaticDeposit<20) return;
            account.lastAutomaticDeposit=now;town.economy().depositOverflow();
        }
    }
    public static boolean unregister(ServerPlayer player,StorageBlockEntity bank) {
        if(!leader(bank,player.getUUID()) || !bank.allowed(player) || !StorageKeys.carries(player,bank.identity)) return false;
        var town=owner(bank);
        if(town==null) return false;
        var members=bank.group();
        for(var member:members) { var stock=town.economy().bank.stocks.get(member.identity); if(stock!=null) stock.registered=false; }
        if(!SettlementConstructionJournal.persist(player.serverLevel())) {
            for(var member:members) { var stock=town.economy().bank.stocks.get(member.identity); if(stock!=null) stock.registered=true; }
            return false;
        }
        bank.treasury=null; bank.setChanged(); town.changed(); return true;
    }
    public static boolean register(ServerPlayer player,StorageBlockEntity bank) {
        var data=SettlementSavedData.get(player.serverLevel());
        var town=data.at(player.level().dimension().location(),bank.getBlockPos()).orElse(null);
        if(data.readOnly() || town==null || !town.leader().equals(player.getUUID()) || !bank.controller()
                || !bank.allowed(player) || !StorageKeys.carries(player,bank.identity) || bank.group().isEmpty()
                || bank.group().stream().anyMatch(member->!town.contains(member.getBlockPos()))) return false;
        return register(levelOf(bank),town,bank);
    }
    private static ServerLevel levelOf(StorageBlockEntity bank) { return (ServerLevel)bank.getLevel(); }
    static boolean register(ServerLevel level,SettlementRecord town,StorageBlockEntity bank) {
        var members=bank.group();
        if(!bank.safe() && !bank.vaultFormed() || SettlementSavedData.get(level).readOnly() || members.isEmpty() || members.stream().anyMatch(member -> !town.contains(member.getBlockPos()) || owner(member)!=null && owner(member)!=town)) return false;
        var account=town.economy().bank; account.bind(level.getServer(),level.registryAccess());
        if(members.stream().anyMatch(member->account.stocks.containsKey(member.identity) && !account.stocks.get(member.identity).position.equals(member.getBlockPos()))) return false;
        if(account.stocks.size()+members.stream().filter(member->!account.stocks.containsKey(member.identity)).count()>512) return false;
        var created=new ArrayList<UUID>(); var previous=new HashMap<UUID,Boolean>();
        for(var member:members) {
            var stock=account.stocks.get(member.identity);
            if(stock==null) {
                stock=account.newStock(member.getBlockPos(),member.localInventory().getContainerSize());
                for(int i=0;i<stock.inventory.getContainerSize();i++) stock.inventory.setItem(i,member.localInventory().getItem(i).copy());
                account.stocks.put(member.identity,stock); created.add(member.identity);
            }
            previous.put(member.identity,stock.registered); stock.registered=true;
        }
        // Settlement inventory becomes authoritative BEFORE any chunk drops its local copy.
        if(!SettlementConstructionJournal.persist(level)) {
            previous.forEach((id,registered)->account.stocks.get(id).registered=registered);
            created.forEach(account.stocks::remove); return false;
        }
        for(var member:members) SettlementSavedData.get(level).bankOwners.put(member.identity,town);
        bank.treasury=town.id().value(); bank.setChanged();
        bank.group().forEach(BankTreasury::reconcile);
        town.economy().bank.bind(level.getServer(),level.registryAccess());
        town.economy().depositOverflow();
        return true;
    }
    public static void reconcile(StorageBlockEntity bank) {
        if(!(bank.getLevel() instanceof ServerLevel level) || bank.group().isEmpty()) return;
        var town=owner(bank);
        if(town!=null) {
            var stock=town.economy().bank.stocks.get(bank.identity);
            if(!stock.position.equals(bank.getBlockPos())) return;
            // Stale chunk-local copies never regain authority, even after unregister/reload.
            boolean changed=!town.id().value().equals(bank.inventoryOwner) || !bank.localInventory().isEmpty();
            bank.inventoryOwner=town.id().value(); if(!bank.localInventory().isEmpty()) bank.localInventory().clearContent();
            if(bank.controller()) bank.treasury=stock.registered?town.id().value():null;
            var lock=bank.lock();
            boolean registered=lock!=null && town.id().value().equals(lock.treasury) && town.contains(bank.getBlockPos());
            if(stock.registered!=registered) {stock.registered=registered;town.changed();changed=true;}
            reconcileRepair(level,bank,stock);
            if(changed) bank.setChanged();
            return;
        }
        var lock=bank.lock();
        var desired=lock==null?null:town(bank,lock.treasury);
        if(desired!=null) register(level,desired,lock);
    }
    static boolean repair(ServerLevel level,SettlementRecord town,StorageBlockEntity bank) {
        var stock=town.economy().bank.stocks.get(bank.identity);
        if(stock==null || stock.repairId!=null || bank.damage<=0 || bank.cooling() || bank.fuse>=0) return false;
        var material=SettlementStock.take(level,town,town.id().value(),stack->stack.is(net.minecraft.world.item.Items.IRON_BLOCK),1);
        if(material.isEmpty()) {town.autonomous().status="Treasury repair needs an iron block in permitted supplies";return false;}
        stock.repairId=UUID.randomUUID();stock.repairTarget=bank.damage-1;stock.repairJournaled=false;
        town.changed(); reconcileRepair(level,bank,stock);return true;
    }
    private static void reconcileRepair(ServerLevel level,StorageBlockEntity bank,Stock stock) {
        if(stock.repairId==null) return;
        if(!stock.repairJournaled) {if(!SettlementConstructionJournal.persist(level)) return;stock.repairJournaled=true;}
        var chunk=level.getChunkSource().getChunkNow(bank.getBlockPos().getX()>>4,bank.getBlockPos().getZ()>>4);
        if(chunk==null) return;
        if(!stock.repairId.equals(bank.repairReceipt)) {
            bank.damage=stock.repairTarget;bank.unlocked=false;bank.repairReceipt=stock.repairId;
            level.setBlock(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.DAMAGE,bank.damage),3);bank.changedLock();
        }
        if(stock.repairCheckpoint<0) {SettlementChunkCheckpoint.changed(chunk);stock.repairCheckpoint=SettlementChunkCheckpoint.revision(chunk);}
        if(SettlementChunkCheckpoint.durable(level,chunk,stock.repairCheckpoint)) {
            stock.repairId=null;stock.repairCheckpoint=-1;
            var town=owner(bank);if(town!=null) town.changed();
        }
    }
    public static void remove(StorageBlockEntity bank) {
        var town=owner(bank);
        if(town!=null && town.economy().bank.stocks.get(bank.identity).position.equals(bank.getBlockPos())) {
            town.economy().bank.stocks.remove(bank.identity); town.economy().bank.dirty.run();
            if(bank.getLevel() instanceof ServerLevel level) SettlementSavedData.get(level).bankOwners.remove(bank.identity);
        }
        bank.inventoryOwner=null;
    }
    public static final class Account {
        final Map<UUID,Stock> stocks=new LinkedHashMap<>();
        final Runnable dirty;
        private MinecraftServer server;
        private HolderLookup.Provider provider;
        private boolean loading;
        private long lastAutomaticDeposit=-1;
        Account(Runnable dirty) { this.dirty=dirty; }
        void bind(MinecraftServer server,HolderLookup.Provider provider) { this.server=server; this.provider=provider; }
        Stock newStock(BlockPos pos,int slots) {
            return new Stock(pos.immutable(),new SimpleContainer(slots) { @Override public void setChanged() { if(!loading) dirty.run(); } });
        }
        CurrencyAmount value() { return value(false); }
        CurrencyAmount value(boolean loadedOnly) {
            long count=0;
            for(var stock:stocks.values()) if(stock.registered && (!loadedOnly || available(stock))) for(int i=0;i<stock.inventory.getContainerSize();i++) {
                var stack=stock.inventory.getItem(i); if(VillagerCurrencyResources.isCurrency(server,stack)) count+=stack.getCount();
            }
            return CurrencyAmount.of(count,1);
        }
        private boolean available(Stock stock) {
            if(server==null) return false;
            for(var level:server.getAllLevels()) if(level.hasChunkAt(stock.position) && level.getBlockEntity(stock.position) instanceof StorageBlockEntity bank
                    && bank.inventory()==stock.inventory && !bank.group().isEmpty() && (bank.safe() || bank.vaultFormed())) return true;
            return false;
        }
        CurrencyAmount deposit(CurrencyAmount amount) { return deposit(amount,false); }
        CurrencyAmount deposit(CurrencyAmount amount,boolean loadedOnly) {
            var units=amount.wholeUnits();
            if(units.signum()==0) return amount;
            for(var stock:stocks.values()) {
                if(units.signum()==0) break;
                if(!stock.registered || loadedOnly && !available(stock)) continue;
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
        CurrencyAmount withdraw(CurrencyAmount amount) { return withdraw(amount,false); }
        CurrencyAmount withdraw(CurrencyAmount amount,boolean loadedOnly) {
            CurrencyAmount removed=CurrencyAmount.ZERO;
            for(var stock:stocks.values()) if(stock.registered && (!loadedOnly || available(stock))) for(int i=0;i<stock.inventory.getContainerSize() && removed.compareTo(amount)<0;i++) {
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
                row.putBoolean("Registered",entry.getValue().registered);
                if(entry.getValue().repairId!=null) {row.putUUID("RepairId",entry.getValue().repairId);row.putInt("RepairTarget",entry.getValue().repairTarget);}
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
                stock.registered=!row.contains("Registered") || row.getBoolean("Registered");
                stock.repairId=row.hasUUID("RepairId")?row.getUUID("RepairId"):null;stock.repairTarget=row.getInt("RepairTarget");
                if(stock.repairId!=null && (stock.repairTarget<0 || stock.repairTarget>2)) throw new IllegalArgumentException("Invalid bank repair target");
                StorageInventoryCodec.load(stock.inventory,row.getList("Items",Tag.TAG_COMPOUND),provider); stocks.put(row.getUUID("Id"),stock);
            }
            } finally { loading=false; }
        }
    }
    static final class Stock {
        final BlockPos position; final SimpleContainer inventory; boolean registered=true;
        UUID repairId; int repairTarget; boolean repairJournaled=true; long repairCheckpoint=-1;
        Stock(BlockPos position,SimpleContainer inventory) {this.position=position;this.inventory=inventory;}
        BlockPos position() { return position; } SimpleContainer inventory() {return inventory;}
    }
}
