package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<Block, PaymentBoxBlock> OAK_PAYMENT_BOX = registerPaymentBox("oak_payment_box", Blocks.OAK_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> SPRUCE_PAYMENT_BOX = registerPaymentBox("spruce_payment_box", Blocks.SPRUCE_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> BIRCH_PAYMENT_BOX = registerPaymentBox("birch_payment_box", Blocks.BIRCH_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> JUNGLE_PAYMENT_BOX = registerPaymentBox("jungle_payment_box", Blocks.JUNGLE_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> ACACIA_PAYMENT_BOX = registerPaymentBox("acacia_payment_box", Blocks.ACACIA_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> DARK_OAK_PAYMENT_BOX = registerPaymentBox("dark_oak_payment_box", Blocks.DARK_OAK_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> MANGROVE_PAYMENT_BOX = registerPaymentBox("mangrove_payment_box", Blocks.MANGROVE_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> CHERRY_PAYMENT_BOX = registerPaymentBox("cherry_payment_box", Blocks.CHERRY_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> BAMBOO_PAYMENT_BOX = registerPaymentBox("bamboo_payment_box", Blocks.BAMBOO_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> CRIMSON_PAYMENT_BOX = registerPaymentBox("crimson_payment_box", Blocks.CRIMSON_PLANKS);
    public static final DeferredHolder<Block, PaymentBoxBlock> WARPED_PAYMENT_BOX = registerPaymentBox("warped_payment_box", Blocks.WARPED_PLANKS);

    public static final List<DeferredHolder<Block, PaymentBoxBlock>> PAYMENT_BOXES = List.of(
            OAK_PAYMENT_BOX,
            SPRUCE_PAYMENT_BOX,
            BIRCH_PAYMENT_BOX,
            JUNGLE_PAYMENT_BOX,
            ACACIA_PAYMENT_BOX,
            DARK_OAK_PAYMENT_BOX,
            MANGROVE_PAYMENT_BOX,
            CHERRY_PAYMENT_BOX,
            BAMBOO_PAYMENT_BOX,
            CRIMSON_PAYMENT_BOX,
            WARPED_PAYMENT_BOX
    );

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

    private static DeferredHolder<Block, PaymentBoxBlock> registerPaymentBox(String id, Block planks) {
        return BLOCKS.register(id, () -> new PaymentBoxBlock(BlockBehaviour.Properties.ofFullCopy(planks)));
    }
}
