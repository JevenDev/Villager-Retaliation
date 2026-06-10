package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
    public static final DeferredHolder<Item, BlockItem> OAK_PAYMENT_BOX =
            registerBlockItem("oak_payment_box", VillagerRetaliationBlocks.OAK_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> SPRUCE_PAYMENT_BOX =
            registerBlockItem("spruce_payment_box", VillagerRetaliationBlocks.SPRUCE_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> BIRCH_PAYMENT_BOX =
            registerBlockItem("birch_payment_box", VillagerRetaliationBlocks.BIRCH_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> JUNGLE_PAYMENT_BOX =
            registerBlockItem("jungle_payment_box", VillagerRetaliationBlocks.JUNGLE_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> ACACIA_PAYMENT_BOX =
            registerBlockItem("acacia_payment_box", VillagerRetaliationBlocks.ACACIA_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> DARK_OAK_PAYMENT_BOX =
            registerBlockItem("dark_oak_payment_box", VillagerRetaliationBlocks.DARK_OAK_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> MANGROVE_PAYMENT_BOX =
            registerBlockItem("mangrove_payment_box", VillagerRetaliationBlocks.MANGROVE_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> CHERRY_PAYMENT_BOX =
            registerBlockItem("cherry_payment_box", VillagerRetaliationBlocks.CHERRY_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> BAMBOO_PAYMENT_BOX =
            registerBlockItem("bamboo_payment_box", VillagerRetaliationBlocks.BAMBOO_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> CRIMSON_PAYMENT_BOX =
            registerBlockItem("crimson_payment_box", VillagerRetaliationBlocks.CRIMSON_PAYMENT_BOX);
    public static final DeferredHolder<Item, BlockItem> WARPED_PAYMENT_BOX =
            registerBlockItem("warped_payment_box", VillagerRetaliationBlocks.WARPED_PAYMENT_BOX);

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

    private static DeferredHolder<Item, BlockItem> registerBlockItem(String id, DeferredHolder<Block, ? extends Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
