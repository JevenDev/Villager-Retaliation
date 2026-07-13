package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.notification.ResolvedVillagerNotification;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final long SYNC_CACHE_TTL_TICKS = 20L * 60L * 5L;
    private static final long SYNC_CACHE_PRUNE_INTERVAL_TICKS = 20L * 10L;
    private static final Map<UUID, List<PendingTierMessage>> PENDING_TIER_MESSAGES = new LinkedHashMap<>();
    private static final Map<SyncKey, SyncedReputation> LAST_SYNCED_REPUTATION = new LinkedHashMap<>();
    private static long nextSyncCachePruneGameTime;

    private VillagerReputationManager() {
    }

    public static void addDirectReputation(ServerLevel level, AbstractVillager villager, UUID playerId, int amount) {
        addReputation(level, villager, playerId, amount, ReputationEventType.DIRECT_HIT, villager.blockPosition());
    }

    public static void addWitnessedReputation(ServerLevel level, AbstractVillager witness, UUID playerId, int amount, BlockPos eventPos) {
        addReputation(level, witness, playerId, amount, ReputationEventType.WITNESSED_HIT, eventPos);
    }

    public static void addContainerBreakReputation(ServerLevel level, AbstractVillager witness, UUID playerId, int amount, BlockPos eventPos) {
        addReputation(level, witness, playerId, amount, ReputationEventType.CONTAINER_BREAK, eventPos);
    }

    public static void addGossipReputation(ServerLevel level, Villager receiver, UUID playerId, int amount, UUID sourceVillagerId) {
        if (sourceVillagerId.equals(receiver.getUUID())) {
            return;
        }
        addReputation(level, receiver, playerId, amount, ReputationEventType.GOSSIP, receiver.blockPosition());
    }

    public static void addUnlawfulOrderReputation(ServerLevel level, Villager villager, UUID playerId, int amount) {
        addReputation(level, villager, playerId, amount, ReputationEventType.UNLAWFUL_ORDER, villager.blockPosition());
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
        notifyQuestReputationChanged(level, villager, player, entry.reputation());
        syncToTrackingPlayer(level, villager, player.getUUID());
    }

    public static void addDialogueReputation(ServerLevel level, AbstractVillager villager, Player player, int amount) {
        addReputation(level, villager, player.getUUID(), amount, ReputationEventType.DIALOGUE, villager.blockPosition());
    }

    public static void addGiftReputation(ServerLevel level, AbstractVillager villager, Player player, int amount) {
        addReputation(level, villager, player.getUUID(), amount, ReputationEventType.GIFT, villager.blockPosition());
    }

    public static void addTradePaymentReturnReputation(ServerLevel level, AbstractVillager villager, Player player, int amount) {
        addReputation(level, villager, player.getUUID(), amount, ReputationEventType.TRADE, villager.blockPosition());
    }

    public static void addHiredWorkReputation(ServerLevel level, AbstractVillager villager, UUID playerId, int amount) {
        addReputation(level, villager, playerId, amount, ReputationEventType.DIALOGUE, villager.blockPosition());
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

    public static boolean isRoyalty(ServerLevel level, AbstractVillager villager, Player player) {
        return getReputationLevel(level, villager, player.getUUID()) == VillagerReputationLevel.ROYALTY;
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
            notifyQuestReputationChanged(level, villager, player, entry.reputation());
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

    public static boolean inheritReputationFromParents(ServerLevel level, Villager child, Villager parentA, Villager parentB) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_PARENT_REPUTATION_INHERITANCE.get()
                || child == null
                || parentA == null
                || parentB == null) {
            return false;
        }
        return VillagerReputationSavedData.get(level).inheritParentEntries(
                child.getUUID(),
                parentA.getUUID(),
                parentB.getUUID(),
                level.getGameTime(),
                child.blockPosition()
        );
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
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || amount == 0
                || shouldIgnorePartyCrimeReputation(level, villager, playerId, amount, eventType)) {
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
            notifyQuestReputationChanged(level, villager, player, entry.reputation());
        }
        syncToTrackingPlayer(level, villager, playerId);
    }

    private static boolean shouldIgnorePartyCrimeReputation(
            ServerLevel level,
            AbstractVillager villager,
            UUID playerId,
            int amount,
            ReputationEventType eventType) {
        if (amount >= 0
                || eventType != ReputationEventType.WITNESSED_HIT && eventType != ReputationEventType.GOSSIP) {
            return false;
        }
        return false;
    }

    private static void notifyQuestReputationChanged(
            ServerLevel level,
            AbstractVillager villager,
            Player player,
            int reputation) {
        if (player instanceof ServerPlayer serverPlayer) {
            VillagerQuestService.onReputationChanged(level, serverPlayer, villager, reputation);
        }
    }

    private static void handleTierChange(ServerLevel level, AbstractVillager villager, Player player, VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel) {
        if (previousLevel == newLevel) {
            return;
        }

        notifyTierChange(player, new PendingTierMessage(
                resolveTierChangeNotification(level, villager, player, previousLevel, newLevel),
                previousLevel,
                newLevel
        ));
        spawnTierChangeParticles(level, villager, previousLevel, newLevel);
        if (player instanceof ServerPlayer serverPlayer) {
            VillagerReputationAdvancements.onReputationTierChanged(level, villager, serverPlayer, previousLevel, newLevel);
        }
    }

    private static void notifyTierChange(Player player, PendingTierMessage message) {
        PENDING_TIER_MESSAGES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>())
                .add(message);
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

        Map<UUID, List<PendingTierMessage>> pendingMessages = new LinkedHashMap<>(PENDING_TIER_MESSAGES);
        PENDING_TIER_MESSAGES.clear();
        for (Map.Entry<UUID, List<PendingTierMessage>> playerEntry : pendingMessages.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) {
                continue;
            }
            if (VillagerRetaliationConfig.COLLAPSE_REPUTATION_CHANGE_NOTIFICATIONS.get()) {
                sendCollapsedTierChangeMessages(player, playerEntry.getValue());
            } else {
                sendUncollapsedTierChangeMessages(player, playerEntry.getValue());
            }
        }
    }

    private static void sendUncollapsedTierChangeMessages(ServerPlayer player, List<PendingTierMessage> messages) {
        for (PendingTierMessage message : messages) {
            VillagerReputationNetworking.sendNotice(player, message.notification());
        }
    }

    private static void sendCollapsedTierChangeMessages(ServerPlayer player, List<PendingTierMessage> messages) {
        Map<TierMessageGroupKey, CollapsedTierMessage> collapsedMessages = new LinkedHashMap<>();
        for (PendingTierMessage message : messages) {
            collapsedMessages.computeIfAbsent(message.groupKey(), ignored -> new CollapsedTierMessage(message))
                    .increment();
        }
        for (CollapsedTierMessage message : collapsedMessages.values()) {
            VillagerReputationNetworking.sendNotice(player, message.notification());
        }
    }

    private static String collapsedTierChangeMessage(VillagerReputationLevel previousLevel, VillagerReputationLevel newLevel, int count) {
        boolean improved = newLevel.isMoreTrustedThan(previousLevel);
        return count + "x " + (switch (newLevel) {
            case ROYALTY -> improved
                    ? "Villagers now treat you like royalty."
                    : "Villagers no longer see you as royalty.";
            case REVERED -> improved
                    ? "Villagers now revere you."
                    : "Villagers' reverence for you has faded.";
            case RESPECTED -> improved
                    ? "Villagers deeply respect you."
                    : "Villagers' deep respect for you is slipping away.";
            case TRUSTED -> improved
                    ? "Villagers now trust you."
                    : "Villagers' trust in you has weakened.";
            case NEUTRAL -> "Villagers seem to feel neutral toward you again.";
            case SUSPICIOUS -> improved
                    ? "Villagers seem less suspicious of you."
                    : "Villagers are becoming suspicious of you.";
            case HOSTILE -> improved
                    ? "Villagers no longer see you as completely unforgivable."
                    : "Villagers are becoming hostile toward you.";
            case DESPISED -> improved
                    ? "Villagers' hatred for you has softened."
                    : "Villagers come to despise you.";
            case FEARED -> improved
                    ? "Villagers no longer fear you completely."
                    : "Villagers now fear you.";
        });
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
            SyncKey key = new SyncKey(playerId, villager.getUUID());
            SyncedReputation previousSync = LAST_SYNCED_REPUTATION.get(key);
            if (previousSync != null
                    && previousSync.reputation() == reputation
                    && previousSync.entityId() == villager.getId()) {
                LAST_SYNCED_REPUTATION.put(key, previousSync.withGameTime(level.getGameTime()));
                return;
            }
            LAST_SYNCED_REPUTATION.put(key, new SyncedReputation(reputation, villager.getId(), level.getGameTime()));
            VillagerReputationNetworking.sendReputation(serverPlayer, villager, reputation);
        }
    }

    public static void pruneSyncState(long gameTime) {
        if (gameTime < nextSyncCachePruneGameTime) {
            return;
        }
        nextSyncCachePruneGameTime = gameTime + SYNC_CACHE_PRUNE_INTERVAL_TICKS;
        LAST_SYNCED_REPUTATION.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > SYNC_CACHE_TTL_TICKS);
    }

    public static void clearRuntimeState() {
        PENDING_TIER_MESSAGES.clear();
        LAST_SYNCED_REPUTATION.clear();
        nextSyncCachePruneGameTime = 0L;
    }

    public record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }

    private record SyncKey(UUID playerId, UUID villagerId) {
    }

    private record SyncedReputation(int reputation, int entityId, long gameTime) {
        SyncedReputation withGameTime(long gameTime) {
            return new SyncedReputation(this.reputation, this.entityId, gameTime);
        }
    }

    private record PendingTierMessage(
            ResolvedVillagerNotification notification,
            VillagerReputationLevel previousLevel,
            VillagerReputationLevel newLevel) {
        private TierMessageGroupKey groupKey() {
            return new TierMessageGroupKey(
                    this.newLevel,
                    this.newLevel.isMoreTrustedThan(this.previousLevel),
                    this.notification.textColor(),
                    this.notification.chatColor(),
                    this.notification.noticeKind(),
                    this.notification.worldTextKind()
            );
        }

        private ResolvedVillagerNotification collapsedNotification(int count) {
            if (count <= 1) {
                return this.notification;
            }
            return new ResolvedVillagerNotification(
                    collapsedTierChangeMessage(this.previousLevel, this.newLevel, count),
                    this.notification.textColor(),
                    this.notification.chatColor(),
                    this.notification.noticeKind(),
                    this.notification.worldTextKind()
            );
        }
    }

    private record TierMessageGroupKey(
            VillagerReputationLevel newLevel,
            boolean improved,
            int textColor,
            int chatColor,
            VillagerReputationNoticeKind noticeKind,
            VillagerWorldTextIndicatorKind worldTextKind) {
    }

    private static final class CollapsedTierMessage {
        private final PendingTierMessage representative;
        private int count;

        private CollapsedTierMessage(PendingTierMessage representative) {
            this.representative = representative;
        }

        private void increment() {
            this.count++;
        }

        private ResolvedVillagerNotification notification() {
            return this.representative.collapsedNotification(this.count);
        }
    }
}
