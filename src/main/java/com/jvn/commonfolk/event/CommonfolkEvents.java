package com.jvn.commonfolk.event;

import com.jvn.commonfolk.loot.VillagerLootHandler;
import com.jvn.commonfolk.loot.WanderingTraderLootHandler;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class CommonfolkEvents {
    private CommonfolkEvents() {
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerLootHandler.addDrops(villager, event);
        } else if (event.getEntity() instanceof WanderingTrader wanderingTrader) {
            WanderingTraderLootHandler.addDrops(wanderingTrader, event);
        }
    }
}
