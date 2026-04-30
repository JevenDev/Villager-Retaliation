package com.jvn.commonfolk.event;

import com.jvn.commonfolk.combat.VillagerRetaliationHandler;
import com.jvn.commonfolk.loot.VillagerLootHandler;
import com.jvn.commonfolk.loot.WanderingTraderLootHandler;
import com.jvn.commonfolk.villager.VillagerFleeBehaviorHandler;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class CommonfolkEvents {
    private CommonfolkEvents() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        VillagerRetaliationHandler.onEntityAttributeModification(event);
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerLootHandler.addDrops(villager, event);
        } else if (event.getEntity() instanceof WanderingTrader wanderingTrader) {
            WanderingTraderLootHandler.addDrops(wanderingTrader, event);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        VillagerRetaliationHandler.onLivingDamage(event);
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        VillagerRetaliationHandler.onLivingDamagePre(event);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        VillagerRetaliationHandler.onLivingDeath(event);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        VillagerFleeBehaviorHandler.onEntityTickPre(event);
        VillagerRetaliationHandler.onEntityTickPre(event);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        VillagerRetaliationHandler.onEntityTickPost(event);
        VillagerFleeBehaviorHandler.onEntityTickPost(event);
    }
}
