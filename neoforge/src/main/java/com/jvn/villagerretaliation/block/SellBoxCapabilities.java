package com.jvn.villagerretaliation.block;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class SellBoxCapabilities {
    private SellBoxCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                VillagerRetaliationBlockEntityTypes.SELL_BOX.get(),
                (sellBox, side) -> side == Direction.DOWN ? sellBox.outputHandler() : sellBox.inputHandler());
    }
}
