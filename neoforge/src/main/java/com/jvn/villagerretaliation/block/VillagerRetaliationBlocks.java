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

    public static final DeferredHolder<Block, PaymentBoxBlock> PAYMENT_BOX =
            BLOCKS.register(
                    "payment_box",
                    () -> new PaymentBoxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredHolder<Block, SellBoxBlock> SELL_BOX =
            BLOCKS.register(
                    "sell_box",
                    () -> new SellBoxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).noOcclusion()));

    public static final List<DeferredHolder<Block, PaymentBoxBlock>> PAYMENT_BOXES = List.of(PAYMENT_BOX);

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
}
