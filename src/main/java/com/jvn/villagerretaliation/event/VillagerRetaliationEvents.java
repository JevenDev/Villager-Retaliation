package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.loot.VillagerLootHandler;
import com.jvn.villagerretaliation.loot.WanderingTraderLootHandler;
import com.jvn.villagerretaliation.villager.VillagerFleeBehaviorHandler;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationEvents {
    private VillagerRetaliationEvents() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        VillagerRetaliationHandler.onEntityAttributeModification(event);
        WanderingTraderRetaliationHandler.onEntityAttributeModification(event);
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
        WanderingTraderRetaliationHandler.onLivingDamage(event);
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        VillagerRetaliationHandler.onLivingDamagePre(event);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        VillagerRetaliationHandler.onLivingDeath(event);
        WanderingTraderRetaliationHandler.onLivingDeath(event);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        VillagerFleeBehaviorHandler.onEntityTickPre(event);
        VillagerRetaliationHandler.onEntityTickPre(event);
        WanderingTraderRetaliationHandler.onEntityTickPre(event);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        VillagerRetaliationHandler.onEntityTickPost(event);
        WanderingTraderRetaliationHandler.onEntityTickPost(event);
        VillagerFleeBehaviorHandler.onEntityTickPost(event);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        VillagerRetaliationHandler.onEntityJoinLevel(event);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack interactionStack = player.getItemInHand(event.getHand());
        ItemStack pacifyStack = interactionStack.is(Items.EMERALD) ? interactionStack : player.getOffhandItem();

        if (event.getTarget() instanceof Villager villager
                && VillagerRetaliationHandler.tryPacifyWithEmeralds(villager, player, pacifyStack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && VillagerRetaliationHandler.blockTradingIfHostile(villager, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (event.getTarget() instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.tryPacifyWithEmeralds(trader, player, pacifyStack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getTarget() instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.blockTradingIfHostile(trader, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        VillagerRetaliationHandler.onEntityLeaveLevel(event);
        WanderingTraderRetaliationHandler.onEntityLeaveLevel(event);
    }
}
