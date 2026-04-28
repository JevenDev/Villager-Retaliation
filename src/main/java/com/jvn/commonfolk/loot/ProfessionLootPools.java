package com.jvn.commonfolk.loot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

public final class ProfessionLootPools {
    @FunctionalInterface
    public interface ProfessionLootPool {
        List<ItemStack> roll(RandomSource random);
    }

    private static final Map<VillagerProfession, ProfessionLootPool> POOLS = new HashMap<>();

    private ProfessionLootPools() {
    }

    public static List<ItemStack> roll(VillagerProfession profession, RandomSource random) {
        ProfessionLootPool pool = POOLS.get(profession);
        if (pool == null) {
            return Collections.emptyList();
        }

        return pool.roll(random).stream().filter(stack -> !stack.isEmpty()).toList();
    }

    public static boolean hasPool(VillagerProfession profession) {
        return POOLS.containsKey(profession);
    }
}
