package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageAllegianceRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_allegiances";
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_ALIAS_DEPTH = 32;
    private static final int DISCOVERY_RADIUS_BLOCKS = 128;
    private static final long RESIDENT_LAST_SEEN_REFRESH_TICKS = 1_200L;
    public static final long ARCHIVE_GRACE_TICKS = 72_000L;

    private final Map<VillageAllegianceId, AllegianceRecord> records = new LinkedHashMap<>();
    private final Map<VillageAllegianceId, VillageAllegianceId> aliases = new LinkedHashMap<>();
    private final Map<VillageAllegianceId, Optional<VillageAllegianceId>> canonicalCache = new HashMap<>();
    private final Map<UUID, LinkedHashSet<VillageAllegianceId>> residentRecords = new HashMap<>();

    public static VillageAllegianceRegistrySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageAllegianceRegistrySavedData::new, VillageAllegianceRegistrySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillageAllegianceRegistrySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageAllegianceRegistrySavedData data = new VillageAllegianceRegistrySavedData();
        if (tag.getInt("FormatVersion") > FORMAT_VERSION) {
            return data;
        }
        for (Tag raw : tag.getList("Records", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag recordTag) || !recordTag.hasUUID("Id")) {
                continue;
            }
            VillageAllegianceId id = new VillageAllegianceId(recordTag.getUUID("Id"));
            ResourceLocation dimension = ResourceLocation.tryParse(recordTag.getString("OriginDimension"));
            BlockPos origin = readPos(recordTag, "Origin");
            BlockPos center = hasPos(recordTag, "Center") ? readPos(recordTag, "Center") : origin;
            VillageLifecycleState state = enumValue(
                    VillageLifecycleState.class,
                    recordTag.getString("LifecycleState"),
                    recordTag.getBoolean("Archived") ? VillageLifecycleState.ARCHIVED : VillageLifecycleState.ACTIVE);
            Set<Long> sourceSections = longSet(recordTag.getLongArray("SourceSections"));
            Set<Long> footprintSections = longSet(recordTag.getLongArray("FootprintSections"));
            if (footprintSections.isEmpty() && dimension != null && !state.equals(VillageLifecycleState.ARCHIVED)) {
                sourceSections = Set.of(SectionPos.asLong(origin));
                footprintSections = expandedFootprint(sourceSections);
            }
            Map<UUID, ResidentRecord> residents = new LinkedHashMap<>();
            for (Tag residentRaw : recordTag.getList("Residents", Tag.TAG_COMPOUND)) {
                if (residentRaw instanceof CompoundTag residentTag && residentTag.hasUUID("Id")) {
                    UUID residentId = residentTag.getUUID("Id");
                    residents.put(residentId, new ResidentRecord(
                            residentId,
                            residentTag.getBoolean("Adult"),
                            residentTag.getLong("LastSeenGameTime")));
                }
            }
            String displayName = recordTag.getString("DisplayName");
            boolean customName = recordTag.getBoolean("CustomName");
            data.records.put(id, new AllegianceRecord(
                    id,
                    recordTag.getLong("CreatedGameTime"),
                    state,
                    displayName,
                    customName,
                    dimension,
                    origin,
                    center,
                    sourceSections,
                    footprintSections,
                    recordTag.getLong("LastSeenGameTime"),
                    recordTag.getLong("EmptyObservedTicks"),
                    residents));
        }
        for (Tag raw : tag.getList("Aliases", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag aliasTag && aliasTag.hasUUID("Source") && aliasTag.hasUUID("Target")) {
                data.aliases.put(
                        new VillageAllegianceId(aliasTag.getUUID("Source")),
                        new VillageAllegianceId(aliasTag.getUUID("Target")));
            }
        }
        data.ensureNames();
        data.rebuildResidentIndex();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag recordTags = new ListTag();
        for (AllegianceRecord record : this.records.values()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("Id", record.id().value());
            recordTag.putLong("CreatedGameTime", record.createdGameTime());
            recordTag.putBoolean("Archived", record.archived());
            recordTag.putString("LifecycleState", record.lifecycleState().name());
            recordTag.putString("DisplayName", record.displayName());
            recordTag.putBoolean("CustomName", record.customName());
            if (record.originDimension() != null) {
                recordTag.putString("OriginDimension", record.originDimension().toString());
            }
            writePos(recordTag, "Origin", record.originPosition());
            writePos(recordTag, "Center", record.center());
            recordTag.putLongArray("SourceSections", record.sourceSections().stream().mapToLong(Long::longValue).toArray());
            recordTag.putLongArray("FootprintSections", record.footprintSections().stream().mapToLong(Long::longValue).toArray());
            recordTag.putLong("LastSeenGameTime", record.lastSeenGameTime());
            recordTag.putLong("EmptyObservedTicks", record.emptyObservedTicks());
            ListTag residentTags = new ListTag();
            for (ResidentRecord resident : record.residents().values()) {
                CompoundTag residentTag = new CompoundTag();
                residentTag.putUUID("Id", resident.id());
                residentTag.putBoolean("Adult", resident.adult());
                residentTag.putLong("LastSeenGameTime", resident.lastSeenGameTime());
                residentTags.add(residentTag);
            }
            recordTag.put("Residents", residentTags);
            recordTags.add(recordTag);
        }
        tag.put("Records", recordTags);
        ListTag aliasTags = new ListTag();
        for (Map.Entry<VillageAllegianceId, VillageAllegianceId> alias : this.aliases.entrySet()) {
            CompoundTag aliasTag = new CompoundTag();
            aliasTag.putUUID("Source", alias.getKey().value());
            aliasTag.putUUID("Target", alias.getValue().value());
            aliasTags.add(aliasTag);
        }
        tag.put("Aliases", aliasTags);
        return tag;
    }

    public VillageAllegianceId create(long gameTime, ResourceLocation dimension, BlockPos position, String displayName) {
        VillageAllegianceId id;
        do {
            id = VillageAllegianceId.random();
        } while (this.records.containsKey(id));
        String safeName = displayName == null ? "" : displayName.trim();
        boolean customName = !safeName.isBlank();
        if (!customName) {
            safeName = VillageNameGenerator.generate(id.value(), unavailableNames());
        }
        this.records.put(id, AllegianceRecord.create(id, gameTime, dimension, position, safeName, customName));
        setDirty();
        return id;
    }

    public void ensureRecord(VillageAllegianceId id, long gameTime, ResourceLocation dimension, BlockPos position) {
        if (id != null && !this.records.containsKey(id)) {
            String name = VillageNameGenerator.generate(id.value(), unavailableNames());
            this.records.put(id, AllegianceRecord.create(id, gameTime, dimension, position, name, false));
            // A missing alias target may have cached empty results for the whole path.
            this.canonicalCache.clear();
            setDirty();
        }
    }

    public Optional<AllegianceRecord> record(VillageAllegianceId id) {
        return Optional.ofNullable(this.records.get(id));
    }

    public Optional<AllegianceRecord> canonicalRecord(VillageAllegianceId id) {
        return canonical(id).flatMap(this::record);
    }

    public List<AllegianceRecord> records() {
        return List.copyOf(this.records.values());
    }

    public List<AllegianceRecord> activeRecords(ResourceLocation dimension) {
        return this.records.values().stream()
                .filter(record -> record.lifecycleState() != VillageLifecycleState.ARCHIVED)
                .filter(record -> dimension == null || dimension.equals(record.originDimension()))
                .filter(record -> canonical(record.id()).map(record.id()::equals).orElse(false))
                .toList();
    }

    public Optional<VillageAllegianceId> resolveAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        long section = SectionPos.asLong(pos);
        return activeRecords(level.dimension().location()).stream()
                .filter(record -> record.footprintSections().contains(section))
                .min(Comparator.comparingDouble(record -> record.center().distSqr(pos)))
                .map(AllegianceRecord::id);
    }

    public Optional<VillageAllegianceId> discoverAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isVillage(pos)) {
            return resolveAt(level, pos);
        }
        Set<Long> sources = occupiedVillageSections(level, pos);
        long originSection = SectionPos.asLong(pos);
        Set<Long> seeds = sources.stream()
                .filter(source -> sectionDistance(source, originSection) <= 1)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (seeds.isEmpty()) {
            return resolveAt(level, pos);
        }
        Set<Long> cluster = connectedSources(sources, seeds);
        Set<Long> footprint = VillageFootprintResolver.resolve(
                level, expandedFootprint(cluster), pos, DISCOVERY_RADIUS_BLOCKS);
        ResourceLocation dimension = level.dimension().location();
        LinkedHashSet<VillageAllegianceId> matches = activeRecords(dimension).stream()
                .filter(record -> intersects(record.footprintSections(), footprint))
                .map(AllegianceRecord::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        VillageAllegianceId resolved;
        if (matches.isEmpty()) {
            resolved = create(level.getGameTime(), dimension, pos, "");
        } else {
            resolved = autoMerge(matches);
        }
        AllegianceRecord current = this.records.get(resolved);
        if (current != null) {
            this.records.put(resolved, current.observe(cluster, footprint, centerOf(cluster, pos), level.getGameTime()));
            setDirty();
        }
        return Optional.of(resolved);
    }

    public boolean rename(VillageAllegianceId id, String displayName) {
        Optional<VillageAllegianceId> canonical = canonical(id);
        Optional<String> validated = validateVillageName(displayName);
        if (canonical.isEmpty() || validated.isEmpty()) {
            return false;
        }
        String safeName = validated.get();
        String normalized = VillageNameGenerator.normalize(safeName);
        boolean duplicate = this.records.values().stream()
                .filter(record -> !record.id().equals(canonical.get()))
                .anyMatch(record -> VillageNameGenerator.normalize(record.displayName()).equals(normalized));
        if (duplicate) {
            return false;
        }
        AllegianceRecord current = this.records.get(canonical.get());
        if (current == null || current.displayName().equals(safeName)) {
            return current != null;
        }
        this.records.put(canonical.get(), current.withName(safeName, true));
        setDirty();
        return true;
    }

    public static Optional<String> validateVillageName(String proposed) {
        if (proposed == null || proposed.isBlank() || proposed.codePoints()
                .anyMatch(codePoint -> Character.isISOControl(codePoint) || codePoint == '\u00a7')) {
            return Optional.empty();
        }
        String normalized = proposed.strip().replaceAll("\\s+", " ");
        return normalized.isBlank() || normalized.length() > 32 ? Optional.empty() : Optional.of(normalized);
    }

    public boolean archive(VillageAllegianceId id) {
        Optional<VillageAllegianceId> canonical = canonical(id);
        AllegianceRecord raw = this.records.get(id);
        AllegianceRecord current = raw != null && canonical.isPresent() && !id.equals(canonical.get())
                ? raw
                : canonical.map(this.records::get).orElse(null);
        if (current == null || current.archived()) {
            return false;
        }
        this.records.put(current.id(), current.withLifecycle(VillageLifecycleState.ARCHIVED, current.emptyObservedTicks()));
        setDirty();
        return true;
    }

    public void observeEmpty(VillageAllegianceId id, long observedTicks) {
        AllegianceRecord current = canonicalRecord(id).orElse(null);
        if (current == null || current.archived() || observedTicks <= 0L) {
            return;
        }
        long total = Math.min(ARCHIVE_GRACE_TICKS, current.emptyObservedTicks() + observedTicks);
        VillageLifecycleState state = total >= ARCHIVE_GRACE_TICKS
                ? VillageLifecycleState.ARCHIVED
                : VillageLifecycleState.EMPTY_GRACE;
        this.records.put(current.id(), current.withLifecycle(state, total));
        setDirty();
    }

    public void refreshLoadedLifecycles(ServerLevel level, long observedTicks) {
        if (level == null || observedTicks <= 0L) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        for (AllegianceRecord record : List.copyOf(activeRecords(dimension))) {
            if (record.sourceSections().isEmpty() || !entireFootprintLoaded(level, record)) {
                continue;
            }
            if (!observeLoadedCluster(level, record)) {
                observeEmpty(record.id(), observedTicks);
            }
        }
    }

    public void addOrUpdateResident(VillageAllegianceId id, UUID residentId, boolean adult, long gameTime) {
        AllegianceRecord current = canonicalRecord(id).orElse(null);
        if (current == null || residentId == null) {
            return;
        }
        boolean changed = false;
        LinkedHashSet<VillageAllegianceId> previousRecords = this.residentRecords.get(residentId);
        if (previousRecords != null) {
            for (VillageAllegianceId previousId : List.copyOf(previousRecords)) {
                if (previousId.equals(current.id())) {
                    continue;
                }
                AllegianceRecord previous = this.records.get(previousId);
                if (previous != null && previous.residents().containsKey(residentId)) {
                    this.records.put(previousId, previous.withoutResident(residentId));
                    changed = true;
                }
            }
        }

        current = this.records.get(current.id());
        ResidentRecord existing = current.residents().get(residentId);
        boolean refreshLastSeen = existing == null
                || existing.adult() != adult
                || gameTime < existing.lastSeenGameTime()
                || gameTime - existing.lastSeenGameTime() >= RESIDENT_LAST_SEEN_REFRESH_TICKS;
        if (refreshLastSeen) {
            this.records.put(current.id(), current.withResident(new ResidentRecord(residentId, adult, gameTime)));
            changed = true;
        }
        this.residentRecords.put(residentId, new LinkedHashSet<>(Set.of(current.id())));
        if (changed) {
            setDirty();
        }
    }

    public void removeResident(VillageAllegianceId id, UUID residentId) {
        AllegianceRecord current = canonicalRecord(id).orElse(null);
        if (current != null && current.residents().containsKey(residentId)) {
            this.records.put(current.id(), current.withoutResident(residentId));
            removeResidentIndex(residentId, current.id());
            setDirty();
        }
    }

    public void removeResidentEverywhere(UUID residentId) {
        if (residentId == null) {
            return;
        }
        LinkedHashSet<VillageAllegianceId> indexed = this.residentRecords.remove(residentId);
        if (indexed == null || indexed.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (VillageAllegianceId recordId : indexed) {
            AllegianceRecord record = this.records.get(recordId);
            if (record != null && record.residents().containsKey(residentId)) {
                this.records.put(recordId, record.withoutResident(residentId));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public Set<UUID> residentIds() {
        return Set.copyOf(this.residentRecords.keySet());
    }

    public Optional<VillageAllegianceId> canonical(VillageAllegianceId raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Optional<VillageAllegianceId> cached = this.canonicalCache.get(raw);
        if (cached != null) {
            return cached;
        }
        Set<VillageAllegianceId> seen = new HashSet<>();
        List<VillageAllegianceId> path = new ArrayList<>();
        VillageAllegianceId current = raw;
        for (int depth = 0; depth <= MAX_ALIAS_DEPTH; depth++) {
            if (!seen.add(current)) {
                return cachePath(path, Optional.empty());
            }
            path.add(current);
            VillageAllegianceId next = this.aliases.get(current);
            if (next == null) {
                return cachePath(path, this.records.containsKey(current) ? Optional.of(current) : Optional.empty());
            }
            current = next;
        }
        return cachePath(path, Optional.empty());
    }

    public boolean merge(VillageAllegianceId source, VillageAllegianceId target) {
        Optional<VillageAllegianceId> sourceCanonical = canonical(source);
        Optional<VillageAllegianceId> targetCanonical = canonical(target);
        if (sourceCanonical.isEmpty() || targetCanonical.isEmpty()) {
            return false;
        }
        if (sourceCanonical.get().equals(targetCanonical.get())) {
            // Reversing a canonical identity into one of its aliases would create a cycle.
            return !(source.equals(sourceCanonical.get()) && !target.equals(targetCanonical.get()));
        }
        return mergeCanonical(sourceCanonical.get(), targetCanonical.get());
    }

    public int aliasCount() {
        return this.aliases.size();
    }

    public void clearRuntimeCache() {
        this.canonicalCache.clear();
    }

    private VillageAllegianceId autoMerge(Collection<VillageAllegianceId> ids) {
        VillageAllegianceId survivor = ids.stream()
                .map(this::canonical)
                .flatMap(Optional::stream)
                .distinct()
                .min(Comparator
                        .comparing((VillageAllegianceId id) -> !this.records.get(id).customName())
                        .thenComparingLong(id -> this.records.get(id).createdGameTime())
                        .thenComparing(VillageAllegianceId::compareTo))
                .orElseThrow();
        for (VillageAllegianceId id : List.copyOf(ids)) {
            canonical(id).filter(candidate -> !candidate.equals(survivor))
                    .ifPresent(candidate -> mergeCanonical(candidate, survivor));
        }
        return survivor;
    }

    private boolean mergeCanonical(VillageAllegianceId source, VillageAllegianceId target) {
        AllegianceRecord sourceRecord = this.records.get(source);
        AllegianceRecord targetRecord = this.records.get(target);
        if (sourceRecord == null || targetRecord == null || source.equals(target)) {
            return false;
        }
        AllegianceRecord merged = targetRecord.absorb(sourceRecord);
        this.records.put(target, merged);
        for (UUID residentId : merged.residents().keySet()) {
            this.residentRecords.computeIfAbsent(residentId, ignored -> new LinkedHashSet<>()).add(target);
        }
        this.aliases.put(source, target);
        this.canonicalCache.clear();
        setDirty();
        return true;
    }

    private Set<Long> occupiedVillageSections(ServerLevel level, BlockPos origin) {
        Set<Long> sections = new LinkedHashSet<>();
        int chunkRadius = Math.floorDiv(DISCOVERY_RADIUS_BLOCKS, 16) + 1;
        ChunkPos originChunk = new ChunkPos(origin);
        PoiManager manager = level.getPoiManager();
        for (int x = originChunk.x - chunkRadius; x <= originChunk.x + chunkRadius; x++) {
            for (int z = originChunk.z - chunkRadius; z <= originChunk.z + chunkRadius; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                manager.getInChunk(type -> type.is(PoiTypeTags.VILLAGE), new ChunkPos(x, z), PoiManager.Occupancy.IS_OCCUPIED)
                        .map(record -> SectionPos.asLong(record.getPos()))
                        .forEach(sections::add);
            }
        }
        return sections;
    }

    private static boolean entireFootprintLoaded(ServerLevel level, AllegianceRecord record) {
        for (long packed : record.footprintSections()) {
            SectionPos section = SectionPos.of(packed);
            if (!level.hasChunk(section.x(), section.z())) {
                return false;
            }
        }
        return true;
    }

    private boolean observeLoadedCluster(ServerLevel level, AllegianceRecord record) {
        Set<Long> occupied = occupiedVillageSections(level, record.center());
        Set<Long> seeds = occupied.stream()
                .filter(record.footprintSections()::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (seeds.isEmpty()) {
            return false;
        }
        Set<Long> cluster = connectedSources(occupied, seeds);
        Set<Long> footprint = VillageFootprintResolver.resolve(
                level, expandedFootprint(cluster), record.center(), DISCOVERY_RADIUS_BLOCKS);
        LinkedHashSet<VillageAllegianceId> matches = activeRecords(level.dimension().location()).stream()
                .filter(candidate -> intersects(candidate.footprintSections(), footprint))
                .map(AllegianceRecord::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        matches.add(record.id());
        VillageAllegianceId resolved = autoMerge(matches);
        AllegianceRecord current = this.records.get(resolved);
        if (current != null) {
            this.records.put(resolved, current.observe(cluster, footprint, centerOf(cluster, record.center()), level.getGameTime()));
            setDirty();
        }
        return true;
    }

    private static Set<Long> connectedSources(Set<Long> all, Set<Long> seeds) {
        Set<Long> result = new LinkedHashSet<>(seeds);
        Set<Long> remaining = new HashSet<>(all);
        remaining.removeAll(seeds);
        ArrayDeque<Long> pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            SectionPos current = SectionPos.of(pending.removeFirst());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) {
                            continue;
                        }
                        long candidate = SectionPos.asLong(current.x() + dx, current.y() + dy, current.z() + dz);
                        if (remaining.remove(candidate)) {
                            result.add(candidate);
                            pending.addLast(candidate);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Set<Long> expandedFootprint(Collection<Long> sourceSections) {
        Set<Long> footprint = new LinkedHashSet<>();
        for (long packed : sourceSections) {
            SectionPos section = SectionPos.of(packed);
            footprint.add(packed);
            for (Direction direction : Direction.values()) {
                footprint.add(SectionPos.asLong(
                        section.x() + direction.getStepX(),
                        section.y() + direction.getStepY(),
                        section.z() + direction.getStepZ()));
            }
        }
        return Set.copyOf(footprint);
    }

    private static int sectionDistance(long first, long second) {
        SectionPos left = SectionPos.of(first);
        SectionPos right = SectionPos.of(second);
        return Math.abs(left.x() - right.x())
                + Math.abs(left.y() - right.y())
                + Math.abs(left.z() - right.z());
    }

    private static boolean intersects(Set<Long> first, Set<Long> second) {
        Set<Long> smaller = first.size() <= second.size() ? first : second;
        Set<Long> larger = smaller == first ? second : first;
        return smaller.stream().anyMatch(larger::contains);
    }

    private static BlockPos centerOf(Set<Long> sections, BlockPos fallback) {
        if (sections.isEmpty()) {
            return fallback.immutable();
        }
        long x = 0L;
        long y = 0L;
        long z = 0L;
        for (long packed : sections) {
            BlockPos center = SectionPos.of(packed).center();
            x += center.getX();
            y += center.getY();
            z += center.getZ();
        }
        return new BlockPos((int) (x / sections.size()), (int) (y / sections.size()), (int) (z / sections.size()));
    }

    private Set<String> unavailableNames() {
        Set<String> result = new HashSet<>();
        this.records.values().stream().map(AllegianceRecord::displayName)
                .map(VillageNameGenerator::normalize).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private void ensureNames() {
        Set<String> unavailable = unavailableNames();
        boolean changed = false;
        for (Map.Entry<VillageAllegianceId, AllegianceRecord> entry : List.copyOf(this.records.entrySet())) {
            if (entry.getValue().displayName().isBlank()) {
                String name = VillageNameGenerator.generate(entry.getKey().value(), unavailable);
                unavailable.add(VillageNameGenerator.normalize(name));
                this.records.put(entry.getKey(), entry.getValue().withName(name, false));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private void rebuildResidentIndex() {
        this.residentRecords.clear();
        for (Map.Entry<VillageAllegianceId, AllegianceRecord> entry : this.records.entrySet()) {
            for (UUID residentId : entry.getValue().residents().keySet()) {
                this.residentRecords.computeIfAbsent(residentId, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
    }

    private void removeResidentIndex(UUID residentId, VillageAllegianceId recordId) {
        LinkedHashSet<VillageAllegianceId> indexed = this.residentRecords.get(residentId);
        if (indexed == null) {
            return;
        }
        indexed.remove(recordId);
        if (indexed.isEmpty()) {
            this.residentRecords.remove(residentId);
        }
    }

    private Optional<VillageAllegianceId> cachePath(List<VillageAllegianceId> path, Optional<VillageAllegianceId> result) {
        path.forEach(id -> this.canonicalCache.put(id, result));
        return result;
    }

    private static Set<Long> longSet(long[] values) {
        Set<Long> result = new LinkedHashSet<>();
        for (long value : values) {
            result.add(value);
        }
        return Set.copyOf(result);
    }

    private static void writePos(CompoundTag tag, String prefix, BlockPos pos) {
        tag.putInt(prefix + "X", pos.getX());
        tag.putInt(prefix + "Y", pos.getY());
        tag.putInt(prefix + "Z", pos.getZ());
    }

    private static BlockPos readPos(CompoundTag tag, String prefix) {
        return new BlockPos(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"));
    }

    private static boolean hasPos(CompoundTag tag, String prefix) {
        return tag.contains(prefix + "X", Tag.TAG_INT)
                && tag.contains(prefix + "Y", Tag.TAG_INT)
                && tag.contains(prefix + "Z", Tag.TAG_INT);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public record ResidentRecord(UUID id, boolean adult, long lastSeenGameTime) {
    }

    public record AllegianceRecord(
            VillageAllegianceId id,
            long createdGameTime,
            VillageLifecycleState lifecycleState,
            String displayName,
            boolean customName,
            ResourceLocation originDimension,
            BlockPos originPosition,
            BlockPos center,
            Set<Long> sourceSections,
            Set<Long> footprintSections,
            long lastSeenGameTime,
            long emptyObservedTicks,
            Map<UUID, ResidentRecord> residents) {
        public AllegianceRecord {
            lifecycleState = lifecycleState == null ? VillageLifecycleState.ACTIVE : lifecycleState;
            displayName = displayName == null ? "" : displayName;
            originPosition = originPosition == null ? BlockPos.ZERO : originPosition.immutable();
            center = center == null ? originPosition : center.immutable();
            sourceSections = sourceSections == null ? Set.of() : Set.copyOf(sourceSections);
            footprintSections = footprintSections == null ? Set.of() : Set.copyOf(footprintSections);
            residents = residents == null ? Map.of() : Map.copyOf(residents);
            emptyObservedTicks = Math.max(0L, emptyObservedTicks);
        }

        private static AllegianceRecord create(
                VillageAllegianceId id,
                long gameTime,
                ResourceLocation dimension,
                BlockPos position,
                String displayName,
                boolean customName) {
            return new AllegianceRecord(
                    id, gameTime, VillageLifecycleState.ACTIVE, displayName, customName,
                    dimension, position, position, Set.of(), Set.of(), gameTime, 0L, Map.of());
        }

        public boolean archived() {
            return this.lifecycleState == VillageLifecycleState.ARCHIVED;
        }

        public int adultResidentCount() {
            return (int) this.residents.values().stream().filter(ResidentRecord::adult).count();
        }

        private AllegianceRecord observe(Set<Long> sources, Set<Long> footprint, BlockPos center, long gameTime) {
            Set<Long> mergedSources = union(this.sourceSections, sources);
            Set<Long> mergedFootprint = union(
                    union(this.footprintSections, footprint), expandedFootprint(mergedSources));
            return new AllegianceRecord(
                    this.id, this.createdGameTime, VillageLifecycleState.ACTIVE, this.displayName, this.customName,
                    this.originDimension, this.originPosition, center, mergedSources, mergedFootprint,
                    gameTime, 0L, this.residents);
        }

        private AllegianceRecord absorb(AllegianceRecord other) {
            Map<UUID, ResidentRecord> mergedResidents = new LinkedHashMap<>(this.residents);
            other.residents.forEach((id, resident) -> mergedResidents.merge(id, resident,
                    (first, second) -> first.lastSeenGameTime() >= second.lastSeenGameTime() ? first : second));
            Set<Long> mergedSources = union(this.sourceSections, other.sourceSections);
            Set<Long> mergedFootprint = union(
                    union(this.footprintSections, other.footprintSections), expandedFootprint(mergedSources));
            return new AllegianceRecord(
                    this.id,
                    Math.min(this.createdGameTime, other.createdGameTime),
                    this.lifecycleState == VillageLifecycleState.ARCHIVED ? other.lifecycleState : this.lifecycleState,
                    this.displayName,
                    this.customName,
                    this.originDimension,
                    this.originPosition,
                    centerOf(mergedSources, this.center),
                    mergedSources,
                    mergedFootprint,
                    Math.max(this.lastSeenGameTime, other.lastSeenGameTime),
                    Math.min(this.emptyObservedTicks, other.emptyObservedTicks),
                    mergedResidents);
        }

        private AllegianceRecord withName(String name, boolean custom) {
            return new AllegianceRecord(
                    this.id, this.createdGameTime, this.lifecycleState, name, custom,
                    this.originDimension, this.originPosition, this.center, this.sourceSections,
                    this.footprintSections, this.lastSeenGameTime, this.emptyObservedTicks, this.residents);
        }

        private AllegianceRecord withLifecycle(VillageLifecycleState state, long emptyTicks) {
            return new AllegianceRecord(
                    this.id, this.createdGameTime, state, this.displayName, this.customName,
                    this.originDimension, this.originPosition, this.center, this.sourceSections,
                    this.footprintSections, this.lastSeenGameTime, emptyTicks, this.residents);
        }

        private AllegianceRecord withResident(ResidentRecord resident) {
            Map<UUID, ResidentRecord> updated = new LinkedHashMap<>(this.residents);
            updated.put(resident.id(), resident);
            return new AllegianceRecord(
                    this.id, this.createdGameTime, this.lifecycleState, this.displayName, this.customName,
                    this.originDimension, this.originPosition, this.center, this.sourceSections,
                    this.footprintSections, this.lastSeenGameTime, this.emptyObservedTicks, updated);
        }

        private AllegianceRecord withoutResident(UUID residentId) {
            Map<UUID, ResidentRecord> updated = new LinkedHashMap<>(this.residents);
            updated.remove(residentId);
            return new AllegianceRecord(
                    this.id, this.createdGameTime, this.lifecycleState, this.displayName, this.customName,
                    this.originDimension, this.originPosition, this.center, this.sourceSections,
                    this.footprintSections, this.lastSeenGameTime, this.emptyObservedTicks, updated);
        }

        private static Set<Long> union(Set<Long> first, Set<Long> second) {
            Set<Long> result = new LinkedHashSet<>(first);
            result.addAll(second);
            return Set.copyOf(result);
        }
    }
}
