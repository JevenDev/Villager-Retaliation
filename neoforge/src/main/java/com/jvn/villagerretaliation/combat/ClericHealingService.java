package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class ClericHealingService {
    private static final String MEMORY = "VillagerRetaliationClericHealing";
    private static final String PLAYER_THROWS = "PlayerFreeThrows";
    private static final String PARTY_THROW = "PartyFreeThrow";
    private static final String NEXT_THROW = "NextSupportThrow";

    private ClericHealingService() {}

    public enum Source { FREE, PAID, SUPPLIED, NONE }
    public enum Reason { READY, DISABLED, REFUSED, UNAUTHORIZED, FULL_HEALTH, COOLDOWN, INSUFFICIENT_FUNDS, UNAVAILABLE }

    public record Offer(Source source, int price, long cooldownTicks, Reason reason) {
        public boolean available() {
            return reason == Reason.READY;
        }

        public boolean sameTerms(Offer other) {
            return other != null && source == other.source && price == other.price && reason == other.reason;
        }
    }

    public static boolean isCleric(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC;
    }

    public static long gameTime(Villager villager) {
        return villager.level().getServer().overworld().getGameTime();
    }

    public static boolean isPartyCleric(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && PartyService.isRecruitedPartyVillager(level, villager.getUUID());
    }

    public static long freeCooldown(Villager villager, LivingEntity target) {
        CompoundTag memory = villager.getPersistentData().getCompound(MEMORY);
        if (isPartyCleric(villager)) {
            return elapsedCooldown(memory, PARTY_THROW, VillagerRetaliationConfig.PARTY_CLERIC_FREE_HEALING_INTERVAL_TICKS.get(), gameTime(villager));
        }
        return elapsedCooldown(memory.getCompound(PLAYER_THROWS), target.getUUID().toString(),
                VillagerRetaliationConfig.CLERIC_FREE_HEALING_INTERVAL_TICKS.get(), gameTime(villager));
    }

    private static long elapsedCooldown(CompoundTag tag, String key, int interval, long now) {
        return tag.contains(key) ? Math.max(0L, tag.getLong(key) + interval - now) : 0L;
    }

    public static long throwCooldown(Villager villager) {
        long supportDelay = villager.getPersistentData().getCompound(MEMORY).getLong(NEXT_THROW) - gameTime(villager);
        return Math.max(VillagerClericPotionHelper.remainingThrowDelay(villager), Math.max(0L, supportDelay));
    }

    private static boolean eligibleForFree(Villager villager, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && VillagerReputationManager.getReputationLevel((ServerLevel) villager.level(), villager, player.getUUID()).trustRank()
                >= Math.max(VillagerReputationLevel.NEUTRAL.trustRank(),
                VillagerRetaliationConfig.CLERIC_FREE_HEALING_MIN_REPUTATION.get().trustRank());
    }

    private static Source automaticSource(Villager villager, LivingEntity target) {
        if (isPartyCleric(villager)) {
            if (freeCooldown(villager, target) == 0L) return Source.FREE;
            return VillagerInventoryAccess.hasCarriedItem(villager, ClericHealingService::isSuppliedHealing)
                    ? Source.SUPPLIED : Source.NONE;
        }
        if (target instanceof Player player) {
            return eligibleForFree(villager, player) && freeCooldown(villager, target) == 0L
                    ? Source.FREE : Source.NONE;
        }
        return Source.FREE;
    }

    public static boolean canAutomaticallyHeal(Villager villager, LivingEntity target) {
        return isCleric(villager) && VillagerRetaliationConfig.CLERICS_USE_POTIONS.get()
                && automaticSource(villager, target) != Source.NONE;
    }

    public static Offer offer(Villager villager, ServerPlayer player) {
        if (!isCleric(villager) || !VillagerRetaliationConfig.CLERICS_USE_POTIONS.get()) return unavailable(Reason.DISABLED);
        boolean party = isPartyCleric(villager);
        if (party && !PartyService.areInSameParty(villager, player)) return unavailable(Reason.UNAUTHORIZED);
        if (!party && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && VillagerReputationManager.getReputationLevel(player.serverLevel(), villager, player.getUUID()).trustRank()
                < VillagerReputationLevel.NEUTRAL.trustRank()) return unavailable(Reason.REFUSED);
        if (!canThrowRequested(villager, player)) return unavailable(Reason.UNAVAILABLE);
        if (player.getHealth() >= player.getMaxHealth()) return unavailable(Reason.FULL_HEALTH);
        long remaining = freeCooldown(villager, player);
        Source source = party ? automaticSource(villager, player)
                : eligibleForFree(villager, player) && remaining == 0L ? Source.FREE : Source.PAID;
        if (source == Source.NONE) return new Offer(source, 0, remaining, Reason.COOLDOWN);
        int price = source == Source.PAID ? VillagerRetaliationConfig.CLERIC_HEALING_PRICE.get() : 0;
        long delay = throwCooldown(villager);
        if (source != Source.PAID && delay > 0L) return new Offer(source, price, delay, Reason.COOLDOWN);
        if (price > VillagerCurrencyPayment.count(player)) return new Offer(source, price, remaining, Reason.INSUFFICIENT_FUNDS);
        return new Offer(source, price, remaining, Reason.READY);
    }

    private static Offer unavailable(Reason reason) {
        return new Offer(Source.NONE, 0, 0L, reason);
    }

    private static boolean canThrowRequested(Villager villager, ServerPlayer player) {
        return villager.level() == player.level() && !villager.isRemoved()
                && VillagerInteractionService.shouldStayConversable(player, villager)
                && !villager.isSleeping() && !VillagerDownedService.isDowned(villager)
                && !VillagerBehaviorSuppressionPolicy.suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.INTERACTION_MENUS)
                && !VillagerClericPotionHelper.isDrinkingPotion(villager)
                && !player.isInvertedHealAndHarm() && villager.hasLineOfSight(player)
                && villager.distanceToSqr(player) <= 144.0D
                && isSafe(villager, player);
    }

    private static boolean isSafe(Villager villager, LivingEntity target) {
        return !DuelService.isParticipant(villager) && !DuelService.isParticipant(target)
                && villager.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(4.0D), DuelService::isParticipant).isEmpty();
    }

    public static boolean tryRequestedHeal(Villager villager, ServerPlayer player, Offer expected) {
        Offer current = offer(villager, player);
        return current.available() && current.sameTerms(expected)
                && throwHealing(villager, player, current.source(), player, current.price());
    }

    static boolean tryAutomaticHeal(Villager villager, LivingEntity target) {
        if (!canAutomaticallyHeal(villager, target) || throwCooldown(villager) > 0L || !isSafe(villager, target)) return false;
        return throwHealing(villager, target, automaticSource(villager, target), null, 0);
    }

    private static boolean isSuppliedHealing(ItemStack stack) {
        return stack.is(Items.SPLASH_POTION) && VillagerRetaliationPotionUtil.isHealingPotion(stack);
    }

    private static boolean throwHealing(Villager villager, LivingEntity target, Source source, ServerPlayer payer, int price) {
        ItemStack potion = source == Source.SUPPLIED
                ? VillagerInventoryAccess.takeCarriedItem(villager, ClericHealingService::isSuppliedHealing)
                : PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
        if (potion.isEmpty() || payer != null && !VillagerCurrencyPayment.tryRemove(payer, price)) return false;
        if (!VillagerClericPotionHelper.launchSplashPotion(villager, target, (ServerLevel) villager.level(), potion)) {
            if (payer != null && price > 0) VillagerCurrencyPayment.give(payer, price);
            if (source == Source.SUPPLIED) {
                ItemStack remainder = VillagerInventoryAccess.addItem(villager, potion);
                if (!remainder.isEmpty()) villager.spawnAtLocation(remainder);
            }
            return false;
        }
        CompoundTag memory = villager.getPersistentData().getCompound(MEMORY);
        long now = gameTime(villager);
        if (source == Source.FREE) {
            if (isPartyCleric(villager)) {
                memory.putLong(PARTY_THROW, now);
            } else if (target instanceof Player) {
                CompoundTag players = memory.getCompound(PLAYER_THROWS);
                int interval = VillagerRetaliationConfig.CLERIC_FREE_HEALING_INTERVAL_TICKS.get();
                List.copyOf(players.getAllKeys()).stream()
                        .filter(key -> elapsedCooldown(players, key, interval, now) == 0L).forEach(players::remove);
                players.putLong(target.getUUID().toString(), now);
                memory.put(PLAYER_THROWS, players);
            }
        }
        memory.putLong(NEXT_THROW, now + (isPartyCleric(villager) ? 60L : 300L));
        villager.getPersistentData().put(MEMORY, memory);
        return true;
    }
}
