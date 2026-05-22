package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationDebugItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<Item, Item> VILLAGER_BREEDING_STICK =
            ITEMS.register("villager_breeding_stick", () -> new VillagerDebugBreedingStickItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> VILLAGER_MATURITY_EMERALD =
            ITEMS.register("villager_maturity_emerald", () -> new VillagerDebugMaturityEmeraldItem(new Item.Properties().stacksTo(1)));

    private VillagerRetaliationDebugItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static boolean isDebugVillagerTool(Item item) {
        return item == VILLAGER_BREEDING_STICK.get() || item == VILLAGER_MATURITY_EMERALD.get();
    }
}
