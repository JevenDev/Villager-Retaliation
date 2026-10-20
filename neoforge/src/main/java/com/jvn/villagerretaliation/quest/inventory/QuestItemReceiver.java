package com.jvn.villagerretaliation.quest.inventory;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface QuestItemReceiver {
    boolean canReceive(List<ItemStack> stacks);
    ItemStack receive(ItemStack stack);
}
