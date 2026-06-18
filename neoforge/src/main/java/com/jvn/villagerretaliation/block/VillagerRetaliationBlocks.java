package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, VillagerRetaliation.MOD_ID);

    private static final Map<PaymentBoxVariant, DeferredHolder<Block, PaymentBoxBlock>> PAYMENT_BOXES_BY_VARIANT =
            registerPaymentBoxes();

    public static final DeferredHolder<Block, PaymentBoxBlock> OAK_PAYMENT_BOX = paymentBox(PaymentBoxVariant.OAK);
    public static final DeferredHolder<Block, PaymentBoxBlock> SPRUCE_PAYMENT_BOX = paymentBox(PaymentBoxVariant.SPRUCE);
    public static final DeferredHolder<Block, PaymentBoxBlock> BIRCH_PAYMENT_BOX = paymentBox(PaymentBoxVariant.BIRCH);
    public static final DeferredHolder<Block, PaymentBoxBlock> JUNGLE_PAYMENT_BOX = paymentBox(PaymentBoxVariant.JUNGLE);
    public static final DeferredHolder<Block, PaymentBoxBlock> ACACIA_PAYMENT_BOX = paymentBox(PaymentBoxVariant.ACACIA);
    public static final DeferredHolder<Block, PaymentBoxBlock> DARK_OAK_PAYMENT_BOX = paymentBox(PaymentBoxVariant.DARK_OAK);
    public static final DeferredHolder<Block, PaymentBoxBlock> MANGROVE_PAYMENT_BOX = paymentBox(PaymentBoxVariant.MANGROVE);
    public static final DeferredHolder<Block, PaymentBoxBlock> CHERRY_PAYMENT_BOX = paymentBox(PaymentBoxVariant.CHERRY);
    public static final DeferredHolder<Block, PaymentBoxBlock> BAMBOO_PAYMENT_BOX = paymentBox(PaymentBoxVariant.BAMBOO);
    public static final DeferredHolder<Block, PaymentBoxBlock> CRIMSON_PAYMENT_BOX = paymentBox(PaymentBoxVariant.CRIMSON);
    public static final DeferredHolder<Block, PaymentBoxBlock> WARPED_PAYMENT_BOX = paymentBox(PaymentBoxVariant.WARPED);

    public static final List<DeferredHolder<Block, PaymentBoxBlock>> PAYMENT_BOXES =
            List.of(PaymentBoxVariant.values()).stream()
                    .map(VillagerRetaliationBlocks::paymentBox)
                    .toList();

    private VillagerRetaliationBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    public static Block[] paymentBoxBlocks() {
        return PAYMENT_BOXES.stream()
                .map(DeferredHolder::get)
                .toArray(Block[]::new);
    }

    public static DeferredHolder<Block, PaymentBoxBlock> paymentBox(PaymentBoxVariant variant) {
        return PAYMENT_BOXES_BY_VARIANT.get(variant);
    }

    private static Map<PaymentBoxVariant, DeferredHolder<Block, PaymentBoxBlock>> registerPaymentBoxes() {
        Map<PaymentBoxVariant, DeferredHolder<Block, PaymentBoxBlock>> boxes = new EnumMap<>(PaymentBoxVariant.class);
        for (PaymentBoxVariant variant : PaymentBoxVariant.values()) {
            boxes.put(variant, registerPaymentBox(variant));
        }
        return Map.copyOf(boxes);
    }

    private static DeferredHolder<Block, PaymentBoxBlock> registerPaymentBox(PaymentBoxVariant variant) {
        return BLOCKS.register(variant.blockId(), () -> new PaymentBoxBlock(BlockBehaviour.Properties.ofFullCopy(variant.planks())));
    }
}
