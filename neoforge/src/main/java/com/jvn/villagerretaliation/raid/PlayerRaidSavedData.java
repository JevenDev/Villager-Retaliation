package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-global, restart-safe state for player-created village raids. */
public final class PlayerRaidSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_player_raids";
    private static final int FORMAT_VERSION = 1;

    private final Map<UUID, RaidRecord> raids = new LinkedHashMap<>();
    private final Map<VillageAllegianceId, Long> cooldowns = new LinkedHashMap<>();

    public static PlayerRaidSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerRaidSavedData::new, PlayerRaidSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static PlayerRaidSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerRaidSavedData data = new PlayerRaidSavedData();
        if (tag.getInt("FormatVersion") > FORMAT_VERSION) {
            return data;
        }
        for (Tag raw : tag.getList("Raids", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag raidTag && raidTag.hasUUID("Id") && raidTag.hasUUID("Village")) {
                RaidRecord record = RaidRecord.load(raidTag);
                data.raids.put(record.id(), record);
            }
        }
        for (Tag raw : tag.getList("Cooldowns", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag cooldown && cooldown.hasUUID("Village")) {
                data.cooldowns.put(new VillageAllegianceId(cooldown.getUUID("Village")), cooldown.getLong("Until"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag raidsTag = new ListTag();
        this.raids.values().forEach(raid -> raidsTag.add(raid.save()));
        tag.put("Raids", raidsTag);
        ListTag cooldownTags = new ListTag();
        this.cooldowns.forEach((village, until) -> {
            CompoundTag cooldown = new CompoundTag();
            cooldown.putUUID("Village", village.value());
            cooldown.putLong("Until", until);
            cooldownTags.add(cooldown);
        });
        tag.put("Cooldowns", cooldownTags);
        return tag;
    }

    public Collection<RaidRecord> raids() {
        return List.copyOf(this.raids.values());
    }

    public RaidRecord raid(UUID id) {
        return this.raids.get(id);
    }

    public RaidRecord activeAt(VillageAllegianceId village) {
        return this.raids.values().stream()
                .filter(RaidRecord::running)
                .filter(raid -> raid.villageId().equals(village))
                .findFirst().orElse(null);
    }

    public RaidRecord activeForParticipant(UUID entityId) {
        return this.raids.values().stream()
                .filter(RaidRecord::running)
                .filter(raid -> raid.isParticipant(entityId))
                .findFirst().orElse(null);
    }

    public RaidRecord create(
            VillageAllegianceId villageId,
            ResourceLocation dimension,
            BlockPos center,
            Set<Long> footprint,
            String villageName,
            UUID initiator,
            UUID partyId,
            Collection<UUID> raiderPlayers,
            Collection<UUID> raiderVillagers,
            Collection<UUID> defenders,
            Collection<UUID> defectors,
            long gameTime) {
        RaidRecord record = new RaidRecord(
                UUID.randomUUID(), villageId, dimension, center, footprint, villageName, initiator, partyId,
                raiderPlayers, raiderVillagers, defenders, defectors, Phase.DECLARATION, gameTime,
                -1L, defenders.size(), 0, 0, 0, 0L);
        this.raids.put(record.id(), record);
        setDirty();
        return record;
    }

    public void changed() {
        setDirty();
    }

    public void remove(UUID id) {
        if (this.raids.remove(id) != null) {
            setDirty();
        }
    }

    public long cooldownUntil(VillageAllegianceId village) {
        return this.cooldowns.getOrDefault(village, Long.MIN_VALUE);
    }

    public long cooldownUntil(
            VillageAllegianceRegistrySavedData registry, VillageAllegianceId village) {
        return this.cooldowns.entrySet().stream()
                .filter(entry -> registry.canonical(entry.getKey()).orElse(entry.getKey()).equals(village))
                .mapToLong(Map.Entry::getValue).max().orElse(Long.MIN_VALUE);
    }

    public void setCooldown(VillageAllegianceId village, long until) {
        this.cooldowns.put(village, until);
        setDirty();
    }

    public enum Phase {
        DECLARATION,
        PREPARING,
        ACTIVE,
        RAIDER_VICTORY,
        DEFENDER_VICTORY
    }

    public static final class RaidRecord {
        private final UUID id;
        private final VillageAllegianceId villageId;
        private final ResourceLocation dimension;
        private final BlockPos center;
        private final Set<Long> footprint;
        private final String villageName;
        private final UUID initiator;
        private final UUID partyId;
        private final Set<UUID> raiderPlayers;
        private final Set<UUID> raiderVillagers;
        private final Set<UUID> defenders;
        private final Set<UUID> defectors;
        private Phase phase;
        private long phaseStarted;
        private long absenceStarted;
        private final int initialDefenderCount;
        private int golemBudget;
        private int golemsSpawned;
        private int milestoneMask;
        private long outcomeCleanupAt;

        private RaidRecord(
                UUID id, VillageAllegianceId villageId, ResourceLocation dimension, BlockPos center,
                Collection<Long> footprint, String villageName, UUID initiator, UUID partyId,
                Collection<UUID> raiderPlayers, Collection<UUID> raiderVillagers,
                Collection<UUID> defenders, Collection<UUID> defectors, Phase phase, long phaseStarted,
                long absenceStarted, int initialDefenderCount, int golemBudget, int golemsSpawned,
                int milestoneMask, long outcomeCleanupAt) {
            this.id = id;
            this.villageId = villageId;
            this.dimension = dimension;
            this.center = center.immutable();
            this.footprint = new LinkedHashSet<>(footprint);
            this.villageName = villageName == null ? "Village" : villageName;
            this.initiator = initiator;
            this.partyId = partyId;
            this.raiderPlayers = new LinkedHashSet<>(raiderPlayers);
            this.raiderVillagers = new LinkedHashSet<>(raiderVillagers);
            this.defenders = new LinkedHashSet<>(defenders);
            this.defectors = new LinkedHashSet<>(defectors);
            this.phase = phase;
            this.phaseStarted = phaseStarted;
            this.absenceStarted = absenceStarted;
            this.initialDefenderCount = Math.max(0, initialDefenderCount);
            this.golemBudget = Math.max(0, golemBudget);
            this.golemsSpawned = Math.max(0, golemsSpawned);
            this.milestoneMask = milestoneMask;
            this.outcomeCleanupAt = outcomeCleanupAt;
        }

        public UUID id() { return this.id; }
        public VillageAllegianceId villageId() { return this.villageId; }
        public ResourceLocation dimension() { return this.dimension; }
        public BlockPos center() { return this.center; }
        public Set<Long> footprint() { return Set.copyOf(this.footprint); }
        public String villageName() { return this.villageName; }
        public UUID initiator() { return this.initiator; }
        public UUID partyId() { return this.partyId; }
        public Set<UUID> raiderPlayers() { return Set.copyOf(this.raiderPlayers); }
        public Set<UUID> raiderVillagers() { return Set.copyOf(this.raiderVillagers); }
        public Set<UUID> defenders() { return Set.copyOf(this.defenders); }
        public Set<UUID> defectors() { return Set.copyOf(this.defectors); }
        public Phase phase() { return this.phase; }
        public long phaseStarted() { return this.phaseStarted; }
        public long absenceStarted() { return this.absenceStarted; }
        public int initialDefenderCount() { return this.initialDefenderCount; }
        public int golemBudget() { return this.golemBudget; }
        public int golemsSpawned() { return this.golemsSpawned; }
        public int milestoneMask() { return this.milestoneMask; }
        public long outcomeCleanupAt() { return this.outcomeCleanupAt; }
        public boolean running() { return this.phase == Phase.DECLARATION || this.phase == Phase.PREPARING || this.phase == Phase.ACTIVE; }
        public boolean isParticipant(UUID id) { return this.raiderPlayers.contains(id) || this.raiderVillagers.contains(id) || this.defenders.contains(id); }

        void setPhase(Phase phase, long now) { this.phase = phase; this.phaseStarted = now; }
        void setAbsenceStarted(long time) { this.absenceStarted = time; }
        void setGolemBudget(int budget) { this.golemBudget = Math.max(0, budget); }
        void addSpawnedGolems(int count) { this.golemsSpawned += Math.max(0, count); }
        void markMilestone(int bit) { this.milestoneMask |= bit; }
        void setOutcomeCleanupAt(long time) { this.outcomeCleanupAt = time; }
        boolean removeDefender(UUID id) { return this.defenders.remove(id); }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", this.id);
            tag.putUUID("Village", this.villageId.value());
            tag.putString("Dimension", this.dimension.toString());
            tag.putLong("Center", this.center.asLong());
            tag.putLongArray("Footprint", this.footprint.stream().mapToLong(Long::longValue).toArray());
            tag.putString("VillageName", this.villageName);
            tag.putUUID("Initiator", this.initiator);
            if (this.partyId != null) tag.putUUID("Party", this.partyId);
            putUuids(tag, "RaiderPlayers", this.raiderPlayers);
            putUuids(tag, "RaiderVillagers", this.raiderVillagers);
            putUuids(tag, "Defenders", this.defenders);
            putUuids(tag, "Defectors", this.defectors);
            tag.putString("Phase", this.phase.name());
            tag.putLong("PhaseStarted", this.phaseStarted);
            tag.putLong("AbsenceStarted", this.absenceStarted);
            tag.putInt("InitialDefenders", this.initialDefenderCount);
            tag.putInt("GolemBudget", this.golemBudget);
            tag.putInt("GolemsSpawned", this.golemsSpawned);
            tag.putInt("Milestones", this.milestoneMask);
            tag.putLong("OutcomeCleanupAt", this.outcomeCleanupAt);
            return tag;
        }

        static RaidRecord load(CompoundTag tag) {
            Phase phase;
            try { phase = Phase.valueOf(tag.getString("Phase")); }
            catch (IllegalArgumentException ignored) { phase = Phase.DEFENDER_VICTORY; }
            return new RaidRecord(
                    tag.getUUID("Id"), new VillageAllegianceId(tag.getUUID("Village")),
                    ResourceLocation.parse(tag.getString("Dimension")), BlockPos.of(tag.getLong("Center")),
                    longs(tag.getLongArray("Footprint")), tag.getString("VillageName"), tag.getUUID("Initiator"),
                    tag.hasUUID("Party") ? tag.getUUID("Party") : null,
                    getUuids(tag, "RaiderPlayers"), getUuids(tag, "RaiderVillagers"),
                    getUuids(tag, "Defenders"), getUuids(tag, "Defectors"), phase,
                    tag.getLong("PhaseStarted"), tag.getLong("AbsenceStarted"), tag.getInt("InitialDefenders"),
                    tag.getInt("GolemBudget"), tag.getInt("GolemsSpawned"), tag.getInt("Milestones"),
                    tag.getLong("OutcomeCleanupAt"));
        }

        private static void putUuids(CompoundTag parent, String key, Collection<UUID> ids) {
            ListTag list = new ListTag();
            ids.forEach(id -> { CompoundTag item = new CompoundTag(); item.putUUID("Id", id); list.add(item); });
            parent.put(key, list);
        }

        private static List<UUID> getUuids(CompoundTag parent, String key) {
            List<UUID> ids = new ArrayList<>();
            for (Tag raw : parent.getList(key, Tag.TAG_COMPOUND)) {
                if (raw instanceof CompoundTag item && item.hasUUID("Id")) ids.add(item.getUUID("Id"));
            }
            return ids;
        }

        private static List<Long> longs(long[] values) {
            List<Long> result = new ArrayList<>(values.length);
            for (long value : values) result.add(value);
            return result;
        }
    }
}
