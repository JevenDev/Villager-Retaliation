package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<Item, Item> CLIPBOARD =
            ITEMS.register("clipboard", () -> new HiredStorageClipboardItem(new Item.Properties().stacksTo(1)));

    private VillagerRetaliationItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static boolean isClipboard(ItemStack stack) {
        return stack != null && stack.is(CLIPBOARD.get());
    }
}
