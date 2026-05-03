package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class VillagerReputationManager {
    private static final long DAY_TICKS = 24000L;
    private static final Map<UUID, Map<String, Integer>> PENDING_TIER_MESSAGES = new LinkedHashMap<>();

    private VillagerReputationManager() {
    }

    public static void addDirectReputation(ServerLevel level, Villager villager, UUID playerId, int amount) {
        addReputation(level, villager, playerId, amount, ReputationEventType.DIRECT_HIT, villager.blockPosition());
    }

    public static void addWitnessedReputation(ServerLevel level, Villager witness, UUID playerId, int amount, BlockPos eventPos) {
        addReputation(level, witness, playerId, amount, ReputationEventType.WITNESSED_HIT, eventPos);
    }

    public static void addGossipReputation(ServerLevel level, Villager receiver, UUID playerId, int amount, UUID sourceVillagerId) {
        if (sourceVillagerId.equals(receiver.getUUID())) {
            return;
        }
        addReputation(level, receiver, playerId, amount, ReputationEventType.GOSSIP, receiver.blockPosition());
    }

    public static void addTradeReputation(ServerLevel level, Villager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return;
        }

        VillagerReputationSavedData data = VillagerReputationSavedData.get(level);
        VillagerReputationSavedData.ReputationEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        long day = level.getDayTime() / DAY_TICKS;
        if (entry.lastTradeDay() != day) {
            entry.resetTrades(day);
        }
        if (entry.tradesToday() >= VillagerRetaliationConfig.MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY.get()) {
            return;
        }
        VillagerReputationLevel previousLevel = VillagerReputationLevel.fromReputation(entry.reputation());
        entry.incrementTradesToday();
        entry.addReputation(VillagerRetaliationConfig.TRADE_REPUTATION_GAIN.get());
        VillagerReputationLevel newLevel = VillagerReputationLevel.fromReputation(entry.reputation());
        entry.setLastInteractionGameTime(level.getGameTime());
        entry.setLastKnownVillagerPosition(villager.blockPosition());
        data.setDirty();
        addVanillaGossip(villager, player.getUUID(), GossipType.TRADING, VillagerRetaliationConfig.TRADE_REPUTATION_GAIN.get());
        handleTierChange(level, villager, player, previousLevel, newLevel);
        VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        syncToTrackingPlayer(level, villager, player.getUUID());
    }

    public static int getReputation(ServerLevel level, Villager villager, UUID playerId) {
        VillagerReputationSavedData.ReputationEntry entry = VillagerReputationSavedData.get(level).get(villager.getUUID(), playerId);
        return entry == null ? 0 : entry.reputation();
    }

    public static VillagerReputationLevel getReputationLevel(ServerLevel level, Villager villager, UUID playerId) {
        return VillagerReputationLevel.fromReputation(getReputation(level, villager, playerId));
    }

    public static boolean isDespised(ServerLevel level, Villager villager, Player player) {
        return getReputationLevel(level, villager, player.getUUID()) == VillagerReputationLevel.DESPISED;
    }

    public static boolean isFeared(ServerLevel level, Villager villager, Player player) {
        return getReputationLevel(level, villager, player.getUUID()) == VillagerReputationLevel.FEARED;
    }

    public static boolean isRespected(ServerLevel level, Villager villager, Player player) {
        VillagerReputationLevel levelForPlayer = getReputationLevel(level, villager, player.getUUID());
        return levelForPlayer == VillagerReputationLevel.RESPECTED
                || levelForPlayer == VillagerReputationLevel.REVERED
                || levelForPlayer == VillagerReputationLevel.ROYALTY;
    }

    public static void pruneOldEntries(ServerLevel level) {
        if (!VillagerRetaliationConfig.REPUTATION_DECAY_ENABLED.get()) {
            return;
        }
        int days = VillagerRetaliationConfig.PRUNE_NEUTRAL_ENTRIES_AFTER_DAYS.get();
        if (days <= 0) {
            return;
        }
        VillagerReputationSavedData.get(level).pruneOldNeutralEntries(level.getGameTime() - days * DAY_TICKS);
    }

    private static void addReputation(ServerLevel level, Villager villager, UUID playerId, int amount, ReputationEventType eventType, BlockPos eventPos) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get() || amount == 0) {
            return;
        }

        VillagerReputationSavedData data = VillagerReputationSavedData.get(level);
        VillagerReputationSavedData.ReputationEntry entry = data.getOrCreate(villager.getUUID(), playerId);
        VillagerReputationLevel previousLevel = VillagerReputationLevel.fromReputation(entry.reputation());
        entry.addReputation(amount);
        VillagerReputationLevel newLevel = VillagerReputationLevel.fromReputation(entry.reputation());
        entry.setLastInteractionGameTime(level.getGameTime());
        entry.setLastKnownVillagerPosition(villager.blockPosition());
        if (eventType == ReputationEventType.DIRECT_HIT) {
            entry.incrementDirectHits();
            addVanillaGossip(villager, playerId, GossipType.MINOR_NEGATIVE, Math.max(1, Math.abs(amount) / 5));
        } else if (amount < 0 && eventType != ReputationEventType.GOSSIP) {
            entry.incrementWitnessedCrimes();
            GossipType gossipType = amount <= VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get()
                    ? GossipType.MAJOR_NEGATIVE
                    : GossipType.MINOR_NEGATIVE;
            addVanillaGossip(villager, playerId, gossipType, Math.max(1, Math.abs(amount) / 10));
        } else if (amount > 0 && eventType != ReputationEventType.GOSSIP) {
            addVanillaGossip(villager, playerId, GossipType.MINOR_POSITIVE, Math.max(1, amount / 5));
        }
        data.setDirty();
        if (level.getPlayerByUUID(playerId) instanceof Player player) {
            handleTierChange(level, villager, player, previousLevel, newLevel);
            VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        }
        syncToTrackingPlayer(level, villager, playerId);
    }

    private static void handleTierChange(ServerLevel level, Villager villager, Player player, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        if (previousLevel == newLevel) {
            return;
        }

        notifyTierChange(player, previousLevel, newLevel);
        spawnTierChangeParticles(level, villager, previousLevel, newLevel);
    }

    private static void notifyTierChange(Player player, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        if (previousLevel == newLevel) {
            return;
        }

        PENDING_TIER_MESSAGES.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>())
                .merge(newLevel.transitionMessageFrom(previousLevel), 1, Integer::sum);
    }

    public static void flushTierChangeMessages(MinecraftServer server) {
        if (PENDING_TIER_MESSAGES.isEmpty()) {
            return;
        }

        Map<UUID, Map<String, Integer>> pendingMessages = new LinkedHashMap<>(PENDING_TIER_MESSAGES);
        PENDING_TIER_MESSAGES.clear();
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : pendingMessages.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) {
                continue;
            }
            for (Map.Entry<String, Integer> messageEntry : playerEntry.getValue().entrySet()) {
                String message = messageEntry.getValue() > 1
                        ? messageEntry.getKey() + " x" + messageEntry.getValue()
                        : messageEntry.getKey();
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    private static void spawnTierChangeParticles(ServerLevel level, Villager villager, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        double x = villager.getX();
        double y = villager.getY() + villager.getBbHeight() + 0.25D;
        double z = villager.getZ();
        if (newLevel.isMoreTrustedThan(previousLevel)) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 10, 0.35D, 0.25D, 0.35D, 0.02D);
        } else {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z, 6, 0.3D, 0.2D, 0.3D, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, x, y - 0.1D, z, 6, 0.25D, 0.15D, 0.25D, 0.01D);
        }
    }

    private static void addVanillaGossip(Villager villager, UUID playerId, GossipType type, int value) {
        if (VillagerRetaliationConfig.ENABLE_VANILLA_GOSSIP_INTEGRATION.get() && value > 0) {
            villager.getGossips().add(playerId, type, value);
        }
    }

    public static void syncToTrackingPlayer(ServerLevel level, Villager villager, UUID playerId) {
        if (!VillagerRetaliationConfig.SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY.get()) {
            return;
        }
        if (level.getPlayerByUUID(playerId) instanceof ServerPlayer serverPlayer
                && serverPlayer.distanceToSqr(villager) <= VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE.get()
                * VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE.get()) {
            int reputation = getReputation(level, villager, playerId);
            VillagerReputationNetworking.sendReputation(serverPlayer, villager, reputation);
        }
    }
}
