package com.jvn.villagerretaliation.storage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import com.jvn.villagerretaliation.settlement.BankTreasury;

public final class StorageBlockEntity extends BlockEntity implements MenuProvider, LidBlockEntity {
    public static final int COOLDOWN_TICKS=6000;
    List<BlockPos> members;
    public UUID identity=UUID.randomUUID();
    public UUID treasury;
    public UUID inventoryOwner;
    public UUID repairReceipt;
    public boolean issued, unlocked;
    public int damage;
    public long repairAfter;
    // A former controller leaves only a mining deadline, never a lock or repair state, on detached blocks.
    public long miningAfter;
    public int fuse=-1;
    private final SimpleContainer local;
    private boolean loading;
    private final ChestLidController doorController=new ChestLidController();
    private final ContainerOpenersCounter openersCounter=new ContainerOpenersCounter() {
        @Override protected void onOpen(Level level,BlockPos pos,BlockState state) {
            level.playSound(null,pos,SoundEvents.IRON_DOOR_OPEN,SoundSource.BLOCKS,0.5F,0.9F+level.random.nextFloat()*0.1F);
        }
        @Override protected void onClose(Level level,BlockPos pos,BlockState state) {
            level.playSound(null,pos,SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,0.5F,0.9F+level.random.nextFloat()*0.1F);
        }
        @Override protected void openerCountChanged(Level level,BlockPos pos,BlockState state,int oldCount,int openCount) {
            level.blockEvent(pos,state.getBlock(),1,openCount);
        }
        @Override protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof StorageMenu menu && menu.isContainer(StorageBlockEntity.this);
        }
    };
    boolean formed;
    public StorageBlockEntity(BlockPos pos,BlockState state) {
        super(StorageRegistry.ENTITY.get(),pos,state);
        members=List.of(pos.immutable());
        local=new SimpleContainer(safe()?54:27) { @Override public void setChanged() { if(!loading) StorageBlockEntity.this.setChanged(); } };
    }
    public static void doorAnimateTick(Level level,BlockPos pos,BlockState state,StorageBlockEntity bank) {
        bank.doorController.tickLid();
    }
    public void startOpen(Player player) {
        if(safe() && !isRemoved() && !player.isSpectator() && level!=null && !level.isClientSide)
            openersCounter.incrementOpeners(player,level,worldPosition,getBlockState());
    }
    public void stopOpen(Player player) {
        if(safe() && !isRemoved() && !player.isSpectator() && level!=null && !level.isClientSide)
            openersCounter.decrementOpeners(player,level,worldPosition,getBlockState());
    }
    public void recheckOpen() {
        if(safe() && !isRemoved()) openersCounter.recheckOpeners(level,worldPosition,getBlockState());
    }
    @Override public boolean triggerEvent(int id,int value) {
        if(safe() && id==1) { doorController.shouldBeOpen(value>0); return true; }
        return super.triggerEvent(id,value);
    }
    @Override public float getOpenNess(float partialTick) { return doorController.getOpenness(partialTick); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        var tag=new CompoundTag();
        if(safe()) tag.putBoolean("DoorOpen",openersCounter.getOpenerCount()>0);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider provider) {
        if(safe()) doorController.shouldBeOpen(tag.getBoolean("DoorOpen"));
    }
    public boolean safe() { return ((StorageBlock)getBlockState().getBlock()).kind==2; }
    public boolean controller() { return ((StorageBlock)getBlockState().getBlock()).kind!=0; }
    public int maxDamage() { return safe()?1:3; }
    public boolean breached() { return damage>=maxDamage(); }
    public boolean locked() { return controller() && !unlocked && !breached(); }
    public long now() { return level instanceof ServerLevel server ? server.getServer().overworld().getGameTime() : 0; }
    public boolean cooling() { return repairAfter>now(); }
    public Container inventory() { return BankTreasury.inventory(this,local); }
    public SimpleContainer localInventory() { return local; }
    public List<StorageBlockEntity> group() { return VaultConnections.members(this); }
    public StorageBlockEntity lock() {
        var lock=group().stream().filter(StorageBlockEntity::controller).findFirst().orElse(null);
        if(lock!=null) BankTreasury.restoreAuthority(lock);
        return lock;
    }
    public void assign(List<BlockPos> positions) {
        members=List.copyOf(positions); formed=true; setChanged();
        if(level!=null) level.invalidateCapabilities(worldPosition);
    }
    public void reconcileTreasury() { BankTreasury.reconcile(this); refreshProtection(); }
    public boolean allowed(Player player) {
        if(level==null || level.isClientSide || player==null || player.isSpectator() || !player.isAlive() || isRemoved()
                || player.distanceToSqr(worldPosition.getCenter())>64 || group().isEmpty()
                || group().stream().anyMatch(b->b.inventory().getContainerSize()!=(b.safe()?54:27))) return false;
        var lock=lock();
        if(lock==null || lock.breached()) return true;
        if(lock.treasury!=null && !BankTreasury.leader(lock,player.getUUID())) return false;
        return !lock.locked() || StorageKeys.carries(player,lock.identity);
    }
    public boolean publicAutomation() {
        if(level==null || level.isClientSide || isRemoved() || group().isEmpty()) return false;
        var lock=lock();
        // A registered treasury also requires a leader for normal withdrawals; hoppers have no leader.
        return lock==null || !lock.locked();
    }
    public IItemHandler automation() {
        // Every operation resolves the current group and permission. Even cached capabilities fail closed.
        return new IItemHandler() {
            private ContainerSlot resolve(int slot) {
                if(slot<0) return null;
                for(var member:group()) {
                    var inventory=member.inventory();
                    if(slot<inventory.getContainerSize()) return new ContainerSlot(inventory,slot);
                    slot-=inventory.getContainerSize();
                }
                return null;
            }
            public int getSlots() { return group().stream().mapToInt(b->b.inventory().getContainerSize()).sum(); }
            public ItemStack getStackInSlot(int slot) {
                var target=resolve(slot); return publicAutomation() && target!=null?target.inventory.getItem(target.slot).copy():ItemStack.EMPTY;
            }
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
                var target=resolve(slot); return publicAutomation() && target!=null?new InvWrapper(target.inventory).insertItem(target.slot,stack,simulate):stack;
            }
            public ItemStack extractItem(int slot,int count,boolean simulate) {
                var target=resolve(slot); var lock=lock();
                return publicAutomation() && target!=null && (lock==null || lock.treasury==null || lock.breached())
                        ?new InvWrapper(target.inventory).extractItem(target.slot,count,simulate):ItemStack.EMPTY;
            }
            public int getSlotLimit(int slot) { return resolve(slot)==null?0:64; }
            public boolean isItemValid(int slot,ItemStack stack) { return publicAutomation() && resolve(slot)!=null; }
        };
    }
    private record ContainerSlot(Container inventory,int slot) { }
    public void issue(Player player) {
        if(!controller() || issued || !(player instanceof ServerPlayer)) return;
        issued=true; setChanged();
        var key=StorageKeys.create(StorageRegistry.KEY.get(),identity);
        if(!player.getInventory().add(key)) player.drop(key,false);
    }
    public void hit() {
        if(!controller()) return;
        damage=Math.min(maxDamage(),damage+1); repairAfter=now()+COOLDOWN_TICKS; fuse=-1;
        level.setBlock(worldPosition,getBlockState().setValue(StorageBlock.CHARGE,false).setValue(StorageBlock.DAMAGE,damage),3);
        changedLock();
    }
    public boolean repair() {
        if(!controller() || cooling() || damage==0 || fuse>=0) return false;
        damage--; unlocked=false; level.setBlock(worldPosition,getBlockState().setValue(StorageBlock.DAMAGE,damage),3);
        changedLock(); return true;
    }
    public void changedLock() { setChanged(); group().forEach(StorageBlockEntity::refreshProtection); }
    public void refreshProtection() {
        if(level==null || level.isClientSide || isRemoved()) return;
        var group=group(); var lock=lock();
        if(lock!=null && lock.repairAfter>miningAfter) { miningAfter=lock.repairAfter; setChanged(); }
        boolean slow=group.isEmpty() || miningAfter>now() || lock!=null && (lock.locked() || lock.cooling());
        if(getBlockState().getValue(StorageBlock.PROTECTED)!=slow)
            level.setBlock(worldPosition,getBlockState().setValue(StorageBlock.PROTECTED,slow),3);
        level.invalidateCapabilities(worldPosition);
    }
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,StorageBlockEntity bank) {
        if(bank.fuse>0 && --bank.fuse==0) {
            bank.hit();
            ((ServerLevel)level).sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1,0,0,0,0);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        }
        if(level.getGameTime()%20==0) {
            VaultConnections.repairMembership(bank);
            BankTreasury.reconcile(bank);
            bank.refreshProtection();
            if(bank.controller()) BankTreasury.depositOverflow(bank);
        }
        if(bank.fuse>=0) bank.setChanged();
    }
    @Override public void onLoad() { super.onLoad(); if(!level.isClientSide) level.scheduleTick(worldPosition,getBlockState().getBlock(),1); }
    @Override public Component getDisplayName() { return Component.translatable("container.villagerretaliation.bank_storage"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return allowed(player)?new StorageMenu(id,inventory,this):null; }
    public void open(ServerPlayer player) {
        if(allowed(player)) player.openMenu(this,buffer->buffer.writeVarInt(group().stream().mapToInt(b->b.inventory().getContainerSize()).sum()));
        else player.displayClientMessage(Component.literal("Locked, unavailable, or reserved for the village leader."),true);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider) {
        super.saveAdditional(tag,provider);
        tag.putUUID("Identity",identity); tag.putBoolean("Issued",issued); tag.putBoolean("Unlocked",unlocked); tag.putBoolean("Formed",formed);
        tag.putInt("Damage",damage); tag.putLong("RepairAfter",repairAfter); tag.putLong("MiningAfter",miningAfter); tag.putInt("Fuse",fuse);
        tag.putLongArray("Members",members.stream().mapToLong(BlockPos::asLong).toArray());
        if(repairReceipt!=null) tag.putUUID("RepairReceipt",repairReceipt);
        if(treasury!=null) tag.putUUID("Treasury",treasury);
        if(inventoryOwner!=null) tag.putUUID("InventoryOwner",inventoryOwner);
        // Registered stocks live ONLY in settlement saved data, next to the economy balance.
        if(inventoryOwner==null) tag.put("Items",StorageInventoryCodec.save(local,provider));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider) {
        super.loadAdditional(tag,provider); loading=true;
        if(tag.hasUUID("Identity")) identity=tag.getUUID("Identity");
        formed=tag.getBoolean("Formed"); issued=tag.getBoolean("Issued"); unlocked=tag.getBoolean("Unlocked"); damage=Math.max(0,Math.min(maxDamage(),tag.getInt("Damage")));
        repairAfter=tag.getLong("RepairAfter"); miningAfter=tag.getLong("MiningAfter"); fuse=tag.contains("Fuse")?tag.getInt("Fuse"):-1;
        repairReceipt=tag.hasUUID("RepairReceipt")?tag.getUUID("RepairReceipt"):null;
        treasury=tag.hasUUID("Treasury")?tag.getUUID("Treasury"):null;
        inventoryOwner=tag.hasUUID("InventoryOwner")?tag.getUUID("InventoryOwner"):null;
        var positions=Arrays.stream(tag.getLongArray("Members")).mapToObj(BlockPos::of).sorted(VaultConnections.ORDER).toList();
        members=positions.contains(worldPosition)&&VaultConnections.rectangle(positions)&&(!safe()||positions.size()==1)?positions:List.of(worldPosition);
        StorageInventoryCodec.load(local,tag.getList("Items",Tag.TAG_COMPOUND),provider); loading=false;
    }
    public CompoundTag pickup(HolderLookup.Provider provider) {
        var tag=new CompoundTag(); tag.putUUID("Identity",identity); tag.putBoolean("Issued",issued);
        tag.putBoolean("Unlocked",unlocked); tag.putInt("Damage",damage);
        if(safe()) {
            var copy=new SimpleContainer(54);
            for(int i=0;i<54;i++) copy.setItem(i,inventory().getItem(i).copy());
            tag.put("Items",StorageInventoryCodec.save(copy,provider));
        }
        return tag;
    }
    public void place(CompoundTag tag,HolderLookup.Provider provider) {
        loadAdditional(tag,provider); members=List.of(worldPosition); treasury=null; inventoryOwner=null; repairAfter=0; miningAfter=0; fuse=-1;
        level.setBlock(worldPosition,getBlockState().setValue(StorageBlock.DAMAGE,damage).setValue(StorageBlock.CHARGE,false),3); setChanged();
    }
}
