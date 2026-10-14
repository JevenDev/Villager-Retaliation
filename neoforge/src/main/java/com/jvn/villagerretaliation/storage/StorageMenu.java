package com.jvn.villagerretaliation.storage;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** All 729 real indices stay fixed on both sides. Paging changes only client slot visibility. */
public final class StorageMenu extends AbstractContainerMenu {
    public final int capacity;
    public int page;
    private final StorageBlockEntity origin, opener;
    private final List<StorageBlockEntity> members;
    private final List<Container> inventories;
    public StorageMenu(int id,Inventory player,RegistryFriendlyByteBuf data) { this(id,player,null,Math.max(27,Math.min(729,data.readVarInt()))); }
    public StorageMenu(int id,Inventory player,StorageBlockEntity origin) { this(id,player,origin,origin.group().stream().mapToInt(b->b.inventory().getContainerSize()).sum()); }
    private StorageMenu(int id,Inventory player,StorageBlockEntity origin,int size) {
        super(StorageRegistry.MENU.get(),id); this.origin=origin; this.opener=origin==null?null:origin.lock(); this.owner=player.player; members=origin==null?List.of():origin.group(); capacity=size; inventories=members.stream().map(StorageBlockEntity::inventory).toList();
        if(origin==null) addBankSlots(new SimpleContainer(size),0);
        else { int offset=0; for(var b:members) { addBankSlots(b.inventory(),offset); offset+=b.inventory().getContainerSize(); } }
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(player,col+row*9+9,8+col*18,140+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(player,col,8+col*18,198));
        if(opener!=null) opener.startOpen(player.player);
    }
    private void addBankSlots(Container inventory,int offset) {
        for(int i=0;i<inventory.getContainerSize();i++) {
            final int index=offset+i;
            addSlot(new Slot(inventory,i,8+(index%9)*18,18+((index/9)%6)*18) {
                @Override public boolean isActive() { return index/54==page; }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
                @Override public boolean mayPlace(ItemStack stack) { return origin==null || origin.allowed(StorageMenu.this.owner); }
            });
        }
    }
    private Player owner;
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        owner=player;
        if(stillValid(player)) super.clicked(slot,button,type,player);
    }
    @Override public boolean stillValid(Player player) {
        return origin==null || origin.allowed(player) && members.equals(origin.group()) && inventories.equals(members.stream().map(StorageBlockEntity::inventory).toList());
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        owner=player;
        if(!stillValid(player)||index<0||index>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(index); if(!slot.hasItem()) return ItemStack.EMPTY;
        var stack=slot.getItem(); var before=stack.copy();
        if(!moveItemStackTo(stack,index<capacity?capacity:0,index<capacity?slots.size():capacity,index<capacity)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); return before;
    }
    public boolean isContainer(StorageBlockEntity bank) { return opener==bank; }
    @Override public void removed(Player player) {
        super.removed(player);
        if(opener!=null) opener.stopOpen(player);
    }
    public int pages() { return (capacity+53)/54; }
}
