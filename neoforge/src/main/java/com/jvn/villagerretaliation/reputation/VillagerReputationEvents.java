package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.VillagerTradePaymentTracker;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.toucanlib.util.ToucanHazardAttribution;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
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
    private static final long COMMONFOLK_VILLAGE_SCAN_INTERVAL_TICKS = 40L;
    private static final long NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS = 20L * 10L;
    private static final long NEGATIVE_REPUTATION_BELL_CACHE_TICKS = 20L * 5L;
    private static final int NEGATIVE_REPUTATION_BELL_SEARCH_RADIUS = 32;
    private static final int CURED_VILLAGER_REPUTATION = 100;
    private static final Map<UUID, PlayerContribution> HOSTILE_PLAYER_CONTRIBUTIONS = new HashMap<>();
    private static final Map<UUID, Long> NEGATIVE_REPUTATION_BELL_COOLDOWNS = new HashMap<>();
    private static final Map<BellSearchKey, BellSearchResult> NEGATIVE_REPUTATION_BELL_CACHE = new HashMap<>();
    private static final Map<BellCooldownKey, Long> NEGATIVE_REPUTATION_BELL_POSITION_COOLDOWNS = new HashMap<>();

    private VillagerReputationEvents() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LivingEntity damaged)
                || !(VillagerRetaliationVillagerCombatUtil.resolveAttacker(damaged, event.getSource()).orElse(null) instanceof Player player)) {
            return;
        }

        if (event.getEntity() instanceof Enemy) {
            HOSTILE_PLAYER_CONTRIBUTIONS.put(event.getEntity().getUUID(), new PlayerContribution(player.getUUID(), level.getGameTime()));
            return;
        }

        if (event.getEntity() instanceof IronGolem ironGolem) {
            if (player instanceof ServerPlayer serverPlayer) {
                VillagerReputationAdvancements.onIronGolemDamaged(level, serverPlayer, ironGolem);
            }
            return;
        }

        if (!(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }
        if (villager.isDeadOrDying() || villager.getHealth() <= 0.0F) {
            return;
        }

        boolean attributedHazardDamage = ToucanHazardAttribution.isPlayerAttributedVanillaHazardDamage(damaged, event.getSource());
        if (player instanceof ServerPlayer serverPlayer) {
            if (attributedHazardDamage) {
                VillagerReputationAdvancements.onVillagerEnvironmentalDamage(level, serverPlayer, villager);
            } else {
                VillagerReputationAdvancements.onVillagerDirectlyDamaged(level, serverPlayer, villager);
                if (villager instanceof Villager villageResident) {
                    VillagerInteractionTracker.rememberDirectHit(level, villageResident, serverPlayer, describeHeldWeapon(player.getMainHandItem()));
                }
            }
        }

        int directPenalty = VillagerAggressionPolicy.shouldForgiveAccidentalHit(villager, player)
                ? Math.min(-1, VillagerRetaliationConfig.DIRECT_HIT_PENALTY.get() / 4)
                : VillagerRetaliationConfig.DIRECT_HIT_PENALTY.get();
        if (attributedHazardDamage) {
            directPenalty = halfReputationChange(directPenalty);
        }
        int witnessedPenalty = attributedHazardDamage
                ? halfReputationChange(VillagerRetaliationConfig.WITNESSED_HIT_PENALTY.get())
                : VillagerRetaliationConfig.WITNESSED_HIT_PENALTY.get();
        VillagerReputationManager.addDirectReputation(level, villager, player.getUUID(), directPenalty);
        applyWitnessed(level, villager, player, witnessedPenalty);
        if (villager instanceof Villager gossipSource) {
            VillagerGossipHooks.spreadReputation(level, gossipSource, player.getUUID(), witnessedPenalty);
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
                if (!(event.getSource().getEntity() instanceof Player)) {
                    findLikelyHazardAttributionPlayer(level, villager)
                            .ifPresent(player -> VillagerReputationAdvancements.onVillagerDeath(level, villager, player, false));
                }
                return;
            }
            int penalty = villager instanceof Villager villageResident && villageResident.isBaby()
                    ? VillagerRetaliationConfig.WITNESSED_BABY_KILL_PENALTY.get()
                    : VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get();
            Player player = creditedPlayer.get().player();
            if (!creditedPlayer.get().fullKillCredit()) {
                penalty = halfReputationChange(penalty);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                boolean directDamageSource = event.getSource().getEntity() instanceof Player;
                VillagerReputationAdvancements.onVillagerDeath(level, villager, serverPlayer, directDamageSource);
            }
            applyWitnessed(level, villager, player, penalty);
            if (villager instanceof Villager gossipSource) {
                VillagerGossipHooks.spreadReputation(level, gossipSource, player.getUUID(), penalty);
            }
        } else if (deceased instanceof IronGolem) {
            if (creditedPlayer.isEmpty()) {
                return;
            }
            int penalty = creditedPlayer.get().fullKillCredit()
                    ? VillagerRetaliationConfig.WITNESSED_IRON_GOLEM_KILL_PENALTY.get()
                    : halfReputationChange(VillagerRetaliationConfig.WITNESSED_IRON_GOLEM_KILL_PENALTY.get());
            applyWitnessed(level, deceased, creditedPlayer.get().player(), penalty);
        } else if (deceased instanceof Enemy) {
            HOSTILE_PLAYER_CONTRIBUTIONS.remove(deceased.getUUID());
            if (creditedPlayer.isEmpty()) {
                return;
            }
            PlayerCredit credit = creditedPlayer.get();
            int gain = positiveWitnessGain(credit);
            boolean hadDistrustedVillagerNearby = credit.player() instanceof ServerPlayer serverPlayer
                    && VillagerReputationAdvancements.hasDistrustedVillagerNearby(level, deceased.blockPosition(), serverPlayer);
            List<AbstractVillager> witnesses = applyWitnessed(level, deceased, credit.player(), gain);
            spreadWitnessGossip(witnesses, credit.player(), gain);
            if (gain > 0 && hadDistrustedVillagerNearby && credit.player() instanceof ServerPlayer serverPlayer) {
                VillagerReputationAdvancements.onHeroicDefenseReputationGain(serverPlayer);
            }
        }
    }

    private static Optional<ServerPlayer> findLikelyHazardAttributionPlayer(ServerLevel level, AbstractVillager villager) {
        AABB area = villager.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
            if (reputationLevel.trustRank() <= VillagerReputationLevel.SUSPICIOUS.trustRank()) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        AbstractVillager villager = event.getAbstractVillager();
        if (villager.level() instanceof ServerLevel level) {
            VillagerReputationManager.addTradeReputation(level, villager, event.getEntity());
            if (villager instanceof Villager villageResident && event.getEntity() instanceof ServerPlayer serverPlayer) {
                storeTradePayments(level, villageResident, serverPlayer, event.getMerchantOffer());
            }
            VillagerAmbientIndicatorService.onTradeCompleted(level, villager, event.getEntity());
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VillagerReputationAdvancements.onTradeCompleted(level, serverPlayer, villager);
            }
        }
    }

    private static void storeTradePayments(ServerLevel level, Villager villager, ServerPlayer player, MerchantOffer offer) {
        if (offer == null) {
            return;
        }

        ItemStack costA = offer.getCostA();
        if (!costA.isEmpty()) {
            VillagerTradePaymentTracker.storeTradePayment(level, villager, player, costA);
        }
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty()) {
            VillagerTradePaymentTracker.storeTradePayment(level, villager, player, costB);
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
            scanReputationReactions(level, villager);
        }
        VillagerAmbientIndicatorService.maybeMurmurNearPlayers(level, villager);
        if (VillagerRetaliationConfig.SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY.get()
                && gameTime % DEBUG_SYNC_INTERVAL_TICKS == Math.floorMod(villager.getUUID().getMostSignificantBits(), DEBUG_SYNC_INTERVAL_TICKS)) {
            syncNearbyDebug(level, villager);
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        VillagerReputationManager.flushTierChangeMessages(event.getServer());
        VillagerGossipHooks.processPending(gameTime);
        VillagerReputationManager.pruneSyncState(gameTime);

        if (gameTime % VillagerRetaliationConfig.REPUTATION_DECAY_INTERVAL.get() == 0L) {
            VillagerReputationManager.pruneOldEntries(event.getServer().overworld());
            pruneHostileContributions(gameTime);
            pruneNegativeReputationBellState(gameTime);
        }

        if (gameTime % COMMONFOLK_VILLAGE_SCAN_INTERVAL_TICKS == 0L) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                VillagerReputationAdvancements.onVillagePresenceCheck(player);
            }
        }

        if (gameTime % SIGHT_SCAN_INTERVAL_TICKS == 0L) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                VillagerReputationAdvancements.onPlayerHostilityCheck(player.serverLevel(), player);
            }
        }
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity source = event.getEntity();
        LivingEntity outcome = event.getOutcome();
        boolean villagerFormConversion = (source instanceof Villager || source instanceof ZombieVillager)
                && (outcome instanceof Villager || outcome instanceof ZombieVillager);
        if (!villagerFormConversion) {
            return;
        }

        UUID curingPlayerId = null;
        boolean hadKnownReputationBeforeCure = false;
        if (source instanceof ZombieVillager zombieVillager) {
            CompoundTag zombieTag = new CompoundTag();
            zombieVillager.saveWithoutId(zombieTag);
            if (zombieTag.hasUUID("ConversionPlayer")) {
                curingPlayerId = zombieTag.getUUID("ConversionPlayer");
            }
            if (curingPlayerId != null) {
                hadKnownReputationBeforeCure = VillagerReputationManager.hasStoredReputation(level, source.getUUID(), curingPlayerId);
            }
        }

        VillagerReputationManager.transferVillagerIdentity(level, source.getUUID(), outcome.getUUID());

        if (source instanceof ZombieVillager
                && outcome instanceof Villager curedVillager
                && curingPlayerId != null) {
            VillagerReputationManager.setReputation(level, curedVillager, curingPlayerId, CURED_VILLAGER_REPUTATION);
            VillageEventMemory.rememberCuredVillager(
                    level,
                    curedVillager.blockPosition(),
                    curedVillager,
                    curingPlayerId,
                    VillagerPresetNameRegistry.resolveDisplayName(curedVillager).getString()
            );
            if (hadKnownReputationBeforeCure) {
                VillagerInteractionTracker.rememberCuredRecognition(level, curedVillager, curingPlayerId);
            }
            if (hadKnownReputationBeforeCure
                    && level.getPlayerByUUID(curingPlayerId) instanceof ServerPlayer serverPlayer) {
                VillagerReputationAdvancements.onCuredKnownZombieVillager(serverPlayer);
            }
        }
    }

    private static Optional<PlayerCredit> creditedPlayer(ServerLevel level, LivingDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity
                && event.getSource().getEntity() instanceof Player player) {
            return Optional.of(new PlayerCredit(player, true));
        }
        if (event.getEntity() instanceof LivingEntity livingEntity
                && ToucanHazardAttribution.resolveVanillaHazardOwner(livingEntity, event.getSource()).orElse(null) instanceof Player player) {
            return Optional.of(new PlayerCredit(player, false));
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

    private static int halfReputationChange(int amount) {
        if (amount == 0) {
            return 0;
        }

        int halved = Math.round(amount / 2.0F);
        return amount > 0 ? Math.max(1, halved) : Math.min(-1, halved);
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

    private static String describeHeldWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "fists";
        }
        String name = stack.getHoverName().getString().trim();
        return name.isEmpty() ? "fists" : name;
    }

    private static void pruneHostileContributions(long gameTime) {
        HOSTILE_PLAYER_CONTRIBUTIONS.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > HOSTILE_CONTRIBUTION_TTL_TICKS);
    }

    private static List<AbstractVillager> applyWitnessed(ServerLevel level, Entity source, Player player, int amount) {
        List<AbstractVillager> witnesses = witnessesNear(level, source);
        applyWitnessed(level, witnesses, source, player, amount);
        return witnesses;
    }

    private static List<AbstractVillager> witnessesNear(ServerLevel level, Entity source) {
        AABB area = source.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        List<AbstractVillager> witnesses = new ArrayList<>();
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == source || !witness.isAlive()) {
                continue;
            }
            if (VillagerRetaliationConfig.VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT.get() && !witness.hasLineOfSight(source)) {
                continue;
            }
            witnesses.add(witness);
        }
        return witnesses;
    }

    private static void applyWitnessed(ServerLevel level, List<AbstractVillager> witnesses, Entity source, Player player, int amount) {
        for (AbstractVillager witness : witnesses) {
            if (!VillagerAggressionPolicy.shouldNearbyVillagerAssist(witness, player, ReputationEventType.WITNESSED_HIT)) {
                continue;
            }
            VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), amount, source.blockPosition());
        }
    }

    private static void spreadWitnessGossip(List<AbstractVillager> witnesses, Player player, int amount) {
        for (AbstractVillager witness : witnesses) {
            if (witness instanceof Villager villager && villager.level() instanceof ServerLevel level) {
                VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), amount);
            }
        }
    }

    private static void scanReputationReactions(ServerLevel level, AbstractVillager villager) {
        double radius = VillagerRetaliationConfig.DESPISED_SIGHT_RADIUS.get();
        double radiusSqr = radius * radius;
        List<NearbyPlayerReputation> nearbyPlayers = nearbyPlayerReputations(level, villager, radius, radiusSqr);
        if (nearbyPlayers.isEmpty()) {
            return;
        }

        if (villager instanceof Villager villageResident) {
            scanDespisedSight(level, villageResident, nearbyPlayers);
            scanReputationDrivenFlee(level, villageResident, nearbyPlayers);
        }
        scanFearedProximity(level, villager, nearbyPlayers);
        scanNegativeReputationGolemAggro(level, villager, nearbyPlayers, radius, radiusSqr);
    }

    private static List<NearbyPlayerReputation> nearbyPlayerReputations(
            ServerLevel level,
            AbstractVillager villager,
            double radius,
            double radiusSqr) {
        AABB area = villager.getBoundingBox().inflate(radius);
        List<NearbyPlayerReputation> players = new ArrayList<>();
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            double distanceSqr = villager.distanceToSqr(player);
            if (distanceSqr > radiusSqr) {
                continue;
            }
            boolean visible = villager.hasLineOfSight(player);
            VillagerReputationLevel reputationLevel =
                    VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
            players.add(new NearbyPlayerReputation(player, distanceSqr, visible, reputationLevel));
        }
        return players;
    }

    private static void scanDespisedSight(
            ServerLevel level,
            Villager villager,
            List<NearbyPlayerReputation> nearbyPlayers) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.get()
                || villager.isBaby()
                || !com.jvn.villagerretaliation.combat.VillagerCombatRoles.canFightBack(villager)
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            return;
        }

        for (NearbyPlayerReputation nearbyPlayer : nearbyPlayers) {
            if (!nearbyPlayer.visible() || nearbyPlayer.reputationLevel() != VillagerReputationLevel.DESPISED) {
                continue;
            }
            triggerNegativeReputationBell(level, villager, VillagerReputationLevel.DESPISED);
            com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.forceAnger(villager, nearbyPlayer.player());
            return;
        }
    }

    private static void scanReputationDrivenFlee(
            ServerLevel level,
            Villager villager,
            List<NearbyPlayerReputation> nearbyPlayers) {
        if (!villager.isBaby() && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
            return;
        }

        for (NearbyPlayerReputation nearbyPlayer : nearbyPlayers) {
            if (!nearbyPlayer.visible() || !shouldFleeFromReputation(villager, nearbyPlayer.reputationLevel())) {
                continue;
            }
            if (isBellAlertTier(nearbyPlayer.reputationLevel())) {
                triggerNegativeReputationBell(level, villager, nearbyPlayer.reputationLevel());
            }

            VillagerRetaliationVillagerBrainUtil.enterFleeState(villager, nearbyPlayer.player(), level.getGameTime());
            return;
        }
    }

    private static boolean shouldFleeFromReputation(Villager villager, VillagerReputationLevel reputationLevel) {
        boolean lowEnoughToFlee = reputationLevel == VillagerReputationLevel.HOSTILE
                || reputationLevel == VillagerReputationLevel.DESPISED
                || reputationLevel == VillagerReputationLevel.FEARED;
        return lowEnoughToFlee
                && (villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || !com.jvn.villagerretaliation.combat.VillagerCombatRoles.canFightBack(villager));
    }

    private static void scanFearedProximity(
            ServerLevel level,
            AbstractVillager villager,
            List<NearbyPlayerReputation> nearbyPlayers) {
        for (NearbyPlayerReputation nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer.distanceSqr() > FEARED_CONVERSION_SHAKE_RADIUS_SQR) {
                continue;
            }
            if (nearbyPlayer.reputationLevel() == VillagerReputationLevel.FEARED) {
                triggerNegativeReputationBell(level, villager, VillagerReputationLevel.FEARED);
                VillagerReputationNetworking.sendFearedPulse(villager, FEARED_CONVERSION_SHAKE_TICKS);
                return;
            }
        }
    }

    private static void scanNegativeReputationGolemAggro(
            ServerLevel level,
            AbstractVillager villager,
            List<NearbyPlayerReputation> nearbyPlayers,
            double radius,
            double radiusSqr) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return;
        }

        for (NearbyPlayerReputation nearbyPlayer : nearbyPlayers) {
            if (!nearbyPlayer.visible() || !isBellAlertTier(nearbyPlayer.reputationLevel())) {
                continue;
            }
            if (aggroNearbyIronGolems(villager, nearbyPlayer.player(), radius, radiusSqr)) {
                triggerNegativeReputationBell(level, villager, nearbyPlayer.reputationLevel());
                return;
            }
        }
    }

    private static boolean aggroNearbyIronGolems(AbstractVillager villager, Player player, double radius, double radiusSqr) {
        ServerLevel level = (ServerLevel) villager.level();
        AABB area = villager.getBoundingBox().inflate(radius);
        boolean aggroedAny = false;
        for (IronGolem ironGolem : level.getEntitiesOfClass(IronGolem.class, area)) {
            if (!ironGolem.isAlive()
                    || ironGolem.distanceToSqr(player) > radiusSqr
                    || !ironGolem.hasLineOfSight(player)) {
                continue;
            }
            ironGolem.setTarget(player);
            ironGolem.setPersistentAngerTarget(player.getUUID());
            ironGolem.startPersistentAngerTimer();
            aggroedAny = true;
        }
        return aggroedAny;
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

        Optional<BlockPos> bellPos = findNearestBell(level, villager);
        if (bellPos.isEmpty()) {
            return;
        }
        BellCooldownKey bellCooldownKey = BellCooldownKey.of(level, bellPos.get());
        long bellCooldownUntil = NEGATIVE_REPUTATION_BELL_POSITION_COOLDOWNS.getOrDefault(bellCooldownKey, 0L);
        if (gameTime < bellCooldownUntil) {
            NEGATIVE_REPUTATION_BELL_COOLDOWNS.put(villager.getUUID(), gameTime + NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS);
            return;
        }

        BlockState bellState = level.getBlockState(bellPos.get());
        if (!(bellState.getBlock() instanceof BellBlock bellBlock)) {
            return;
        }

        if (bellBlock.attemptToRing(villager, level, bellPos.get(), null)) {
            NEGATIVE_REPUTATION_BELL_COOLDOWNS.put(villager.getUUID(), gameTime + NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS);
            NEGATIVE_REPUTATION_BELL_POSITION_COOLDOWNS.put(bellCooldownKey, gameTime + NEGATIVE_REPUTATION_BELL_COOLDOWN_TICKS);
        }
    }

    private static Optional<BlockPos> findNearestBell(ServerLevel level, AbstractVillager villager) {
        BlockPos origin = villager.blockPosition();
        if (villager instanceof Villager villageResident) {
            Optional<BlockPos> meetingBell = knownMeetingPointBell(level, villageResident);
            if (meetingBell.isPresent()) {
                return meetingBell;
            }
            Optional<VillageMembership.VillageArea> villageArea = VillageMembership.resolve(level, villageResident);
            if (villageArea.isPresent()) {
                return findNearestBell(level, villageArea.get().centerBlock(), NEGATIVE_REPUTATION_BELL_SEARCH_RADIUS);
            }
        }
        return findNearestBell(level, origin, NEGATIVE_REPUTATION_BELL_SEARCH_RADIUS);
    }

    private static Optional<BlockPos> findNearestBell(ServerLevel level, BlockPos origin, int radius) {
        BellSearchKey cacheKey = BellSearchKey.of(level, origin, radius);
        long gameTime = level.getGameTime();
        BellSearchResult cached = NEGATIVE_REPUTATION_BELL_CACHE.get(cacheKey);
        if (cached != null && gameTime < cached.expiresGameTime()) {
            return Optional.ofNullable(cached.pos());
        }

        Optional<BlockPos> bellPos = level.getPoiManager()
                .findClosest(
                        poiType -> poiType.is(PoiTypes.MEETING),
                        pos -> level.getBlockState(pos).getBlock() instanceof BellBlock,
                        origin,
                        radius,
                        PoiManager.Occupancy.ANY
                )
                .map(BlockPos::immutable);
        NEGATIVE_REPUTATION_BELL_CACHE.put(cacheKey, new BellSearchResult(bellPos.orElse(null), gameTime + NEGATIVE_REPUTATION_BELL_CACHE_TICKS));
        return bellPos;
    }

    private static Optional<BlockPos> knownMeetingPointBell(ServerLevel level, Villager villager) {
        Optional<BlockPos> meetingPoint = VillageMembership.meetingPoint(level, villager);
        if (meetingPoint.isEmpty()) {
            return Optional.empty();
        }
        return level.getBlockState(meetingPoint.get()).getBlock() instanceof BellBlock
                ? meetingPoint.map(BlockPos::immutable)
                : Optional.empty();
    }

    private static void pruneNegativeReputationBellState(long gameTime) {
        NEGATIVE_REPUTATION_BELL_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        NEGATIVE_REPUTATION_BELL_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() <= gameTime);
        NEGATIVE_REPUTATION_BELL_POSITION_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
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

    private record NearbyPlayerReputation(Player player, double distanceSqr, boolean visible, VillagerReputationLevel reputationLevel) {
    }

    private record BellSearchKey(ResourceKey<Level> dimension, int sectionX, int sectionY, int sectionZ, int radius) {
        private static BellSearchKey of(ServerLevel level, BlockPos origin, int radius) {
            return new BellSearchKey(
                    level.dimension(),
                    net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX()),
                    net.minecraft.core.SectionPos.blockToSectionCoord(origin.getY()),
                    net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ()),
                    radius
            );
        }
    }

    private record BellSearchResult(BlockPos pos, long expiresGameTime) {
    }

    private record BellCooldownKey(ResourceKey<Level> dimension, BlockPos pos) {
        private static BellCooldownKey of(ServerLevel level, BlockPos pos) {
            return new BellCooldownKey(level.dimension(), pos.immutable());
        }
    }
}
