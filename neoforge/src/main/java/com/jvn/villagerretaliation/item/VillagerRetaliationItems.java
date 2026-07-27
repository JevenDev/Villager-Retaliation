package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
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
    public static final DeferredHolder<Item, Item> CONSTRUCTION_BLUEPRINT =
            ITEMS.register("construction_blueprint", () -> new ConstructionBlueprintItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> ITEM_FILTER =
            ITEMS.register("item_filter", () -> new VillagerItemFilterItem(new Item.Properties().stacksTo(64)));
    public static final DeferredHolder<Item, Item> ATTRIBUTE_FILTER =
            ITEMS.register("attribute_filter", () -> new VillagerAttributeFilterItem(new Item.Properties().stacksTo(64)));
    public static final DeferredHolder<Item, BlockItem> PAYMENT_BOX =
            ITEMS.register(
                    "payment_box",
                    () -> new BlockItem(VillagerRetaliationBlocks.PAYMENT_BOX.get(), new Item.Properties()));

    private VillagerRetaliationItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static boolean isClipboard(ItemStack stack) {
        return stack != null && stack.is(CLIPBOARD.get());
    }

    public static boolean isConstructionBlueprint(ItemStack stack) {
        return ConstructionBlueprintItem.isBlueprint(stack);
    }

    public static boolean isItemFilter(ItemStack stack) {
        return stack != null && stack.is(ITEM_FILTER.get());
    }

    public static boolean isAttributeFilter(ItemStack stack) {
        return stack != null && stack.is(ATTRIBUTE_FILTER.get());
    }

    public static boolean isFilter(ItemStack stack) {
        return isItemFilter(stack) || isAttributeFilter(stack);
    }
}
