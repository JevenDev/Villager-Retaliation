package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ui.ToucanItemSlot;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

public final class VillagerItemSlot extends ToucanItemSlot {
    private static final Style STYLE = new Style(VillagerRetaliationClientAssets.ITEM_FILTER_CONTAINER_TEXTURE,
            176, 166, 7, 17, 0x40FFFFFF, 0xFF81B6FF);

    public VillagerItemSlot(int x, int y, Supplier<ItemStack> item, IntConsumer click, IntConsumer scroll) {
        super(x, y, item, click, scroll, STYLE);
    }
}
