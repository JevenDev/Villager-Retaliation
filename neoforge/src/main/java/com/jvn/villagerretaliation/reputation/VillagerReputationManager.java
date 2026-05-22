package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.ResolvedVillagerNotification;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

public final class VillagerReputationManager {
    private static final long DAY_TICKS = 24000L;
    private static final Map<UUID, Map<ResolvedVillagerNotification, Integer>> PENDING_TIER_MESSAGES = new LinkedHashMap<>();

    private VillagerReputationManager() {
    }

    public static void addDirectReputation(ServerLevel level, AbstractVillager villager, UUID playerId, int amount) {
        addReputation(level, villager, playerId, amount, ReputationEventType.DIRECT_HIT, villager.blockPosition());
    }

    public static void addWitnessedReputation(ServerLevel level, AbstractVillager witness, UUID playerId, int amount, BlockPos eventPos) {
        addReputation(level, witness, playerId, amount, ReputationEventType.WITNESSED_HIT, eventPos);
    }

    public static void addGossipReputation(ServerLevel level, Villager receiver, UUID playerId, int amount, UUID sourceVillagerId) {
        if (sourceVillagerId.equals(receiver.getUUID())) {
            return;
        }
        addReputation(level, receiver, playerId, amount, ReputationEventType.GOSSIP, receiver.blockPosition());
    }

    public static void addTradeReputation(ServerLevel level, AbstractVillager villager, Player player) {
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
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.REPUTATION_CHANGED, villager.blockPosition(), villager, player);
        addVanillaGossip(villager, player.getUUID(), GossipType.TRADING, VillagerRetaliationConfig.TRADE_REPUTATION_GAIN.get());
        handleTierChange(level, villager, player, previousLevel, newLevel);
        VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        syncToTrackingPlayer(level, villager, player.getUUID());
    }

    public static void addDialogueReputation(ServerLevel level, AbstractVillager villager, Player player, int amount) {
        addReputation(level, villager, player.getUUID(), amount, ReputationEventType.DIALOGUE, villager.blockPosition());
    }

    public static void addGiftReputation(ServerLevel level, AbstractVillager villager, Player player, int amount) {
        addReputation(level, villager, player.getUUID(), amount, ReputationEventType.GIFT, villager.blockPosition());
    }

    public static int getReputation(ServerLevel level, AbstractVillager villager, UUID playerId) {
        VillagerReputationSavedData.ReputationEntry entry = VillagerReputationSavedData.get(level).get(villager.getUUID(), playerId);
        return entry == null ? 0 : entry.reputation();
    }

    public static VillagerReputationLevel getReputationLevel(ServerLevel level, AbstractVillager villager, UUID playerId) {
        return VillagerReputationLevel.fromReputation(getReputation(level, villager, playerId));
    }

    public static ReputationSnapshot getReputationSnapshot(ServerLevel level, AbstractVillager villager, UUID playerId) {
        int reputation = getReputation(level, villager, playerId);
        return new ReputationSnapshot(reputation, VillagerReputationLevel.fromReputation(reputation));
    }

    public static boolean isDespised(ServerLevel level, AbstractVillager villager, Player player) {
        return getReputationLevel(level, villager, player.getUUID()) == VillagerReputationLevel.DESPISED;
    }

    public static boolean isFeared(ServerLevel level, AbstractVillager villager, Player player) {
        return getReputationLevel(level, villager, player.getUUID()) == VillagerReputationLevel.FEARED;
    }

    public static boolean isRespected(ServerLevel level, AbstractVillager villager, Player player) {
        VillagerReputationLevel levelForPlayer = getReputationLevel(level, villager, player.getUUID());
        return levelForPlayer == VillagerReputationLevel.RESPECTED
                || levelForPlayer == VillagerReputationLevel.REVERED
                || levelForPlayer == VillagerReputationLevel.ROYALTY;
    }

    public static boolean canGiveHighReputationGift(ServerLevel level, AbstractVillager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return false;
        }

        VillagerReputationSavedData.ReputationEntry entry = VillagerReputationSavedData.get(level).get(villager.getUUID(), player.getUUID());
        if (entry == null) {
            return false;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(entry.reputation());
        if (reputationLevel != VillagerReputationLevel.REVERED && reputationLevel != VillagerReputationLevel.ROYALTY) {
            return false;
        }

        long day = level.getDayTime() / DAY_TICKS;
        return entry.lastGiftDay() != day;
    }

    public static void markHighReputationGiftGiven(ServerLevel level, AbstractVillager villager, Player player) {
        VillagerReputationSavedData data = VillagerReputationSavedData.get(level);
        VillagerReputationSavedData.ReputationEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        long day = level.getDayTime() / DAY_TICKS;
        entry.setLastGiftDay(day);
        entry.setLastInteractionGameTime(level.getGameTime());
        entry.setLastKnownVillagerPosition(villager.blockPosition());
        data.setDirty();
    }

    public static boolean setReputationForDebug(ServerLevel level, AbstractVillager villager, UUID playerId, int reputation) {
        return setReputation(level, villager, playerId, reputation);
    }

    public static boolean setReputation(ServerLevel level, AbstractVillager villager, UUID playerId, int reputation) {
        VillagerReputationSavedData data = VillagerReputationSavedData.get(level);
        VillagerReputationSavedData.ReputationEntry entry = data.getOrCreate(villager.getUUID(), playerId);
        int previousReputation = entry.reputation();
        if (previousReputation == reputation) {
            syncToTrackingPlayer(level, villager, playerId);
            return false;
        }

        VillagerReputationLevel previousLevel = VillagerReputationLevel.fromReputation(previousReputation);
        entry.setReputation(reputation);
        VillagerReputationLevel newLevel = VillagerReputationLevel.fromReputation(reputation);
        entry.setLastInteractionGameTime(level.getGameTime());
        entry.setLastKnownVillagerPosition(villager.blockPosition());
        data.setDirty();
        Player player = level.getPlayerByUUID(playerId);
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.REPUTATION_CHANGED, villager.blockPosition(), villager, player);
        if (player != null) {
            handleTierChange(level, villager, player, previousLevel, newLevel);
            VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        }
        syncToTrackingPlayer(level, villager, playerId);
        return true;
    }

    public static boolean hasStoredReputation(ServerLevel level, UUID villagerId, UUID playerId) {
        return VillagerReputationSavedData.get(level).hasEntry(villagerId, playerId);
    }

    public static boolean transferVillagerIdentity(ServerLevel level, UUID sourceVillagerId, UUID targetVillagerId) {
        return VillagerReputationSavedData.get(level).transferVillagerEntries(sourceVillagerId, targetVillagerId);
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

    private static void addReputation(ServerLevel level, AbstractVillager villager, UUID playerId, int amount, ReputationEventType eventType, BlockPos eventPos) {
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
        } else if (amount < 0 && eventType != ReputationEventType.GOSSIP && eventType != ReputationEventType.DIALOGUE) {
            entry.incrementWitnessedCrimes();
            GossipType gossipType = amount <= VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get()
                    ? GossipType.MAJOR_NEGATIVE
                    : GossipType.MINOR_NEGATIVE;
            addVanillaGossip(villager, playerId, gossipType, Math.max(1, Math.abs(amount) / 10));
        } else if (amount > 0 && eventType != ReputationEventType.GOSSIP) {
            addVanillaGossip(villager, playerId, GossipType.MINOR_POSITIVE, Math.max(1, amount / 5));
        }
        data.setDirty();
        Player player = level.getPlayerByUUID(playerId);
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.REPUTATION_CHANGED, eventPos, villager, player);
        if (player != null) {
            handleTierChange(level, villager, player, previousLevel, newLevel);
            VillagerReputationTradePricing.refreshPricesForPlayer(level, villager, player);
        }
        syncToTrackingPlayer(level, villager, playerId);
    }

    private static void handleTierChange(ServerLevel level, AbstractVillager villager, Player player, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        if (previousLevel == newLevel) {
            return;
        }

        notifyTierChange(player, resolveTierChangeNotification(level, villager, player, previousLevel, newLevel));
        spawnTierChangeParticles(level, villager, previousLevel, newLevel);
        if (player instanceof ServerPlayer serverPlayer) {
            VillagerReputationAdvancements.onReputationTierChanged(level, villager, serverPlayer, previousLevel, newLevel);
        }
    }

    private static void notifyTierChange(Player player, ResolvedVillagerNotification notification) {
        PENDING_TIER_MESSAGES.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>())
                .merge(notification, 1, Integer::sum);
    }

    private static ResolvedVillagerNotification resolveTierChangeNotification(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            VillagerReputationLevel previousLevel,
            VillagerReputationLevel newLevel) {
        String direction = newLevel.isMoreTrustedThan(previousLevel) ? "improved" : "worsened";
        String trigger = "reputation.tier." + newLevel.name().toLowerCase(java.util.Locale.ROOT) + "." + direction;
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        return VillagerNotifications.resolve(
                level,
                villager,
                player,
                trigger,
                VillagerNotifications.replacements(
                        "villager", name,
                        "villager_possessive", toPossessive(name),
                        "villager_kind", villager instanceof WanderingTrader ? "wandering trader" : "villager",
                        "previous_level", previousLevel.name().toLowerCase(java.util.Locale.ROOT),
                        "new_level", newLevel.name().toLowerCase(java.util.Locale.ROOT)
                ),
                resolveTierChangeMessage(villager, previousLevel, newLevel),
                VillagerReputationNoticeKind.DEFAULT
        );
    }

    private static String resolveTierChangeMessage(AbstractVillager villager, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        String message = newLevel.transitionMessageFrom(previousLevel);
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        if (!name.isBlank() && (villager.hasCustomName() || !VillagerPresetNameRegistry.resolvePresetName(villager).isBlank())) {
            String possessiveName = toPossessive(name);
            return message
                    .replace("A villager's", possessiveName)
                    .replace("A villager", name)
                    .replace("a villager's", possessiveName)
                    .replace("a villager", name);
        }

        if (!(villager instanceof WanderingTrader)) {
            return message;
        }

        return message
                .replace("A villager's", "A wandering trader's")
                .replace("A villager", "A wandering trader")
                .replace("a villager's", "a wandering trader's")
                .replace("a villager", "a wandering trader");
    }

    private static String toPossessive(String name) {
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
    }

    public static void flushTierChangeMessages(MinecraftServer server) {
        if (PENDING_TIER_MESSAGES.isEmpty()) {
            return;
        }

        Map<UUID, Map<ResolvedVillagerNotification, Integer>> pendingMessages = new LinkedHashMap<>(PENDING_TIER_MESSAGES);
        PENDING_TIER_MESSAGES.clear();
        for (Map.Entry<UUID, Map<ResolvedVillagerNotification, Integer>> playerEntry : pendingMessages.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) {
                continue;
            }
            for (Map.Entry<ResolvedVillagerNotification, Integer> messageEntry : playerEntry.getValue().entrySet()) {
                ResolvedVillagerNotification notification = messageEntry.getKey();
                if (messageEntry.getValue() > 1) {
                    notification = new ResolvedVillagerNotification(
                            notification.text() + " x" + messageEntry.getValue(),
                            notification.textColor(),
                            notification.chatColor(),
                            notification.noticeKind(),
                            notification.worldTextKind()
                    );
                }
                VillagerReputationNetworking.sendNotice(player, notification);
            }
        }
    }

    private static void spawnTierChangeParticles(ServerLevel level, AbstractVillager villager, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
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

    private static void addVanillaGossip(AbstractVillager villager, UUID playerId, GossipType type, int value) {
        if (VillagerRetaliationConfig.ENABLE_VANILLA_GOSSIP_INTEGRATION.get()
                && value > 0
                && villager instanceof Villager villageResident) {
            villageResident.getGossips().add(playerId, type, value);
        }
    }

    public static void syncToTrackingPlayer(ServerLevel level, AbstractVillager villager, UUID playerId) {
        double witnessRadius = VillagerRetaliationConfig.WITNESS_RADIUS.get();
        if (level.getPlayerByUUID(playerId) instanceof ServerPlayer serverPlayer
                && serverPlayer.distanceToSqr(villager) <= witnessRadius * witnessRadius) {
            int reputation = getReputation(level, villager, playerId);
            VillagerReputationNetworking.sendReputation(serverPlayer, villager, reputation);
        }
    }

    public record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }
}
