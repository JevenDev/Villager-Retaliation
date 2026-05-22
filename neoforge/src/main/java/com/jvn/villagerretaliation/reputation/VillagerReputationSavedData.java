package com.jvn.villagerretaliation.reputation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerReputationSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_reputations";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_REPUTATION = "Reputation";
    private static final String TAG_LAST_INTERACTION = "LastInteractionGameTime";
    private static final String TAG_DIRECT_HITS = "DirectHits";
    private static final String TAG_WITNESSED_CRIMES = "WitnessedCrimes";
    private static final String TAG_LAST_TRADE_DAY = "LastTradeDay";
    private static final String TAG_TRADES_TODAY = "TradesToday";
    private static final String TAG_LAST_GIFT_DAY = "LastGiftDay";
    private static final String TAG_LAST_POS = "LastKnownVillagerPosition";

    private final Map<UUID, Map<UUID, ReputationEntry>> entries = new HashMap<>();

    public static VillagerReputationSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerReputationSavedData::new, VillagerReputationSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerReputationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerReputationSavedData data = new VillagerReputationSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_VILLAGER)
                    || !entryTag.hasUUID(TAG_PLAYER)) {
                continue;
            }

            UUID villagerId = entryTag.getUUID(TAG_VILLAGER);
            UUID playerId = entryTag.getUUID(TAG_PLAYER);
            ReputationEntry entry = new ReputationEntry();
            entry.reputation = entryTag.getInt(TAG_REPUTATION);
            entry.lastInteractionGameTime = entryTag.getLong(TAG_LAST_INTERACTION);
            entry.directHits = entryTag.getInt(TAG_DIRECT_HITS);
            entry.witnessedCrimes = entryTag.getInt(TAG_WITNESSED_CRIMES);
            entry.lastTradeDay = entryTag.getLong(TAG_LAST_TRADE_DAY);
            entry.tradesToday = entryTag.getInt(TAG_TRADES_TODAY);
            entry.lastGiftDay = entryTag.getLong(TAG_LAST_GIFT_DAY);
            if (entryTag.contains(TAG_LAST_POS, Tag.TAG_COMPOUND)) {
                CompoundTag posTag = entryTag.getCompound(TAG_LAST_POS);
                entry.lastKnownVillagerPosition = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
            }
            data.entries.computeIfAbsent(villagerId, ignored -> new HashMap<>()).put(playerId, entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<UUID, Map<UUID, ReputationEntry>> villagerEntry : this.entries.entrySet()) {
            UUID villagerId = villagerEntry.getKey();
            for (Map.Entry<UUID, ReputationEntry> playerEntry : villagerEntry.getValue().entrySet()) {
                ReputationEntry entry = playerEntry.getValue();
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID(TAG_VILLAGER, villagerId);
                entryTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                entryTag.putInt(TAG_REPUTATION, entry.reputation);
                entryTag.putLong(TAG_LAST_INTERACTION, entry.lastInteractionGameTime);
                entryTag.putInt(TAG_DIRECT_HITS, entry.directHits);
                entryTag.putInt(TAG_WITNESSED_CRIMES, entry.witnessedCrimes);
                entryTag.putLong(TAG_LAST_TRADE_DAY, entry.lastTradeDay);
                entryTag.putInt(TAG_TRADES_TODAY, entry.tradesToday);
                entryTag.putLong(TAG_LAST_GIFT_DAY, entry.lastGiftDay);
                if (entry.lastKnownVillagerPosition != null) {
                    CompoundTag posTag = new CompoundTag();
                    posTag.putInt("X", entry.lastKnownVillagerPosition.getX());
                    posTag.putInt("Y", entry.lastKnownVillagerPosition.getY());
                    posTag.putInt("Z", entry.lastKnownVillagerPosition.getZ());
                    entryTag.put(TAG_LAST_POS, posTag);
                }
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public ReputationEntry getOrCreate(UUID villagerId, UUID playerId) {
        return this.entries.computeIfAbsent(villagerId, ignored -> new HashMap<>())
                .computeIfAbsent(playerId, ignored -> new ReputationEntry());
    }

    public ReputationEntry get(UUID villagerId, UUID playerId) {
        Map<UUID, ReputationEntry> playerEntries = this.entries.get(villagerId);
        return playerEntries == null ? null : playerEntries.get(playerId);
    }

    public boolean hasEntry(UUID villagerId, UUID playerId) {
        Map<UUID, ReputationEntry> playerEntries = this.entries.get(villagerId);
        return playerEntries != null && playerEntries.containsKey(playerId);
    }

    public boolean transferVillagerEntries(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId.equals(targetVillagerId)) {
            return false;
        }

        Map<UUID, ReputationEntry> sourceEntries = this.entries.remove(sourceVillagerId);
        if (sourceEntries == null || sourceEntries.isEmpty()) {
            return false;
        }

        Map<UUID, ReputationEntry> targetEntries = this.entries.computeIfAbsent(targetVillagerId, ignored -> new HashMap<>());
        for (Map.Entry<UUID, ReputationEntry> sourceEntry : sourceEntries.entrySet()) {
            targetEntries.merge(sourceEntry.getKey(), sourceEntry.getValue(), VillagerReputationSavedData::preferMostRecentEntry);
        }

        setDirty();
        return true;
    }

    public boolean inheritParentEntries(UUID childVillagerId, UUID parentAId, UUID parentBId, long gameTime, BlockPos childPos) {
        if (childVillagerId == null || parentAId == null || parentBId == null) {
            return false;
        }

        Map<UUID, ReputationEntry> parentAEntries = this.entries.getOrDefault(parentAId, Map.of());
        Map<UUID, ReputationEntry> parentBEntries = this.entries.getOrDefault(parentBId, Map.of());
        if (parentAEntries.isEmpty() && parentBEntries.isEmpty()) {
            return false;
        }

        Set<UUID> playerIds = new HashSet<>();
        playerIds.addAll(parentAEntries.keySet());
        playerIds.addAll(parentBEntries.keySet());

        boolean changed = false;
        for (UUID playerId : playerIds) {
            Map<UUID, ReputationEntry> childEntries = this.entries.computeIfAbsent(childVillagerId, ignored -> new HashMap<>());
            if (childEntries.containsKey(playerId)) {
                continue;
            }

            ReputationEntry parentAEntry = parentAEntries.get(playerId);
            ReputationEntry parentBEntry = parentBEntries.get(playerId);
            int inherited = inheritedParentReputation(
                    parentAEntry == null ? 0 : parentAEntry.reputation(),
                    parentAEntry != null,
                    parentBEntry == null ? 0 : parentBEntry.reputation(),
                    parentBEntry != null
            );
            if (inherited == 0) {
                continue;
            }

            ReputationEntry childEntry = new ReputationEntry();
            childEntry.setReputation(inherited);
            childEntry.setLastInteractionGameTime(gameTime);
            childEntry.setLastKnownVillagerPosition(childPos);
            childEntries.put(playerId, childEntry);
            changed = true;
        }

        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void pruneOldNeutralEntries(long olderThanGameTime) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Map<UUID, ReputationEntry>>> villagerIterator = this.entries.entrySet().iterator();
        while (villagerIterator.hasNext()) {
            Map<UUID, ReputationEntry> playerEntries = villagerIterator.next().getValue();
            Iterator<Map.Entry<UUID, ReputationEntry>> playerIterator = playerEntries.entrySet().iterator();
            while (playerIterator.hasNext()) {
                ReputationEntry entry = playerIterator.next().getValue();
                if (entry.reputation == 0 && entry.lastInteractionGameTime < olderThanGameTime) {
                    playerIterator.remove();
                    changed = true;
                }
            }
            if (playerEntries.isEmpty()) {
                villagerIterator.remove();
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static ReputationEntry preferMostRecentEntry(ReputationEntry existing, ReputationEntry incoming) {
        return incoming.lastInteractionGameTime >= existing.lastInteractionGameTime ? incoming : existing;
    }

    private static int inheritedParentReputation(int parentA, boolean hasParentA, int parentB, boolean hasParentB) {
        int base;
        if (hasParentA && hasParentB) {
            if (parentA > 0 && parentB > 0) {
                base = Math.max(parentA, parentB);
            } else if (parentA < 0 && parentB < 0) {
                base = Math.min(parentA, parentB);
            } else {
                base = parentA + parentB;
            }
        } else {
            base = hasParentA ? parentA : parentB;
        }
        return Math.round(base * 0.75F);
    }

    public static class ReputationEntry {
        private int reputation;
        private long lastInteractionGameTime;
        private int directHits;
        private int witnessedCrimes;
        private long lastTradeDay = Long.MIN_VALUE;
        private int tradesToday;
        private long lastGiftDay = Long.MIN_VALUE;
        private BlockPos lastKnownVillagerPosition;

        public int reputation() {
            return this.reputation;
        }

        void setReputation(int reputation) {
            this.reputation = reputation;
        }

        void addReputation(int amount) {
            this.reputation += amount;
        }

        void setLastInteractionGameTime(long lastInteractionGameTime) {
            this.lastInteractionGameTime = lastInteractionGameTime;
        }

        void incrementDirectHits() {
            this.directHits++;
        }

        void incrementWitnessedCrimes() {
            this.witnessedCrimes++;
        }

        public long lastTradeDay() {
            return this.lastTradeDay;
        }

        public int tradesToday() {
            return this.tradesToday;
        }

        void resetTrades(long day) {
            this.lastTradeDay = day;
            this.tradesToday = 0;
        }

        void incrementTradesToday() {
            this.tradesToday++;
        }

        public long lastGiftDay() {
            return this.lastGiftDay;
        }

        void setLastGiftDay(long day) {
            this.lastGiftDay = day;
        }

        void setLastKnownVillagerPosition(BlockPos lastKnownVillagerPosition) {
            this.lastKnownVillagerPosition = lastKnownVillagerPosition;
        }
    }
}
