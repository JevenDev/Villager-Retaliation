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
    private net.minecraft.server.MinecraftServer bankServer;
    private static final String DATA_NAME = "villagerretaliation_settlements";
    private static final int FORMAT_VERSION = 73;
    final SettlementSimulationService simulation = new SettlementSimulationService();
    final SettlementAllocationService allocation = new SettlementAllocationService();
    final Map<UUID, UUID> policyRequests = new java.util.HashMap<>();
    final Map<UUID, UUID> workerPoolRequests = new java.util.HashMap<>();
    private final Map<VillageAllegianceId, SettlementRecord> settlements = new LinkedHashMap<>();
    private final SettlementRecovery recovery = new SettlementRecovery();
    private final Map<VillageAllegianceId, CompoundTag> retired = new LinkedHashMap<>();

    private final Map<ResourceLocation, Map<Long, VillageAllegianceId>> ownership = new java.util.HashMap<>();

    public static SettlementSavedData get(ServerLevel level) {
        var data = level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> missing(level.getServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data")),
                        SettlementSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
        if (data.bankServer != level.getServer()) {
            data.bankServer = level.getServer();
            data.settlements.values().forEach(town -> town.economy().bank.bind(level.getServer(), level.registryAccess()));
        }
        return data;
    }

    static SettlementSavedData get(net.minecraft.world.level.storage.DimensionDataStorage storage, java.nio.file.Path directory) {
        return storage.computeIfAbsent(
                new SavedData.Factory<>(() -> missing(directory), SettlementSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public Optional<SettlementRecord> get(VillageAllegianceId id) {
        if (!this.retired.containsKey(id)) return Optional.ofNullable(this.settlements.get(id));
        var visited = new java.util.HashSet<VillageAllegianceId>();
        while (this.retired.containsKey(id)) {
            if (!visited.add(id)) return Optional.empty();
            var entry = this.retired.get(id);
            if (!entry.hasUUID("MergedInto")) return Optional.empty();
            id = new VillageAllegianceId(entry.getUUID("MergedInto"));
        }
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
        if (readOnly() || dimension == null || center == null) return false;
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
        if (readOnly() || this.recovery.reserved(villageId)
                || village == null || this.recovery.reserved(village.id()) || village.archived() || village.originDimension() == null || leader == null
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
        this.retired.remove(town.id());
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
        restoreRegistry(null, registry);
    }

    public void restoreRegistry(ServerLevel level, VillageAllegianceRegistrySavedData registry) {
        if (readOnly()) return;
        for (SettlementRecord town : this.settlements.values()) {
            registry.ensureRecord(town.id(), town.foundedAt(), town.dimension(), town.center());
        }
        for (var entry : this.retired.entrySet()) {
            var raw = entry.getValue();
            registry.ensureRecord(entry.getKey(), raw.getLong("FoundedAt"),
                    ResourceLocation.parse(raw.getString("Dimension")), BlockPos.of(raw.getLong("Center")));
        }
        for (var entry : this.retired.entrySet()) {
            var raw = entry.getValue();
            if (!raw.hasUUID("MergedInto")) continue;
            var target = new VillageAllegianceId(raw.getUUID("MergedInto"));
            while (this.retired.containsKey(target) && this.retired.get(target).hasUUID("MergedInto")) {
                target = new VillageAllegianceId(this.retired.get(target).getUUID("MergedInto"));
            }
            registry.mergeSettlements(level, entry.getKey(), target);
        }
        for (var entry : this.retired.entrySet()) {
            if (!entry.getValue().hasUUID("MergedInto")) registry.releaseSettlement(entry.getKey());
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
        if (this.recovery.preservedRoot() != null) return this.recovery.preservedRoot().copy();
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag entries = new ListTag();
        this.settlements.values().forEach(town -> entries.add(town.save(provider)));
        this.recovery.appendQuarantine(entries);
        tag.put("Settlements", entries);
        var retiredTags = new ListTag();
        this.retired.values().forEach(entry -> retiredTags.add(entry.copy()));
        tag.put("RetiredSettlements", retiredTags);
        this.recovery.saveOriginal(tag);
        return tag;
    }

    public static SettlementSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        SettlementSavedData data = new SettlementSavedData();
        data.recovery.load(tag, provider, FORMAT_VERSION, data::setDirty, data.settlements, data.ownership);
        if (!data.readOnly()) {
            try {
                if (tag.contains("RetiredSettlements") && (!(tag.get("RetiredSettlements") instanceof ListTag retiredTags)
                        || !retiredTags.isEmpty() && retiredTags.getElementType() != Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Invalid settlement lifecycle journal");
                }
                for (var raw : tag.getList("RetiredSettlements", Tag.TAG_COMPOUND)) {
                    var entry = (CompoundTag) raw;
                    if (!entry.hasUUID("Id") || !entry.contains("Center", Tag.TAG_LONG)
                            || ResourceLocation.tryParse(entry.getString("Dimension")) == null
                            || entry.contains("MergedInto") && !entry.hasUUID("MergedInto")) {
                        throw new IllegalArgumentException("Invalid retired settlement identity");
                    }
                    var id = new VillageAllegianceId(entry.getUUID("Id"));
                    if (data.settlements.containsKey(id) || data.recovery.reserved(id) || data.retired.putIfAbsent(id, entry.copy()) != null) {
                        throw new IllegalArgumentException("Duplicate retired settlement identity");
                    }
                }
                for (var id : data.retired.keySet()) {
                    var visited = new java.util.HashSet<VillageAllegianceId>();
                    var cursor = id;
                    while (data.retired.containsKey(cursor) && data.retired.get(cursor).hasUUID("MergedInto")) {
                        if (!visited.add(cursor)) throw new IllegalArgumentException("Cyclic settlement merger");
                        cursor = new VillageAllegianceId(data.retired.get(cursor).getUUID("MergedInto"));
                    }
                    if (!data.retired.containsKey(cursor) && !data.settlements.containsKey(cursor) && !data.recovery.reserved(cursor)) {
                        throw new IllegalArgumentException("Missing settlement merger target");
                    }
                }
            } catch (IllegalArgumentException exception) {
                data.recovery.protect(tag, exception.toString());
                data.settlements.clear();
                data.ownership.clear();
            }
        }
        return data;
    }

    public List<String> recoveryDiagnostics() { return this.recovery.diagnostics(); }

    public boolean readOnly() { return this.recovery.readOnly(); }

    private static SettlementSavedData missing(java.nio.file.Path directory) {
        var data = new SettlementSavedData();
        if (!java.nio.file.Files.notExists(directory.resolve(DATA_NAME + ".dat"))) {
            data.recovery.protect(new CompoundTag(), "Existing settlement file could not be read");
        }
        return data;
    }

    void applyLifecycle(ServerLevel level, SettlementRecord source, SettlementRecord target, CompoundTag replacement) throws java.io.IOException {
        if (readOnly() || this.settlements.get(source.id()) != source
                || target != null && this.settlements.get(target.id()) != target) throw new IllegalStateException("Stale settlement lifecycle");
        var next = replacement == null ? null : SettlementRecord.load(replacement, level.registryAccess(), this::setDirty);
        if (next != null) next.economy().bank.bind(level.getServer(), level.registryAccess());
        var root = save(new CompoundTag(), level.registryAccess());
        var entries = root.getList("Settlements", Tag.TAG_COMPOUND);
        entries.removeIf(raw -> {
            var id = new VillageAllegianceId(((CompoundTag) raw).getUUID("Id"));
            return id.equals(source.id()) || target != null && id.equals(target.id());
        });
        if (replacement != null) entries.add(replacement.copy());
        CompoundTag retiredEntry = null;
        if (replacement == null || target != null) {
            retiredEntry = source.save(level.registryAccess());
            if (target != null) retiredEntry.putUUID("MergedInto", target.id().value());
            root.getList("RetiredSettlements", Tag.TAG_COMPOUND).add(retiredEntry);
        }
        var wrapper = new CompoundTag();
        wrapper.put("data", root);
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(wrapper);
        var file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve(DATA_NAME + ".dat");
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(wrapper, file);
        this.settlements.remove(source.id());
        if (target != null) this.settlements.remove(target.id());
        if (next != null) this.settlements.put(next.id(), next);
        if (retiredEntry != null) this.retired.put(source.id(), retiredEntry);
        this.ownership.values().forEach(plots -> plots.values().removeIf(id -> id.equals(source.id()) || target != null && id.equals(target.id())));
        if (next != null) index(next);
        this.simulation.invalidate();
        SettlementViewService.closeTown(source);
        if (target != null) SettlementViewService.closeTown(target);
        SettlementGroundItemService.invalidateOwners();
        SettlementGroundExperienceService.invalidateOwners();
        SettlementLivestockService.invalidateOwners();
        SettlementEntityDeathService.invalidateOwners();
        restoreRegistry(level, VillageAllegianceRegistrySavedData.get(level));
        setDirty();
    }

    void persistProductionMigration(ServerLevel level, SettlementRecord source, SettlementRecord converted) throws java.io.IOException {
        if (readOnly() || this.settlements.get(source.id()) != source || !source.id().equals(converted.id())
                || !source.dimension().equals(converted.dimension())) throw new IllegalStateException("Stale production migration");
        var root = save(new CompoundTag(), level.registryAccess());
        var entries = root.getList("Settlements", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            if (((CompoundTag) entries.get(index)).getUUID("Id").equals(source.id().value())) {
                entries.set(index, converted.save(level.registryAccess()));
                break;
            }
        }
        var wrapper = new CompoundTag();
        wrapper.put("data", root);
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(wrapper);
        var file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve(DATA_NAME + ".dat");
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(wrapper, file);
    }

    @Override
    public void save(java.io.File file, HolderLookup.Provider provider) {
        if (!readOnly()) super.save(file, provider);
    }
}
