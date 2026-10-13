package com.jvn.villagerretaliation.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Explicit integer indices, including empty gaps; never compact a block's assigned slots. */
public final class StorageInventoryCodec {
    private StorageInventoryCodec() { }
    public static ListTag save(Container inventory,HolderLookup.Provider provider) {
        var list=new ListTag();
        for(int i=0;i<inventory.getContainerSize();i++) if(!inventory.getItem(i).isEmpty()) {
            var entry=new CompoundTag(); entry.putInt("Slot",i); entry.put("Stack",inventory.getItem(i).save(provider)); list.add(entry);
        }
        return list;
    }
    public static void load(Container inventory,ListTag list,HolderLookup.Provider provider) {
        inventory.clearContent();
        var seen=new java.util.HashSet<Integer>();
        for(var raw:list) {
            var entry=(CompoundTag)raw; int slot=entry.getInt("Slot");
            if(slot<0 || slot>=inventory.getContainerSize() || !seen.add(slot)) throw new IllegalArgumentException("Invalid bank inventory slot");
            inventory.setItem(slot,ItemStack.parse(provider,entry.getCompound("Stack")).orElseThrow(()->new IllegalArgumentException("Invalid bank stack")));
        }
    }
}
