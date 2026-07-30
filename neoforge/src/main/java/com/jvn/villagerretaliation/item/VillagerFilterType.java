package com.jvn.villagerretaliation.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Matching contract shared by every configured physical filter item. */
public interface VillagerFilterType {
    boolean supports(ItemStack filter);

    boolean rawMatches(
            Level level,
            ItemStack filter,
            ItemStack candidate,
            VillagerFilterMatcher.MatchContext context);
}
