package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.loot.VillagerLootHandler;
import com.jvn.villagerretaliation.loot.WanderingTraderLootHandler;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerRetaliationRandomUtil;
import com.jvn.villagerretaliation.villager.VillagerFleeBehaviorHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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

        if (event.getTarget() instanceof Villager && player instanceof ServerPlayer serverPlayer) {
            VillagerReputationAdvancements.onVillagerInteraction(serverPlayer);
        }

        ItemStack interactionStack = player.getItemInHand(event.getHand());
        ItemStack pacifyStack = interactionStack.is(Items.EMERALD) ? interactionStack : player.getOffhandItem();

        if (event.getTarget() instanceof Villager villager
                && VillagerRetaliationHandler.tryPacifyWithEmeralds(villager, player, pacifyStack)) {
            if (player instanceof ServerPlayer serverPlayer) {
                VillagerReputationAdvancements.onVillagerPacified(serverPlayer);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && VillagerRetaliationHandler.blockTradingIfHostile(villager, player)) {
            if (player instanceof ServerPlayer serverPlayer
                    && villager.level() instanceof ServerLevel level
                    && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                    <= VillagerReputationLevel.HOSTILE.trustRank()) {
                VillagerReputationAdvancements.onTradeRefusedDueToLowReputation(serverPlayer);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (event.getTarget() instanceof Villager villager) {
            tryGiveHighReputationGift(villager, player, event.getHand());
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

    private static void tryGiveHighReputationGift(Villager villager, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !(villager.level() instanceof ServerLevel level)
                || !villager.isAlive()
                || villager.isBaby()
                || !VillagerReputationManager.canGiveHighReputationGift(level, villager, player)) {
            return;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        ItemStack gift = createHighReputationGift(villager, reputationLevel);
        if (gift.isEmpty()) {
            return;
        }

        ItemStack remainder = gift.copy();
        if (!player.addItem(remainder) && !remainder.isEmpty()) {
            player.drop(remainder, false);
        }

        VillagerReputationManager.markHighReputationGiftGiven(level, villager, player);
        villager.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.85F + villager.getRandom().nextFloat() * 0.25F);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                villager.getX(),
                villager.getY() + villager.getBbHeight() + 0.25D,
                villager.getZ(),
                7,
                0.3D,
                0.2D,
                0.3D,
                0.02D);
    }

    private static ItemStack createHighReputationGift(Villager villager, VillagerReputationLevel reputationLevel) {
        if (reputationLevel != VillagerReputationLevel.REVERED && reputationLevel != VillagerReputationLevel.ROYALTY) {
            return ItemStack.EMPTY;
        }

        boolean royalty = reputationLevel == VillagerReputationLevel.ROYALTY;
        int lowTierCount = royalty ? 2 : 1;
        int highTierCount = royalty ? 4 : 2;
        VillagerProfession profession = villager.getVillagerData().getProfession();

        if (profession == VillagerProfession.FARMER) {
            return new ItemStack(royalty ? Items.GOLDEN_CARROT : Items.BREAD,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount + 1));
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return new ItemStack(royalty ? Items.COOKED_SALMON : Items.COD,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return new ItemStack(royalty ? Items.BOOKSHELF : Items.BOOK,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), 1, royalty ? 2 : 3));
        }
        if (profession == VillagerProfession.CLERIC) {
            return new ItemStack(royalty ? Items.GLOWSTONE_DUST : Items.REDSTONE,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }
        if (profession == VillagerProfession.FLETCHER) {
            return new ItemStack(Items.ARROW,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), royalty ? 6 : 4, royalty ? 12 : 8));
        }
        if (profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH) {
            return new ItemStack(royalty ? Items.IRON_INGOT : Items.COAL,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return new ItemStack(royalty ? Items.COMPASS : Items.MAP, 1);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return new ItemStack(royalty ? Items.WHITE_WOOL : Items.WHITE_CARPET,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount + 1, highTierCount + 2));
        }
        if (profession == VillagerProfession.BUTCHER) {
            return new ItemStack(royalty ? Items.COOKED_BEEF : Items.BEEF,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return new ItemStack(royalty ? Items.LEATHER_HORSE_ARMOR : Items.LEATHER,
                    royalty ? 1 : VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }
        if (profession == VillagerProfession.MASON) {
            return new ItemStack(royalty ? Items.BRICKS : Items.CLAY_BALL,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount + 1));
        }
        if (profession == VillagerProfession.NITWIT || profession == VillagerProfession.NONE) {
            return new ItemStack(royalty ? Items.EMERALD : Items.APPLE,
                    VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
        }

        return new ItemStack(Items.EMERALD,
                VillagerRetaliationRandomUtil.between(villager.getRandom(), lowTierCount, highTierCount));
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        VillagerRetaliationHandler.onEntityLeaveLevel(event);
        WanderingTraderRetaliationHandler.onEntityLeaveLevel(event);
    }
}
