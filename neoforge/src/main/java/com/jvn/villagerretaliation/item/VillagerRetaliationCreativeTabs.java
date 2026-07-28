package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VILLAGER_RETALIATION =
            CREATIVE_MODE_TABS.register("villager_retaliation", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.villagerretaliation.villager_retaliation"))
                    .icon(() -> new ItemStack(VillagerRetaliationItems.CLIPBOARD.get()))
                    .displayItems((parameters, output) -> {
                        VillagerRetaliationBlocks.PAYMENT_BOXES.forEach(block -> output.accept(block.get()));
                        output.accept(VillagerRetaliationBlocks.SELL_BOX.get());
                        output.accept(VillagerRetaliationItems.CLIPBOARD.get());
                        output.accept(VillagerRetaliationItems.CONSTRUCTION_BLUEPRINT.get());
                        output.accept(VillagerRetaliationItems.ITEM_FILTER.get());
                        output.accept(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
                    })
                    .build());

    private VillagerRetaliationCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
