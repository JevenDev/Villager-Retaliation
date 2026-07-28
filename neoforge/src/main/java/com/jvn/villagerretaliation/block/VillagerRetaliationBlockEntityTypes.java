package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaymentBoxBlockEntity>> PAYMENT_BOX =
            BLOCK_ENTITY_TYPES.register("payment_box", () -> BlockEntityType.Builder
                    .of(PaymentBoxBlockEntity::new, VillagerRetaliationBlocks.paymentBoxBlocks())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SellBoxBlockEntity>> SELL_BOX =
            BLOCK_ENTITY_TYPES.register("sell_box", () -> BlockEntityType.Builder
                    .of(SellBoxBlockEntity::new, VillagerRetaliationBlocks.SELL_BOX.get())
                    .build(null));

    private VillagerRetaliationBlockEntityTypes() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
