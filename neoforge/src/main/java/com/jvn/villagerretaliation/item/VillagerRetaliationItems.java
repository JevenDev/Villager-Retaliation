package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.PaymentBoxVariant;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import java.util.EnumMap;
import java.util.Map;
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
    private static final Map<PaymentBoxVariant, DeferredHolder<Item, BlockItem>> PAYMENT_BOX_ITEMS_BY_VARIANT =
            registerPaymentBoxItems();

    public static final DeferredHolder<Item, BlockItem> OAK_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.OAK);
    public static final DeferredHolder<Item, BlockItem> SPRUCE_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.SPRUCE);
    public static final DeferredHolder<Item, BlockItem> BIRCH_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.BIRCH);
    public static final DeferredHolder<Item, BlockItem> JUNGLE_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.JUNGLE);
    public static final DeferredHolder<Item, BlockItem> ACACIA_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.ACACIA);
    public static final DeferredHolder<Item, BlockItem> DARK_OAK_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.DARK_OAK);
    public static final DeferredHolder<Item, BlockItem> MANGROVE_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.MANGROVE);
    public static final DeferredHolder<Item, BlockItem> CHERRY_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.CHERRY);
    public static final DeferredHolder<Item, BlockItem> BAMBOO_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.BAMBOO);
    public static final DeferredHolder<Item, BlockItem> CRIMSON_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.CRIMSON);
    public static final DeferredHolder<Item, BlockItem> WARPED_PAYMENT_BOX = paymentBoxItem(PaymentBoxVariant.WARPED);

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

    public static DeferredHolder<Item, BlockItem> paymentBoxItem(PaymentBoxVariant variant) {
        return PAYMENT_BOX_ITEMS_BY_VARIANT.get(variant);
    }

    private static Map<PaymentBoxVariant, DeferredHolder<Item, BlockItem>> registerPaymentBoxItems() {
        Map<PaymentBoxVariant, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(PaymentBoxVariant.class);
        for (PaymentBoxVariant variant : PaymentBoxVariant.values()) {
            items.put(variant, registerPaymentBoxItem(variant));
        }
        return Map.copyOf(items);
    }

    private static DeferredHolder<Item, BlockItem> registerPaymentBoxItem(PaymentBoxVariant variant) {
        return ITEMS.register(
                variant.blockId(),
                () -> new BlockItem(VillagerRetaliationBlocks.paymentBox(variant).get(), new Item.Properties()));
    }
}
