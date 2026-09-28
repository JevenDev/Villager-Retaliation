package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class SettlementSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_settlements";
    private static final int FORMAT_VERSION = 55;
    final SettlementSimulationService simulation = new SettlementSimulationService();
    private final Map<VillageAllegianceId, SettlementRecord> settlements = new LinkedHashMap<>();

    private final Map<ResourceLocation, Map<Long, VillageAllegianceId>> ownership = new java.util.HashMap<>();

    public static SettlementSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementSavedData::new, SettlementSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public Optional<SettlementRecord> get(VillageAllegianceId id) {
        return Optional.ofNullable(this.settlements.get(id));
    }

    public List<SettlementRecord> settlements() {
        return List.copyOf(this.settlements.values());
    }

    public Optional<SettlementRecord> at(ResourceLocation dimension, BlockPos position) {
        var owner = this.ownership.getOrDefault(dimension, Map.of()).get(new net.minecraft.world.level.ChunkPos(position).toLong());
        return owner == null ? Optional.empty() : get(owner);
    }

    public List<SettlementRecord> accessibleTo(UUID player) {
        return this.settlements.values().stream()
                .filter(town -> town.permits(player, SettlementPermission.VIEW))
                .toList();
    }

    public boolean canEstablish(ResourceLocation dimension, BlockPos center) {
        if (dimension == null || center == null) return false;
        var chunk = new net.minecraft.world.level.ChunkPos(center);
        for (int x = chunk.x - 1; x <= chunk.x + 1; x++) {
            for (int z = chunk.z - 1; z <= chunk.z + 1; z++) {
                if (!SettlementTerritory.validChunk(x, z) || owner(dimension, x, z).isPresent()) return false;
            }
        }
        return true;
    }

    public Optional<SettlementRecord> establish(VillageAllegianceRegistrySavedData registry,
                                                VillageAllegianceId villageId, UUID leader,
                                                BlockPos center, long gameTime) {
        var village = registry.canonicalRecord(villageId).orElse(null);
        if (village == null || village.archived() || village.originDimension() == null || leader == null
                || center == null || this.settlements.containsKey(village.id())
                || registry.isManaged(village.id()) || village.footprintSections().isEmpty() && !canEstablish(village.originDimension(), center)) {
            return Optional.empty();
        }
        SettlementRecord town = new SettlementRecord(village.id(), village.originDimension(),
                center, leader, gameTime, this::setDirty);
        if (!village.footprintSections().isEmpty()) {
            var desired = SettlementTerritoryMigration.desired(town, village.footprintSections());
            var owned = SettlementTerritoryMigration.connect(desired, occupied(registry, town), new net.minecraft.world.level.ChunkPos(center).toLong());
            town.migrateTerritory(owned, owned.isEmpty() ? "Settlement inactive: all preserved plots conflict with older ownership."
                    : owned.containsAll(desired) ? "" : "Some facilities are outside surviving territory because another village owns their plots.");
        } else {
            for (long plot : town.territory().plots()) {
                if (reserved(registry, town, net.minecraft.world.level.ChunkPos.getX(plot), net.minecraft.world.level.ChunkPos.getZ(plot))) return Optional.empty();
            }
        }
        this.settlements.put(town.id(), town);
        index(town);
        town.recordEvent(gameTime, SettlementEvent.Type.ESTABLISHED, "");
        updateFootprint(registry, town);
        setDirty();
        return Optional.of(town);
    }

    public boolean putArea(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id,
                           UUID actor, SettlementArea area) {
        SettlementRecord town = this.settlements.get(id);
        if (town == null || area == null
                || this.settlements.values().stream()
                        .filter(other -> !other.id().equals(id) && other.dimension().equals(town.dimension()))
                        .anyMatch(other -> other.areas().values().stream().filter(other::owns).anyMatch(area::intersects))
                || !town.putArea(actor, area)) {
            return false;
        }
        updateFootprint(registry, town);
        return true;
    }

    public boolean removeArea(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id,
                              UUID actor, UUID areaId) {
        SettlementRecord town = this.settlements.get(id);
        if (town == null || !town.removeArea(actor, areaId)) {
            return false;
        }
        updateFootprint(registry, town);
        return true;
    }

    public void restoreRegistry(VillageAllegianceRegistrySavedData registry) {
        for (SettlementRecord town : this.settlements.values()) {
            registry.ensureRecord(town.id(), town.foundedAt(), town.dimension(), town.center());
        }
        var ordered = this.settlements.values().stream().sorted(java.util.Comparator
                .comparingLong((SettlementRecord town) -> registry.canonicalRecord(town.id()).orElseThrow().createdGameTime())
                .thenComparing(SettlementRecord::id)).toList();
        for (var town : ordered) {
            if (!town.territory().migrated()) {
                var desired = SettlementTerritoryMigration.desired(town, registry.canonicalRecord(town.id()).orElseThrow().footprintSections());
                var owned = SettlementTerritoryMigration.connect(desired, occupied(registry, town), town.territory().foundingChunk().toLong());
                String conflict = owned.isEmpty() ? "Settlement inactive: all preserved plots conflict with older ownership."
                        : owned.containsAll(desired) ? "" : "Some facilities are outside surviving territory because another village owns their plots.";
                town.migrateTerritory(owned, conflict);
            }
            index(town);
            updateFootprint(registry, town);
        }
    }

    private static void updateFootprint(VillageAllegianceRegistrySavedData registry, SettlementRecord town) {
        Set<Long> footprint = new LinkedHashSet<>();
        for (long plot : town.territory().plots()) {
            footprint.add(SectionPos.asLong(net.minecraft.world.level.ChunkPos.getX(plot),
                    SectionPos.blockToSectionCoord(town.center().getY()), net.minecraft.world.level.ChunkPos.getZ(plot)));
        }
        registry.manage(town.id(), town.center(), footprint);
    }

    public Optional<VillageAllegianceId> owner(ResourceLocation dimension, int x, int z) {
        return Optional.ofNullable(this.ownership.getOrDefault(dimension, Map.of()).get(net.minecraft.world.level.ChunkPos.asLong(x, z)));
    }

    boolean reserved(VillageAllegianceRegistrySavedData registry, SettlementRecord town, int x, int z) {
        var owner = owner(town.dimension(), x, z);
        if (owner.isPresent() && !owner.get().equals(town.id())) return true;
        return registry.activeRecords(town.dimension()).stream().filter(record -> !record.id().equals(town.id())
                && !this.settlements.containsKey(record.id()) && !registry.isManaged(record.id()))
                .anyMatch(record -> record.footprintSections().stream().anyMatch(section -> SectionPos.x(section) == x && SectionPos.z(section) == z));
    }

    private Set<Long> occupied(VillageAllegianceRegistrySavedData registry, SettlementRecord town) {
        Set<Long> occupied = new java.util.HashSet<>();
        this.ownership.getOrDefault(town.dimension(), Map.of()).forEach((chunk, owner) -> {
            if (!owner.equals(town.id())) occupied.add(chunk);
        });
        for (var village : registry.activeRecords(town.dimension())) {
            if (village.id().equals(town.id()) || this.settlements.containsKey(village.id()) || registry.isManaged(village.id())) continue;
            for (long section : village.footprintSections()) occupied.add(net.minecraft.world.level.ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)));
        }
        return occupied;
    }

    private void index(SettlementRecord town) {
        var dimension = this.ownership.computeIfAbsent(town.dimension(), ignored -> new java.util.HashMap<>());
        for (long plot : town.territory().plots()) {
            var previous = dimension.putIfAbsent(plot, town.id());
            if (previous != null && !previous.equals(town.id())) throw new IllegalArgumentException("Duplicated settlement plot ownership");
        }
    }

    void claimed(VillageAllegianceRegistrySavedData registry, SettlementRecord town) {
        index(town);
        updateFootprint(registry, town);
        town.changed();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag entries = new ListTag();
        this.settlements.values().forEach(town -> entries.add(town.save(provider)));
        tag.put("Settlements", entries);
        return tag;
    }

    public static SettlementSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        int version = tag.getInt("FormatVersion");
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported settlement format " + version);
        }
        SettlementSavedData data = new SettlementSavedData();
        for (Tag raw : tag.getList("Settlements", Tag.TAG_COMPOUND)) {
            SettlementRecord town = SettlementRecord.load((CompoundTag) raw, provider, data::setDirty);
            if (data.settlements.putIfAbsent(town.id(), town) != null) {
                throw new IllegalArgumentException("Duplicate settlement identity " + town.id());
            }
        }
        data.settlements.values().stream().filter(town -> town.territory().migrated()).forEach(data::index);
        return data;
    }
}
