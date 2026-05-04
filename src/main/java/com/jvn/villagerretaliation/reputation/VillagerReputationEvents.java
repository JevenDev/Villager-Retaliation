package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillagerReputationEvents {
    private static final long SIGHT_SCAN_INTERVAL_TICKS = 20L;
    private static final long DEBUG_SYNC_INTERVAL_TICKS = 40L;
    private static final long HOSTILE_CONTRIBUTION_TTL_TICKS = 20L * 30L;
    private static final double FEARED_CONVERSION_SHAKE_RADIUS = 5.0D;
    private static final double FEARED_CONVERSION_SHAKE_RADIUS_SQR = FEARED_CONVERSION_SHAKE_RADIUS * FEARED_CONVERSION_SHAKE_RADIUS;
    private static final int FEARED_CONVERSION_SHAKE_TICKS = 30;
    private static final long NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS = 20L * 10L;
    private static final int NEGATIVE_REPUTATION_BELL_SEARCH_HORIZONTAL_RADIUS = 32;
    private static final int NEGATIVE_REPUTATION_BELL_SEARCH_VERTICAL_RADIUS = 8;
    private static final Map<UUID, PlayerContribution> HOSTILE_PLAYER_CONTRIBUTIONS = new HashMap<>();
    private static final Map<UUID, Long> NEGATIVE_REPUTATION_BELL_COOLDOWNS = new HashMap<>();

    private VillagerReputationEvents() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        if (event.getEntity() instanceof Enemy) {
            HOSTILE_PLAYER_CONTRIBUTIONS.put(event.getEntity().getUUID(), new PlayerContribution(player.getUUID(), level.getGameTime()));
            return;
        }

        if (!(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }

        int directPenalty = VillagerAggressionPolicy.shouldForgiveAccidentalHit(villager, player)
                ? Math.min(-1, VillagerRetaliationConfig.DIRECT_HIT_PENALTY.get() / 4)
                : VillagerRetaliationConfig.DIRECT_HIT_PENALTY.get();
        VillagerReputationManager.addDirectReputation(level, villager, player.getUUID(), directPenalty);
        applyWitnessed(level, villager, player, VillagerRetaliationConfig.WITNESSED_HIT_PENALTY.get());
        if (villager instanceof Villager gossipSource) {
            VillagerGossipHooks.spreadReputation(level, gossipSource, player.getUUID(), VillagerRetaliationConfig.WITNESSED_HIT_PENALTY.get());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        Entity deceased = event.getEntity();
        Optional<PlayerCredit> creditedPlayer = creditedPlayer(level, event);
        if (deceased instanceof AbstractVillager villager) {
            if (creditedPlayer.isEmpty()) {
                return;
            }
            int penalty = villager instanceof Villager villageResident && villageResident.isBaby()
                    ? VillagerRetaliationConfig.WITNESSED_BABY_KILL_PENALTY.get()
                    : VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get();
            Player player = creditedPlayer.get().player();
            applyWitnessed(level, villager, player, penalty);
            if (villager instanceof Villager gossipSource) {
                VillagerGossipHooks.spreadReputation(level, gossipSource, player.getUUID(), penalty);
            }
        } else if (deceased instanceof IronGolem) {
            if (creditedPlayer.isEmpty()) {
                return;
            }
            applyWitnessed(level, deceased, creditedPlayer.get().player(), VillagerRetaliationConfig.WITNESSED_IRON_GOLEM_KILL_PENALTY.get());
        } else if (deceased instanceof Enemy) {
            HOSTILE_PLAYER_CONTRIBUTIONS.remove(deceased.getUUID());
            if (creditedPlayer.isEmpty()) {
                return;
            }
            PlayerCredit credit = creditedPlayer.get();
            int gain = positiveWitnessGain(credit);
            applyWitnessed(level, deceased, credit.player(), gain);
            spreadWitnessGossip(level, deceased, credit.player(), gain);
        }
    }

    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        AbstractVillager villager = event.getAbstractVillager();
        if (villager.level() instanceof ServerLevel level) {
            VillagerReputationManager.addTradeReputation(level, villager, event.getEntity());
        }
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        Player player = event.getEntity();
        AABB area = player.getBoundingBox().inflate(8.0D);
        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (villager.getTradingPlayer() == player) {
                VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
                VillagerReputationManager.syncToTrackingPlayer(level, villager, player.getUUID());
                return;
            }
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractVillager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % SIGHT_SCAN_INTERVAL_TICKS == Math.floorMod(villager.getUUID().getLeastSignificantBits(), SIGHT_SCAN_INTERVAL_TICKS)) {
            if (villager instanceof Villager villageResident) {
                scanDespisedSight(level, villageResident);
                scanReputationDrivenFlee(level, villageResident);
            }
            scanFearedProximity(level, villager);
        }
        if (VillagerRetaliationConfig.SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY.get()
                && gameTime % DEBUG_SYNC_INTERVAL_TICKS == Math.floorMod(villager.getUUID().getMostSignificantBits(), DEBUG_SYNC_INTERVAL_TICKS)) {
            syncNearbyDebug(level, villager);
        }
        if (gameTime % VillagerRetaliationConfig.REPUTATION_DECAY_INTERVAL.get() == 0L) {
            VillagerReputationManager.pruneOldEntries(level);
            pruneHostileContributions(gameTime);
            pruneNegativeReputationBellCooldowns(gameTime);
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        VillagerReputationManager.flushTierChangeMessages(event.getServer());
    }

    private static Optional<PlayerCredit> creditedPlayer(ServerLevel level, LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            return Optional.of(new PlayerCredit(player, true));
        }
        if (event.getEntity() instanceof LivingEntity livingEntity
                && livingEntity.getKillCredit() instanceof Player player) {
            return Optional.of(new PlayerCredit(player, true));
        }

        PlayerContribution contribution = HOSTILE_PLAYER_CONTRIBUTIONS.get(event.getEntity().getUUID());
        if (contribution == null || level.getGameTime() - contribution.gameTime() > HOSTILE_CONTRIBUTION_TTL_TICKS) {
            return Optional.empty();
        }
        Player player = level.getPlayerByUUID(contribution.playerId());
        return player == null ? Optional.empty() : Optional.of(new PlayerCredit(player, false));
    }

    private static int positiveWitnessGain(PlayerCredit credit) {
        int gain = VillagerRetaliationConfig.POSITIVE_WITNESS_GAIN.get();
        if (credit.fullKillCredit()) {
            return gain;
        }

        int assistedGain = (int) Math.round(gain * VillagerRetaliationConfig.HOSTILE_MOB_ASSIST_REPUTATION_MULTIPLIER.get());
        if (gain > 0) {
            return Math.max(1, assistedGain);
        }
        if (gain < 0) {
            return Math.min(-1, assistedGain);
        }
        return 0;
    }

    private static void pruneHostileContributions(long gameTime) {
        HOSTILE_PLAYER_CONTRIBUTIONS.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > HOSTILE_CONTRIBUTION_TTL_TICKS);
    }

    private static void applyWitnessed(ServerLevel level, Entity source, Player player, int amount) {
        AABB area = source.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == source || !witness.isAlive()) {
                continue;
            }
            if (VillagerRetaliationConfig.VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT.get() && !witness.hasLineOfSight(source)) {
                continue;
            }
            if (!VillagerAggressionPolicy.shouldNearbyVillagerAssist(witness, player, ReputationEventType.WITNESSED_HIT)) {
                continue;
            }
            VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), amount, source.blockPosition());
        }
    }

    private static void spreadWitnessGossip(ServerLevel level, Entity source, Player player, int amount) {
        AABB area = source.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        for (Villager witness : level.getEntitiesOfClass(Villager.class, area)) {
            if (!witness.isAlive()) {
                continue;
            }
            if (VillagerRetaliationConfig.VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT.get() && !witness.hasLineOfSight(source)) {
                continue;
            }
            VillagerGossipHooks.spreadReputation(level, witness, player.getUUID(), amount);
        }
    }

    private static void scanDespisedSight(ServerLevel level, Villager villager) {
        double radius = VillagerRetaliationConfig.DESPISED_SIGHT_RADIUS.get();
        AABB area = villager.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (!villager.hasLineOfSight(player)) {
                continue;
            }
            if (VillagerAggressionPolicy.shouldAttackOnSight(villager, player)) {
                triggerNegativeReputationBell(level, villager, VillagerReputationLevel.DESPISED);
                com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.forceAnger(villager, player);
                return;
            }
        }
    }

    private static void scanReputationDrivenFlee(ServerLevel level, Villager villager) {
        if (!villager.isBaby() && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
            return;
        }

        double radius = VillagerRetaliationConfig.DESPISED_SIGHT_RADIUS.get();
        double radiusSqr = radius * radius;
        AABB area = villager.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (villager.distanceToSqr(player) > radiusSqr || !villager.hasLineOfSight(player)) {
                continue;
            }
            VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
            if (!VillagerAggressionPolicy.shouldFleeFromPlayer(villager, player)) {
                continue;
            }
            if (isBellAlertTier(reputationLevel)) {
                triggerNegativeReputationBell(level, villager, reputationLevel);
            }

            long gameTime = level.getGameTime();
            villager.getBrain().setActiveActivityIfPossible(Activity.PANIC);
            villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, gameTime);
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, player);
            return;
        }
    }

    private static void scanFearedProximity(ServerLevel level, AbstractVillager villager) {
        AABB area = villager.getBoundingBox().inflate(FEARED_CONVERSION_SHAKE_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (villager.distanceToSqr(player) > FEARED_CONVERSION_SHAKE_RADIUS_SQR) {
                continue;
            }
            VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
            if (reputationLevel == VillagerReputationLevel.FEARED) {
                triggerNegativeReputationBell(level, villager, reputationLevel);
                VillagerReputationNetworking.sendFearedPulse(villager, FEARED_CONVERSION_SHAKE_TICKS);
                return;
            }
        }
    }

    private static boolean isBellAlertTier(VillagerReputationLevel reputationLevel) {
        return reputationLevel == VillagerReputationLevel.DESPISED || reputationLevel == VillagerReputationLevel.FEARED;
    }

    private static void triggerNegativeReputationBell(ServerLevel level, AbstractVillager villager, VillagerReputationLevel reputationLevel) {
        if (!isBellAlertTier(reputationLevel)) {
            return;
        }

        long gameTime = level.getGameTime();
        long cooldownUntil = NEGATIVE_REPUTATION_BELL_COOLDOWNS.getOrDefault(villager.getUUID(), 0L);
        if (gameTime < cooldownUntil) {
            return;
        }

        Optional<BlockPos> bellPos = findNearestBell(level, villager.blockPosition());
        if (bellPos.isEmpty()) {
            return;
        }

        BlockState bellState = level.getBlockState(bellPos.get());
        if (!(bellState.getBlock() instanceof BellBlock bellBlock)) {
            return;
        }

        if (bellBlock.attemptToRing(villager, level, bellPos.get(), null)) {
            NEGATIVE_REPUTATION_BELL_COOLDOWNS.put(villager.getUUID(), gameTime + NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS);
        }
    }

    private static Optional<BlockPos> findNearestBell(ServerLevel level, BlockPos origin) {
        BlockPos min = origin.offset(
                -NEGATIVE_REPUTATION_BELL_SEARCH_HORIZONTAL_RADIUS,
                -NEGATIVE_REPUTATION_BELL_SEARCH_VERTICAL_RADIUS,
                -NEGATIVE_REPUTATION_BELL_SEARCH_HORIZONTAL_RADIUS);
        BlockPos max = origin.offset(
                NEGATIVE_REPUTATION_BELL_SEARCH_HORIZONTAL_RADIUS,
                NEGATIVE_REPUTATION_BELL_SEARCH_VERTICAL_RADIUS,
                NEGATIVE_REPUTATION_BELL_SEARCH_HORIZONTAL_RADIUS);

        BlockPos best = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof BellBlock)) {
                continue;
            }

            double distanceSqr = candidate.distSqr(origin);
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                best = candidate.immutable();
            }
        }
        return Optional.ofNullable(best);
    }

    private static void pruneNegativeReputationBellCooldowns(long gameTime) {
        NEGATIVE_REPUTATION_BELL_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static void syncNearbyDebug(ServerLevel level, AbstractVillager villager) {
        double radius = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE.get();
        AABB area = villager.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            VillagerReputationManager.syncToTrackingPlayer(level, villager, player.getUUID());
        }
    }

    private record PlayerContribution(UUID playerId, long gameTime) {
    }

    private record PlayerCredit(Player player, boolean fullKillCredit) {
    }
}
