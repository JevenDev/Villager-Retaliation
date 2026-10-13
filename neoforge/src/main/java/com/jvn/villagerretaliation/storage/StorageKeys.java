package com.jvn.villagerretaliation.storage;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Names are deliberately excluded from lock matching. Blank creative keys do not open locks. */
public final class StorageKeys {
    private StorageKeys() { }
    public static UUID identity(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("StorageLock") ? tag.getUUID("StorageLock") : null;
    }
    public static ItemStack create(Item item, UUID identity) {
        if (identity == null) return ItemStack.EMPTY;
        var stack = new ItemStack(item);
        var tag = new CompoundTag();
        tag.putUUID("StorageLock", identity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
    public static boolean carries(Player player, UUID identity) {
        if (identity == null) return false;
        for (var stack : player.getInventory().items)
            if (stack.is(StorageRegistry.KEY.get()) && identity.equals(identity(stack))) return true;
        var offhand = player.getOffhandItem();
        return offhand.is(StorageRegistry.KEY.get()) && identity.equals(identity(offhand));
    }
}
